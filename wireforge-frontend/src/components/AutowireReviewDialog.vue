<template>
  <el-dialog v-model="visible" title="配置智能连线" width="min(1180px, 96vw)" top="4vh" append-to-body
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" class="autowire-dialog">
    <div v-if="loadingMessage" class="notice review-progress" role="status" aria-live="polite"><span class="progress-spinner" aria-hidden="true"></span>{{ loadingMessage }}</div>
    <div v-if="error" class="notice error" role="alert">{{ error }}<el-button v-if="plan && !result" class="error-recheck" text size="small" :disabled="busy" @click="recompute">重新检查</el-button></div>
    <p v-if="!plan && !busy && !error" class="empty">尚未生成检查结果。</p>
    <div v-if="plan" class="review-content">
      <div class="review-topline">
        <div><span class="review-summary">{{ candidatePageCount }} 个页面 · {{ plan.items.length }} 条建议</span><p class="review-intro">选择左侧关系，对照原型确认跳转页面，然后应用。</p></div>
        <label class="navigation-filter"><input type="checkbox" v-model="onlyNavigation" :disabled="busy" />仅看导航</label>
      </div>
      <div v-if="result" class="notice result-notice" role="status">
        已保存：补全 {{ result.completed }} 条，新增 {{ result.added }} 条，删除 {{ result.removed }} 条<template v-if="result.excluded">，不再推荐 {{ result.excluded }} 项</template>。
        <span v-if="result.renderStatus === 'done'">交互预览已更新。</span>
        <span v-else-if="result.renderStatus === 'pending'">交互预览更新中。</span>
        <span v-else>交互预览更新失败，关系已保存。</span>
        <p v-if="result.renderError" class="muted">{{ result.renderError }}</p>
        <el-button v-if="result.renderStatus === 'failed'" size="small" :loading="busy" @click="$emit('retry')">重试预览更新</el-button>
        <el-button v-if="result.renderStatus === 'pending'" size="small" :loading="busy" @click="$emit('refresh')">刷新状态</el-button>
      </div>
      <div class="review-tabs" role="tablist" aria-label="关系类型">
        <button v-for="group in groups" :key="group.key" type="button" role="tab" :aria-selected="tab === group.key" :class="{ active: tab === group.key }" @click="tab = group.key">{{ group.label }}<span>{{ count(group.key) }}</span></button>
        <button type="button" role="tab" :aria-selected="tab === 'exclusions'" :class="{ active: tab === 'exclusions' }" @click="tab = 'exclusions'">不再推荐<span>{{ plan.exclusions.length }}</span></button>
      </div>
      <div v-if="tab !== 'exclusions'" class="review-workspace">
        <aside class="relation-list" aria-label="按来源页面分组的关系">
          <label class="relation-search"><Search :size="15" aria-hidden="true" /><input v-model="search" type="search" placeholder="搜索页面或控件" aria-label="搜索页面或控件" /></label>
          <div class="relation-list-scroll">
            <p v-if="!filteredItems.length" class="empty">{{ search ? '没有匹配的关系' : groups.find(group => group.key === tab)?.empty }}</p>
            <section v-for="pageGroup in pageGroups" :key="pageGroup.id" class="page-group">
              <header class="page-group-heading">
                <input v-if="pageGroup.items.some(canApplyItem)" type="checkbox" :checked="pageFullySelected(pageGroup.items)" :indeterminate="pagePartiallySelected(pageGroup.items)" :disabled="busy || !!result" :aria-label="`选中 ${pageTitle(pageGroup.id, pageGroup.name)} 的可应用关系`" @change="choosePage(pageGroup.items, ($event.target as HTMLInputElement).checked)" />
                <FileText v-else :size="14" aria-hidden="true" /><strong>{{ pageTitle(pageGroup.id, pageGroup.name) }}</strong><span>{{ pageGroup.items.length }}</span>
              </header>
              <article v-for="item in pageGroup.items" :key="item.id" :data-item-id="item.id" class="relation-row" :class="{ active: activeItem?.id === item.id, chosen: selected.includes(item.id), blocked: !canApplyItem(item), excluded: !!excluded[item.id] }" tabindex="0" role="button" :aria-label="relationSummary(item)" :aria-pressed="activeItem?.id === item.id" @click="activeKey = reviewKey(item)" @keydown.enter.prevent="activeKey = reviewKey(item)" @keydown.space.prevent="activeKey = reviewKey(item)">
                <input type="checkbox" :checked="selected.includes(item.id)" :disabled="!canApplyItem(item) || busy || !!result || !!excluded[item.id]" :aria-label="`应用 ${item.pageName} · ${item.elementLabel || '未命名控件'}`" @click.stop @keydown.stop @change="chooseRelation(item, ($event.target as HTMLInputElement).checked)" />
                <span class="row-thumbnail"><img v-if="pageThumbnail(item.pageId)" :src="pageThumbnail(item.pageId)" alt="" loading="lazy" /><FileText v-else :size="18" aria-hidden="true" /></span>
                <div class="relation-row-copy"><strong>{{ item.elementLabel || '未命名控件' }}</strong><span class="row-destination"><ArrowRight :size="12" aria-hidden="true" />{{ targetTitle(item) }}</span><span class="row-status" :class="{ warning: !canApplyItem(item), removal: item.category === 'remove' }">{{ rowStatus(item) }}</span></div>
                <ChevronRight :size="15" class="row-chevron" aria-hidden="true" />
              </article>
            </section>
          </div>
          <div class="list-footnote">取消勾选只表示本次不应用。</div>
        </aside>
        <section v-if="activeItem" class="relation-detail" :data-active-item-id="activeItem.id" aria-label="关系对照预览">
          <div class="detail-heading"><h3>{{ activeItem.elementLabel || '未命名控件' }}</h3><span class="change-tag" :class="{ removal: activeItem.category === 'remove', warning: !canApplyItem(activeItem) }">{{ changeName(activeItem) }}</span></div>
          <div class="preview-comparison">
            <div class="preview-card source-card">
              <header><span class="preview-caption">来源页面</span><strong>{{ pageTitle(activeItem.pageId, activeItem.pageName) }}</strong></header>
              <AutowirePagePreview class="comparison-preview" :page="activeSourcePage" :element-id="activeItem.elementId" :navigation="activeItem.navigation" source />
            </div>
            <ArrowRight :size="22" class="comparison-arrow" aria-hidden="true" />
            <div class="preview-card target-card" :class="{ missing: needsTarget(activeItem.action) && chosenTarget(activeItem) == null, removal: activeItem.category === 'remove' }">
              <header>
                <label v-if="canChooseTarget(activeItem)" class="target-picker"><span class="preview-caption">{{ targetCaption(activeItem) }}</span><select :value="chosenTarget(activeItem) ?? ''" :disabled="busy || !!result || !!excluded[activeItem.id]" :aria-label="`跳转目标 ${activeItem.pageName} · ${activeItem.elementLabel}`" @change="chooseTarget(activeItem, ($event.target as HTMLSelectElement).value)"><option value="">请选择跳转页面</option><option v-for="target in reviewTargets(activeItem)" :key="target.id" :value="target.id">{{ pageTitle(target.id, target.name) }}</option></select></label>
                <template v-else><span class="preview-caption">{{ targetCaption(activeItem) }}</span><strong>{{ targetTitle(activeItem) }}</strong></template>
              </header>
              <AutowirePagePreview v-if="needsTarget(activeItem.action) && activeTargetPage" class="comparison-preview" :page="activeTargetPage" />
              <div v-else class="target-empty"><component :is="activeItem.action === 'back' ? CornerUpLeft : activeItem.category === 'remove' ? Unlink : Files" :size="30" aria-hidden="true" /><strong>{{ targetEmptyTitle(activeItem) }}</strong><p>{{ targetEmptyHint(activeItem) }}</p></div>
            </div>
          </div>
          <div class="relation-sentence" :class="{ removal: activeItem.category === 'remove' }"><span class="sentence-label">{{ excluded[activeItem.id] ? '以后不再推荐的关系' : activeItem.category === 'remove' ? '应用后删除' : '应用后的关系' }}</span><p>{{ relationSummary(activeItem) }}</p><div class="previous-relation"><span>原有关系</span><p>{{ previousState(activeItem) || '尚未设置此交互关系' }}</p></div><span v-if="selected.includes(activeItem.id) && canApplyItem(activeItem)" class="selected-indicator"><Check :size="13" aria-hidden="true" />{{ result ? '已应用' : '已选，将在应用时保存' }}</span></div>
          <div v-if="!canApplyItem(activeItem)" class="notice needs-attention" role="status">{{ targetUnavailableReason(activeItem) }}</div>
          <details class="item-more" :open="!!excluded[activeItem.id]">
            <summary>{{ excluded[activeItem.id] ? recommendationChoice(activeItem) : '更多设置' }}</summary>
            <label class="exclude-label">后续推荐<select v-model="excluded[activeItem.id]" :disabled="busy || !!result" :aria-label="`下次还推荐吗 ${activeItem.pageName} · ${activeItem.elementLabel || '未命名控件'}`" @change="onExclude(activeItem.id)"><option value="">继续推荐</option><option v-if="activeItem.targetPageId != null || activeItem.action === 'back'" value="relation">以后不再推荐这条{{ activeItem.action === 'back' ? '返回关系' : activeItem.action === 'navigate' ? '跳转' : '弹窗关系' }}</option><option value="element">以后不再推荐此控件的关系</option></select></label>
            <p v-if="excluded[activeItem.id]" class="preference-effect">应用后会记住选择，并删除该范围内已有的自动连线。</p>
            <div class="recommendation-evidence"><span>推荐依据</span><p>{{ activeItem.reason }}</p><p class="muted">{{ sourceName(activeItem.source) }}<template v-if="activeItem.navigation"> · {{ regionName(activeItem.navigation.region) }} · {{ basisName(activeItem.navigation.basis) }}</template><template v-if="activeItem.evidenceRefs.length"> · {{ [...new Set(activeItem.evidenceRefs.map(evidenceName))].join('、') }}</template></p></div>
          </details>
        </section>
        <div v-else class="detail-empty"><Files :size="36" aria-hidden="true" /><p>选择左侧关系，查看来源控件与跳转页面。</p></div>
      </div>
      <div v-else class="exclusions-panel">
        <p class="muted">勾选要恢复的控件，再应用。恢复后会在下次检查中重新推荐。</p>
        <p v-if="!plan.exclusions.length" class="empty">没有不再推荐的项目</p>
        <article v-for="item in plan.exclusions" :key="item.id" class="exclusion-row"><label><input type="checkbox" v-model="restores" :value="item.id" :disabled="busy || !!result" :aria-label="`恢复 ${item.elementLabel}`" /><div><strong>{{ item.pageName }} · {{ item.elementLabel || '未命名控件' }}</strong><p>{{ item.scope === 'element' ? '不推荐此控件的关系' : '不推荐原来的关系' }}<template v-if="!item.matched"> · 控件已变化，需重新检查</template></p><p v-if="item.reason" class="muted">{{ item.reason }}</p></div><span>恢复推荐</span></label></article>
      </div>
      <details v-if="plan.warnings.length || plan.navigationDiagnostics?.length || plan.protectedCount" class="navigation-diagnostics"><summary>其他检查结果<template v-if="plan.warnings.length"> · {{ plan.warnings.length }} 条提示</template></summary><p v-for="warning in plan.warnings" :key="warning" class="muted">{{ warning }}</p><p v-if="plan.protectedCount" class="muted">{{ plan.protectedCount }} 条人工关系已保留。</p><p v-if="diagnosticSummary" class="muted">{{ diagnosticSummary }}</p><p v-for="(entry, index) in plan.navigationDiagnostics" :key="`${entry.pageId}:${entry.label}:${index}`" class="muted">{{ entry.pageName }} · {{ entry.label || '导航项' }}：{{ entry.reason }}</p></details>
    </div>
    <template #footer><div class="footer"><span class="muted">{{ footerSummary }}</span><div class="actions"><el-button :disabled="busy" @click="visible = false">{{ result ? '完成' : '取消' }}</el-button><el-button v-if="!plan && error && !result" :disabled="busy" @click="recompute">重试检查</el-button><el-button v-if="plan && !result" type="primary" :loading="busy" :disabled="busy || !hasDecisions" @click="submit">{{ primaryLabel }}</el-button></div></div></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, Check, ChevronRight, CornerUpLeft, Files, FileText, Search, Unlink } from 'lucide-vue-next'
