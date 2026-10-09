<template>
  <el-dialog v-model="visible" title="审核智能连线" width="min(1040px, 94vw)" top="5vh" append-to-body
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" class="autowire-dialog">
    <div class="review-content" v-if="plan">
      <div class="review-topline">
        <span class="review-summary">{{ candidatePageCount }} 个页面 · {{ plan.items.length }} 条建议</span>
        <el-popover trigger="click" placement="bottom-end" width="280">
          <template #reference><button type="button" class="help-button">怎么操作</button></template>
          <p class="help-copy">勾选需要的跳转，再点“确认应用”。</p>
          <p class="help-copy">取消勾选只跳过这次。</p>
          <p class="help-copy">“更多设置”可以记住以后不再推荐的跳转。手动连线不会被修改。</p>
        </el-popover>
      </div>
      <div class="navigation-tools">
        <label class="page-filter">页面
          <select v-model="sourcePage" aria-label="筛选来源页面">
            <option value="">全部页面</option>
            <option v-for="page in sourcePages" :key="page.id" :value="String(page.id)">{{ pageTitle(page.id, page.name) }}</option>
          </select>
        </label>
        <label><input type="checkbox" v-model="onlyNavigation" /> 仅看导航</label>
      </div>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <div v-for="warning in plan.warnings" :key="warning" class="notice">{{ warning }}</div>
      <div v-if="result" class="notice" role="status">
        已保存：补全 {{ result.completed }} 条，新增 {{ result.added }} 条，删除 {{ result.removed }} 条<template v-if="result.excluded">，不再推荐 {{ result.excluded }} 项</template>。
        <span v-if="result.renderStatus === 'done'">交互预览已更新。</span>
        <span v-else-if="result.renderStatus === 'pending'">交互预览更新中。</span>
        <span v-else>交互预览更新失败，关系已保存。</span>
        <p v-if="result.renderError" class="muted">{{ result.renderError }}</p>
        <el-button v-if="result.renderStatus === 'failed'" size="small" :loading="busy" @click="$emit('retry')">重试预览更新</el-button>
        <el-button v-if="result.renderStatus === 'pending'" size="small" :loading="busy" @click="$emit('refresh')">刷新状态</el-button>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane v-for="group in groups" :key="group.key" :name="group.key" :label="`${group.label} ${count(group.key)}`">
          <div class="items">
            <p v-if="!count(group.key)" class="empty">{{ group.empty }}</p>
            <section v-for="pageGroup in pageGroups(group.key)" :key="pageGroup.id" class="page-group">
              <header class="page-group-heading"><FileText :size="15" aria-hidden="true" /><strong>{{ pageTitle(pageGroup.id, pageGroup.name) }}</strong><span class="muted">{{ pageGroup.items.length }} 条</span></header>
              <article v-for="item in pageGroup.items" :key="item.id" class="item">
              <div class="item-heading">
                <label class="choice">
                  <input type="checkbox" :checked="selected.includes(item.id)" :disabled="(!item.applicable && !canConfirmTarget(item)) || busy || !!result || !!excluded[item.id]"
                    :aria-label="`应用 ${item.pageName} · ${item.elementLabel || '未命名元素'}`" @change="chooseRelation(item, ($event.target as HTMLInputElement).checked)" />
                  <span class="change-tag" :class="item.category">{{ changeName(item) }}</span>
                </label>
                <span v-if="item.source === 'ai_inferred'" class="muted">AI 建议</span>
              </div>
              <div class="relation-route">
                <div class="route-point route-source"><span class="route-label">页面</span><strong>{{ pageTitle(item.pageId, item.pageName) }}</strong></div>
                <ArrowRight class="route-arrow" :size="17" aria-hidden="true" />
                <div class="route-point route-element"><span class="route-label">{{ triggerName(item.trigger) }}</span><strong>{{ item.elementLabel || '未命名元素' }}</strong></div>
                <ArrowRight class="route-arrow" :size="17" aria-hidden="true" />
                <div class="route-point route-target" :class="{ unresolved: needsTarget(item.action) && item.targetPageId == null, removal: item.category === 'remove' }">
                  <label v-if="canChooseTarget(item)" class="route-target-picker">
                    <span class="route-label">{{ targetCaption(item) }}</span>
                    <select :value="targetChoices[reviewKey(item)] ?? ''" :disabled="busy || !!result || !!excluded[item.id]" :aria-label="`跳转目标 ${item.pageName} · ${item.elementLabel}`" @change="chooseTarget(item, ($event.target as HTMLSelectElement).value)">
                      <option value="">{{ item.targetPageId == null ? '请选择页面' : targetTitle(item) }}</option>
                      <option v-for="target in reviewTargets(item)" :key="target.id" :value="target.id">{{ pageTitle(target.id, target.name) }}</option>
                    </select>
                  </label>
                  <template v-else><span class="route-label">{{ targetCaption(item) }}</span><strong>{{ targetTitle(item) }}</strong></template>
                </div>
              </div>
              <div v-if="item.category === 'complete' || previousState(item)" class="change-explanation">
                <p><span>原来</span>{{ previousState(item) || '未设置目标页面' }}</p>
              </div>
              <p v-if="pendingTarget(item) != null" class="pending-target"><template v-if="confirmations.has(reviewKey(item))">已勾选此跳转，请点击“检查新目标”完成校验。</template><template v-else>已选择「{{ pageTitle(pendingTarget(item)!) }}」，可勾选并点击“检查新目标”。</template></p>
              <p v-else-if="!item.applicable && !canChooseTarget(item)" class="muted confirmation-hint">{{ targetUnavailableReason(item) }}</p>
              <details class="item-more" :open="!!excluded[item.id]">
                <summary>{{ excluded[item.id] ? recommendationChoice(item) : '更多设置' }}</summary>
                <div class="item-options">
                  <label class="exclude-label">下次还推荐吗
                    <select v-model="excluded[item.id]" :disabled="busy || !!result" @change="onExclude(item.id)" :aria-label="`下次还推荐吗 ${item.pageName} · ${item.elementLabel || '未命名元素'}`">
                      <option value="">继续推荐</option>
                      <option v-if="item.targetPageId != null || item.action === 'back'" value="relation">{{ item.action === 'back' ? '别再推荐这个返回操作' : item.action === 'navigate' ? '别再推荐这个跳转' : '别再推荐这个弹窗' }}</option>
                      <option value="element">这个元素都不推荐</option>
                    </select>
                  </label>
                </div>
                <p v-if="excluded[item.id]" class="preference-effect">保存后会记住选择，并删除对应的已有自动连线。</p>
                <details class="recommendation-evidence">
                  <summary>为什么推荐</summary>
                  <p class="reason">{{ item.reason }}</p>
                  <p class="muted">{{ sourceName(item.source) }}<template v-if="item.navigation"> · {{ regionName(item.navigation.region) }} · {{ basisName(item.navigation.basis) }}</template></p>
                  <p v-if="item.evidenceRefs.length" class="muted">{{ [...new Set(item.evidenceRefs.map(evidenceName))].join('、') }}</p>
                </details>
              </details>
              </article>
            </section>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`不再推荐 ${plan.exclusions.length}`" name="exclusions">
          <div class="items">
            <p v-if="plan.exclusions.length" class="muted">勾选需要恢复的项目，再点“确认应用”。</p>
            <p v-if="!plan.exclusions.length" class="empty">没有不再推荐的项目</p>
            <article v-for="item in plan.exclusions" :key="item.id" class="item">
              <label class="choice">
                <input type="checkbox" v-model="restores" :value="item.id" :disabled="busy || !!result" :aria-label="`恢复 ${item.elementLabel}`" />
                <div class="relation"><strong>{{ item.elementLabel || '未命名元素' }}</strong><span class="muted">{{ item.pageName }} · {{ item.scope === 'element' ? '不推荐这个元素的任何跳转' : '不推荐原来的跳转' }}<template v-if="!item.matched"> · 元素已变化，需检查</template></span></div>
              </label>
              <p class="reason">{{ item.reason }}</p>
            </article>
          </div>
        </el-tab-pane>
      </el-tabs>
      <details v-if="plan.navigationDiagnostics?.length || plan.protectedCount" class="navigation-diagnostics">
        <summary>其他检查结果</summary>
        <p v-if="plan.protectedCount" class="muted">{{ plan.protectedCount }} 条人工关系已保留。</p>
        <p v-if="diagnosticSummary" class="muted">{{ diagnosticSummary }}</p>
        <p v-for="(entry, index) in plan.navigationDiagnostics" :key="`${entry.pageId}:${entry.label}:${index}`" class="muted">{{ entry.pageName }} · {{ entry.label || '导航项' }}：{{ entry.reason }}</p>
      </details>
    </div>
    <template #footer>
      <div class="footer">
        <span class="muted">{{ result ? '已保存' : `已选 ${selected.length} 条${hiddenSelectedCount ? `（${hiddenSelectedCount} 条在当前筛选外）` : ''}${excludeCount ? ` · 不再推荐 ${excludeCount} 项` : ''}${restores.length ? ` · 恢复推荐 ${restores.length} 项` : ''}` }}</span>
        <div class="actions">
          <el-button :disabled="busy" @click="visible = false">{{ result ? '完成' : '取消' }}</el-button>
          <el-button :disabled="busy" @click="recompute">{{ hasPendingTargets ? '检查新目标' : '重新检查' }}</el-button>
          <el-button v-if="!result" type="primary" :loading="busy" :disabled="!plan || !hasDecisions || hasPendingTargets" @click="submit">确认应用</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, FileText } from 'lucide-vue-next'
