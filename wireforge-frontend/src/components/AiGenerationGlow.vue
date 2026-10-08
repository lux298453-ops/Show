<template>
  <Transition name="ai-glow">
    <div
      v-if="active"
      ref="surface"
      class="ai-generation-glow"
      :class="{ 'is-fixed': fixed }"
      :style="originStyle"
      aria-hidden="true"
    >
      <div class="ai-glow-spread">
        <div class="ai-glow-colors"></div>
        <div class="ai-glow-bloom"></div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, watchEffect } from 'vue'

const props = withDefaults(defineProps<{
  active: boolean
  origin?: HTMLElement | null
  fixed?: boolean
}>(), { origin: null, fixed: false })

const surface = ref<HTMLElement | null>(null)
const originStyle = ref({ '--ai-origin-x': '85%', '--ai-origin-y': '8%' })

watchEffect(() => {
  if (!props.active || !surface.value || !props.origin) return
  const bounds = surface.value.getBoundingClientRect()
  const source = props.origin.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  const clamp = (value: number) => Math.max(0, Math.min(100, value))
  originStyle.value = {
    '--ai-origin-x': `${clamp((source.left + source.width / 2 - bounds.left) / bounds.width * 100)}%`,
    '--ai-origin-y': `${clamp((source.top + source.height / 2 - bounds.top) / bounds.height * 100)}%`,
  }
}, { flush: 'post' })
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
  transform-origin: var(--ai-origin-x) var(--ai-origin-y);
  animation: ai-glow-spread 1.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

/* Soft gradients supply the blur; only opacity and transform move during generation. */
.ai-glow-colors,
.ai-glow-bloom {
  position: absolute;
  inset: -22%;
  pointer-events: none;
}

.ai-glow-colors {
  background:
    radial-gradient(ellipse at 13% 28%, rgb(161 236 85 / 22%), transparent 48%),
    radial-gradient(ellipse at 82% 15%, rgb(85 198 236 / 26%), transparent 48%),
    radial-gradient(ellipse at 93% 82%, rgb(161 85 236 / 18%), transparent 46%),
    radial-gradient(ellipse at 20% 95%, rgb(85 161 236 / 20%), transparent 44%);
  animation: ai-glow-drift 9s ease-in-out infinite alternate;
}

.ai-glow-bloom {
  background:
    radial-gradient(ellipse at 32% 35%, rgb(85 198 236 / 36%), transparent 20%),
    radial-gradient(ellipse at 72% 68%, rgb(161 85 236 / 26%), transparent 22%),
    radial-gradient(ellipse at 65% 100%, rgb(236 85 85 / 8%), transparent 38%);
  animation: ai-glow-flow 12s ease-in-out infinite;
}

:global(html.dark .ai-generation-glow) {
  --ai-glow-strength: 0.55;
}

.ai-glow-enter-active {
  transition: opacity 800ms ease-out;
}

.ai-glow-leave-active {
  transition: opacity 900ms ease-out;
}

.ai-glow-enter-from,
.ai-glow-leave-to {
  opacity: 0;
}

@keyframes ai-glow-spread {
  from { transform: scale(0.3); }
  to { transform: scale(1); }
}

@keyframes ai-glow-drift {
  from { transform: translate(-9%, -5%) rotate(-4deg) scale(1.12); }
  to { transform: translate(9%, 6%) rotate(4deg) scale(1.17); }
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
  .ai-glow-spread,
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