import AutowirePagePreview from './AutowirePagePreview.vue'
import { getFileUrl } from '../api/http'
import type { Page } from '../types'
import type { AutowirePlan, AutowireApplyResult, AutowireDecisions, AutowireItem, AutowirePreviewRequest } from '../types/autowire'
import { rememberReview, restoreReview, reviewKey, reviewTargets, reviewFingerprint, type ExclusionScope } from '../utils/autowireReviewState'

const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ plan: AutowirePlan | null; result: AutowireApplyResult | null; busy: boolean; error: string; pages?: Page[]; loadingMessage?: string }>()
const emit = defineEmits<{ apply: [AutowireDecisions]; recompute: [AutowirePreviewRequest]; retry: []; refresh: [] }>()
const tab = ref('set')
const selected = ref<string[]>([])
const excluded = ref<Record<string, ExclusionScope>>({})
const restores = ref<number[]>([])
const onlyNavigation = ref(false)
const search = ref('')
const activeKey = ref('')
const targetChoices = ref<Record<string, number | undefined>>({})
const originalRelations = ref<Record<string, { action?: string; target?: number | null }>>({})
const groups = [
  { key: 'set', label: '设置关系', empty: '没有需要设置的关系' },
  { key: 'remove', label: '删除旧线', empty: '没有需要删除的旧线' },
  { key: 'blocked', label: '需要处理', empty: '没有需要处理的关系' },
]

