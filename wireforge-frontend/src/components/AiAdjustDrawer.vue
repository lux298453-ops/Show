<template>
  <aside class="ai-adjust-drawer" :class="{ 'is-open': isOpen }" :inert="!isOpen" :aria-hidden="!isOpen" aria-label="AI 调整面板" @click.stop>
    <header class="ai-drawer-header">
      <span class="ai-page-name" :title="currentPage?.name">{{ currentPage?.name || '未选择画板' }}</span>
      <div class="ai-header-actions">
        <button v-if="canRestoreSessionInitial" type="button" class="ai-icon-button" :disabled="saving || loading" title="恢复初始版本" aria-label="恢复初始版本" @click="handleRestoreSessionInitial"><RotateCcw /></button>
        <button type="button" class="ai-icon-button" title="收起面板" aria-label="关闭对话框" @click="$emit('close')"><X /></button>
      </div>
    </header>

    <div ref="chatStreamRef" class="ai-chat-stream">
      <details v-if="historySnapshots.length" class="ai-history">
        <summary><History /><span>版本记录</span><span class="ai-count">{{ historySnapshots.length }}</span><ChevronDown class="ai-history-chevron" /></summary>
        <div v-for="(snap, sIdx) in historySnapshots" :key="snap.id" class="ai-history-item">
          <div><span>版本 {{ sIdx + 1 }}</span><button type="button" class="ai-text-button" :disabled="saving || loading" title="回退到此历史版本" @click="handleRollbackToSnapshot(snap)">恢复此版本</button></div>
          <p>{{ snap.summary }}</p>
        </div>
      </details>

      <section v-if="messages.length === 0 && !loading" class="ai-welcome">
        <h3>想调整哪里？</h3>
      </section>

      <template v-for="msg in messages" :key="msg.id">
        <article v-if="msg.role === 'user'" class="ai-user-message"><div>{{ msg.content }}</div><time>{{ msg.time }}</time></article>
        <article v-else-if="msg.role === 'assistant'" class="ai-assistant-message">
          <div class="ai-message-author"><span class="ai-author-icon"><Sparkles /></span><span>WireForge AI</span><time>{{ msg.time }}</time></div>
          <div class="ai-result-card">
            <div class="ai-result-heading"><h3>{{ msg.summary || '已完成调整' }}</h3><span v-if="msg.isElementMode" class="ai-mode-badge">局部调整</span></div>
            <details v-if="msg.diffList?.length" class="ai-change-details">
              <summary>修改详情<ChevronDown /></summary>
              <div class="ai-diff-list">
                <div v-for="(diff, dIdx) in msg.diffList" :key="dIdx" class="ai-diff-item">
                  <span v-if="diff.includes('删除') || diff.includes('移除')" class="ai-diff-tag is-delete">删除</span>
                  <span v-else-if="diff.includes('新增') || diff.includes('添加')" class="ai-diff-tag is-add">新增</span>
                  <span v-else class="ai-diff-tag">修改</span>
                  <span>{{ diff.replace(/^\[(删除|修改|新增)\]\s*/, '') }}</span>
                </div>
              </div>
            </details>
            <div v-if="msg.generatedImages?.length" class="ai-generated-images">
              <figure v-for="image in msg.generatedImages" :key="image.url" class="ai-generated-image">
                <img :src="getFileUrl(image.url)" :alt="image.name" loading="lazy" />
                <figcaption>{{ image.name }}</figcaption>
              </figure>
            </div>
            <div class="ai-result-status" :class="{ 'is-applied': msg.isCommitted && !msg.isReverted }">
              <RotateCcw v-if="msg.isReverted" /><Check v-else-if="msg.isCommitted" /><span v-else class="ai-status-dot"></span>
              <span>{{ msg.unchanged ? '无需修改' : msg.isReverted ? '已还原' : msg.isCommitted ? '已应用' : '待确认' }}</span>
            </div>
            <div v-if="!msg.unchanged && !msg.isCommitted && !msg.isReverted" class="ai-result-actions">
              <button type="button" class="ai-button is-primary" :disabled="saving || loading" @click="handleCommit(msg)"><Check /><span>{{ saving ? '保存中…' : '应用修改' }}</span></button>
              <button type="button" class="ai-button" :disabled="saving || loading" title="还原到修改前的状态 (Ctrl+Z)" @click="handleRevert(msg)"><RotateCcw /><span>还原</span></button>
            </div>
            <div v-else-if="!msg.unchanged && msg.isCommitted && !msg.isReverted" class="ai-result-actions">
              <button type="button" class="ai-button" :disabled="saving || loading" title="撤销此次应用 (Ctrl+Z)" @click="handleRevertCommitted(msg)"><RotateCcw /><span>{{ saving ? '回退中…' : '撤回此修改' }}</span></button>
            </div>
            <div v-else-if="!msg.unchanged" class="ai-result-actions">
              <button type="button" class="ai-button" :disabled="saving || loading" @click="handleReapply(msg)"><RotateCcw /><span>{{ saving ? '处理中…' : '重新应用' }}</span></button>
            </div>
            <button v-if="!msg.unchanged && !msg.isElementMode && !msg.isCommitted && !msg.isReverted" type="button" class="ai-compare-button" title="按住查看已保存版本，松开恢复当前预览" @mousedown="$emit('start-compare')" @mouseup="$emit('end-compare')" @mouseleave="$emit('end-compare')"><Split /><span>按住对比已保存版本</span></button>
          </div>
        </article>
      </template>

      <div v-if="loading" class="ai-thinking" role="status" aria-live="polite">
        <div class="ai-message-author"><span class="ai-author-icon"><Sparkles /></span><span>WireForge AI</span></div>
        <div class="ai-thinking-card"><LoaderCircle class="ai-spinner" /><span>正在调整…</span></div>
        <div class="ai-skeleton"><span></span><span></span></div>
      </div>
      <button v-if="canRetry" type="button" class="ai-button" @click="handleRetry">重试上次请求</button>
      <div v-if="errorMessage" class="ai-error" role="alert"><AlertCircle /><span>{{ errorMessage }}</span><button type="button" class="ai-icon-button" aria-label="关闭错误提示" @click="errorMessage = ''"><X /></button></div>
    </div>

    <footer class="ai-composer-area">
      <div v-if="hasDraft" class="ai-draft-actions"><button type="button" class="ai-text-button" :disabled="saving || loading" @click="handleSaveDraft">保存当前预览</button><button type="button" class="ai-text-button" :disabled="saving || loading" @click="handleDiscardDraft">丢弃草稿</button></div>
      <div class="ai-composer">
        <div v-if="selectedElement" class="ai-selection-chip">
          <Crosshair /><span :title="selectedElement.label">{{ selectedElement.label || '选中元素' }}</span>
          <button type="button" class="ai-icon-button" title="取消选中，切回整页" aria-label="取消选中，切回整页" @click="$emit('clear-selected-element')"><X /></button>
        </div>
        <textarea ref="textareaRef" v-model="inputPrompt" rows="2" :placeholder="inputPlaceholder" aria-label="描述原型调整需求" @keydown="onTextareaKeydown"></textarea>
        <div class="ai-composer-toolbar"><button v-if="loading" type="button" class="ai-text-button" @click="handleCancel">停止</button><button type="button" class="ai-send-button" :disabled="!inputPrompt.trim() || loading || saving" title="发送调整需求 (Enter；Shift + Enter 换行)" aria-label="发送" @click="handleSend"><LoaderCircle v-if="loading" class="ai-spinner" /><ArrowUp v-else /></button></div>
      </div>
    </footer>
  </aside>
