<template>
  <div class="h-full flex flex-col overflow-hidden bg-slate-100 text-slate-800 select-none selection:bg-emerald-500 selection:text-white">
    <!-- ===== Professional SaaS Top Toolbar ===== -->
    <header class="h-13 wf-glass-panel px-4 flex items-center justify-between z-40 shrink-0 shadow-2xs">
      <!-- Left: Back & Project Info -->
      <div class="flex items-center gap-3">
        <button
          class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-white/70 hover:bg-white border border-slate-200/80 rounded-xl shadow-2xs transition-all cursor-pointer"
          @click="router.push(`/projects/${id}`)"
        >
          <ArrowLeft class="w-3.5 h-3.5" />
          <span>返回项目</span>
        </button>

        <div class="h-4 w-[1px] bg-slate-200"></div>

        <div class="flex items-center gap-2">
          <span class="text-xs font-bold text-slate-900 tracking-tight truncate max-w-[180px] sm:max-w-xs">
            {{ proto?.project.name || '原型画板' }}
          </span>
          <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 border border-slate-200 uppercase tracking-wider">
            交互模式
          </span>
        </div>
      </div>

      <!-- Center: Clean Space -->
      <div class="flex-1"></div>

      <!-- Right: Preview CTA Dropdown (Figma Style) -->
      <div class="flex items-center gap-3">
        <el-dropdown trigger="click" @command="handlePreviewCommand">
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 rounded-xl shadow-sm transition-all cursor-pointer"
            title="原型演示 (Present / Preview)"
          >
            <Play class="w-3.5 h-3.5 fill-white" />
            <ChevronDown class="w-3 h-3 text-emerald-200" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="present">
                <div class="flex items-center gap-2.5 py-1 pr-2">
                  <Maximize2 class="w-4 h-4 text-cyan-600 shrink-0" />
                  <div>
                    <div class="font-bold text-xs text-slate-800">Present (全屏演示)</div>
                    <div class="text-[10px] text-slate-400">独立全屏分享 · iPhone 16 Pro</div>
                  </div>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="preview" divided>
                <div class="flex items-center gap-2.5 py-1 pr-2">
                  <Smartphone class="w-4 h-4 text-emerald-600 shrink-0" />
                  <div>
                    <div class="font-bold text-xs text-slate-800">Preview (侧边手机浮层)</div>
                    <div class="text-[10px] text-slate-400">在画布呈现可拖拽真机模型演示</div>
                  </div>
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- ===== Main Workbench Workspace ===== -->
    <div class="flex flex-1 overflow-hidden relative">
      <!-- ===== Left Sidebar: Design File Explorer & Component Palette ===== -->
      <aside class="w-64 bg-white/95 backdrop-blur-md border-r border-slate-200/90 flex flex-col shrink-0 z-10 shadow-2xs">
        <!-- Sidebar Header: Double Tab Switch -->
        <div class="px-2 py-2 border-b border-slate-100 flex items-center gap-1 bg-slate-50/70 shrink-0">
          <button
            class="wf-tap flex-1 py-1.5 px-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            :class="leftSidebarTab === 'outline'
              ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80'
              : 'text-slate-500 hover:text-slate-800'"
            @click="leftSidebarTab = 'outline'"
          >
            <Layers class="w-3.5 h-3.5" :class="leftSidebarTab === 'outline' ? 'text-emerald-600' : 'text-slate-400'" />
            <span>设计稿大纲</span>
          </button>
          <button
            class="wf-tap flex-1 py-1.5 px-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            :class="leftSidebarTab === 'layers'
              ? 'bg-white text-blue-700 shadow-2xs border border-slate-200/80'
              : 'text-slate-500 hover:text-slate-800'"
            @click="leftSidebarTab = 'layers'"
          >
            <ListTree class="w-3.5 h-3.5" :class="leftSidebarTab === 'layers' ? 'text-blue-600' : 'text-slate-400'" />
            <span>图层</span>
          </button>
        </div>

        <!-- Tab 1: 100% Retained Design Outline -->
        <div v-if="leftSidebarTab === 'outline'" class="flex-1 flex flex-col overflow-hidden">
          <!-- Batch Action Bar: HTML Fast Render & AI Deep Re-analyze -->
          <div class="px-3 py-2 bg-slate-50/80 border-b border-slate-100 flex flex-col gap-1.5">
            <div class="flex items-center justify-between">
              <label class="flex items-center gap-1.5 text-xs text-slate-600 font-semibold cursor-pointer">
                <input
                  type="checkbox"
                  :checked="regenAll"
                  @change="toggleRegenAll"
                  class="w-3.5 h-3.5 text-emerald-600 rounded border-slate-300 focus:ring-0"
                />
                <span>全选</span>
              </label>
              <span class="text-[11px] text-slate-400 tabular-nums">已选 {{ regenChecked.size }} 页</span>
            </div>

            <div class="flex items-center gap-1.5">
              <button
                class="wf-tap flex-1 inline-flex items-center justify-center gap-1 py-1 text-[11px] font-semibold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 rounded-lg transition-all disabled:opacity-40 disabled:pointer-events-none cursor-pointer shadow-2xs"
                :disabled="regenChecked.size === 0 || regeneratingIds.size > 0 || isReanalyzing"
                title="基于已有元素与跳转拓扑快速同步并刷新原型（毫秒级，不消耗 AI Token）"
                @click="onRegenerateChecked"
              >
                <RotateCw class="w-3 h-3 text-slate-500" :class="{ 'animate-spin': regeneratingIds.size > 0 }" />
                <span>原型交互刷新</span>
              </button>
              <button
                class="wf-tap flex-1 inline-flex items-center justify-center gap-1 py-1 text-[11px] font-semibold text-teal-700 bg-teal-50 hover:bg-teal-100 border border-teal-200/60 rounded-lg transition-all disabled:opacity-40 disabled:pointer-events-none cursor-pointer shadow-2xs"
                :disabled="regenChecked.size === 0 || isReanalyzing || regeneratingIds.size > 0"
                title="调用视觉大模型重新提取页面组件与识别元素（耗时约10-20秒）"
                @click="onReanalyzeChecked"
              >
                <Sparkles class="w-3 h-3 text-teal-600" :class="{ 'animate-spin': isReanalyzing }" />
                <span>AI 重新识别</span>
              </button>
            </div>
          </div>

          <!-- Pages Scroll List -->
          <div class="flex-1 overflow-y-auto p-2 space-y-1">
            <div
              v-for="b in blocks"
              :key="b.page.id"
              class="group relative flex items-center gap-2.5 p-2 rounded-xl transition-all cursor-pointer border"
              :class="b.page.id === focusPageId
                ? 'bg-emerald-50/80 border-emerald-200/80 text-emerald-950 shadow-2xs'
                : 'hover:bg-slate-50 border-transparent text-slate-700'"
              @click="focusPage(b.page.id)"
            >
              <!-- Thumbnail -->
              <div class="w-9 h-12 rounded-lg bg-slate-100 overflow-hidden border border-slate-200/80 shrink-0 flex items-center justify-center shadow-2xs">
                <img v-if="b.page.background_image" :src="getFileUrl(b.page.background_image)" class="w-full h-full object-cover" />
                <div v-else class="text-[9px] text-slate-400">无图</div>
              </div>

              <!-- Meta Info -->
              <div class="flex-1 min-w-0">
                <div class="text-xs font-bold truncate leading-tight mb-1" :class="b.page.id === focusPageId ? 'text-emerald-900' : 'text-slate-900'">
                  {{ b.page.name }}
                </div>
                <div class="flex items-center gap-1.5 text-[10px] text-slate-400 tabular-nums">
                  <span class="w-1.5 h-1.5 rounded-full" :class="b.page.analyzed ? 'bg-emerald-500' : 'bg-amber-400'"></span>
                  <span>{{ b.page.analyzed ? '已分析' : '待分析' }}</span>
                  <span>·</span>
                  <span>{{ b.page.elements.length }} 元素</span>
                </div>
              </div>

              <!-- Delete Page Button (hover to show) -->
              <button
                class="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-all cursor-pointer shrink-0"
                title="删除画板"
                @click.stop="confirmDeletePage(b.page)"
                @mousedown.stop
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <!-- Sidebar Footer Summary -->
          <div class="p-3 bg-slate-50/80 border-t border-slate-100 text-[11px] text-slate-500 space-y-1.5 tabular-nums">
            <div class="flex justify-between"><span>总元素数</span><span class="font-bold text-slate-700">{{ totalElements }}</span></div>
            <div class="flex justify-between"><span>交互事件</span><span class="font-bold text-slate-700">{{ totalInteractions }}</span></div>
            <div class="flex justify-between"><span>说明标注</span><span class="font-bold text-slate-700">{{ totalAnnotations }}</span></div>
            <div class="pt-1.5 border-t border-slate-200/60 text-[10px] text-slate-400 flex items-center gap-1">
              <MousePointer class="w-3 h-3" />
              <span>拖动画布漫游，滚轮平滑缩放</span>
            </div>
          </div>
        </div>

        <!-- Tab 2: Layers Tree -->
        <LayerTree
          v-else
          class="flex-1"
          :current-page="currentFocusPage"
          :selected-element-id="selectedElementId"
          :hovered-element-id="hoveredElementId"
          @select-element="onLayerSelectElement"
          @select-frame="onLayerSelectFrame"
          @hover-element="hoveredElementId = $event"
        />
      </aside>

      <!-- ===== Right: Infinite Workbench Canvas ===== -->
      <main
        class="canvas-viewport flex-1 overflow-hidden relative cursor-grab bg-slate-100"
        ref="viewportRef"
        :class="{
          '!cursor-grabbing': isAnyDragging,
          '!cursor-text': activeDrawTool === 'text' && !isAnyDragging,
          '!cursor-crosshair': activeDrawTool !== 'select' && activeDrawTool !== 'text' && !isAnyDragging,
        }"
        @wheel.prevent="onWheel"
        @mousedown="onMouseDown"
        @dragover.prevent="onViewportDragOver"
        @drop.prevent="onViewportDrop"
      >
        <!-- ===== 画布顶部悬浮轻量绘制指引胶囊提示 ===== -->
        <Transition name="fade-fast">
          <div
            v-if="activeDrawTool !== 'select'"
            class="absolute top-5 left-1/2 -translate-x-1/2 z-50 px-4 py-2 bg-slate-900/90 backdrop-blur-md text-white border border-white/15 rounded-full shadow-2xl flex items-center gap-2.5 text-xs font-semibold select-none pointer-events-auto"
          >
            <span
              class="w-2 h-2 rounded-full shrink-0"
              :class="activeDrawTool === 'comment' ? 'bg-amber-400 animate-ping' : 'bg-[#0D99FF] animate-ping'"
            ></span>
            <span v-if="activeDrawTool === 'comment'">
              正在评论模式：点击画板任意位置打点发表评论，点击已有图钉展开回复，按 <kbd class="px-1.5 py-0.5 bg-white/20 rounded font-mono text-[11px]">Esc</kbd> 退出
            </span>
            <span v-else>
              正在绘制{{ currentToolName }}：按住鼠标左键拖拽自由画出尺寸，或点击放置，按 <kbd class="px-1.5 py-0.5 bg-white/20 rounded font-mono text-[11px]">Esc</kbd> 取消
            </span>
            <button
              type="button"
              class="ml-1 p-0.5 text-white/70 hover:text-white rounded-full hover:bg-white/20 transition-colors cursor-pointer"
              :title="activeDrawTool === 'comment' ? '退出评论模式 (Esc)' : '取消绘制 (Esc)'"
              @click="activeDrawTool = 'select'"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </Transition>

        <!-- Full-viewport transparent overlay during drag to shield iframes and eliminate hit-testing cost -->
        <div
          v-if="isAnyDragging || isDraggingConnection"
          class="fixed inset-0 z-[9999] select-none"
          :class="isDraggingConnection ? 'cursor-crosshair' : 'cursor-grabbing'"
          style="pointer-events: auto;"
        />

        <div
          class="canvas-content absolute top-0 left-0"
          :class="{ 'is-animating': !isAnyDragging && animating, 'is-dragging': isAnyDragging }"
          :style="contentStyle"
        >


          <template v-for="b in blocks" :key="b.page.id">
            <div
              class="page-block absolute transition-all duration-200 rounded-2xl cursor-pointer"
              :class="{
                'ring-4 ring-emerald-400 ring-offset-2 shadow-[0_0_24px_rgba(52,211,153,0.5)] scale-[1.01]': hoveredTargetBlockId === b.page.id,
                'ring-2 ring-blue-500 ring-offset-4 ring-offset-slate-100 shadow-[0_0_0_2px_#3b82f6,0_12px_28px_rgba(59,130,246,0.22)]': selectedNodeId === b.page.id && hoveredTargetBlockId !== b.page.id,
                'ring-1 ring-slate-200/90 hover:ring-2 hover:ring-blue-400/50 hover:shadow-md': selectedNodeId !== b.page.id && hoveredTargetBlockId !== b.page.id,
                '!cursor-text': activeDrawTool === 'text',
                '!cursor-crosshair': activeDrawTool !== 'select' && activeDrawTool !== 'text',
              }"
              :style="{ left: `${b.x}px`, top: `${b.y}px` }"
              @click.stop="onPageBlockClick($event, b)"
              @mouseenter="hoveredNodeId = b.page.id"
              @mouseleave="hoveredNodeId = null"
            >



              <!-- Figma 画板级别交互连线拉线手柄 (交互连线模式下：选中或悬停时呈现于原型屏幕右侧边缘) -->
              <div
                v-if="workbenchMode === 'interactive' && (selectedNodeId === b.page.id || hoveredNodeId === b.page.id)"
                class="node-connector-handle absolute z-40 w-7 h-7 rounded-full text-white flex items-center justify-center cursor-crosshair shadow-[0_0_0_3px_#ffffff,0_4px_14px_rgba(37,99,235,0.7)] hover:scale-125 transition-all select-none group pointer-events-auto"
                :class="selectedNodeId === b.page.id ? 'bg-blue-600 hover:bg-blue-500 ring-2 ring-blue-300 ring-offset-1 animate-pulse' : 'bg-blue-500/85 hover:bg-blue-600'"
                :style="{
                  left: `${(b.page.canvas_width || 375) + 16 + (b.page.canvas_width || 375)}px`,
                  top: `${(b.page.canvas_height || 812) / 2}px`,
                  transform: 'translate(-50%, -50%)',
                }"
                :title="selectedNodeId === b.page.id ? '画板整体连线：按住拖拽至目标画板以建立连线' : '画板整体连线：点击或按住拖拽至其他画板'"
                @mousedown.stop="startNodeConnectionDrag($event, b)"
              >
                <Plus class="w-4 h-4 stroke-[2.8] group-hover:rotate-90 transition-transform" />
              </div>

              <!-- Block Header Capsule Floating Label -->
              <div
                class="block-label absolute -top-9 left-0 inline-flex items-center gap-2 px-3 py-1.5 bg-white/90 backdrop-blur-md border border-slate-200/85 rounded-xl shadow-xs text-xs font-bold text-slate-800 hover:border-emerald-300 hover:text-emerald-600 hover:shadow-md transition-all cursor-grab active:cursor-grabbing"
                @mousedown.stop="onBlockDragStart($event, b)"
              >
                <input
                  v-if="b.page.html_content"
                  type="checkbox"
                  :checked="regenChecked.has(b.page.id)"
                  class="w-3.5 h-3.5 text-emerald-600 rounded border-slate-300 focus:ring-0 mr-0.5 cursor-pointer"
                  @change="(e: any) => { if (e.target.checked) regenChecked.add(b.page.id); else regenChecked.delete(b.page.id); regenChecked = new Set(regenChecked) }"
                  @click.stop
                  @mousedown.stop
                />
                <GripVertical class="w-3.5 h-3.5 text-slate-400" />
                <span class="cursor-pointer hover:underline truncate max-w-[150px]" @click.stop="focusPage(b.page.id)">{{ b.page.name }}</span>

                <!-- 独占锁定徽标：他人正在微调此页 -->
                <span
                  v-if="pageEditingConflicts[b.page.id]?.conflict"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-300 text-[10px] font-semibold select-none shrink-0"
                  :title="`${pageEditingConflicts[b.page.id]?.editor || '其他成员'} 正在独占微调此页面，已被锁定保护`"
                >
                  <Lock class="w-3 h-3 text-rose-500 shrink-0" />
                  <span>{{ pageEditingConflicts[b.page.id]?.editor || '协同成员' }} 独占微调中</span>
                </span>

                <!-- 本地微调徽标：当前正独占微调此页 -->
                <span
                  v-else-if="fineTune && mode === 'edit' && activeEditingPageId === b.page.id"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-300 text-[10px] font-semibold select-none shrink-0 animate-pulse"
                >
                  <SlidersHorizontal class="w-3 h-3 text-emerald-600 shrink-0" />
                  <span>微调中</span>
                </span>

                <button
                  class="wf-tap ml-0.5 p-1 rounded-md text-slate-400 hover:text-emerald-600 hover:bg-slate-100 transition-colors cursor-pointer"
                  title="放大聚焦此页"
                  @click.stop="focusPage(b.page.id)"
                  @mousedown.stop
                >
                  <Maximize2 class="w-3 h-3" />
                </button>
                <button
                  v-if="b.page.html_content"
                  class="wf-tap p-1 rounded-md text-emerald-600 hover:text-emerald-800 hover:bg-emerald-50 transition-colors cursor-pointer"
                  :class="{ 'animate-spin pointer-events-none': regeneratingIds.has(b.page.id) }"
                  title="刷新本页原型交互"
                  @click.stop="onRegenerateHtml(b.page.id)"
                  @mousedown.stop
                >
                  <RotateCw class="w-3 h-3" />
                </button>
                <button
                  class="wf-tap p-1 rounded-md text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                  title="删除此画板 (Delete / Backspace)"
                  @click.stop="confirmDeletePage(b.page)"
                  @mousedown.stop
                >
                  <Trash2 class="w-3 h-3" />
                </button>
              </div>

              <!-- Page Canvas Component (画布上禁止跳转，仅供选中与布线) -->
              <PageCanvas
                :ref="(el: any) => setPageRef(b.page.id, el)"
                :page="b.page"
                :all-pages="pages"
                :show-wireframe="showWireframe"
                :show-annotations="showAnnotations"
                :selected-element-id="selectedElementId"
                :hovered-element-id="hoveredElementId"
                :hovered-ann-id="hoveredAnnId"
                :show-design="true"
                :edit-mode="mode === 'edit' && !pageEditingConflicts[b.page.id]?.conflict"
                :interactive="false"
                :locked-by-other="fineTune && mode === 'edit' && pageEditingConflicts[b.page.id]?.conflict ? (pageEditingConflicts[b.page.id]?.editor || '协同成员') : null"
                :custom-orders="pageAnnOrders"
                :custom-titles="customTitles"
                :box-w="220"
                :gap="16"
                :dragging-component="isDraggingComponent"
                @navigate="onProtoNavigate"
                @back="onProtoBack"
                @save-html="onSaveHtml"
                @element-click="handleElementClick"
                @element-hover="handleElementHover"
                @ann-hover="hoveredAnnId = $event"
                @ann-click="handleAnnClick"
                @ann-save="handleAnnSave"
                @ann-order-change="handleAnnOrderChange"
                @locked-click="showLockedToast(b.page)"
                @request-edit="fineTune = true"
                @element-selected="onElementSelected(b.page.id, $event)"
                @element-deselected="onElementDeselected(b.page.id)"
              />

              <!-- ===== Figma 交互模式：具体组件加号方框与拉线手柄 (浮于画板与 iframe 之上，确保双击/单击精准捕获) ===== -->
              <div
                v-if="workbenchMode === 'interactive'"
                class="figma-interactive-layer absolute inset-0 pointer-events-none z-38"
              >
                <div
                  v-for="el in (b.page.elements || []).filter((e: any) => e.width >= 16 && e.height >= 14 && e.type !== 'background')"
                  :key="`figma-el-${el.id}`"
                  class="figma-element-target absolute pointer-events-auto transition-all cursor-pointer"
                  :style="{
                    left: `${(b.page.canvas_width || 375) + 16 + el.x}px`,
                    top: `${el.y}px`,
                    width: `${el.width}px`,
                    height: `${el.height}px`,
                  }"
                  :title="`组件：${el.label || el.type}${el.interaction?.target_page_id ? ` (已连线到「${getPageName(el.interaction.target_page_id)}」)` : ' (双击/拖动右侧小圆点可新建连线)'}`"
                  @click.stop="onInteractiveElementClick(b, el)"
                  @dblclick.stop="onInteractiveElementDblClick(b, el)"
                  @mouseenter="hoveredElementId = el.id"
                  @mouseleave="hoveredElementId = null"
                >
                  <!-- 悬停/选中：Figma 标准天蓝精致外框 -->
                  <div
                    v-if="selectedElementId === el.id || hoveredElementId === el.id"
                    class="absolute inset-0 rounded-sm pointer-events-none transition-all"
                    :class="selectedElementId === el.id
                      ? 'border-2 border-[#0D99FF] bg-[#0D99FF]/12 ring-2 ring-[#0D99FF]/30'
                      : 'border-[1.5px] border-[#0D99FF]/80 bg-[#0D99FF]/6'"
                  />

                  <!-- 元素尺寸标注（Figma 风格，悬停/选中时显示在下方） -->
                  <div
                    v-if="selectedElementId === el.id || hoveredElementId === el.id"
                    class="absolute -bottom-5 left-0 right-0 flex justify-center pointer-events-none"
                  >
                    <span class="px-1.5 py-0.5 rounded text-[9px] font-mono font-semibold bg-[#0D99FF] text-white leading-none shadow-sm select-none whitespace-nowrap">
                      {{ Math.round(el.width) }}×{{ Math.round(el.height) }}
                    </span>
                  </div>

                  <!-- Figma 连线手柄：悬停/选中时显示在元素右侧边缘中点，按住即可拖动连接线 -->
                  <div
                    v-if="selectedElementId === el.id || hoveredElementId === el.id"
                    class="absolute top-1/2 -right-[5px] -translate-y-1/2 z-50 w-[11px] h-[11px] rounded-full bg-white border-[2px] border-[#0D99FF] cursor-crosshair shadow-[0_0_0_1.5px_rgba(13,153,255,0.6),0_2px_8px_rgba(13,153,255,0.4)] hover:scale-150 hover:bg-sky-50 transition-transform select-none pointer-events-auto"
                    :title="el.interaction?.target_page_id ? `按住拖动可重连（当前→「${getPageName(el.interaction.target_page_id)}」）` : `按住拖动连接线到其他画板`"
                    @mousedown.stop="startElementConnectionDrag($event, b, el)"
                  />
                </div>
              </div>

              <!-- 原子组件拖拽释放接收层 (置于 PageCanvas 与 iframe 顶层 z-100，彻底捕获拖放并杜绝 iframe 吸收) -->
              <div
                v-if="isDraggingComponent || rightSidebarTab === 'components'"
                class="palette-drop-receiver absolute inset-0 rounded-2xl transition-all flex flex-col items-center justify-center select-none"
                :style="{ zIndex: 100 }"
                :class="[
                  isDraggingComponent ? 'pointer-events-auto cursor-copy' : 'pointer-events-none',
                  hoveredDropBlockId === b.page.id
                    ? 'bg-blue-600/25 border-4 border-dashed border-blue-500 shadow-2xl backdrop-blur-[2px]'
                    : (isDraggingComponent ? 'bg-blue-500/10 border-2 border-dashed border-blue-400/50' : '')
                ]"
                @dragover.prevent.stop="onDropZoneDragOver($event, b.page.id)"
                @dragleave.stop="onDropZoneDragLeave($event, b.page.id)"
                @drop.prevent.stop="onDropZoneDrop($event, b.page.id)"
              >
                <div
                  v-if="isDraggingComponent"
                  class="px-5 py-3 rounded-2xl text-sm font-bold flex items-center gap-2.5 shadow-2xl transition-all pointer-events-none"
                  :class="hoveredDropBlockId === b.page.id
                    ? 'bg-blue-600 text-white scale-110 shadow-blue-500/50 animate-pulse ring-4 ring-blue-300'
                    : 'bg-white/95 text-blue-700 border border-blue-200'"
                >
                  <Plus class="w-5 h-5 stroke-[3]" />
                  <span>{{ hoveredDropBlockId === b.page.id ? `松手放入「${b.page.name}」` : `释放添加至「${b.page.name}」` }}</span>
                </div>
              </div>

              <!-- 实时自由拖拽绘制矩形/形状选框 (Figma Live Drag-to-Draw Marquee) -->
              <div
                v-if="activeShapeDraw && activeShapeDraw.block.page.id === b.page.id && (activeShapeDraw.width > 2 || activeShapeDraw.height > 2)"
                class="live-draw-marquee pointer-events-none absolute z-[130] select-none"
                :style="{
                  left: `${(activeShapeDraw.isDesignSide ? 0 : ((b.page.canvas_width || 375) + 16)) + activeShapeDraw.left}px`,
                  top: `${activeShapeDraw.top}px`,
                  width: `${activeShapeDraw.width}px`,
                  height: `${activeShapeDraw.height}px`,
                  borderRadius: activeShapeDraw.tool === 'circle' ? '50%' : (activeShapeDraw.tool === 'container' ? '14px' : (activeShapeDraw.tool === 'rect' ? '6px' : '2px')),
                  border: '2px solid #0D99FF',
                  backgroundColor: 'rgba(13, 153, 255, 0.16)',
                  boxShadow: '0 0 0 1px rgba(13, 153, 255, 0.4), 0 4px 16px rgba(13, 153, 255, 0.25)'
                }"
              >
                <!-- Figma 实时尺寸标注悬浮胶囊 -->
                <div
                  class="absolute -bottom-6 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded-full bg-[#0D99FF] text-white text-[10px] font-mono font-bold shadow-lg whitespace-nowrap leading-none flex items-center gap-1 ring-1 ring-white/50"
                >
                  <span>{{ Math.round(activeShapeDraw.width) }} × {{ Math.round(activeShapeDraw.height) }}</span>
                </div>
              </div>

              <!-- ===== Figma 评论图钉层 (在评论模式下呈现于画板上) ===== -->
              <template v-if="activeDrawTool === 'comment'">
                <CommentPin
                  v-for="th in getPageComments(b.page.id)"
                  :key="th.id"
                  :thread="th"
                  :index="getCommentIndex(th.id)"
                  :is-selected="selectedThreadId === th.id"
                  :current-user="currentCommentUser"
                  @select="selectCommentThread(th.id)"
                  @close="selectedThreadId = null"
                  @reply="handleCommentReply(th.id, $event)"
                  @resolve="handleCommentResolve(th.id, $event)"
                  @delete="handleCommentDelete(th.id)"
                />
              </template>

              <!-- 草稿评论输入气泡 (在评论模式下点击画板后暂存输入) -->
              <div
                v-if="draftComment && draftComment.pageId === b.page.id"
                class="draft-comment-container absolute z-[160] select-none"
                :style="{ left: `${draftComment.x}px`, top: `${draftComment.y}px` }"
                @click.stop
                @mousedown.stop
              >
                <!-- 临时草稿图钉 -->
                <div class="relative -translate-x-1/2 -translate-y-full">
                  <div class="w-7 h-7 rounded-full bg-amber-500 text-white font-bold text-xs flex items-center justify-center shadow-lg border-2 border-white ring-4 ring-amber-400/40 animate-bounce">
                    +
                  </div>
                  <div class="w-0 h-0 border-l-[4px] border-l-transparent border-r-[4px] border-r-transparent border-t-[5px] border-t-amber-500 mx-auto -mt-[1px]"></div>
                </div>

                <!-- 草稿输入气泡框 -->
                <div class="absolute top-1 left-2 w-[290px] bg-white border border-slate-200/90 rounded-2xl shadow-2xl p-3 z-[170] flex flex-col gap-2 animate-in fade-in zoom-in-95 duration-100">
                  <div class="flex items-center justify-between text-xs font-semibold text-slate-800">
                    <div class="flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-amber-500"></span>
                      <span>添加评审评论</span>
                    </div>
                    <button type="button" class="text-slate-400 hover:text-slate-700 p-0.5 rounded cursor-pointer" @click="cancelDraftComment">
                      <X class="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <textarea
                    ref="draftInputRef"
                    v-model="draftComment.text"
                    placeholder="输入你的评审意见... (Enter 发送, Esc 取消)"
                    rows="2"
                    class="w-full text-xs p-2 bg-slate-50 border border-slate-200 rounded-xl outline-none focus:border-amber-500 focus:bg-white focus:ring-2 focus:ring-amber-500/20 resize-none custom-scrollbar"
                    @keydown.enter.exact.prevent="submitDraftComment"
                    @keydown.esc="cancelDraftComment"
                  ></textarea>

                  <div class="flex items-center justify-between pt-1">
                    <span class="text-[10px] text-slate-400">
                      以 <strong class="text-slate-700">{{ currentCommentUser }}</strong> 的身份
                    </span>
                    <div class="flex items-center gap-1.5">
                      <button
                        type="button"
                        class="px-2 py-1 text-[11px] text-slate-500 hover:bg-slate-100 rounded-lg cursor-pointer"
                        @click="cancelDraftComment"
                      >
                        取消
                      </button>
                      <button
                        type="button"
                        class="px-3 py-1 bg-amber-500 hover:bg-amber-600 active:scale-95 text-white text-[11px] font-semibold rounded-lg shadow-sm disabled:opacity-40 disabled:pointer-events-none cursor-pointer"
                        :disabled="!draftComment.text.trim()"
                        @click="submitDraftComment"
                      >
                        发送
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 直接选择绘制 / 评论交互层 (浮于 iframe 之上，捕获 mousedown 自由拖拽拉框与快速点击) -->
              <div
                v-if="activeDrawTool !== 'select'"
                class="draw-placement-overlay absolute inset-0 rounded-2xl z-[110] select-none border-2 border-dashed"
                :class="[
                  activeDrawTool === 'comment'
                    ? 'border-amber-400/60 bg-amber-400/5 !cursor-crosshair'
                    : (activeDrawTool === 'text' ? '!cursor-text border-[#0D99FF]/60 bg-[#0D99FF]/5' : '!cursor-crosshair border-[#0D99FF]/60 bg-[#0D99FF]/5')
                ]"
                :title="activeDrawTool === 'comment' ? '点击画板打点添加评审评论' : `在画板上拖拽自由画出尺寸，或点击放置「${currentToolName}」`"
                @mousedown.stop.prevent="onArtboardDrawMouseDown($event, b)"
              />
            </div>
          </template>

          <!-- 实时自由拖拽绘制画板选框 (Live Frame Marquee) -->
          <div
            v-if="activeFrameDraw && (activeFrameDraw.width > 2 || activeFrameDraw.height > 2)"
            class="live-frame-draw-marquee pointer-events-none absolute z-[140] border-2 border-blue-500 bg-blue-500/10 rounded-2xl shadow-2xl select-none"
            :style="{
              left: `${activeFrameDraw.left}px`,
              top: `${activeFrameDraw.top}px`,
              width: `${activeFrameDraw.width}px`,
              height: `${activeFrameDraw.height}px`,
            }"
          >
            <div class="absolute -top-7 left-0 px-2 py-0.5 rounded bg-blue-600 text-white text-[11px] font-bold shadow-md flex items-center gap-1">
              <span>Frame {{ Math.round(activeFrameDraw.width) }} × {{ Math.round(activeFrameDraw.height) }}</span>
            </div>
          </div>

          <!-- ===== Figma Prototype 模式：细致贝塞尔连线与节点圆圈 (位于画板之上浮层，确保连线不被画板遮盖) ===== -->
          <template v-if="visibleConnections.length > 0 || activeDragLine">
            <svg
              class="interaction-svg-layer pointer-events-none"
              style="position: absolute; left: 0; top: 0; width: 60000px; height: 60000px; overflow: visible; z-index: 45;"
            >
              <defs>
                <!-- 激活态高亮箭头 (Figma 天蓝开放式轻灵小箭头) -->
                <marker
                  id="figma-arrow-active"
                  viewBox="0 0 10 10"
                  refX="7"
                  refY="5"
                  markerWidth="7"
                  markerHeight="7"
                  orient="auto"
                >
                  <path d="M 1.5 1.5 L 6.5 5 L 1.5 8.5" fill="none" stroke="#0D99FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                </marker>
                <!-- 常规状态箭头 (Figma 天蓝轻灵小箭头) -->
                <marker
                  id="figma-arrow-normal"
                  viewBox="0 0 10 10"
                  refX="7"
                  refY="5"
                  markerWidth="6"
                  markerHeight="6"
                  orient="auto"
                >
                  <path d="M 1.5 1.5 L 6.5 5 L 1.5 8.5" fill="none" stroke="#0D99FF" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </marker>
                <!-- 实时拖拽拉线箭头 -->
                <marker
                  id="figma-drag-arrow"
                  viewBox="0 0 10 10"
                  refX="7"
                  refY="5"
                  markerWidth="7"
                  markerHeight="7"
                  orient="auto"
                >
                  <path d="M 1.5 1.5 L 6.5 5 L 1.5 8.5" fill="none" stroke="#0D99FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                </marker>
              </defs>

              <g v-for="conn in visibleConnections" :key="conn.id" class="figma-conn-item">
                <!-- 宽热区透明线 (18px 宽，点击可精准选中该连线并隐藏其他连线，敲击键盘 Backspace 键可直接删除) -->
                <path
                  :d="conn.path"
                  fill="none"
                  stroke="transparent"
                  stroke-width="18"
                  class="cursor-pointer pointer-events-auto"
                  :title="`交互连线：${conn.label} ➔ ${conn.actionLabel} (点击选中此连线，按 Backspace 键可删除)`"
                  @click.stop="selectConnection(conn)"
                />

                <!-- 外发光辅线 (0.8px-2px 柔和微光) -->
                <path
                  :d="conn.path"
                  fill="none"
                  :stroke="isConnActive(conn) ? 'rgba(13, 153, 255, 0.45)' : 'rgba(13, 153, 255, 0.18)'"
                  :stroke-width="isConnActive(conn) ? 5 : 3"
                  stroke-linecap="round"
                />
                <!-- Figma 纯正亮天蓝贝塞尔曲线 (还原 Figma Prototype 官方标准 1.6px 纤细轻灵设计) -->
                <path
                  :d="conn.path"
                  fill="none"
                  stroke="#0D99FF"
                  :stroke-width="isConnActive(conn) ? 2.2 : 1.6"
                  stroke-linecap="round"
                  :marker-end="isConnActive(conn) ? 'url(#figma-arrow-active)' : 'url(#figma-arrow-normal)'"
                />
                <!-- 起点 Figma 节点圆圈 (白底蓝环小圆点) -->
                <circle
                  :cx="conn.x1"
                  :cy="conn.y1"
                  :r="isConnActive(conn) ? 5.5 : 4.5"
                  fill="#ffffff"
                  stroke="#0D99FF"
                  :stroke-width="isConnActive(conn) ? 2.2 : 1.6"
                />
                <circle
                  :cx="conn.x1"
                  :cy="conn.y1"
                  :r="isConnActive(conn) ? 2.4 : 1.8"
                  fill="#0D99FF"
                />
              </g>

              <!-- 实时拖拽拉出的贝塞尔连线 (Figma 拖拽连线预览) -->
              <g v-if="activeDragLine">
                <path
                  :d="activeDragLine.path"
                  fill="none"
                  stroke="#0D99FF"
                  stroke-width="2"
                  stroke-dasharray="5,4"
                  stroke-linecap="round"
                  marker-end="url(#figma-drag-arrow)"
                />
                <circle
                  :cx="activeDragLine.x1"
                  :cy="activeDragLine.y1"
                  r="5.5"
                  fill="#ffffff"
                  stroke="#0D99FF"
                  stroke-width="2"
                />
                <circle
                  :cx="activeDragLine.x1"
                  :cy="activeDragLine.y1"
                  r="2.2"
                  fill="#0D99FF"
                />
              </g>
            </svg>

            <!-- 交互连线轻量胶囊标签 (Figma 交互胶囊，悬浮于连线上方) -->
            <div
              v-for="conn in visibleConnections"
              :key="`tag-${conn.id}`"
              class="absolute pointer-events-auto transform -translate-x-1/2 -translate-y-1/2 px-2.5 py-0.5 rounded-full text-white text-[10px] font-medium shadow-md flex items-center gap-1 cursor-pointer select-none transition-all"
              :class="isConnActive(conn) ? 'bg-[#0D99FF] ring-2 ring-sky-300 ring-offset-1 scale-105 z-50' : 'bg-slate-700/90 hover:bg-[#0D99FF] hover:scale-105 opacity-90 hover:opacity-100 z-45'"
              :style="{ left: `${conn.midX}px`, top: `${conn.midY}px` }"
              :title="`交互：${conn.label} ➔ ${conn.actionLabel} (${conn.fromPageName} → ${conn.toPageName})，点击选中此连线，敲击键盘 Backspace 键可删除`"
              @click.stop="selectConnection(conn)"
            >
              <Zap class="w-2.5 h-2.5 fill-current" />
              <span>{{ conn.label }}: {{ conn.actionLabel }}</span>
              <button
                class="w-3.5 h-3.5 rounded-full hover:bg-black/30 text-white flex items-center justify-center text-[9px] ml-0.5 cursor-pointer"
                title="删除交互连线 (快捷键 Backspace)"
                @click.stop="removeConnection(conn)"
              >
                ×
              </button>
            </div>
          </template>

          <div v-if="!pages.length" class="flex flex-col items-center justify-center p-20 text-slate-400">
            <Layers class="w-12 h-12 text-slate-300 mb-3" />
            <p class="text-sm font-medium">项目暂无页面，请先返回项目页扫描设计稿</p>
          </div>
        </div>

        <!-- ===== Figma UI3 底部居中悬浮工具栏 ===== -->
        <FigmaBottomToolbar
          v-if="mode === 'edit'"
          v-model:active-tool="activeDrawTool"
          :show-annotations="showAnnotations"
          :target-page="currentFocusPage"
          :comment-count="unresolvedCommentsCount"
          @tool-change="handleBottomToolChange"
          @add-component="handleBottomAddComponent"
          @toggle-annotations="showAnnotations = !showAnnotations"
          @create-frame="handleCreateFramePreset"
        />
      </main>

      <!-- ===== Right Sidebar: Mode Switcher, Zoom Controls, Design Inspector & Component Palette ===== -->
      <aside class="w-72 min-w-[288px] max-w-[288px] bg-white/95 backdrop-blur-md border-l border-slate-200/90 flex flex-col shrink-0 z-10 shadow-2xs overflow-hidden">
        <!-- 评论模式：右侧呈现专属评论面板 -->
        <CommentPanel
          v-if="activeDrawTool === 'comment'"
          class="flex-1 w-full"
          :comments="comments"
          :selected-thread-id="selectedThreadId"
          :current-user="currentCommentUser"
          @close="activeDrawTool = 'select'"
          @select-thread="selectCommentThread"
          @update-current-user="onUpdateCommentUser"
          @toggle-resolve="handleCommentResolve($event, !comments.find(c => c.id === $event)?.resolved)"
          @delete-thread="handleCommentDelete"
        />

        <template v-else>
          <!-- 0. Right Sidebar Topmost: Mode Switcher [ 需求走查 (Design) | 交互连线 (Prototype) ] -->
          <div class="px-2.5 py-2 border-b border-slate-100 bg-slate-50/90 flex items-center justify-between shrink-0">
          <div class="flex items-center gap-1 w-full bg-slate-200/80 p-1 rounded-xl">
            <button
              class="wf-tap flex-1 py-1 px-2 text-xs font-bold rounded-lg transition-all cursor-pointer text-center flex items-center justify-center gap-1"
              :class="workbenchMode === 'design'
                ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80'
                : 'text-slate-500 hover:text-slate-800'"
              @click="setWorkbenchMode('design')"
            >
              <span>需求走查</span>
            </button>
            <button
              class="wf-tap flex-1 py-1 px-2 text-xs font-bold rounded-lg transition-all cursor-pointer text-center flex items-center justify-center gap-1"
              :class="workbenchMode === 'interactive'
                ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/30'
                : 'text-slate-500 hover:text-slate-800'"
              @click="setWorkbenchMode('interactive')"
            >
              <Zap class="w-3 h-3 fill-current" />
              <span>交互连线</span>
            </button>
          </div>
        </div>

        <!-- 1. Right Sidebar: Canvas Zoom Controls -->
        <div class="px-2.5 py-2 border-b border-slate-100 flex items-center justify-between bg-white shrink-0">
          <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-lg p-0.5 text-slate-600 shadow-2xs">
            <button
              class="wf-tap p-1 hover:bg-slate-100 hover:text-slate-900 rounded transition-colors cursor-pointer"
              title="缩小 (Ctrl + 滚轮)"
              @click="zoomAtCenter(0.85)"
            >
              <Minus class="w-3 h-3" />
            </button>
            <button
              class="wf-tap px-2 py-0.5 text-[11px] font-bold tabular-nums text-slate-700 hover:text-emerald-700 hover:bg-slate-100 rounded select-none transition-colors cursor-pointer"
              title="点击还原 100% 比例 (Ctrl+0)"
              @click="resetZoom100"
            >
              {{ Math.round(view.k * 100) }}%
            </button>
            <button
              class="wf-tap p-1 hover:bg-slate-100 hover:text-slate-900 rounded transition-colors cursor-pointer"
              title="放大 (Ctrl + 滚轮)"
              @click="zoomAtCenter(1.18)"
            >
              <Plus class="w-3 h-3" />
            </button>
          </div>

          <!-- Quick Canvas Focus Tools -->
          <div class="flex items-center gap-1 text-slate-400">
            <button
              class="wf-tap p-1.5 hover:bg-slate-200/70 hover:text-slate-700 rounded-md transition-colors cursor-pointer"
              title="聚焦当前选中画框 (Shift+2)"
              @click="focusPage(focusPageId ?? (blocks[0]?.page.id ?? 0))"
            >
              <Crosshair class="w-3.5 h-3.5" />
            </button>
            <button
              class="wf-tap p-1.5 hover:bg-slate-200/70 hover:text-slate-700 rounded-md transition-colors cursor-pointer"
              title="自适应全览所有画框 (Shift+1)"
              @click="fitAll"
            >
              <Maximize2 class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <!-- 2. Right Sidebar Header: Double Tab Switch -->
        <div class="px-2 py-1.5 border-b border-slate-100 flex items-center gap-1 bg-white shrink-0">
          <button
            class="wf-tap flex-1 py-1 px-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            :class="rightSidebarTab === 'design'
              ? 'bg-slate-100 text-slate-900 font-bold border border-slate-200/80 shadow-2xs'
              : 'text-slate-500 hover:text-slate-800'"
            @click="rightSidebarTab = 'design'"
          >
            <SlidersHorizontal class="w-3.5 h-3.5" :class="rightSidebarTab === 'design' ? 'text-[#0D99FF]' : 'text-slate-400'" />
            <span>属性</span>
          </button>
          <button
            class="wf-tap flex-1 py-1 px-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer"
            :class="rightSidebarTab === 'components'
              ? 'bg-blue-50 text-blue-700 font-bold border border-blue-200/80 shadow-2xs'
              : 'text-slate-500 hover:text-slate-800'"
            @click="rightSidebarTab = 'components'"
          >
            <Component class="w-3.5 h-3.5" :class="rightSidebarTab === 'components' ? 'text-blue-600' : 'text-slate-400'" />
            <span>组件库</span>
          </button>
        </div>

        <!-- 3. Tab 1: Design Inspector Panel -->
        <DesignInspector
          v-if="rightSidebarTab === 'design'"
          :current-page="currentFocusPage"
          :selected-element="selectedElementObj"
          :element-info="activeSelectedElementInfo"
          @update-dimension="onInspectorUpdateDimension"
          @update-position="onInspectorUpdatePosition"
          @update-color="onInspectorUpdateColor"
          @update-font-size="onInspectorUpdateFontSize"
          @align-selection="onInspectorAlign"
          @update-radius="onInspectorUpdateRadius"
          @update-stroke="onInspectorUpdateStroke"
          @update-shadow="onInspectorUpdateShadow"
          @duplicate-selection="onInspectorDuplicate"
          @delete-selection="onInspectorDelete"
          @replace-asset="onInspectorReplaceAsset"
          @start-text-edit="onInspectorStartTextEdit"
          @update-text="onInspectorUpdateText"
          @select-parent="onInspectorSelectParent"
        />

        <!-- 4. Tab 2: Atomic Component Palette -->
        <ComponentPalette
          v-else
          class="flex-1"
          :target-page="currentFocusPage"
          @add-component="onPaletteAddComponent"
          @drag-start="onPaletteDragStart"
          @drag-end="onPaletteDragEnd"
        />
        </template>
      </aside>
    </div>

    <!-- ===== Figma Floating iPhone Prototype Preview Widget ===== -->
    <FigmaFloatingPreview
      :visible="showFloatingPreview"
      :pages="pages"
      :initial-page-id="currentFocusPage?.id"
      @close="showFloatingPreview = false"
      @open-present="openPurePreview"
      @navigate-page="onFloatingNavigate"
    />

    <!-- ===== Mobile Device Simulator Overlay (Preview Mode) ===== -->
    <div v-if="mode === 'preview'" class="absolute inset-0 bg-slate-900/90 backdrop-blur-xl z-50 flex flex-col animate-fade-in">
      <!-- Simulator Topbar -->
      <header class="h-14 px-6 bg-slate-900/60 border-b border-white/10 flex items-center justify-between text-white shrink-0">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
            <Smartphone class="w-4 h-4" />
          </div>
          <div>
            <div class="text-sm font-bold tracking-tight">真机原型预览</div>
            <div class="text-xs text-slate-400">当前页面: {{ previewPage?.name }}</div>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <button
            class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition-all cursor-pointer border"
            :class="showSimDrawer 
              ? 'bg-emerald-600 text-white border-emerald-500 shadow-sm shadow-emerald-600/30' 
              : 'bg-white/10 text-slate-300 border-white/15 hover:bg-white/20'"
            @click="showSimDrawer = !showSimDrawer"
            title="展开或收起业务逻辑说明抽屉"
          >
            <FileText class="w-3.5 h-3.5" />
            <span>业务说明</span>
            <span v-if="simAnnList.length" class="ml-0.5 px-1.5 py-0.2 rounded-full text-[10px] bg-white/20">
              {{ simAnnList.length }}
            </span>
          </button>
          <el-select v-model="previewPageId" size="default" style="width: 170px" placeholder="切换页面">
            <el-option v-for="p in pages" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-cyan-600 hover:bg-cyan-500 active:bg-cyan-700 border border-cyan-500/80 rounded-xl shadow-xs transition-all cursor-pointer"
            title="进入纯原型全屏演示模式 (Figma 体验)"
            @click="openPurePreview"
          >
            <Maximize2 class="w-3.5 h-3.5" />
            <span>全屏纯演示</span>
          </button>
          <button
            class="wf-tap inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white bg-white/10 hover:bg-white/20 border border-white/15 rounded-xl transition-all cursor-pointer"
            @click="mode = 'edit'"
          >
            <X class="w-3.5 h-3.5" />
            <span>退出预览 (Esc)</span>
          </button>
        </div>
      </header>

      <!-- Simulator Device Frame Area -->
      <div class="flex-1 overflow-hidden flex items-center justify-center p-4 select-none" ref="deviceRef">
        <div class="flex items-center justify-center gap-6 max-h-full max-w-full">
          <!-- Clean Mobile Viewport Frame Container -->
          <div :style="{ width: `${375 * phoneScale}px`, height: `${previewHeight * phoneScale}px`, position: 'relative' }" class="shrink-0">
            <div :style="{ transform: `scale(${phoneScale})`, transformOrigin: 'top left', position: 'absolute', top: 0, left: 0 }">
              <div
                class="relative w-[375px] bg-white rounded-[32px] shadow-[0_25px_70px_-15px_rgba(0,0,0,0.8),0_0_0_10px_#1e293b,0_0_0_11px_rgba(255,255,255,0.12)] border border-slate-700/60 overflow-hidden"
                :class="slideClass"
                :style="{ height: `${previewHeight}px` }"
              >
                <!-- Back Navigator Floating Action Button -->
                <button
                  v-if="previewPageId !== pages[0]?.id"
                  class="wf-tap absolute top-4 left-4 z-30 w-8 h-8 rounded-full bg-black/40 hover:bg-black/60 backdrop-blur-md text-white flex items-center justify-center transition-all cursor-pointer shadow-md border border-white/20"
                  title="返回上一页"
                  @click="onProtoBack"
                >
                  <ArrowLeft class="w-4 h-4" />
                </button>

                <!-- Page View Container (Zero Padding, Full Screen Fit) -->
                <div ref="simScrollRef" class="w-[375px] overflow-x-hidden overflow-y-auto custom-scrollbar relative" :style="{ height: `${previewHeight}px` }">
                  <div class="relative min-w-[375px]">
                    <PageCanvas
                      v-if="previewPage"
                      :page="previewPage"
                      :all-pages="pages"
                      :show-wireframe="true"
                      :show-design="false"
                      :show-annotations="false"
                      :edit-mode="false"
                      :interactive="true"
                      :box-w="0"
                      :gap="0"
                      @navigate="onProtoNavigate"
                      @back="onProtoBack"
                      @save-html="onSaveHtml"
                      @element-click="handlePreviewClick"
                    />

                    <!-- 业务说明元素聚光灯与呼吸高亮框 -->
                    <transition name="fade-fast">
                      <div
                        v-if="displaySpotlightRect"
                        class="sim-spotlight-box pointer-events-none absolute z-40 transition-all duration-300"
                        :style="{
                          left: `${displaySpotlightRect.x - 3}px`,
                          top: `${displaySpotlightRect.y - 3}px`,
                          width: `${displaySpotlightRect.width + 6}px`,
                          height: `${displaySpotlightRect.height + 6}px`,
                        }"
                      >
                        <!-- 呼吸外发光光圈 -->
                        <div class="absolute inset-0 rounded-lg border-2 sim-breathing-ring"></div>
                        <!-- 轻微脉冲半透明高亮填充 -->
                        <div class="absolute inset-0 rounded-lg bg-emerald-400/15 animate-pulse"></div>
                        <!-- 悬浮组件名称标签 -->
                        <div
                          class="absolute -top-7 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded-full bg-emerald-600 text-white text-[10px] font-bold shadow-lg flex items-center gap-1.5 whitespace-nowrap"
                        >
                          <span class="w-1.5 h-1.5 rounded-full bg-emerald-200 animate-ping"></span>
                          <span>{{ activeSimAnnTitle || '目标组件' }}</span>
                        </div>
                      </div>
                    </transition>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Simulator Spec Drawer (业务说明与交互逻辑侧栏) -->
          <transition name="drawer-fade">
            <div
              v-if="showSimDrawer"
              class="w-80 md:w-96 rounded-2xl bg-slate-900/90 backdrop-blur-2xl border border-slate-700/70 shadow-[0_20px_50px_rgba(0,0,0,0.5)] flex flex-col text-slate-200 overflow-hidden shrink-0 transition-all duration-300"
              :style="{ height: `${Math.min(previewHeight * phoneScale, 780)}px` }"
            >
              <!-- Drawer Header -->
              <div class="px-4 py-3.5 border-b border-slate-800/80 bg-slate-950/50 flex items-center justify-between shrink-0">
                <div class="flex items-center gap-2.5 overflow-hidden">
                  <div class="w-7 h-7 rounded-lg bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                    <FileText class="w-4 h-4" />
                  </div>
                  <div class="overflow-hidden">
                    <div class="text-xs font-bold text-white truncate flex items-center gap-2">
                      <span class="truncate">{{ previewPage?.name || '页面说明' }}</span>
                      <span
                        v-if="previewPage && pageEditingConflicts[previewPage.id]?.conflict"
                        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 text-[10px] font-semibold animate-pulse border border-amber-500/30 shrink-0"
                        :title="`${pageEditingConflicts[previewPage.id]?.editor || '其他成员'} 正在编辑该页面`"
                      >
                        <AlertTriangle class="w-3 h-3 text-amber-400" />
                        <span>当前有人正在编辑</span>
                      </span>
                    </div>
                    <div class="text-[11px] text-slate-400 flex items-center gap-2">
                      <span>{{ simAnnList.length }} 条说明</span>
                      <span v-if="simInteractiveCount" class="text-emerald-400 font-medium">⚡ {{ simInteractiveCount }} 项交互</span>
                    </div>
                  </div>
                </div>
                <button
                  class="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-white/10 transition-colors cursor-pointer"
                  title="收起说明抽屉"
                  @click="showSimDrawer = false"
                >
                  <X class="w-4 h-4" />
                </button>
              </div>

              <!-- Drawer List Content -->
              <div class="flex-1 overflow-y-auto p-3 space-y-2.5 custom-scrollbar">
                <div v-if="!simAnnList.length" class="h-44 flex flex-col items-center justify-center text-slate-500 text-xs">
                  <FileText class="w-8 h-8 opacity-30 mb-2" />
                  <span>当前页面暂无业务逻辑标注说明</span>
                </div>
                <div
                  v-for="item in simAnnList"
                  :key="item.id"
                  :id="`sim-ann-${item.id}`"
                  class="p-3 rounded-xl border transition-all cursor-pointer relative group select-none"
                  :class="[
                    selectedSimAnnId === item.id 
                      ? 'bg-emerald-950/50 border-emerald-500/80 shadow-[0_0_15px_rgba(16,185,129,0.25)] ring-1 ring-emerald-500/50' 
                      : 'bg-slate-800/50 hover:bg-slate-800/80 border-slate-700/50 hover:border-slate-600/80',
                    dragOverAnnId === item.id ? 'border-t-2 !border-t-emerald-400 -translate-y-0.5' : '',
                    draggingAnnId === item.id ? 'opacity-40 scale-[0.98]' : '',
                    editingSimAnnId === item.id ? '!border-emerald-500/90 !bg-slate-800' : ''
                  ]"
                  :draggable="editingSimAnnId !== item.id"
                  @dragstart="onSimDragStart($event, item.id)"
                  @dragover.prevent="onSimDragOver($event, item.id)"
                  @dragleave="onSimDragLeave($event, item.id)"
                  @drop.prevent="onSimDrop($event, item.id)"
                  @dragend="onSimDragEnd"
                  @click="onSimCardClick(item)"
                >
                  <!-- Edit Form Mode -->
                  <div v-if="editingSimAnnId === item.id" class="space-y-2 text-xs" @click.stop @mousedown.stop>
                    <div class="space-y-1">
                      <span class="text-[10px] font-semibold text-slate-400">组件标题</span>
                      <input
                        ref="simTitleInputRef"
                        v-model="editSimTitle"
                        class="w-full bg-slate-900 border border-emerald-500 rounded-lg px-2.5 py-1.5 text-xs text-white placeholder-slate-500 outline-none focus:ring-1 focus:ring-emerald-400"
                        placeholder="组件名称..."
                        @keydown.enter.prevent="saveSimEdit(item)"
                        @keydown.esc.stop="cancelSimEdit"
                      />
                    </div>
                    <div class="space-y-1">
                      <span class="text-[10px] font-semibold text-slate-400">业务说明详情</span>
                      <textarea
                        v-model="editSimText"
                        rows="3"
                        class="w-full bg-slate-900 border border-emerald-500 rounded-lg px-2.5 py-1.5 text-xs text-white placeholder-slate-500 outline-none focus:ring-1 focus:ring-emerald-400 leading-relaxed resize-none"
                        placeholder="业务说明详情..."
                        @keydown.ctrl.enter.prevent="saveSimEdit(item)"
                        @keydown.esc.stop="cancelSimEdit"
                      ></textarea>
                    </div>
                    <div class="flex items-center justify-end gap-2 pt-1">
                      <button
                        class="px-2.5 py-1 rounded-md text-[11px] bg-slate-700 hover:bg-slate-600 text-slate-300 font-medium transition-colors cursor-pointer"
                        @click.stop="cancelSimEdit"
                      >
                        取消
                      </button>
                      <button
                        class="px-3 py-1 rounded-md text-[11px] bg-emerald-600 hover:bg-emerald-500 text-white font-semibold transition-colors cursor-pointer shadow-xs"
                        @click.stop="saveSimEdit(item)"
                      >
                        保存
                      </button>
                    </div>
                  </div>

                  <!-- Normal Display Mode -->
                  <template v-else>
                    <!-- Card Header -->
                    <div class="flex items-center justify-between gap-2 mb-1.5">
                      <div class="flex items-center gap-1.5 overflow-hidden flex-1">
                        <!-- Drag Handle Indicator -->
                        <span
                          class="text-slate-500 hover:text-slate-300 cursor-grab active:cursor-grabbing shrink-0 transition-colors p-0.5 -ml-1 rounded"
                          title="长按或拖拽调整顺序"
                          @mousedown.stop
                        >
                          <GripVertical class="w-3.5 h-3.5" />
                        </span>
                        <span class="font-semibold text-xs text-white truncate" :title="item.title">{{ item.title }}</span>
                        <!-- Edit Button on Hover -->
                        <button
                          class="opacity-0 group-hover:opacity-100 p-0.5 text-slate-400 hover:text-emerald-400 rounded transition-opacity cursor-pointer shrink-0"
                          title="编辑标题与说明"
                          @click.stop="startSimEdit(item)"
                          @mousedown.stop
                        >
                          <Pencil class="w-3 h-3" />
                        </button>
                      </div>
                      <!-- Interaction Capsule -->
                      <span
                        v-if="item.interactionType"
                        class="shrink-0 text-[10px] font-semibold px-2 py-0.5 rounded-full flex items-center gap-1 shadow-2xs"
                        :class="{
                          'bg-teal-500/20 text-teal-300 border border-teal-500/30': item.interactionType === 'navigate',
                          'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30': item.interactionType === 'modal',
                          'bg-amber-500/20 text-amber-300 border border-amber-500/30': item.interactionType === 'toggle',
                        }"
                      >
                        <span>{{ item.interactionType === 'navigate' ? '⚡ 跳转' : (item.interactionType === 'modal' ? '⚡ 弹窗' : '⚡ 切换') }}</span>
                      </span>
                    </div>

                    <!-- Target Destination Hint -->
                    <div
                      v-if="item.interactionTarget"
                      class="mb-2 px-2 py-1 rounded-md bg-slate-950/40 border border-slate-800 text-[11px] text-slate-300 flex items-center justify-between gap-1"
                    >
                      <span class="truncate text-slate-400">去向: <span class="text-emerald-300 font-medium">{{ item.interactionTarget }}</span></span>
                      <ChevronRight v-if="item.interactionType === 'navigate'" class="w-3 h-3 text-emerald-400 shrink-0 group-hover:translate-x-0.5 transition-transform" />
                    </div>

                    <!-- Description Text -->
                    <div class="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap word-break" @dblclick.stop="startSimEdit(item)" title="双击快速编辑说明">
                      {{ item.text || '暂无业务描述' }}
                    </div>

                    <!-- Interactive Action Tip -->
                    <div v-if="item.interactionType === 'navigate'" class="mt-2 pt-1.5 border-t border-slate-700/40 text-[10px] text-emerald-400/80 flex items-center gap-1">
                      <Compass class="w-3 h-3" />
                      <span>在真机屏幕中点击该组件即可体验跳转</span>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </transition>
        </div>
      </div>

      <!-- Floating Toast Notifications -->
      <div
        class="fixed bottom-10 left-1/2 -translate-x-1/2 px-4 py-2 bg-slate-900/90 text-white text-xs font-medium rounded-xl border border-white/10 shadow-elevated transition-opacity pointer-events-none z-50 backdrop-blur-md"
        :class="toastMsg ? 'opacity-100' : 'opacity-0'"
      >
        {{ toastMsg }}
      </div>
    </div>

    <!-- ===== Figma 风格 Interaction Details 交互配置卡片 ===== -->
    <el-dialog
      v-model="showInteractionModal"
      width="440px"
      append-to-body
      :show-close="true"
      class="wf-interaction-dialog"
    >
      <template #header>
        <div class="flex items-center gap-2.5">
          <div class="w-7 h-7 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
            <Zap class="w-4 h-4 fill-blue-600" />
          </div>
          <div>
            <div class="text-sm font-bold text-slate-900">Interaction Details (交互配置)</div>
            <div class="text-[11px] text-slate-400">设置组件点击/交互响应与转场动效</div>
          </div>
        </div>
      </template>

      <div class="space-y-4 py-1 select-none">
        <!-- 触发源信息展示 -->
        <div class="p-3 bg-slate-50 rounded-xl border border-slate-200/80 text-xs text-slate-600 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="font-bold text-slate-700">触发源:</span>
            <span class="px-2 py-0.5 rounded-md bg-blue-50 text-blue-700 font-semibold border border-blue-200">
              {{ currentDraggingAnchor?.label || '未命名组件' }}
            </span>
          </div>
          <span class="text-[11px] text-slate-400">来自：{{ currentDraggingAnchor?.pageName }}</span>
        </div>

        <!-- Trigger: [ On Click ] -->
        <div class="space-y-1.5">
          <label class="text-xs font-bold text-slate-700 flex items-center gap-1.5">
            <span>触发手势 (Trigger)</span>
          </label>
          <div class="grid grid-cols-3 gap-2">
            <button
              v-for="t in triggerOptions"
              :key="t.value"
              class="py-2 px-2 text-xs font-semibold rounded-xl border transition-all cursor-pointer text-center"
              :class="interactionForm.trigger === t.value
                ? 'bg-blue-50 border-blue-500 text-blue-700 font-bold shadow-2xs'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'"
              @click="interactionForm.trigger = t.value"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <!-- Action: [ Navigate to / Open Overlay ] -->
        <div class="space-y-1.5">
          <label class="text-xs font-bold text-slate-700 flex items-center gap-1.5">
            <span>响应动作 (Action)</span>
          </label>
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="a in actionOptions"
              :key="a.value"
              class="py-2 px-2 text-xs font-semibold rounded-xl border transition-all cursor-pointer text-center"
              :class="interactionForm.action === a.value
                ? 'bg-blue-50 border-blue-500 text-blue-700 font-bold shadow-2xs'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'"
              @click="interactionForm.action = a.value"
            >
              {{ a.label }}
            </button>
          </div>
        </div>

        <!-- Destination: Target Page -->
        <div class="space-y-1.5">
          <label class="text-xs font-bold text-slate-700 flex items-center gap-1.5">
            <span>目标页面 (Destination)</span>
          </label>
          <el-select v-model="interactionForm.targetPageId" class="w-full" size="large" placeholder="选择目标跳转页面">
            <el-option
              v-for="p in pages"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </div>

        <!-- Animation: [ Push ] -->
        <div class="space-y-1.5">
          <label class="text-xs font-bold text-slate-700 flex items-center gap-1.5">
            <span>转场动效 (Animation)</span>
          </label>
          <div class="grid grid-cols-4 gap-1.5">
            <button
              v-for="m in animationOptions"
              :key="m.value"
              class="py-1.5 px-2 text-xs font-semibold rounded-lg border transition-all cursor-pointer text-center"
              :class="interactionForm.animation === m.value
                ? 'bg-blue-50 border-blue-500 text-blue-700 font-bold'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'"
              @click="interactionForm.animation = m.value"
            >
              {{ m.label }}
            </button>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between pt-2 border-t border-slate-100">
          <span class="text-[11px] text-slate-400">点击确定后将持久化保存连线拓扑</span>
          <div class="flex items-center gap-2">
            <el-button @click="showInteractionModal = false">取消</el-button>
            <el-button type="primary" :loading="isSavingInteraction" @click="confirmCreateInteraction">
              确定建立连线
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Layers,
  Tag,
  SlidersHorizontal,
  Minus,
  Plus,
  Maximize2,
  Crosshair,
  Play,
  RotateCw,
  GripVertical,
  MousePointer,
  Smartphone,
  Sparkles,
  X,
  FileText,
  Compass,
  ChevronRight,
  ChevronDown,
  Pencil,
  Users,
  AlertTriangle,
  Lock,
  Component,
  Zap,
  Trash2,
  ListTree,
} from 'lucide-vue-next'
import { projectApi } from '../api/project'
import { getFileUrl } from '../api/http'
import type { Element, Page, Prototype, CommentThread, CommentReply } from '../types'
import PageCanvas from '../components/PageCanvas.vue'
import ComponentPalette, { type PaletteItem } from '../components/ComponentPalette.vue'
import FigmaBottomToolbar, { type ActiveToolType } from '../components/FigmaBottomToolbar.vue'
import LayerTree from '../components/LayerTree.vue'
import DesignInspector from '../components/DesignInspector.vue'
import FigmaFloatingPreview from '../components/FigmaFloatingPreview.vue'
import CommentPin from '../components/CommentPin.vue'
import CommentPanel from '../components/CommentPanel.vue'

