<!--
  深色落地页专用壳 · 走廊 dolly-in 空间过渡
  登录 / 注册 / 忘记密码三页之间切换时：
  - 离开页：向相机推近（translateZ + scale 放大 + blur + 淡出）
  - 进入页：从远处推近落焦（translateZ 负值 + scale 缩小 + blur → 归位）
  - 过渡期间闪现 vignette 暗角，强化纵深感
  - prefers-reduced-motion 下降级为简单淡入淡出
-->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

// 用于控制 vignette 暗角在过渡期间的显隐
const transitioning = ref(false)
const router = useRouter()

// 路由深度映射：用于判断"前进"还是"后退"
// 登录 = 0（前厅），注册 = 1（深处），忘记密码 = 2（侧厅）
const depthMap = {
  '/login': 0,
  '/register': 1,
  '/forgot-password': 2,
}

// 动态决定过渡方向
const transitionName = ref('dolly-forward')

router.beforeEach((to, from) => {
  const toDepth = depthMap[to.path] ?? -1
  const fromDepth = depthMap[from.path] ?? -1
  // 介绍页 → 认证页 或 认证页之间
  if (toDepth >= 0 && fromDepth >= 0) {
    transitionName.value = toDepth > fromDepth ? 'dolly-forward' : 'dolly-backward'
  } else {
    transitionName.value = 'dolly-forward'
  }
})

function onBeforeEnter() {
  transitioning.value = true
}
function onAfterEnter() {
  transitioning.value = false
}
function onBeforeLeave() {
  transitioning.value = true
}
function onAfterLeave() {
  transitioning.value = false
}
</script>

<template>
  <div class="landing-layout">
    <!-- 过渡期间的 vignette 暗角 -->
    <div class="landing-vignette" :class="{ 'landing-vignette--active': transitioning }" aria-hidden="true" />

    <RouterView v-slot="{ Component }">
      <Transition
        :name="transitionName"
        mode="out-in"
        @before-enter="onBeforeEnter"
        @after-enter="onAfterEnter"
        @before-leave="onBeforeLeave"
        @after-leave="onAfterLeave"
      >
        <component :is="Component" />
      </Transition>
    </RouterView>
  </div>
</template>

<style scoped lang="scss">
.landing-layout {
  position: relative;
  min-height: 100vh;
  background: #0f0f1a;
  color: #fff;
  // 为 3D 过渡提供透视容器
  perspective: 1200px;
  perspective-origin: 50% 50%;
  overflow: hidden;
}

// ========== Vignette 暗角 ==========
.landing-vignette {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9999;
  background: radial-gradient(circle at 50% 50%, transparent 30%, rgba(15, 23, 42, 0.65) 80%);
  opacity: 0;
  transition: opacity 400ms ease;
}

.landing-vignette--active {
  opacity: 1;
}

// ========== 走廊 dolly-in 前进（进入更深的房间） ==========
// 离开：向相机推近 → 放大 + 模糊 + 淡出
.dolly-forward-leave-active {
  position: absolute;
  inset: 0;
  z-index: 1;
  transition:
    transform 650ms cubic-bezier(0.65, 0, 0.35, 1),
    opacity 450ms ease,
    filter 450ms ease;
}
.dolly-forward-leave-to {
  transform: translateZ(200px) scale(1.12);
  opacity: 0;
  filter: blur(8px);
}

// 进入：从远处推近落焦
.dolly-forward-enter-active {
  transition:
    transform 700ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 500ms ease,
    filter 500ms ease;
}
.dolly-forward-enter-from {
  transform: translateZ(-300px) scale(0.82);
  opacity: 0;
  filter: blur(10px);
}

// ========== 走廊 dolly-in 后退（退回前厅） ==========
// 离开：向远处退去 → 缩小 + 模糊 + 淡出
.dolly-backward-leave-active {
  position: absolute;
  inset: 0;
  z-index: 1;
  transition:
    transform 650ms cubic-bezier(0.65, 0, 0.35, 1),
    opacity 450ms ease,
    filter 450ms ease;
}
.dolly-backward-leave-to {
  transform: translateZ(-300px) scale(0.82);
  opacity: 0;
  filter: blur(8px);
}

// 进入：从近处退回
.dolly-backward-enter-active {
  transition:
    transform 700ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 500ms ease,
    filter 500ms ease;
}
.dolly-backward-enter-from {
  transform: translateZ(200px) scale(1.12);
  opacity: 0;
  filter: blur(10px);
}

// ========== prefers-reduced-motion 降级 ==========
@media (prefers-reduced-motion: reduce) {
  .dolly-forward-leave-active,
  .dolly-forward-enter-active,
  .dolly-backward-leave-active,
  .dolly-backward-enter-active {
    transition: opacity 200ms ease !important;
  }
  .dolly-forward-leave-to,
  .dolly-forward-enter-from,
  .dolly-backward-leave-to,
  .dolly-backward-enter-from {
    transform: none !important;
    filter: none !important;
  }
  .landing-vignette {
    display: none;
  }
}
</style>
