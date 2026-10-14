import { onUnmounted, watch } from 'vue'

const defaultTitle = document.title

/** 浏览器标签名带上项目名，同时开几个项目时能分清；离开页面恢复默认标题 */
export function useProjectTitle(name: () => string | null | undefined) {
  watch(name, (n) => {
    document.title = n ? `${n} · WireForge` : defaultTitle
  }, { immediate: true })
  onUnmounted(() => {
    document.title = defaultTitle
  })
}