</template>


<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { X, Check, RotateCcw, ArrowUp, Split, Sparkles, Crosshair, History, ChevronDown, LoaderCircle, AlertCircle } from 'lucide-vue-next'
import type { Page } from '../types'
import type { AiElementTarget } from '../utils/aiAdjustment'
import { useAiAdjustment } from '../composables/useAiAdjustment'
import { getFileUrl } from '../api/http'

const props = withDefaults(defineProps<{
  projectId: number
  currentPage: Page | null
  isOpen: boolean
  currentDraftHtml?: string | null
  selectedElement?: AiElementTarget | null
}>(), { currentDraftHtml: null, selectedElement: null })
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'preview-draft', pageId: number, html: string): void
  (e: 'revert-draft', pageId: number): void
  (e: 'commit-success', pageId: number, html: string): void
  (e: 'clear-selected-element'): void
  (e: 'start-compare'): void
  (e: 'end-compare'): void
  (e: 'toast', message: string): void
}>()
const {
  inputPrompt, errorMessage, messages, historySnapshots, loading, saving, canRetry, hasDraft, canRestoreSessionInitial,
  handleSend, handleRetry, handleCancel, handleCommit, handleRevert, handleRevertCommitted, handleReapply,
  handleRollbackToSnapshot, handleRestoreSessionInitial, handleSaveDraft, handleDiscardDraft, onGlobalKeyDown,
} = useAiAdjustment(props, {
  preview: (pageId, html) => emit('preview-draft', pageId, html),
  revert: pageId => emit('revert-draft', pageId),
  commit: (pageId, html) => emit('commit-success', pageId, html),
  toast: message => emit('toast', message),
})
const chatStreamRef = ref<HTMLDivElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const inputPlaceholder = computed(() => props.selectedElement ? '描述如何调整这个元素…' : '描述修改需求…')
watch(() => [props.isOpen, props.currentPage?.id, messages.value.length, loading.value], () => {
  nextTick(() => {
    if (chatStreamRef.value) chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight
  })
})
watch(() => props.isOpen, open => { if (open) nextTick(() => textareaRef.value?.focus()) })
function onTextareaKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    handleSend()
  }
}
onMounted(() => window.addEventListener('keydown', onGlobalKeyDown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeyDown))
</script>

