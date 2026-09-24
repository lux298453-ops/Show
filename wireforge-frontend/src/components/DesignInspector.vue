<template>
  <div class="design-inspector-panel flex-1 flex flex-col h-full bg-white select-none text-xs overflow-y-auto custom-scrollbar">
    <!-- 1. 顶部当前选中对象标识 & 6大对齐工具 -->
    <div class="border-b border-slate-100 bg-slate-50/60 shrink-0">
      <!-- 对象标识行 -->
      <div class="px-3 py-2 flex items-center justify-between gap-1">
        <div class="flex items-center gap-1.5 min-w-0 flex-1">
          <component
            :is="hasSelection ? Box : Hash"
            class="w-3.5 h-3.5 text-slate-500 shrink-0"
          />
          <span class="font-bold text-slate-800 truncate text-[11px]">
            {{ hasSelection ? (elementInfo?.tagName || selectedElement?.label || selectedElement?.type || '选定元素') : (currentPage?.name || '未选画板') }}
          </span>
        </div>
        <div class="flex items-center gap-1 shrink-0">
          <button
            v-if="elementInfo?.hasParentContainer"
            type="button"
            class="px-1.5 py-0.5 rounded text-[10px] font-medium bg-blue-50 text-[#0D99FF] hover:bg-blue-100 transition-colors cursor-pointer flex items-center gap-0.5"
            title="一键选中外层卡片容器"
            @click="emit('select-parent')"
          >
            <ArrowUp class="w-3 h-3" />
            <span>选外层</span>
          </button>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-200/80 text-slate-600 font-mono">
            {{ hasSelection ? 'Element' : 'Frame' }}
          </span>
        </div>
      </div>

      <!-- 6 大 Figma 一键对齐工具栏 -->
      <div class="px-2.5 py-1.5 border-t border-slate-200/60 bg-white flex items-center justify-between gap-1">
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="左对齐 (Align Left)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'left')"
        >
          <AlignStartVertical class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="水平居中 (Align Horizontal Centers)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'center-h')"
        >
          <AlignCenterVertical class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="右对齐 (Align Right)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'right')"
        >
          <AlignEndVertical class="w-3.5 h-3.5" />
        </button>
        <div class="w-[1px] h-3.5 bg-slate-200/80 mx-0.5"></div>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="顶对齐 (Align Top)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'top')"
        >
          <AlignStartHorizontal class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="垂直居中 (Align Vertical Centers)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'center-v')"
        >
          <AlignCenterHorizontal class="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          class="flex-1 h-6 rounded flex items-center justify-center text-slate-500 hover:text-[#0D99FF] hover:bg-blue-50/80 transition-colors cursor-pointer disabled:opacity-30 disabled:pointer-events-none"
          title="底对齐 (Align Bottom)"
          :disabled="!hasSelection"
          @click="emit('align-selection', 'bottom')"
        >
          <AlignEndHorizontal class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- 2. 几何尺寸与坐标 (Transform) -->
    <div class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">布局 (Layout)</div>
      <div class="grid grid-cols-2 gap-2">
        <!-- X 坐标 -->
        <div
          class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors"
          :class="{ 'opacity-40 pointer-events-none': !canEditGeometry }"
        >
          <span class="text-[10px] text-slate-400 font-mono w-3.5">X</span>
          <input
            type="number"
            :value="displayX"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!canEditGeometry"
            @change="onPosChange('x', $event)"
            @keydown.enter="($event.target as HTMLInputElement).blur()"
          />
        </div>
        <!-- Y 坐标 -->
        <div
          class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors"
          :class="{ 'opacity-40 pointer-events-none': !canEditGeometry }"
        >
          <span class="text-[10px] text-slate-400 font-mono w-3.5">Y</span>
          <input
            type="number"
            :value="displayY"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!canEditGeometry"
            @change="onPosChange('y', $event)"
            @keydown.enter="($event.target as HTMLInputElement).blur()"
          />
        </div>
        <!-- 宽度 W -->
        <div
          class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors"
          :class="{ 'opacity-40 pointer-events-none': !canEditGeometry }"
        >
          <span class="text-[10px] text-slate-400 font-mono w-3.5">W</span>
          <input
            type="number"
            :value="displayW"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!canEditGeometry"
            @change="onDimensionChange('width', $event)"
            @keydown.enter="($event.target as HTMLInputElement).blur()"
          />
        </div>
        <!-- 高度 H -->
        <div
          class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors"
          :class="{ 'opacity-40 pointer-events-none': !canEditGeometry }"
        >
          <span class="text-[10px] text-slate-400 font-mono w-3.5">H</span>
          <input
            type="number"
            :value="displayH"
            class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
            :disabled="!canEditGeometry"
            @change="onDimensionChange('height', $event)"
            @keydown.enter="($event.target as HTMLInputElement).blur()"
          />
        </div>
      </div>
    </div>

    <!-- 3. 圆角调节 (Corner Radius) -->
    <div
      class="p-3 border-b border-slate-100"
      :class="{ 'opacity-60 pointer-events-none': isVectorShape, 'opacity-40 pointer-events-none': !hasSelection && !isVectorShape }"
    >
      <div class="flex items-center justify-between mb-2">
        <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">圆角 (Corner Radius)</div>
        <span v-if="!isVectorShape" class="font-mono text-[10px] text-slate-500">{{ currentRadius }}px</span>
      </div>
      <!-- 矢量图形置灰并提示不支持整体圆角 -->
      <div v-if="isVectorShape" class="py-1.5 px-2 bg-slate-50 border border-slate-200/80 rounded-md text-[11px] text-slate-400 flex items-center justify-between">
        <span class="text-[10px] text-slate-400">自由路径不支持整体圆角</span>
        <span class="text-[10px] text-slate-400 font-mono">N/A</span>
      </div>
      <div v-else class="space-y-2">
        <!-- 数值输入与滑块 -->
        <div class="flex items-center gap-2">
          <div class="flex-1 flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white">
            <Minimize2 class="w-3 h-3 text-slate-400 mr-1.5" />
            <input
              type="number"
              min="0"
              max="9999"
              :value="currentRadius"
              class="w-full bg-transparent text-[11px] font-mono outline-none text-slate-700"
              :disabled="!hasSelection"
              @change="onRadiusChange"
            />
          </div>
          <!-- 常用预设快捷按钮 -->
          <div class="flex items-center gap-1">
            <button
              v-for="r in [0, 4, 8, 12, 16]"
              :key="r"
              type="button"
              class="px-1.5 py-1 rounded text-[10px] font-mono transition-colors cursor-pointer"
              :class="currentRadius === r ? 'bg-blue-600 text-white font-bold' : 'bg-slate-100 hover:bg-slate-200 text-slate-600'"
              :disabled="!hasSelection"
              @click="applyRadius(r)"
            >
              {{ r }}
            </button>
            <button
              type="button"
              class="px-1.5 py-1 rounded text-[10px] font-mono transition-colors cursor-pointer"
              :class="currentRadius >= 99 ? 'bg-blue-600 text-white font-bold' : 'bg-slate-100 hover:bg-slate-200 text-slate-600'"
              title="胶囊全圆角"
              :disabled="!hasSelection"
              @click="applyRadius(9999)"
            >
              全
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 描边与边框 (Stroke / Border) -->
    <div
      class="p-3 border-b border-slate-100"
      :class="{ 'opacity-40 pointer-events-none': !hasSelection }"
    >
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">描边 (Stroke)</div>
      <div class="flex items-center justify-between gap-2">
        <!-- 粗细选择 -->
        <div class="flex items-center gap-1 bg-slate-50 border border-slate-200/80 rounded-md p-0.5">
          <button
            v-for="w in [0, 1, 2, 4]"
            :key="w"
            type="button"
            class="px-1.5 py-0.5 rounded text-[10px] font-mono transition-colors cursor-pointer"
            :class="strokeWidth === w ? 'bg-white shadow-xs text-blue-600 font-bold' : 'text-slate-500 hover:text-slate-800'"
            :disabled="!hasSelection"
            @click="setStrokeWidth(w)"
          >
            {{ w === 0 ? '无' : `${w}px` }}
          </button>
        </div>

        <!-- 边框色选择器 -->
        <div class="flex items-center gap-1.5 bg-slate-50 border border-slate-200/80 rounded-md px-1.5 py-1">
          <label
            class="w-4 h-4 rounded border border-slate-300 shadow-2xs relative overflow-hidden shrink-0"
            :class="hasSelection ? 'cursor-pointer' : 'cursor-default'"
            :style="{ backgroundColor: strokeColor }"
          >
            <input
              type="color"
              :value="strokeColor"
              class="absolute -top-2 -left-2 w-8 h-8 opacity-0"
              :class="hasSelection ? 'cursor-pointer' : 'pointer-events-none'"
              :disabled="!hasSelection"
              @input="onStrokeColorInput"
            />
          </label>
          <span class="font-mono text-[10px] text-slate-600 uppercase">
            {{ strokeColor }}
          </span>
        </div>
      </div>
    </div>

    <!-- 5. 效果 (Effects)：外阴影、内阴影、模糊，参数跟 Figma 一样可调 -->
    <div
      class="p-3 border-b border-slate-100"
      :class="{ 'opacity-40 pointer-events-none': !hasSelection }"
    >
      <div class="flex items-center justify-between mb-2">
        <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">效果 (Effects)</div>
        <button
          type="button"
          class="wf-effect-anchor w-5 h-5 rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-800 flex items-center justify-center cursor-pointer"
          title="添加效果"
          :disabled="!hasSelection"
          @click.stop="toggleAddMenu"
        >
          <Plus class="w-3.5 h-3.5" />
        </button>
      </div>
      <div v-if="!effects.length" class="text-[11px] text-slate-400 px-0.5">还没有效果</div>
      <div v-else class="flex flex-col gap-1">
        <div
          v-for="fx in effects"
          :key="fx.id"
          class="wf-effect-anchor flex items-center gap-1.5 px-1.5 py-1 rounded-md"
          :class="openEffectId === fx.id ? 'bg-blue-50' : 'hover:bg-slate-50'"
        >
          <input
            type="checkbox"
            class="w-3.5 h-3.5 accent-[#0D99FF] cursor-pointer"
            :checked="fx.visible"
            @change="patchEffect(fx.id, { visible: ($event.target as HTMLInputElement).checked })"
          />
          <button
            type="button"
            class="flex-1 min-w-0 text-left text-[12px] text-slate-800 truncate cursor-pointer"
            @click.stop="openEffectEditor($event, fx.id)"
          >
            {{ effectLabel(fx.type) }}
          </button>
          <button
            type="button"
            class="w-5 h-5 rounded text-slate-400 hover:text-slate-700 flex items-center justify-center cursor-pointer"
            :title="fx.visible ? '隐藏效果' : '显示效果'"
            @click.stop="patchEffect(fx.id, { visible: !fx.visible })"
          >
            <Eye v-if="fx.visible" class="w-3.5 h-3.5" />
            <EyeOff v-else class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="showAddMenu"
        class="wf-effect-menu fixed z-[80] w-48 rounded-lg bg-slate-900 text-white shadow-2xl py-1"
        :style="{ left: addMenuPos.left + 'px', top: addMenuPos.top + 'px' }"
      >
        <button
          v-for="item in effectMenu"
          :key="item.type"
          type="button"
          class="w-full flex items-center gap-2 px-3 py-1.5 text-left text-[12px] hover:bg-white/10 cursor-pointer"
          @click="addEffect(item.type)"
        >
          <span class="w-4 text-center text-slate-300 text-[11px]">{{ effectMark(item.type) }}</span>
          <span>{{ item.hint }}</span>
        </button>
      </div>
      <div
        v-if="openEffect"
        class="wf-effect-pop fixed z-[80] w-[248px] rounded-xl bg-white border border-slate-200 shadow-2xl p-3"
        :style="{ left: popPos.left + 'px', top: popPos.top + 'px' }"
      >
        <div class="flex items-center gap-1.5 mb-3">
          <input
            type="checkbox"
            class="w-3.5 h-3.5 accent-[#0D99FF] cursor-pointer"
            :checked="openEffect.visible"
            @change="patchEffect(openEffect.id, { visible: ($event.target as HTMLInputElement).checked })"
          />
          <select
            class="flex-1 min-w-0 bg-slate-50 border border-slate-200 rounded-md text-[12px] text-slate-800 px-1.5 py-1 cursor-pointer"
            :value="openEffect.type"
            @change="patchEffect(openEffect.id, { type: ($event.target as HTMLSelectElement).value as WfEffectType })"
          >
            <option v-for="item in effectMenu" :key="item.type" :value="item.type">{{ item.label }}</option>
          </select>
          <button
            type="button"
            class="w-6 h-6 rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 flex items-center justify-center cursor-pointer"
            title="删除这个效果"
            @click="removeEffect(openEffect.id)"
          >
            <X class="w-3.5 h-3.5" />
          </button>
        </div>
        <template v-if="!isBlurEffect(openEffect.type)">
          <div class="flex items-center gap-2 mb-2">
            <span class="w-10 text-[11px] text-slate-500 shrink-0">位置</span>
            <label class="flex-1 flex items-center gap-1 bg-slate-100 rounded-md px-1.5 py-1">
              <span class="text-[10px] text-slate-400">X</span>
              <input :value="openEffect.x" type="number" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'x', $event, true)" @change="onEffectNumber(openEffect.id, 'x', $event, false)" />
            </label>
            <label class="flex-1 flex items-center gap-1 bg-slate-100 rounded-md px-1.5 py-1">
              <span class="text-[10px] text-slate-400">Y</span>
              <input :value="openEffect.y" type="number" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'y', $event, true)" @change="onEffectNumber(openEffect.id, 'y', $event, false)" />
            </label>
          </div>
          <div class="flex items-center gap-2 mb-2">
            <span class="w-10 text-[11px] text-slate-500 shrink-0">模糊</span>
            <label class="flex-1 flex items-center bg-slate-100 rounded-md px-1.5 py-1">
              <input :value="openEffect.blur" type="number" min="0" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'blur', $event, true)" @change="onEffectNumber(openEffect.id, 'blur', $event, false)" />
            </label>
          </div>
          <div class="flex items-center gap-2 mb-2">
            <span class="w-10 text-[11px] text-slate-500 shrink-0">扩展</span>
            <label class="flex-1 flex items-center bg-slate-100 rounded-md px-1.5 py-1">
              <input :value="openEffect.spread" type="number" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'spread', $event, true)" @change="onEffectNumber(openEffect.id, 'spread', $event, false)" />
            </label>
          </div>
          <div class="flex items-center gap-2">
            <span class="w-10 text-[11px] text-slate-500 shrink-0">颜色</span>
            <label class="w-5 h-5 rounded border border-slate-300 relative overflow-hidden shrink-0 cursor-pointer" :style="{ backgroundColor: openEffect.color }">
              <input type="color" :value="toColorInput(openEffect.color)" class="absolute -top-2 -left-2 w-10 h-10 opacity-0 cursor-pointer" @input="onEffectColor(openEffect.id, $event, true)" @change="onEffectColor(openEffect.id, $event, false)" />
            </label>
            <input
              :value="openEffect.color.replace('#', '')"
              class="flex-1 min-w-0 bg-slate-100 rounded-md px-1.5 py-1 text-[12px] font-mono uppercase text-slate-800 outline-none"
              maxlength="6"
              @change="onEffectHex(openEffect.id, $event)"
            />
            <label class="w-14 flex items-center bg-slate-100 rounded-md px-1.5 py-1">
              <input :value="openEffect.opacity" type="number" min="0" max="100" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'opacity', $event, true)" @change="onEffectNumber(openEffect.id, 'opacity', $event, false)" />
              <span class="text-[10px] text-slate-400">%</span>
            </label>
          </div>
        </template>
        <div v-else class="flex items-center gap-2">
          <span class="w-10 text-[11px] text-slate-500 shrink-0">模糊</span>
          <label class="flex-1 flex items-center bg-slate-100 rounded-md px-1.5 py-1">
            <input :value="openEffect.blur" type="number" min="0" class="w-full bg-transparent text-[12px] text-slate-800 outline-none" @input="onEffectNumber(openEffect.id, 'blur', $event, true)" @change="onEffectNumber(openEffect.id, 'blur', $event, false)" />
          </label>
        </div>
      </div>
    </Teleport>

    <!-- 6. 填充与色彩 (Fill)。没选中元素时，颜色铺在当前画框上。 -->
    <div
      class="p-3 border-b border-slate-100"
      :class="{ 'opacity-40 pointer-events-none': !canFill }"
    >
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">
        {{ hasSelection ? '填充 (Fill)' : '画框填充' }}
      </div>
      <div class="flex items-center justify-between gap-2 bg-slate-50 border border-slate-200/80 rounded-lg p-1.5">
        <div class="flex items-center gap-2">
          <!-- 色盘输入器 -->
          <label
            class="w-5 h-5 rounded border border-slate-300 shadow-2xs relative overflow-hidden shrink-0"
            :class="canFill ? 'cursor-pointer' : 'cursor-default'"
            :style="{ backgroundColor: currentFillColor }"
          >
            <input
              ref="fillInputRef"
              :key="fillInputKey"
              type="color"
              :value="currentFillColor"
              class="absolute -top-2 -left-2 w-10 h-10 opacity-0"
              :class="canFill ? 'cursor-pointer' : 'pointer-events-none'"
              :disabled="!canFill"
              @input="onColorInput"
              @change="onColorChange"
            />
          </label>
          <span class="font-mono text-[11px] text-slate-700 font-semibold uppercase">
            {{ currentFillColor }}
          </span>
        </div>
        <!-- 快捷预设色点 -->
        <div class="flex items-center gap-1">
          <button
            v-for="c in ['#0D99FF', '#10b981', '#f97316', '#64748b', '#ffffff']"
            :key="c"
            type="button"
            class="w-3.5 h-3.5 rounded-xs border border-black/10 cursor-pointer hover:scale-125 transition-transform"
            :style="{ backgroundColor: c }"
            :disabled="!canFill"
            @click="applyPresetColor(c)"
          />
        </div>
      </div>
    </div>

    <!-- 7. 媒体素材 (Media - 仅当选中图片元素时呈现) -->
    <div v-if="elementInfo?.isImage" class="p-3 border-b border-slate-100">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">图片素材 (Media)</div>
      <div class="flex items-center gap-2.5 bg-slate-50 border border-slate-200/80 rounded-lg p-2">
        <div class="w-10 h-10 rounded-md border border-slate-200 bg-white overflow-hidden shrink-0 flex items-center justify-center">
          <img
            v-if="elementInfo.imgSrc"
            :src="elementInfo.imgSrc"
            class="w-full h-full object-cover"
            alt="素材预览"
          />
          <ImageIcon v-else class="w-5 h-5 text-slate-400" />
        </div>
        <div class="flex-1 min-w-0">
          <button
            type="button"
            class="w-full py-1.5 px-2 bg-[#0D99FF] hover:bg-blue-600 text-white rounded-md text-[11px] font-semibold flex items-center justify-center gap-1 transition-colors cursor-pointer shadow-xs"
            @click="emit('replace-asset')"
          >
            <ImageIcon class="w-3.5 h-3.5" />
            <span>替换图片素材...</span>
          </button>
          <p class="text-[10px] text-slate-400 mt-1 truncate">双击画布图片也可直接替换</p>
        </div>
      </div>
    </div>

    <!-- 8. 文字属性 (Typography - 当选中包含文字或为文字元素时呈现) -->
    <div
      v-if="isTextElement || elementInfo?.hasText"
      class="p-3 border-b border-slate-100"
      :class="{ 'opacity-40 pointer-events-none': !hasSelection }"
    >
      <div class="flex items-center justify-between mb-2">
        <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">文字 (Typography)</div>
        <button
          type="button"
          class="text-[10px] text-[#0D99FF] hover:text-blue-700 font-medium flex items-center gap-0.5 cursor-pointer"
          title="在画布上直接打字修改文案"
          :disabled="!hasSelection"
          @click="emit('start-text-edit')"
        >
          <Edit3 class="w-2.5 h-2.5" />
          <span>双击打字</span>
        </button>
      </div>

      <!-- 文案直接编辑输入框 -->
      <div class="mb-2.5">
        <div class="text-[10px] text-slate-500 mb-1">文本内容</div>
        <div class="flex items-center bg-slate-50 border border-slate-200/80 rounded-md px-2 py-1 focus-within:border-[#0D99FF] focus-within:bg-white transition-colors">
          <input
            type="text"
            :value="currentTextVal"
            placeholder="输入文案..."
            class="w-full bg-transparent text-[11px] outline-none text-slate-700"
            :disabled="!hasSelection"
            @input="onTextInput"
            @change="onTextChange"
          />
        </div>
      </div>

      <!-- 字号调节 -->
      <div class="flex items-center justify-between gap-2">
        <span class="text-slate-500 text-[11px]">字号大小</span>
        <div class="flex items-center gap-1 bg-slate-50 border border-slate-200/80 rounded-lg p-1">
          <button
            type="button"
            class="w-5 h-5 rounded hover:bg-slate-200 flex items-center justify-center font-bold text-slate-600 text-xs cursor-pointer"
            title="减小字号 (快捷键 [ )"
            :disabled="!hasSelection"
            @click="stepFontSize(-2)"
          >
            −
          </button>
          <span class="w-8 text-center font-mono text-[11px] font-semibold text-slate-700">
            {{ currentFontSize }}px
          </span>
          <button
            type="button"
            class="w-5 h-5 rounded hover:bg-slate-200 flex items-center justify-center font-bold text-slate-600 text-xs cursor-pointer"
            title="增大字号 (快捷键 ] )"
            :disabled="!hasSelection"
            @click="stepFontSize(2)"
          >
            +
          </button>
        </div>
      </div>
    </div>

    <!-- 9. 快速操作 (Actions) -->
    <div class="p-3">
      <div class="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">常用操作 (Actions)</div>
      <div class="flex flex-col gap-1.5">
        <button
          v-if="elementInfo?.hasParentContainer"
          type="button"
          class="w-full py-1.5 px-2 bg-blue-50 hover:bg-blue-100 border border-blue-200/80 rounded-lg text-[#0D99FF] text-[11px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          @click="emit('select-parent')"
        >
          <ArrowUp class="w-3 h-3" />
          <span>选中外层卡片容器</span>
        </button>
        <button
          v-if="hasSelection"
          class="w-full py-1.5 px-2 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-lg text-slate-700 text-[11px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          @click="emit('duplicate-selection')"
        >
          <Copy class="w-3 h-3 text-slate-500" />
          <span>克隆副本 (Ctrl+D)</span>
        </button>
        <button
          v-if="hasSelection"
          class="w-full py-1.5 px-2 bg-rose-50 hover:bg-rose-100 border border-rose-200/80 rounded-lg text-rose-700 text-[11px] font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          @click="emit('delete-selection')"
        >
          <Trash2 class="w-3 h-3 text-rose-500" />
          <span>删除元素 (Backspace)</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import {
  Hash,
  Box,
  Copy,
  Trash2,
  ArrowUp,
  AlignStartVertical,
  AlignCenterVertical,
  AlignEndVertical,
  AlignStartHorizontal,
  AlignCenterHorizontal,
  AlignEndHorizontal,
  Minimize2,
  Image as ImageIcon,
  Edit3,
  Plus,
  X,
  Eye,
  EyeOff,
} from 'lucide-vue-next'
import type { Page, Element } from '../types'
import {
  createEffect,
  effectLabel,
  effectMenu,
  isBlurEffect,
  parseStoredEffects,
  type WfEffect,
  type WfEffectType,
} from '../utils/effects'

