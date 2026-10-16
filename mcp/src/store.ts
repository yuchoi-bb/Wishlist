/**
 * Reading and writing the same Firestore documents the Android app does.
 *
 * Arc keeps every item at users/{uid}/wishlist_items/{id}, and the app subscribes with a snapshot
 * listener, so a write from here lands on the phone within a second without the app doing anything
 * — that is the whole reason this server can exist without touching the app.
 *
 * The field names below are not a schema this server chose; they are what
 * FirestoreArcRepository.toFirestoreMap writes and toArcItem reads. A name that drifts from the
 * app's is silently dropped on read, so they stay in step with that file.
 */
import { applicationDefault, cert, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore, type Firestore } from 'firebase-admin/firestore';
import { startOfDayMillis, toIsoDate } from './dates.js';

export interface SubItemInput {
  title: string;
  done?: boolean;
  endDate?: string | null;
}

export interface ItemInput {
  title: string;
  memo?: string | null;
  subItems?: SubItemInput[];
  majorCategory?: string | null;
  minorCategory?: string | null;
  startedAt?: string | null;
  endDate?: string | null;
  isDone?: boolean;
  priority?: number;
}

export interface StoredItem {
  id: string;
  title: string;
  memo: string | null;
  subItems: { title: string; done: boolean; endDate: string | null }[];
  majorCategory: string | null;
  minorCategory: string | null;
  startedAt: string | null;
  endDate: string | null;
  isDone: boolean;
  priority: number;
}

/** 보통 — what the app's editor starts a new 항목 on (ArcItem.DEFAULT_PRIORITY). */
const DEFAULT_PRIORITY = 2;

export class ArcStore {
  private readonly db: Firestore;

  constructor(
    private readonly uid: string,
    private readonly timeZone: string,
    credentialsJson?: string,
  ) {
    if (getApps().length === 0) {
      initializeApp({
        credential: credentialsJson ? cert(JSON.parse(credentialsJson)) : applicationDefault(),
      });
    }
    this.db = getFirestore();
  }

  private items() {
    return this.db.collection('users').doc(this.uid).collection('wishlist_items');
  }

  async list(): Promise<StoredItem[]> {
    const snapshot = await this.items().get();
    return snapshot.docs.map((doc) => this.fromDoc(doc.id, doc.data()));
  }

  async get(id: string): Promise<StoredItem | null> {
    const doc = await this.items().doc(id).get();
    return doc.exists ? this.fromDoc(doc.id, doc.data() ?? {}) : null;
  }

  async add(input: ItemInput): Promise<StoredItem> {
    const ref = await this.items().add(this.toDoc(input, null));
    const created = await this.get(ref.id);
    if (!created) throw new Error('방금 만든 할 일을 다시 읽지 못했습니다.');
    return created;
  }

  /**
   * Merges onto what is stored rather than replacing it, so naming one field in a request cannot
   * quietly blank the others. The app writes whole documents with `set`, which is safe there
   * because its editor always holds the complete item; a tool call carries only what was mentioned.
   */
  async update(id: string, input: Partial<ItemInput>): Promise<StoredItem> {
    const existing = await this.get(id);
    if (!existing) throw new Error(`그런 할 일이 없습니다: ${id}`);

    const merged: ItemInput = {
      title: input.title ?? existing.title,
      memo: input.memo !== undefined ? input.memo : existing.memo,
      subItems: input.subItems ?? existing.subItems,
      majorCategory: input.majorCategory !== undefined ? input.majorCategory : existing.majorCategory,
      minorCategory: input.minorCategory !== undefined ? input.minorCategory : existing.minorCategory,
      startedAt: input.startedAt !== undefined ? input.startedAt : existing.startedAt,
      endDate: input.endDate !== undefined ? input.endDate : existing.endDate,
      isDone: input.isDone ?? existing.isDone,
      priority: input.priority ?? existing.priority,
    };

    const position = (await this.items().doc(id).get()).get('position');
    await this.items().doc(id).set(this.toDoc(merged, typeof position === 'number' ? position : null));
    const saved = await this.get(id);
    if (!saved) throw new Error(`수정한 할 일을 다시 읽지 못했습니다: ${id}`);
    return saved;
  }

  async remove(id: string): Promise<void> {
    await this.items().doc(id).delete();
  }

  /** The 대분류/중분류 already in use, so an assistant files under them instead of coining new ones. */
  async categories(): Promise<{ majorCategory: string; minorCategories: string[] }[]> {
    const grouped = new Map<string, Set<string>>();
    for (const item of await this.list()) {
      const major = item.majorCategory?.trim();
      if (!major) continue;
      const minors = grouped.get(major) ?? new Set<string>();
      const minor = item.minorCategory?.trim();
      if (minor) minors.add(minor);
      grouped.set(major, minors);
    }
    return [...grouped.entries()]
      .map(([majorCategory, minors]) => ({
        majorCategory,
        minorCategories: [...minors].sort((a, b) => a.localeCompare(b, 'ko')),
      }))
      .sort((a, b) => a.majorCategory.localeCompare(b.majorCategory, 'ko'));
  }

  private date(iso: string | null | undefined): number | null {
    const trimmed = iso?.trim();
    return trimmed ? startOfDayMillis(trimmed, this.timeZone) : null;
  }

  private toDoc(input: ItemInput, keepPosition: number | null): Record<string, unknown> {
    const blankToNull = (value: string | null | undefined): string | null => {
      const trimmed = value?.trim();
      return trimmed ? trimmed : null;
    };
    return {
      title: input.title.trim(),
      memo: blankToNull(input.memo),
      subItems: (input.subItems ?? [])
        .filter((sub) => sub.title.trim().length > 0)
        .map((sub) => ({
          title: sub.title.trim(),
          done: sub.done ?? false,
          endDate: this.date(sub.endDate),
        })),
      majorCategory: blankToNull(input.majorCategory),
      minorCategory: blankToNull(input.minorCategory),
      // 시작일 defaults to today the way the editor does when it opens on a new 항목.
      startedAt: this.date(input.startedAt) ?? startOfDayMillis(toIsoDate(Date.now(), this.timeZone), this.timeZone),
      endDate: this.date(input.endDate),
      isDone: input.isDone ?? false,
      priority: input.priority ?? DEFAULT_PRIORITY,
      // Creation order. Kept on update so an edited item doesn't jump to the end of the list.
      position: keepPosition ?? Date.now(),
    };
  }

  private fromDoc(id: string, data: FirebaseFirestore.DocumentData): StoredItem {
    const iso = (value: unknown): string | null =>
      typeof value === 'number' ? toIsoDate(value, this.timeZone) : null;
    const subItems = Array.isArray(data.subItems) ? data.subItems : [];
    return {
      id,
      title: typeof data.title === 'string' ? data.title : '',
      memo: typeof data.memo === 'string' ? data.memo : null,
      subItems: subItems.map((sub: Record<string, unknown>) => ({
        title: typeof sub?.title === 'string' ? sub.title : '',
        done: sub?.done === true,
        endDate: iso(sub?.endDate),
      })),
      majorCategory: typeof data.majorCategory === 'string' ? data.majorCategory : null,
      minorCategory: typeof data.minorCategory === 'string' ? data.minorCategory : null,
      startedAt: iso(data.startedAt),
      endDate: iso(data.endDate),
      isDone: data.isDone === true,
      priority: typeof data.priority === 'number' ? data.priority : DEFAULT_PRIORITY,
    };
  }
}
