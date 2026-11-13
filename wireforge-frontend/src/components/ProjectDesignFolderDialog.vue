<template>
  <el-dialog v-model="visible" :title="t('designFolderDialogTitle')" width="min(480px, calc(100vw - 32px))" align-center append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <form @submit.prevent="save">
      <DesignFolderPicker v-if="modelValue" v-model="folder" :disabled="saving" />
      <p v-if="error" class="folder-dialog-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!folder || !project" @click="save">{{ t('designFolderConfirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import DesignFolderPicker from './DesignFolderPicker.vue'
import { projectApi } from '../api/project'
import { t } from '../utils/i18n'
import type { Project } from '../types'

const props = defineProps<{ modelValue: boolean; project: Project | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved', project: Project): void }>()
const saving = ref(false)
const folder = ref('')
const error = ref('')
const visible = computed({ get: () => props.modelValue, set: value => { if (!saving.value) emit('update:modelValue', value) } })
watch(() => [props.modelValue, props.project?.id] as const, ([open]) => {
  if (!open || saving.value) return
  folder.value = props.project?.designFolder || ''
  error.value = ''
}, { immediate: true })

async function save() {
  if (saving.value || !props.project) return
  if (!folder.value) { error.value = t('designFolderRequired'); return }
  saving.value = true
  error.value = ''
  try {
    const saved = await projectApi.setDesignFolder(props.project.id, folder.value)
    emit('update:modelValue', false)
    emit('saved', saved)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : t('projectInfoSaveFailed')
  } finally { saving.value = false }
}
</script>

<style scoped>
.folder-dialog-error { color: var(--el-color-danger); font-size: 13px; line-height: 1.6; margin: 12px 0 0; }
</style>