const props = defineProps<{
  currentPage: Page | null
  selectedElement: Element | null
  /** 没选中元素时，填充色作用在画框背景上 */
  frameFill?: string | null
  elementInfo?: {
    tagName?: string
    className?: string
    x?: number
    y?: number
    width?: number
    height?: number
    borderRadius?: number
    borderWidth?: number
    borderColor?: string
    borderStyle?: string
    boxShadow?: string
    effects?: string
    inlineShadow?: string
    inlineFilter?: string
    backgroundColor?: string
    fontSize?: number
    isImage?: boolean
    imgSrc?: string
    hasText?: boolean
    textContent?: string
    hasParentContainer?: boolean
    layerUid?: string
  } | null
}>()

const emit = defineEmits<{
  (e: 'update-dimension', payload: { key: 'width' | 'height'; val: number }): void
  (e: 'update-position', payload: { key: 'x' | 'y'; val: number }): void
  (e: 'update-color', color: string): void
  (e: 'update-frame-color', color: string): void
  (e: 'update-font-size', delta: number): void
  (e: 'align-selection', type: 'left' | 'center-h' | 'right' | 'top' | 'center-v' | 'bottom'): void
  (e: 'update-radius', radius: number): void
  (e: 'update-stroke', stroke: { width: number; color: string; style: string }): void
  (e: 'update-effects', payload: { effects: WfEffect[]; live: boolean }): void
  (e: 'duplicate-selection'): void
  (e: 'delete-selection'): void
  (e: 'replace-asset'): void
  (e: 'start-text-edit'): void
  (e: 'update-text', text: string): void
  (e: 'select-parent'): void
}>()

