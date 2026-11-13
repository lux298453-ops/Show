<template>
  <div class="design-folder-picker">
    <label :for="inputId">{{ t('designFolder') }}</label>
    <div class="folder-select-row">
      <el-select
        :id="inputId"
        :model-value="modelValue || ''"
        :aria-label="t('designFolder')"
        :placeholder="t('designFolderPlaceholder')"
        :loading="loading"
        :disabled="disabled"
        filterable
        clearable
        @update:model-value="emit('update:modelValue', $event || '')"
      >
        <el-option v-if="!loading && !error && modelValue && !folders.includes(modelValue)" :value="modelValue" :label="`${modelValue} (${t('designFolderMissing')})`" disabled />
        <el-option v-for="folder in folders" :key="folder" :value="folder" :label="folder === '.' ? t('designFolderRoot') : folder" />
      </el-select>
      <el-button :disabled="disabled || loading" :aria-label="t('designFolderRefresh')" :title="t('designFolderRefresh')" @click="loadFolders">
        <RefreshCw :size="15" aria-hidden="true" />
      </el-button>
    </div>
    <p v-if="error" role="alert" class="folder-error">{{ error }}</p>
    <p v-else class="folder-hint" :class="{ 'folder-root-hint': modelValue === '.' }">{{ t(modelValue === '.' ? 'designFolderRootHint' : 'designFolderHint') }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { projectApi } from '../api/project'
import { t } from '../utils/i18n'

defineProps<{ modelValue?: string | null; disabled?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', folder: string): void }>()
const inputId = `design-folder-${useId()}`
const folders = ref<string[]>([])
const loading = ref(false)
const error = ref('')
let controller: AbortController | null = null

async function loadFolders() {
  if (loading.value) return
  const request = new AbortController()
  controller = request
  loading.value = true
  error.value = ''
  try {
    const result = await projectApi.designFolders(request.signal)
    if (!request.signal.aborted) folders.value = result
  } catch (e: unknown) {
    if (!request.signal.aborted) error.value = e instanceof Error ? e.message : t('designFolderLoadError')
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

onMounted(loadFolders)
onUnmounted(() => controller?.abort())
</script>

<style scoped>
.design-folder-picker { display: grid; gap: 8px; min-width: 0; }
.design-folder-picker > label { color: var(--el-text-color-primary); font-size: 13px; font-weight: 500; }
.folder-select-row { display: flex; gap: 8px; min-width: 0; }
.folder-select-row :deep(.el-select) { flex: 1; min-width: 0; }
.folder-select-row > .el-button { padding: 8px 10px; flex-shrink: 0; }
.folder-hint, .folder-error { margin: 0; font-size: 12px; line-height: 1.6; }
.folder-hint { color: var(--el-text-color-secondary); }
.folder-root-hint { color: var(--el-color-warning); }
.folder-error { color: var(--el-color-danger); }
</style>
