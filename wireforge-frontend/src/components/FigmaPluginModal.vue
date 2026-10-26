<template>
  <Teleport to="body">
    <!-- 1. 类似 Figma 的全局资源与插件检索主模态框 (Plugins & Widgets Modal) -->
    <div
      v-if="modelValue && !activeRunningPlugin"
      class="fixed inset-0 z-[150] flex items-center justify-center bg-black/40 backdrop-blur-sm select-none"
      @mousedown.self="closeModal"
      @keydown.esc="closeModal"
    >
      <div
        class="w-[560px] max-w-[94vw] h-[640px] max-h-[90vh] bg-white dark:bg-[#252525] rounded-2xl shadow-2xl border border-slate-200 dark:border-[#383838] flex flex-col overflow-hidden text-slate-800 dark:text-slate-100 font-sans transition-all"
        @click.stop
      >
        <!-- A. 顶部搜索框 (完全对齐 Figma 搜索条) -->
        <div class="p-3 border-b border-slate-100 dark:border-[#333] flex items-center gap-2">
          <div class="relative flex-1 flex items-center">
            <Search class="w-4 h-4 text-slate-400 absolute left-3 pointer-events-none" />
            <input
              ref="searchInputRef"
              v-model="searchQuery"
              type="text"
              placeholder="搜索插件与资源库..."
              class="w-full h-10 pl-9 pr-8 text-sm bg-slate-100 dark:bg-[#1e1e1e] border-none rounded-xl text-slate-800 dark:text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0D99FF]/30"
              @keydown.esc="closeModal"
            />
            <button
              v-if="searchQuery"
              type="button"
              class="absolute right-2.5 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
              @click="searchQuery = ''"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
          <button
            type="button"
            class="p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-[#333] rounded-xl cursor-pointer transition-colors"
            title="关闭 (Esc)"
            @click="closeModal"
          >
            <X class="w-4 h-4" />
          </button>
        </div>

        <!-- B. 插件分类筛选栏 (全部响应式可用) -->
        <div class="px-4 py-2 border-b border-slate-100 dark:border-[#333] flex items-center justify-between text-xs font-medium">
          <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
            <button
              v-for="cat in [
                { id: 'all', label: '全部' },
                { id: 'data', label: '数据与图表' },
                { id: 'assets', label: '矢量与配图' },
                { id: 'utility', label: '实用工具' },
                { id: 'motion', label: '微动效' }
              ]"
              :key="cat.id"
              type="button"
              class="px-2.5 py-1 rounded-lg transition-colors cursor-pointer shrink-0"
              :class="selectedCategory === cat.id ? 'bg-[#0D99FF]/10 text-[#0D99FF] dark:text-[#38bdf8] font-semibold' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-300'"
              @click="selectedCategory = cat.id as any"
            >
              {{ cat.label }}
            </button>
          </div>
        </div>

        <!-- C. 内容区：插件列表 / 详情卡片 -->
        <div class="flex-1 overflow-y-auto p-4 custom-scrollbar">
          <!-- 插件详情视图 (对齐 Figma 截图 4) -->
          <div v-if="selectedDetailPlugin" class="flex flex-col h-full">
            <div class="flex items-center justify-between mb-3 text-xs text-slate-500">
              <button
                type="button"
                class="inline-flex items-center gap-1 text-slate-600 dark:text-slate-300 hover:text-[#0D99FF] font-medium cursor-pointer"
                @click="selectedDetailPlugin = null"
              >
                <ChevronLeft class="w-4 h-4" />
                <span>返回插件列表</span>
              </button>
              <div class="flex items-center gap-2">
                <button type="button" class="p-1 hover:bg-slate-100 dark:hover:bg-[#333] rounded cursor-pointer" title="在社区查看">
                  <ExternalLink class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            <!-- Banner 预览卡片 -->
            <div
              class="w-full h-44 rounded-xl p-6 flex flex-col justify-end text-white shadow-inner mb-4 relative overflow-hidden"
              :class="`bg-gradient-to-br ${selectedDetailPlugin.bannerGradient}`"
            >
              <div class="absolute right-4 top-4 opacity-20 pointer-events-none">
                <component :is="resolveIcon(selectedDetailPlugin.iconName)" class="w-32 h-32" />
              </div>
              <span class="text-xs uppercase tracking-wider font-semibold opacity-80">WireForge 插件</span>
              <h2 class="text-xl font-bold mt-1">{{ selectedDetailPlugin.name }}</h2>
              <p class="text-xs opacity-90 mt-0.5">{{ selectedDetailPlugin.subtitle }}</p>
            </div>

            <!-- 标题与作者信息 -->
            <div class="flex items-start justify-between mb-3">
              <div>
                <h3 class="text-base font-bold text-slate-900 dark:text-white">{{ selectedDetailPlugin.name }}</h3>
                <div class="flex items-center gap-2 text-xs text-slate-400 mt-1">
                  <span>作者: {{ selectedDetailPlugin.author }}</span>
                  <span>·</span>
                  <span>v{{ selectedDetailPlugin.version }}</span>
                </div>
              </div>
              <div class="flex items-center gap-3 text-xs text-slate-400">
                <span class="flex items-center gap-1"><Heart class="w-3.5 h-3.5 text-rose-500 fill-rose-500" />{{ selectedDetailPlugin.likesCount }}</span>
                <span class="flex items-center gap-1"><User class="w-3.5 h-3.5" />{{ selectedDetailPlugin.usersCount }}</span>
              </div>
            </div>

            <!-- 描述 -->
            <p class="text-xs leading-relaxed text-slate-600 dark:text-slate-300 mb-4 whitespace-pre-line">
              {{ selectedDetailPlugin.description }}
            </p>

            <!-- 标签 -->
            <div class="flex flex-wrap gap-1.5 mb-6">
              <span
                v-for="t in selectedDetailPlugin.tags"
                :key="t"
                class="px-2 py-0.5 rounded-md bg-slate-100 dark:bg-[#333] text-[11px] text-slate-500 dark:text-slate-400"
              >
                {{ t }}
              </span>
            </div>

            <!-- 底部运行条 (对齐 Figma 截图 4) -->
            <div class="mt-auto pt-3 border-t border-slate-100 dark:border-[#333] flex items-center justify-between">
              <button
                type="button"
                class="px-4 py-2 rounded-xl text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#333] transition-colors cursor-pointer"
                @click="selectedDetailPlugin = null"
              >
                关闭
              </button>
              <button
                type="button"
                class="px-6 py-2 rounded-xl text-xs font-semibold bg-[#0D99FF] hover:bg-[#077cd1] text-white shadow-md shadow-[#0D99FF]/20 flex items-center gap-1.5 transition-all cursor-pointer"
                @click="runPlugin(selectedDetailPlugin)"
              >
                <Play class="w-3.5 h-3.5 fill-current" />
                <span>运行</span>
              </button>
            </div>
          </div>

          <!-- 列表视图 (对齐 Figma 截图 2 & 3) -->
          <div v-else class="space-y-4">
            <!-- 最近使用 (Recents) -->
            <div v-if="recentPlugin && !searchQuery">
              <div class="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">最近使用</div>
              <div
                class="p-2.5 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:bg-slate-50 dark:hover:bg-[#2c2c2c] flex items-center justify-between cursor-pointer transition-colors group"
                @click="runPlugin(recentPlugin)"
              >
                <div class="flex items-center gap-3">
                  <div
                    class="w-10 h-10 rounded-xl flex items-center justify-center text-white shrink-0 shadow-sm"
                    :class="`bg-gradient-to-br ${recentPlugin.bannerGradient}`"
                  >
                    <component :is="resolveIcon(recentPlugin.iconName)" class="w-5 h-5" />
                  </div>
                  <div>
                    <h4 class="text-xs font-semibold text-slate-800 dark:text-slate-100 group-hover:text-[#0D99FF] transition-colors">
                      {{ recentPlugin.name }}
                    </h4>
                    <p class="text-[11px] text-slate-400 mt-0.5 truncate max-w-[280px]">
                      {{ recentPlugin.subtitle }}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  class="px-3 py-1.5 rounded-lg text-xs font-medium bg-[#0D99FF]/10 text-[#0D99FF] hover:bg-[#0D99FF] hover:text-white transition-colors cursor-pointer"
                  @click.stop="runPlugin(recentPlugin)"
                >
                  运行
                </button>
              </div>
            </div>

            <!-- 推荐 / 社区插件列表 -->
            <div>
              <div class="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">
                {{ searchQuery ? `搜索结果 (${filteredPlugins.length})` : '全部插件' }}
              </div>

              <div v-if="filteredPlugins.length === 0" class="py-12 text-center text-xs text-slate-400">
                未检索到匹配插件，请换个关键词试试
              </div>

              <div v-else class="space-y-2">
                <div
                  v-for="p in filteredPlugins"
                  :key="p.id"
                  class="p-3 rounded-xl border border-slate-100 dark:border-[#333] hover:border-[#0D99FF]/40 hover:bg-slate-50/80 dark:hover:bg-[#2c2c2c]/80 flex items-start justify-between cursor-pointer transition-all group"
                  @click="runPlugin(p)"
                >
                  <div class="flex items-start gap-3">
                    <div
                      class="w-10 h-10 rounded-xl flex items-center justify-center text-white shrink-0 shadow-sm mt-0.5"
                      :class="`bg-gradient-to-br ${p.bannerGradient}`"
                    >
                      <component :is="resolveIcon(p.iconName)" class="w-5 h-5" />
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <h4 class="text-xs font-bold text-slate-800 dark:text-slate-100 group-hover:text-[#0D99FF] transition-colors">
                          {{ p.name }}
                        </h4>
                        <span class="text-[10px] px-1.5 py-0.2 rounded bg-slate-100 dark:bg-[#333] text-slate-400">
                          插件
                        </span>
                      </div>
                      <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-1 leading-snug line-clamp-2 max-w-[320px]">
                        {{ p.description }}
                      </p>
                      <div class="flex items-center gap-3 text-[10px] text-slate-400 mt-2">
                        <span class="flex items-center gap-1"><User class="w-3 h-3" />{{ p.usersCount }} 活跃用户</span>
                        <span class="flex items-center gap-1"><Heart class="w-3 h-3" />{{ p.likesCount }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="flex items-center gap-1.5 shrink-0 ml-2">
                    <button
                      type="button"
                      class="px-2.5 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-[#333] transition-colors cursor-pointer"
                      title="查看介绍详情"
                      @click.stop="openPluginDetail(p)"
                    >
                      详情
                    </button>
                    <button
                      type="button"
                      class="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-[#0D99FF] hover:bg-[#077cd1] text-white shadow-sm transition-all cursor-pointer"
                      @click.stop="runPlugin(p)"
                    >
                      运行
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 插件运行时浮动交互面板 (Plugin Active Runner Window) -->
    <!-- 对齐 Figma: 运行插件时以独立轻量浮窗悬浮在画布上方，可即时操作画板，支持鼠标自由拖拽 -->
    <div
      v-if="activeRunningPlugin"
      id="wf-plugin-runner"
      class="fixed z-[160] w-[360px] bg-white dark:bg-[#252525] rounded-2xl shadow-2xl border border-slate-200 dark:border-[#383838] flex flex-col overflow-hidden text-slate-800 dark:text-slate-100 select-none animate-in fade-in zoom-in-95 duration-150"
      :style="runnerStyle"
      @mousedown.stop
    >
      <!-- Runner Header (按住可拖动位置) -->
      <div
        class="px-3 py-2 bg-slate-50 dark:bg-[#202020] border-b border-slate-200/80 dark:border-[#333] flex items-center justify-between cursor-grab active:cursor-grabbing group transition-colors hover:bg-slate-100/60 dark:hover:bg-[#252525]"
        title="按住鼠标拖拽浮窗位置"
        @mousedown="onRunnerHeaderMouseDown"
      >
        <div class="flex items-center gap-2">
          <GripVertical class="w-3.5 h-3.5 text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-200 shrink-0" />
          <div
            class="w-5 h-5 rounded-md flex items-center justify-center text-white text-xs shrink-0"
            :class="`bg-gradient-to-br ${activeRunningPlugin.bannerGradient}`"
          >
            <component :is="resolveIcon(activeRunningPlugin.iconName)" class="w-3.5 h-3.5" />
          </div>
          <span class="text-xs font-bold text-slate-800 dark:text-slate-100 truncate max-w-[190px]">
            {{ activeRunningPlugin.name }}
          </span>
        </div>
        <div class="flex items-center gap-1">
          <button
            type="button"
            class="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-md transition-colors cursor-pointer"
            title="关闭插件"
            @click="closeRunner"
          >
            <X class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <!-- Runner Body: 根据插件 ID 渲染不同交互面板 -->
      <div class="p-3.5 max-h-[480px] overflow-y-auto custom-scrollbar text-xs">
        <!-- 插件 1: User Profile | Avatar -->
        <div v-if="activeRunningPlugin.id === 'user-profile-avatar'" class="space-y-3">
          <div class="flex items-center justify-between text-slate-500">
            <!-- 性别/类型快捷筛选 -->
            <div class="flex items-center gap-1 bg-slate-100 dark:bg-[#1f1f1f] p-0.5 rounded-lg text-[11px]">
              <button
                v-for="g in [
                  { id: 'all', label: '全部' },
                  { id: 'men', label: '男士' },
                  { id: 'women', label: '女士' },
                ]"
                :key="g.id"
                type="button"
                class="px-2 py-0.5 rounded-md font-medium transition-colors cursor-pointer"
                :class="selectedAvatarGender === g.id ? 'bg-white dark:bg-[#333] text-slate-900 dark:text-white shadow-xs' : 'text-slate-500 hover:text-slate-900'"
                @click="onSelectAvatarGender(g.id as any)"
              >
                {{ g.label }}
              </button>
            </div>
            <button
              type="button"
              class="inline-flex items-center gap-1 text-[#0D99FF] hover:underline font-medium cursor-pointer"
              :disabled="isFetchingAvatars"
              @click="refreshAvatars"
            >
              <RefreshCw class="w-3 h-3" :class="{ 'animate-spin': isFetchingAvatars }" />
              <span>{{ isFetchingAvatars ? '正在拉取...' : '换一批新头像' }}</span>
            </button>
          </div>

          <!-- 头像网格 -->
          <div class="grid grid-cols-4 gap-2.5 py-1">
            <div
              v-for="(av, idx) in currentAvatars"
              :key="idx"
              class="relative aspect-square rounded-xl overflow-hidden border-2 cursor-pointer transition-all hover:scale-105 group"
              :class="selectedAvatarUrl === av ? 'border-[#0D99FF] ring-2 ring-[#0D99FF]/30' : 'border-transparent hover:border-slate-300'"
              title="点击直接填入选中框或插入头像组件"
              @click="onPickAvatar(av)"
            >
              <img :src="av" class="w-full h-full object-cover" />
              <div v-if="selectedAvatarUrl === av" class="absolute inset-0 bg-[#0D99FF]/20 flex items-center justify-center">
                <Check class="w-4 h-4 text-white drop-shadow" />
              </div>
            </div>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点击任意头像直接填入画板 (选中框则替换，未选中则插入)</p>

          <div class="pt-2 border-t border-slate-100 dark:border-[#333] flex flex-col gap-2">
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-[#0D99FF] hover:bg-[#077cd1] text-white font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="applyAvatarToSelection"
            >
              <Image class="w-3.5 h-3.5" />
              <span>填入选中的头像占位框</span>
            </button>
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-slate-100 dark:bg-[#333] hover:bg-slate-200 dark:hover:bg-[#444] text-slate-700 dark:text-slate-200 font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="insertNewAvatarComponent"
            >
              <Plus class="w-3.5 h-3.5" />
              <span>插入全新用户头像卡片</span>
            </button>
          </div>
        </div>

        <!-- 插件 2: Chinese User Data Generator -->
        <div v-else-if="activeRunningPlugin.id === 'chinese-user-generator'" class="space-y-3">
          <div class="flex items-center justify-between text-slate-500">
            <span>当前生成的业务数据预览:</span>
            <button
              type="button"
              class="inline-flex items-center gap-1 text-[#0D99FF] hover:underline font-medium cursor-pointer"
              @click="regenerateUserData"
            >
              <RefreshCw class="w-3 h-3" />
              <span>换一个人</span>
            </button>
          </div>

          <!-- 数据卡片预览 (点击直接插入) -->
          <div
            class="p-3 bg-slate-50 dark:bg-[#1f1f1f] border border-slate-200/80 dark:border-[#333] hover:border-[#0D99FF] rounded-xl space-y-1.5 cursor-pointer transition-all group"
            title="点击直接插入此卡片到画板"
            @click="insertUserCardComponent"
          >
            <div class="flex items-center gap-2.5">
              <img :src="mockUserData.avatarUrl" class="w-10 h-10 rounded-full object-cover border border-slate-200" />
              <div>
                <div class="font-bold text-slate-900 dark:text-white text-sm group-hover:text-[#0D99FF] transition-colors">{{ mockUserData.name }}</div>
                <div class="text-[11px] text-slate-400">{{ mockUserData.title }} · {{ mockUserData.department }}</div>
              </div>
            </div>
            <div class="text-[11px] text-slate-500 space-y-0.5 pt-1.5 border-t border-slate-200/60 dark:border-[#333]">
              <div>手机: <span class="font-mono text-slate-700 dark:text-slate-300">{{ mockUserData.phone }}</span></div>
              <div>邮箱: <span class="font-mono text-slate-700 dark:text-slate-300">{{ mockUserData.email }}</span></div>
              <div>地址: <span class="text-slate-700 dark:text-slate-300">{{ mockUserData.city }}</span></div>
            </div>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点击上方卡片或下方按钮直接插入到画板</p>

          <div class="pt-2 border-t border-slate-100 dark:border-[#333] flex flex-col gap-2">
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-[#0D99FF] hover:bg-[#077cd1] text-white font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="applyTextToSelection"
            >
              <FileText class="w-3.5 h-3.5" />
              <span>将姓名填入选中文字</span>
            </button>
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-slate-100 dark:bg-[#333] hover:bg-slate-200 dark:hover:bg-[#444] text-slate-700 dark:text-slate-200 font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="insertUserCardComponent"
            >
              <Plus class="w-3.5 h-3.5" />
              <span>插入完整个人资料卡片</span>
            </button>
          </div>
        </div>

        <!-- 插件 3: Vector Icon Finder (接入 20万+ 开源矢量图标库生态) -->
        <div v-else-if="activeRunningPlugin.id === 'vector-icon-finder'" class="space-y-2">
          <!-- 智能双语搜索栏 -->
          <div class="relative">
            <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              v-model="iconSearchQuery"
              type="text"
              placeholder="搜索 20万+ 图标..."
              class="w-full h-8 pl-8 pr-7 text-xs bg-slate-100 dark:bg-[#1e1e1e] border-none rounded-lg text-slate-800 dark:text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-1 focus:ring-[#0D99FF]"
            />
            <button
              v-if="iconSearchQuery"
              type="button"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
              title="清空搜索"
              @click="clearIconSearch"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>

          <!-- 分类标签 (未输入时展示，隐藏滚动条更加清爽) -->
          <div v-if="!iconSearchQuery" class="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5 text-[11px]">
            <button
              v-for="cat in iconCategories"
              :key="cat"
              type="button"
              class="px-2.5 py-0.5 rounded-full shrink-0 transition-colors cursor-pointer"
              :class="selectedIconCategory === cat ? 'bg-[#0D99FF] text-white font-medium shadow-sm' : 'bg-slate-100 dark:bg-[#2a2a2a] text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-[#333]'"
              @click="selectedIconCategory = cat"
            >
              {{ cat }}
            </button>
          </div>

          <!-- 热门搜索词快捷胶囊 (输入时快捷切换) -->
          <div v-else class="flex items-center gap-1 overflow-x-auto no-scrollbar py-0.5 text-[10px]">
            <span class="text-slate-400 shrink-0">热搜:</span>
            <button
              v-for="kw in popularKeywords"
              :key="kw"
              type="button"
              class="px-1.5 py-0.5 rounded bg-slate-100 dark:bg-[#2a2a2a] text-slate-500 hover:text-[#0D99FF] shrink-0 cursor-pointer transition-colors"
              @click="onQuickSearchKeyword(kw)"
            >
              {{ kw }}
            </button>
          </div>

          <!-- 状态与来源信息栏 -->
          <div class="flex items-center justify-between text-[10px] text-slate-400 px-0.5">
            <span v-if="isSearchingOnline" class="flex items-center gap-1 text-[#0D99FF]">
              <Loader2 class="w-3 h-3 animate-spin" />
              <span>正在从 200,000+ 开源矢量库检索...</span>
            </span>
            <span v-else-if="iconSearchQuery">
              找到 {{ displayIcons.length }} 个矢量图标
            </span>
            <span v-else>
              {{ selectedIconCategory === '全部' ? '全部图标 (64+ 离线就绪)' : selectedIconCategory }}
            </span>
            <span class="flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
              <Globe class="w-3 h-3" />
              <span>20万+ 全库联网</span>
            </span>
          </div>

          <!-- 图标网格区 (4列紧凑居中卡片，高度协调) -->
          <div class="grid grid-cols-4 gap-1.5 max-h-[270px] overflow-y-auto custom-scrollbar p-0.5">
            <div v-if="displayIcons.length === 0 && !isSearchingOnline" class="col-span-4 py-8 text-center text-xs text-slate-400">
              未找到匹配图标，建议输入如“汽车”、“相机”或英文关键词重试
            </div>
            <button
              v-for="(ico, idx) in displayIcons"
              :key="ico.id || idx"
              type="button"
              class="h-[60px] px-1 py-1 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] hover:bg-[#0D99FF]/5 flex flex-col items-center justify-center gap-1 transition-all cursor-pointer group"
              :title="`${ico.name} · 点击直接插入画板`"
              @click="insertSvgIcon(ico)"
            >
              <div class="w-6 h-6 flex items-center justify-center text-slate-700 dark:text-slate-200 group-hover:text-[#0D99FF] shrink-0">
                <div v-if="ico.svg" v-html="ico.svg" class="w-6 h-6 flex items-center justify-center"></div>
                <img v-else-if="ico.previewUrl" :src="ico.previewUrl" class="w-6 h-6 object-contain" />
              </div>
              <div class="w-full text-center leading-tight">
                <div class="text-[10px] font-medium text-slate-600 dark:text-slate-300 truncate max-w-[70px] mx-auto group-hover:text-[#0D99FF]">
                  {{ ico.name }}
                </div>
                <div v-if="ico.collection" class="text-[8px] text-slate-400 truncate scale-90 -mt-0.5">
                  {{ ico.collection }}
                </div>
              </div>
            </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center pt-0.5">点击任意图标直接插入画板 (支持双向缩放与填色)</p>
        </div>

        <!-- 插件 4: Icon Motion -->
        <div v-else-if="activeRunningPlugin.id === 'icon-motion-effects'" class="space-y-3">
          <div class="text-slate-500">选择要赋予选中的微动效交互:</div>
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="m in motionPresets"
              :key="m.id"
              type="button"
              class="p-2.5 rounded-xl border text-left flex flex-col gap-1 transition-all cursor-pointer"
              :class="selectedMotion === m.id ? 'border-[#0D99FF] bg-[#0D99FF]/10 text-[#0D99FF]' : 'border-slate-200 dark:border-[#383838] hover:border-slate-300 text-slate-700 dark:text-slate-200'"
              @click="onSelectMotion(m.id)"
            >
              <span class="font-bold text-xs">{{ m.name }}</span>
              <span class="text-[10px] text-slate-400">{{ m.desc }}</span>
            </button>
          </div>

          <div class="pt-2 border-t border-slate-100 dark:border-[#333]">
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-[#0D99FF] hover:bg-[#077cd1] text-white font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="applyMotionEffect"
            >
              <Sparkles class="w-3.5 h-3.5" />
              <span>应用动效至选中元素</span>
            </button>
          </div>
        </div>

        <!-- 插件 5: Commercial Image Library (商业高清配图库) -->
        <div v-else-if="activeRunningPlugin.id === 'commercial-image-library'" class="space-y-2.5">
          <!-- 主题分类选择与换一批 -->
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-1 overflow-x-auto no-scrollbar py-0.5 text-[11px]">
              <button
                v-for="cat in ['全部', '科技商务', '建筑空间', '现代生活', '自然风光']"
                :key="cat"
                type="button"
                class="px-2.5 py-0.5 rounded-full shrink-0 transition-colors cursor-pointer"
                :class="selectedPhotoCategory === cat ? 'bg-[#0D99FF] text-white font-medium shadow-sm' : 'bg-slate-100 dark:bg-[#2a2a2a] text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-[#333]'"
                @click="selectedPhotoCategory = cat"
              >
                {{ cat }}
              </button>
            </div>
            <button
              type="button"
              class="inline-flex items-center gap-1 text-[11px] text-[#0D99FF] hover:underline shrink-0 ml-1 cursor-pointer"
              title="调用 Picsum REST API 换一批摄影素材"
              @click="refreshPicsumPhotos"
            >
              <RefreshCw class="w-3 h-3" :class="{ 'animate-spin': isFetchingPhotos }" />
              <span>换一批</span>
            </button>
          </div>

          <!-- 图片卡片网格 -->
          <div class="grid grid-cols-2 gap-2 max-h-[250px] overflow-y-auto custom-scrollbar p-0.5">
            <div
              v-for="img in filteredPhotos"
              :key="img.id"
              class="group relative aspect-[16/10] rounded-xl overflow-hidden border border-slate-200/80 dark:border-[#383838] cursor-pointer hover:border-[#0D99FF] transition-all shadow-sm"
              :class="selectedPhotoUrl === img.url ? 'ring-2 ring-[#0D99FF]' : ''"
              @click="onPickPhoto(img)"
            >
              <img :src="img.url" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
              <div class="absolute inset-0 bg-gradient-to-t from-black/75 via-black/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex flex-col justify-end p-2">
                <span class="text-white text-[10px] font-medium truncate">{{ img.title }}</span>
                <span class="text-white/70 text-[8px] truncate">摄影师: {{ img.author }}</span>
              </div>
              <div v-if="selectedPhotoUrl === img.url" class="absolute top-1.5 right-1.5 w-5 h-5 rounded-full bg-[#0D99FF] text-white flex items-center justify-center shadow">
                <Check class="w-3 h-3" />
              </div>
            </div>
          </div>

          <div class="pt-1.5 border-t border-slate-100 dark:border-[#333] flex flex-col gap-1.5">
            <button
              type="button"
              class="w-full py-1.5 rounded-xl bg-[#0D99FF] hover:bg-[#077cd1] text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="applyPhotoToSelection"
            >
              <Image class="w-3.5 h-3.5" />
              <span>填入选中的图片框 / 容器</span>
            </button>
            <button
              type="button"
              class="w-full py-1.5 rounded-xl bg-slate-100 dark:bg-[#333] hover:bg-slate-200 dark:hover:bg-[#444] text-slate-700 dark:text-slate-200 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="insertPhotoCardComponent"
            >
              <Plus class="w-3.5 h-3.5" />
              <span>插入全新高清画报卡片</span>
            </button>
          </div>
        </div>

        <!-- 插件 6: Business Chart Generator (业务图表与指标卡) -->
        <div v-else-if="activeRunningPlugin.id === 'business-chart-generator'" class="space-y-2">
          <div class="text-[11px] text-slate-500">选择要插入画板的商业 / SaaS 图表组件:</div>
          <div class="space-y-2 max-h-[280px] overflow-y-auto custom-scrollbar p-0.5">
            <div
              v-for="ch in CHART_PRESETS"
              :key="ch.id"
              class="p-2.5 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] hover:bg-slate-50/80 dark:hover:bg-[#252525] flex items-center justify-between cursor-pointer transition-all group"
              @click="insertChartComponent(ch)"
            >
              <div class="flex items-center gap-3">
                <div class="w-16 h-12 rounded-lg bg-slate-100 dark:bg-[#1a1a1a] p-1.5 flex items-center justify-center text-[#0D99FF] shrink-0 border border-slate-200/60 dark:border-[#333]">
                  <div v-html="ch.previewSvg" class="w-full h-full flex items-center justify-center"></div>
                </div>
                <div>
                  <div class="flex items-center gap-1.5">
                    <span class="text-xs font-bold text-slate-800 dark:text-slate-100 group-hover:text-[#0D99FF]">{{ ch.name }}</span>
                    <span class="text-[9px] px-1 py-0.2 rounded bg-slate-100 dark:bg-[#333] text-slate-400">{{ ch.category }}</span>
                  </div>
                  <p class="text-[10px] text-slate-400 mt-0.5">{{ ch.description }}</p>
                </div>
              </div>
              <button
                type="button"
                class="px-2.5 py-1 rounded-lg text-xs font-medium bg-[#0D99FF]/10 text-[#0D99FF] group-hover:bg-[#0D99FF] group-hover:text-white transition-colors shrink-0"
              >
                插入
              </button>
            </div>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点击任意图表直接插入画板 (支持矢量无限缩放与文案编辑)</p>
        </div>

        <!-- 插件 7: Brand & Payment Logos (品牌与支付矢量库) -->
        <div v-else-if="activeRunningPlugin.id === 'brand-payment-logos'" class="space-y-2">
          <!-- 搜索与分类 -->
          <div class="flex items-center gap-1 overflow-x-auto no-scrollbar py-0.5 text-[11px]">
            <button
              v-for="cat in [
                { id: 'all', label: '全部' },
                { id: 'payment', label: '移动支付' },
                { id: 'platform', label: '平台工具' },
                { id: 'tech', label: '开发生态' }
              ]"
              :key="cat.id"
              type="button"
              class="px-2.5 py-0.5 rounded-full shrink-0 transition-colors cursor-pointer"
              :class="selectedBrandCategory === cat.id ? 'bg-[#0D99FF] text-white font-medium shadow-sm' : 'bg-slate-100 dark:bg-[#2a2a2a] text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-[#333]'"
              @click="selectedBrandCategory = cat.id"
            >
              {{ cat.label }}
            </button>
          </div>

          <!-- Logo 4列网格 -->
          <div class="grid grid-cols-4 gap-1.5 max-h-[260px] overflow-y-auto custom-scrollbar p-0.5">
            <button
              v-for="b in filteredBrandLogos"
              :key="b.id"
              type="button"
              class="h-[62px] px-1 py-1 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] hover:bg-[#0D99FF]/5 flex flex-col items-center justify-center gap-1 transition-all cursor-pointer group"
              :title="`${b.name} · 点击插入画板`"
              @click="insertBrandLogo(b)"
            >
              <div class="w-6 h-6 flex items-center justify-center shrink-0">
                <div v-html="b.svg" class="w-6 h-6 flex items-center justify-center"></div>
              </div>
              <span class="text-[10px] font-medium text-slate-600 dark:text-slate-300 truncate max-w-[70px] mx-auto group-hover:text-[#0D99FF] leading-tight">
                {{ b.name }}
              </span>
            </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点击任意 Logo 插入官方纯净矢量图形到画板</p>
        </div>

        <!-- 插件 8: QR & Barcode Generator (动态二维码与条码) -->
        <div v-else-if="activeRunningPlugin.id === 'qr-barcode-generator'" class="space-y-2.5">
          <!-- 类型切换 -->
          <div class="flex items-center gap-1 bg-slate-100 dark:bg-[#222] p-0.5 rounded-lg text-xs font-medium">
            <button
              type="button"
              class="flex-1 py-1 rounded-md transition-all cursor-pointer"
              :class="codeType === 'qrcode' ? 'bg-white dark:bg-[#333] text-slate-900 dark:text-white shadow-sm font-semibold' : 'text-slate-500 hover:text-slate-800'"
              @click="codeType = 'qrcode'"
            >
              二维码 (QR Code)
            </button>
            <button
              type="button"
              class="flex-1 py-1 rounded-md transition-all cursor-pointer"
              :class="codeType === 'barcode' ? 'bg-white dark:bg-[#333] text-slate-900 dark:text-white shadow-sm font-semibold' : 'text-slate-500 hover:text-slate-800'"
              @click="codeType = 'barcode'"
            >
              条形码 (Barcode)
            </button>
          </div>

          <!-- 内容输入框 -->
          <div class="space-y-1">
            <div class="text-[10px] text-slate-400">输入内容或链接 (支持微信/手机直接扫码):</div>
            <input
              v-model="qrContentInput"
              type="text"
              class="w-full h-8 px-2.5 text-xs bg-slate-100 dark:bg-[#1e1e1e] border-none rounded-lg text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-[#0D99FF]"
              placeholder="如 https://wireforge.design 或 WF-88219"
            />
          </div>

          <!-- 快捷预设标签 -->
          <div class="flex items-center gap-1 overflow-x-auto no-scrollbar text-[10px]">
            <span class="text-slate-400 shrink-0">预设:</span>
            <button
              v-for="ps in [
                { label: '原型分享', val: 'https://wireforge.design/preview/project-108' },
                { label: '扫码支付', val: 'https://wireforge.design/pay/order-88219' },
                { label: '核销凭证', val: 'TICKET-2026-9912' },
                { label: 'APP下载', val: 'https://wireforge.design/download' }
              ]"
              :key="ps.label"
              type="button"
              class="px-1.5 py-0.5 rounded bg-slate-100 dark:bg-[#2a2a2a] text-slate-600 dark:text-slate-300 hover:text-[#0D99FF] shrink-0 cursor-pointer"
              @click="qrContentInput = ps.val"
            >
              {{ ps.label }}
            </button>
          </div>

          <!-- 实时扫码预览卡片 -->
          <div class="p-2.5 bg-slate-50 dark:bg-[#1a1a1a] rounded-xl border border-slate-200/80 dark:border-[#333] flex items-center justify-center min-h-[110px]">
            <div v-if="codeType === 'qrcode'" class="flex flex-col items-center gap-1">
              <img :src="currentQrUrl" class="w-24 h-24 rounded-lg bg-white p-1 border border-slate-200 shadow-sm" alt="QR Code" />
              <span class="text-[9px] text-slate-400 font-mono truncate max-w-[200px]">{{ qrContentInput }}</span>
            </div>
            <div v-else class="flex flex-col items-center gap-1 py-1">
              <!-- 条形码模拟 SVG -->
              <div class="bg-white p-2 rounded-lg border border-slate-200 shadow-sm flex flex-col items-center">
                <svg viewBox="0 0 160 45" class="w-36 h-10">
                  <rect x="5" y="0" width="3" height="40" fill="#000"/><rect x="11" y="0" width="1" height="40" fill="#000"/>
                  <rect x="15" y="0" width="4" height="40" fill="#000"/><rect x="22" y="0" width="2" height="40" fill="#000"/>
                  <rect x="27" y="0" width="5" height="40" fill="#000"/><rect x="35" y="0" width="2" height="40" fill="#000"/>
                  <rect x="40" y="0" width="1" height="40" fill="#000"/><rect x="44" y="0" width="4" height="40" fill="#000"/>
                  <rect x="51" y="0" width="3" height="40" fill="#000"/><rect x="57" y="0" width="1" height="40" fill="#000"/>
                  <rect x="61" y="0" width="5" height="40" fill="#000"/><rect x="69" y="0" width="2" height="40" fill="#000"/>
                  <rect x="74" y="0" width="3" height="40" fill="#000"/><rect x="80" y="0" width="1" height="40" fill="#000"/>
                  <rect x="84" y="0" width="4" height="40" fill="#000"/><rect x="91" y="0" width="2" height="40" fill="#000"/>
                  <rect x="96" y="0" width="5" height="40" fill="#000"/><rect x="104" y="0" width="1" height="40" fill="#000"/>
                  <rect x="108" y="0" width="3" height="40" fill="#000"/><rect x="114" y="0" width="4" height="40" fill="#000"/>
                  <rect x="121" y="0" width="2" height="40" fill="#000"/><rect x="126" y="0" width="3" height="40" fill="#000"/>
                  <rect x="132" y="0" width="1" height="40" fill="#000"/><rect x="136" y="0" width="4" height="40" fill="#000"/>
                  <rect x="143" y="0" width="2" height="40" fill="#000"/><rect x="148" y="0" width="4" height="40" fill="#000"/>
                </svg>
                <span class="text-[9px] font-mono tracking-widest text-slate-700 mt-0.5">{{ qrContentInput.toUpperCase().slice(0, 16) }}</span>
              </div>
            </div>
          </div>

          <!-- 插入按钮 -->
          <div class="pt-0.5">
            <button
              type="button"
              class="w-full py-2 rounded-xl bg-[#0D99FF] hover:bg-[#077cd1] text-white text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
              @click="insertCodeComponent"
            >
              <QrCode class="w-3.5 h-3.5" />
              <span>插入{{ codeType === 'qrcode' ? '二维码扫码组件' : '条形码凭据组件' }}</span>
            </button>
          </div>
        </div>

        <!-- 插件 9: 气泡图标 -->
        <div v-else-if="activeRunningPlugin.id === 'game-bubble-icons'" class="space-y-2">
          <div class="flex items-center gap-1 overflow-x-auto no-scrollbar py-0.5 text-[11px]">
            <button
              v-for="cat in ['全部', '表情', '互动', '战斗', '资源']"
              :key="cat"
              type="button"
              class="px-2.5 py-0.5 rounded-full shrink-0 transition-colors cursor-pointer"
              :class="gameIconCategory === cat ? 'bg-[#0D99FF] text-white font-medium shadow-sm' : 'bg-slate-100 dark:bg-[#2a2a2a] text-slate-600 dark:text-slate-300 hover:bg-slate-200'"
              @click="gameIconCategory = cat"
            >
              {{ cat }}
            </button>
          </div>
          <div class="grid grid-cols-4 gap-1.5 max-h-[280px] overflow-y-auto custom-scrollbar p-0.5">
            <button
              v-for="icon in filteredGameIcons"
              :key="icon.id"
              type="button"
              class="h-[64px] rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] hover:bg-[#0D99FF]/5 flex flex-col items-center justify-center gap-1 cursor-pointer"
              :title="`${icon.name} · 点击插入`"
              @click="insertGameBubbleIcon(icon)"
            >
              <div class="w-8 h-8" v-html="icon.svg"></div>
              <span class="text-[10px] text-slate-600 dark:text-slate-300">{{ icon.name }}</span>
            </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点一个放一个，窗口保持打开</p>
        </div>

        <!-- 插件 10: 聊天气泡 -->
        <div v-else-if="activeRunningPlugin.id === 'game-chat-bubbles'" class="space-y-2.5">
          <input
            v-model="gameBubbleText"
            type="text"
            class="w-full h-8 px-2.5 text-xs bg-slate-100 dark:bg-[#1e1e1e] border-none rounded-lg text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-[#0D99FF]"
            placeholder="要放进气泡里的话"
          />
          <div v-for="group in ['外形', '漫画']" :key="group" class="space-y-1">
            <div class="text-[10px] text-slate-400 px-0.5">{{ group }}</div>
            <div class="grid grid-cols-2 gap-1.5">
              <button
                v-for="style in GAME_CHAT_BUBBLE_STYLES.filter((item) => item.group === group)"
                :key="style.id"
                type="button"
                class="h-11 px-2 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] flex items-center gap-2 cursor-pointer text-left"
                @click="insertGameChatBubble(style.id)"
              >
                <span class="w-10 h-7 shrink-0" v-html="style.preview"></span>
                <span class="text-[11px] font-semibold text-slate-700 dark:text-slate-200">{{ style.name }}</span>
              </button>
            </div>
          </div>
          <div class="flex flex-wrap gap-1">
            <button
              v-for="line in GAME_CHAT_PRESETS"
              :key="line"
              type="button"
              class="px-2 py-1 rounded-full bg-slate-100 dark:bg-[#2a2a2a] text-[11px] text-slate-600 dark:text-slate-300 cursor-pointer hover:text-[#0D99FF]"
              @click="gameBubbleText = line"
            >
              {{ line }}
            </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center">先写一句话，再点样式。连续点会往下排开</p>
        </div>

        <!-- 插件 11: 聊天框 -->
        <div v-else-if="activeRunningPlugin.id === 'game-chat-frame'" class="space-y-2">
          <div class="space-y-2 max-h-[320px] overflow-y-auto custom-scrollbar pr-0.5">
          <button
            v-for="msg in GAME_CHAT_MESSAGES"
            :key="msg.id"
            type="button"
            class="w-full text-left p-2.5 rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] bg-slate-50/80 dark:bg-[#1f1f1f] cursor-pointer"
            @click="insertGameChatFrame(msg)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="font-bold text-slate-800 dark:text-slate-100">{{ msg.name }}</span>
              <span class="text-[10px] text-slate-400">{{ msg.role === 'player' ? '玩家' : msg.role === 'npc' ? 'NPC' : '系统' }} · {{ msg.time }}</span>
            </div>
            <div class="mt-1 text-[12px] text-slate-600 dark:text-slate-300 truncate">{{ msg.text }}</div>
          </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点一条插入一整条消息，窗口保持打开</p>
        </div>

        <!-- 插件 12: 道具格 -->
        <div v-else-if="activeRunningPlugin.id === 'game-item-slots'" class="space-y-2">
          <div class="grid grid-cols-3 gap-2 max-h-[320px] overflow-y-auto custom-scrollbar p-0.5">
            <button
              v-for="item in GAME_ITEM_SLOTS"
              :key="item.id"
              type="button"
              class="rounded-xl border border-slate-200/80 dark:border-[#383838] hover:border-[#0D99FF] p-2 flex flex-col items-center gap-1 cursor-pointer"
              @click="insertGameItemSlot(item)"
            >
              <div class="w-12 h-12 rounded-xl flex items-center justify-center" :style="{ background: item.bg, border: `2px solid ${item.border}` }">
                <div class="w-7 h-7" v-html="item.svg"></div>
              </div>
              <span class="text-[11px] font-semibold text-slate-700 dark:text-slate-200">{{ item.name }}</span>
              <span class="text-[10px] text-slate-400">{{ item.rarity }} · {{ item.count }}</span>
            </button>
          </div>
          <p class="text-[10px] text-slate-400 text-center">点一个放一个道具格，窗口保持打开</p>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  Search,
  X,
  ChevronLeft,
  Heart,
  User,
  Play,
  ExternalLink,
  RefreshCw,
  Check,
  Plus,
  Image,
  FileText,
  Sparkles,
  GripVertical,
  Loader2,
  Globe,
  BarChart3,
  CreditCard,
  QrCode,
  Smile,
  MessageCircle,
  Gift,
} from 'lucide-vue-next'
import {
  BUILTIN_PLUGINS,
  PRESET_AVATARS,
  PRESET_ICONS,
  PRESET_COMMERCIAL_IMAGES,
  fetchPicsumPhotos,
  fetchDynamicAvatars,
  CHART_PRESETS,
  PRESET_BRAND_LOGOS,
  generateQrCodeUrl,
  generateRandomUserData,
  searchOnlineIcons,
  fetchCleanSvg,
  GAME_BUBBLE_ICONS,
  GAME_CHAT_PRESETS,
  GAME_CHAT_BUBBLE_STYLES,
  GAME_CHAT_MESSAGES,
  GAME_ITEM_SLOTS,
  gameBubbleIconHtml,
  gameChatBubbleHtml,
  gameChatFrameHtml,
  gameItemSlotHtml,
  type PluginMeta,
  type MockUserData,
  type VectorIconItem,
  type CommercialImageItem,
  type ChartPresetItem,
  type BrandLogoItem,
  type GameBubbleIcon,
  type GameChatMessage,
  type GameItemSlot,
} from '@/services/pluginService'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  currentPageId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'insertComponent', payload: { html: string; name: string; width?: number; height?: number; stackStep?: number }): void
  (e: 'replaceAsset', src: string): void
  (e: 'updateText', text: string): void
  (e: 'applyEffect', effectClass: string): void
}>()

