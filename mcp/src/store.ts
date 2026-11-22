/**
 * Reading and writing the same Firestore documents the Android app does.
 *
 * Arc keeps a private item at users/{uid}/wishlist_items/{id} and a shared one at
 * shared_items/{id}, and the app subscribes to both with snapshot listeners, so a write from here
 * lands on the phone — and on every other member's — within a second without the app doing
 * anything. That is the whole reason this server can exist without touching the app.
 *
 * Admin credentials bypass the security rules entirely, so the membership check that firestore.rules
 * performs for the app and the web is done here by hand. Nothing below reaches a shared document
 * this server's own uid is not a member of.
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
  /** Set when this step stands for another 항목; carried through so an edit never cuts the link. */
  linkedItemId?: string | null;
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
  subItems: { title: string; done: boolean; endDate: string | null; linkedItemId: string | null }[];
  majorCategory: string | null;
  minorCategory: string | null;
  startedAt: string | null;
  endDate: string | null;
  isDone: boolean;
  priority: number;
  /** True when this 할 일 is shared with another account, so a change is not this user's alone. */
  shared: boolean;
}

/** What a write must carry over rather than invent: it replaces the whole document. */
interface Preserved {
  position: number | null;
  ownerUid: string;
  members: string[];
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

  /** Items shared between accounts, outside anybody's subtree because two people own them. */
  private shared() {
    return this.db.collection('shared_items');
  }

  /**
   * Finds a 할 일 by id in whichever collection holds it, or null when this account may not have
   * it. The membership test is the one the rules would have made: admin credentials skip them, so
   * without it any id would reach any shared item in the project.
   */
  private async refFor(id: string) {
    const own = this.items().doc(id);
    if ((await own.get()).exists) return own;
    const shared = this.shared().doc(id);
    const snapshot = await shared.get();
    if (!snapshot.exists) return null;
    const members = snapshot.get('members');
    return Array.isArray(members) && members.includes(this.uid) ? shared : null;
  }

  async list(): Promise<StoredItem[]> {
    const [own, shared] = await Promise.all([
      this.items().get(),
      this.shared().where('members', 'array-contains', this.uid).get(),
    ]);
    // Keyed by id with shared winning, so the moment during a share when the document exists in
    // both collections yields one 할 일 rather than two.
    const byId = new Map<string, StoredItem>();
    for (const doc of own.docs) byId.set(doc.id, this.fromDoc(doc.id, doc.data()));
    for (const doc of shared.docs) byId.set(doc.id, this.fromDoc(doc.id, doc.data()));
    return [...byId.values()];
  }

  async get(id: string): Promise<StoredItem | null> {
    const ref = await this.refFor(id);
    if (!ref) return null;
    const doc = await ref.get();
    return doc.exists ? this.fromDoc(doc.id, doc.data() ?? {}) : null;
  }

  async add(input: ItemInput): Promise<StoredItem> {
    // A 할 일 is always born private; sharing it is a deliberate step, taken in the app or the web.
    const ref = await this.items().add(this.toDoc(input, { position: null, ownerUid: '', members: [] }));
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
    const ref = await this.refFor(id);
    if (!ref) throw new Error(`그런 할 일이 없습니다: ${id}`);
    const snapshot = await ref.get();
    const existing = this.fromDoc(id, snapshot.data() ?? {});

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

    const position = snapshot.get('position');
    const members = snapshot.get('members');
    const ownerUid = snapshot.get('ownerUid');
    await ref.set(this.toDoc(merged, {
      position: typeof position === 'number' ? position : null,
      // Carried through untouched. This replaces the whole document, so dropping them would move a
      // shared 할 일 out from under everyone it is shared with.
      ownerUid: typeof ownerUid === 'string' ? ownerUid : '',
      members: Array.isArray(members) ? members.filter((m): m is string => typeof m === 'string') : [],
    }));
    const saved = await this.get(id);
    if (!saved) throw new Error(`수정한 할 일을 다시 읽지 못했습니다: ${id}`);
    return saved;
  }

  async remove(id: string): Promise<void> {
    const ref = await this.refFor(id);
    if (!ref) throw new Error(`그런 할 일이 없습니다: ${id}`);
    const snapshot = await ref.get();
    const members = snapshot.get('members');
    if (Array.isArray(members) && members.length > 0 && snapshot.get('ownerUid') !== this.uid) {
      // Deleting it would take it away from its owner as well. Leaving a share is done in the app
      // or on the web, where it is clear whose 할 일 is whose.
      throw new Error('공유받은 할 일입니다. 지우는 것은 주인만 할 수 있습니다.');
    }
    await ref.delete();
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

  private toDoc(input: ItemInput, keep: Preserved): Record<string, unknown> {
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
          // Kept even when null: a step linked to another 항목 would otherwise lose the link the
          // first time a tool touched the 할 일 it belongs to.
          linkedItemId: sub.linkedItemId ?? null,
        })),
      majorCategory: blankToNull(input.majorCategory),
      minorCategory: blankToNull(input.minorCategory),
      // 시작일 defaults to today the way the editor does when it opens on a new 항목.
      startedAt: this.date(input.startedAt) ?? startOfDayMillis(toIsoDate(Date.now(), this.timeZone), this.timeZone),
      endDate: this.date(input.endDate),
      isDone: input.isDone ?? false,
      priority: input.priority ?? DEFAULT_PRIORITY,
      // Creation order. Kept on update so an edited item doesn't jump to the end of the list.
      position: keep.position ?? Date.now(),
      ownerUid: keep.ownerUid,
      members: keep.members,
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
        linkedItemId: typeof sub?.linkedItemId === 'string' && sub.linkedItemId ? sub.linkedItemId : null,
      })),
      majorCategory: typeof data.majorCategory === 'string' ? data.majorCategory : null,
      minorCategory: typeof data.minorCategory === 'string' ? data.minorCategory : null,
      startedAt: iso(data.startedAt),
      endDate: iso(data.endDate),
      isDone: data.isDone === true,
      priority: typeof data.priority === 'number' ? data.priority : DEFAULT_PRIORITY,
      shared: Array.isArray(data.members) && data.members.length > 0,
    };
  }
}
