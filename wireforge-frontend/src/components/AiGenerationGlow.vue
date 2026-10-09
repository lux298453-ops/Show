<template>
  <Transition name="ai-glow" @after-leave="onFadeComplete">
    <div
      v-if="active"
      ref="surface"
      class="ai-generation-glow"
      :class="{ 'is-fixed': fixed }"
      :style="originStyle"
      aria-hidden="true"
    >
      <div class="ai-glow-spread">
        <canvas
          ref="inkCanvas"
          class="ai-glow-ink"
          :class="{ 'is-ready': inkReady }"
          @webglcontextlost.prevent="onContextLost"
          @webglcontextrestored="initializeInk"
        ></canvas>
        <div v-if="!inkReady" class="ai-glow-fallback">
          <div class="ai-glow-colors"></div>
          <div class="ai-glow-bloom"></div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { createInkBackground, type InkBackground } from '../utils/inkBackground'
import { isDark } from '../utils/theme'

const props = withDefaults(defineProps<{
  active: boolean
  origin?: HTMLElement | null
  fixed?: boolean
}>(), { origin: null, fixed: false })

const surface = ref<HTMLElement | null>(null)
const inkCanvas = ref<HTMLCanvasElement | null>(null)
const inkReady = ref(false)
const reducedMotion = ref(false)
const originStyle = ref({ '--ai-origin-x': '85%', '--ai-origin-y': '8%' })
const originPoint = ref({ x: 0.85, y: 0.08 })
const spreadDuration = 1.8

let ink: InkBackground | null = null
let resizeObserver: ResizeObserver | null = null
let motionPreference: MediaQueryList | null = null
let animationFrame = 0
let elapsed = 0
let lastTick = 0
let lastDraw = 0

function captureOrigin() {
  const bounds = surface.value?.getBoundingClientRect()
  const source = props.origin?.getBoundingClientRect()
  const clamp = (value: number) => Math.max(0, Math.min(1, value))
  // One generation cycle has one source. Starting another concurrent request
  // must not move an expansion that is already visible.
  originPoint.value = bounds?.width && bounds.height && source ? {
    x: clamp((source.left + source.width / 2 - bounds.left) / bounds.width),
    y: clamp((source.top + source.height / 2 - bounds.top) / bounds.height),
  } : { x: 0.85, y: 0.08 }
  originStyle.value = {
    '--ai-origin-x': `${originPoint.value.x * 100}%`,
    '--ai-origin-y': `${originPoint.value.y * 100}%`,
  }
}

function renderInk() {
  ink?.render(elapsed, isDark.value, {
    progress: reducedMotion.value ? 1 : Math.min(elapsed / spreadDuration, 1),
    origin: originPoint.value,
  })
}

function pauseAnimation() {
  if (animationFrame) cancelAnimationFrame(animationFrame)
  animationFrame = 0
  lastTick = 0
}

function drawFrame(now: number) {
  animationFrame = 0
  if (!ink || !props.active || reducedMotion.value || document.hidden) return
  if (lastTick) elapsed += Math.min((now - lastTick) / 1000, 0.25)
  lastTick = now
  if (now - lastDraw >= 1000 / 24) {
    renderInk()
    lastDraw = now
  }
  animationFrame = requestAnimationFrame(drawFrame)
}

function resumeAnimation() {
  if (!ink || !props.active || reducedMotion.value || document.hidden || animationFrame) return
  lastTick = 0
  lastDraw = 0
  animationFrame = requestAnimationFrame(drawFrame)
}

function releaseInk() {
  pauseAnimation()
  resizeObserver?.disconnect()
  resizeObserver = null
  ink?.dispose()
  ink = null
}

function onFadeComplete() {
  if (!props.active) releaseInk()
}

function initializeInk() {
  if (!props.active || !inkCanvas.value || !surface.value) return
  releaseInk()
  inkReady.value = false
  ink = createInkBackground(inkCanvas.value)
  if (!ink) return
  const resize = () => {
    if (!surface.value || !ink) return
    ink.resize(surface.value.clientWidth, surface.value.clientHeight)
    renderInk()
  }
  resize()
  inkReady.value = true
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(surface.value)
  resumeAnimation()
}

function onContextLost() {
  releaseInk()
  inkReady.value = false
}