import type { Page } from '../types'
import type { AutowirePlan, AutowireApplyResult, AutowireDecisions, AutowireItem, AutowirePreviewRequest } from '../types/autowire'
import { rememberReview, restoreReview, reviewKey, reviewTargets, reviewTargetBasis, type ExclusionScope, type ReviewConfirmation } from '../utils/autowireReviewState'
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ plan: AutowirePlan | null; result: AutowireApplyResult | null; busy: boolean; error: string; pages?: Page[] }>()
const emit = defineEmits<{ apply: [AutowireDecisions]; recompute: [AutowirePreviewRequest]; retry: []; refresh: [] }>()
const tab = ref('complete')
const selected = ref<string[]>([])
const excluded = ref<Record<string, ExclusionScope>>({})
const restores = ref<number[]>([])
const onlyNavigation = ref(false)
const sourcePage = ref('')
const targetChoices = ref<Record<string, number | undefined>>({})
const confirmations = ref(new Map<string, ReviewConfirmation & { ownsTargetChoice: boolean }>())
const groups: { key: AutowireItem['category']; label: string; empty: string }[] = [
  { key: 'complete', label: '补充 / 修改', empty: '没有需要补充的连线' },
  { key: 'add', label: '新增连线', empty: '没有新增连线' },
  { key: 'remove', label: '删除建议', empty: '没有需要删除的连线' },
  { key: 'uncertain', label: '待确认', empty: '没有待确认的连线' },
]
watch(() => props.plan, (next, previous) => {
  if (!next || next.previewId === previous?.previewId) return
  const memory = next.projectId === previous?.projectId ? rememberReview(previous, selected.value, excluded.value, targetChoices.value) : new Map()
  const restored = restoreReview(next, memory, next.projectId === previous?.projectId ? confirmations.value : new Map())
  selected.value = restored.selected; excluded.value = restored.excluded; targetChoices.value = restored.targets
  confirmations.value = new Map()
  restores.value = restores.value.filter(id => next.exclusions.some(e => e.id === id))
  if (next.projectId !== previous?.projectId) sourcePage.value = ''
  if (sourcePage.value && !next.items.some(i => String(i.pageId) === sourcePage.value)) sourcePage.value = ''
  if (!count(tab.value)) tab.value = groups.find(g => count(g.key))?.key || 'complete'
}, { immediate: true })
function matchesFilters(item: AutowireItem) { return (!onlyNavigation.value || !!item.navigation) && (!sourcePage.value || String(item.pageId) === sourcePage.value) }
function groupItems(key: string) { return props.plan?.items.filter(i => i.category === key && matchesFilters(i)) || [] }
function count(key: string) { return groupItems(key).length }
function pageGroups(key: string) {
  const grouped = new Map<number, { id: number; name: string; items: AutowireItem[] }>()
  for (const item of groupItems(key)) {
    if (!grouped.has(item.pageId)) grouped.set(item.pageId, { id: item.pageId, name: item.pageName, items: [] })
    grouped.get(item.pageId)!.items.push(item)
  }
  return [...grouped.values()]
}
watch([onlyNavigation, sourcePage], () => { if (!count(tab.value)) tab.value = groups.find(g => count(g.key))?.key || 'complete' })
const sourcePages = computed(() => [...new Map((props.plan?.items || []).map(i => [i.pageId, { id: i.pageId, name: i.pageName }])).values()])
const candidatePageCount = computed(() => sourcePages.value.length)
const hiddenSelectedCount = computed(() => props.plan?.items.filter(i => selected.value.includes(i.id) && !matchesFilters(i)).length || 0)
const pageNames = computed(() => {
  const names = new Map<number,string>()
  for (const page of props.pages || []) names.set(page.id, page.name)
  for (const item of props.plan?.items || []) {
    names.set(item.pageId, item.pageName)
    if (item.targetPageId != null && item.targetPageName) names.set(item.targetPageId, item.targetPageName)
    for (const target of reviewTargets(item)) if (!names.has(target.id)) names.set(target.id, target.name)
  }
  return names
})
function pageTitle(id: number, fallback?: string) {
  const name = fallback?.trim() || pageNames.value.get(id)?.trim()
  if (!name) return `页面 #${id}`
  const duplicate = [...pageNames.value].some(([otherId, otherName]) => otherId !== id && otherName === name)
  return duplicate ? `${name}（#${id}）` : name
}
function existingInteraction(item: AutowireItem) {
  const current = props.pages?.find(p => p.id === item.pageId)?.elements.find(e => e.id === item.elementId)?.interaction
  return current && item.interactionId != null && current.id === item.interactionId ? current : undefined
}
function previousAction(item: AutowireItem) { return item.navigation?.previousAction || existingInteraction(item)?.action }
function previousTarget(item: AutowireItem) { return item.navigation?.previousAction ? item.navigation.previousTargetPageId : existingInteraction(item)?.target_page_id }
function previousState(item: AutowireItem) {
  const action = previousAction(item), target = previousTarget(item)
  if (!action) return ''
  if (action === 'tab_switch' || action === 'tab') return '只切换本页内容'
  if (action === 'back') return '返回上一页'
  if (!needsTarget(action)) return actionName(action)
  if (target == null) return `${actionName(action)}，尚未关联目标页面`
  return `${actionName(action)}「${pageTitle(target)}」`
}
function changeName(item: AutowireItem) {
  if (item.category === 'uncertain') return confirmations.value.has(reviewKey(item)) ? '已勾选，待检查' : canConfirmTarget(item) ? '请勾选确认' : canChooseTarget(item) && item.targetPageId == null ? '请选择目标页面' : '关系需确认'
  if (item.category === 'remove') return '删除连线'
  if (item.category === 'add') return item.action === 'navigate' ? '新增跳转' : item.action === 'back' ? '新增返回操作' : '新增弹窗交互'
  const oldAction = previousAction(item), oldTarget = previousTarget(item)
  if (oldAction && oldAction !== item.action) return oldAction === 'tab_switch' || oldAction === 'tab' ? '改为页面跳转' : '修改操作'
  return oldTarget != null && oldTarget !== item.targetPageId ? '修改跳转目标' : item.action === 'navigate' ? '补充跳转目标' : '补充弹窗目标'
}
function needsTarget(action: string) { return ['navigate', 'popup', 'modal'].includes(action) }
function canChooseTarget(item: AutowireItem) { return item.category !== 'remove' && needsTarget(item.action) && item.navigation?.status !== 'conflict' && reviewTargets(item).length > 0 }
function canConfirmTarget(item: AutowireItem) {
  const target = targetChoices.value[reviewKey(item)] ?? item.targetPageId
  return canChooseTarget(item) && target != null && reviewTargets(item).some(t => t.id === target)
}
function targetUnavailableReason(item: AutowireItem) {
  if (item.navigation?.status === 'conflict') return '现有跳转有冲突，请先在画布的交互设置中处理。'
  if (item.navigation || item.targetSelection) return '没有可选的目标页面，请先添加目标页面再重新检查。'
  return `${item.reason}；请在画布的交互设置中确认。`
}
function recommendationChoice(item: AutowireItem) {
  if (excluded.value[item.id] === 'element') return '这个元素都不推荐'
  return item.action === 'back' ? '别再推荐这个返回操作' : item.action === 'navigate' ? '别再推荐这个跳转' : '别再推荐这个弹窗'
}
function targetCaption(item: AutowireItem) {
  if (item.action === 'back') return '返回到'
  if (!needsTarget(item.action)) return '交互结果'
  if (item.category === 'remove') return '删除此连线'
  return item.action === 'navigate' ? '跳转到' : '打开弹窗'
}
function targetTitle(item: AutowireItem) {
  if (item.action === 'back') return '上一页'
  if (!needsTarget(item.action)) return actionName(item.action)
  if (item.targetPageId == null) return '目标未确定'
  return pageTitle(item.targetPageId, item.targetPageName)
}
function pendingTarget(item: AutowireItem) {
  const target = targetChoices.value[reviewKey(item)]
  return !excluded.value[item.id] && target != null && (target !== item.targetPageId || reviewTargetBasis(item) !== 'user_choice') ? target : null
}
const excludeCount = computed(() => Object.values(excluded.value).filter(Boolean).length)
const hasDecisions = computed(() => !!(selected.value.length || excludeCount.value || restores.value.length))
const hasPendingTargets = computed(() => props.plan?.items.some(item => pendingTarget(item) != null) || false)
const diagnosticSummary = computed(() => {
  const entries = props.plan?.navigationDiagnostics || []
  const labels: Record<string,string> = { correct: '原跳转已保留', current_page: '当前页无需跳转', local_tab: '保留本页内容切换', excluded: '不再推荐', protected: '原交互已保留', unbound: '导航元素未识别', missing_target: '原目标已不存在' }
  return Object.entries(labels).map(([status,label]) => ({ label, count: entries.filter(e => e.status === status).length })).filter(s => s.count).map(s => `${s.label} ${s.count} 项`).join(' · ')
})
function chooseTarget(item: AutowireItem, value: string) {
  if (value && !reviewTargets(item).some(target => target.id === Number(value))) return
  confirmations.value.delete(reviewKey(item))
  targetChoices.value[reviewKey(item)] = value ? Number(value) : undefined
  toggle(item.id, false)
}
function chooseRelation(item: AutowireItem, on: boolean) {
  if (props.busy || props.result || excluded.value[item.id]) return
  const key = reviewKey(item), confirmation = confirmations.value.get(key)
  if (!on) {
    if (confirmation?.ownsTargetChoice && targetChoices.value[key] === confirmation.target) delete targetChoices.value[key]
    confirmations.value.delete(key)
    toggle(item.id, false)
    return
  }
  if (!item.applicable || pendingTarget(item) != null) {
    if (!canConfirmTarget(item)) return
    const target = targetChoices.value[key] ?? item.targetPageId!
    confirmations.value.set(key, { target, action: item.action, trigger: item.trigger, ownsTargetChoice: targetChoices.value[key] == null })
    targetChoices.value[key] = target
  }
  toggle(item.id, true)
}
function recompute() {
  const choices = (props.plan?.items || []).filter(i => canChooseTarget(i) && !excluded.value[i.id] && targetChoices.value[reviewKey(i)] != null)
  const resolution = (i: AutowireItem) => ({ stableKey: reviewKey(i), targetPageId: targetChoices.value[reviewKey(i)]! })
  emit('recompute', { previousPreviewId: props.plan?.previewId, navigationResolutions: choices.filter(i => i.navigation).map(resolution), targetResolutions: choices.filter(i => !i.navigation).map(resolution) })
}
function toggle(id: string, on: boolean) { selected.value = selected.value.filter(i => i !== id); if (on) selected.value.push(id) }
function onExclude(id: string) {
  if (!excluded.value[id]) return
  toggle(id, false)
  const item = props.plan?.items.find(i => i.id === id)
  if (item) { delete targetChoices.value[reviewKey(item)]; confirmations.value.delete(reviewKey(item)) }
}
function submit() {
  if (props.busy || props.result || hasPendingTargets.value || !hasDecisions.value || props.plan?.items.some(i => selected.value.includes(i.id) && !i.applicable)) return
  emit('apply', { selectedIds: [...selected.value].sort(), exclusions: Object.entries(excluded.value).filter(([, scope]) => !!scope).map(([itemId, scope]) => ({ itemId, scope: scope as 'relation' | 'element' })).sort((a, b) => a.itemId.localeCompare(b.itemId)), restoreExclusionIds: [...restores.value].sort((a,b) => a-b) })
}
const actionName = (action: string) => ({ navigate: '跳转', popup: '打开弹窗', modal: '打开弹窗', back: '返回', tab_switch: '页内切换', tab: '页内切换', toggle: '切换当前页面的状态', close: '关闭弹窗' }[action] || '执行原有操作')
const triggerName = (trigger: string) => ({ click: '点击', tap: '点击', double_click: '双击', dblclick: '双击', hover: '悬停于', long_press: '长按', longpress: '长按' }[trigger] || `触发（${trigger}）`)
const sourceName = (source: string) => ({ user: '人工关系', ai: '基于原有 AI 识别', ai_inferred: 'AI 建议，需审核', autowire: '规则推导', autowire_review: '有明确依据的建议' }[source] || '原有关系')
function evidenceName(ref: string) { const [kind, id] = ref.split(':'); if(kind === 'nav_basis') return basisName(id || ''); return ({ annotation: '业务说明', interaction: '已有交互', control: '明确操作入口', navigation: '已识别的导航项' }[kind] || '关联依据') }
const regionName = (region: string) => ({ bottom: '底部导航', top: '顶部导航', side: '侧边导航' }[region] || '导航入口')
const basisName = (basis: string) => ({ existing_target: '保留已有有效目标', exact_name: '名称与页面唯一匹配', confirmed_mapping: '同组已确认映射', ai_suggestion: 'AI 建议，需审核', user_choice: '人工选择的目标', conflict: '关系冲突，需先确认', unresolved: '目标待确认', ambiguous: '目标存在歧义' }[basis] || basis)
</script>