const activeDrawTool = ref<ActiveToolType>('select')
const toolNames: Record<string, string> = {
  rect: '矩形方框',
  circle: '圆形/椭圆',
  container: '卡片容器',
  line: '水平分割线',
  text: '纯文本',
  frame: '画板框架',
  comment: '评论图钉',
}
const currentToolName = computed(() => toolNames[activeDrawTool.value] || '组件')

interface ShapeDrawState {
  tool: ActiveToolType
  block: { page: Page; x: number; y: number }
  startX: number
  startY: number
  curX: number
  curY: number
  left: number
  top: number
  width: number
  height: number
  isDesignSide: boolean
}
const activeShapeDraw = ref<ShapeDrawState | null>(null)

interface FrameDrawState {
  startX: number
  startY: number
  curX: number
  curY: number
  left: number
  top: number
  width: number
  height: number
}
const activeFrameDraw = ref<FrameDrawState | null>(null)

// ===== Figma 评论系统状态与交互管理 =====
const comments = ref<CommentThread[]>([])
const selectedThreadId = ref<number | null>(null)
const currentCommentUser = ref(localStorage.getItem('wf_comment_author') || '我')
const draftComment = ref<{ pageId: number; x: number; y: number; text: string } | null>(null)
const draftInputRef = ref<HTMLTextAreaElement | null>(null)

