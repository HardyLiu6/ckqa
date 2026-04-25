<!-- 磁吸按钮 + 点击涟漪 · 详见设计稿 §7.1 动效 ③ -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { computeMagneticOffset } from '@/utils/magnetic'

defineProps({
  variant: { type: String, default: 'primary' }, // 'primary' | 'secondary'
})

const emit = defineEmits(['click'])

const btnRef = ref(null)
const offsetX = ref(0)
const offsetY = ref(0)
const ripples = ref([])
let rippleId = 0

// prefers-reduced-motion 检测
const prefersReduced = ref(false)

function updateMotionPreference() {
  prefersReduced.value = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

function onMouseMove(e) {
  if (prefersReduced.value || !btnRef.value) return
  const rect = btnRef.value.getBoundingClientRect()
  const center = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
  const offset = computeMagneticOffset({
    cursor: { x: e.clientX, y: e.clientY },
    center,
    radius: 80, // 外扩吸附圆
    maxShift: 8,
  })
  offsetX.value = offset.x
  offsetY.value = offset.y
}

function onMouseLeave() {
  offsetX.value = 0
  offsetY.value = 0
}

function onClick(e) {
  if (!prefersReduced.value && btnRef.value) {
    const rect = btnRef.value.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const id = ++rippleId
    ripples.value.push({ id, x, y })
    setTimeout(() => {
      ripples.value = ripples.value.filter((r) => r.id !== id)
    }, 500)
  }
  emit('click', e)
}

onMounted(() => {
  updateMotionPreference()
  const mq = window.matchMedia?.('(prefers-reduced-motion: reduce)')
  mq?.addEventListener?.('change', updateMotionPreference)
  // 监听整个 window 的 mousemove 以实现"鼠标靠近就吸附"
  window.addEventListener('mousemove', onMouseMove, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
})
</script>

<template>
  <button
    ref="btnRef"
    :class="['magnetic-btn', `variant-${variant}`]"
    :style="{ transform: `translate(${offsetX}px, ${offsetY}px)` }"
    @mouseleave="onMouseLeave"
    @click="onClick"
  >
    <slot />
    <span
      v-for="r in ripples"
      :key="r.id"
      class="ripple"
      :style="{ left: r.x + 'px', top: r.y + 'px' }"
    ></span>
  </button>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.magnetic-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 28px;
  font-family: inherit;
  font-weight: 600;
  font-size: 15px;
  border: 0;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: transform $duration-base $ease-spring, box-shadow $duration-fast $ease-out;
}

.variant-primary {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.4);

  &:hover {
    box-shadow: 0 12px 40px rgba(99, 102, 241, 0.55), 0 0 0 3px rgba(99, 102, 241, 0.2);
  }
}

.variant-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.15);

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }
}

.ripple {
  position: absolute;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: ripple-anim 500ms $ease-out forwards;
}

@keyframes ripple-anim {
  0% { width: 0; height: 0; opacity: 0.5; }
  100% { width: 300px; height: 300px; opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .magnetic-btn { transform: none !important; }
  .ripple { display: none; }
}
</style>
