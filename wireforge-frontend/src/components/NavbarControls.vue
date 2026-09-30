<template>
  <div class="flex items-center gap-2" ref="containerRef">
    <!-- ===== 1. Language Toggle Button ===== -->
    <div class="relative">
      <button
        class="w-8 h-8 rounded-xl flex items-center justify-center transition-all cursor-pointer select-none"
        :class="
          isLangOpen
            ? 'bg-emerald-50 dark:bg-[#383838] border border-emerald-200 dark:border-[#0d99ff] text-emerald-700 dark:text-[#0d99ff] shadow-xs ring-2 ring-emerald-500/20 dark:ring-[#0d99ff]/20'
            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/90 dark:text-slate-200 dark:hover:text-white dark:hover:bg-[#444444] border border-slate-200/80 dark:border-[#484848] bg-white/70 dark:bg-[#383838] shadow-2xs'
        "
        :title="t('language')"
        @click.stop="toggleLang"
      >
        <Globe class="w-4 h-4" />
      </button>

      <!-- Language Popover Dropdown (1:1 对标用户参考图 1) -->
      <transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="transform scale-95 opacity-0 -translate-y-1"
        enter-to-class="transform scale-100 opacity-100 translate-y-0"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="transform scale-100 opacity-100 translate-y-0"
        leave-to-class="transform scale-95 opacity-0 -translate-y-1"
      >
        <div
          v-if="isLangOpen"
          class="absolute right-0 top-10 w-44 bg-white dark:bg-[#2c2c2c] border border-slate-200/90 dark:border-[#383838] rounded-2xl shadow-xl dark:shadow-2xl dark:shadow-black/80 p-1.5 z-50 space-y-0.5"
          @click.stop
        >
          <div class="px-2.5 pt-1.5 pb-1 text-[11px] text-slate-400 dark:text-[#a1a1a1] font-medium select-none">
            {{ t('language') }}
          </div>

          <button
            v-for="item in languageOptions"
            :key="item.code"
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-xs font-medium cursor-pointer transition-colors text-left"
            :class="
              currentLang === item.code
                ? 'bg-emerald-50 dark:bg-[#383838] text-emerald-800 dark:text-[#0d99ff] font-semibold border border-emerald-200/60 dark:border-[#0d99ff]/40'
                : 'text-slate-700 dark:text-slate-200 hover:bg-slate-100/80 dark:hover:bg-[#383838] border border-transparent'
            "
            @click="selectLang(item.code)"
          >
            <div class="flex items-center gap-2.5">
              <span class="text-sm leading-none">{{ item.flag }}</span>
              <span>{{ item.label }}</span>
            </div>
            <Check
              v-if="currentLang === item.code"
              class="w-3.5 h-3.5 text-emerald-600 dark:text-[#0d99ff] stroke-[2.5]"
            />
          </button>
        </div>
      </transition>
    </div>

    <!-- ===== 2. Appearance / Theme Toggle Button ===== -->
    <div class="relative">
      <button
        class="w-8 h-8 rounded-xl flex items-center justify-center transition-all cursor-pointer select-none"
        :class="
          isThemeOpen
            ? 'bg-emerald-50 dark:bg-[#383838] border border-emerald-200 dark:border-[#0d99ff] text-emerald-700 dark:text-[#0d99ff] shadow-xs ring-2 ring-emerald-500/20 dark:ring-[#0d99ff]/20'
            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/90 dark:text-slate-200 dark:hover:text-white dark:hover:bg-[#444444] border border-slate-200/80 dark:border-[#484848] bg-white/70 dark:bg-[#383838] shadow-2xs'
        "
        :title="t('appearance')"
        @click.stop="toggleTheme"
      >
        <!-- Icon reflecting current mode -->
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 3v18a9 9 0 0 0 0-18z" fill="currentColor" />
        </svg>
      </button>

      <!-- Theme Popover Dropdown (1:1 对标用户参考图 2) -->
      <transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="transform scale-95 opacity-0 -translate-y-1"
        enter-to-class="transform scale-100 opacity-100 translate-y-0"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="transform scale-100 opacity-100 translate-y-0"
        leave-to-class="transform scale-95 opacity-0 -translate-y-1"
      >
        <div
          v-if="isThemeOpen"
          class="absolute right-0 top-10 w-44 bg-white dark:bg-[#2c2c2c] border border-slate-200/90 dark:border-[#383838] rounded-2xl shadow-xl dark:shadow-2xl dark:shadow-black/80 p-1.5 z-50 space-y-0.5"
          @click.stop
        >
          <div class="px-2.5 pt-1.5 pb-1 text-[11px] text-slate-400 dark:text-[#a1a1a1] font-medium select-none">
            {{ t('appearance') }}
          </div>

          <!-- Option 1: 跟随系统 -->
          <button
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-xs font-medium cursor-pointer transition-colors text-left"
            :class="
              currentTheme === 'system'
                ? 'bg-emerald-50 dark:bg-[#383838] text-emerald-800 dark:text-[#0d99ff] font-semibold border border-emerald-200/60 dark:border-[#0d99ff]/40'
                : 'text-slate-700 dark:text-slate-200 hover:bg-slate-100/80 dark:hover:bg-[#383838] border border-transparent'
            "
            @click="selectTheme('system')"
          >
            <div class="flex items-center gap-2.5">
              <svg class="w-4 h-4 text-slate-500 dark:text-slate-400" :class="{ 'text-emerald-700 dark:text-[#0d99ff]': currentTheme === 'system' }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 3v18a9 9 0 0 0 0-18z" fill="currentColor" />
              </svg>
              <span>{{ t('system') }}</span>
            </div>
            <Check
              v-if="currentTheme === 'system'"
              class="w-3.5 h-3.5 text-emerald-600 dark:text-[#0d99ff] stroke-[2.5]"
            />
          </button>

          <!-- Option 2: 浅色 -->
          <button
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-xs font-medium cursor-pointer transition-colors text-left"
            :class="
              currentTheme === 'light'
                ? 'bg-emerald-50 dark:bg-[#383838] text-emerald-800 dark:text-[#0d99ff] font-semibold border border-emerald-200/60 dark:border-[#0d99ff]/40'
                : 'text-slate-700 dark:text-slate-200 hover:bg-slate-100/80 dark:hover:bg-[#383838] border border-transparent'
            "
            @click="selectTheme('light')"
          >
            <div class="flex items-center gap-2.5">
              <Sun class="w-4 h-4 text-slate-500 dark:text-slate-400" :class="{ 'text-emerald-700 dark:text-[#0d99ff]': currentTheme === 'light' }" />
              <span>{{ t('light') }}</span>
            </div>
            <Check
              v-if="currentTheme === 'light'"
              class="w-3.5 h-3.5 text-emerald-600 dark:text-[#0d99ff] stroke-[2.5]"
            />
          </button>

          <!-- Option 3: 深色 -->
          <button
            class="w-full flex items-center justify-between px-2.5 py-2 rounded-xl text-xs font-medium cursor-pointer transition-colors text-left"
            :class="
              currentTheme === 'dark'
                ? 'bg-emerald-50 dark:bg-[#383838] text-emerald-800 dark:text-[#0d99ff] font-semibold border border-emerald-200/60 dark:border-[#0d99ff]/40'
                : 'text-slate-700 dark:text-slate-200 hover:bg-slate-100/80 dark:hover:bg-[#383838] border border-transparent'
            "
            @click="selectTheme('dark')"
          >
            <div class="flex items-center gap-2.5">
              <Moon class="w-4 h-4 text-slate-500 dark:text-slate-400" :class="{ 'text-emerald-700 dark:text-[#0d99ff]': currentTheme === 'dark' }" />
              <span>{{ t('dark') }}</span>
            </div>
            <Check
              v-if="currentTheme === 'dark'"
              class="w-3.5 h-3.5 text-emerald-600 dark:text-[#0d99ff] stroke-[2.5]"
            />
          </button>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Globe, Sun, Moon, Check } from 'lucide-vue-next'
import { currentLang, languageOptions, setLanguage, t, type LanguageCode } from '@/utils/i18n'
import { currentTheme, setTheme, type ThemeMode } from '@/utils/theme'

const isLangOpen = ref(false)
const isThemeOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

function toggleLang() {
  isLangOpen.value = !isLangOpen.value
  if (isLangOpen.value) {
    isThemeOpen.value = false
  }
}

function toggleTheme() {
  isThemeOpen.value = !isThemeOpen.value
  if (isThemeOpen.value) {
    isLangOpen.value = false
  }
}

function selectLang(code: LanguageCode) {
  setLanguage(code)
  isLangOpen.value = false
}

function selectTheme(mode: ThemeMode) {
  setTheme(mode)
  isThemeOpen.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    isLangOpen.value = false
    isThemeOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>