<style scoped>
.route-target-picker{display:flex;flex-direction:column;min-width:0;cursor:pointer}.route-target-picker select{width:100%;min-width:0;height:32px;padding:4px 8px;border:1px solid #93c5fd;border-radius:5px;background:#fff;color:#1d4ed8;font-size:13px;font-weight:600;cursor:pointer}.route-target-picker select:focus-visible{outline:2px solid #0d99ff;outline-offset:2px}.route-target-picker select:disabled{cursor:not-allowed;opacity:.6}.unresolved .route-target-picker select{border-color:#e9c85f;color:#92400e}:global(.dark .autowire-dialog .route-target-picker select){background:#22364a;border-color:#4e7094;color:#bfdbfe}:global(.dark .autowire-dialog .unresolved .route-target-picker select){background:#433724;border-color:#89723c;color:#fde68a}
.review-topline{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:0 0 8px}.review-topline .review-summary{margin:0}.help-button{border:0;padding:2px 0;background:transparent;color:#64748b;font-size:12px;cursor:pointer}.help-button:hover{color:#0d99ff}.help-copy{margin:6px 0;font-size:12px;line-height:1.7}
.item .item-heading{margin-bottom:8px}.item .route-point{min-height:54px;padding:8px 10px}.item .route-point strong{font-size:13px}.item .change-explanation{margin:7px 0}.item .change-explanation p>span{flex-basis:30px}.item-more{margin-top:8px;color:#64748b;font-size:12px}.item-more>summary{width:fit-content;cursor:pointer}.item-more[open]>summary{color:#475569}.item-more .item-options{margin-top:8px}.item-more .recommendation-evidence{margin-top:10px}.preference-effect{margin:8px 0 0;color:#92400e;font-size:12px}.unresolved-choice{margin:10px 0 0!important;flex-wrap:wrap}.confirmation-hint{margin-top:8px!important}
:global(.dark .autowire-dialog .help-button),:global(.dark .autowire-dialog .item-more){color:#a6abb5}:global(.dark .autowire-dialog .item-more[open]>summary){color:#e5e7eb}:global(.dark .autowire-dialog .preference-effect){color:#fde68a}
.review-summary{margin:8px 0;color:#64748b;font-size:12px}.review-summary strong{color:#334155;font-weight:600}
.page-filter select{min-width:160px;max-width:260px}.navigation-tools select{padding:6px 10px;border:1px solid #d1d5db;border-radius:6px;background:#fff;color:#334155;font-size:12px}
.page-group+.page-group{margin-top:18px}.page-group-heading{display:flex;align-items:center;gap:7px;flex-wrap:wrap;margin:0 0 8px;color:#64748b;font-size:12px}.page-group-heading strong{color:#334155;font-weight:600;overflow-wrap:anywhere}.page-group-heading>.muted{margin-left:auto}
.item-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.item-heading .choice{align-items:center;gap:8px}.item-heading .choice input{margin:0}
.change-tag{padding:2px 8px;border-radius:4px;font-size:11px;font-weight:500;background:#eff6ff;color:#2563eb}.change-tag.add{background:#ecfdf5;color:#047857}.change-tag.remove{background:#fff1f2;color:#be123c}.change-tag.uncertain{background:#fffbeb;color:#a16207}
.relation-route{display:grid;grid-template-columns:minmax(0,1fr) 18px minmax(0,1fr) 18px minmax(0,1fr);align-items:center;gap:12px}.route-point{display:flex;flex-direction:column;justify-content:center;min-width:0;min-height:68px;padding:10px 12px;border:1px solid #e2e8f0;border-radius:7px;background:#f8fafc}.route-label{color:#64748b;font-size:11px;line-height:1.5;margin-bottom:4px}.route-point strong{color:#1e293b;font-size:14px;font-weight:600;line-height:1.5;overflow-wrap:anywhere}.route-arrow{color:#94a3b8}.route-target{border-color:#bfdbfe;background:#eff6ff}.route-target strong{color:#1d4ed8}.route-target.unresolved{border-color:#fde68a;background:#fffbeb}.route-target.unresolved strong{color:#92400e}.route-target.removal{background:#fff1f2;border-color:#fecdd3}.route-target.removal strong{color:#9f1239}
.change-explanation{margin:10px 0;color:#334155;font-size:12px;line-height:1.7}.change-explanation p{display:flex;align-items:baseline;gap:10px;margin:3px 0;overflow-wrap:anywhere}.change-explanation p>span{flex:0 0 48px;color:#64748b}.pending-target{margin:8px 0;padding:8px 10px;border-radius:5px;background:#fffbeb;color:#92400e;font-size:12px}
.recommendation-evidence{font-size:12px;color:#64748b}.recommendation-evidence summary{cursor:pointer;width:fit-content}.recommendation-evidence .reason{margin:6px 0;color:#475569}.item-options{display:flex;align-items:center;flex-wrap:wrap;gap:8px 20px;margin-top:12px;padding-top:10px;border-top:1px solid #f1f5f9}.item-options .target-choice,.item-options .exclude-label{margin:0;flex-wrap:wrap}.item-options select{max-width:260px}
:global(.dark .autowire-dialog .review-summary),:global(.dark .autowire-dialog .page-group-heading),:global(.dark .autowire-dialog .route-label),:global(.dark .autowire-dialog .change-explanation p>span),:global(.dark .autowire-dialog .recommendation-evidence){color:#a6abb5}
:global(.dark .autowire-dialog .review-summary strong),:global(.dark .autowire-dialog .page-group-heading strong),:global(.dark .autowire-dialog .route-point strong),:global(.dark .autowire-dialog .change-explanation),:global(.dark .autowire-dialog .recommendation-evidence .reason){color:#e5e7eb}
:global(.dark .autowire-dialog .route-point){background:#2d3035;border-color:#454b55}:global(.dark .autowire-dialog .route-target){background:#162c40;border-color:#31506c}:global(.dark .autowire-dialog .route-target strong){color:#93c5fd}:global(.dark .autowire-dialog .route-target.unresolved),:global(.dark .autowire-dialog .pending-target){background:#352c1c;border-color:#66532a;color:#fde68a}:global(.dark .autowire-dialog .route-target.unresolved strong){color:#fde68a}:global(.dark .autowire-dialog .route-target.removal){background:#3f2020;border-color:#783434}:global(.dark .autowire-dialog .route-target.removal strong){color:#fda4af}:global(.dark .autowire-dialog .item-options){border-color:#3a3d43}
:global(.dark .autowire-dialog .change-tag){background:#162c40;color:#93c5fd}:global(.dark .autowire-dialog .change-tag.add){background:#14352c;color:#6ee7b7}:global(.dark .autowire-dialog .change-tag.remove){background:#3f2020;color:#fda4af}:global(.dark .autowire-dialog .change-tag.uncertain){background:#352c1c;color:#fde68a}
@media(max-width:640px){.relation-route{grid-template-columns:minmax(0,1fr) 18px minmax(0,1fr);gap:8px}.route-source{grid-column:1/-1;min-height:auto}.route-source+.route-arrow{display:none}.route-point{padding:8px 10px}.route-point strong{font-size:13px}.navigation-tools{flex-wrap:wrap}.page-filter select{max-width:210px}.item-options select{max-width:210px}.page-group-heading>.muted{margin-left:0}}
.navigation-tools{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:10px 0}.navigation-tools label{display:flex;align-items:center;gap:6px}.target-choice{display:flex;align-items:center;gap:8px;margin:8px 0 0 24px;font-size:12px}.target-choice select{max-width:100%;padding:5px 8px;border:1px solid #d1d5db;border-radius:5px;background:#fff;color:#374151}.navigation-diagnostics{margin-top:12px;padding:10px 12px;border:1px solid #e5e7eb;border-radius:8px}.navigation-diagnostics summary{cursor:pointer;font-size:12px}.navigation-diagnostics p{margin:6px 0}:global(.dark .autowire-dialog .target-choice select){background:#303136;border-color:#555;color:#e5e7eb}
:global(.autowire-dialog){max-height:90vh;display:flex;flex-direction:column;margin-bottom:0}:global(.autowire-dialog .el-dialog__body){min-height:0;overflow:auto}:global(.autowire-dialog .el-dialog__header),:global(.autowire-dialog .el-dialog__footer){flex-shrink:0}
.review-content{color:#1f2937;font-size:13px;line-height:1.6}.intro{margin:0 0 4px;font-weight:500}.muted{color:#6b7280;font-size:12px;margin:4px 0}.notice{padding:10px 12px;border:1px solid #bfdbfe;background:#eff6ff;border-radius:6px;margin:10px 0;font-size:12px}.error{border-color:#fecaca;background:#fef2f2;color:#b91c1c}.items{max-height:48vh;overflow:auto;padding:2px}.empty{text-align:center;padding:32px;color:#6b7280}.item{border:1px solid #e5e7eb;border-radius:8px;padding:12px;margin-bottom:10px}.choice{display:flex;align-items:flex-start;gap:10px;cursor:pointer}.choice input{margin-top:5px;accent-color:#0d99ff}.relation{display:flex;flex-wrap:wrap;align-items:center;gap:6px 12px;min-width:0}.relation strong{font-size:13px;font-weight:500;overflow-wrap:anywhere}.relation>.muted{width:100%}.destination{font-size:12px;color:#2563eb}.reason{margin:6px 0 4px 24px;font-size:12px}.evidence{margin-left:24px}.exclude-label{display:flex;align-items:center;gap:8px;margin:8px 0 0 24px;font-size:12px;color:#6b7280}.exclude-label select{padding:5px 8px;border:1px solid #d1d5db;border-radius:5px;background:#fff;color:#374151;font-size:12px}.footer{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.actions{display:flex;gap:8px}:global(.dark .autowire-dialog){background:#252525}:global(.dark .autowire-dialog .el-dialog__title),:global(.dark .autowire-dialog .review-content){color:#e5e7eb}:global(.dark .autowire-dialog .item){border-color:#454545}:global(.dark .autowire-dialog .muted),:global(.dark .autowire-dialog .exclude-label){color:#a6abb5}:global(.dark .autowire-dialog .notice){background:#162c40;border-color:#31506c}:global(.dark .autowire-dialog .error){background:#3f2020;border-color:#783434;color:#fca5a5}:global(.dark .autowire-dialog select){background:#303136;border-color:#555;color:#e5e7eb}
</style>
