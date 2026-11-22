/**
 * The 할 일 editor, in the same field order the app's dialog uses:
 * 할 일 → 우선순위 → 최종 종료일 → 세부항목 → 분류 → 메모, with 시작일 and 완료 at the end where
 * they are rarely touched.
 *
 * It only edits a plain object and hands it back; writing to Firestore is the caller's job.
 */

const pad = (n) => String(n).padStart(2, '0');

/**
 * Dates are stored as epoch millis at the local start of day, which is what the phone writes
 * through toStartOfDayMillis(). `new Date(y, m, d)` is local midnight, so the browser's own zone
 * does the same job here without any conversion.
 */
export const isoToMillis = (iso) => {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).getTime();
};

export const millisToIso = (millis) => {
  if (millis == null) return '';
  const d = new Date(millis);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

const blankItem = () => ({
  id: '', title: '', memo: null, subItems: [], majorCategory: null, minorCategory: null,
  startedAt: isoToMillis(millisToIso(Date.now())), endDate: null, isDone: false, priority: 2,
  // Born private: sharing it is a separate, deliberate step, and needs a saved document anyway.
  position: Date.now(), ownerUid: '', members: [],
});

/** Read-only as IDL properties — input.list hands back the datalist, so assigning to it throws. */
const ATTRIBUTE_ONLY = new Set(['list']);

const el = (tag, props = {}, children = []) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (ATTRIBUTE_ONLY.has(key)) node.setAttribute(key, value);
    else node[key] = value;
  }
  node.append(...children);
  return node;
};

/**
 * Opens the editor on `item`, or on a new 할 일 when it is null.
 *
 * Resolves with {action:'save', item}, {action:'delete'}, {action:'share', item, uid},
 * {action:'unshare', item} or null when nothing was decided. The form is compared against the state
 * it opened in, so closing an untouched editor never asks.
 */
