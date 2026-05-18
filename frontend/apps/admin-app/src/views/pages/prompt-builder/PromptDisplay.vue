<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PromptDisplayRich from './PromptDisplayRich.vue'
import PromptDisplayRaw from './PromptDisplayRaw.vue'
import { parsePromptSections } from './prompt-display-parser.js'

const props = defineProps({
  text: { type: String, required: true },
  defaultMode: { type: String, default: 'rich' },
})

const mode = ref(props.defaultMode)

const fallbackToRaw = computed(() => {
  const sections = parsePromptSections(props.text)
  return sections.length === 1 && sections[0].fallback
})

watch(fallbackToRaw, (val) => {
  if (val && mode.value !== 'raw') {
    mode.value = 'raw'
  }
}, { immediate: true })

const leftPaneRef = ref(null)
const rightPaneRef = ref(null)
let syncing = false

function onLeftScroll() {
  if (mode.value !== 'split' || syncing) return
  const left = leftPaneRef.value
  const right = rightPaneRef.value
  if (!left || !right) return
  const leftMax = left.scrollHeight - left.clientHeight
  const rightMax = right.scrollHeight - right.clientHeight
  if (leftMax <= 0 || rightMax <= 0) return
  syncing = true
  right.scrollTop = (left.scrollTop / leftMax) * rightMax
  requestAnimationFrame(() => { syncing = false })
}

function onRightScroll() {
  if (mode.value !== 'split' || syncing) return
  const left = leftPaneRef.value
  const right = rightPaneRef.value
  if (!left || !right) return
  const leftMax = left.scrollHeight - left.clientHeight
  const rightMax = right.scrollHeight - right.clientHeight
  if (leftMax <= 0 || rightMax <= 0) return
  syncing = true
  left.scrollTop = (right.scrollTop / rightMax) * leftMax
  requestAnimationFrame(() => { syncing = false })
}

async function copyText() {
  try {
    await navigator.clipboard.writeText(props.text)
    ElMessage.success('已复制完整提示词')
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
</script>

<template>
  <article class="prompt-display">
    <header class="prompt-display__head">
      <div class="prompt-display__view-switch">
        <button :class="{ active: mode === 'rich' }" :disabled="fallbackToRaw" @click="mode = 'rich'">仅文档</button>
        <button :class="{ active: mode === 'split' }" :disabled="fallbackToRaw" @click="mode = 'split'">分屏</button>
        <button :class="{ active: mode === 'raw' }" @click="mode = 'raw'">仅原文</button>
      </div>
      <button class="prompt-display__copy" @click="copyText">📋 复制</button>
    </header>

    <div v-if="mode === 'rich'" class="prompt-display__body">
      <PromptDisplayRich :text="text" />
    </div>

    <div v-else-if="mode === 'split'" class="prompt-display__split">
      <div ref="leftPaneRef" class="prompt-display__pane prompt-display__pane--left" @scroll="onLeftScroll">
        <PromptDisplayRich :text="text" />
      </div>
      <div ref="rightPaneRef" class="prompt-display__pane prompt-display__pane--right" @scroll="onRightScroll">
        <PromptDisplayRaw :text="text" />
      </div>
    </div>

    <div v-else-if="mode === 'raw'" class="prompt-display__body">
      <PromptDisplayRaw :text="text" />
    </div>
  </article>
</template>