const searchInputRef = ref<HTMLInputElement | null>(null)
const searchQuery = ref('')
const selectedCategory = ref<'all' | 'data' | 'assets' | 'motion' | 'utility'>('all')
const selectedDetailPlugin = ref<PluginMeta | null>(null)
const activeRunningPlugin = ref<PluginMeta | null>(null)

// 浮窗自由拖拽状态 (默认避开右侧属性检查栏)
const runnerPos = ref<{ x: number; y: number } | null>(null)
const isDraggingRunner = ref(false)

const runnerStyle = computed(() => {
  if (runnerPos.value) {
    return {
      left: `${runnerPos.value.x}px`,
      top: `${runnerPos.value.y}px`,
      right: 'auto',
    }
  }
  // 默认避开右侧属性面板 (宽约 280-320px)
  return {
    top: '72px',
    right: '320px',
  }
})

function onRunnerHeaderMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  const target = e.target as HTMLElement
  if (target.closest('button')) return

  isDraggingRunner.value = true
  const runnerEl = document.getElementById('wf-plugin-runner')
  const rect = runnerEl?.getBoundingClientRect()
  const startLeft = rect ? rect.left : (window.innerWidth - 360 - 320)
  const startTop = rect ? rect.top : 72
  const shiftX = e.clientX - startLeft
  const shiftY = e.clientY - startTop

  function onMouseMove(ev: MouseEvent) {
    if (!isDraggingRunner.value) return
    const maxLeft = Math.max(16, window.innerWidth - 380)
    const maxTop = Math.max(16, window.innerHeight - 140)
    const newX = Math.max(16, Math.min(maxLeft, ev.clientX - shiftX))
    const newY = Math.max(16, Math.min(maxTop, ev.clientY - shiftY))
    runnerPos.value = { x: newX, y: newY }
  }

  function onMouseUp() {
    isDraggingRunner.value = false
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

// 插件状态
const recentPlugin = ref<PluginMeta | null>(BUILTIN_PLUGINS[0])
const currentAvatars = ref<string[]>([...PRESET_AVATARS])
const selectedAvatarUrl = ref<string>(PRESET_AVATARS[0])
const selectedAvatarGender = ref<'all' | 'men' | 'women'>('all')
const isFetchingAvatars = ref(false)
const mockUserData = ref<MockUserData>(generateRandomUserData())
const iconSearchQuery = ref('')

const motionPresets = [
  { id: 'wf-motion-breathe', name: '呼吸感 (Breathe)', desc: '柔和放缩与呼吸节奏' },
  { id: 'wf-motion-pulse', name: '脉冲高亮 (Pulse)', desc: '轻量青蓝外发光脉冲' },
  { id: 'wf-motion-spin', name: '平滑旋转 (Spin)', desc: '常速匀速 360° 旋转' },
  { id: 'wf-motion-bounce', name: '轻盈弹跳 (Bounce)', desc: '上下轻微浮动弹跳' },
]
const selectedMotion = ref(motionPresets[0].id)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

const filteredPlugins = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  let list = BUILTIN_PLUGINS
  if (selectedCategory.value !== 'all') {
    list = list.filter((p) => p.category === selectedCategory.value)
  }
  if (!q) return list
  return list.filter((p) => {
    return (
      p.name.toLowerCase().includes(q) ||
      p.subtitle.toLowerCase().includes(q) ||
      p.description.toLowerCase().includes(q) ||
      p.tags.some((t) => t.toLowerCase().includes(q))
    )
  })
})

