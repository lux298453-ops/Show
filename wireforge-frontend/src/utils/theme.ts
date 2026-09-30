import { ref } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'

const THEME_STORAGE_KEY = 'wf_theme'

export const currentTheme = ref<ThemeMode>('system')
export const isDark = ref<boolean>(false)

let mediaQueryListener: ((e: MediaQueryListEvent) => void) | null = null

function updateDom(dark: boolean) {
  isDark.value = dark
  if (dark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

function handleSystemChange(e: MediaQueryListEvent) {
  if (currentTheme.value === 'system') {
    updateDom(e.matches)
  }
}

export function initTheme() {
  const saved = (localStorage.getItem(THEME_STORAGE_KEY) as ThemeMode) || 'system'
  currentTheme.value = saved

  const mql = window.matchMedia('(prefers-color-scheme: dark)')
  if (!mediaQueryListener) {
    mediaQueryListener = (e: MediaQueryListEvent) => handleSystemChange(e)
    if (mql.addEventListener) {
      mql.addEventListener('change', mediaQueryListener)
    } else {
      // legacy support
      mql.addListener(mediaQueryListener)
    }
  }

  if (saved === 'dark') {
    updateDom(true)
  } else if (saved === 'light') {
    updateDom(false)
  } else {
    updateDom(mql.matches)
  }
}

export function setTheme(mode: ThemeMode) {
  currentTheme.value = mode
  localStorage.setItem(THEME_STORAGE_KEY, mode)

  if (mode === 'dark') {
    updateDom(true)
  } else if (mode === 'light') {
    updateDom(false)
  } else {
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    updateDom(mql.matches)
  }
}
