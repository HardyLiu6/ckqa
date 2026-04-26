<script setup>
import { Monitor, Moon, Sun } from 'lucide-vue-next'

import { THEME_ACCENTS, themeStore } from '../../stores/theme.js'

const modeOptions = [
  { key: 'light', label: '亮色', icon: Sun },
  { key: 'dark', label: '暗色', icon: Moon },
  { key: 'auto', label: '跟随系统', icon: Monitor },
]
</script>

<template>
  <div class="theme-control" aria-label="主题设置">
    <div class="theme-segment" role="group" aria-label="显示模式">
      <button
        v-for="mode in modeOptions"
        :key="mode.key"
        class="theme-button"
        type="button"
        :aria-label="mode.label"
        :aria-pressed="themeStore.state.mode === mode.key"
        :title="mode.label"
        @click="themeStore.setMode(mode.key)"
      >
        <component :is="mode.icon" :size="16" aria-hidden="true" />
      </button>
    </div>

    <div class="theme-swatches" role="group" aria-label="主题色">
      <button
        v-for="accent in THEME_ACCENTS"
        :key="accent.key"
        class="swatch-button"
        type="button"
        :aria-label="`主题色 ${accent.label}`"
        :aria-pressed="themeStore.state.accent === accent.key"
        :title="accent.label"
        @click="themeStore.setAccent(accent.key)"
      >
        <span class="swatch-dot" :style="{ '--swatch-color': accent.strong }" />
      </button>
    </div>
  </div>
</template>