const hasSelection = computed(() => !!props.selectedElement || !!props.elementInfo)
const canFill = computed(() => hasSelection.value || !!props.currentPage)
const isVectorShape = computed(() => {
  const tag = (props.elementInfo?.tagName || '').toLowerCase()
  const cls = props.elementInfo?.className || ''
  const selectedType = props.selectedElement?.type || ''
  return tag === 'svg' || cls.includes('wf-vector-shape') || selectedType === 'svg' || selectedType === 'vector'
})
/** 有选中元素时编辑元素；仅选中画板时也可编辑画板 X/Y/W/H */
const canEditGeometry = computed(() => hasSelection.value || !!props.currentPage)

const displayX = computed(() => {
  if (props.elementInfo?.x !== undefined) return props.elementInfo.x
  if (props.selectedElement) return Math.round(props.selectedElement.x)
  return Math.round(props.currentPage?.canvas_x || 0)
})

const displayY = computed(() => {
  if (props.elementInfo?.y !== undefined) return props.elementInfo.y
  if (props.selectedElement) return Math.round(props.selectedElement.y)
  return Math.round(props.currentPage?.canvas_y || 0)
})

const displayW = computed(() => {
  if (props.elementInfo?.width !== undefined) return props.elementInfo.width
  if (props.selectedElement) return Math.round(props.selectedElement.width)
  return Math.round(props.currentPage?.canvas_width || 375)
})

