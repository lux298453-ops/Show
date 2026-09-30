import { computed, reactive } from 'vue'
import type { InternalAxiosRequestConfig } from 'axios'

type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'
type Replay = (config: InternalAxiosRequestConfig) => Promise<unknown>
interface SaveTicket {
  id: number
  generation: number
  scope: string
  resource: string
  fields: string[]
  data: unknown
  config: InternalAxiosRequestConfig
  replay: Replay
  retryable: boolean
  message?: string
}
interface ScopeState {
  generation: number
  pending: Set<number>
  failures: Map<number, SaveTicket>
  versions: Map<string, number>
  completed: boolean
  localEdits: Map<string, string>
}

const states = new Map<string, ScopeState>()
let sequence = 0

function stateFor(scope: string): ScopeState {
  let state = states.get(scope)
  if (!state) {
    state = reactive({ generation: 0, pending: new Set<number>(), failures: new Map<number, SaveTicket>(), versions: new Map<string, number>(), completed: false, localEdits: new Map<string, string>() })
    states.set(scope, state)
  }
  return state
}

function parseData(data: unknown): unknown {
  if (typeof data === 'string') {
    try { return JSON.parse(data) } catch { return data }
  }
  if (data && typeof data === 'object') return JSON.parse(JSON.stringify(data))
  return data
}

function remainingFields(ticket: SaveTicket): string[] {
  const state = stateFor(ticket.scope)
  if (ticket.generation !== state.generation) return []
  return ticket.fields.filter(field => state.versions.get(`${ticket.resource}:${field}`) === ticket.id)
}

function activeFailures(state: ScopeState): SaveTicket[] {
  return [...state.failures.values()].filter(ticket => remainingFields(ticket).length > 0)
}

/** Observe editing writes only. Generation, deletion, imports and edit-lock heartbeats
 * are excluded: they must never be replayed by the save-status control. */
export function beginSave(config: InternalAxiosRequestConfig, replay: Replay): SaveTicket | undefined {
  const url = (config.url || '').split('?')[0]
  const match = url.match(/^\/projects\/(\d+)\/(.+)$/)
  if (!match) return
  const method = (config.method || 'get').toLowerCase()
  const route = match[2]
  const retryable = method === 'put' && (
    /^pages\/\d+\/(html|position|size|annotation-orders)$/.test(route) ||
    /^annotations\/\d+$/.test(route)
  )
  const manualWrite = method === 'post' && (
    route === 'interactions' || /^pages\/\d+\/elements$/.test(route)
  )
  if (!retryable && !manualWrite) return
  const data = parseData(config.data)
  const isAnnotation = /^annotations\/\d+$/.test(route)
  // Annotation PUTs are partial updates. Track each field so that a later write
  // to one field cannot hide a failure for an unrelated field.
  const fields = isAnnotation && data && typeof data === 'object' && !Array.isArray(data)
    ? Object.keys(data as Record<string, unknown>)
    : ['$value']
  const ticketId = ++sequence
  let resource = `${method}:${url}`
  if (manualWrite && data && typeof data === 'object') {
    const body = data as Record<string, unknown>
    // Independent element creations must not supersede each other's failures.
    // An interaction edit identifies the same logical item by elementId.
    resource += route === 'interactions' && body.elementId != null
      ? `:${body.elementId}` : `:operation-${ticketId}`
  }
  const replayConfig = { ...config, headers: config.headers }
  delete (replayConfig as InternalAxiosRequestConfig & { wfSaveTicket?: SaveTicket }).wfSaveTicket
  const state = stateFor(match[1])
  const ticket: SaveTicket = {
    id: ticketId, generation: state.generation, scope: match[1], resource, fields, data,
    config: replayConfig, replay, retryable,
  }
  state.pending.add(ticket.id)
  state.localEdits.delete(resource)
  fields.forEach(field => state.versions.set(`${resource}:${field}`, ticket.id))
  return ticket
}

export function finishSave(ticket: SaveTicket | undefined, error?: string): void {
  if (!ticket) return
  const state = stateFor(ticket.scope)
  if (ticket.generation !== state.generation) return
  state.pending.delete(ticket.id)
  if (error && remainingFields(ticket).length) {
    state.failures.set(ticket.id, { ...ticket, message: error })
  } else if (!error) {
    state.completed = true
  }
  for (const [id, failed] of state.failures) {
    if (!remainingFields(failed).length) state.failures.delete(id)
  }
}

/** Called only after the user explicitly chooses to leave unsaved edits.
 * Old responses and snapshots cannot re-enter a newly loaded editor session. */
export function discardSaveState(scope: string): void {
  const state = stateFor(scope)
  state.generation++
  state.pending.clear()
  state.failures.clear()
  state.versions.clear()
  state.completed = false
  state.localEdits.clear()
}

export function markLocalEdit(scope: string, pageId: number, error = ''): void {
  stateFor(scope).localEdits.set(`put:/projects/${scope}/pages/${pageId}/html`, error)
}

export function useSaveState(scope: string) {
  const state = stateFor(scope)
  const failures = computed(() => activeFailures(state))
  const pendingCount = computed(() => state.pending.size)
  const localError = computed(() => [...state.localEdits.values()].find(Boolean) || '')
  const status = computed<SaveStatus>(() => {
    if (pendingCount.value) return 'saving'
    if (failures.value.length || localError.value) return 'error'
    if (state.localEdits.size) return 'dirty'
    return state.completed ? 'saved' : 'idle'
  })
  const errorMessage = computed(() => localError.value || failures.value[failures.value.length - 1]?.message || '')
  const canRetry = computed(() => !state.pending.size && failures.value.some(ticket => ticket.retryable && !state.localEdits.has(ticket.resource)))
  const hasManualFailures = computed(() => !!localError.value || failures.value.some(ticket => !ticket.retryable))
  const hasUnsavedChanges = computed(() => state.localEdits.size > 0 || pendingCount.value > 0 || failures.value.length > 0)

  async function retryFailed(): Promise<void> {
    // Recheck immediately before each replay; a newer edit always supersedes an
    // older failed snapshot. POST creation operations cannot be safely retried.
    for (const ticket of [...failures.value]) {
      if (!ticket.retryable) continue
      const fields = remainingFields(ticket)
      if (!fields.length || state.pending.size || state.localEdits.has(ticket.resource)) continue
      let data = ticket.data
      if (/^put:\/projects\/\d+\/annotations\//.test(ticket.resource)) {
        const body = ticket.data as Record<string, unknown>
        data = Object.fromEntries(fields.map(field => [field, body[field]]))
      }
      try {
        await ticket.replay({ ...ticket.config, data })
      } catch {
        // The interceptor records the new failure and leaves it visible.
      }
    }
  }

  return { status, errorMessage, pendingCount, canRetry, hasManualFailures, hasUnsavedChanges, retryFailed }
}