// 矢量图标生态搜索状态
const selectedIconCategory = ref('全部')
const iconCategories = ['全部', '基础操作', '导航布局', '媒体视听', '安全系统', '电商商务']
const popularKeywords = ['汽车', '相机', '用户', '设置', '搜索', '购物车', '图表', '心形', '通知', '邮件', '网络', '密码锁']

const isSearchingOnline = ref(false)
const onlineIcons = ref<VectorIconItem[]>([])
let searchDebounceTimer: any = null

function onQuickSearchKeyword(kw: string) {
  iconSearchQuery.value = kw
}

function clearIconSearch() {
  iconSearchQuery.value = ''
  onlineIcons.value = []
}

// 监听搜索词输入，防抖并发起联网 Iconify 20万+ 检索
watch(iconSearchQuery, (newVal) => {
  const q = newVal.trim()
  if (!q) {
    onlineIcons.value = []
    isSearchingOnline.value = false
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
    return
  }

  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(async () => {
    isSearchingOnline.value = true
    try {
      const res = await searchOnlineIcons(q, 48)
      if (iconSearchQuery.value.trim() === q) {
        onlineIcons.value = res
      }
    } finally {
      if (iconSearchQuery.value.trim() === q) {
        isSearchingOnline.value = false
      }
    }
  }, 280)
})

