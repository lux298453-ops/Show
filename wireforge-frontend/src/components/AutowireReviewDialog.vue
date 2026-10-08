<template>
  <el-dialog v-model="visible" title="审核智能连线" width="min(960px, 94vw)" top="5vh" append-to-body
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" class="autowire-dialog">
    <div class="review-content" v-if="plan">
      <p class="intro">优先补全已有关系。普通展示卡片不自动连线，人工关系保持不变。</p>
      <p class="muted">已保护 {{ plan.protectedCount }} 条人工关系。取消勾选仅跳过本次；选择排除才会阻止后续自动生成。</p>
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <div v-for="warning in plan.warnings" :key="warning" class="notice">{{ warning }}</div>
      <div v-if="result" class="notice" role="status">
        已保存：补全 {{ result.completed }} 条，新增 {{ result.added }} 条，移除 {{ result.removed }} 条，排除 {{ result.excluded }} 项。
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
            <article v-for="item in plan.items.filter(i => i.category === group.key)" :key="item.id" class="item">
              <label class="choice">
                <input type="checkbox" :checked="selected.includes(item.id)" :disabled="!item.applicable || busy || !!result || !!excluded[item.id]"
                  :aria-label="`应用 ${item.pageName} · ${item.elementLabel || '未命名元素'}`" @change="toggle(item.id, ($event.target as HTMLInputElement).checked)" />
                <div class="relation">
                  <strong>{{ item.elementLabel || '未命名元素' }}</strong>
                  <span class="destination">{{ actionName(item.action) }} → {{ item.targetPageName }}</span>
                  <span class="muted">{{ item.pageName }} · {{ sourceName(item.source) }}</span>
                </div>
              </label>
              <p class="reason">{{ item.reason }}</p>
              <p v-if="item.evidenceRefs.length" class="muted evidence">依据：{{ item.evidenceRefs.map(evidenceName).join('、') }}</p>
              <label class="exclude-label">自动连线
                <select v-model="excluded[item.id]" :disabled="busy || !!result" @change="onExclude(item.id)" :aria-label="`排除 ${item.pageName} · ${item.elementLabel || '未命名元素'}`">
                  <option value="">不排除</option>
                  <option v-if="item.targetPageId != null || item.action === 'back'" value="relation">排除这条关系</option>
                  <option value="element">排除此元素的自动连线</option>
                </select>
              </label>
            </article>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`已排除 ${plan.exclusions.length}`" name="exclusions">
          <div class="items">
            <p class="muted">恢复后需重新预检，才能重新生成候选。失效记录不会模糊绑定到新元素。</p>
            <p v-if="!plan.exclusions.length" class="empty">没有已排除的自动关系</p>
            <article v-for="item in plan.exclusions" :key="item.id" class="item">
              <label class="choice">
                <input type="checkbox" v-model="restores" :value="item.id" :disabled="busy || !!result" :aria-label="`恢复 ${item.elementLabel}`" />
                <div class="relation"><strong>{{ item.elementLabel || '未命名元素' }}</strong><span class="muted">{{ item.pageName }} · {{ item.scope === 'element' ? '整个元素' : '指定关系' }} · {{ item.matched ? '有效' : '元素已变化，待确认' }}</span></div>
              </label>
              <p class="reason">{{ item.reason }}</p>
            </article>
          </div>
        </el-tab-pane>
      </el-tabs>
      <p v-if="excludeCount" class="notice">应用排除会移除对应的自动关系；人工关系和来源不明的旧关系会保留。</p>
    </div>
    <template #footer>
      <div class="footer">
        <span class="muted">{{ result ? '变更已保存' : `将应用 ${selected.length} 项 · 排除 ${excludeCount} 项 · 恢复 ${restores.length} 项` }}</span>
        <div class="actions">
          <el-button :disabled="busy" @click="visible = false">{{ result ? '完成' : '取消' }}</el-button>
          <el-button :disabled="busy" @click="$emit('recompute')">重新预检</el-button>
          <el-button v-if="!result" type="primary" :loading="busy" :disabled="!plan || !hasDecisions" @click="submit">应用已审核结果</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AutowirePlan, AutowireApplyResult, AutowireDecisions, AutowireItem } from '../types/autowire'