watch(() => props.plan, (next, previous) => {
  if (!next || next.previewId === previous?.previewId) return
  // Keep the before/after comparison truthful when loadData refreshes pages
  // after applying a change or deleting the original interaction.
  originalRelations.value = Object.fromEntries(next.items.map(item => {
    const current = props.pages?.find(page => page.id === item.pageId)?.elements.find(element => element.id === item.elementId)?.interaction
    const original = current && item.interactionId != null && current.id === item.interactionId ? current : undefined
    return [item.id, { action: item.navigation?.previousAction || original?.action, target: item.navigation?.previousAction ? item.navigation.previousTargetPageId : original?.target_page_id }]
  }))
  const memory = next.projectId === previous?.projectId ? rememberReview(previous, selected.value, excluded.value, targetChoices.value) : new Map()
  const restored = restoreReview(next, memory)
  selected.value = restored.selected
  excluded.value = restored.excluded
  targetChoices.value = restored.targets
  // Preserve explicit selections only while their relation is unchanged.
  for (const item of next.items) {
    const old = memory.get(reviewKey(item))
    if (old?.selected && old.fingerprint === reviewFingerprint(item) && !excluded.value[item.id] && canApplyItem(item)) toggle(item.id, true)
  }
  selected.value = selected.value.filter(id => next.items.some(item => item.id === id && canApplyItem(item) && !excluded.value[id]))
  restores.value = restores.value.filter(id => next.exclusions.some(item => item.id === id))
  if (next.projectId !== previous?.projectId) { search.value = ''; activeKey.value = ''; tab.value = 'set' }
  if (tab.value !== 'exclusions' && !count(tab.value)) tab.value = groups.find(group => count(group.key))?.key || 'set'
}, { immediate: true })

function bucket(item: AutowireItem) {
  if (item.navigation?.status === 'conflict') return 'blocked'
  if (item.category === 'remove') return 'remove'
  return item.applicable || canChooseTarget(item) ? 'set' : 'blocked'
}
function matchesFilters(item: AutowireItem) {
  const query = search.value.trim().toLocaleLowerCase()
  return (!onlyNavigation.value || !!item.navigation) && (!query || `${item.pageName} ${item.elementLabel} ${targetTitle(item)}`.toLocaleLowerCase().includes(query))
}
function count(key: string) { return props.plan?.items.filter(item => bucket(item) === key && (!onlyNavigation.value || !!item.navigation)).length || 0 }
const filteredItems = computed(() => (props.plan?.items || []).filter(item => bucket(item) === tab.value && matchesFilters(item)))
const pageGroups = computed(() => {
  const grouped = new Map<number, { id: number; name: string; items: AutowireItem[] }>()
  for (const item of filteredItems.value) {
    if (!grouped.has(item.pageId)) grouped.set(item.pageId, { id: item.pageId, name: item.pageName, items: [] })
    grouped.get(item.pageId)!.items.push(item)
  }
  return [...grouped.values()]
})
const activeItem = computed(() => filteredItems.value.find(item => reviewKey(item) === activeKey.value) || filteredItems.value[0])
const activeSourcePage = computed(() => props.pages?.find(page => page.id === activeItem.value?.pageId))
const activeTargetPage = computed(() => activeItem.value ? props.pages?.find(page => page.id === chosenTarget(activeItem.value!)) : undefined)
const candidatePageCount = computed(() => new Set((props.plan?.items || []).map(item => item.pageId)).size)
watch(onlyNavigation, () => { if (tab.value !== 'exclusions' && !count(tab.value)) tab.value = groups.find(group => count(group.key))?.key || 'set' })

