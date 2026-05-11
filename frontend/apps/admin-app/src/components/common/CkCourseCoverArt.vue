<script setup>
import { computed } from 'vue'

import { pickPalette, pickGlyph } from './course-cover-art-model.js'

const props = defineProps({
  seed: { type: String, default: '' },
  label: { type: String, default: '' },
  ariaLabel: { type: String, default: '课程封面' },
})

const palette = computed(() => pickPalette(props.seed || props.label))
const glyph = computed(() => pickGlyph(props.label))
// 单字符（中文/CJK）字号更大，双字符稍小以保持留白
const glyphFontSize = computed(() => (glyph.value.length === 1 ? 220 : 180))
// 为同一封面生成可复用的 gradient id（避免多卡片冲突）
const gradientId = computed(
  () => `ck-cover-grad-${Math.abs(props.seed?.length ?? 0)}-${glyph.value.charCodeAt(0)}`,
)
</script>

<template>
  <svg
    class="ck-course-cover-art"
    viewBox="0 0 960 540"
    role="img"
    :aria-label="ariaLabel"
    preserveAspectRatio="xMidYMid slice"
  >
    <title>{{ ariaLabel }}</title>
    <defs>
      <linearGradient :id="gradientId" x1="0" x2="1" y1="0" y2="1">
        <stop offset="0" :stop-color="palette.bgFrom" />
        <stop offset="1" :stop-color="palette.bgTo" />
      </linearGradient>
    </defs>
    <rect width="960" height="540" :fill="`url(#${gradientId})`" />
    <path
      d="M0 410 C170 350 285 470 455 400 C625 330 740 365 960 292 L960 540 L0 540 Z"
      :fill="palette.plateRing"
      opacity="0.32"
    />
    <path
      d="M0 450 C180 400 330 470 500 425 C680 378 760 425 960 365 L960 540 L0 540 Z"
      :fill="palette.accent"
      opacity="0.16"
    />
    <g transform="translate(300 120)">
      <rect
        x="0"
        y="0"
        width="360"
        height="300"
        rx="28"
        fill="#ffffff"
        :stroke="palette.plateRing"
        stroke-width="4"
      />
      <text
        x="180"
        y="180"
        text-anchor="middle"
        dominant-baseline="central"
        font-family="var(--ckqa-font-display, 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif)"
        :font-size="glyphFontSize"
        font-weight="700"
        :fill="palette.accent"
      >{{ glyph }}</text>
    </g>
    <g :stroke="palette.accent" stroke-width="3" stroke-linecap="round" fill="#ffffff" opacity="0.65">
      <line x1="760" y1="120" x2="820" y2="80" />
      <line x1="820" y1="80" x2="880" y2="130" />
      <line x1="760" y1="120" x2="880" y2="130" />
      <circle cx="760" cy="120" r="9" />
      <circle cx="820" cy="80" r="9" />
      <circle cx="880" cy="130" r="9" />
    </g>
  </svg>
</template>

<style scoped lang="scss">
.ck-course-cover-art {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
