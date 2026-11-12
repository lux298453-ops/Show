import { computed, onMounted, onBeforeUnmount, reactive, watch } from 'vue'
import { projectApi } from '../api/project'
import type { AiGeneratedImage } from '../api/project'
import type { Page } from '../types'
import { isTextEditingTarget, patchAiElement, type AiElementTarget } from '../utils/aiAdjustment'

interface AdjustmentRequest { requestId?: string; instruction: string; html: string; element?: AiElementTarget; conversationContext: string }
interface AdjustmentOperation { beforeHtml: string; afterHtml: string; messageId?: string }
export interface AdjustmentMessage {
  id: string
  projectId: number
  pageId: number
  role: 'user' | 'assistant'
  time: string
  content?: string
  summary?: string
  diffList?: string[]
  generatedImages?: AiGeneratedImage[]
  originalHtml?: string
  modifiedHtml?: string
  isElementMode?: boolean
  isCommitted?: boolean
  isReverted?: boolean
  unchanged?: boolean
}
interface AdjustmentSession {
  projectId: number
  pageId: number
  initialHtml: string
  baseHtml: string
  draftHtml: string | null
  input: string
  error: string
  messages: AdjustmentMessage[]
  undoStack: AdjustmentOperation[]
  failedRequest?: AdjustmentRequest
  loading: boolean
  saving: boolean
  conflict: boolean
  revision: number
}
interface Props {
  projectId: number
  currentPage: Page | null
  currentDraftHtml?: string | null
  selectedElement?: AiElementTarget | null
  isOpen: boolean
}
interface Events {
  preview(pageId: number, html: string): void
  revert(pageId: number): void
  commit(pageId: number, html: string): void
  toast(message: string): void
}

const newId = () => globalThis.crypto?.randomUUID?.() || `${Date.now()}_${Math.random().toString(36).slice(2)}`
const time = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
const key = (projectId: number, pageId: number) => `${projectId}:${pageId}`
const storageKey = (session: AdjustmentSession) => `wf_ai_adjust_v1:${key(session.projectId, session.pageId)}`

