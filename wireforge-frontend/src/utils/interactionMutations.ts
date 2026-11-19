import type { Interaction, InteractionChange, InteractionState, Page } from '../types'

/** Optimistic relation edits share a FIFO with undo/redo. Page documents stay untouched. */
export function createInteractionMutations(options: {
  pages: () => Page[]
  save: (changes: InteractionChange[]) => Promise<InteractionState[]>
  beginPending?: () => () => void
}) {
  let tail = Promise.resolve()
  let sequence = 0
  let disposed = false
  const versions = new Map<number, number>()
  const pending = new Map<number, number>()
  const confirmed = new Map<number, Interaction | undefined>()
  const copy = (value?: Interaction) => value ? { ...value } : undefined
  const element = (change: { pageId: number; elementId: number }) =>
    options.pages().find(page => page.id === change.pageId)?.elements.find(el => el.id === change.elementId)

  function apply(input: InteractionChange[]): Promise<boolean> {
    // Own immutable intent: a later undo or property edit cannot mutate a queued request.
    const changes = input.map(change => ({ ...change }))
    if (!changes.length) return Promise.resolve(false)
    if (new Set(changes.map(change => change.elementId)).size !== changes.length
        || changes.some(change => !element(change))) return Promise.reject(new Error('交互元素已变化，请重新选择'))
    const version = ++sequence
    const finishPending = options.beginPending?.() || (() => {})
    for (const change of changes) {
      const el = element(change)!
      if (!pending.get(el.id)) confirmed.set(el.id, copy(el.interaction))
      pending.set(el.id, (pending.get(el.id) || 0) + 1)
      versions.set(el.id, version)
      el.interaction = {
        id: el.interaction?.id,
        trigger: change.triggerType || 'click', action: change.remove ? 'none' : change.actionType || 'navigate',
        target_page_id: change.remove ? null : change.targetPageId,
        params: change.params ?? el.interaction?.params, source: 'user',
      }
    }
    const current = (change: InteractionChange) => !disposed && versions.get(change.elementId) === version
    const result = tail.then(async () => {
      try {
        const states = await options.save(changes)
        if (states.length !== changes.length || changes.some(change =>
          !states.some(state => state.elementId === change.elementId && state.pageId === change.pageId && state.interaction?.id))) {
          throw new Error('交互保存结果不完整，请重试')
        }
        for (const state of states) {
          const saved = state.interaction
          const value: Interaction = { id: saved.id, trigger: saved.triggerType, action: saved.actionType,
            target_page_id: saved.targetPageId, params: saved.params, source: saved.source }
          confirmed.set(state.elementId, value)
          // An older response must not overwrite an optimistic undo or a newer edit.
          if (current(state) && element(state)) element(state)!.interaction = copy(value)
        }
        return changes.every(current)
      } catch (error) {
        for (const change of changes) {
          const el = element(change)
          if (!current(change) || !el) continue
          const value = copy(confirmed.get(change.elementId))
          if (value) el.interaction = value
          else delete el.interaction
        }
        throw error
      } finally {
        for (const change of changes) {
          const count = (pending.get(change.elementId) || 1) - 1
          if (count) pending.set(change.elementId, count)
          else { pending.delete(change.elementId); confirmed.delete(change.elementId) }
        }
        finishPending()
      }
    })
    // A failed operation rolls back only its own nodes; independent queued edits can continue.
    tail = result.then(() => {}, () => {})
    return result
  }
  return { apply, dispose() { disposed = true }, settled: () => tail }
}
