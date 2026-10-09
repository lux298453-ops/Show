<template>
  <el-dialog
    v-model="visible"
    :title="t('editProjectInfo')"
    width="min(480px, calc(100vw - 32px))"
    align-center
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    :before-close="beforeClose"
    class="project-metadata-dialog"
    @opened="nameInput?.focus()"
  >
    <form id="project-metadata-form" class="metadata-form" novalidate @submit.prevent="save" @keydown.ctrl.enter.prevent="save">
      <label for="project-metadata-name">{{ t('projectName') }} <span class="required">*</span></label>
      <el-input id="project-metadata-name" ref="nameInput" v-model="name" maxlength="255" :disabled="saving" :placeholder="t('projectNamePlaceholder')" />
      <label for="project-metadata-description">{{ t('projectDesc') }}</label>
      <el-input id="project-metadata-description" v-model="description" type="textarea" :rows="4" :disabled="saving" :placeholder="t('projectDescPlaceholder')" />
      <p v-if="error" class="metadata-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <div class="metadata-actions">
        <el-button :disabled="saving" @click="visible = false">{{ t('cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="!editingId" @click="save">{{ saving ? t('projectInfoSaving') : t('saveProjectInfo') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { projectApi } from '../api/project'
import { t } from '../utils/i18n'
import type { Project } from '../types'

const props = defineProps<{ modelValue: boolean; project: Project | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved', project: Project): void }>()
const visible = computed({ get: () => props.modelValue, set: value => { if (!saving.value) emit('update:modelValue', value) } })
const nameInput = ref<{ focus: () => void } | null>(null)
const editingId = ref<number | null>(null)
const name = ref('')
const description = ref('')
const saving = ref(false)
const error = ref('')

watch(() => [props.modelValue, props.project?.id] as const, ([open]) => {
  if (!open || saving.value) return
  editingId.value = props.project?.id ?? null
  name.value = props.project?.name || ''
  description.value = props.project?.description || ''
  error.value = ''
}, { immediate: true })

function beforeClose(done: () => void) { if (!saving.value) done() }
async function save() {
  if (saving.value || !editingId.value) return
  if (!name.value.trim()) { error.value = t('projectNameRequired'); nameInput.value?.focus(); return }
  saving.value = true
  error.value = ''
  try {
    const project = await projectApi.update(editingId.value, { name: name.value.trim(), description: description.value.trim() })
    emit('saved', project)
    emit('update:modelValue', false)
    ElMessage.success(t('projectInfoSaved'))
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : t('projectInfoSaveFailed')
  } finally { saving.value = false }
}
</script>

<style scoped>
.metadata-form { display: grid; gap: 10px; padding: 4px 0; }
.metadata-form label { color: var(--el-text-color-primary); font-size: 13px; font-weight: 500; }
.metadata-form label:not(:first-child) { margin-top: 10px; }
.required, .metadata-error { color: var(--el-color-danger); }
.metadata-error { margin: 2px 0 0; font-size: 13px; line-height: 1.6; }
.metadata-actions { display: flex; justify-content: flex-end; gap: 8px; }
.metadata-actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
