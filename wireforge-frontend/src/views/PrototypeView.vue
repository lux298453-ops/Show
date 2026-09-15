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

      <!-- Center: Feature Toggles (Segmented Controls) -->
      <div class="flex items-center gap-1.5 bg-slate-100/90 p-1 rounded-xl border border-slate-200/70 shadow-inner">
        <label
          class="wf-tap flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold cursor-pointer transition-all"
          :class="showAnnotations ? 'bg-white text-emerald-700 shadow-2xs border border-slate-200/60' : 'text-slate-500 hover:text-slate-800'"
        >
          <input type="checkbox" v-model="showAnnotations" class="hidden" />
          <Tag class="w-3.5 h-3.5" />
          <span>标注面板</span>
        </label>

        <label
          class="wf-tap flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold cursor-pointer transition-all"
          :class="[
            fineTune ? 'bg-white text-emerald-700 shadow-2xs border border-slate-200/60' : 'text-slate-500 hover:text-slate-800',
            mode === 'preview' ? 'opacity-40 pointer-events-none' : ''
          ]"
        >
          <input type="checkbox" v-model="fineTune" :disabled="mode === 'preview'" class="hidden" />
          <SlidersHorizontal class="w-3.5 h-3.5" />
          <span>元素微调</span>
        </label>
      </div>

      <!-- Right: Zoom Controls & Preview CTA -->
      <div class="flex items-center gap-3">
        <!-- Canvas Zoom Control Capsule -->
        <div class="flex items-center bg-white/80 border border-slate-200/80 rounded-xl overflow-hidden p-0.5 text-slate-600 shadow-2xs">
          <button
            class="wf-tap p-1.5 hover:bg-slate-100 hover:text-slate-900 rounded-lg transition-colors cursor-pointer"
            title="缩小 (Ctrl + 滚轮)"
            @click="zoomAtCenter(0.85)"
          >
            <Minus class="w-3.5 h-3.5" />
          </button>
          <button
            class="wf-tap px-1.5 py-1 text-center text-xs font-bold tabular-nums text-slate-700 hover:text-emerald-700 hover:bg-slate-100 rounded-md select-none transition-colors cursor-pointer"
            title="点击还原 100% 原始比例 (Ctrl+0)"
            @click="resetZoom100"
          >
            {{ Math.round(view.k * 100) }}%
          </button>
          <button
            class="wf-tap p-1.5 hover:bg-slate-100 hover:text-slate-900 rounded-lg transition-colors cursor-pointer"
            title="放大 (Ctrl + 滚轮)"
            @click="zoomAtCenter(1.18)"
          >
            <Plus class="w-3.5 h-3.5" />
          </button>
          <div class="h-3 w-[1px] bg-slate-200 my-auto mx-0.5"></div>
          <button
            class="wf-tap p-1.5 hover:bg-slate-100 hover:text-slate-900 rounded-lg transition-colors cursor-pointer"
            title="聚焦当前选中画框 (Shift+2)"
            @click="focusPage(focusPageId ?? (blocks[0]?.page.id ?? 0))"
          >
            <Crosshair class="w-3.5 h-3.5" />
          </button>
          <button
            class="wf-tap p-1.5 hover:bg-slate-100 hover:text-slate-900 rounded-lg transition-colors cursor-pointer"
            title="自适应全览所有画框 (Shift+1)"
            @click="fitAll"
          >
            <Maximize2 class="w-3.5 h-3.5" />
          </button>
        </div>

        <!-- Launch Mobile Preview -->
        <button
          class="wf-tap inline-flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold text-white bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 rounded-xl shadow-sm shadow-emerald-500/25 border border-emerald-500/80 transition-all cursor-pointer"
          @click="openPreview"
        >
          <Play class="w-3.5 h-3.5 fill-white" />
          <span>预览原型</span>
        </button>
      </div>
    </header>

    <!-- ===== Main Workbench Workspace ===== -->
    <div class="flex flex-1 overflow-hidden relative">
      <!-- ===== Left Sidebar: Design File Explorer ===== -->
      <aside class="w-60 bg-white/95 backdrop-blur-md border-r border-slate-200/90 flex flex-col shrink-0 z-10 shadow-2xs">
        <!-- Sidebar Header -->
        <div class="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="w-5 h-5 rounded-md bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Layers class="w-3.5 h-3.5" />
            </div>
            <span class="text-xs font-bold text-slate-900">设计稿 ({{ pages.length }})</span>
          </div>
        </div>

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
      </aside>

      <!-- ===== Right: Infinite Workbench Canvas ===== -->
      <main
        class="canvas-viewport flex-1 overflow-hidden relative cursor-grab bg-slate-100"
        ref="viewportRef"
        :class="{ '!cursor-grabbing': isAnyDragging }"
        @wheel.prevent="onWheel"
        @mousedown="onMouseDown"
      >
        <!-- Full-viewport transparent overlay during drag to shield iframes and eliminate hit-testing cost -->
        <div
          v-if="isAnyDragging"
          class="absolute inset-0 z-30 cursor-grabbing select-none"
          style="pointer-events: auto;"
        />

        <div
          class="canvas-content absolute top-0 left-0"
          :class="{ 'is-animating': !isAnyDragging && animating, 'is-dragging': isAnyDragging }"
          :style="contentStyle"
        >
          <template v-for="b in blocks" :key="b.page.id">
            <div
              class="page-block absolute"
              :style="{ left: `${b.x}px`, top: `${b.y}px` }"
            >
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
              </div>

              <!-- Page Canvas Component -->
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
                :edit-mode="fineTune && mode === 'edit'"
                :interactive="!fineTune"
                :box-w="220"
                :gap="16"
                @navigate="onProtoNavigate"
                @back="onProtoBack"
                @save-html="onSaveHtml"
                @element-click="handleElementClick"
                @element-hover="handleElementHover"
                @ann-hover="hoveredAnnId = $event"
                @ann-click="handleAnnClick"
                @ann-save="handleAnnSave"
              />
            </div>
          </template>

          <div v-if="!pages.length" class="flex flex-col items-center justify-center p-20 text-slate-400">
            <Layers class="w-12 h-12 text-slate-300 mb-3" />
            <p class="text-sm font-medium">项目暂无页面，请先返回项目页扫描设计稿</p>
          </div>
        </div>
      </main>
    </div>

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
                        v-if="activeSimElement"
                        class="sim-spotlight-box pointer-events-none absolute z-40 transition-all duration-300"
                        :style="{
                          left: `${activeSimElement.x - 3}px`,
                          top: `${activeSimElement.y - 3}px`,
                          width: `${activeSimElement.width + 6}px`,
                          height: `${activeSimElement.height + 6}px`,
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
                    <div class="text-xs font-bold text-white truncate">{{ previewPage?.name || '页面说明' }}</div>
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
                    draggingAnnId === item.id ? 'opacity-40 scale-[0.98]' : ''
                  ]"
                  draggable="true"
                  @dragstart="onSimDragStart($event, item.id)"
                  @dragover.prevent="onSimDragOver($event, item.id)"
                  @dragleave="onSimDragLeave($event, item.id)"
                  @drop.prevent="onSimDrop($event, item.id)"
                  @dragend="onSimDragEnd"
                  @click="onSimCardClick(item)"
                >
                  <!-- Card Header -->
                  <div class="flex items-center justify-between gap-2 mb-1.5">
                    <div class="flex items-center gap-1.5 overflow-hidden">
                      <!-- Drag Handle Indicator -->
                      <span
                        class="text-slate-500 hover:text-slate-300 cursor-grab active:cursor-grabbing shrink-0 transition-colors p-0.5 -ml-1 rounded"
                        title="长按或拖拽调整顺序"
                      >
                        <GripVertical class="w-3.5 h-3.5" />
                      </span>
                      <span class="font-semibold text-xs text-white truncate">{{ item.title }}</span>
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
                  <div class="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap word-break">
                    {{ item.text || '暂无业务描述' }}
                  </div>

                  <!-- Interactive Action Tip -->
                  <div v-if="item.interactionType === 'navigate'" class="mt-2 pt-1.5 border-t border-slate-700/40 text-[10px] text-emerald-400/80 flex items-center gap-1">
                    <Compass class="w-3 h-3" />
                    <span>点击卡片直接在模拟器中体验跳转</span>
                  </div>
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
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
} from 'lucide-vue-next'
import { projectApi } from '../api/project'
import { getFileUrl } from '../api/http'
import type { Element, Page, Prototype } from '../types'
import PageCanvas from '../components/PageCanvas.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const proto = ref<Prototype | null>(null)
const pages = computed(() => proto.value?.pages || [])

