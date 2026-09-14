<template>
  <div class="proto-node" :class="{ collapsed }">
    <!-- 节点头部（参考 HTML：类型徽章 + 标题 + 标注数 + 折叠） -->
    <div class="node-header" @dblclick.stop="collapsed = !collapsed">
      <span class="node-type-badge badge-wireframe">线框</span>
      <span class="node-title">{{ page.name }}</span>
      <span v-if="page.annotations.length" class="ann-count">📝 {{ page.annotations.length }}</span>
      <span class="collapse-btn" @click.stop="collapsed = !collapsed">{{ collapsed ? '▸' : '▾' }}</span>
    </div>

    <div v-show="!collapsed" class="node-body">
      <PageCanvas
        :page="page"
        :show-wireframe="editor.showWireframe"
        :show-annotations="editor.showAnnotations"
        :selected-element-id="editor.selectedElementId"
        :hovered-element-id="editor.hoveredElementId"
        :hovered-ann-id="editor.hoveredAnnId"
        :canvas-scale="0.5"
        :left-w="150"
        :right-w="150"
        :box-w="140"
        :gap="16"
        @element-click="(el) => $emit('elementClick', page, el)"
        @element-hover="(el, on) => $emit('elementHover', el, on)"
        @ann-hover="$emit('annHover', $event)"
        @ann-click="(annId) => $emit('annClick', page, annId)"
        @ann-dbl-click="$emit('annDblClick', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import type { NodeProps } from '@vue-flow/core'
import type { Annotation, Element, Page } from '../types'
import PageCanvas from './PageCanvas.vue'

export interface EditorState {
  showWireframe: boolean
  showAnnotations: boolean
  selectedElementId: number | null
  hoveredElementId: number | null
  hoveredAnnId: number | null
}

const props = defineProps<NodeProps & { data: { page: Page } }>()

const emit = defineEmits<{
  (e: 'elementClick', page: Page, el: Element): void
  (e: 'elementHover', el: Element, on: boolean): void
  (e: 'annHover', annId: number | null): void
  (e: 'annClick', page: Page, annId: number): void
  (e: 'annDblClick', ann: Annotation): void
}>()

const editor = inject<EditorState>('editorState')!
const page = computed(() => props.data.page)
const collapsed = ref(false)
watch(
  () => props.data.page.id,
  () => {
    collapsed.value = false
  },
)
</script>

<style scoped lang="scss">
.proto-node {
  background: var(--bg-node, #ffffff);
  border: 1px solid var(--border-node, #e5e7eb);
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;

  &.collapsed {
    .node-body {
      display: none;
    }
  }
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-node-header, #f3f4f6);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
  cursor: grab;
  user-select: none;
}

.node-type-badge {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;

  &.badge-wireframe {
    background: rgba(0, 230, 118, 0.15);
    color: #10b981;
  }
}

.node-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ann-count {
  font-size: 10px;
  color: #6b7280;
  background: rgba(0, 0, 0, 0.04);
  padding: 1px 6px;
  border-radius: 8px;
}

.collapse-btn {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #6b7280;
  font-size: 10px;
  border-radius: 3px;

  &:hover {
    background: rgba(0, 0, 0, 0.08);
    color: #fff;
  }
}

.node-body {
  padding: 6px;
  background: #f8fafc;
}
</style>