<style scoped>
.ai-adjust-drawer {
  --ai-accent: var(--wf-primary, #0d99ff);
  --ai-surface: var(--wf-surface, #fff);
  --ai-field: var(--wf-bg, #f6f8fb);
  --ai-border: var(--wf-border, #e2e8f0);
  --ai-text: var(--wf-text-main, #1e293b);
  --ai-muted: var(--wf-text-muted, #64748b);
  --ai-success: #15805d;
  --ai-danger: #cf4b55;
  --ai-tint: color-mix(in srgb, var(--ai-accent) 9%, var(--ai-surface));
  position: fixed; inset: 0 0 0 auto; z-index: 60;
  display: flex; flex-direction: column; width: min(420px, 100vw); height: 100%;
  background: var(--ai-surface); color: var(--ai-text); border-left: 1px solid var(--ai-border);
  box-shadow: -4px 0 20px rgb(0 0 0 / 4%); transform: translateX(100%);
  transition: transform 240ms cubic-bezier(.2,.7,.2,1); pointer-events: none;
  font: 13px/1.6 Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif; -webkit-font-smoothing: antialiased;
}
.ai-adjust-drawer.is-open { transform: translateX(0); pointer-events: auto; }
:global(html.dark .ai-adjust-drawer) { --ai-success: #6dd5ae; --ai-danger: #f999a1; }
.ai-adjust-drawer :is(button, textarea) { font: inherit; }
.ai-adjust-drawer button { cursor: pointer; transition: background-color 150ms, border-color 150ms, color 150ms; }
.ai-adjust-drawer button:disabled { cursor: not-allowed; opacity: .45; }
.ai-adjust-drawer button:focus-visible { outline: 2px solid var(--ai-accent); outline-offset: 3px; }
.ai-adjust-drawer svg { width: 16px; height: 16px; flex-shrink: 0; stroke-width: 1.75; }
.ai-drawer-header { display: flex; align-items: center; gap: 12px; flex-shrink: 0; min-height: 60px; padding: 12px 16px 12px 20px; }
.ai-page-name { flex: 1; min-width: 0; color: var(--ai-muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-header-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.ai-icon-button { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--ai-muted); flex-shrink: 0; }
.ai-icon-button:hover { background: var(--ai-field); color: var(--ai-text); }
.ai-chat-stream { display: flex; flex-direction: column; flex: 1; min-height: 0; gap: 24px; padding: 24px 20px; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; scrollbar-color: var(--ai-border) transparent; }
.ai-welcome { margin: auto 0; padding-bottom: 24px; text-align: center; }
.ai-welcome h3 { margin: 0; font-size: 16px; line-height: 1.5; font-weight: 400; color: var(--ai-muted); }
.ai-history { border: 1px solid var(--ai-border); border-radius: 8px; flex-shrink: 0; }
.ai-history summary { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; list-style: none; font-size: 12px; }
.ai-history summary::-webkit-details-marker { display: none; }
.ai-count { color: var(--ai-muted); font-variant-numeric: tabular-nums; }
.ai-history-chevron { margin-left: auto; color: var(--ai-muted); }
.ai-history[open] .ai-history-chevron { transform: rotate(180deg); }
.ai-history-item { padding: 10px 12px; border-top: 1px solid var(--ai-border); font-size: 12px; }
.ai-history-item > div { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ai-history-item p { margin: 4px 0 0; color: var(--ai-muted); overflow-wrap: anywhere; }
.ai-user-message { align-self: flex-end; max-width: 88%; }
.ai-user-message > div { padding: 10px 13px; border-radius: 12px 12px 3px 12px; background: var(--ai-accent); color: white; white-space: pre-wrap; overflow-wrap: anywhere; }
.ai-user-message time, .ai-message-author time { display: block; font-size: 10px; color: var(--ai-muted); font-variant-numeric: tabular-nums; }
.ai-user-message time { margin-top: 5px; text-align: right; }
.ai-message-author { display: flex; align-items: center; gap: 7px; margin-bottom: 10px; font-size: 12px; font-weight: 500; }
.ai-message-author time { margin-left: auto; font-weight: 400; }
.ai-author-icon { display: inline-flex; color: var(--ai-accent); }
.ai-result-card { padding: 14px; border: 1px solid var(--ai-border); border-radius: 10px; background: var(--ai-surface); }
.ai-result-heading { display: flex; align-items: flex-start; gap: 10px; justify-content: space-between; }
.ai-result-heading h3 { font-size: 13px; font-weight: 500; line-height: 1.7; margin: 0; overflow-wrap: anywhere; }
.ai-generated-images { display: flex; gap: 10px; margin-top: 12px; }
.ai-generated-image { flex: 1; min-width: 0; max-width: 130px; margin: 0; }
.ai-generated-image img { display: block; width: 100%; height: 92px; border: 1px solid var(--ai-border); border-radius: 7px; object-fit: contain; background: var(--ai-field); }
.ai-generated-image figcaption { margin-top: 4px; color: var(--ai-muted); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-mode-badge { flex-shrink: 0; padding: 1px 6px; border-radius: 4px; font-size: 10px; color: var(--ai-accent); background: var(--ai-tint); }
.ai-change-details { margin-top: 12px; }
.ai-change-details summary { display: flex; align-items: center; gap: 5px; width: fit-content; cursor: pointer; list-style: none; color: var(--ai-muted); font-size: 11px; }
.ai-change-details summary::-webkit-details-marker { display: none; }
.ai-change-details summary svg { width: 12px; height: 12px; }
.ai-change-details[open] summary svg { transform: rotate(180deg); }
.ai-diff-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.ai-diff-item { display: flex; align-items: baseline; gap: 8px; font-size: 12px; color: var(--ai-muted); overflow-wrap: anywhere; }
.ai-diff-tag { flex-shrink: 0; font-size: 10px; padding: 1px 5px; border-radius: 4px; color: var(--ai-accent); background: var(--ai-tint); }
.ai-diff-tag.is-add { color: var(--ai-success); background: rgb(16 185 129 / 10%); }
.ai-diff-tag.is-delete { color: var(--ai-danger); background: rgb(244 63 94 / 8%); }
.ai-result-status { display: flex; align-items: center; gap: 6px; margin-top: 14px; font-size: 11px; color: var(--ai-muted); }
.ai-result-status svg { width: 13px; height: 13px; }
.ai-result-status.is-applied { color: var(--ai-success); }
.ai-status-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--ai-accent); }
.ai-result-actions { display: flex; gap: 8px; margin-top: 13px; }
.ai-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 32px; padding: 5px 11px; border: 1px solid var(--ai-border); border-radius: 6px; background: var(--ai-surface); color: var(--ai-text); font-size: 12px !important; }
.ai-button:hover { background: var(--ai-field); }
.ai-button svg { width: 14px; height: 14px; }
.ai-button.is-primary { flex: 1; background: var(--ai-accent); border-color: var(--ai-accent); color: white; }
.ai-button.is-primary:hover, .ai-send-button:not(:disabled):hover { background: var(--wf-primary-hover, #0b7ed4); }
.ai-text-button, .ai-compare-button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 3px 0; border: 0; background: transparent; color: var(--ai-muted); font-size: 11px !important; }
.ai-text-button:hover, .ai-compare-button:hover { color: var(--ai-accent); }
.ai-text-button svg { width: 13px; height: 13px; }
.ai-compare-button { width: 100%; margin-top: 10px; }
.ai-compare-button svg { width: 13px; height: 13px; }
.ai-thinking-card { display: flex; align-items: center; gap: 8px; color: var(--ai-muted); font-size: 12px; }
.ai-spinner { animation: ai-spin 1s linear infinite; }
.ai-skeleton { display: flex; flex-direction: column; gap: 8px; margin: 14px 0 0 24px; }
.ai-skeleton span { height: 7px; border-radius: 3px; background: var(--ai-field); animation: ai-pulse 1.5s ease-in-out infinite; }
.ai-skeleton span:last-child { width: 70%; }
.ai-error { display: flex; align-items: flex-start; gap: 8px; padding: 10px 10px 10px 12px; border: 1px solid rgb(244 63 94 / 20%); border-radius: 8px; background: rgb(244 63 94 / 5%); color: var(--ai-danger); font-size: 12px; overflow-wrap: anywhere; }
.ai-error > svg { margin-top: 3px; }
.ai-error > span { flex: 1; min-width: 0; }
.ai-error .ai-icon-button { width: 22px; height: 22px; color: inherit; }
.ai-composer-area { flex-shrink: 0; padding: 16px; background: var(--ai-surface); }
.ai-selection-chip { display: flex; align-items: center; gap: 6px; width: fit-content; max-width: 100%; margin-bottom: 10px; padding: 3px 4px 3px 7px; border-radius: 5px; background: var(--ai-tint); color: var(--ai-accent); font-size: 11px; }
.ai-selection-chip > svg { width: 12px; height: 12px; }
.ai-selection-chip > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-selection-chip .ai-icon-button { width: 20px; height: 20px; color: inherit; }
.ai-selection-chip .ai-icon-button svg { width: 12px; height: 12px; }
.ai-composer { border: 1px solid var(--ai-border); border-radius: 10px; background: var(--ai-field); padding: 12px; transition: border-color 150ms, box-shadow 150ms; }
.ai-composer:focus-within { border-color: var(--ai-accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--ai-accent) 9%, transparent); }
.ai-composer textarea { display: block; width: 100%; min-height: 52px; max-height: 180px; padding: 0; resize: vertical; border: 0; outline: none; background: transparent; color: var(--ai-text); line-height: 1.75; scrollbar-width: thin; }
.ai-composer textarea::placeholder { color: var(--ai-muted); opacity: .85; }
.ai-draft-actions { display: flex; justify-content: space-between; gap: 12px; margin: 0 2px 10px; }
.ai-composer-toolbar { display: flex; justify-content: flex-end; margin-top: 8px; }
.ai-send-button { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 7px; background: var(--ai-accent); color: white; }
.ai-send-button:disabled { background: var(--ai-border); color: var(--ai-muted); opacity: 1; }
@keyframes ai-spin { to { transform: rotate(360deg); } }
@keyframes ai-pulse { 50% { opacity: .4; } }
@media (max-height: 700px) {
  .ai-chat-stream { padding-top: 18px; gap: 18px; }
  .ai-welcome { padding-bottom: 8px; }
}
@media (max-width: 480px) { .ai-drawer-header { padding: 16px; } .ai-chat-stream { padding-right: 16px; padding-left: 16px; } }
@media (prefers-reduced-motion: reduce) { .ai-adjust-drawer, .ai-adjust-drawer * { animation: none; transition: none; } }
</style>
