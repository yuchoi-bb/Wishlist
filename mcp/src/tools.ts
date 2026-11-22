/**
 * The tools an assistant sees. Named and described in the words Arc itself uses — 항목, 세부항목,
 * 대분류, 예정일 — so a model that has only this schema to go on files things the way the app's own
 * screens read, rather than inventing a vocabulary of its own.
 */
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { todayIso } from './dates.js';
import type { ArcStore, StoredItem } from './store.js';

const ISO_DATE = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'YYYY-MM-DD 형식이어야 합니다')
  .describe('날짜 (YYYY-MM-DD). 시각은 없습니다 — Arc의 날짜는 하루 단위입니다.');

const PRIORITY = z
  .number()
  .int()
  .min(1)
  .max(3)
  .describe('우선순위: 1 높음, 2 보통, 3 여유. 생략하면 보통.');

const SUB_ITEM = z.object({
  title: z.string().min(1).describe('세부항목 이름'),
  done: z.boolean().optional().describe('이미 끝난 단계면 true'),
  endDate: ISO_DATE.optional().describe('이 단계의 완료예정일'),
});

/** What goes back to the model: compact, and dates as text it can reason about. */
function render(item: StoredItem): string {
  const category = [item.majorCategory, item.minorCategory].filter(Boolean).join(' › ') || '분류 없음';
  const priority = { 1: '높음', 2: '보통', 3: '여유' }[item.priority] ?? '보통';
  const head = [
    `[${item.id}] ${item.title}${item.isDone ? ' (완료)' : ''}${item.shared ? ' (공유)' : ''}`,
    `  분류: ${category} · 순위: ${priority}`,
    `  시작일: ${item.startedAt ?? '-'} · 최종 종료일: ${item.endDate ?? '-'}`,
  ];
  if (item.memo) head.push(`  메모: ${item.memo}`);
  for (const sub of item.subItems) {
    const link = sub.linkedItemId ? `  → ${sub.linkedItemId}` : '';
    head.push(`   - [${sub.done ? 'x' : ' '}] ${sub.title}${sub.endDate ? `  예정일 ${sub.endDate}` : ''}${link}`);
  }
  return head.join('\n');
}

const text = (body: string) => ({ content: [{ type: 'text' as const, text: body }] });

