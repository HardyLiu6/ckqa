<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

import { renderQaMarkdown } from './qa-markdown-renderer'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
  streaming: {
    type: Boolean,
    default: false,
  },
})

const html = ref('')
let renderFrameId = 0

watch(
  () => [props.content, props.streaming],
  () => {
    scheduleRender()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  cancelScheduledRender()
})

function scheduleRender() {
  if (props.streaming && typeof window !== 'undefined' && window.requestAnimationFrame) {
    cancelScheduledRender()
    renderFrameId = window.requestAnimationFrame(() => {
      renderFrameId = 0
      renderNow()
    })
    return
  }
  cancelScheduledRender()
  renderNow()
}

function renderNow() {
  html.value = renderQaMarkdown(props.content, { streaming: props.streaming })
}

function cancelScheduledRender() {
  if (!renderFrameId || typeof window === 'undefined' || !window.cancelAnimationFrame) {
    renderFrameId = 0
    return
  }
  window.cancelAnimationFrame(renderFrameId)
  renderFrameId = 0
}
</script>

<template>
  <div class="qa-markdown-content" v-html="html"></div>
</template>

<style scoped lang="scss">
.qa-markdown-content {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
}

.qa-markdown-content :deep(*) {
  box-sizing: border-box;
}

.qa-markdown-content :deep(:first-child) {
  margin-top: 0;
}

.qa-markdown-content :deep(:last-child) {
  margin-bottom: 0;
}

.qa-markdown-content :deep(h1),
.qa-markdown-content :deep(h2),
.qa-markdown-content :deep(h3),
.qa-markdown-content :deep(h4) {
  margin: 14px 0 8px;
  color: #0f172a;
  font-weight: 850;
  letter-spacing: 0;
  line-height: 1.32;
}

.qa-markdown-content :deep(h1) {
  font-size: 18px;
}

.qa-markdown-content :deep(h2) {
  font-size: 16px;
}

.qa-markdown-content :deep(h3),
.qa-markdown-content :deep(h4) {
  font-size: 15px;
}

.qa-markdown-content :deep(p) {
  margin: 8px 0;
}

.qa-markdown-content :deep(ul),
.qa-markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.qa-markdown-content :deep(li) {
  margin: 3px 0;
  padding-left: 2px;
}

.qa-markdown-content :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid rgba(13, 148, 136, 0.65);
  background: rgba(13, 148, 136, 0.07);
  color: #334155;
}

.qa-markdown-content :deep(code) {
  border-radius: 5px;
  background: rgba(15, 23, 42, 0.07);
  color: #334155;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 0.92em;
  padding: 0.12em 0.35em;
}

.qa-markdown-content :deep(pre) {
  margin: 10px 0;
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 8px;
  background: #0f172a;
  padding: 12px;
}

.qa-markdown-content :deep(pre code) {
  display: block;
  min-width: max-content;
  border-radius: 0;
  background: transparent;
  color: #e2e8f0;
  padding: 0;
}

.qa-markdown-content :deep(a) {
  color: #2563eb;
  font-weight: 700;
  text-decoration: none;
}

.qa-markdown-content :deep(a:hover),
.qa-markdown-content :deep(a:focus-visible) {
  text-decoration: underline;
}

.qa-markdown-content :deep(.qa-source-marker) {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  margin: 0 2px;
  padding: 1px 7px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 750;
  line-height: 1.4;
  white-space: nowrap;
}
</style>