export function showEditor(item, {
  majorCategories = [], minorCategoriesFor = () => [], linkCandidates = [],
  // Starts wherever 완료항목 숨기기 is set for the table, so the editor opens showing what the
  // person is already looking at — and can be flipped for this one edit without changing that.
  hideCompletedSubItems = false,
  // The signed-in account, which decides whether a shared 할 일 is this user's to administer.
  myUid = '',
} = {}) {
  const draft = item ? structuredClone(item) : blankItem();
  const isNew = !draft.id;
  const members = draft.members ?? [];
  const sharedAlready = members.length > 0;
  const others = members.filter((m) => m !== myUid);
  // A guest can edit a shared 할 일 but not delete it: it is not theirs to take away from everyone,
  // and Firestore would reject the write anyway.
  const mine = !sharedAlready || draft.ownerUid === myUid;

  const dialog = document.getElementById('editor');
  const form = document.getElementById('editor-form');
  dialog.querySelector('h2').textContent = isNew ? '새 할 일' : '할 일 수정';
  document.getElementById('editor-delete').hidden = isNew || !mine;
  form.replaceChildren();

  const field = (label, control) => el('label', { className: 'field' }, [el('span', { textContent: label }), control]);

  const title = el('input', { type: 'text', value: draft.title, required: true, autocomplete: 'off' });
  form.append(field('할 일', title));

  const priority = el('div', { className: 'segmented' });
  for (const [value, text] of [[1, '높음'], [2, '보통'], [3, '여유']]) {
    const button = el('button', { type: 'button', textContent: text });
    button.dataset.value = String(value);
    button.ariaPressed = String(draft.priority === value);
    button.onclick = () => {
      draft.priority = value;
      [...priority.children].forEach((b) => { b.ariaPressed = String(b.dataset.value === String(value)); });
    };
    priority.append(button);
  }
  form.append(field('우선순위', priority));

  const endDate = el('input', { type: 'date', value: millisToIso(draft.endDate) });
  form.append(field('최종 종료일 (목표일)', endDate));

  // 세부항목 ---------------------------------------------------------------
  const hideDoneSubs = el('input', { type: 'checkbox', checked: hideCompletedSubItems });
  const hiddenNote = el('span', { className: 'hidden-note' });
  const subList = el('div', { className: 'sub-list' });

  /**
   * Hiding only changes what is drawn. Every row still carries its real index into draft.subItems,
   * so a hidden step is never dropped on save, and ↑ ↓ step between *visible* neighbours rather
   * than by one slot — otherwise a move would look like it did nothing when a completed step sat
   * in between.
   */
  const renderSubs = () => {
    subList.replaceChildren();
    const shown = draft.subItems
      .map((sub, index) => index)
      .filter((index) => !hideDoneSubs.checked || !draft.subItems[index].done);

    const buried = draft.subItems.length - shown.length;
    hiddenNote.textContent = buried > 0 ? `완료 ${buried}개 숨김` : '';

    shown.forEach((index, position) => {
      const sub = draft.subItems[index];
      const done = el('input', { type: 'checkbox', checked: sub.done, ariaLabel: '완료' });
      done.onchange = () => { sub.done = done.checked; };
      const name = el('input', {
        type: 'text', value: sub.title,
        placeholder: sub.linkedItemId ? '연결된 할 일' : '세부항목',
      });
      if (sub.linkedItemId) name.title = '다른 할 일로 연결된 세부항목입니다.';
      name.oninput = () => { sub.title = name.value; };
      const date = el('input', { type: 'date', value: millisToIso(sub.endDate) });
      date.onchange = () => { sub.endDate = isoToMillis(date.value); };
      const remove = el('button', { type: 'button', textContent: '✕', title: '세부항목 삭제', className: 'icon' });
      remove.onclick = () => { draft.subItems.splice(index, 1); renderSubs(); };

      // Order is the order they are stored in, which is the order the table shows them under their
      // 항목, so moving one here moves it everywhere.
      const move = (to) => {
        const [moved] = draft.subItems.splice(index, 1);
        draft.subItems.splice(to, 0, moved);
        renderSubs();
      };
      const up = el('button', {
        type: 'button', textContent: '↑', title: '위로', className: 'icon',
        disabled: position === 0,
      });
      up.onclick = () => move(shown[position - 1]);
      const down = el('button', {
        type: 'button', textContent: '↓', title: '아래로', className: 'icon',
        disabled: position === shown.length - 1,
      });
      down.onclick = () => move(shown[position + 1]);

      subList.append(el('div', { className: 'sub-row' }, [done, name, date, up, down, remove]));
    });
  };
  hideDoneSubs.onchange = renderSubs;
  renderSubs();

  const newSub = el('input', { type: 'text', placeholder: '세부항목 추가', autocomplete: 'off' });
  const addSub = el('button', { type: 'button', textContent: '추가' });

  /**
   * Links a step to a 할 일 that already exists rather than naming a new one. The target keeps its
   * place in the main table — this adds a way in, not a second kind of thing — and its name is
   * copied so the row still reads sensibly before it loads, or once it is gone.
   */
  const linkSub = el('button', { type: 'button', textContent: '할 일 연결' });
  linkSub.onclick = () => {
    // Linking an item to itself would make a loop with nowhere to go back to.
    const choices = linkCandidates.filter((c) => c.id !== draft.id);
    if (choices.length === 0) { alert('연결할 다른 할 일이 없습니다.'); return; }
    const lines = choices.map((c, i) => {
      const category = [c.majorCategory, c.minorCategory].filter(Boolean).join(' › ');
      return `${i + 1}. ${c.title}${category ? `  (${category})` : ''}`;
    });
    const answer = prompt(`연결할 할 일의 번호를 입력하세요.\n\n${lines.join('\n')}`);
    const picked = choices[Number(answer) - 1];
    if (!picked) return;
    draft.subItems.push({
      title: picked.title,
      done: false,
      endDate: picked.endDate ?? isoToMillis(endDate.value),
      linkedItemId: picked.id,
    });
    renderSubs();
  };
  const commitSub = () => {
    const text = newSub.value.trim();
    if (!text) return;
    // A new 세부항목 starts from the 항목's 최종 종료일, as the app's editor does, so the table has
    // a real 완료예정일 to sort by instead of an empty cell.
    draft.subItems.push({ title: text, done: false, endDate: isoToMillis(endDate.value) });
    newSub.value = '';
    renderSubs();
  };
  addSub.onclick = commitSub;
  newSub.onkeydown = (e) => { if (e.key === 'Enter') { e.preventDefault(); commitSub(); } };

  // Setting the 항목's date fills in any 세부항목 that still has none.
  endDate.onchange = () => {
    const value = isoToMillis(endDate.value);
    if (value == null) return;
    draft.subItems.forEach((sub) => { if (sub.endDate == null) sub.endDate = value; });
    renderSubs();
  };

  form.append(
    el('div', { className: 'field' }, [
      el('div', { className: 'field-head' }, [
        el('span', { textContent: '세부항목' }),
        hiddenNote,
        el('label', { className: 'hide-done' }, [hideDoneSubs, el('span', { textContent: '완료 숨기기' })]),
      ]),
      subList,
      el('div', { className: 'sub-add' }, [newSub, addSub, linkSub]),
    ]),
  );

  // 분류 -------------------------------------------------------------------
  const majorList = el('datalist', { id: 'major-options' },
    majorCategories.map((name) => el('option', { value: name })));
  const minorList = el('datalist', { id: 'minor-options' });
  const major = el('input', { type: 'text', value: draft.majorCategory ?? '', list: 'major-options', autocomplete: 'off' });
  const minor = el('input', { type: 'text', value: draft.minorCategory ?? '', list: 'minor-options', autocomplete: 'off' });
  const refreshMinors = () => {
    minorList.replaceChildren(...minorCategoriesFor(major.value.trim()).map((n) => el('option', { value: n })));
  };
  major.oninput = refreshMinors;
  refreshMinors();
  form.append(field('대분류', major), majorList, field('중분류', minor), minorList);

  const memo = el('textarea', { value: draft.memo ?? '', rows: 3 });
  form.append(field('메모', memo));

  const startedAt = el('input', { type: 'date', value: millisToIso(draft.startedAt) });
  form.append(field('시작일', startedAt));

  const isDone = el('input', { type: 'checkbox', checked: draft.isDone });
  form.append(el('label', { className: 'field inline' }, [isDone, el('span', { textContent: '완료' })]));

  // 공유 ------------------------------------------------------------------
  // Sharing needs a document to share, so it waits until the 할 일 has been saved once.
  const shareButton = el('button', { type: 'button', textContent: 'UID로 공유' });
  const unshareButton = el('button', {
    type: 'button', textContent: mine ? '공유 중단' : '공유에서 나가기',
  });
  if (!isNew) {
    const note = (text) => el('p', { className: 'share-note', textContent: text });
    const status = !sharedAlready ? '나만 보는 할 일입니다.'
      : !mine ? '다른 사람이 나와 공유한 할 일입니다. 내용은 함께 고칠 수 있습니다.'
        : others.length === 0 ? '공유 중이지만 상대가 모두 나갔습니다.'
          : `다른 ${others.length}명과 공유 중입니다. 서로의 체크가 바로 반영됩니다.`;
    form.append(el('div', { className: 'field' }, [
      el('span', { textContent: '공유' }),
      note(status),
      // Enough of each uid to recognise, since that is all the owner has to go on when checking
      // they shared with the person they meant to.
      ...(mine ? others.map((m) => note(`· ${m.slice(0, 12)}…`)) : []),
      el('div', { className: 'share-row' }, [
        ...(mine ? [shareButton] : []),
        ...(sharedAlready ? [unshareButton] : []),
      ]),
    ]));
  }

  const collect = () => ({
    ...draft,
    title: title.value.trim(),
    memo: memo.value.trim() || null,
    subItems: draft.subItems.filter((s) => s.title.trim()).map((s) => ({ ...s, title: s.title.trim() })),
    majorCategory: major.value.trim() || null,
    minorCategory: minor.value.trim() || null,
    startedAt: isoToMillis(startedAt.value) ?? draft.startedAt,
    endDate: isoToMillis(endDate.value),
    isDone: isDone.checked,
  });

  const opened = JSON.stringify(collect());

  return new Promise((resolve) => {
    const finish = (result) => {
      dialog.close();
      dialog.onclose = null;
      resolve(result);
    };
    document.getElementById('editor-save').onclick = () => {
      const edited = collect();
      if (!edited.title) { title.focus(); return; }
      finish({ action: 'save', item: edited });
    };
    document.getElementById('editor-delete').onclick = () => {
      if (confirm(`"${draft.title}"을(를) 지울까요? 모든 기기에서 사라집니다.`)) finish({ action: 'delete' });
    };
    /**
     * A uid rather than an email address because that is the only name a client can use: Firestore
     * cannot look an account up by email, and the lookup that could needs a server. Nothing here
     * can check it either, so a typo is accepted and simply shares with nobody.
     */
    shareButton.onclick = () => {
      const entered = prompt(
        '상대방이 Arc에서 「내 UID 복사」로 보내준 값을 넣어주세요.\n\n'
        + 'UID가 틀리면 아무에게도 보이지 않습니다. 그럴 때는 공유를 중단하고 다시 공유해주세요.',
      );
      const uid = entered?.trim();
      if (!uid || uid === myUid || uid.length < 8 || /\s/.test(uid)) return;
      // The form as it stands goes with the share: the write carries the whole document, so this
      // saves the sitting's edits rather than leaving them behind.
      finish({ action: 'share', item: collect(), uid });
    };
    unshareButton.onclick = () => finish({ action: 'unshare', item: collect() });
    document.getElementById('editor-cancel').onclick = () => {
      // Esc and the 취소 button both come through here, so nothing is lost silently.
      if (JSON.stringify(collect()) !== opened && !confirm('저장하지 않으면 수정한 내용이 사라집니다. 닫을까요?')) return;
      finish(null);
    };
    dialog.onclose = () => resolve(null);
    dialog.showModal();
    title.focus();
  });
}