const unresolvedCommentsCount = computed(() => {
  return comments.value.filter((c) => !c.resolved).length
})

function getPageComments(pageId: number) {
  return comments.value.filter((c) => c.pageId === pageId)
}

function getCommentIndex(threadId: number) {
  const idx = comments.value.findIndex((c) => c.id === threadId)
  return idx >= 0 ? idx + 1 : 1
}

function selectCommentThread(threadId: number) {
  selectedThreadId.value = threadId
  const thread = comments.value.find((c) => c.id === threadId)
  if (thread) {
    focusPage(thread.pageId)
  }
}

function onUpdateCommentUser(name: string) {
  currentCommentUser.value = name
  localStorage.setItem('wf_comment_author', name)
}

function cancelDraftComment() {
  draftComment.value = null
}

async function loadComments() {
  try {
    const list = await projectApi.listComments(id)
    comments.value = list || []
  } catch (e: any) {
    console.error('Failed to load comments', e)
  }
}

async function submitDraftComment() {
  if (!draftComment.value || !draftComment.value.text.trim()) return
  const { pageId, x, y, text } = draftComment.value
  try {
    const thread = await projectApi.createComment(id, {
      pageId,
      x,
      y,
      author: currentCommentUser.value,
      content: text.trim(),
    })
    comments.value.push(thread)
    selectedThreadId.value = thread.id
    draftComment.value = null
    ElMessage.success('评论已发表')
  } catch (err: any) {
    ElMessage.error(err.message || '发表评论失败')
  }
}