const displayIcons = computed<VectorIconItem[]>(() => {
  const q = iconSearchQuery.value.trim().toLowerCase()
  if (!q) {
    if (selectedIconCategory.value === '全部') {
      return PRESET_ICONS
    }
    return PRESET_ICONS.filter((ico) => ico.category === selectedIconCategory.value)
  }

  // 搜索态：结合本地匹配与在线 20万+ 库匹配
  const localMatched = PRESET_ICONS.filter(
    (ico) => ico.name.toLowerCase().includes(q) || (ico.tags && ico.tags.some((t) => t.includes(q)))
  )

  const seen = new Set<string>()
  const merged: VectorIconItem[] = []

  for (const item of localMatched) {
    seen.add(item.name.toLowerCase())
    merged.push(item)
  }

  for (const item of onlineIcons.value) {
    if (!seen.has(item.name.toLowerCase())) {
      seen.add(item.name.toLowerCase())
      merged.push(item)
    }
  }

  return merged
})

function resolveIcon(name: string) {
  switch (name) {
    case 'User':
      return User
    case 'FileText':
      return FileText
    case 'Search':
      return Search
    case 'Sparkles':
      return Sparkles
    case 'Image':
      return Image
    case 'BarChart3':
      return BarChart3
    case 'CreditCard':
      return CreditCard
    case 'QrCode':
      return QrCode
    case 'Smile':
      return Smile
    case 'MessageCircle':
      return MessageCircle
    case 'Gift':
      return Gift
    default:
      return Sparkles
  }
}