function updateMotionPreference() {
  reducedMotion.value = motionPreference?.matches ?? false
}

function onVisibilityChange() {
  if (document.hidden) pauseAnimation()
  else resumeAnimation()
}

watch([() => props.active, inkCanvas], ([active, canvas]) => {
  if (!active || !canvas) {
    // The transition still displays the last frame while fading out.
    pauseAnimation()
    resizeObserver?.disconnect()
    resizeObserver = null
    return
  }
  elapsed = reducedMotion.value ? spreadDuration : 0
  captureOrigin()
  initializeInk()
}, { flush: 'post' })

watch([reducedMotion, isDark], () => {
  // A static preview is already fully revealed; returning to motion should
  // continue the flow instead of replaying the entry and making it disappear.
  if (reducedMotion.value) elapsed = Math.max(elapsed, spreadDuration)
  renderInk()
  if (reducedMotion.value) pauseAnimation()
  else resumeAnimation()
})

onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  updateMotionPreference()
  motionPreference.addEventListener('change', updateMotionPreference)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  releaseInk()
  motionPreference?.removeEventListener('change', updateMotionPreference)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

</script>

<style scoped>
.ai-generation-glow {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  user-select: none;
  --ai-glow-strength: 0.9;
}

.ai-generation-glow.is-fixed {
  position: fixed;
}

.ai-glow-spread {
  position: absolute;
  inset: 0;
  opacity: var(--ai-glow-strength);
}

.ai-glow-fallback {
  position: absolute;
  inset: 0;
  animation: ai-glow-fade 1.8s ease-in-out both;
}

.ai-glow-ink {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  pointer-events: none;
}

.ai-glow-ink.is-ready {
  opacity: 1;
}

/* CSS remains available when the browser cannot draw the pigment field. */
.ai-glow-colors,
.ai-glow-bloom {
  position: absolute;
  inset: -40%;
  pointer-events: none;
}

.ai-glow-colors {
  background:
    radial-gradient(ellipse at 13% 28%, rgb(85 198 236 / 28%), transparent 48%),
    radial-gradient(ellipse at 82% 15%, rgb(161 85 236 / 26%), transparent 48%),
    radial-gradient(ellipse at 93% 82%, rgb(161 85 236 / 18%), transparent 46%),
    radial-gradient(ellipse at 20% 95%, rgb(85 161 236 / 20%), transparent 44%);
  animation: ai-glow-drift 9s ease-in-out infinite alternate;
}

.ai-glow-bloom {
  background:
    radial-gradient(ellipse at 32% 35%, rgb(161 236 85 / 36%), transparent 26%),
    radial-gradient(ellipse at 72% 68%, rgb(85 198 236 / 32%), transparent 30%),
    radial-gradient(ellipse at 65% 100%, rgb(236 85 85 / 8%), transparent 38%);
  animation: ai-glow-flow 12s ease-in-out infinite;
}

:global(html.dark .ai-generation-glow) {
  --ai-glow-strength: 0.55;
}

.ai-glow-enter-active {
  transition: opacity 800ms ease-in-out;
}

.ai-glow-leave-active {
  transition: opacity 900ms ease-out;
}

.ai-glow-enter-from,
.ai-glow-leave-to {
  opacity: 0;
}

@keyframes ai-glow-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes ai-glow-drift {
  from { transform: translate(-9%, -5%) rotate(-4deg) scale(1.12); opacity: 1; }
  to { transform: translate(9%, 6%) rotate(4deg) scale(1.17); opacity: 0.2; }
}

@keyframes ai-glow-flow {
  0%, 100% {
    transform: translate(-20%, 8%) rotate(-7deg) scale(1.08);
    opacity: 0.45;
  }
  33% {
    transform: translate(14%, -12%) rotate(4deg) scale(1.12);
    opacity: 0.85;
  }
  66% {
    transform: translate(22%, 12%) rotate(-2deg) scale(1.08);
    opacity: 0.65;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-glow-fallback,
  .ai-glow-colors,
  .ai-glow-bloom {
    animation: none;
  }

  .ai-glow-bloom {
    opacity: 0.45;
  }

  .ai-glow-enter-active,
  .ai-glow-leave-active {
    transition-duration: 150ms;
  }
}
</style>