async function handleCommentReply(threadId: number, content: string) {
  try {
    const reply = await projectApi.addReply(id, threadId, {
      author: currentCommentUser.value,
      content,
    })
    const thread = comments.value.find((c) => c.id === threadId)
    if (thread) {
      if (!thread.replies) thread.replies = []
      thread.replies.push(reply)
    }
  } catch (err: any) {
    ElMessage.error(err.message || '回复失败')
  }
}

async function handleCommentResolve(threadId: number, resolved: boolean) {
  try {
    const updated = await projectApi.toggleResolveComment(id, threadId, { resolved })
    const thread = comments.value.find((c) => c.id === threadId)
    if (thread) {
      thread.resolved = updated.resolved
    }
    ElMessage.success(resolved ? '评论已标记为已解决' : '评论已重新打开')
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

async function handleCommentDelete(threadId: number) {
  try {
    await projectApi.deleteCommentThread(id, threadId)
    comments.value = comments.value.filter((c) => c.id !== threadId)
    if (selectedThreadId.value === threadId) {
      selectedThreadId.value = null
    }
    ElMessage.success('评论已删除')
  } catch (err: any) {
    ElMessage.error(err.message || '删除失败')
  }
}


const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const proto = ref<Prototype | null>(null)
const pages = computed(() => proto.value?.pages || [])

const leftSidebarTab = ref<'outline' | 'layers'>('outline')
const rightSidebarTab = ref<'design' | 'components'>('design')
const showFloatingPreview = ref(false)

const workbenchMode = ref<'design' | 'interactive'>('design')

function setWorkbenchMode(m: 'design' | 'interactive') {
  workbenchMode.value = m
  if (m === 'interactive') {
    if (!selectedNodeId.value && pages.value.length > 0) {
      selectedNodeId.value = focusPageId.value || pages.value[0]?.id
    }
    ElMessage.info({
      message: '⚡ 已开启 Figma 交互连线模式：拖拽画板边缘的 [+] 蓝色手柄到目标画板即可连线',
      duration: 3500,
    })
  } else {
    ElMessage.info({
      message: '🎨 已返回需求走查模式',
      duration: 2000,
    })
  }
}

const showWireframe = ref(true)
const showAnnotations = ref(false)
/** 整页原型微调与编辑模式：8点控制盒调整大小 / 双击改文案 / 拖动位移 / Del 删除 / 自动落库 */
const fineTune = ref(true)

watch(rightSidebarTab, (tab) => {
  if (tab === 'components') {
    fineTune.value = true
  }
})

/** 多用户并发协同编辑防覆盖检测 */
function getOrCreateClientId(): string {
  try {
    let cid = sessionStorage.getItem('wf_client_id')
    if (!cid) {
      cid = 'user_' + Math.random().toString(36).slice(2, 9)
      sessionStorage.setItem('wf_client_id', cid)
    }
    return cid
  } catch {
    return 'user_' + Math.random().toString(36).slice(2, 9)
  }
}
const clientId = ref(getOrCreateClientId())
const pageEditingConflicts = ref<Record<number, { editing: boolean; conflict: boolean; editor?: string }>>({})

const currentConflictNotice = computed(() => {
  const focusedConflict = focusPageId.value ? pageEditingConflicts.value[focusPageId.value] : null
  if (focusedConflict?.conflict) {
    const p = pages.value.find((pg) => pg.id === focusPageId.value)
    return `「${p?.name || '当前页'}」正被 ${focusedConflict.editor || '其他成员'} 独占微调`
  }
  const conflictEntry = Object.entries(pageEditingConflicts.value).find(([_, v]) => v.conflict)
  if (conflictEntry) {
    const pageId = Number(conflictEntry[0])
    const p = pages.value.find((pg) => pg.id === pageId)
    return `「${p?.name || '页面'}」已被其他成员独占微调`
  }
  return ''
})

function showLockedToast(page: Page) {
  const editor = pageEditingConflicts.value[page.id]?.editor || '其他成员'
  showToast(`🔒「${page.name}」正由 ${editor} 独占微调中，已开启防覆盖保护。您可以微调其他页面！`)
}

function onPageBlockClick(e: MouseEvent, b: { page: Page; x: number; y: number }) {
  if (suppressBlockClick) return
  if (activeDrawTool.value === 'frame') {
    const blockW = (b.page.canvas_width || 375) + 16
    const nextX = b.x + blockW + 120
    const nextY = b.y
    handleCreateFrameOnCanvas(nextX, nextY)
    return
  }
  if (activeDrawTool.value !== 'select') {
    return
  }
  if (pageEditingConflicts.value[b.page.id]?.conflict) {
    const p = pages.value.find((pg) => pg.id === b.page.id)
    if (p) showLockedToast(p)
    return
  }
  selectedNodeId.value = b.page.id
  selectedConnId.value = null
  selectedElementId.value = null
  focusPageId.value = b.page.id
}

function onSaveHtml(p: { pageId: number; html: string }) {
  const conflict = pageEditingConflicts.value[p.pageId]?.conflict
  if (conflict) {
    const editor = pageEditingConflicts.value[p.pageId]?.editor || '其他成员'
    const pageName = pages.value.find((pg) => pg.id === p.pageId)?.name || '页面'
    showToast(`🔒 保存被拦截:「${pageName}」当前正被 ${editor} 独占微调，无法覆盖其修改！`)
    return
  }
  projectApi
    .saveHtml(id, p.pageId, p.html, clientId.value)
    .then(() => {
      const page = proto.value?.pages.find((pg) => pg.id === p.pageId)
      if (page) page.html_content = p.html
      showToast('✅ 微调已保存')
    })
    .catch((e: any) => showToast(`❌ 保存失败: ${e?.response?.data?.message || e?.message || '未知错误'}`))
}

// ===== 原子组件库拖拽放置与快捷添加系统 =====
const isDraggingComponent = ref(false)
const hoveredDropBlockId = ref<number | null>(null)

const currentFocusPage = computed(() => {
  const targetId = focusPageId.value || selectedNodeId.value
  return pages.value.find((p) => p.id === targetId) || pages.value[0] || null
})

function onPaletteDragStart(item: any) {
  isDraggingComponent.value = true
}

function onPaletteDragEnd() {
  isDraggingComponent.value = false
  hoveredDropBlockId.value = null
}

function onViewportDragOver(e: DragEvent) {
  if (!(window as any).__wfDraggingComponent && !isDraggingComponent.value) return
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
  isDraggingComponent.value = true

  const rect = viewportRef.value?.getBoundingClientRect()
  if (!rect) return
  const cx = (e.clientX - rect.left - view.value.x) / view.value.k
  const cy = (e.clientY - rect.top - view.value.y) / view.value.k

  let targetId: number | null = null
  for (const b of blocks.value) {
    const left = b.x
    const right = b.x + b.w
    const top = b.y
    const bottom = b.y + b.h
    if (cx >= left && cx <= right && cy >= top && cy <= bottom) {
      targetId = b.page.id
      break
    }
  }
  hoveredDropBlockId.value = targetId
}

function onViewportDrop(e: DragEvent) {
  if (!isDraggingComponent.value && !(window as any).__wfDraggingComponent) return
  e.preventDefault()
  e.stopPropagation()

  const targetId = hoveredDropBlockId.value || focusPageId.value || selectedNodeId.value || pages.value[0]?.id
  const item = (window as any).__wfDraggingComponent
  let html = item?.html || ''
  if (!html && e.dataTransfer) {
    html = e.dataTransfer.getData('text/html') || e.dataTransfer.getData('text/plain') || ''
  }

  isDraggingComponent.value = false
  hoveredDropBlockId.value = null

  if (targetId && html) {
    const block = blocks.value.find((b) => b.page.id === targetId)
    let dropX = 20
    let dropY = 220
    if (block && viewportRef.value) {
      const rect = viewportRef.value.getBoundingClientRect()
      const logicX = (e.clientX - rect.left - view.value.x) / view.value.k
      const logicY = (e.clientY - rect.top - view.value.y) / view.value.k
      const blockRelX = logicX - block.x
      const blockRelY = logicY - block.y
      const canvasW = block.page.canvas_width || 375
      const wireX = canvasW + 16
      const rawX = blockRelX >= wireX ? (blockRelX - wireX) : blockRelX
      const rawY = blockRelY
      dropX = Math.round(Math.max(16, Math.min(canvasW - 60, rawX)))
      dropY = Math.round(Math.max(60, Math.min((block.page.canvas_height || 812) - 80, rawY)))
    }
    insertComponentIntoPage(targetId, item || html, dropX, dropY)
  }
}

function onDropZoneDragOver(e: DragEvent, pageId: number) {
  e.preventDefault()
  e.stopPropagation()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
  isDraggingComponent.value = true
  hoveredDropBlockId.value = pageId
}

function onDropZoneDragLeave(e: DragEvent, pageId: number) {
  if (hoveredDropBlockId.value === pageId) {
    hoveredDropBlockId.value = null
  }
}

function onDropZoneDrop(e: DragEvent, pageId: number) {
  e.preventDefault()
  e.stopPropagation()
  hoveredDropBlockId.value = null
  isDraggingComponent.value = false

  let html = ''
  if ((window as any).__wfDraggingComponent?.html) {
    html = (window as any).__wfDraggingComponent.html
  } else if (e.dataTransfer) {
    html = e.dataTransfer.getData('text/html') || e.dataTransfer.getData('text/plain') || ''
  }

  const item = (window as any).__wfDraggingComponent
  if (!html) return

  const block = blocks.value.find((b) => b.page.id === pageId)
  let dropX = 20
  let dropY = 220

  if (block && viewportRef.value) {
    const rect = viewportRef.value.getBoundingClientRect()
    const logicX = (e.clientX - rect.left - view.value.x) / view.value.k
    const logicY = (e.clientY - rect.top - view.value.y) / view.value.k
    const blockRelX = logicX - block.x
    const blockRelY = logicY - block.y
    const canvasW = block.page.canvas_width || 375
    const wireX = canvasW + 16
    const rawX = blockRelX >= wireX ? (blockRelX - wireX) : blockRelX
    const rawY = blockRelY
    dropX = Math.round(Math.max(16, Math.min(canvasW - 60, rawX)))
    dropY = Math.round(Math.max(60, Math.min((block.page.canvas_height || 812) - 80, rawY)))
  }

  insertComponentIntoPage(pageId, item || html, dropX, dropY)
}

function onPaletteAddComponent(item: PaletteItem) {
  const targetId = focusPageId.value || selectedNodeId.value || pages.value[0]?.id
  if (!targetId) {
    showToast('⚠️ 请先在画布上点击选择一个目标画板')
    return
  }
  insertComponentIntoPage(targetId, item, 20, 220)
}

function insertComponentIntoPage(pageId: number, itemOrHtml: any, dropX = 20, dropY = 220, autoEditText = false) {
  const page = pages.value.find((p) => p.id === pageId)
  if (!page) return
  const htmlSnippet = typeof itemOrHtml === 'string' ? itemOrHtml : (itemOrHtml?.html || '')
  const itemName = typeof itemOrHtml === 'string' ? '原子组件' : (itemOrHtml?.name || '原子组件')

  if (!htmlSnippet) return

  fineTune.value = true
  selectedNodeId.value = pageId
  focusPageId.value = pageId

  // 1. 优先通过 PageCanvas 实例向运行中的 iframe 注入并自动触发持久化
  const inst = pageRefs.value[pageId]
  if (inst && typeof (inst as any).insertComponent === 'function') {
    ;(inst as any).insertComponent(htmlSnippet, dropX, dropY, autoEditText)
    showToast(`✅ 已将「${itemName}」添加至「${page.name}」(${dropX}, ${dropY})`)
    return
  }

  // 2. 如果尚未挂载 iframe，直接拼接 HTML 内容并持久化落库
  let currentHtml = page.html_content || ''
  const isModal = /wf-modal|wf-bottom-sheet/i.test(htmlSnippet)
  const wrappedSnippet = isModal
    ? htmlSnippet
    : `<div class="wf-el wf-inserted-component" style="position: absolute; left: ${dropX}px; top: ${dropY}px; z-index: 999; max-width: 335px;">${htmlSnippet}</div>`

  if (currentHtml.includes('</body>')) {
    currentHtml = currentHtml.replace('</body>', `${wrappedSnippet}\n</body>`)
  } else {
    currentHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=375"><style>body{margin:0;padding:16px;background:#f8fafc;font-family:sans-serif;}</style></head><body>${currentHtml}\n${wrappedSnippet}</body></html>`
  }
  onSaveHtml({ pageId, html: currentHtml })
  showToast(`✅ 已将「${itemName}」添加至「${page.name}」`)
}

// Figma 自由拖拽绘制组件 (Drag-to-Draw / Click-to-Place)
function onArtboardDrawMouseDown(e: MouseEvent, b: { page: Page; x: number; y: number }) {
  if (e.button !== 0) return
  if (activeDrawTool.value === 'select') return
  e.preventDefault()
  e.stopPropagation()

  if (!viewportRef.value) return
  const rect = viewportRef.value.getBoundingClientRect()
  const logicX = (e.clientX - rect.left - view.value.x) / view.value.k
  const logicY = (e.clientY - rect.top - view.value.y) / view.value.k
  const blockRelX = logicX - b.x
  const blockRelY = logicY - b.y

  if (activeDrawTool.value === 'comment') {
    selectedThreadId.value = null
    draftComment.value = {
      pageId: b.page.id,
      x: Math.round(blockRelX),
      y: Math.round(blockRelY),
      text: '',
    }
    nextTick(() => {
      draftInputRef.value?.focus()
    })
    return
  }

  const canvasW = b.page.canvas_width || 375
  const canvasH = b.page.canvas_height || 812
  const wireX = canvasW + 16
  const isDesignSide = blockRelX < wireX
  const rawX = isDesignSide ? blockRelX : (blockRelX - wireX)
  const rawY = blockRelY

  const startX = Math.round(Math.max(0, Math.min(canvasW, rawX)))
  const startY = Math.round(Math.max(0, Math.min(canvasH, rawY)))

  activeShapeDraw.value = {
    tool: activeDrawTool.value,
    block: b,
    startX,
    startY,
    curX: startX,
    curY: startY,
    left: startX,
    top: startY,
    width: 0,
    height: 0,
    isDesignSide,
  }

  window.addEventListener('mousemove', onArtboardDrawMouseMove, { capture: true })
  window.addEventListener('mouseup', onArtboardDrawMouseUp, { capture: true, once: true })
}

function onArtboardDrawMouseMove(e: MouseEvent) {
  if (!activeShapeDraw.value || !viewportRef.value) return
  e.preventDefault()
  e.stopPropagation()

  const s = activeShapeDraw.value
  const b = s.block
  const rect = viewportRef.value.getBoundingClientRect()
  const logicX = (e.clientX - rect.left - view.value.x) / view.value.k
  const logicY = (e.clientY - rect.top - view.value.y) / view.value.k
  const blockRelX = logicX - b.x
  const blockRelY = logicY - b.y
  const canvasW = b.page.canvas_width || 375
  const canvasH = b.page.canvas_height || 812
  const rawX = s.isDesignSide ? blockRelX : (blockRelX - (canvasW + 16))
  const rawY = blockRelY

  const curX = Math.round(Math.max(0, Math.min(canvasW, rawX)))
  const curY = Math.round(Math.max(0, Math.min(canvasH, rawY)))

  let w = Math.abs(curX - s.startX)
  let h = Math.abs(curY - s.startY)

  // Shift 键或圆形工具强制等比约束 (正方形 / 正圆)
  if (e.shiftKey || s.tool === 'circle') {
    const maxSide = Math.max(w, h)
    w = maxSide
    h = maxSide
  }

  const left = curX >= s.startX ? s.startX : s.startX - w
  const top = curY >= s.startY ? s.startY : s.startY - h

  s.curX = curX
  s.curY = curY
  s.left = Math.max(0, left)
  s.top = Math.max(0, top)
  s.width = w
  s.height = h
}

function onArtboardDrawMouseUp(e: MouseEvent) {
  window.removeEventListener('mousemove', onArtboardDrawMouseMove, { capture: true })
  if (!activeShapeDraw.value) return
  e.preventDefault()
  e.stopPropagation()

  const s = activeShapeDraw.value
  activeShapeDraw.value = null

  const b = s.block
  const tool = s.tool
  let w = Math.round(s.width)
  let h = Math.round(s.height)
  let left = Math.round(s.left)
  let top = Math.round(s.top)

  let targetSnippet = ''
  let targetName = ''
  let isText = false

  // 如果拖拽尺寸小于 6px，视作快速单点点击，自动回退至标准推荐尺寸
  const isClick = w < 6 && h < 6

  if (tool === 'rect') {
    targetName = '纯矩形 (R)'
    if (isClick) { w = 140; h = 90; }
    targetSnippet = `<div class="wf-shape wf-shape-rect" style="width: ${w}px; height: ${h}px; background: #e2e8f0; border: 1.5px solid #94a3b8; border-radius: 6px; box-sizing: border-box;"></div>`
  } else if (tool === 'circle') {
    targetName = '纯圆形/椭圆 (O)'
    if (isClick) { w = 80; h = 80; }
    targetSnippet = `<div class="wf-shape wf-shape-circle" style="width: ${w}px; height: ${h}px; background: #e2e8f0; border: 1.5px solid #94a3b8; border-radius: 50%; box-sizing: border-box;"></div>`
  } else if (tool === 'line') {
    targetName = '水平分割线 (L)'
    if (isClick) { w = 240; h = 2; }
    targetSnippet = `<div class="wf-shape wf-shape-line" style="width: ${w}px; height: ${Math.max(2, h)}px; background: #94a3b8; box-sizing: border-box;"></div>`
  } else if (tool === 'container') {
    targetName = '空白容器卡片 (Box)'
    if (isClick) { w = 320; h = 160; }
    targetSnippet = `<div class="wf-shape wf-shape-card" style="width: ${w}px; height: ${h}px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px; box-shadow: 0 4px 14px rgba(0,0,0,0.06); box-sizing: border-box;"></div>`
  } else if (tool === 'text') {
    targetName = '纯文本 (T)'
    isText = true
    if (isClick) {
      targetSnippet = `<div class="wf-text" style="display: inline-block; font-size: 16px; font-weight: 500; color: #1e293b; line-height: 1.4; outline: none; min-width: 40px; cursor: text;">文本</div>`
    } else {
      targetSnippet = `<div class="wf-text" style="display: block; width: ${w}px; min-height: ${Math.max(24, h)}px; font-size: 16px; font-weight: 500; color: #1e293b; line-height: 1.4; outline: none; cursor: text;">文本</div>`
    }
  } else if (tool === 'frame') {
    handleCreateFramePreset()
    activeDrawTool.value = 'select'
    return
  }

  insertComponentIntoPage(b.page.id, { name: targetName, html: targetSnippet }, left, top, isText)

  // 注入后自动重置 activeDrawTool = 'select' 退出绘制模式，恢复选择与编辑态
  activeDrawTool.value = 'select'
  ElMessage.success(`已绘制「${targetName}」(${w} × ${h}px)`)
}

// 自由拖拽绘制画板框架 (Drag-to-Draw Frame)
function startFrameDraw(logicX: number, logicY: number) {
  activeFrameDraw.value = {
    startX: logicX,
    startY: logicY,
    curX: logicX,
    curY: logicY,
    left: logicX,
    top: logicY,
    width: 0,
    height: 0,
  }

  function onFrameMouseMove(e: MouseEvent) {
    if (!activeFrameDraw.value || !viewportRef.value) return
    const rect = viewportRef.value.getBoundingClientRect()
    const curX = Math.round((e.clientX - rect.left - view.value.x) / view.value.k)
    const curY = Math.round((e.clientY - rect.top - view.value.y) / view.value.k)
    let w = Math.abs(curX - activeFrameDraw.value.startX)
    let h = Math.abs(curY - activeFrameDraw.value.startY)
    if (e.shiftKey) {
      const maxSide = Math.max(w, h)
      w = maxSide
      h = maxSide
    }
    const left = curX >= activeFrameDraw.value.startX ? activeFrameDraw.value.startX : activeFrameDraw.value.startX - w
    const top = curY >= activeFrameDraw.value.startY ? activeFrameDraw.value.startY : activeFrameDraw.value.startY - h

    activeFrameDraw.value.curX = curX
    activeFrameDraw.value.curY = curY
    activeFrameDraw.value.left = left
    activeFrameDraw.value.top = top
    activeFrameDraw.value.width = w
    activeFrameDraw.value.height = h
  }

  async function onFrameMouseUp(e: MouseEvent) {
    window.removeEventListener('mousemove', onFrameMouseMove, { capture: true })
    if (!activeFrameDraw.value) return
    const s = activeFrameDraw.value
    activeFrameDraw.value = null

    const w = Math.round(s.width)
    const h = Math.round(s.height)
    const isClick = w < 20 && h < 20
    const finalW = isClick ? 375 : Math.max(120, w)
    const finalH = isClick ? 812 : Math.max(120, h)
    const finalX = isClick ? s.startX : s.left
    const finalY = isClick ? s.startY : s.top

    await handleCreateFrameOnCanvas(finalX, finalY, {
      name: `Frame ${pages.value.length + 1}`,
      width: finalW,
      height: finalH,
    })
  }

  window.addEventListener('mousemove', onFrameMouseMove, { capture: true })
  window.addEventListener('mouseup', onFrameMouseUp, { capture: true, once: true })
}

function createHtmlSnippetForElement(el: any): string {
  if (el.type === 'button') {
    return `<button class="wf-btn" style="width:${el.width}px;height:${el.height}px;background:#2563eb;color:#fff;border:none;border-radius:6px;font-weight:600;font-size:14px;cursor:pointer;">${el.label || '按钮'}</button>`
  }
  if (el.type === 'text') {
    return `<div class="wf-text" style="font-size:14px;color:#0f172a;line-height:1.5;">${el.label || '输入文本'}</div>`
  }
  return `<div class="wf-shape wf-shape-rect" style="width:${el.width}px;height:${el.height}px;background:#f1f5f9;border:1.5px solid #cbd5e1;border-radius:8px;"></div>`
}

// ===== Figma Frame 画板创建与画布落盘核心体系 =====
async function doCreatePage(params: { name: string; width: number; height: number; x: number; y: number; htmlContent?: string }) {
  try {
    const defaultHtml = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=${params.width}">
  <style>
    body {
      margin: 0;
      padding: 16px;
      background: #ffffff;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      box-sizing: border-box;
      min-height: ${params.height}px;
      position: relative;
    }
  </style>
</head>
<body>
</body>
</html>`

    const newPage = await projectApi.createPage(id, {
      name: params.name,
      width: params.width,
      height: params.height,
      x: params.x,
      y: params.y,
      htmlContent: params.htmlContent || defaultHtml,
    })

    showToast(`✅ 已新建画板「${newPage?.name || params.name}」(${params.width}×${params.height})`)
    await loadData()
    if (newPage && newPage.id) {
      focusPageId.value = newPage.id
      selectedNodeId.value = newPage.id
      nextTick(() => {
        focusPage(newPage.id)
      })
    }
  } catch (err: any) {
    ElMessage.error(`创建画板失败: ${err?.response?.data?.message || err?.message || '网络错误'}`)
  }
}

async function handleCreateFramePreset(preset?: { name: string; width: number; height: number }) {
  const pName = preset?.name || '新画板'
  const pWidth = preset?.width || 375
  const pHeight = preset?.height || 812

  // 智能计算新画板在无限画布上的位置：放置在已有最右侧画板的右侧
  let posX = 56
  let posY = 64
  if (blocks.value.length > 0) {
    const rightmost = blocks.value.reduce((max, b) => Math.max(max, b.x + b.w), 0)
    posX = rightmost + 120
    posY = blocks.value[0]?.y || 64
  }

  await doCreatePage({
    name: pName,
    width: pWidth,
    height: pHeight,
    x: posX,
    y: posY,
  })
}

async function handleCreateFrameOnCanvas(logicX: number, logicY: number, preset?: { name: string; width: number; height: number }) {
  const pName = preset?.name || '新画板'
  const pWidth = preset?.width || 375
  const pHeight = preset?.height || 812

  await doCreatePage({
    name: pName,
    width: pWidth,
    height: pHeight,
    x: logicX,
    y: logicY,
  })
  activeDrawTool.value = 'select'
}

async function confirmDeletePage(page: Page) {
  try {
    await ElMessageBox.confirm(
      `确定要删除画板「${page.name}」吗？删除后画板及其包含的所有组件和交互将被移除。`,
      '删除画板确认',
      {
        confirmButtonText: '删除画板',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        type: 'warning',
      }
    )

    await projectApi.deletePage(id, page.id)
    showToast(`🗑️ 已删除画板「${page.name}」`)
    if (pageOverrides.value[page.id]) {
      delete pageOverrides.value[page.id]
    }
    if (focusPageId.value === page.id) {
      focusPageId.value = null
    }
    if (selectedNodeId.value === page.id) {
      selectedNodeId.value = null
    }
    if (previewPageId.value === page.id) {
      previewPageId.value = null
    }
    await loadData()
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(`删除画板失败: ${err?.response?.data?.message || err?.message || '网络错误'}`)
    }
  }
}

function handleBottomToolChange(tool: string) {
  if (['select', 'frame', 'rect', 'circle', 'container', 'line', 'text'].includes(tool)) {
    activeDrawTool.value = tool as ActiveToolType
    if (tool === 'frame') {
      showToast('已激活「画板框架 (F)」工具：点击画布任意空白处即可生成新画板，或按 Esc 取消')
    } else if (tool !== 'select') {
      showToast(`已激活「${toolNames[tool] || tool}」绘制工具：点击画板任意位置放置，按 Esc 键取消`)
    }
  }
}

function handleBottomAddComponent(item: any) {
  const targetId = focusPageId.value || selectedNodeId.value || pages.value[0]?.id
  if (!targetId) {
    showToast('⚠️ 请先在画布上点击选择一个目标画板')
    return
  }
  insertComponentIntoPage(targetId, item, 20, 220)
}

const mode = ref<'edit' | 'preview'>('edit')

// 预览手机壳按视口可用宽度和高度双向等比自适应，确保在任意屏幕分辨率下整机 100% 完整可见
const deviceRef = ref<HTMLElement | null>(null)
const phoneScale = ref(1)
function updatePhoneScale() {
  const el = deviceRef.value
  if (!el) return
  const PAD_X = 32
  const PAD_Y = 48
  const availW = Math.max(80, el.clientWidth - PAD_X)
  const availH = Math.max(80, el.clientHeight - PAD_Y)
  const phoneW = 375
  const phoneH = previewHeight.value || 812
  const scaleW = availW / phoneW
  const scaleH = availH / phoneH
  phoneScale.value = Math.min(1, Math.min(scaleW, scaleH))
}
const selectedElementId = ref<number | null>(null)
const hoveredElementId = ref<number | null>(null)
const hoveredAnnId = ref<number | null>(null)

const previewPageId = ref<number | null>(null)
const slideClass = ref('')
const toastMsg = ref('')
let toastTimer: ReturnType<typeof setTimeout> | null = null

// ===== 整页 HTML 重新生成（Stitch 式直出快速迭代） =====
const regeneratingIds = ref<Set<number>>(new Set())
const regenChecked = ref<Set<number>>(new Set())
const regenAll = computed(() => {
  const regenable = pages.value.filter((p) => p.html_content).map((p) => p.id)
  return regenable.length > 0 && regenable.every((id) => regenChecked.value.has(id))
})
function toggleRegenAll() {
  const regenable = pages.value.filter((p) => p.html_content).map((p) => p.id)
  if (regenAll.value) {
    regenable.forEach((id) => regenChecked.value.delete(id))
  } else {
    regenable.forEach((id) => regenChecked.value.add(id))
  }
  regenChecked.value = new Set(regenChecked.value)
}

async function onRegenerateHtml(pageId: number) {
  if (regeneratingIds.value.has(pageId)) return
  regeneratingIds.value.add(pageId)
  try {
    const html = await projectApi.regenerateHtml(id, pageId)
    const page = proto.value?.pages.find((p) => p.id === pageId)
    if (page) page.html_content = html
    showToast(`✅ 页面「${page?.name || pageId}」原型交互已刷新`)
  } catch (e: any) {
    showToast(`❌ 刷新失败: ${e?.message || '未知错误'}`)
  } finally {
    regeneratingIds.value.delete(pageId)
    regenChecked.value.delete(pageId)
    regeneratingIds.value = new Set(regeneratingIds.value)
  }
}

async function onRegenerateChecked() {
  const targets = pages.value.filter((p) => p.html_content && regenChecked.value.has(p.id)).map((p) => p.id)
  if (!targets.length) {
    showToast('请先勾选要刷新的页面')
    return
  }
  let ok = 0
  for (const pageId of targets) {
    await onRegenerateHtml(pageId)
    ok++
  }
  showToast(`✅ 已刷新 ${ok} 个页面的原型交互`)
}

const isReanalyzing = ref(false)

async function loadData() {
  try {
    proto.value = await projectApi.prototype(id)
    await loadComments()
    for (const p of proto.value.pages) {
      if (p.canvas_x != null && p.canvas_y != null) {
        pageOverrides.value[p.id] = { x: p.canvas_x, y: p.canvas_y }
      }
      // 从后端返回的数据中初始化各页面的说明排序
      if (p.annotations && p.annotations.length) {
        pageAnnOrders.value[p.id] = p.annotations.map((a) => a.id)
      }
    }
    const qPage = route.query.page ? Number(route.query.page) : null
    if (qPage && proto.value.pages.some((p) => p.id === qPage)) {
      focusPageId.value = qPage
      previewPageId.value = qPage
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  }
}

async function onReanalyzePage(pageId: number) {
  if (isReanalyzing.value) return
  isReanalyzing.value = true
  const page = proto.value?.pages.find((p) => p.id === pageId)
  showToast(`🧠 正在调用 AI 重新深度识别「${page?.name || pageId}」...`)
  try {
    await projectApi.reanalyzePage(id, pageId)
    await loadData()
    showToast(`✅ 页面「${page?.name || pageId}」已由 AI 重新识别并更新！`)
  } catch (e: any) {
    showToast(`❌ 识别失败: ${e?.message || '未知错误'}`)
  } finally {
    isReanalyzing.value = false
    regenChecked.value.delete(pageId)
  }
}

async function onReanalyzeChecked() {
  const targets = pages.value.filter((p) => regenChecked.value.has(p.id)).map((p) => p.id)
  if (!targets.length) {
    showToast('请先勾选要重新识别的页面')
    return
  }
  if (isReanalyzing.value) return
  isReanalyzing.value = true
  showToast(`🧠 正在调用 AI 重新深度识别选中的 ${targets.length} 个页面...`)
  try {
    for (const pageId of targets) {
      await projectApi.reanalyzePage(id, pageId)
    }
    await loadData()
    showToast(`✅ 已完成 ${targets.length} 个页面的 AI 深度重新识别！`)
  } catch (e: any) {
    showToast(`❌ 重新识别失败: ${e?.message || '未知错误'}`)
  } finally {
    isReanalyzing.value = false
    regenChecked.value.clear()
  }
}
const backStack = ref<number[]>([])
function onProtoBack() {
  clearSpotlightInIframe()
  const prev = backStack.value.pop()
  if (prev != null && pages.value.some((p) => p.id === prev)) {
    if (mode.value === 'preview' && previewPageId.value != null) {
      slideClass.value = 'slide-in-left'
      previewPageId.value = prev
      setTimeout(() => (slideClass.value = ''), 400)
    } else {
      focusPage(prev)
    }
    showToast('← 返回')
  } else {
    showToast('已是第一页')
  }
}

function findTargetPage(pageKey: string, fromPageId?: number | null): Page | null {
  if (!pageKey) return null
  const rawKey = pageKey.trim()

  // 1. 直接通过页面 ID 匹配
  const asNum = Number(rawKey)
  if (!isNaN(asNum) && asNum > 0) {
    const byId = pages.value.find((p) => p.id === asNum)
    if (byId) return byId
  }

  // 2. 若有来源页面上下文，优先寻找当前页具有对应交互行为的目标页面
  if (fromPageId) {
    const fromPage = pages.value.find((p) => p.id === fromPageId)
    if (fromPage) {
      const matchedEl = fromPage.elements.find(
        (e) =>
          e.interaction?.target_page_id &&
          (e.label === rawKey || (e.interaction.params && e.interaction.params.includes(rawKey)))
      )
      if (matchedEl?.interaction?.target_page_id) {
        const targetById = pages.value.find((p) => p.id === matchedEl.interaction!.target_page_id)
        if (targetById) return targetById
      }
    }
  }

  // 3. 页面全名精准匹配
  const exact = pages.value.find((p) => p.name.trim() === rawKey)
  if (exact) return exact

  // 4. 常见语义模糊归一化处理（彻底修复“我的”跳错、“家园”跳错问题）
  const norm = (s: string) => s.replace(/[（\(].*?[）\)]/g, '').replace(/[_\-\s]/g, '').trim()
  const normKey = norm(rawKey)

  if (normKey === '我的') {
    const myLogged = pages.value.find((p) => p.name.includes('我的') && p.name.includes('已登录'))
    if (myLogged) return myLogged
    const myPage = pages.value.find((p) => p.name.startsWith('我的') && !p.name.includes('设置'))
    if (myPage) return myPage
  }
  if (normKey === '家园' || normKey === '宠物家园') {
    const home = pages.value.find((p) => p.name.includes('宠物家园') || p.name.includes('家园'))
    if (home) return home
  }
  if (normKey === '收集') {
    const coll = pages.value.find((p) => p.name.includes('收集'))
    if (coll) return coll
  }

  const normMatch = pages.value.find((p) => norm(p.name) === normKey)
  if (normMatch) return normMatch

  // 5. 前缀与包含匹配
  const startsWith = pages.value.find((p) => p.name.trim().startsWith(rawKey))
  if (startsWith) return startsWith

  const includes = pages.value.find((p) => p.name.trim().includes(rawKey))
  if (includes) return includes

  const reverseIncludes = pages.value.find((p) => rawKey.includes(p.name.trim()))
  if (reverseIncludes) return reverseIncludes

  return null
}

// ===== 整页 HTML 内的跨页跳转：点击 data-nav 元素 → 切换到目标页原型 =====
function onProtoNavigate(pageName: string) {
  clearSpotlightInIframe()
  const current = mode.value === 'preview' ? previewPageId.value : focusPageId.value ?? null
  if (current != null) backStack.value.push(current)

  const target = findTargetPage(pageName, current)
  if (!target) {
    showToast(`未找到页面「${pageName}」`)
    return
  }
  if (mode.value === 'preview' && previewPageId.value != null) {
    slideClass.value = 'slide-in-right'
    previewPageId.value = target.id
    setTimeout(() => (slideClass.value = ''), 400)
  } else {
    focusPage(target.id)
  }
  showToast(`↗ 跳转到「${target.name}」`)
}

// ===== 无限画布状态（translate + scale） =====
const view = ref({ x: 0, y: 0, k: 1 })
const isDragging = ref(false)
const isBlockDragging = ref(false)
const isAnyDragging = computed(() => isDragging.value || isBlockDragging.value)
const animating = ref(false)
let dragStart = { x: 0, y: 0 }
let dragOrigin = { x: 0, y: 0 }
let animTimer: ReturnType<typeof setTimeout> | null = null

const viewportRef = ref<HTMLElement | null>(null)

// ===== 页面区块布局 =====
const pageRefs = ref<Record<number, InstanceType<typeof PageCanvas> | null>>({})

function setPageRef(pageId: number, el: InstanceType<typeof PageCanvas> | null) {
  pageRefs.value[pageId] = el
}

const fallbackW = computed(() => (showAnnotations.value ? 980 : 766))
const fallbackH = 1000

const pageOverrides = ref<Record<number, { x: number; y: number }>>({})

const blocks = computed(() => {
  // 依赖跟踪：当标注面板显隐或线框图显隐切换时，重新排版各画板位置
  const _ann = showAnnotations.value
  const _wire = showWireframe.value
  const list: { page: Page; x: number; y: number; w: number; h: number }[] = []
  const perRow = 10
  const gapX = 120
  const gapY = 160
  let x = 56
  let y = 64
  let col = 0
  let rowMaxH = 0
  pages.value.forEach((p) => {
    const inst = pageRefs.value[p.id]
    const w = inst?.stageW ?? fallbackW.value
    const h = inst?.stageH ?? fallbackH
    if (col >= perRow) {
      y += rowMaxH + gapY
      x = 56
      col = 0
      rowMaxH = 0
    }
    const defX = x
    const defY = y
    x += w + gapX
    col++
    rowMaxH = Math.max(rowMaxH, h)
    const ov = pageOverrides.value[p.id]
    list.push({ page: p, x: ov ? ov.x : defX, y: ov ? ov.y : defY, w, h })
  })
  return list
})

// ===== 交互连线模式 (Prototype Mode) 连线逻辑与弹性贝塞尔曲线 =====
interface AnchorItem {
  id: string
  pageId: number
  pageName: string
  elementId: number
  label: string
  x: number
  y: number
}

const isDraggingConnection = ref(false)
const currentDraggingAnchor = ref<AnchorItem | null>(null)
const dragCurrentPos = ref({ x: 0, y: 0 })
const hoveredTargetBlockId = ref<number | null>(null)

const showInteractionModal = ref(false)
const isSavingInteraction = ref(false)
const interactionForm = ref({
  trigger: 'click',
  action: 'navigate',
  targetPageId: null as number | null,
  animation: 'push',
})

const triggerOptions = [
  { label: 'On Click (点击)', value: 'click' },
  { label: 'On Hover (悬停)', value: 'hover' },
  { label: 'On Drag (拖拽)', value: 'drag' },
]

const actionOptions = [
  { label: 'Navigate to (跳转页面)', value: 'navigate' },
  { label: 'Open Overlay (打开弹窗)', value: 'overlay' },
]

const animationOptions = [
  { label: 'Push (推入)', value: 'push' },
  { label: 'Instant (即时)', value: 'instant' },
  { label: 'Slide (滑入)', value: 'slide' },
  { label: 'Dissolve (淡入)', value: 'dissolve' },
]

/**
 * 过滤提取页面的真实交互热区元素：
 * 1. 严格使用已有跳转关系的组件 (el.interaction && el.interaction.target_page_id)
 * 2. 针对底部导航栏 (如家园、空间、我的) 同时存在 icon 与 text 两条数据的情况，
 *    按产品要求保留图标 (icon) 的交互连线，去掉冗余的文本 (text) 连线，杜绝两条重复连线！
 * 3. 针对按钮附带角标/小文本也指向同目标页的情况，仅保留主按钮，避免多余杂线。
 */
function getPrimaryInteractiveElements(page: Page): Element[] {
  const raw = (page.elements || []).filter((e) => e.interaction && e.interaction.target_page_id)
  const result: Element[] = []

  for (const el of raw) {
    const targetId = el.interaction!.target_page_id
    // 检查同页面中是否有指向同一目标页面的更优先组件
    const hasBetterAlternative = raw.some((other) => {
      if (other.id === el.id) return false
      if (other.interaction?.target_page_id !== targetId) return false

      // 导航栏去重：文本 vs 图标 -> 丢弃文本，保留图标
      if (el.type === 'text' && other.type === 'icon') {
        return true
      }
      // 按钮 vs 角标/文本 -> 丢弃角标/文本，保留按钮
      if ((el.type === 'badge' || el.type === 'text') && other.type === 'button') {
        return true
      }
      // 若类型相同且位置高度接近（同个导航槽），保留 ID 较小的一个
      if (el.type === other.type && Math.abs(el.x - other.x) < 50 && Math.abs(el.y - other.y) < 60) {
        return el.id > other.id
      }
      return false
    })

    if (!hasBetterAlternative) {
      result.push(el)
    }
  }
  return result
}

// ===== Figma Prototype 模式：节点驱动连线架构 =====
const selectedNodeId = ref<number | null>(null)
const hoveredNodeId = ref<number | null>(null)

interface NodeItem {
  id: number
  name: string
  x: number
  y: number
  width: number
  height: number
  wireX: number
  pageW: number
  pageH: number
}

const nodes = computed<NodeItem[]>(() => {
  return blocks.value.map((b) => {
    const scaleVal = 1
    const pageW = b.page.canvas_width || 375
    const pageH = b.page.canvas_height || 812
    // showDesign is always true on the workbench canvas, so wireframe always starts at pageW + gap (16)
    const wireX = pageW + 16
    return {
      id: b.page.id,
      name: b.page.name,
      x: b.x,
      y: b.y,
      width: b.w,
      height: b.h,
      wireX,
      pageW,
      pageH,
    }
  })
})

interface ConnectionItem {
  id: string
  fromId: number
  toId: number
  fromPageName: string
  toPageName: string
  elementId?: number
  interactionId?: number
  trigger?: string
  action?: string
  params?: string | null
  label: string
  isSelfLoop: boolean
  x1: number
  y1: number
  x2: number
  y2: number
  path: string
  midX: number
  midY: number
  triggerLabel: string
  actionLabel: string
}

const connections = computed<ConnectionItem[]>(() => {
  const nodeMap = new Map<number, NodeItem>()
  nodes.value.forEach((n) => nodeMap.set(n.id, n))

  const list: ConnectionItem[] = []

  for (const b of blocks.value) {
    const fromNode = nodeMap.get(b.page.id)
    if (!fromNode) continue

    const primaryEls = getPrimaryInteractiveElements(b.page)

    for (const el of primaryEls) {
      const targetId = el.interaction!.target_page_id
      if (!targetId) continue

      const toNode = nodeMap.get(targetId)
      if (!toNode) continue

      const isSelfLoop = fromNode.id === toNode.id
      const triggerMap: Record<string, string> = { click: '点击', hover: '悬停', drag: '拖拽' }
      const actionMap: Record<string, string> = { navigate: '跳转', overlay: '弹窗', back: '返回' }
      const triggerLabel = triggerMap[el.interaction!.trigger] || '点击'
      const actionLabel = actionMap[el.interaction!.action] || toNode.name
      const elW = el.width || 60
      const elH = el.height || 30
      const elCenterX = fromNode.x + fromNode.wireX + el.x + elW / 2
      const elCenterY = fromNode.y + Math.max(15, Math.min(fromNode.pageH - 15, el.y + elH / 2))

      const toWireX = toNode.x + toNode.wireX
      const toRightX = toWireX + toNode.pageW
      const fromWireX = fromNode.x + fromNode.wireX

      let startX = elCenterX
      let startY = elCenterY
      let endX = 0
      let endY = 0
      let path = ''
      let midX = 0
      let midY = 0

      if (isSelfLoop) {
        // 自环情况：从组件右侧绕出，优雅回折入顶部
        startX = fromNode.x + fromNode.wireX + el.x + elW
        startY = elCenterY
        endX = fromNode.x + fromNode.wireX + fromNode.pageW / 2
        endY = fromNode.y
        const loopOutX = startX + 40
        const loopTopY = fromNode.y - 35
        path = `M ${startX} ${startY} C ${loopOutX} ${startY}, ${loopOutX} ${loopTopY}, ${endX + 25} ${loopTopY} S ${endX} ${fromNode.y - 12}, ${endX} ${endY}`
        midX = startX + 25
        midY = fromNode.y - 25
      } else if (toRightX <= fromWireX + 30) {
        // 目标页面在源页面左侧 (正如用户所附 Figma 截图：右屏 APP 连向左屏 Today！)
        startX = fromNode.x + fromNode.wireX + el.x + Math.min(12, elW / 2)
        startY = elCenterY
        endX = toRightX
        endY = Math.max(toNode.y + 40, Math.min(toNode.y + toNode.pageH - 40, elCenterY))

        const dx = Math.abs(startX - endX)
        const cx1 = startX - Math.max(dx * 0.45, 50)
        const cy1 = startY
        const cx2 = endX + Math.max(dx * 0.45, 50)
        const cy2 = endY
        path = `M ${startX} ${startY} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${endX} ${endY}`

        midX = (startX + endX) / 2
        midY = (startY + endY) / 2
      } else {
        // 目标页面在源页面右侧或下方
        startX = fromNode.x + fromNode.wireX + el.x + elW - Math.min(12, elW / 2)
        startY = elCenterY
        endX = toWireX
        endY = Math.max(toNode.y + 40, Math.min(toNode.y + toNode.pageH - 40, elCenterY))

        const dx = Math.abs(endX - startX)
        const cx1 = startX + Math.max(dx * 0.45, 50)
        const cy1 = startY
        const cx2 = endX - Math.max(dx * 0.45, 50)
        const cy2 = endY
        path = `M ${startX} ${startY} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${endX} ${endY}`

        midX = (startX + endX) / 2
        midY = (startY + endY) / 2
      }

      list.push({
        id: `conn-${el.id}-${toNode.id}`,
        fromId: fromNode.id,
        toId: toNode.id,
        fromPageName: fromNode.name,
        toPageName: toNode.name,
        elementId: el.id,
        interactionId: el.interaction?.id,
        trigger: el.interaction?.trigger,
        action: el.interaction?.action,
        params: el.interaction?.params,
        label: el.label || el.type,
        isSelfLoop,
        x1: startX,
        y1: startY,
        x2: endX,
        y2: endY,
        path,
        midX,
        midY,
        triggerLabel,
        actionLabel,
      })
    }
  }

  return list
})

// 单选特定连线状态：点击某条连接线时，只高亮该条连线，其他连线全部隐藏
const selectedConnId = ref<string | null>(null)

// 连线激活状态：
// 1. 如果选中了某条特定的连接线，其他所有连线隐藏，仅展示当前选中的连线（还原 Figma 连线点击聚焦）
// 2. 在交互连线模式下，展示所有连线
// 3. 在走查模式下，若点击了某节点则展示关联线，否则隐藏
const visibleConnections = computed<ConnectionItem[]>(() => {
  if (selectedConnId.value) {
    return connections.value.filter((c) => c.id === selectedConnId.value)
  }
  if (workbenchMode.value === 'interactive') {
    return connections.value
  }
  if (!selectedNodeId.value) return []
  return connections.value.filter(
    (c) => c.fromId === selectedNodeId.value || c.toId === selectedNodeId.value
  )
})

function getPageName(pageId?: number | null): string {
  if (!pageId) return ''
  return pages.value.find((p) => p.id === pageId)?.name || '目标画板'
}

// ===== 连线撤销与重做历史记录栈 (支持 Ctrl+Z / Ctrl+Y) =====
interface InteractionHistoryItem {
  type: 'delete' | 'connect'
  elementId?: number
  pageId: number
  targetPageId?: number | null
  prevTargetPageId?: number | null
  triggerType: string
  actionType: string
  params?: string
  label: string
  fromPageName: string
  toPageName?: string
  prevToPageName?: string
}

const interactionUndoStack = ref<InteractionHistoryItem[]>([])
const interactionRedoStack = ref<InteractionHistoryItem[]>([])

async function undoInteraction(): Promise<boolean> {
  if (interactionUndoStack.value.length === 0) return false
  const item = interactionUndoStack.value.pop()!
  interactionRedoStack.value.push(item)

  try {
    if (item.type === 'delete') {
      await projectApi.saveInteraction(id, {
        elementId: item.elementId,
        pageId: item.pageId,
        targetPageId: item.targetPageId,
        triggerType: item.triggerType,
        actionType: item.actionType,
        params: item.params,
      })
      await loadData()
      selectedConnId.value = `conn-${item.elementId}-${item.targetPageId}`
      ElMessage.success(`↺ 已撤销删除：已恢复「${item.label} ➔ ${item.toPageName || '目标画板'}」连线`)
      return true
    } else if (item.type === 'connect') {
      if (item.prevTargetPageId) {
        await projectApi.saveInteraction(id, {
          elementId: item.elementId,
          pageId: item.pageId,
          targetPageId: item.prevTargetPageId,
          triggerType: item.triggerType,
          actionType: item.actionType,
          params: item.params,
        })
        selectedConnId.value = `conn-${item.elementId}-${item.prevTargetPageId}`
        ElMessage.success(`↺ 已撤销连线：已恢复为「${item.label} ➔ ${item.prevToPageName || '原目标画板'}」`)
      } else {
        await projectApi.saveInteraction(id, {
          elementId: item.elementId,
          pageId: item.pageId,
          targetPageId: null,
        })
        selectedConnId.value = null
        ElMessage.success(`↺ 已撤销新建连线`)
      }
      await loadData()
      return true
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '撤销连线失败')
  }
  return false
}

async function redoInteraction(): Promise<boolean> {
  if (interactionRedoStack.value.length === 0) return false
  const item = interactionRedoStack.value.pop()!
  interactionUndoStack.value.push(item)

  try {
    if (item.type === 'delete') {
      await projectApi.saveInteraction(id, {
        elementId: item.elementId,
        pageId: item.pageId,
        targetPageId: null,
      })
      selectedConnId.value = null
      await loadData()
      ElMessage.info(`↻ 已重做删除：连线已再次删除`)
      return true
    } else if (item.type === 'connect') {
      await projectApi.saveInteraction(id, {
        elementId: item.elementId,
        pageId: item.pageId,
        targetPageId: item.targetPageId,
        triggerType: item.triggerType,
        actionType: item.actionType,
        params: item.params,
      })
      await loadData()
      selectedConnId.value = `conn-${item.elementId}-${item.targetPageId}`
      ElMessage.success(`↻ 已重做连线：「${item.label} ➔ ${item.toPageName || '目标画板'}」`)
      return true
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '重做连线失败')
  }
  return false
}

function selectConnection(conn: ConnectionItem) {
  selectedConnId.value = conn.id
  selectedElementId.value = conn.elementId || null
  selectedNodeId.value = conn.fromId
  ElMessage.info({
    message: `已选中连线：「${conn.label} ➔ ${conn.toPageName}」，按键盘 Backspace 键可直接删除`,
    duration: 3000,
  })
}

function isConnActive(conn: ConnectionItem): boolean {
  if (selectedConnId.value === conn.id) return true
  if (selectedElementId.value && conn.elementId === selectedElementId.value) return true
  if (selectedNodeId.value && (conn.fromId === selectedNodeId.value || conn.toId === selectedNodeId.value)) return true
  return false
}

function onInteractiveElementClick(b: any, el: Element) {
  selectedNodeId.value = b.page.id
  selectedElementId.value = el.id
  focusPageId.value = b.page.id
  // 检查该按钮是否已有连线；若已有连线，只聚焦该连线，其他连线隐藏
  const matchingConn = connections.value.find((c) => c.elementId === el.id)
  if (matchingConn) {
    selectedConnId.value = matchingConn.id
  } else {
    selectedConnId.value = null
  }
}

function onInteractiveElementDblClick(b: any, el: Element) {
  onInteractiveElementClick(b, el)
  ElMessage.info({
    message: `已选中「${el.label || el.type}」，拖拽右侧白色圆点手柄可直接连接至目标画板`,
    duration: 3000,
  })
}

function startElementConnectionDrag(event: MouseEvent, b: any, el: Element) {
  event.preventDefault()
  event.stopPropagation()

  selectedNodeId.value = b.page.id
  selectedElementId.value = el.id
  focusPageId.value = b.page.id

  const pageW = b.page.canvas_width || 375
  // showDesign is always true on canvas, wireframe always starts after design column
  const wireX = pageW + 16
  const elW = el.width || 60
  const elH = el.height || 30
  const anchorX = b.x + wireX + el.x + elW / 2
  const anchorY = b.y + el.y + elH / 2

  const anchor: AnchorItem = {
    id: `el-${el.id}`,
    pageId: b.page.id,
    pageName: b.page.name,
    elementId: el.id,
    label: el.label || el.type,
    x: anchorX,
    y: anchorY,
  }

  startConnectionDrag(event, anchor)
}

const activeDragLine = computed(() => {
  if (!isDraggingConnection.value || !currentDraggingAnchor.value) return null
  const x1 = currentDraggingAnchor.value.x
  const y1 = currentDraggingAnchor.value.y
  let x2 = dragCurrentPos.value.x
  let y2 = dragCurrentPos.value.y

  if (hoveredTargetBlockId.value) {
    const targetB = blocks.value.find((b) => b.page.id === hoveredTargetBlockId.value)
    if (targetB) {
      // showDesign always true on workbench canvas, wireframe always starts after design column
      const targetWireX = (targetB.page.canvas_width || 375) + 16
      const targetPageW = targetB.page.canvas_width || 375
      if (x1 > targetB.x + targetWireX + targetPageW) {
        x2 = targetB.x + targetWireX + targetPageW
      } else {
        x2 = targetB.x + targetWireX
      }
      y2 = Math.max(targetB.y + 40, Math.min(targetB.y + targetB.h - 40, y1))
    }
  }

  const dx = Math.abs(x2 - x1)
  const isTargetLeft = x2 < x1
  const cx1 = isTargetLeft ? x1 - Math.max(dx * 0.45, 60) : x1 + Math.max(dx * 0.45, 60)
  const cy1 = y1
  const cx2 = isTargetLeft ? x2 + Math.max(dx * 0.45, 60) : x2 - Math.max(dx * 0.45, 60)
  const cy2 = y2
  const path = `M ${x1} ${y1} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${x2} ${y2}`
  return { path, x1, y1, x2, y2 }
})

function startConnectionDrag(event: MouseEvent, anchor: AnchorItem) {
  event.preventDefault()
  event.stopPropagation()
  isDraggingConnection.value = true
  currentDraggingAnchor.value = anchor

  if (viewportRef.value) {
    const rect = viewportRef.value.getBoundingClientRect()
    dragCurrentPos.value = {
      x: (event.clientX - rect.left - view.value.x) / view.value.k,
      y: (event.clientY - rect.top - view.value.y) / view.value.k,
    }
  } else {
    dragCurrentPos.value = { x: anchor.x, y: anchor.y }
  }
  hoveredTargetBlockId.value = null

  function onWindowMouseMove(e: MouseEvent) {
    if (!viewportRef.value) return
    const rect = viewportRef.value.getBoundingClientRect()
    const cx = (e.clientX - rect.left - view.value.x) / view.value.k
    const cy = (e.clientY - rect.top - view.value.y) / view.value.k
    dragCurrentPos.value = { x: cx, y: cy }

    let foundTargetId: number | null = null
    for (const b of blocks.value) {
      if (b.page.id === anchor.pageId) continue
      const pageW = b.page.canvas_width || 375
      const pageH = b.page.canvas_height || 812
      // showDesign always true, wireframe starts after design column
      const wireX = pageW + 16
      const left = b.x - 40
      const right = b.x + Math.max(b.w, wireX + pageW) + 40
      const top = b.y - 40
      const bottom = b.y + Math.max(b.h, pageH) + 40

      if (cx >= left && cx <= right && cy >= top && cy <= bottom) {
        foundTargetId = b.page.id
        break
      }
    }
    hoveredTargetBlockId.value = foundTargetId
  }

  async function onWindowMouseUp() {
    window.removeEventListener('mousemove', onWindowMouseMove)
    window.removeEventListener('mouseup', onWindowMouseUp)
    isDraggingConnection.value = false

    const targetId = hoveredTargetBlockId.value
    hoveredTargetBlockId.value = null

    if (!targetId) {
      // 没有拖到目标页面 → 静默取消，不弹窗
      currentDraggingAnchor.value = null
      return
    }

    // ✅ Figma 式：拖到目标页面直接静默保存，无需弹窗
    const savedAnchor = currentDraggingAnchor.value
    currentDraggingAnchor.value = null
    if (!savedAnchor) return

    try {
      const prevConn = connections.value.find((c) => c.elementId === savedAnchor.elementId)
      interactionUndoStack.value.push({
        type: 'connect',
        elementId: savedAnchor.elementId,
        pageId: savedAnchor.pageId,
        targetPageId: targetId,
        prevTargetPageId: prevConn?.toId || null,
        triggerType: 'click',
        actionType: 'navigate',
        params: JSON.stringify({ animation: 'push' }),
        label: savedAnchor.label,
        fromPageName: savedAnchor.pageName,
        toPageName: getPageName(targetId),
        prevToPageName: prevConn ? getPageName(prevConn.toId) : undefined,
      })
      interactionRedoStack.value = []

      await projectApi.saveInteraction(id, {
        elementId: savedAnchor.elementId,
        pageId: savedAnchor.pageId,
        targetPageId: targetId,
        triggerType: 'click',
        actionType: 'navigate',
        params: JSON.stringify({ animation: 'push' }),
      })
      await loadData()
      selectedNodeId.value = savedAnchor.pageId
      selectedConnId.value = `conn-${savedAnchor.elementId}-${targetId}`
      ElMessage.success(`已连线：${savedAnchor.label} ➔ ${getPageName(targetId)} (按 Ctrl+Z 可撤销)`)
    } catch (err: any) {
      ElMessage.error(err?.message || '保存连线失败')
    }
  }

  window.addEventListener('mousemove', onWindowMouseMove)
  window.addEventListener('mouseup', onWindowMouseUp)
}

/**
 * 从画板右侧手柄拉出 Figma 交互连线
 */
function startNodeConnectionDrag(event: MouseEvent, b: any) {
  event.preventDefault()
  event.stopPropagation()

  selectedNodeId.value = b.page.id
  focusPageId.value = b.page.id

  const el = b.page.elements?.find((e: any) => e.type === 'button' || e.type === 'icon') || b.page.elements?.[0]
  const pageW = b.page.canvas_width || 375
  const pageH = b.page.canvas_height || 812
  // showDesign always true on workbench canvas
  const wireX = pageW + 16
  const anchorX = b.x + wireX + pageW
  const anchorY = b.y + pageH / 2

  const anchor: AnchorItem = {
    id: `node-${b.page.id}`,
    pageId: b.page.id,
    pageName: b.page.name,
    elementId: el?.id || 0,
    label: el?.label || `${b.page.name} 画板`,
    x: anchorX,
    y: anchorY,
  }

  startConnectionDrag(event, anchor)
}

async function confirmCreateInteraction() {
  if (!currentDraggingAnchor.value || !interactionForm.value.targetPageId) {
    ElMessage.warning('请选择目标页面')
    return
  }
  isSavingInteraction.value = true
  try {
    const { elementId, pageId } = currentDraggingAnchor.value
    const targetPageId = interactionForm.value.targetPageId
    const triggerType = interactionForm.value.trigger
    const actionType = interactionForm.value.action
    const params = JSON.stringify({ animation: interactionForm.value.animation })

    await projectApi.saveInteraction(id, {
      elementId,
      pageId,
      targetPageId,
      triggerType,
      actionType,
      params,
    })

    await loadData()
    selectedNodeId.value = pageId

    ElMessage.success('交互连线创建成功并已持久化落库')
    showInteractionModal.value = false
    currentDraggingAnchor.value = null
  } catch (err: any) {
    ElMessage.error(err?.message || '保存交互连线失败')
  } finally {
    isSavingInteraction.value = false
  }
}

async function removeConnection(conn: any) {
  try {
    // 记录历史供 Ctrl+Z 撤销恢复
    interactionUndoStack.value.push({
      type: 'delete',
      elementId: conn.elementId,
      pageId: conn.fromId,
      targetPageId: conn.toId,
      triggerType: conn.trigger || 'click',
      actionType: conn.action || 'navigate',
      params: conn.params || JSON.stringify({ animation: 'push' }),
      label: conn.label || '组件',
      fromPageName: conn.fromPageName || '源页面',
      toPageName: conn.toPageName || '目标页面',
    })
    interactionRedoStack.value = []

    if (conn.interactionId) {
      await projectApi.deleteInteraction(id, conn.interactionId)
    } else {
      await projectApi.saveInteraction(id, {
        elementId: conn.elementId,
        pageId: conn.fromId,
        targetPageId: null,
      })
    }
    const sourcePage = pages.value.find((p) => p.id === conn.fromId || p.id === conn.fromPageId)
    if (sourcePage) {
      const el = sourcePage.elements.find((e) => e.id === conn.elementId)
      if (el && el.interaction) {
        delete el.interaction
      }
    }
    selectedConnId.value = null
    await loadData()
    ElMessage.success('交互连线已删除 (按 Ctrl+Z 可随时撤销恢复)')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除交互失败')
  }
}

let suppressBlockClick = false

function onBlockDragStart(e: MouseEvent, b: { page: Page; x: number; y: number }) {
  if (e.button !== 0) return
  if ((e.target as HTMLElement).closest('.ann-panel, .el-button, input, textarea, select')) return
  e.stopPropagation()
  isBlockDragging.value = true
  const startClientX = e.clientX
  const startClientY = e.clientY
  const startX = b.x
  const startY = b.y
  const pageId = b.page.id
  let moved = false
  let blockRafId: number | null = null
  let curNx = startX
  let curNy = startY

  const onMove = (ev: MouseEvent) => {
    if (!moved && Math.abs(ev.clientX - startClientX) < 4 && Math.abs(ev.clientY - startClientY) < 4) return
    moved = true
    const k = view.value.k || 1
    curNx = startX + (ev.clientX - startClientX) / k
    curNy = startY + (ev.clientY - startClientY) / k
    if (blockRafId === null) {
      blockRafId = requestAnimationFrame(() => {
        pageOverrides.value = { ...pageOverrides.value, [pageId]: { x: curNx, y: curNy } }
        blockRafId = null
      })
    }
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    if (blockRafId !== null) {
      cancelAnimationFrame(blockRafId)
      blockRafId = null
      pageOverrides.value = { ...pageOverrides.value, [pageId]: { x: curNx, y: curNy } }
    }
    isBlockDragging.value = false
    if (!moved) return
    suppressBlockClick = true
    setTimeout(() => (suppressBlockClick = false), 0)
    const ov = pageOverrides.value[pageId]
    if (ov) {
      projectApi.updatePagePosition(id, pageId, { canvasX: ov.x, canvasY: ov.y }).catch((e2: any) => {
        ElMessage.error(`位置保存失败: ${e2.message || '网络错误'}`)
      })
    }
  }
  window.addEventListener('mousemove', onMove, { passive: true })
  window.addEventListener('mouseup', onUp)
}

function onBlockClickCapture(e: MouseEvent) {
  if (suppressBlockClick) {
    e.stopPropagation()
    e.preventDefault()
  }
}

const contentStyle = computed(() => {
  const v = view.value
  return {
    transform: `translate3d(${v.x}px, ${v.y}px, 0) scale(${v.k})`,
  }
})

const focusPageId = ref<number | null>(null)

function viewportSize() {
  if (!viewportRef.value) return { w: 1200, h: 800 }
  const r = viewportRef.value.getBoundingClientRect()
  return { w: r.width, h: r.height }
}

function focusPage(pageId: number) {
  const b = blocks.value.find((bb) => bb.page.id === pageId)
  if (!b) return
  focusPageId.value = pageId
  selectedNodeId.value = pageId
  hoveredElementId.value = null
  hoveredAnnId.value = null
  selectedElementId.value = null
  const { w, h } = viewportSize()
  const v = view.value
  const targetX = w / 2 - (b.x + b.w / 2) * v.k
  const targetY = h / 2 - (b.y + b.h / 2) * v.k
  animating.value = true
  view.value = { ...v, x: targetX, y: targetY }
  if (animTimer) clearTimeout(animTimer)
  animTimer = setTimeout(() => (animating.value = false), 500)
}

let dragRafId: number | null = null
let pendingDragX = 0
let pendingDragY = 0

function onWindowMouseMove(e: MouseEvent) {
  if (!isDragging.value) return
  pendingDragX = dragOrigin.x + (e.clientX - dragStart.x)
  pendingDragY = dragOrigin.y + (e.clientY - dragStart.y)
  if (dragRafId === null) {
    dragRafId = requestAnimationFrame(() => {
      view.value = {
        ...view.value,
        x: pendingDragX,
        y: pendingDragY,
      }
      dragRafId = null
    })
  }
}

function onWindowMouseUp() {
  if (dragRafId !== null) {
    cancelAnimationFrame(dragRafId)
    dragRafId = null
    view.value = {
      ...view.value,
      x: pendingDragX,
      y: pendingDragY,
    }
  }
  isDragging.value = false
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  if ((e.target as HTMLElement).closest('.wf-element, .ann-box, .el-button, .el-checkbox, input, select, textarea, .block-label, .page-block, .ann-panel, .figma-bottom-toolbar')) return

  if (activeDrawTool.value === 'frame') {
    e.preventDefault()
    e.stopPropagation()
    const rect = viewportRef.value?.getBoundingClientRect()
    if (rect) {
      const logicX = Math.round((e.clientX - rect.left - view.value.x) / view.value.k)
      const logicY = Math.round((e.clientY - rect.top - view.value.y) / view.value.k)
      startFrameDraw(logicX, logicY)
    }
    return
  }

  selectedElementId.value = null
  selectedConnId.value = null
  // 点击空白区域 → 取消选中，所有连线恢复显示
  selectedNodeId.value = null
  isDragging.value = true
  dragStart = { x: e.clientX, y: e.clientY }
  dragOrigin = { x: view.value.x, y: view.value.y }
  pendingDragX = dragOrigin.x
  pendingDragY = dragOrigin.y

  window.addEventListener('mousemove', onWindowMouseMove, { passive: true })
  window.addEventListener('mouseup', onWindowMouseUp)
}

let wheelRafId: number | null = null
let accumulatedDeltaFactor = 1
let lastWheelX = 0
let lastWheelY = 0

function onWheel(e: WheelEvent) {
  const factor = Math.exp(-e.deltaY * 0.0012)
  accumulatedDeltaFactor *= factor
  lastWheelX = e.clientX
  lastWheelY = e.clientY
  if (wheelRafId === null) {
    wheelRafId = requestAnimationFrame(() => {
      zoomAt(lastWheelX, lastWheelY, accumulatedDeltaFactor)
      accumulatedDeltaFactor = 1
      wheelRafId = null
    })
  }
}

function zoomAtCenter(factor: number) {
  const { w, h } = viewportSize()
  zoomAt(viewportRef.value!.getBoundingClientRect().left + w / 2, viewportRef.value!.getBoundingClientRect().top + h / 2, factor)
}

function zoomAt(cx: number, cy: number, factor: number) {
  if (!viewportRef.value) return
  const rect = viewportRef.value.getBoundingClientRect()
  const px = cx - rect.left
  const py = cy - rect.top
  const v = view.value
  const newK = Math.min(3, Math.max(0.15, v.k * factor))
  view.value = {
    k: newK,
    x: px - ((px - v.x) * newK) / v.k,
    y: py - ((py - v.y) * newK) / v.k,
  }
}

function resetZoom100() {
  if (!viewportRef.value) return
  const { w, h } = viewportSize()
  zoomAt(viewportRef.value.getBoundingClientRect().left + w / 2, viewportRef.value.getBoundingClientRect().top + h / 2, 1 / view.value.k)
}

function fitAll() {
  if (!blocks.value.length || !viewportRef.value) return
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  blocks.value.forEach((b) => {
    minX = Math.min(minX, b.x)
    minY = Math.min(minY, b.y)
    maxX = Math.max(maxX, b.x + b.w)
    maxY = Math.max(maxY, b.y + b.h)
  })

  const totalW = maxX - minX
  const totalH = maxY - minY
  if (totalW <= 0 || totalH <= 0) return

  const { w, h } = viewportSize()
  const padX = 72
  const padY = 72
  const availW = Math.max(120, w - padX * 2)
  const availH = Math.max(120, h - padY * 2)

  const scale = Math.min(1.5, Math.max(0.15, Math.min(availW / totalW, availH / totalH)))
  const targetCenterX = minX + totalW / 2
  const targetCenterY = minY + totalH / 2

  animating.value = true
  view.value = {
    k: scale,
    x: w / 2 - targetCenterX * scale,
    y: h / 2 - targetCenterY * scale,
  }
  if (animTimer) clearTimeout(animTimer)
  animTimer = setTimeout(() => (animating.value = false), 450)
}

function hasNavigate(el: Element): boolean {
  return el.interaction?.action === 'navigate' && !!el.interaction?.target_page_id
}

function handleElementClick(el: Element) {
  const p = pages.value.find((pg) => pg.elements.some((e) => e.id === el.id))
  if (p) {
    selectedNodeId.value = p.id
    focusPageId.value = p.id
  }
  if (selectedElementId.value === el.id) {
    selectedElementId.value = null
    return
  }
  selectedElementId.value = el.id
  const ix = el.interaction
  if (!ix) return
  if (ix.action === 'navigate' && ix.target_page_id) {
    ElMessage.info(`跳转目标: ${targetPageName(ix)}（可在预览模式体验跳转）`)
  } else if (ix.action === 'modal') {
    ElMessage.info(`[${el.label || '弹窗'}] 打开弹窗`)
  } else if (ix.action === 'input_focus') {
    ElMessage.info(`[${el.label || '输入框'}] 聚焦输入`)
  }
}

function handleElementHover(el: Element | null, on: boolean) {
  hoveredElementId.value = on && el ? el.id : null
}

function handleAnnClick(annId: number) {
  const ann = pages.value
    .flatMap((p) => p.annotations)
    .find((a) => a.id === annId)
  if (ann) selectedElementId.value = ann.element_id
}

function targetPageName(ix: { target_page_id?: number | null }): string {
  if (!ix.target_page_id) return '—'
  return pages.value.find((p) => p.id === ix.target_page_id)?.name || `#${ix.target_page_id}`
}

function handleAnnOrderChange(pageId: number, order: number[]) {
  pageAnnOrders.value[pageId] = order
  saveAnnOrders()
  projectApi.updatePageAnnotationOrders(id, pageId, order).catch((e: any) => {
    console.error('Failed to sync annotation orders to server', e)
  })
  showToast('📌 画布说明顺序已更新并同步')
}

// 持久化自定义说明标题 (双向同步到画布与模拟器)
const customTitles = ref<Record<number, string>>({})

function loadCustomTitles() {
  try {
    const saved = localStorage.getItem('wf_sim_ann_titles')
    if (saved) {
      customTitles.value = JSON.parse(saved)
    }
  } catch (e) {
    console.error('Failed to load custom ann titles', e)
  }
}

function saveCustomTitles() {
  try {
    localStorage.setItem('wf_sim_ann_titles', JSON.stringify(customTitles.value))
  } catch (e) {
    console.error('Failed to save custom ann titles', e)
  }
}

loadCustomTitles()

function handleAnnSave(annId: number, text: string, title?: string) {
  if (!proto.value) return
  const ann = proto.value.pages.flatMap((p) => p.annotations).find((a) => a.id === annId)
  let el: Element | undefined
  if (ann) {
    const page = proto.value.pages.find((p) => p.annotations.some((x) => x.id === annId))
    if (page) {
      el = page.elements.find((e) => e.id === ann.element_id)
    }
  }

  if (title) {
    customTitles.value[annId] = title
    saveCustomTitles()
    if (el) el.label = title
  }

  projectApi
    .updateAnnotation(id, annId, { text, title })
    .then(() => {
      if (ann) ann.text = text
      showToast('✅ 说明已保存')
    })
    .catch((e: any) => {
      ElMessage.error(`保存失败: ${e.message || '网络错误'}`)
    })
}

const previewPage = computed<Page | null>(() => {
  return pages.value.find((p) => p.id === previewPageId.value) || null
})

// 预览手机壳高度：忠实呈现页面的设计稿画布高度，杜绝人为裁切导致底部内容丢失
const previewHeight = computed(() => {
  if (!previewPage.value) return 812
  return previewPage.value.canvas_height || 812
})

watch(previewPageId, () => {
  selectedSimAnnId.value = null
  clearSpotlightInIframe()
  nextTick(updatePhoneScale)
})

function openPreview() {
  if (!pages.value.length) return
  const active = focusPageId.value || pages.value[0].id
  previewPageId.value = active
  mode.value = 'preview'
}

function openPurePreview() {
  if (!pages.value.length) return
  const targetPage = previewPageId.value || focusPageId.value || pages.value[0].id
  router.push({
    name: 'PurePreview',
    params: { id },
    query: targetPage ? { page: String(targetPage) } : undefined,
  })
}

function handlePreviewCommand(cmd: string) {
  if (cmd === 'present' || cmd === 'pure') {
    openPurePreview()
  } else if (cmd === 'preview' || cmd === 'inpage') {
    showFloatingPreview.value = true
  }
}

function onFloatingNavigate(targetPageId: number) {
  focusPage(targetPageId)
}

function onLayerSelectElement(elementId: number) {
  selectedElementId.value = elementId
  if (currentFocusPage.value) {
    focusPage(currentFocusPage.value.id)
  }
}

function onLayerSelectFrame(pageId?: number) {
  selectedElementId.value = null
  if (pageId) focusPage(pageId)
}

const activeSelectedElementInfo = ref<any>(null)

function onElementSelected(pageId: number, info: any) {
  focusPageId.value = pageId
  activeSelectedElementInfo.value = info
}

function onElementDeselected(pageId: number) {
  if (focusPageId.value === pageId) {
    activeSelectedElementInfo.value = null
  }
}

const selectedElementObj = computed<Element | null>(() => {
  if (!selectedElementId.value || !currentFocusPage.value?.elements) return null
  return currentFocusPage.value.elements.find((e) => e.id === selectedElementId.value) || null
})

function onInspectorAlign(alignType: string) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.alignSelectedElement?.(alignType)
  }
}

function onInspectorUpdateRadius(radius: number) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateElementRadius?.(radius)
  }
}