function closeModal() {
  emit('update:modelValue', false)
  selectedDetailPlugin.value = null
}

function openPluginDetail(p: PluginMeta) {
  selectedDetailPlugin.value = p
}

function runPlugin(p: PluginMeta) {
  recentPlugin.value = p
  activeRunningPlugin.value = p
  closeModal()
  ElMessage.success(`已启动「${p.name}」：在悬浮窗中直接点击素材即可即时注入画板`)
}

function closeRunner() {
  activeRunningPlugin.value = null
}

// 供外部直接按 ID 启动插件 (右键菜单调用)
function launchPluginDirect(pluginId: string) {
  const target = BUILTIN_PLUGINS.find((p) => p.id === pluginId)
  if (target) {
    runPlugin(target)
  }
}

defineExpose({
  launchPluginDirect,
})

// 头像操作 (接入动态人像库与真实头像池)
async function refreshAvatars() {
  if (isFetchingAvatars.value) return
  isFetchingAvatars.value = true
  try {
    const newAvatars = await fetchDynamicAvatars(12, selectedAvatarGender.value)
    if (newAvatars && newAvatars.length > 0) {
      currentAvatars.value = newAvatars
      selectedAvatarUrl.value = newAvatars[0]
      ElMessage.success('已动态换取全新一批真实头像')
    }
  } catch (e) {
    ElMessage.error('拉取新头像失败，请重试')
  } finally {
    isFetchingAvatars.value = false
  }
}