const displayH = computed(() => {
  if (props.elementInfo?.height !== undefined) return props.elementInfo.height
  if (props.selectedElement) return Math.round(props.selectedElement.height)
  return Math.round(props.currentPage?.canvas_height || 812)
})

// 色彩
const currentFillColor = ref('#0D99FF')
const fillInputRef = ref<HTMLInputElement | null>(null)
/** 换画框时重开色盘，避免原生颜色框还停在上一张画框的颜色 */
const fillInputKey = computed(() =>
  hasSelection.value ? `el-${props.elementInfo?.layerUid || props.selectedElement?.id || 'on'}` : `frame-${props.currentPage?.id || 0}`,
)
// 字号
const currentFontSize = ref(14)
// 圆角
const currentRadius = ref(0)
// 描边
const strokeWidth = ref(0)
const strokeColor = ref('#cbd5e1')
const strokeStyle = ref('solid')
// 阴影
const effects = ref<WfEffect[]>([])
const showAddMenu = ref(false)
const openEffectId = ref('')
const addMenuPos = ref({ left: 0, top: 0 })
const popPos = ref({ left: 0, top: 0 })
const openEffect = computed(() => effects.value.find((fx) => fx.id === openEffectId.value) || null)

const currentTextVal = ref('')

watch(
  () => props.frameFill,
  (color) => {
    if (hasSelection.value || !color) return
    const hex = rgbToHex(color)
    currentFillColor.value = hex
    const input = fillInputRef.value
    if (input && input.value.toLowerCase() !== hex.toLowerCase()) input.value = hex
  },
  { immediate: true },
)

