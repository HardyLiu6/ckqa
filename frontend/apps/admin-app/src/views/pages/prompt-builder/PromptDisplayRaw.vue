<script setup>
import { computed } from 'vue'
import Prism from './prompt-display-prism.js'

const props = defineProps({
  text: { type: String, default: '' },
})

const lines = computed(() => {
  const html = Prism.highlight(props.text, Prism.languages['prompt-tune'], 'prompt-tune')
  return html.split(/\r?\n/).map((lineHtml, i) => ({
    no: i + 1,
    html: lineHtml.length === 0 ? '&nbsp;' : lineHtml,
  }))
})
</script>

<template>
  <pre class="prompt-display-raw"><code class="language-prompt-tune"><span
    v-for="line in lines"
    :key="line.no"
    class="prompt-display-raw__line"
  ><span class="prompt-display-raw__lineno">{{ line.no }}</span><span class="prompt-display-raw__content" v-html="line.html"></span></span></code></pre>
</template>

<style scoped>
.prompt-display-raw {
  margin: 0;
  padding: 12px 0;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
.prompt-display-raw__line {
  display: flex;
  align-items: flex-start;
  padding: 0 16px;
  white-space: pre-wrap;
  word-break: break-word;
}
.prompt-display-raw__lineno {
  flex: 0 0 40px;
  color: #6e7681;
  user-select: none;
  text-align: right;
  margin-right: 12px;
}
.prompt-display-raw__content {
  flex: 1;
}
/* 覆盖 prism-tomorrow 主题：保留段落 / 占位 / 关键字 / 箭头颜色 */
.prompt-display-raw :deep(.token.section) {
  color: #98c379;
  font-weight: 600;
}
.prompt-display-raw :deep(.token.placeholder) {
  color: #e5c07b;
  background: rgba(229, 192, 123, 0.12);
  padding: 0 2px;
  border-radius: 2px;
}
.prompt-display-raw :deep(.token.arrow) {
  color: #61afef;
  font-weight: 600;
}
.prompt-display-raw :deep(.token.comment) {
  color: #5c6370;
  font-style: italic;
}
.prompt-display-raw :deep(.token.keyword) {
  color: #c678dd;
}
</style>
