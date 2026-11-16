<template>
  <span class="wf-account-avatar" aria-hidden="true">
    <img v-if="src && !failed" :src="src" alt="" @error="failed = true" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{ name: string; src?: string }>()
const failed = ref(false)
const initial = computed(() => Array.from(props.name.trim())[0]?.toUpperCase() || '?')
watch(() => props.src, () => { failed.value = false })
</script>

<style scoped>
.wf-account-avatar {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 28px;
  height: 28px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--wf-auth-blue-soft, #edf7ff);
  color: var(--wf-auth-blue, #0b7ed4);
  font-size: 12px;
  font-weight: 500;
}
.wf-account-avatar img { width: 100%; height: 100%; object-fit: cover; }
</style>