watch(
  () => props.elementInfo,
  (info) => {
    if (!info) {
      currentTextVal.value = ''
      currentRadius.value = 0
      strokeWidth.value = 0
      if (props.frameFill) currentFillColor.value = rgbToHex(props.frameFill)
      return
    }
    if (info.borderRadius !== undefined) currentRadius.value = info.borderRadius
    if (info.borderWidth !== undefined) strokeWidth.value = info.borderWidth
    if (info.borderColor) strokeColor.value = info.borderColor
    if (info.fontSize !== undefined) currentFontSize.value = info.fontSize
    if (info.backgroundColor && info.backgroundColor !== 'transparent' && info.backgroundColor !== 'rgba(0, 0, 0, 0)') {
      currentFillColor.value = rgbToHex(info.backgroundColor)
    }
    if (info.textContent !== undefined) {
      currentTextVal.value = info.textContent
    }
  },
  { immediate: true, deep: true }
)

function onTextInput(e: Event) {
  if (!hasSelection.value) return
  const v = (e.target as HTMLInputElement).value
  currentTextVal.value = v
  emit('update-text', v)
}

function onTextChange(e: Event) {
  if (!hasSelection.value) return
  const v = (e.target as HTMLInputElement).value
  currentTextVal.value = v
  emit('update-text', v)
}