const pageNames = computed(() => {
  const names = new Map<number, string>()
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
  return [...pageNames.value].some(([otherId, otherName]) => otherId !== id && otherName === name) ? `${name}（#${id}）` : name
}
function pageThumbnail(id: number) { return getFileUrl(props.pages?.find(page => page.id === id)?.background_image) || undefined }
function previousAction(item: AutowireItem) { return originalRelations.value[item.id]?.action }
function previousTarget(item: AutowireItem) { return originalRelations.value[item.id]?.target }
function previousState(item: AutowireItem) {
  const action = previousAction(item), target = previousTarget(item)
  if (!action) return ''
  if (action === 'tab_switch' || action === 'tab') return '只切换本页内容'
  if (action === 'back') return '返回上一页'
  if (!needsTarget(action)) return actionName(action)
  return target == null ? `${actionName(action)}，尚未关联目标页面` : `${actionName(action)}「${pageTitle(target)}」`
}
function needsTarget(action: string) { return ['navigate', 'popup', 'modal'].includes(action) }
function canChooseTarget(item: AutowireItem) { return item.category !== 'remove' && needsTarget(item.action) && item.navigation?.status !== 'conflict' && reviewTargets(item).length > 0 }
function chosenTarget(item: AutowireItem) {
  const key = reviewKey(item)
  if (Object.prototype.hasOwnProperty.call(targetChoices.value, key)) return targetChoices.value[key]
  // Display a suggestion as a real selected option, never an empty placeholder.
  if (item.targetPageId != null && (item.category === 'remove' || item.applicable || reviewTargets(item).some(target => target.id === item.targetPageId))) return item.targetPageId
  return undefined
}
function canApplyItem(item: AutowireItem) {
  if (item.navigation?.status === 'conflict') return false
  if (item.category === 'remove') return item.applicable
  if (!needsTarget(item.action)) return item.applicable
  const target = chosenTarget(item)
  if (target == null) return false
  return canChooseTarget(item) ? reviewTargets(item).some(candidate => candidate.id === target) : item.applicable
}
function targetUnavailableReason(item: AutowireItem) {
  if (excluded.value[item.id]) return '此关系已设为不再推荐。可以在更多设置中改回“继续推荐”。'
  if (item.navigation?.status === 'conflict') return `现有关系有冲突，暂不能应用。${item.reason || '请先在画布的交互设置中处理。'}`
  if (canChooseTarget(item) && chosenTarget(item) == null) return '先选择右侧的跳转页面，选择后会自动勾选这条关系。'
  if (needsTarget(item.action) && !item.applicable && !reviewTargets(item).length) return `没有可选的目标页面，暂不能应用。${item.reason || '请先确认目标页面已添加。'}`
  return item.reason || '来源控件或现有交互尚未明确，请在画布的交互设置中处理。'
}
function changeName(item: AutowireItem) {
  if (excluded.value[item.id]) return '以后不再推荐'
  if (item.category === 'remove') return '删除现有连线'
  if (!canApplyItem(item)) return canChooseTarget(item) ? '请选择跳转页面' : '需要处理'
  const oldAction = previousAction(item), oldTarget = previousTarget(item), target = chosenTarget(item)
  if (oldAction && oldAction !== item.action) return oldAction === 'tab_switch' || oldAction === 'tab' ? '改为页面跳转' : '修改交互操作'
  if (oldTarget != null && oldTarget !== target) return '修改跳转目标'
  if (item.category === 'add') return item.action === 'back' ? '新增返回关系' : item.action === 'navigate' ? '新增跳转' : '新增弹窗关系'
  if (item.category === 'uncertain') return '可直接应用'
  return item.action === 'navigate' ? '补充跳转' : '补充弹窗关系'
}
function rowStatus(item: AutowireItem) {
  if (excluded.value[item.id]) return '以后不再推荐'
  if (!canApplyItem(item)) return item.navigation?.status === 'conflict' ? '已有关系冲突' : canChooseTarget(item) ? '请先选择目标页面' : needsTarget(item.action) && !reviewTargets(item).length ? '没有可选目标' : '控件或关系需核对'
  return selected.value.includes(item.id) ? item.category === 'remove' ? '已选 · 删除旧线' : '已选 · 应用后保存' : item.category === 'remove' ? '删除旧线' : item.category === 'uncertain' ? '勾选即可应用' : changeName(item)
}
function targetCaption(item: AutowireItem) {
  if (item.category === 'remove') return '原来的目标页面'
  if (item.action === 'back') return '返回到'
  return needsTarget(item.action) ? item.action === 'navigate' ? '跳转到' : '打开弹窗' : '交互结果'
}
function targetTitle(item: AutowireItem) {
  if (item.action === 'back') return '上一页'
  if (!needsTarget(item.action)) return actionName(item.action)
  const target = chosenTarget(item)
  return target == null ? '请选择跳转页面' : pageTitle(target, target === item.targetPageId ? item.targetPageName : undefined)
}
function relationSummary(item: AutowireItem) {
  const source = `在「${pageTitle(item.pageId, item.pageName)}」${triggerName(item.trigger)}「${item.elementLabel || '未命名控件'}」`
  const target = chosenTarget(item)
  const outcome = item.action === 'back' ? '返回上一页' : needsTarget(item.action) ? target == null ? '尚未选择跳转页面' : `${item.action === 'navigate' ? '跳转到' : '打开弹窗'}「${pageTitle(target)}」` : actionName(item.action)
  return item.category === 'remove' ? `删除这条关系：${source} → ${outcome}。` : `${source}，${outcome}。`
}
function targetEmptyTitle(item: AutowireItem) {
  if (item.action === 'back') return '返回上一页'
  if (!needsTarget(item.action)) return actionName(item.action)
  return chosenTarget(item) == null ? '选择目标页面' : '暂无目标预览'
}
function targetEmptyHint(item: AutowireItem) {
  if (item.action === 'back') return '按原型演示的访问顺序返回。'
  if (!needsTarget(item.action)) return '此交互不跳转到其他页面。'
  return canChooseTarget(item) && chosenTarget(item) == null ? '从上方选择控件要跳转到的页面。' : '可以根据页面名称确认这条关系。'
}
function recommendationChoice(item: AutowireItem) { return excluded.value[item.id] === 'element' ? '以后不再推荐此控件的关系' : '以后不再推荐这条关系' }
function eligibleItems(items: AutowireItem[]) { return items.filter(item => canApplyItem(item) && !excluded.value[item.id]) }
function pageFullySelected(items: AutowireItem[]) { const eligible = eligibleItems(items); return !!eligible.length && eligible.every(item => selected.value.includes(item.id)) }
function pagePartiallySelected(items: AutowireItem[]) { const eligible = eligibleItems(items); return eligible.some(item => selected.value.includes(item.id)) && !pageFullySelected(items) }
function choosePage(items: AutowireItem[], on: boolean) { for (const item of eligibleItems(items)) chooseRelation(item, on) }
function toggle(id: string, on: boolean) { selected.value = selected.value.filter(itemId => itemId !== id); if (on) selected.value.push(id) }
function chooseRelation(item: AutowireItem, on: boolean) {
  if (props.busy || props.result || excluded.value[item.id] || (on && !canApplyItem(item))) return
  if (on && canChooseTarget(item) && chosenTarget(item) != null) targetChoices.value[reviewKey(item)] = chosenTarget(item)
  toggle(item.id, on)
  activeKey.value = reviewKey(item)
}
function chooseTarget(item: AutowireItem, value: string) {
  if (props.busy || props.result || excluded.value[item.id] || !canChooseTarget(item)) return
  if (value && !reviewTargets(item).some(target => target.id === Number(value))) return
  targetChoices.value[reviewKey(item)] = value ? Number(value) : undefined
  toggle(item.id, canApplyItem(item))
}
function onExclude(id: string) {
  if (!excluded.value[id]) return
  toggle(id, false)
  const item = props.plan?.items.find(entry => entry.id === id)
  // Exclusions refer to the server's original proposal, not an unsaved target.
  if (item) delete targetChoices.value[reviewKey(item)]
}
const excludeCount = computed(() => Object.values(excluded.value).filter(Boolean).length)
const decisionCount = computed(() => selected.value.length + excludeCount.value + restores.value.length)
const hasDecisions = computed(() => decisionCount.value > 0)
const primaryLabel = computed(() => selected.value.length || !(excludeCount.value || restores.value.length) ? `应用已选（${selected.value.length}）` : '保存推荐设置')
const footerSummary = computed(() => {
  if (props.result) return '关系已保存'
  if (!props.plan) return props.busy ? '正在准备检查结果' : '检查未完成'
  const shownIds = new Set(filteredItems.value.map(item => item.id))
  const hiddenCount = selected.value.filter(id => !shownIds.has(id)).length
  return [`已选 ${selected.value.length} 条关系`, hiddenCount ? `${hiddenCount} 条在当前筛选外` : '', excludeCount.value ? `不再推荐 ${excludeCount.value} 项` : '', restores.value.length ? `恢复推荐 ${restores.value.length} 项` : ''].filter(Boolean).join(' · ')
})
const diagnosticSummary = computed(() => {
  const entries = props.plan?.navigationDiagnostics || []
  const labels: Record<string, string> = { correct: '原跳转已保留', current_page: '当前页无需跳转', local_tab: '保留本页内容切换', excluded: '不再推荐', protected: '原交互已保留', unbound: '导航控件未识别', missing_target: '原目标已不存在' }
  return Object.entries(labels).map(([status, label]) => ({ label, count: entries.filter(entry => entry.status === status).length })).filter(summary => summary.count).map(summary => `${summary.label} ${summary.count} 项`).join(' · ')
})
function recompute() {
  if (props.busy) return
  const choices = (props.plan?.items || []).filter(item => canChooseTarget(item) && !excluded.value[item.id] && targetChoices.value[reviewKey(item)] != null)
  const resolution = (item: AutowireItem) => ({ stableKey: reviewKey(item), targetPageId: targetChoices.value[reviewKey(item)]! })
  emit('recompute', { previousPreviewId: props.plan?.previewId, navigationResolutions: choices.filter(item => !!item.navigation).map(resolution), targetResolutions: choices.filter(item => !item.navigation).map(resolution) })
}
function submit() {
  if (props.busy || props.result || !props.plan || !hasDecisions.value) return
  const chosen = props.plan.items.filter(item => selected.value.includes(item.id))
  if (chosen.some(item => !canApplyItem(item) || !!excluded.value[item.id])) return
  const resolutions = chosen.filter(item => canChooseTarget(item) && chosenTarget(item) != null)
  const resolution = (item: AutowireItem) => ({ stableKey: reviewKey(item), targetPageId: chosenTarget(item)! })
  emit('apply', {
    selectedIds: [...selected.value].sort(),
    exclusions: Object.entries(excluded.value).filter(([, scope]) => !!scope).map(([itemId, scope]) => ({ itemId, scope: scope as 'relation' | 'element' })).sort((a, b) => a.itemId.localeCompare(b.itemId)),
    restoreExclusionIds: [...restores.value].sort((a, b) => a - b),
    navigationResolutions: resolutions.filter(item => !!item.navigation).map(resolution),
    targetResolutions: resolutions.filter(item => !item.navigation).map(resolution),
  })
}
const actionName = (action: string) => ({ navigate: '跳转', popup: '打开弹窗', modal: '打开弹窗', back: '返回', tab_switch: '页内切换', tab: '页内切换', toggle: '切换当前页面的状态', close: '关闭弹窗' }[action] || '执行原有操作')
const triggerName = (trigger: string) => ({ click: '点击', tap: '点击', double_click: '双击', dblclick: '双击', hover: '悬停于', long_press: '长按', longpress: '长按' }[trigger] || `触发（${trigger}）`)
const sourceName = (source: string) => ({ user: '人工关系', ai: '基于原有 AI 识别', ai_inferred: 'AI 建议', autowire: '规则推导', autowire_review: '有明确依据的建议' }[source] || '原有关系')
function evidenceName(ref: string) { const [kind, id] = ref.split(':'); if (kind === 'nav_basis') return basisName(id || ''); return ({ annotation: '业务说明', interaction: '已有交互', control: '明确操作入口', navigation: '已识别的导航项' }[kind] || '关联依据') }
const regionName = (region: string) => ({ bottom: '底部导航', top: '顶部导航', side: '侧边导航' }[region] || '导航入口')
const basisName = (basis: string) => ({ existing_target: '保留已有目标', exact_name: '名称与页面唯一匹配', confirmed_mapping: '同组已确认映射', ai_suggestion: 'AI 建议', user_choice: '人工选择的目标', conflict: '现有关系冲突', unresolved: '需要选择目标', ambiguous: '有多个可能的目标' }[basis] || basis)
</script>

<style scoped>
.review-content{--review-text:#1e293b;--review-muted:#64748b;--review-border:#e2e8f0;--review-bg:#fff;--review-soft:#f8fafc;--review-blue:#0d99ff;--review-blue-soft:#eff6ff;--review-blue-border:#bfdbfe;--review-warning:#92400e;--review-warning-bg:#fffbeb;--review-warning-border:#fde68a;color:var(--review-text);font-size:13px;line-height:1.6;min-height:0}
.review-topline{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:0 0 16px}.review-summary{font-size:12px;color:var(--review-muted)}.review-intro{margin:3px 0 0;color:var(--review-text);font-size:13px}.navigation-filter{display:flex;align-items:center;gap:6px;white-space:nowrap;font-size:12px;color:var(--review-muted)}input[type=checkbox]{accent-color:#0d99ff;cursor:pointer}input[type=checkbox]:disabled{cursor:not-allowed}
.review-tabs{display:flex;gap:5px;margin-bottom:14px;border-bottom:1px solid var(--review-border);overflow-x:auto}.review-tabs button{display:flex;align-items:center;gap:6px;flex-shrink:0;padding:10px 12px 11px;border:0;border-bottom:2px solid transparent;background:none;color:var(--review-muted);font-size:13px;cursor:pointer}.review-tabs button.active{color:var(--review-blue);border-bottom-color:var(--review-blue);font-weight:600}.review-tabs button span{padding:0 5px;border-radius:4px;background:var(--review-soft);font-size:11px;font-weight:400}.review-tabs button.active span{background:var(--review-blue-soft)}
.review-workspace{display:grid;grid-template-columns:300px minmax(0,1fr);min-height:410px;height:min(680px,66vh);border:1px solid var(--review-border);border-radius:10px;overflow:hidden;background:var(--review-bg)}.relation-list{display:flex;flex-direction:column;min-height:0;min-width:0;border-right:1px solid var(--review-border);background:var(--review-soft)}.relation-search{display:flex;align-items:center;gap:7px;margin:14px 12px;padding:7px 9px;background:var(--review-bg);border:1px solid var(--review-border);border-radius:6px;color:var(--review-muted)}.relation-search input{min-width:0;width:100%;border:0;outline:0;background:transparent;color:var(--review-text);font-size:12px}.relation-search:focus-within{outline:2px solid var(--review-blue);outline-offset:1px}.relation-search input::placeholder{color:var(--review-muted)}.relation-list-scroll{overflow:auto;min-height:0;flex:1;padding:0 10px 14px}.page-group+.page-group{margin-top:17px}.page-group-heading{display:flex;align-items:center;gap:7px;margin:0 4px 7px;font-size:11px;color:var(--review-muted)}.page-group-heading input{margin:0;flex-shrink:0}.page-group-heading strong{min-width:0;overflow-wrap:anywhere;font-weight:600}.page-group-heading>span{margin-left:auto;font-size:11px}.relation-row{display:flex;align-items:flex-start;gap:8px;margin-bottom:5px;padding:11px 9px;border:1px solid transparent;border-radius:7px;cursor:pointer;transition:background .15s,border-color .15s}.relation-row:hover{background:var(--review-bg)}.relation-row.active{border-color:var(--review-blue-border);background:var(--review-blue-soft)}.relation-row:focus-visible{outline:2px solid var(--review-blue);outline-offset:1px}.relation-row>input{margin:3px 0 0;flex-shrink:0}.row-thumbnail{display:flex;align-items:center;justify-content:center;width:32px;height:44px;flex-shrink:0;border:1px solid var(--review-border);border-radius:4px;background:var(--review-bg);color:var(--review-muted);overflow:hidden}.row-thumbnail img{width:100%;height:100%;object-fit:contain}.relation-row-copy{display:flex;flex-direction:column;min-width:0;flex:1;gap:3px}.relation-row-copy>strong{font-size:13px;font-weight:600;overflow-wrap:anywhere}.row-destination{display:flex;align-items:center;gap:4px;font-size:12px;color:var(--review-muted);overflow-wrap:anywhere}.row-destination svg{flex-shrink:0}.row-status{font-size:10px;color:var(--review-muted)}.chosen .row-status{color:var(--review-blue)}.row-status.warning{color:var(--review-warning)}.row-status.removal{color:#be123c}.row-chevron{flex-shrink:0;color:var(--review-muted);margin-top:4px}.relation-row.active .row-chevron{color:var(--review-blue)}.relation-row.excluded .relation-row-copy{opacity:.65}.list-footnote{padding:10px 14px;border-top:1px solid var(--review-border);font-size:11px;color:var(--review-muted);background:var(--review-soft)}
.relation-detail{min-height:0;min-width:0;overflow:auto;padding:20px 22px}.detail-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}.detail-heading h3{margin:0;font-size:17px;line-height:1.4;font-weight:600;overflow-wrap:anywhere}.change-tag{padding:3px 8px;background:var(--review-blue-soft);border-radius:5px;color:var(--review-blue);font-size:11px;white-space:nowrap}.change-tag.warning{background:var(--review-warning-bg);color:var(--review-warning)}.change-tag.removal{background:#fff1f2;color:#be123c}.preview-comparison{display:grid;grid-template-columns:minmax(0,1fr) 24px minmax(0,1fr);gap:12px;align-items:center}.comparison-preview{flex:1;min-height:0;height:auto;border:0;border-radius:0}.preview-card{height:clamp(310px,43vh,470px);display:flex;flex-direction:column;min-width:0;border:1px solid var(--review-border);border-radius:8px;overflow:hidden;background:var(--review-soft)}.preview-card>header{display:flex;flex-direction:column;justify-content:center;min-height:68px;padding:9px 12px;border-bottom:1px solid var(--review-border);background:var(--review-bg)}.preview-caption{display:block;color:var(--review-muted);font-size:11px;margin-bottom:4px}.preview-card header>strong{font-size:13px;font-weight:600;overflow-wrap:anywhere}.target-card{border-color:var(--review-blue-border)}.target-card>header{background:var(--review-blue-soft);border-color:var(--review-blue-border)}.target-picker{display:block}.target-picker select{display:block;width:100%;min-width:0;height:32px;padding:4px 25px 4px 7px;background:var(--review-bg);border:1px solid var(--review-blue-border);border-radius:5px;color:var(--review-text);font-size:12px;font-weight:600;cursor:pointer}.target-picker select:focus-visible{outline:2px solid var(--review-blue);outline-offset:1px}.target-picker select:disabled{cursor:not-allowed;opacity:.6}.target-card.missing{border-color:var(--review-warning-border)}.target-card.missing>header{background:var(--review-warning-bg);border-color:var(--review-warning-border)}.target-card.missing select{border-color:var(--review-warning-border);color:var(--review-warning)}.target-card.removal{border-color:#fecdd3}.target-card.removal>header{background:#fff1f2;border-color:#fecdd3}.comparison-arrow{color:var(--review-muted)}.target-empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;min-height:0;padding:24px;color:var(--review-muted);text-align:center}.target-empty strong{font-size:13px;color:var(--review-text);font-weight:500}.target-empty p{font-size:12px;line-height:1.8;margin:0;max-width:210px}.detail-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:30px;color:var(--review-muted);font-size:13px;text-align:center}
.relation-sentence{padding:13px 15px;margin-top:17px;background:var(--review-blue-soft);border:1px solid var(--review-blue-border);border-radius:7px}.sentence-label{font-size:11px;color:var(--review-muted)}.relation-sentence p{margin:4px 0 0;line-height:1.8;font-size:13px;font-weight:500;overflow-wrap:anywhere}.relation-sentence.removal{background:#fff1f2;border-color:#fecdd3}.selected-indicator{display:inline-flex;align-items:center;gap:4px;margin-top:6px;color:var(--review-blue);font-size:11px}.previous-relation{display:flex;align-items:baseline;gap:9px;margin:6px 0 0;font-size:12px}.previous-relation>span{color:var(--review-muted);flex-shrink:0}.previous-relation p{margin:0;font-size:12px;font-weight:400;color:var(--review-muted);overflow-wrap:anywhere}.item-more{margin-top:12px;padding-top:10px;border-top:1px solid var(--review-border);font-size:12px;color:var(--review-muted)}.item-more>summary{width:fit-content;cursor:pointer}.exclude-label{display:flex;align-items:center;gap:10px;margin-top:12px;flex-wrap:wrap}.exclude-label select{min-width:180px;max-width:100%;padding:5px 8px;background:var(--review-bg);border:1px solid var(--review-border);border-radius:5px;color:var(--review-text);font-size:12px}.preference-effect{color:var(--review-warning);font-size:12px;margin:7px 0}.recommendation-evidence{margin-top:14px}.recommendation-evidence>span{font-size:11px}.recommendation-evidence p{margin:4px 0;color:var(--review-text);font-size:12px}.recommendation-evidence p.muted{color:var(--review-muted)}.notice.needs-attention{color:var(--review-warning);background:var(--review-warning-bg);border-color:var(--review-warning-border)}
.exclusions-panel{height:min(570px,58vh);min-height:300px;overflow:auto;padding:14px;border:1px solid var(--review-border);border-radius:10px}.exclusion-row{padding:14px;margin-top:10px;border:1px solid var(--review-border);border-radius:7px}.exclusion-row>label{display:flex;align-items:flex-start;gap:11px;cursor:pointer}.exclusion-row input{margin-top:4px}.exclusion-row label>div{flex:1;min-width:0}.exclusion-row strong{font-weight:500;font-size:13px;overflow-wrap:anywhere}.exclusion-row p{font-size:12px;margin:4px 0;color:var(--review-muted)}.exclusion-row label>span{font-size:12px;white-space:nowrap;color:var(--review-blue)}.navigation-diagnostics{margin-top:12px;padding:9px 12px;border:1px solid var(--review-border);border-radius:7px;font-size:12px}.navigation-diagnostics summary{cursor:pointer;color:var(--review-muted)}.navigation-diagnostics p{margin:6px 0}.muted{color:#64748b;font-size:12px;margin:4px 0}.notice{padding:10px 12px;border:1px solid #bfdbfe;background:#eff6ff;border-radius:6px;margin:10px 0;font-size:12px}.notice.error{border-color:#fecaca;background:#fef2f2;color:#b91c1c}.empty{margin:0;text-align:center;padding:38px 12px;color:#64748b;font-size:12px}.review-progress{display:flex;align-items:center;gap:9px}.progress-spinner{width:14px;height:14px;flex-shrink:0;border:2px solid #bfdbfe;border-top-color:#0d99ff;border-radius:50%;animation:review-spin 1s linear infinite}@keyframes review-spin{to{transform:rotate(360deg)}}.footer{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.actions{display:flex;gap:8px}.actions .el-button+.el-button{margin-left:0}
:global(.autowire-dialog){max-height:92vh;display:flex;flex-direction:column;margin-bottom:0}:global(.autowire-dialog .el-dialog__body){min-height:0;overflow:auto;padding-top:10px}:global(.autowire-dialog .el-dialog__header),:global(.autowire-dialog .el-dialog__footer){flex-shrink:0}:global(.autowire-dialog .el-dialog__footer){padding-top:14px;border-top:1px solid #e2e8f0}
:global(.dark .autowire-dialog){background:#25272b}:global(.dark .autowire-dialog .el-dialog__title){color:#e5e7eb}:global(.dark .autowire-dialog .el-dialog__footer){border-color:#41464f}:global(.dark .autowire-dialog .review-content){--review-text:#e5e7eb;--review-muted:#a6afbc;--review-border:#41464f;--review-bg:#292c31;--review-soft:#24272c;--review-blue:#75bcff;--review-blue-soft:#1a3047;--review-blue-border:#385d80;--review-warning:#fde68a;--review-warning-bg:#3a301e;--review-warning-border:#70592d}:global(.dark .autowire-dialog .notice){background:#1a3047;border-color:#385d80;color:#dbeafe}:global(.dark .autowire-dialog .notice.error){background:#3f2427;border-color:#783434;color:#fca5a5}:global(.dark .autowire-dialog .notice.needs-attention){background:#3a301e;border-color:#70592d;color:#fde68a}:global(.dark .autowire-dialog .muted),:global(.dark .autowire-dialog .empty){color:#a6afbc}:global(.dark .autowire-dialog .row-status.removal),:global(.dark .autowire-dialog .change-tag.removal){color:#fda4af}:global(.dark .autowire-dialog .change-tag.removal),:global(.dark .autowire-dialog .relation-sentence.removal),:global(.dark .autowire-dialog .target-card.removal>header){background:#3f2427}:global(.dark .autowire-dialog .relation-sentence.removal),:global(.dark .autowire-dialog .target-card.removal),:global(.dark .autowire-dialog .target-card.removal>header){border-color:#783434}
@media(max-width:900px){.review-workspace{grid-template-columns:245px minmax(0,1fr)}.relation-detail{padding:16px}.preview-comparison{gap:8px;grid-template-columns:minmax(0,1fr) 16px minmax(0,1fr)}.comparison-arrow{width:16px}.preview-card>header{padding:9px}.target-picker select{font-size:11px}.preview-card{height:320px}.target-empty{min-height:0;padding:15px}.detail-heading h3{font-size:16px}.change-tag{font-size:10px}.row-thumbnail{width:26px;height:38px}.relation-row{gap:6px;padding-left:7px;padding-right:7px}}
@media(max-width:680px){.review-topline{align-items:flex-start;gap:10px;margin-bottom:8px}.review-intro{font-size:12px}.navigation-filter{font-size:11px;margin-top:2px}.review-tabs button{font-size:12px;padding:9px 8px}.review-workspace{display:flex;flex-direction:column;height:auto;min-height:0;overflow:visible}.relation-list{border-right:0;border-bottom:1px solid var(--review-border);max-height:220px}.relation-search{margin:9px}.relation-list-scroll{padding:0 7px 9px}.page-group+.page-group{margin-top:12px}.relation-row{padding:8px}.row-thumbnail{width:25px;height:32px}.relation-row-copy{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:1px 8px}.relation-row-copy>strong{font-size:12px}.row-destination{font-size:11px}.row-status{grid-column:1/-1}.list-footnote{display:none}.relation-detail{overflow:visible;padding:14px 12px}.detail-heading{margin-bottom:12px}.preview-comparison{gap:6px;grid-template-columns:minmax(0,1fr) 14px minmax(0,1fr)}.preview-card>header{min-height:70px;padding:8px}.preview-card header>strong{font-size:12px}.preview-caption{font-size:10px}.target-picker select{height:30px;padding-left:4px;padding-right:14px}.comparison-arrow{width:14px}.preview-card{height:290px}.target-empty{min-height:0;padding:12px 8px}.target-empty p{font-size:11px}.relation-sentence{padding:11px;margin-top:13px}.relation-sentence p{font-size:12px}.footer{gap:8px}.footer>.muted{font-size:11px}.actions{margin-left:auto}.navigation-diagnostics{margin-top:9px}.exclusions-panel{min-height:240px;height:auto;max-height:48vh}.exclusion-row{padding:10px}.exclusion-row label>span{font-size:11px}:global(.autowire-dialog .el-dialog__body){padding:10px 12px}:global(.autowire-dialog .el-dialog__header){padding:16px 12px 12px}:global(.autowire-dialog .el-dialog__footer){padding:12px}}
</style>