const visible = defineModel<boolean>({ default: false })
const props = defineProps<{ plan: AutowirePlan | null; result: AutowireApplyResult | null; busy: boolean; error: string }>()
const emit = defineEmits<{ apply: [AutowireDecisions]; recompute: []; retry: []; refresh: [] }>()
const tab = ref('complete')
const selected = ref<string[]>([])
const excluded = ref<Record<string, '' | 'relation' | 'element'>>({})
const restores = ref<number[]>([])
const groups: { key: AutowireItem['category']; label: string; empty: string }[] = [
  { key: 'complete', label: '补全目标', empty: '没有需要补全的明确关系' },
  { key: 'add', label: '新增关系', empty: '没有有依据的新关系' },
  { key: 'remove', label: '建议移除', empty: '没有建议移除的自动关系' },
  { key: 'uncertain', label: '无法确定', empty: '没有待确认关系' },
]
watch(() => props.plan?.previewId, () => {
  selected.value = props.plan?.items.filter(i => i.applicable && i.selectedByDefault).map(i => i.id) || []
  excluded.value = Object.fromEntries(props.plan?.items.map(i => [i.id, '']) || []); restores.value = []
  tab.value = groups.find(g => props.plan?.items.some(i => i.category === g.key))?.key || 'complete'
})
const count = (key: string) => props.plan?.items.filter(i => i.category === key).length || 0
const excludeCount = computed(() => Object.values(excluded.value).filter(Boolean).length)
const hasDecisions = computed(() => !!(selected.value.length || excludeCount.value || restores.value.length))
function toggle(id: string, on: boolean) { selected.value = selected.value.filter(i => i !== id); if (on) selected.value.push(id) }
function onExclude(id: string) { if (excluded.value[id]) toggle(id, false) }
function submit() {
  emit('apply', { selectedIds: [...selected.value].sort(), exclusions: Object.entries(excluded.value).filter(([, scope]) => !!scope).map(([itemId, scope]) => ({ itemId, scope: scope as 'relation' | 'element' })).sort((a, b) => a.itemId.localeCompare(b.itemId)), restoreExclusionIds: [...restores.value].sort((a,b) => a-b) })
}
const actionName = (action: string) => ({ navigate: '跳转', popup: '打开弹窗', modal: '打开弹窗', back: '返回' }[action] || action)
const sourceName = (source: string) => ({ user: '人工关系', ai: '识别关系', ai_inferred: 'AI 建议', autowire: '规则关系', autowire_review: '已审核规则' }[source] || '旧关系')
function evidenceName(ref: string) { const [kind, id] = ref.split(':'); return `${({ annotation: '业务标注', interaction: '已有关系', control: '明确入口' }[kind] || '依据')} #${id}` }
</script>

<style scoped>
:global(.autowire-dialog){max-height:90vh;display:flex;flex-direction:column;margin-bottom:0}:global(.autowire-dialog .el-dialog__body){min-height:0;overflow:auto}:global(.autowire-dialog .el-dialog__header),:global(.autowire-dialog .el-dialog__footer){flex-shrink:0}
.review-content{color:#1f2937;font-size:13px;line-height:1.6}.intro{margin:0 0 4px;font-weight:500}.muted{color:#6b7280;font-size:12px;margin:4px 0}.notice{padding:10px 12px;border:1px solid #bfdbfe;background:#eff6ff;border-radius:6px;margin:10px 0;font-size:12px}.error{border-color:#fecaca;background:#fef2f2;color:#b91c1c}.items{max-height:48vh;overflow:auto;padding:2px}.empty{text-align:center;padding:32px;color:#6b7280}.item{border:1px solid #e5e7eb;border-radius:8px;padding:12px;margin-bottom:10px}.choice{display:flex;align-items:flex-start;gap:10px;cursor:pointer}.choice input{margin-top:5px;accent-color:#0d99ff}.relation{display:flex;flex-wrap:wrap;align-items:center;gap:6px 12px;min-width:0}.relation strong{font-size:13px;font-weight:500;overflow-wrap:anywhere}.relation>.muted{width:100%}.destination{font-size:12px;color:#2563eb}.reason{margin:6px 0 4px 24px;font-size:12px}.evidence{margin-left:24px}.exclude-label{display:flex;align-items:center;gap:8px;margin:8px 0 0 24px;font-size:12px;color:#6b7280}.exclude-label select{padding:5px 8px;border:1px solid #d1d5db;border-radius:5px;background:#fff;color:#374151;font-size:12px}.footer{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.actions{display:flex;gap:8px}:global(.dark .autowire-dialog){background:#252525}:global(.dark .autowire-dialog .el-dialog__title),:global(.dark .autowire-dialog .review-content){color:#e5e7eb}:global(.dark .autowire-dialog .item){border-color:#454545}:global(.dark .autowire-dialog .muted),:global(.dark .autowire-dialog .exclude-label){color:#a6abb5}:global(.dark .autowire-dialog .notice){background:#162c40;border-color:#31506c}:global(.dark .autowire-dialog .error){background:#3f2020;border-color:#783434;color:#fca5a5}:global(.dark .autowire-dialog select){background:#303136;border-color:#555;color:#e5e7eb}
</style>