/** One independent session per artboard. Async callbacks keep their original session reference. */
export function useAiAdjustment(props: Props, events: Events) {
  const sessions = reactive(new Map<string, AdjustmentSession>())
  const controllers = new Map<string, AbortController>()
  const activeRequests = new Map<string, AdjustmentRequest>()
  const dirtySessions = new Set<AdjustmentSession>()
  const stopSessionWatches: Array<() => void> = []
  const empty = reactive<AdjustmentSession>({ projectId: 0, pageId: 0, initialHtml: '', baseHtml: '', draftHtml: null,
    input: '', error: '', messages: [], undoStack: [], loading: false, saving: false, conflict: false, revision: 0 })
  const current = computed(() => props.currentPage ? sessions.get(key(props.projectId, props.currentPage.id)) || empty : empty)
  let persistTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  const clientId = `ai_${newId()}`

  function persist() {
    clearTimeout(persistTimer)
    const pending = [...dirtySessions]
    dirtySessions.clear()
    for (const session of pending) {
      try {
        // Intern HTML snapshots: consecutive versions share the same source strings.
        const html: string[] = []
        const encoded = JSON.stringify({ ...session, loading: false, saving: false }, (name, value) => {
          if (['initialHtml', 'baseHtml', 'draftHtml', 'originalHtml', 'modifiedHtml', 'beforeHtml', 'afterHtml', 'html'].includes(name) && typeof value === 'string') {
            let index = html.indexOf(value)
            if (index < 0) index = html.push(value) - 1
            return { htmlRef: index }
          }
          return value
        })
        sessionStorage.setItem(storageKey(session), JSON.stringify({ data: JSON.parse(encoded), html }))
      } catch {
        session.error = '本地草稿暂时无法备份，请应用修改后再刷新或退出'
      }
    }
  }

  function schedulePersist(session: AdjustmentSession) {
    dirtySessions.add(session)
    clearTimeout(persistTimer)
    persistTimer = setTimeout(persist, 250)
  }

  watch(() => [props.projectId, props.currentPage?.id], () => {
    const page = props.currentPage
    if (!page) return
    const id = key(props.projectId, page.id)
    if (!sessions.has(id)) {
      let restored: AdjustmentSession | undefined
      try {
        const stored = sessionStorage.getItem(`wf_ai_adjust_v1:${id}`)
        if (stored) {
          const { data, html } = JSON.parse(stored)
          restored = JSON.parse(JSON.stringify(data), (_name, value) => value && typeof value === 'object' && 'htmlRef' in value ? html[value.htmlRef] : value)
          if (!restored || restored.projectId !== props.projectId || restored.pageId !== page.id || !Array.isArray(restored.messages)) restored = undefined
        }
      } catch { /* Invalid or unavailable local storage does not block editing. */ }
      const base = page.html_content || ''
      const session: AdjustmentSession = restored || { projectId: props.projectId, pageId: page.id,
        initialHtml: base, baseHtml: base, draftHtml: props.currentDraftHtml || null, input: '', error: '', messages: [], undoStack: [],
        loading: false, saving: false, conflict: false, revision: 0 }
      session.loading = false
      session.saving = false
      session.undoStack ||= []
      session.conflict = session.baseHtml !== base && session.draftHtml !== null
      if (!session.conflict) session.baseHtml = base
      session.messages.forEach(message => {
        if (message.role === 'assistant') message.isCommitted = !message.unchanged && message.modifiedHtml === base
      })
      if (session.conflict) session.error = '画板已在其他窗口更新。本地版本已保留，请丢弃草稿后基于最新画板重新调整'
      sessions.set(id, session)
      const storedSession = sessions.get(id)!
      stopSessionWatches.push(watch(storedSession, () => schedulePersist(storedSession), { deep: true, flush: 'sync' }))
      schedulePersist(storedSession)
    }
    const session = sessions.get(id)!
    if (session.draftHtml) events.preview(page.id, session.draftHtml)
  }, { immediate: true })

  const inputPrompt = computed({ get: () => current.value.input, set: value => { current.value.input = value } })
  const errorMessage = computed({ get: () => current.value.error, set: value => { current.value.error = value } })
  const messages = computed(() => current.value.messages)
  const historySnapshots = computed(() => messages.value.filter(m => m.role === 'assistant' && !m.unchanged && m.modifiedHtml))
  const loading = computed(() => current.value.loading)
  const saving = computed(() => current.value.saving)
  const canRetry = computed(() => !!current.value.failedRequest && !loading.value && !saving.value)
  const hasDraft = computed(() => current.value.draftHtml !== null)
  const canRestoreSessionInitial = computed(() => !!current.value.pageId && source(current.value) !== current.value.initialHtml)
  function source(session: AdjustmentSession) { return session.draftHtml ?? session.baseHtml }
  function preview(session: AdjustmentSession, html: string) {
    session.draftHtml = html === session.baseHtml ? null : html
    session.revision++
    if (session.draftHtml === null) events.revert(session.pageId)
    else events.preview(session.pageId, html)
  }
  function findSession(message: AdjustmentMessage) { return sessions.get(key(message.projectId, message.pageId)) }
  function lastOperation(session: AdjustmentSession) { return session.undoStack[session.undoStack.length - 1] }

  async function sendRequest(session: AdjustmentSession, request: AdjustmentRequest) {
    if (session.loading || session.saving || session.conflict) return
    if (request.html !== source(session)) {
      session.error = '画板已变化，请基于当前预览重新发送调整需求'
      return
    }
    session.error = ''
    // Backfill requests restored from older sessions, then retain the same ID on retry.
    request.requestId ||= newId()
    // Keep the in-flight request recoverable even when the tab refreshes before a response.
    session.failedRequest = request
    session.loading = true
    const revision = session.revision
    const controller = new AbortController()
    const id = key(session.projectId, session.pageId)
    controllers.set(id, controller)
    activeRequests.set(id, request)
    try {
      const element = request.element
      let html: string
      let summary: string
      let diffList: string[]
      let unchanged: boolean
      let generatedImages: AiGeneratedImage[]
      if (element) {
        const result = await projectApi.aiAdjustElement(session.projectId, session.pageId, {
          elementId: element.id, elementTag: element.tag, elementLabel: element.label, elementHtml: element.html,
          requestId: request.requestId, instruction: request.instruction, layoutContext: element.layoutContext, conversationContext: request.conversationContext,
        }, controller.signal)
        if (controller.signal.aborted || disposed) return
        if (result.elementId !== element.id) throw new Error('返回结果的元素标识不一致，未修改画板')
        unchanged = result.action === 'NO_CHANGE' || result.changed === false
        if (!unchanged && result.action !== 'REPLACE' && result.action !== 'DELETE') throw new Error('AI 返回了无效的元素操作')
        html = unchanged ? request.html : patchAiElement(request.html, element, result.action as 'REPLACE' | 'DELETE', result.modifiedSnippet)
        summary = result.summary
        generatedImages = result.generatedImages || []
        diffList = unchanged ? [] : [result.action === 'DELETE' ? `[删除] ${element.label}` : `[修改] ${element.label}`]
      } else {
        const result = await projectApi.aiAdjust(session.projectId, session.pageId, {
          requestId: request.requestId, instruction: request.instruction, contextHtml: request.html, conversationContext: request.conversationContext,
        }, controller.signal)
        if (controller.signal.aborted || disposed) return
        if (result.pageId !== session.pageId) throw new Error('返回结果的画板标识不一致，未修改画板')
        if (!result.modifiedHtml) throw new Error('AI 未能生成有效的画板修改方案')
        html = result.modifiedHtml
        summary = result.summary
        generatedImages = result.generatedImages || []
        diffList = result.diffList || []
        unchanged = result.changed === false || html === request.html
      }
      const messageId = newId()
      session.messages.push({ id: messageId, projectId: session.projectId, pageId: session.pageId, role: 'assistant', time: time(),
        summary: unchanged ? (summary || '无需修改') : summary, diffList, originalHtml: request.html, modifiedHtml: html,
        isElementMode: !!element, isCommitted: false, isReverted: false, unchanged, generatedImages })
      session.failedRequest = undefined
      if (!unchanged && revision === session.revision) {
        preview(session, html)
        session.undoStack.push({ beforeHtml: request.html, afterHtml: html, messageId })
      }
      else if (!unchanged) session.error = '请求期间画板已变化。结果已保留，未覆盖当前预览'
    } catch (error) {
      if (!disposed && controllers.get(id) === controller) {
        session.failedRequest = request
        session.error = controller.signal.aborted ? '已停止等待，可重试此请求' : (error instanceof Error ? error.message : '调整失败，请重试')
      }
    } finally {
      if (controllers.get(id) === controller) {
        controllers.delete(id)
        activeRequests.delete(id)
        session.loading = false
      }
    }
  }

  async function handleSend() {
    const session = current.value
    const instruction = session.input.trim()
    if (!instruction || !session.pageId || session.loading || session.saving) return
    if (session.conflict) { session.error = '请先丢弃过期草稿，再基于最新画板调整'; return }
    const conversationContext = session.messages.slice(-12).map(m => `${m.role}: ${m.content || m.summary || ''}`).join('\n').slice(-6000)
    const request = { requestId: newId(), instruction, html: source(session), element: props.selectedElement ? { ...props.selectedElement } : undefined, conversationContext }
    session.messages.push({ id: newId(), projectId: session.projectId, pageId: session.pageId, role: 'user', content: instruction, time: time() })
    session.input = ''
    await sendRequest(session, request)
  }
  function handleRetry() {
    const session = current.value
    if (session.failedRequest) return sendRequest(session, session.failedRequest)
  }
  function handleCancel() {
    const session = current.value
    const id = key(session.projectId, session.pageId)
    session.failedRequest = activeRequests.get(id)
    controllers.get(id)?.abort()
    controllers.delete(id)
    activeRequests.delete(id)
    session.error = '已停止等待，可重试此请求'
    // Release UI immediately; a late response is ignored even if a transport cannot abort.
    session.loading = false
  }
  function handleRevert(message: AdjustmentMessage) {
    const session = findSession(message)
    if (!session || session.saving || session.loading || !message.originalHtml) return
    const operation = lastOperation(session)
    if (!operation || source(session) !== message.modifiedHtml || operation.messageId !== message.id) {
      session.error = '当前预览已包含后续修改，请先撤回后续版本，或在版本记录中恢复此版本'
      return
    }
    return undo(session, operation, message)
  }

  function undo(session: AdjustmentSession, operation: AdjustmentOperation, message?: AdjustmentMessage) {
    const complete = () => {
      if (lastOperation(session) === operation) session.undoStack.pop()
      if (message) message.isReverted = true
    }
    if (source(session) === session.baseHtml) return save(session, operation.beforeHtml, complete)
    preview(session, operation.beforeHtml)
    complete()
  }

  async function save(session: AdjustmentSession, html: string, afterSave?: () => void) {
    if (session.saving || session.loading || session.conflict || !html) return
    session.saving = true
    session.error = ''
    const expectedHtml = session.baseHtml
    try {
      await projectApi.saveHtml(session.projectId, session.pageId, html, clientId, expectedHtml)
      session.baseHtml = html
      session.draftHtml = null
      session.revision++
      // Exactly one historical result represents the version now stored on the server.
      session.messages.forEach(m => {
        if (m.role === 'assistant') {
          m.isCommitted = !m.unchanged && m.modifiedHtml === html
          if (m.isCommitted) m.isReverted = false
        }
      })
      afterSave?.()
      if (!disposed) events.commit(session.pageId, html)
    } catch (error) {
      session.error = `保存失败：${error instanceof Error ? error.message : '网络异常'}。本地版本仍保留`
    } finally {
      session.saving = false
      if (disposed) { dirtySessions.add(session); persist() }
    }
  }
  function handleCommit(message: AdjustmentMessage) {
    const session = findSession(message)
    if (session && message.modifiedHtml && !message.unchanged) return saveVersion(session, message.modifiedHtml, message.id)
  }
  function saveVersion(session: AdjustmentSession, html: string, messageId?: string) {
    const beforeHtml = source(session)
    return save(session, html, () => {
      if (beforeHtml !== html) session.undoStack.push({ beforeHtml, afterHtml: html, messageId })
    })
  }
  function handleSaveDraft() { const s = current.value; if (s.draftHtml) return saveVersion(s, s.draftHtml) }
  function handleRevertCommitted(message: AdjustmentMessage) { return handleRevert(message) }
  function handleReapply(message: AdjustmentMessage) { return handleCommit(message) }
  function handleRollbackToSnapshot(message: AdjustmentMessage) { return handleCommit(message) }
  function handleRestoreSessionInitial() { return saveVersion(current.value, current.value.initialHtml) }
  function handleDiscardDraft() {
    const session = current.value
    if (session.loading || session.saving) return
    session.baseHtml = props.currentPage?.html_content || session.baseHtml
    session.conflict = false
    session.error = ''
    session.failedRequest = undefined
    while (session.undoStack.length && lastOperation(session)?.afterHtml !== session.baseHtml) session.undoStack.pop()
    preview(session, session.baseHtml)
  }
  function onGlobalKeyDown(event: KeyboardEvent) {
    if (!props.isOpen || event.defaultPrevented || event.shiftKey || event.altKey || isTextEditingTarget(event.target) || loading.value || saving.value) return
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
      const session = current.value
      const operation = lastOperation(session)
      if (operation && operation.afterHtml === source(session)) {
        event.preventDefault()
        undo(session, operation, session.messages.find(message => message.id === operation.messageId))
        return
      }
    }
  }
  onMounted(() => {
    window.addEventListener('pagehide', persist)
    window.addEventListener('beforeunload', persist)
  })
  onBeforeUnmount(() => {
    disposed = true
    controllers.forEach(controller => controller.abort())
    window.removeEventListener('pagehide', persist)
    window.removeEventListener('beforeunload', persist)
    persist()
    stopSessionWatches.forEach(stop => stop())
  })
  return { inputPrompt, errorMessage, messages, historySnapshots, loading, saving, canRetry, hasDraft, canRestoreSessionInitial,
    handleSend, handleRetry, handleCancel, handleCommit, handleRevert, handleRevertCommitted, handleReapply,
    handleRollbackToSnapshot, handleRestoreSessionInitial, handleSaveDraft, handleDiscardDraft, onGlobalKeyDown }
}
