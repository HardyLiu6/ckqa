<script setup>
// SbIcon —— sidebar v3 统一图标组件。
// 视觉打磨迭代（2026-05-09）新增。
// 全部使用 24×24 viewBox / stroke-width:1.6 / currentColor 的线性图标，
// 避免相对弧线指令（`a...`）在主流浏览器 stroke 出现细线断点。

defineProps({
  name: { type: String, required: true },
})
</script>

<template>
  <!-- 田字格仪表盘 -->
  <svg
    v-if="name === 'dashboard'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <rect x="3" y="3" width="8" height="8" rx="1.5" />
    <rect x="13" y="3" width="8" height="8" rx="1.5" />
    <rect x="3" y="13" width="8" height="8" rx="1.5" />
    <rect x="13" y="13" width="8" height="8" rx="1.5" />
  </svg>

  <!-- 闭合书本 + 内文横线 -->
  <svg
    v-else-if="name === 'book'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M5 4 L5 20 L19 20 L19 4 Z" />
    <line x1="5" y1="8" x2="19" y2="8" />
    <line x1="8" y1="12" x2="16" y2="12" />
    <line x1="8" y1="15.5" x2="14" y2="15.5" />
  </svg>

  <!-- 数据库圆柱：用椭圆 + 二次贝塞尔绘制上下弧（避免相对弧线渲染断点） -->
  <svg
    v-else-if="name === 'database'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <ellipse cx="12" cy="5" rx="7" ry="2.5" />
    <path d="M5 5 L5 12 C5 13.38 8.13 14.5 12 14.5 C15.87 14.5 19 13.38 19 12 L19 5" />
    <path d="M5 12 L5 19 C5 20.38 8.13 21.5 12 21.5 C15.87 21.5 19 20.38 19 19 L19 12" />
  </svg>

  <!-- 对话气泡 -->
  <svg
    v-else-if="name === 'chat'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M4 5 L4 17 L8 17 L8 20 L12 17 L20 17 L20 5 Z" />
  </svg>

  <!-- 横线列表：检索日志 -->
  <svg
    v-else-if="name === 'list'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <line x1="6" y1="6" x2="18" y2="6" />
    <line x1="6" y1="12" x2="18" y2="12" />
    <line x1="6" y1="18" x2="18" y2="18" />
    <circle cx="4" cy="6" r="0.8" fill="currentColor" />
    <circle cx="4" cy="12" r="0.8" fill="currentColor" />
    <circle cx="4" cy="18" r="0.8" fill="currentColor" />
  </svg>

  <!-- 盾牌 + 对勾：知识库验证 -->
  <svg
    v-else-if="name === 'shield'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M12 3 L19 6 L19 12 C19 16 15.5 19.5 12 21 C8.5 19.5 5 16 5 12 L5 6 Z" />
    <path d="M9 12 L11 14 L15 10" />
  </svg>

  <!-- 双人：用户与权限 -->
  <svg
    v-else-if="name === 'users'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <circle cx="9" cy="8" r="3" />
    <circle cx="16.5" cy="9" r="2.5" />
    <path d="M3.5 19 C3.5 15.5 6 13 9 13 C12 13 14.5 15.5 14.5 19" />
    <path d="M14.5 19 C14.5 16.5 16 14.5 18 14.5 C19.5 14.5 20.5 16 20.5 18" />
  </svg>

  <!-- 心电波形：系统健康 -->
  <svg
    v-else-if="name === 'heart'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M3 12 L8 12 L10 7 L13 17 L15 12 L21 12" />
  </svg>

  <!-- 文件 + 横线：审计日志 -->
  <svg
    v-else-if="name === 'file'"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M7 3 L14 3 L18 7 L18 21 L7 21 Z" />
    <path d="M14 3 L14 7 L18 7" />
    <line x1="9.5" y1="12" x2="15.5" y2="12" />
    <line x1="9.5" y1="15.5" x2="15.5" y2="15.5" />
    <line x1="9.5" y1="18.5" x2="13" y2="18.5" />
  </svg>

  <!-- 兜底（未知 name） -->
  <svg
    v-else
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="4" />
  </svg>
</template>

<style scoped>
svg {
  width: 1em;
  height: 1em;
  transition: stroke-width var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
</style>
