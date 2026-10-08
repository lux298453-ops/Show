<template>
  <el-dialog
    v-model="visible"
    :title="annotation ? '编辑说明标注' : '添加说明标注'"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving && !deleting"
    :show-close="!saving && !deleting"
    :before-close="beforeClose"
    class="annotation-create-dialog"
    @opened="titleInput?.focus()"
  >
    <form v-if="page" class="annotation-form" @submit.prevent="submit" @keydown.ctrl.enter.prevent="submit">
      <p class="annotation-page">{{ page.name }} <span>· 功能与业务说明</span></p>
      <label for="annotation-target">关联对象</label>
      <select id="annotation-target" v-model="elementId" :disabled="saving || !!annotation">
        <option :value="null">整个页面（不关联元素）</option>
        <option v-for="el in page.elements" :key="el.id" :value="el.id">
          {{ el.label || '未命名元素' }} · {{ typeName(el.type) }}
        </option>
      </select>
      <label for="annotation-title">说明标题</label>
      <input id="annotation-title" ref="titleInput" v-model="title" maxlength="200" :disabled="saving" placeholder="例如：兑换条件、商品展示规则" />
      <label for="annotation-content">说明内容 <span class="required">*</span></label>
      <textarea id="annotation-content" v-model="text" rows="6" maxlength="5000" :disabled="saving" required placeholder="写下这个功能的用途、操作步骤、限制条件或需要注意的状态…" />
      <div class="annotation-form-hint"><span>说明会显示在画布和原型预览的说明列表中。</span><span>{{ text.length }}/5000</span></div>
      <p v-if="error" class="annotation-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <div class="annotation-footer">
        <el-button v-if="annotation" type="danger" plain :disabled="saving" :loading="deleting" @click="emit('delete', annotation.id)">删除说明</el-button>
        <span v-else></span>
        <div>
          <el-button :disabled="saving || deleting" @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="deleting || !text.trim() || !page" @click="submit">{{ saving ? '保存中…' : annotation ? '保存说明' : '添加说明' }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { projectApi } from '../api/project'
import type { Annotation, Page } from '../types'

const props = defineProps<{ modelValue: boolean; projectId: number; page: Page | null; initialElementId?: number | null; annotation?: Annotation | null; deleting?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'created', pageId: number, annotation: Annotation): void; (e: 'updated', annotation: Annotation): void; (e: 'delete', annId: number): void }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const titleInput = ref<HTMLInputElement | null>(null)
const elementId = ref<number | null>(null)
const title = ref('')
const text = ref('')
const error = ref('')
const saving = ref(false)
function typeName(type: string) {
  return ({ button: '按钮', container: '容器', text: '文字', image: '图片', icon: '图标', input: '输入框', navbar: '导航', switch: '开关', checkbox: '选项' } as Record<string, string>)[type] || '元素'
}
watch(() => props.modelValue, open => {
  if (!open) return
  const target = props.annotation?.element_id ?? props.initialElementId
  elementId.value = props.page?.elements.some(e => e.id === target) ? target! : null
  title.value = props.annotation?.title || props.page?.elements.find(e => e.id === elementId.value)?.label || ''
  text.value = props.annotation?.text || ''
  error.value = ''
})
function beforeClose(done: () => void) { if (!saving.value && !props.deleting) done() }
async function submit() {
  if (saving.value || props.deleting || !props.page || !text.value.trim()) return
  const pageId = props.page.id
  saving.value = true
  error.value = ''
  try {
    const body = { elementId: elementId.value, title: title.value.trim(), text: text.value.trim() }
    if (props.annotation) {
      await projectApi.updateAnnotation(props.projectId, props.annotation.id, { title: body.title, text: body.text })
      emit('updated', { ...props.annotation, title: body.title, text: body.text, source: 'user' })
    } else {
      const annotation = await projectApi.createAnnotation(props.projectId, pageId, body)
      emit('created', pageId, annotation)
    }
    visible.value = false
  } catch (e: any) {
    error.value = e?.message || '说明保存失败，请重试。'
  } finally { saving.value = false }
}
</script>

<style scoped>
:global(.annotation-create-dialog) { max-width: calc(100vw - 32px); }
.annotation-form { display: flex; flex-direction: column; gap: 9px; }
.annotation-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.annotation-page { margin: 0 0 8px; color: var(--el-text-color-primary); font-weight: 500; }
.annotation-page span { color: var(--el-text-color-secondary); font-weight: 400; }
.annotation-form label { color: var(--el-text-color-primary); font-size: 12px; font-weight: 500; }
.annotation-form input, .annotation-form textarea, .annotation-form select { width: 100%; padding: 10px 12px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); color: var(--el-text-color-primary); font: inherit; font-size: 13px; outline: none; }
.annotation-form textarea { resize: vertical; line-height: 1.7; min-height: 130px; max-height: 260px; }
.annotation-form input:focus, .annotation-form textarea:focus, .annotation-form select:focus { border-color: var(--el-color-primary); }
.annotation-form-hint { display: flex; justify-content: space-between; gap: 12px; color: var(--el-text-color-secondary); font-size: 11px; }
.required, .annotation-error { color: var(--el-color-danger); }
</style>
