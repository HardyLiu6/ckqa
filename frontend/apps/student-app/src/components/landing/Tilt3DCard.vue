<!-- 鼠标跟踪 3D 倾斜 · 详见设计稿 §7.1 动效 ⑥ -->
<script setup>
import { ref, onMounted } from 'vue'

const cardRef = ref(null)
const rotX = ref(0)
const rotY = ref(0)

const MAX = 8
let isTouch = false

onMounted(() => {
  isTouch = matchMedia('(hover: none)').matches
})

function onMouseMove(e) {
  if (isTouch || !cardRef.value) return
  const rect = cardRef.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width // 0..1
  const y = (e.clientY - rect.top) / rect.height
  rotY.value = (x - 0.5) * 2 * MAX
  rotX.value = -(y - 0.5) * 2 * MAX
}

function onMouseLeave() {
  rotX.value = 0
  rotY.value = 0
}
</script>

<template>
  <div
    ref="cardRef"
    class="tilt-card"
    :style="{ transform: `perspective(1000px) rotateX(${rotX}deg) rotateY(${rotY}deg)` }"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
  >
    <slot />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;

.tilt-card {
  transition: transform $duration-base $ease-snap;
  transform-style: preserve-3d;
  will-change: transform;
}

@media (prefers-reduced-motion: reduce), (hover: none) {
  .tilt-card { transform: none !important; }
}
</style>