function rgbToHex(rgbStr: string): string {
  if (rgbStr.startsWith('#')) return rgbStr
  const match = rgbStr.match(/\d+/g)
  if (!match || match.length < 3) return '#0D99FF'
  const r = parseInt(match[0]).toString(16).padStart(2, '0')
  const g = parseInt(match[1]).toString(16).padStart(2, '0')
  const b = parseInt(match[2]).toString(16).padStart(2, '0')
  return `#${r}${g}${b}`
}

function onRadiusChange(e: Event) {
  if (!hasSelection.value) return
  const v = Math.max(0, parseInt((e.target as HTMLInputElement).value) || 0)
  currentRadius.value = v
  emit('update-radius', v)
}

function applyRadius(r: number) {
  if (!hasSelection.value) return
  currentRadius.value = r
  emit('update-radius', r)
}

function setStrokeWidth(w: number) {
  if (!hasSelection.value) return
  strokeWidth.value = w
  emit('update-stroke', {
    width: w,
    color: strokeColor.value,
    style: strokeStyle.value,
  })
}

function onStrokeColorInput(e: Event) {
  if (!hasSelection.value) return
  const c = (e.target as HTMLInputElement).value
  strokeColor.value = c
  if (strokeWidth.value === 0) strokeWidth.value = 1
  emit('update-stroke', {
    width: strokeWidth.value,
    color: c,
    style: strokeStyle.value,
  })
}

