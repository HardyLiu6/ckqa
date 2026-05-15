<script setup>
import { computed } from 'vue'

const props = defineProps({
  text: { type: String, required: true },
})

function escapeHtml(s) {
  return s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
}

function highlightLine(line) {
  if (/^-([^-\n][^\n]*?)-\s*$/.test(line)) {
    return `<span class="raw-section">${escapeHtml(line)}</span>`
  }
  let escaped = escapeHtml(line)
  escaped = escaped.replace(/\{[a-zA-Z_][a-zA-Z0-9_]*\}/g, (m) => `<span class="raw-placeholder">${m}</span>`)
  return escaped
}

const lines = computed(() =>
  props.text.split(/\r?\n/).map((line, i) => ({ no: i + 1, html: highlightLine(line) || '&nbsp;' }))
)
</script>

<template>
  <pre class="prompt-display-raw"><span v-for="line in lines" :key="line.no" class="prompt-display-raw__line"><span class="prompt-display-raw__lineno">{{ line.no }}</span><span class="prompt-display-raw__text" v-html="line.html"></span>
</span></pre>
</template>
