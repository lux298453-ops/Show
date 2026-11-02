export interface DeletedBinding {
  pageId: number
  elementId: number
  deletedRootUid?: string
  deletedRootElementId?: number
}

/** Match DOM identities, never script strings or nearby geometry, when restoring history. */
export function snapshotIdentityIndex(snapshot: string) {
  const html = snapshot.startsWith('WFBG:') ? snapshot.slice(snapshot.indexOf('\n') + 1) : snapshot
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const uids = new Set<string>(), ids = new Set<number>()
  doc.querySelectorAll('[data-wf-uid],[id],[data-wf-element-id]').forEach(node => {
    for (const key of ['data-wf-uid', 'id']) { const value = node.getAttribute(key); if (value) uids.add(value) }
    const value = node.getAttribute('data-wf-element-id'); if (value) ids.add(Number(value))
  })
  return (record: DeletedBinding) => record.deletedRootUid ? uids.has(record.deletedRootUid)
    : !!record.deletedRootElementId && ids.has(record.deletedRootElementId)
}

/** Serialize deletion and undo requests per page, so a quick undo cannot race the API delete. */
export function createDeletionHistory<T extends DeletedBinding>(actions: {
  remove: (record: T) => Promise<void>
  restore: (record: T) => Promise<void>
}) {
  const records = new Map<string, { record: T; removed: boolean }>()
  const queues = new Map<number, Promise<void>>()
  function enqueue(pageId: number, work: () => Promise<void>) {
    const next = (queues.get(pageId) || Promise.resolve()).catch(() => {}).then(work)
    queues.set(pageId, next)
    return next
  }
  return {
    deleted(pageId: number, batch: T[]) {
      return enqueue(pageId, async () => {
        for (const record of batch) {
          const key = pageId + ':' + record.elementId
          let entry = records.get(key)
          if (!entry || !entry.removed) { entry = { record, removed: false }; records.set(key, entry) }
          if (!entry.removed) { await actions.remove(entry.record); entry.removed = true }
        }
      })
    },
    reconcile(pageId: number, snapshot: string) {
      return enqueue(pageId, async () => {
        const present = snapshotIdentityIndex(snapshot)
        for (const entry of records.values()) {
          if (entry.record.pageId !== pageId) continue
          if (entry.removed && present(entry.record)) {
            await actions.restore(entry.record); entry.removed = false
          } else if (!entry.removed && !present(entry.record)) {
            await actions.remove(entry.record); entry.removed = true
          }
        }
      })
    },
    clear() { records.clear() },
  }
}