function onInspectorUpdateStroke(stroke: { width: number; color: string; style: string }) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateElementStroke?.(stroke)
  }
}

function onInspectorUpdateShadow(shadow: string) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateElementShadow?.(shadow)
  }
}

function onInspectorUpdateDimension(payload: { key: 'width' | 'height'; val: number }) {
  if (selectedElementObj.value) {
    selectedElementObj.value[payload.key] = payload.val
  } else if (currentFocusPage.value) {
    if (payload.key === 'width') currentFocusPage.value.canvas_width = payload.val
    else currentFocusPage.value.canvas_height = payload.val
  }
}

function onInspectorUpdatePosition(payload: { key: 'x' | 'y'; val: number }) {
  if (selectedElementObj.value) {
    selectedElementObj.value[payload.key] = payload.val
  }
}

function onInspectorUpdateColor(color: string) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateElementColor?.(color)
  }
  if (selectedElementObj.value) {
    try {
      const styleObj = selectedElementObj.value.style ? JSON.parse(selectedElementObj.value.style) : {}
      styleObj.fill = color
      selectedElementObj.value.style = JSON.stringify(styleObj)
    } catch {
      selectedElementObj.value.style = JSON.stringify({ fill: color })
    }
  }
}

function onInspectorUpdateFontSize(delta: number) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateElementFontSize?.(delta)
  }
}

