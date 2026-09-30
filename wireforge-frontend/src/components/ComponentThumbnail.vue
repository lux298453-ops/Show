<template>
  <div class="wf-component-thumbnail" aria-hidden="true">
    <div class="wf-component-sample" v-html="html"></div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ html: string }>()
</script>

<style scoped>
.wf-component-thumbnail {
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  pointer-events: none;
  background: #f5f6f8;
  border-bottom: 1px solid var(--editor-border, #e6e8eb);
}
.wf-component-sample { transform: scale(1.2); transform-origin: center; flex-shrink: 0; }
.wf-component-sample :deep(svg) { shape-rendering: geometricPrecision; }
:global(.dark .wf-component-thumbnail) { background: #292b30; }
:global(.dark .wf-component-sample) { background: #f5f6f8; padding: 4px; border-radius: 4px; }
</style>