function effectMark(type: WfEffectType): string {
  if (type === 'inner-shadow') return '▣'
  if (type === 'drop-shadow') return '□'
  if (type === 'layer-blur') return '◌'
  return '◎'
}

function toColorInput(color: string): string {
  return /^#[0-9a-fA-F]{6}$/.test(color) ? color : '#000000'
}

function emitEffects(live: boolean) {
  emit('update-effects', { effects: effects.value.map((fx) => ({ ...fx })), live })
}

function patchEffect(id: string, patch: Partial<WfEffect>, live = false) {
  effects.value = effects.value.map((fx) => (fx.id === id ? { ...fx, ...patch } : fx))
  emitEffects(live)
}

function onEffectNumber(id: string, key: 'x' | 'y' | 'blur' | 'spread' | 'opacity', e: Event, live: boolean) {
  const raw = parseFloat((e.target as HTMLInputElement).value)
  const value = Number.isFinite(raw) ? raw : 0
  const next = key === 'opacity' ? Math.max(0, Math.min(100, value)) : key === 'blur' ? Math.max(0, value) : value
  patchEffect(id, { [key]: next }, live)
}

function onEffectColor(id: string, e: Event, live: boolean) {
  patchEffect(id, { color: (e.target as HTMLInputElement).value }, live)
}