function onInspectorDuplicate() {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.duplicateSelectedElement?.()
  }
}

function onInspectorDelete() {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.deleteSelectedElement?.()
  }
  if (selectedElementObj.value && currentFocusPage.value) {
    const elId = selectedElementObj.value.id
    currentFocusPage.value.elements = currentFocusPage.value.elements.filter((e) => e.id !== elId)
    selectedElementId.value = null
  }
  ElMessage.success('已删除选定元素')
}

function onInspectorReplaceAsset() {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.openAssetPickerForSelected?.()
  }
}

function onInspectorStartTextEdit() {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.startTextEdit?.()
  }
}

function onInspectorUpdateText(text: string) {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.updateText?.(text)
  }
  if (activeSelectedElementInfo.value) {
    activeSelectedElementInfo.value.textContent = text
  }
}

function onInspectorSelectParent() {
  if (currentFocusPage.value) {
    pageRefs.value[currentFocusPage.value.id]?.selectParentContainer?.()
  }
}

const simScrollRef = ref<HTMLElement | null>(null)
const showSimDrawer = ref(true)
const selectedSimAnnId = ref<number | null>(null)

// 按页面持久化记录业务说明卡片的排序偏好
const pageAnnOrders = ref<Record<number, number[]>>({})

function loadAnnOrders() {
  try {
    const saved = localStorage.getItem('wf_sim_ann_orders')
    if (saved) {
      pageAnnOrders.value = JSON.parse(saved)
    }
  } catch (e) {
    console.error('Failed to load custom ann orders', e)
  }
}