const showWireframe = ref(true)
const showAnnotations = ref(true)
/** 整页原型微调模式：拖动移动元素 / 滚轮调字号 / Del 隐藏，自动保存 */
const fineTune = ref(false)

function onSaveHtml(p: { pageId: number; html: string }) {
  projectApi
    .saveHtml(id, p.pageId, p.html)
    .then(() => {
      const page = proto.value?.pages.find((pg) => pg.id === p.pageId)
      if (page) page.html_content = p.html
      showToast('✅ 微调已保存')
    })
    .catch((e: any) => showToast(`❌ 保存失败: ${e?.message || '未知错误'}`))
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
    for (const p of proto.value.pages) {
      if (p.canvas_x != null && p.canvas_y != null) {
        pageOverrides.value[p.id] = { x: p.canvas_x, y: p.canvas_y }
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

// ===== 整页 HTML 内的跨页跳转：点击 data-nav 元素 → 切换到目标页原型 =====
function onProtoNavigate(pageName: string) {
  const current = mode.value === 'preview' ? previewPageId.value : focusPageId.value ?? null
  if (current != null) backStack.value.push(current)
  const nameKey = pageName.trim()
  const target =
    pages.value.find((p) => p.name.trim() === nameKey) ||
    pages.value.find((p) => p.name.trim().includes(nameKey)) ||
    pages.value.find((p) => nameKey.includes(p.name.trim()))
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

const fallbackW = 980
const fallbackH = 1000

const pageOverrides = ref<Record<number, { x: number; y: number }>>({})

const blocks = computed(() => {
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
    const w = inst?.stageW ?? fallbackW
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
  if ((e.target as HTMLElement).closest('.wf-element, .ann-box, .el-button, .el-checkbox, input, select, textarea, .block-label')) return
  selectedElementId.value = null
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

function handleAnnSave(annId: number, text: string) {
  if (!proto.value) return
  const ann = proto.value.pages.flatMap((p) => p.annotations).find((a) => a.id === annId)
  projectApi
    .updateAnnotation(id, annId, { text })
    .then(() => {
      if (ann) ann.text = text
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
  nextTick(updatePhoneScale)
})

function openPreview() {
  if (!pages.value.length) return
  const active = focusPageId.value || pages.value[0].id
  previewPageId.value = active
  mode.value = 'preview'
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
    pageAnnOrders.value[pageId] = currentList.map((a) => a.id)
    saveAnnOrders()
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

    return {
      id: ann.id,
      index: idx,
      elementId: el?.id ?? null,
      title: el?.label || `标注 ${idx + 1}`,
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

const activeSimAnn = computed(() => simAnnList.value.find((a) => a.id === selectedSimAnnId.value))
const activeSimElement = computed(() => {
  if (!previewPage.value || !activeSimAnn.value?.elementId) return null
  return previewPage.value.elements.find((e) => e.id === activeSimAnn.value!.elementId) || null
})
const activeSimAnnTitle = computed(() => activeSimAnn.value?.title || '')

function scrollToElementInSimulator(el: Element | null) {
  if (!el || !simScrollRef.value) return
  nextTick(() => {
    if (!simScrollRef.value) return
    const targetTop = Math.max(0, el.y - previewHeight.value / 2 + el.height / 2)
    simScrollRef.value.scrollTo({
      top: targetTop,
      behavior: 'smooth',
    })
  })
}

const simInteractiveCount = computed(() => simAnnList.value.filter((a) => !!a.interactionType).length)

function onSimCardClick(item: SimAnnItem) {
  selectedSimAnnId.value = item.id
  const targetEl = previewPage.value?.elements.find((e) => e.id === item.elementId) || null
  if (targetEl) {
    scrollToElementInSimulator(targetEl)
  }
  if (item.interactionType === 'navigate') {
    if (item.targetPageId) {
      showToast(`⚡ 执行跳转: ${item.interactionTarget || '目标页面'}`)
      slideClass.value = ''
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          slideClass.value = 'slide-in-right'
          previewPageId.value = item.targetPageId!
          setTimeout(() => (slideClass.value = ''), 400)
        })
      })
    } else if (item.interactionTarget) {
      onProtoNavigate(item.interactionTarget)
    }
  } else if (item.interactionType === 'modal') {
    showToast(`⚡ 唤起弹窗: ${item.interactionTarget || item.title}`)
  } else if (item.interactionType === 'toggle') {
    showToast(`⚡ 切换状态: ${item.title}`)
  } else {
    showToast(`📌 组件说明: ${item.title}`)
  }
}

function handlePreviewClick(el: Element) {
  // 联动高亮对应的说明卡片并滚动到可视区域
  const matchedAnn = simAnnList.value.find((a) => a.elementId === el.id)
  if (matchedAnn) {
    selectedSimAnnId.value = matchedAnn.id
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

onMounted(async () => {
  window.addEventListener('resize', updatePhoneScale)
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
  // 1. ESC key exits preview mode immediately
  if (e.key === 'Escape' && mode.value === 'preview') {
    mode.value = 'edit'
    return
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

  // 3. Fine tune undo/redo in canvas edit mode
  if (!fineTune.value || mode.value === 'preview') return
  const isZ = e.key === 'z' || e.key === 'Z'
  const isY = e.key === 'y' || e.key === 'Y'
  if ((e.ctrlKey || e.metaKey) && isZ && !e.shiftKey) {
    e.preventDefault()
    const frames = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
    frames.forEach((f) => f.contentWindow?.postMessage({ type: 'wf-undo' }, '*'))
  } else if ((e.ctrlKey || e.metaKey) && (isY || (isZ && e.shiftKey))) {
    e.preventDefault()
    const frames = document.querySelectorAll<HTMLIFrameElement>('iframe.html-frame')
    frames.forEach((f) => f.contentWindow?.postMessage({ type: 'wf-redo' }, '*'))
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
</style>