export function registerArcTools(server: McpServer, store: ArcStore, timeZone: string): void {
  server.registerTool(
    'list_categories',
    {
      title: '분류 목록',
      description:
        '이미 쓰고 있는 대분류와 중분류를 돌려줍니다. 할 일을 추가하기 전에 먼저 불러서, 새 분류를 만드는 대신 기존 분류에 넣으세요. Arc는 분류마다 색을 기억하므로 이름이 조금만 달라도 다른 분류가 됩니다.',
      inputSchema: {},
      annotations: { readOnlyHint: true },
    },
    async () => {
      const groups = await store.categories();
      if (groups.length === 0) return text('아직 쓰이는 분류가 없습니다.');
      return text(
        groups
          .map((g) => `${g.majorCategory}${g.minorCategories.length ? ` › ${g.minorCategories.join(', ')}` : ''}`)
          .join('\n'),
      );
    },
  );

  server.registerTool(
    'list_tasks',
    {
      title: '할 일 목록',
      description:
        'Arc에 있는 할 일을 봅니다. 수정하거나 완료 처리하려면 먼저 이걸로 id를 확인해야 합니다. 기본은 아직 안 끝난 것만 보여줍니다.',
      inputSchema: {
        includeDone: z.boolean().optional().describe('완료된 항목도 포함할지. 기본 false'),
        majorCategory: z.string().optional().describe('이 대분류만'),
        dueBefore: ISO_DATE.optional().describe('최종 종료일이 이 날짜 이전인 것만'),
      },
      annotations: { readOnlyHint: true },
    },
    async ({ includeDone, majorCategory, dueBefore }) => {
      let items = await store.list();
      if (!includeDone) items = items.filter((i) => !i.isDone);
      if (majorCategory) items = items.filter((i) => i.majorCategory === majorCategory);
      if (dueBefore) items = items.filter((i) => i.endDate !== null && i.endDate <= dueBefore);
      items.sort((a, b) => (a.endDate ?? '9999').localeCompare(b.endDate ?? '9999'));

      if (items.length === 0) return text('해당하는 할 일이 없습니다.');
      return text(`오늘은 ${todayIso(timeZone)}입니다.\n\n${items.map(render).join('\n\n')}`);
    },
  );

  server.registerTool(
    'add_task',
    {
      title: '할 일 추가',
      description:
        '할 일(항목) 하나를 Arc에 넣습니다. 함께 정한 단계들은 subItems로 같이 넣으세요 — 따로 추가하지 말고 한 번에 넣는 것이 맞습니다. 사용자 폰에 바로 나타나며 되돌리려면 지워야 하니, 무엇을 넣을지 먼저 사용자에게 확인하세요.',
      inputSchema: {
        title: z.string().min(1).describe('할 일 이름. 예: "호주여행계획"'),
        subItems: z.array(SUB_ITEM).optional().describe('세부항목(단계) 목록'),
        majorCategory: z.string().optional().describe('대분류. list_categories로 먼저 확인하세요'),
        minorCategory: z.string().optional().describe('중분류'),
        endDate: ISO_DATE.optional().describe('최종 종료일(목표일)'),
        startedAt: ISO_DATE.optional().describe('시작일. 생략하면 오늘'),
        priority: PRIORITY.optional(),
        memo: z.string().optional().describe('메모'),
      },
      annotations: { idempotentHint: false },
    },
    async (args) => {
      const created = await store.add(args);
      return text(`추가했습니다.\n\n${render(created)}`);
    },
  );

  server.registerTool(
    'update_task',
    {
      title: '할 일 수정',
      description:
        '기존 할 일을 고칩니다. 적은 항목만 바뀌고 나머지는 그대로 둡니다. 단, subItems를 주면 기존 세부항목 전체를 대체하므로, 하나만 더할 때도 기존 목록을 함께 보내야 합니다.',
      inputSchema: {
        id: z.string().min(1).describe('list_tasks가 알려준 id'),
        title: z.string().optional(),
        subItems: z.array(SUB_ITEM).optional().describe('기존 세부항목을 통째로 대체합니다'),
        majorCategory: z.string().optional(),
        minorCategory: z.string().optional(),
        endDate: ISO_DATE.optional(),
        startedAt: ISO_DATE.optional(),
        priority: PRIORITY.optional(),
        memo: z.string().optional(),
        isDone: z.boolean().optional(),
      },
    },
    async ({ id, ...rest }) => text(`수정했습니다.\n\n${render(await store.update(id, rest))}`),
  );

  server.registerTool(
    'complete_task',
    {
      title: '할 일 완료',
      description: '할 일을 완료로 표시합니다. 세부항목 하나만 끝났다면 update_task를 쓰세요.',
      inputSchema: { id: z.string().min(1).describe('list_tasks가 알려준 id') },
    },
    async ({ id }) => text(`완료 처리했습니다.\n\n${render(await store.update(id, { isDone: true }))}`),
  );

  server.registerTool(
    'delete_task',
    {
      title: '할 일 삭제',
      description:
        '할 일을 지웁니다. 되돌릴 수 없고 사용자의 다른 기기에서도 사라지니, 지우기 전에 반드시 사용자에게 확인하세요.',
      inputSchema: { id: z.string().min(1).describe('list_tasks가 알려준 id') },
      annotations: { destructiveHint: true },
    },
    async ({ id }) => {
      const existing = await store.get(id);
      if (!existing) return text(`그런 할 일이 없습니다: ${id}`);
      await store.remove(id);
      return text(`지웠습니다: ${existing.title}`);
    },
  );
}