function onSelectAvatarGender(g: 'all' | 'men' | 'women') {
  selectedAvatarGender.value = g
  refreshAvatars()
}

function onPickAvatar(av: string) {
  selectedAvatarUrl.value = av
  emit('replaceAsset', av)
}

function applyAvatarToSelection() {
  if (!selectedAvatarUrl.value) return
  emit('replaceAsset', selectedAvatarUrl.value)
}

function insertNewAvatarComponent() {
  const html = `
<div class="wf-avatar-box" style="box-sizing: border-box; display: inline-flex; align-items: center; justify-content: center; width: 56px; height: 56px; border-radius: 50%; overflow: hidden; border: 2px solid #ffffff; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); background-color: #f1f5f9;">
  <img src="${selectedAvatarUrl.value}" alt="Avatar" style="width: 100%; height: 100%; object-fit: cover; display: block;" />
</div>
`
  emit('insertComponent', { html, name: '用户头像组件', width: 56, height: 56 })
  closeRunner()
}

// 中文数据操作
function regenerateUserData() {
  mockUserData.value = generateRandomUserData()
}

function applyTextToSelection() {
  emit('updateText', mockUserData.value.name)
  ElMessage.success(`已填入文本: ${mockUserData.value.name}`)
}

function insertUserCardComponent() {
  const u = mockUserData.value
  const html = `
<div class="wf-user-card" style="box-sizing: border-box; display: flex; flex-direction: column; gap: 8px; padding: 14px 16px; background: #ffffff; border-radius: 16px; border: 1px solid rgba(226, 232, 240, 0.9); box-shadow: 0 1px 3px rgba(0,0,0,0.06); width: 280px; height: 148px; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="display: flex; align-items: center; gap: 10px;">
    <img src="${u.avatarUrl}" alt="Avatar" style="width: 40px; height: 40px; min-width: 40px; min-height: 40px; border-radius: 50%; object-fit: cover; border: 1px solid #e2e8f0; display: block; flex-shrink: 0;" />
    <div style="min-width: 0; flex: 1; overflow: hidden;">
      <div class="wf-text" style="font-weight: 700; color: #1e293b; font-size: 14px; line-height: 1.3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: text;">${u.name}</div>
      <div class="wf-text" style="font-size: 11px; color: #64748b; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: text;">${u.title} · ${u.department}</div>
    </div>
  </div>
  <div style="padding-top: 8px; border-top: 1px solid rgba(226, 232, 240, 0.8); display: flex; flex-direction: column; gap: 3px; font-size: 11px; color: #64748b;">
    <div class="wf-text" style="cursor: text; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">手机: <span style="font-family: monospace; color: #334155;">${u.phone}</span></div>
    <div class="wf-text" style="cursor: text; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">邮箱: <span style="font-family: monospace; color: #334155;">${u.email}</span></div>
    <div class="wf-text" style="cursor: text; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">地址: <span style="color: #334155;">${u.city}</span></div>
  </div>
</div>
`
  emit('insertComponent', { html, name: `个人资料卡片 (${u.name})`, width: 280, height: 148 })
  closeRunner()
}