function saveAnnOrders() {
  try {
    localStorage.setItem('wf_sim_ann_orders', JSON.stringify(pageAnnOrders.value))
  } catch (e) {
    console.error('Failed to save custom ann orders', e)
  }
}

loadAnnOrders()

// 模拟器抽屉卡片就地编辑状态与方法
const editingSimAnnId = ref<number | null>(null)
const editSimTitle = ref('')
const editSimText = ref('')
const simTitleInputRef = ref<HTMLInputElement | null>(null)

function startSimEdit(item: SimAnnItem) {
  editingSimAnnId.value = item.id
  editSimTitle.value = item.title
  editSimText.value = item.text || ''
  nextTick(() => {
    simTitleInputRef.value?.focus()
    simTitleInputRef.value?.select()
  })
}

function cancelSimEdit() {
  editingSimAnnId.value = null
}

function saveSimEdit(item: SimAnnItem) {
  if (editingSimAnnId.value !== item.id) return
  const t = editSimTitle.value.trim() || item.title
  const txt = editSimText.value.trim()
  editingSimAnnId.value = null
  handleAnnSave(item.id, txt, t)
}

const draggingAnnId = ref<number | null>(null)
const dragOverAnnId = ref<number | null>(null)

function onSimDragStart(e: DragEvent, annId: number) {
  draggingAnnId.value = annId
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(annId))
  }
}

function onSimDragOver(e: DragEvent, targetId: number) {
  if (draggingAnnId.value === targetId) return
  dragOverAnnId.value = targetId
}

function onSimDragLeave(e: DragEvent, targetId: number) {
  if (dragOverAnnId.value === targetId) {
    dragOverAnnId.value = null
  }
}

function onSimDrop(e: DragEvent, targetId: number) {
  const sourceId = draggingAnnId.value
  if (!sourceId || sourceId === targetId || !previewPage.value) {
    dragOverAnnId.value = null
    draggingAnnId.value = null
    return
  }

  const currentList = [...simAnnList.value]
  const srcIdx = currentList.findIndex((a) => a.id === sourceId)
  const tgtIdx = currentList.findIndex((a) => a.id === targetId)

  if (srcIdx !== -1 && tgtIdx !== -1) {
    const [moved] = currentList.splice(srcIdx, 1)
    currentList.splice(tgtIdx, 0, moved)

    const pageId = previewPage.value.id
    const newOrder = currentList.map((a) => a.id)
    pageAnnOrders.value[pageId] = newOrder
    saveAnnOrders()
    projectApi.updatePageAnnotationOrders(id, pageId, newOrder).catch((e: any) => {
      console.error('Failed to sync annotation orders to server', e)
    })
    showToast('📌 已置顶/调整该业务说明')
  }

  dragOverAnnId.value = null
  draggingAnnId.value = null
}

function onSimDragEnd() {
  dragOverAnnId.value = null
  draggingAnnId.value = null
}

interface SimAnnItem {
  id: number
  index: number
  elementId: number | null
  title: string
  text: string
  interactionType?: 'navigate' | 'modal' | 'toggle' | null
  interactionLabel?: string | null
  interactionTarget?: string | null
  targetPageId?: number | null
}

const simAnnList = computed<SimAnnItem[]>(() => {
  if (!previewPage.value) return []
  const p = previewPage.value
  const rawList = (p.annotations || []).map((ann, idx) => {
    const el = p.elements.find((e) => e.id === ann.element_id)
    let interactionType: 'navigate' | 'modal' | 'toggle' | null = null
    let interactionLabel: string | null = null
    let interactionTarget: string | null = null
    let targetPageId: number | null = null

    if (el) {
      if (el.interaction) {
        if (el.interaction.action === 'navigate') {
          interactionType = 'navigate'
          interactionLabel = '页面跳转'
          targetPageId = el.interaction.target_page_id ?? null
          if (targetPageId) {
            const tp = pages.value.find((x) => x.id === targetPageId)
            if (tp) interactionTarget = tp.name
          }
          if (!interactionTarget && el.interaction.params) {
            try {
              const parsed = JSON.parse(el.interaction.params)
              interactionTarget = parsed.target_name || null
            } catch {
              interactionTarget = el.interaction.params
            }
          }
        } else if (el.interaction.action === 'modal') {
          interactionType = 'modal'
          interactionLabel = '唤起弹窗'
          interactionTarget = el.interaction.params || '业务弹窗'
        }
      } else if (el.type === 'switch' || el.type === 'checkbox') {
        interactionType = 'toggle'
        interactionLabel = el.type === 'switch' ? '开关状态' : '勾选状态'
      }
    }

    const customTitle = customTitles.value[ann.id]
    const effectiveTitle = customTitle || el?.label || `说明 ${idx + 1}`

    return {
      id: ann.id,
      index: idx,
      elementId: el?.id ?? null,
      title: effectiveTitle,
      text: ann.text || '',
      interactionType,
      interactionLabel,
      interactionTarget,
      targetPageId,
    }
  })

  // 按用户调整后的顺序排序
  const customOrder = pageAnnOrders.value[p.id]
  if (customOrder && customOrder.length) {
    return [...rawList].sort((a, b) => {
      const idxA = customOrder.indexOf(a.id)
      const idxB = customOrder.indexOf(b.id)
      if (idxA !== -1 && idxB !== -1) return idxA - idxB
      if (idxA !== -1) return -1
      if (idxB !== -1) return 1
      return 0
    })
  }

  return rawList
})

const activeSpotlightRect = ref<{ x: number; y: number; width: number; height: number } | null>(null)

const activeSimAnn = computed(() => simAnnList.value.find((a) => a.id === selectedSimAnnId.value))
const activeSimElement = computed(() => {
  if (!previewPage.value || !activeSimAnn.value?.elementId) return null
  return previewPage.value.elements.find((e) => e.id === activeSimAnn.value!.elementId) || null
})
const activeSimAnnTitle = computed(() => activeSimAnn.value?.title || '')

const displaySpotlightRect = computed(() => {
  if (!selectedSimAnnId.value || !activeSimAnn.value) return null
  if (activeSpotlightRect.value) return activeSpotlightRect.value
  if (activeSimElement.value) {
    return {
      x: activeSimElement.value.x,
      y: activeSimElement.value.y,
      width: activeSimElement.value.width,
      height: activeSimElement.value.height,
    }
  }
  return null
})

function scrollToSimulatorY(y: number, height: number) {
  if (!simScrollRef.value) return
  nextTick(() => {
    if (!simScrollRef.value) return
    const targetTop = Math.max(0, y - previewHeight.value / 2 + height / 2)
    simScrollRef.value.scrollTo({
      top: targetTop,
      behavior: 'smooth',
    })
  })
}

function scrollToElementInSimulator(el: Element | null) {
  if (!el) return
  scrollToSimulatorY(el.y, el.height)
}

function sendSpotlightToIframe(item: SimAnnItem, targetEl: Element | null) {
  const iframes = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
  const payload = {
    type: 'wf-spotlight',
    active: true,
    pageId: previewPage.value?.id || null,
    elementId: item.elementId,
    elementType: targetEl?.type || null,
    label: targetEl?.label || item.title,
    targetPageName: item.interactionTarget || null,
    x: targetEl?.x ?? null,
    y: targetEl?.y ?? null,
    w: targetEl?.width ?? null,
    h: targetEl?.height ?? null,
  }
  iframes.forEach((ifr) => {
    try {
      ifr.contentWindow?.postMessage(payload, '*')
    } catch {}
  })
}