function onEffectHex(id: string, e: Event) {
  const raw = (e.target as HTMLInputElement).value.replace('#', '').trim()
  if (!/^[0-9a-fA-F]{6}$/.test(raw)) return
  patchEffect(id, { color: '#' + raw }, false)
}

function addEffect(type: WfEffectType) {
  const fx = createEffect(type)
  effects.value = [...effects.value, fx]
  showAddMenu.value = false
  openEffectId.value = fx.id
  emitEffects(false)
}

function removeEffect(id: string) {
  effects.value = effects.value.filter((fx) => fx.id !== id)
  if (openEffectId.value === id) openEffectId.value = ''
  emitEffects(false)
}

function toggleAddMenu(e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  addMenuPos.value = {
    left: Math.max(8, rect.right - 192),
    top: rect.bottom + 6,
  }
  openEffectId.value = ''
  showAddMenu.value = !showAddMenu.value
}

function openEffectEditor(e: MouseEvent, id: string) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  popPos.value = {
    left: Math.max(8, rect.left - 256),
    top: Math.max(8, Math.min(rect.top, window.innerHeight - 280)),
  }
  showAddMenu.value = false
  openEffectId.value = openEffectId.value === id ? '' : id
}

function onEffectDocDown(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (target?.closest?.('.wf-effect-pop, .wf-effect-menu, .wf-effect-anchor')) return
  showAddMenu.value = false
  openEffectId.value = ''
}

watch([showAddMenu, openEffectId], ([menu, id]) => {
  if (menu || id) window.addEventListener('mousedown', onEffectDocDown, true)
  else window.removeEventListener('mousedown', onEffectDocDown, true)
})

onBeforeUnmount(() => window.removeEventListener('mousedown', onEffectDocDown, true))

watch(
  () => props.elementInfo?.layerUid ?? '',
  () => {
    showAddMenu.value = false
    openEffectId.value = ''
    effects.value = props.elementInfo ? parseStoredEffects(props.elementInfo) : []
  },
  { immediate: true },
)

const isTextElement = computed(() => {
  if (props.elementInfo?.tagName) {
    return /^(h[1-6]|p|span|button|a|label)$/i.test(props.elementInfo.tagName)
  }
  if (!props.selectedElement) return false
  const t = props.selectedElement.type?.toLowerCase() || ''
  return t.includes('text') || t.includes('btn') || t.includes('button') || t.includes('label')
})

function onDimensionChange(key: 'width' | 'height', e: Event) {
  if (!canEditGeometry.value) return
  const val = Math.max(10, parseInt((e.target as HTMLInputElement).value) || 0)
  emit('update-dimension', { key, val })
}

function onPosChange(key: 'x' | 'y', e: Event) {
  if (!canEditGeometry.value) return
  const val = parseInt((e.target as HTMLInputElement).value) || 0
  emit('update-position', { key, val })
}

function paintFill(color: string) {
  currentFillColor.value = color
  if (hasSelection.value) emit('update-color', color)
  else if (props.currentPage) emit('update-frame-color', color)
}

function onColorInput(e: Event) {
  if (!canFill.value) return
  paintFill((e.target as HTMLInputElement).value)
}

function onColorChange(e: Event) {
  if (!canFill.value) return
  paintFill((e.target as HTMLInputElement).value)
}

function applyPresetColor(color: string) {
  if (!canFill.value) return
  paintFill(color)
}

function stepFontSize(delta: number) {
  if (!hasSelection.value) return
  currentFontSize.value = Math.max(10, Math.min(60, currentFontSize.value + delta))
  emit('update-font-size', delta)
}
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
</style>