// 矢量图标操作 (支持在线动态拉取与本地即插即用)
async function insertSvgIcon(ico: VectorIconItem) {
  try {
    const rawSvg = await fetchCleanSvg(ico)
    const html = `
<div class="wf-vector-icon inline-flex items-center justify-center p-2 text-slate-800 dark:text-slate-100">
  ${rawSvg}
</div>
`
    emit('insertComponent', { html, name: `矢量图标 (${ico.name})`, width: 40, height: 40 })
    ElMessage.success(`已插入矢量图标: ${ico.name} · ${ico.collection}`)
  } catch (e) {
    ElMessage.error('插入图标失败，请重试')
  }
}

// 动效微交互操作
function onSelectMotion(motionId: string) {
  selectedMotion.value = motionId
  applyMotionEffect()
}

function applyMotionEffect() {
  emit('applyEffect', selectedMotion.value)
}

// ==========================================
// 插件 5: 商业高清配图库状态与操作
// ==========================================
const selectedPhotoCategory = ref('全部')
const commercialPhotos = ref<CommercialImageItem[]>([...PRESET_COMMERCIAL_IMAGES])
const selectedPhotoUrl = ref<string>(PRESET_COMMERCIAL_IMAGES[0]?.url || '')
const isFetchingPhotos = ref(false)
let picsumPage = 1

const filteredPhotos = computed(() => {
  if (selectedPhotoCategory.value === '全部') return commercialPhotos.value
  return commercialPhotos.value.filter((p) => p.category === selectedPhotoCategory.value)
})

function onPickPhoto(img: CommercialImageItem) {
  selectedPhotoUrl.value = img.url
  emit('replaceAsset', img.url)
  ElMessage.success(`已选定配图: ${img.title}`)
}

async function refreshPicsumPhotos() {
  isFetchingPhotos.value = true
  picsumPage++
  try {
    const newPhotos = await fetchPicsumPhotos(picsumPage, 8)
    if (newPhotos.length > 0) {
      commercialPhotos.value = [...newPhotos, ...PRESET_COMMERCIAL_IMAGES]
      selectedPhotoUrl.value = newPhotos[0].url
      ElMessage.success('已从 Picsum 开放 API 拉取新一批摄影素材')
    }
  } finally {
    isFetchingPhotos.value = false
  }
}

function applyPhotoToSelection() {
  emit('replaceAsset', selectedPhotoUrl.value)
  ElMessage.success('已填入选中的图片框或容器')
}