function clearSpotlightInIframe() {
  selectedSimAnnId.value = null
  activeSpotlightRect.value = null
  const iframes = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
  iframes.forEach((ifr) => {
    try {
      ifr.contentWindow?.postMessage({ type: 'wf-spotlight', active: false }, '*')
    } catch {}
  })
}

function onSimMessage(ev: MessageEvent) {
  if (!ev.data) return
  if (ev.data.type === 'wf-spotlight-rect' && ev.data.rect) {
    if (!selectedSimAnnId.value || !activeSimAnn.value) return
    if (ev.data.pageId && ev.data.pageId !== previewPageId.value) return
    activeSpotlightRect.value = ev.data.rect
    scrollToSimulatorY(ev.data.rect.y, ev.data.rect.height)
  }
  if (ev.data.type === 'wf-element-copied' && ev.data.data) {
    ;(window as any).__wfCopiedElement = ev.data.data
    ;(window as any).__wfLastCopyType = 'element'
  }
}

onMounted(() => {
  window.addEventListener('message', onSimMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', onSimMessage)
})

const simInteractiveCount = computed(() => simAnnList.value.filter((a) => !!a.interactionType).length)

function onSimCardClick(item: SimAnnItem) {
  if (selectedSimAnnId.value === item.id) {
    clearSpotlightInIframe()
    return
  }

  selectedSimAnnId.value = item.id
  activeSpotlightRect.value = null
  const targetEl = previewPage.value?.elements.find((e) => e.id === item.elementId) || null
  if (targetEl) {
    scrollToElementInSimulator(targetEl)
  }
  sendSpotlightToIframe(item, targetEl)

  if (item.interactionType === 'navigate') {
    showToast(`🎯 已定位: ${item.title}${item.interactionTarget ? ` (去向: ${item.interactionTarget})` : ''}`)
  } else if (item.interactionType === 'modal') {
    showToast(`🎯 已定位弹窗组件: ${item.title}`)
  } else if (item.interactionType === 'toggle') {
    showToast(`🎯 已定位开关组件: ${item.title}`)
  } else {
    showToast(`📌 组件说明: ${item.title}`)
  }
}

function handlePreviewClick(el: Element) {
  // 联动高亮对应的说明卡片并滚动到可视区域
  const matchedAnn = simAnnList.value.find((a) => a.elementId === el.id)
  if (matchedAnn) {
    selectedSimAnnId.value = matchedAnn.id
    activeSpotlightRect.value = null
    sendSpotlightToIframe(matchedAnn, el)
    nextTick(() => {
      const cardEl = document.getElementById(`sim-ann-${matchedAnn.id}`)
      if (cardEl) {
        cardEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
      }
    })
  }
  scrollToElementInSimulator(el)
  const ix = el.interaction
  if (!ix) return
  if (ix.action === 'navigate' && ix.target_page_id) {
    showToast(`🔗 跳转到: ${targetPageName(ix)}`)
    slideClass.value = ''
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        slideClass.value = 'slide-in-right'
        previewPageId.value = ix.target_page_id!
      })
    })
  } else if (ix.action === 'modal') {
    showToast(`💬 弹窗: ${el.label || '弹窗'}`)
  } else if (ix.action === 'input_focus') {
    showToast(`⌨️ 输入框获得焦点`)
  }
}

function showToast(msg: string) {
  toastMsg.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMsg.value = ''
  }, 2000)
}

const totalElements = computed(() => pages.value.reduce((s, p) => s + p.elements.length, 0))
const totalInteractions = computed(() => pages.value.reduce((s, p) => s + p.elements.filter((e) => e.interaction).length, 0))
const totalAnnotations = computed(() => pages.value.reduce((s, p) => s + p.annotations.length, 0))

const activeEditingPageId = computed<number | null>(() => {
  if (mode.value === 'edit' && fineTune.value) {
    const candidate = focusPageId.value ?? (blocks.value[0]?.page?.id ?? null)
    if (candidate && pageEditingConflicts.value[candidate]?.conflict) {
      // 若该候选页面正被他人独占锁定，则当前用户不持有该页面锁
      return null
    }
    return candidate
  }
  if (editingSimAnnId.value && previewPageId.value) {
    return previewPageId.value
  }
  return null
})

const activeEditingPage = computed(() => {
  if (!activeEditingPageId.value) return null
  return pages.value.find((p) => p.id === activeEditingPageId.value) || null
})

let editStatusTimer: ReturnType<typeof setInterval> | null = null
let lastLockedPageId: number | null = null

async function syncEditingSession() {
  // 1. 批量拉取所有页面的编辑/冲突状态
  try {
    const statuses = await projectApi.getAllPageEditingStatuses(id, clientId.value)
    pageEditingConflicts.value = statuses || {}
  } catch {}

  // 2. 如果当前有合法的待锁定页面，上报独占心跳锁
  const currentEditId = activeEditingPageId.value
  if (currentEditId) {
    try {
      const lockRes = await projectApi.lockPageEditing(id, currentEditId, {
        clientId: clientId.value,
        userName: '协同成员',
        active: true,
      })
      lastLockedPageId = currentEditId
      if (lockRes.conflict) {
        const p = pages.value.find((pg) => pg.id === currentEditId)
        showToast(`🔒「${p?.name || '本页'}」已被 ${lockRes.editor || '其他成员'} 独占微调，已为您开启只读保护`)
      }
    } catch {}
  } else if (lastLockedPageId) {
    try {
      await projectApi.lockPageEditing(id, lastLockedPageId, {
        clientId: clientId.value,
        active: false,
      })
    } catch {}
    lastLockedPageId = null
  }
}

watch(activeEditingPageId, (newId, oldId) => {
  if (oldId && oldId !== newId) {
    projectApi.lockPageEditing(id, oldId, { clientId: clientId.value, active: false }).catch(() => {})
  }
  if (newId) {
    syncEditingSession()
  }
})

onMounted(async () => {
  window.addEventListener('resize', updatePhoneScale)
  window.addEventListener('wf-component-dragstart', () => {
    isDraggingComponent.value = true
  })
  window.addEventListener('wf-component-dragend', () => {
    isDraggingComponent.value = false
    hoveredDropBlockId.value = null
  })
  window.addEventListener('dragover', (e) => {
    if ((window as any).__wfDraggingComponent || isDraggingComponent.value) {
      e.preventDefault()
      if (e.dataTransfer) {
        e.dataTransfer.dropEffect = 'copy'
      }
      isDraggingComponent.value = true
    }
  })
  window.addEventListener('dragend', () => {
    isDraggingComponent.value = false
    hoveredDropBlockId.value = null
  })
  window.addEventListener('drop', () => {
    isDraggingComponent.value = false
    hoveredDropBlockId.value = null
  })
  editStatusTimer = setInterval(syncEditingSession, 3500)
  syncEditingSession()
  await loadData()
})

const pageReady = computed(() => {
  if (!pages.value.length) return false
  return pages.value.every((p) => !!pageRefs.value[p.id])
})
let didInitialFocus = false
watch(
  pageReady,
  (ready) => {
    if (ready && !didInitialFocus) {
      didInitialFocus = true
      nextTick(() => {
        const qPage = route.query.page ? Number(route.query.page) : null
        const targetBlock = (qPage ? blocks.value.find((b) => b.page.id === qPage) : null) || blocks.value[0]
        if (!targetBlock) return
        focusPageId.value = targetBlock.page.id
        if (qPage) {
          previewPageId.value = qPage
        }
        const { w, h } = viewportSize()
        view.value = { x: w / 2 - (targetBlock.x + targetBlock.w / 2), y: h / 2 - (targetBlock.y + targetBlock.h / 2), k: 1 }
      })
    }
  },
  { immediate: true },
)

watch(
  () => route.query.page,
  (newPageId) => {
    if (newPageId != null) {
      const pid = Number(newPageId)
      if (!isNaN(pid) && pages.value.some((p) => p.id === pid)) {
        focusPage(pid)
        if (mode.value === 'preview') {
          previewPageId.value = pid
        }
      }
    }
  },
)

onBeforeUnmount(() => {
  if (editStatusTimer) clearInterval(editStatusTimer)
  if (lastLockedPageId) {
    projectApi.lockPageEditing(id, lastLockedPageId, { clientId: clientId.value, active: false }).catch(() => {})
  }
  if (animTimer) clearTimeout(animTimer)
  if (dragRafId !== null) cancelAnimationFrame(dragRafId)
  if (wheelRafId !== null) cancelAnimationFrame(wheelRafId)
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
  window.removeEventListener('resize', updatePhoneScale)
})

watch(
  mode,
  (m) => {
    if (m === 'preview') nextTick(updatePhoneScale)
  },
)

function onGlobalKeydown(e: KeyboardEvent) {
  // 1. ESC key: exits draw mode, or cancels line selection, or exits preview
  if (e.key === 'Escape') {
    if (activeDrawTool.value !== 'select' || activeShapeDraw.value || activeFrameDraw.value) {
      e.preventDefault()
      activeShapeDraw.value = null
      activeFrameDraw.value = null
      activeDrawTool.value = 'select'
      showToast('已取消绘制模式')
      return
    }
    if (selectedConnId.value) {
      selectedConnId.value = null
      return
    }
    if (mode.value === 'preview') {
      mode.value = 'edit'
      return
    }
  }

  // 2. 绘制工具快捷键监听 (V: 指针选择, R: 矩形方框, T: 文本落字, O: 圆形头像)
  const activeTag = (document.activeElement?.tagName || '').toLowerCase()
  const isInput = activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable
  if (!isInput && !e.ctrlKey && !e.metaKey && !e.altKey && mode.value !== 'preview') {
    const key = e.key.toUpperCase()
    if (key === 'V') {
      e.preventDefault()
      activeDrawTool.value = 'select'
      showToast('已切换至指针选择工具 (V)')
      return
    } else if (key === 'R') {
      e.preventDefault()
      activeDrawTool.value = 'rect'
      showToast('已切换至矩形绘制工具 (R)：点击画板任意位置放置')
      return
    } else if (key === 'T') {
      e.preventDefault()
      activeDrawTool.value = 'text'
      showToast('已切换至文本落字工具 (T)：点击画板任意位置放置')
      return
    } else if (key === 'F') {
      e.preventDefault()
      activeDrawTool.value = 'frame'
      showToast('已切换至画板框架工具 (F)：点击画布任意位置放置新画板')
      return
    } else if (key === 'L') {
      e.preventDefault()
      activeDrawTool.value = 'line'
      showToast('已切换至直线工具 (L)：点击画板任意位置放置')
      return
    } else if (key === 'O') {
      e.preventDefault()
      activeDrawTool.value = 'circle'
      showToast('已切换至圆形绘制工具 (O)：点击画板任意位置放置')
      return
    }
  }

  // 3. Figma Prototype 连线删除快捷键 (Backspace / Delete)
  if ((e.key === 'Backspace' || e.key === 'Delete') && selectedConnId.value) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }
    e.preventDefault()
    const targetConn = connections.value.find((c) => c.id === selectedConnId.value)
    if (targetConn) {
      removeConnection(targetConn)
    }
    return
  }

  // 4. 画板整体删除快捷键 (Backspace / Delete)
  if ((e.key === 'Backspace' || e.key === 'Delete') && selectedNodeId.value && !selectedElementId.value) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }
    const targetPage = pages.value.find((p) => p.id === selectedNodeId.value)
    if (targetPage) {
      e.preventDefault()
      confirmDeletePage(targetPage)
      return
    }
  }

  // 2. Canvas zoom shortcuts
  if ((e.ctrlKey || e.metaKey) && e.key === '0') {
    e.preventDefault()
    resetZoom100()
    return
  }
  if (e.shiftKey && (e.key === '1' || e.key === '!')) {
    e.preventDefault()
    fitAll()
    return
  }
  if (e.shiftKey && (e.key === '2' || e.key === '@')) {
    e.preventDefault()
    focusPage(focusPageId.value ?? (blocks.value[0]?.page.id ?? 0))
    return
  }

  // 3. 全局连线撤销与重做快捷键 (Ctrl+Z / Ctrl+Y / Cmd+Z / Cmd+Shift+Z)
  const isZ = e.key === 'z' || e.key === 'Z'
  const isY = e.key === 'y' || e.key === 'Y'
  if ((e.ctrlKey || e.metaKey) && (isZ || isY)) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }

    if (isZ && !e.shiftKey) {
      if (interactionUndoStack.value.length > 0) {
        e.preventDefault()
        undoInteraction()
        return
      }
    } else if (isY || (isZ && e.shiftKey)) {
      if (interactionRedoStack.value.length > 0) {
        e.preventDefault()
        redoInteraction()
        return
      }
    }
  }

  // 4. Fine tune undo/redo in canvas edit mode
  if (!fineTune.value || mode.value === 'preview') return
  if ((e.ctrlKey || e.metaKey) && isZ && !e.shiftKey) {
    e.preventDefault()
    const frames = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
    frames.forEach((f) => f.contentWindow?.postMessage({ type: 'wf-undo' }, '*'))
  } else if ((e.ctrlKey || e.metaKey) && (isY || (isZ && e.shiftKey))) {
    e.preventDefault()
    const frames = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
    frames.forEach((f) => f.contentWindow?.postMessage({ type: 'wf-redo' }, '*'))
  }

  // 5. 复制快捷键 (Ctrl+C / Cmd+C)
  const isC = e.key === 'c' || e.key === 'C'
  const isV = e.key === 'v' || e.key === 'V'
  const isD = e.key === 'd' || e.key === 'D'

  if ((e.ctrlKey || e.metaKey) && isC && !e.shiftKey) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }

    // A. 选中了画板整体
    if (selectedNodeId.value && !selectedElementId.value) {
      const targetPage = pages.value.find((p) => p.id === selectedNodeId.value)
      if (targetPage) {
        const pageData = {
          name: targetPage.name,
          width: targetPage.canvas_width || 375,
          height: targetPage.canvas_height || 812,
          htmlContent: targetPage.html_content || '',
        }
        ;(window as any).__wfCopiedPage = pageData
        ;(window as any).__wfLastCopyType = 'page'
        try {
          localStorage.setItem('wf_clipboard_page', JSON.stringify(pageData))
          localStorage.setItem('wf_last_copy_type', 'page')
        } catch {}
        showToast(`📋 已复制画板「${targetPage.name}」(按 Ctrl+V 粘贴新画板)`)
        e.preventDefault()
        return
      }
    }

    // B. 选中了具体连线组件
    if (selectedElementId.value) {
      const allEls = pages.value.flatMap((p) => p.elements || [])
      const foundEl = allEls.find((el) => el.id === selectedElementId.value)
      if (foundEl) {
        const snippet = createHtmlSnippetForElement(foundEl)
        const data = {
          html: snippet,
          name: foundEl.label || foundEl.type,
          left: foundEl.x,
          top: foundEl.y,
          width: foundEl.width,
          height: foundEl.height,
        }
        ;(window as any).__wfCopiedElement = data
        ;(window as any).__wfLastCopyType = 'element'
        try {
          localStorage.setItem('wf_clipboard_element', JSON.stringify(data))
          localStorage.setItem('wf_last_copy_type', 'element')
        } catch {}
        showToast(`📋 已复制组件「${data.name}」(按 Ctrl+V 粘贴)`)
        e.preventDefault()
        return
      }
    }

    // C. 转发给当前聚焦画板 iframe
    const targetId = focusPageId.value || selectedNodeId.value
    if (targetId && pageRefs.value[targetId]) {
      pageRefs.value[targetId]?.copySelectedElement?.()
    }
  }

  // 6. 粘贴快捷键 (Ctrl+V / Cmd+V)
  if ((e.ctrlKey || e.metaKey) && isV && !e.shiftKey) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }

    const lastCopyType = (window as any).__wfLastCopyType || localStorage.getItem('wf_last_copy_type')

    // A. 粘贴画板
    if (lastCopyType === 'page') {
      const cp = (window as any).__wfCopiedPage || JSON.parse(localStorage.getItem('wf_clipboard_page') || 'null')
      if (cp) {
        e.preventDefault()
        const rightmost = blocks.value.reduce((max, b) => Math.max(max, b.x + b.w), 0)
        const posX = rightmost > 0 ? rightmost + 120 : 56
        const posY = blocks.value[0]?.y || 64
        doCreatePage({
          name: `${cp.name} 副本`,
          width: cp.width,
          height: cp.height,
          x: posX,
          y: posY,
          htmlContent: cp.htmlContent,
        })
        return
      }
    }

    // B. 粘贴组件/元素
    const copied = (window as any).__wfCopiedElement || JSON.parse(localStorage.getItem('wf_clipboard_element') || 'null')
    if (copied && copied.html) {
      e.preventDefault()
      const targetId = focusPageId.value || selectedNodeId.value || pages.value[0]?.id
      if (targetId) {
        const inst = pageRefs.value[targetId]
        if (inst && typeof inst.pasteCopiedElement === 'function') {
          inst.pasteCopiedElement()
        } else {
          insertComponentIntoPage(
            targetId,
            { name: copied.name || '复制的组件', html: copied.html },
            (copied.left || 20) + 16,
            (copied.top || 120) + 16
          )
        }
        return
      }
    }
  }

  // 7. 快速克隆副本快捷键 (Ctrl+D / Cmd+D)
  if ((e.ctrlKey || e.metaKey) && isD && !e.shiftKey) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag === 'input' || activeTag === 'textarea' || (document.activeElement as HTMLElement)?.isContentEditable) {
      return
    }

    // A. 克隆画板
    if (selectedNodeId.value && !selectedElementId.value) {
      const targetPage = pages.value.find((p) => p.id === selectedNodeId.value)
      if (targetPage) {
        e.preventDefault()
        const rightmost = blocks.value.reduce((max, b) => Math.max(max, b.x + b.w), 0)
        const posX = rightmost > 0 ? rightmost + 120 : 56
        const posY = blocks.value[0]?.y || 64
        doCreatePage({
          name: `${targetPage.name} 副本`,
          width: targetPage.canvas_width || 375,
          height: targetPage.canvas_height || 812,
          x: posX,
          y: posY,
          htmlContent: targetPage.html_content || '',
        })
        return
      }
    }

    // B. 克隆组件
    const targetId = focusPageId.value || selectedNodeId.value
    if (targetId && pageRefs.value[targetId]) {
      e.preventDefault()
      pageRefs.value[targetId]?.duplicateSelectedElement?.()
      return
    }
  }

  // 8. 评论模式快捷键 (C 键，单键)
  if (!e.ctrlKey && !e.metaKey && !e.altKey && (e.key === 'c' || e.key === 'C')) {
    const activeTag = (document.activeElement?.tagName || '').toLowerCase()
    if (activeTag !== 'input' && activeTag !== 'textarea' && !(document.activeElement as HTMLElement)?.isContentEditable) {
      activeDrawTool.value = activeDrawTool.value === 'comment' ? 'select' : 'comment'
      return
    }
  }

  // 9. 取消评论草稿 / 退出评论模式 (Escape 键)
  if (e.key === 'Escape') {
    if (draftComment.value) {
      draftComment.value = null
      return
    }
    if (selectedThreadId.value) {
      selectedThreadId.value = null
      return
    }
    if (activeDrawTool.value === 'comment') {
      activeDrawTool.value = 'select'
      return
    }
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onUnmounted(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>

<style scoped lang="scss">
/* Canvas Viewport Dot Grid Background */
.canvas-viewport {
  background-color: #f8fafc;
  background-image: radial-gradient(#cbd5e1 1.2px, transparent 1.2px);
  background-size: 24px 24px;
  background-position: center;
}

.canvas-content {
  transform-origin: 0 0;
  will-change: transform;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;

  &.is-animating {
    transition: transform 0.45s cubic-bezier(0.25, 0.8, 0.35, 1);
  }

  &.is-dragging {
    pointer-events: none;

    * {
      pointer-events: none !important;
      user-select: none !important;
    }
  }
}

.page-block {
  transform-origin: 0 0;
}

.slide-in-right {
  animation: slideInRight 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-in-left {
  animation: slideInLeft 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideInRight {
  from {
    transform: translateX(40px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes slideInLeft {
  from {
    transform: translateX(-40px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: all 0.28s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

.custom-scrollbar {
  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.15);
    border-radius: 999px;
    &:hover {
      background: rgba(255, 255, 255, 0.25);
    }
  }
}

/* 业务说明联动：模拟器组件呼吸发光外圈 */
.sim-breathing-ring {
  border-color: #10b981;
  animation: simBreathing 1.8s ease-in-out infinite;
}

@keyframes simBreathing {
  0% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.75), 0 0 12px rgba(16, 185, 129, 0.45);
    border-color: rgba(52, 211, 153, 0.9);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0), 0 0 24px rgba(16, 185, 129, 0.85);
    border-color: #10b981;
  }
  100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.75), 0 0 12px rgba(16, 185, 129, 0.45);
    border-color: rgba(52, 211, 153, 0.9);
  }
}

.fade-fast-enter-active,
.fade-fast-leave-active {
  transition: opacity 0.22s cubic-bezier(0.16, 1, 0.3, 1), transform 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

.fade-fast-enter-from,
.fade-fast-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

@keyframes pulseSlow {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 0 10px rgba(37,99,235,0.85), 0 0 0 2px #ffffff;
  }
  50% {
    transform: scale(1.15);
    box-shadow: 0 0 20px rgba(59,130,246,1), 0 0 0 3px #93c5fd;
  }
}
.animate-pulse-slow {
  animation: pulseSlow 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>