function insertPhotoCardComponent() {
  const url = selectedPhotoUrl.value
  const html = `
<div class="wf-photo-card" style="box-sizing: border-box; border-radius: 16px; overflow: hidden; border: 1px solid rgba(226, 232, 240, 0.9); box-shadow: 0 1px 3px rgba(0,0,0,0.06); background: #ffffff; width: 280px; height: 230px; display: flex; flex-direction: column; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <div style="width: 100%; height: 155px; overflow: hidden; flex-shrink: 0;">
    <img src="${url}" alt="Commercial Photo" style="width: 100%; height: 100%; object-fit: cover; display: block;" />
  </div>
  <div style="padding: 12px; flex: 1; min-height: 0; box-sizing: border-box; display: flex; flex-direction: column; justify-content: center;">
    <div class="wf-text" style="font-size: 12px; font-weight: 700; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: text;">精选商业摄影素材</div>
    <div class="wf-text" style="font-size: 10px; color: #94a3b8; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: text;">免版权商用高清配图 · Unsplash 摄影</div>
  </div>
</div>
`
  emit('insertComponent', { html, name: '商业画报卡片', width: 280, height: 230 })
  closeRunner()
}

// ==========================================
// 插件 6: 业务图表与指标卡操作
// ==========================================
function insertChartComponent(ch: ChartPresetItem) {
  const html = ch.getComponentHtml()
  emit('insertComponent', { html, name: ch.name, width: ch.width, height: ch.height })
  ElMessage.success(`已插入图表组件: ${ch.name}`)
  closeRunner()
}

// ==========================================
// 插件 7: 品牌与支付矢量库操作
// ==========================================
const selectedBrandCategory = ref('all')
const filteredBrandLogos = computed(() => {
  if (selectedBrandCategory.value === 'all') return PRESET_BRAND_LOGOS
  return PRESET_BRAND_LOGOS.filter((b) => b.category === selectedBrandCategory.value)
})

function insertBrandLogo(b: BrandLogoItem) {
  const html = `
<div class="wf-brand-logo inline-flex items-center bg-white rounded-xl border border-slate-200/90 shadow-sm" style="box-sizing: border-box; display: inline-flex; align-items: center; justify-content: flex-start; gap: 10px; padding: 8px 14px; width: 100%; height: 100%;">
  <div class="wf-brand-icon-wrap flex items-center justify-center shrink-0" style="width: 24px; height: 24px; min-width: 0; min-height: 0; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${b.svg}</div>
  <span class="wf-brand-name font-bold text-slate-800" style="font-size: 13px; font-weight: 700; color: #1e293b; white-space: nowrap; line-height: 1.2;">${b.name}</span>
</div>
`
  emit('insertComponent', { html, name: `品牌徽标 (${b.name})`, width: 140, height: 40 })
  ElMessage.success(`已插入官方矢量 Logo: ${b.name}`)
}

// ==========================================
// 插件 8: 动态二维码与条形码操作
// ==========================================
const codeType = ref<'qrcode' | 'barcode'>('qrcode')
const qrContentInput = ref('https://wireforge.design/preview')
const currentQrUrl = computed(() => generateQrCodeUrl(qrContentInput.value, 200))

function insertCodeComponent() {
  let html = ''
  if (codeType.value === 'qrcode') {
    html = `
<div class="wf-qrcode-card" style="box-sizing: border-box; padding: 16px; background: #ffffff; border-radius: 16px; border: 1px solid rgba(226, 232, 240, 0.9); box-shadow: 0 1px 3px rgba(0,0,0,0.06); display: flex; flex-direction: column; align-items: center; text-align: center; width: 180px; height: 210px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <img src="${currentQrUrl.value}" alt="QR Code" style="width: 128px; height: 128px; border-radius: 8px; background: #ffffff; border: 1px solid #f1f5f9; padding: 4px; box-sizing: border-box; display: block;" />
  <div class="wf-text" style="font-size: 12px; font-weight: 700; color: #1e293b; margin-top: 8px; white-space: nowrap;">扫码预览原型</div>
  <div class="wf-text" style="font-size: 9px; color: #94a3b8; margin-top: 2px; white-space: nowrap;">请使用微信或手机扫码</div>
</div>
`
    emit('insertComponent', { html, name: '扫码二维码组件', width: 180, height: 210 })
  } else {
    html = `
<div class="wf-barcode-card" style="box-sizing: border-box; padding: 12px; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.06); display: flex; flex-direction: column; align-items: center; width: 200px; height: 75px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <svg viewBox="0 0 160 45" style="width: 100%; height: 40px; display: block;">
    <rect x="5" y="0" width="3" height="40" fill="#000"/><rect x="11" y="0" width="1" height="40" fill="#000"/>
    <rect x="15" y="0" width="4" height="40" fill="#000"/><rect x="22" y="0" width="2" height="40" fill="#000"/>
    <rect x="27" y="0" width="5" height="40" fill="#000"/><rect x="35" y="0" width="2" height="40" fill="#000"/>
    <rect x="40" y="0" width="1" height="40" fill="#000"/><rect x="44" y="0" width="4" height="40" fill="#000"/>
    <rect x="51" y="0" width="3" height="40" fill="#000"/><rect x="57" y="0" width="1" height="40" fill="#000"/>
    <rect x="61" y="0" width="5" height="40" fill="#000"/><rect x="69" y="0" width="2" height="40" fill="#000"/>
    <rect x="74" y="0" width="3" height="40" fill="#000"/><rect x="80" y="0" width="1" height="40" fill="#000"/>
    <rect x="84" y="0" width="4" height="40" fill="#000"/><rect x="91" y="0" width="2" height="40" fill="#000"/>
    <rect x="96" y="0" width="5" height="40" fill="#000"/><rect x="104" y="0" width="1" height="40" fill="#000"/>
    <rect x="108" y="0" width="3" height="40" fill="#000"/><rect x="114" y="0" width="4" height="40" fill="#000"/>
    <rect x="121" y="0" width="2" height="40" fill="#000"/><rect x="126" y="0" width="3" height="40" fill="#000"/>
    <rect x="132" y="0" width="1" height="40" fill="#000"/><rect x="136" y="0" width="4" height="40" fill="#000"/>
    <rect x="143" y="0" width="2" height="40" fill="#000"/><rect x="148" y="0" width="4" height="40" fill="#000"/>
  </svg>
  <div class="wf-text" style="font-size: 9px; font-family: monospace; letter-spacing: 0.1em; color: #334155; margin-top: 4px; white-space: nowrap;">${qrContentInput.value.slice(0, 16)}</div>
</div>
`
    emit('insertComponent', { html, name: '条形码组件', width: 200, height: 75 })
  }
  ElMessage.success(`已插入${codeType.value === 'qrcode' ? '二维码' : '条形码'}组件`)
  closeRunner()
}

const gameIconCategory = ref('全部')
const gameBubbleText = ref('这个副本我来开')
const filteredGameIcons = computed(() => {
  if (gameIconCategory.value === '全部') return GAME_BUBBLE_ICONS
  return GAME_BUBBLE_ICONS.filter((icon) => icon.category === gameIconCategory.value)
})

function insertGameBubbleIcon(icon: GameBubbleIcon) {
  emit('insertComponent', { html: gameBubbleIconHtml(icon), name: `气泡图标 (${icon.name})`, width: 48, height: 48, stackStep: 56 })
  ElMessage.success(`已插入${icon.name}`)
}

function insertGameChatBubble(styleId: string) {
  const text = gameBubbleText.value.trim() || '……'
  const style = GAME_CHAT_BUBBLE_STYLES.find((item) => item.id === styleId)
  const label = style?.name || '聊天气泡'
  emit('insertComponent', {
    html: gameChatBubbleHtml(styleId, text),
    name: `聊天气泡 (${label})`,
    stackStep: style?.stackStep || 64,
  })
  ElMessage.success(`已插入${label}`)
}

function insertGameChatFrame(message: GameChatMessage) {
  emit('insertComponent', { html: gameChatFrameHtml(message), name: `聊天框 (${message.name})`, width: 280, stackStep: 78 })
  ElMessage.success(`已插入${message.name}的消息`)
}

function insertGameItemSlot(item: GameItemSlot) {
  emit('insertComponent', { html: gameItemSlotHtml(item), name: `道具格 (${item.name})`, width: 64, height: 64, stackStep: 72 })
  ElMessage.success(`已插入${item.name}`)
}
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.4);
  border-radius: 9999px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
