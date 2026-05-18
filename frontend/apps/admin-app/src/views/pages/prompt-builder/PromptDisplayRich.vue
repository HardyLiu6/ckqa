<script setup>
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { parsePromptSections, resolveSectionMeta } from './prompt-display-parser.js'

const props = defineProps({
  text: { type: String, required: true },
})

const sections = computed(() => parsePromptSections(props.text))
const md = new MarkdownIt({ html: false, breaks: false, linkify: false })

function renderBody(body) {
  const tokenMap = new Map()
  let counter = 0
  const escaped = body.replace(/\{[a-zA-Z_][a-zA-Z0-9_]*\}/g, (match) => {
    const token = `\uE000PH${counter++}\uE001`
    tokenMap.set(token, match)
    return token
  })
  let html = md.render(escaped)
  for (const [token, original] of tokenMap) {
    html = html.replaceAll(token, `<mark class="prompt-display-placeholder">${original}</mark>`)
  }
  return html
}

const localCollapsed = ref({})

function toggleSection(idx) {
  localCollapsed.value = { ...localCollapsed.value, [idx]: !localCollapsed.value[idx] }
}
</script>

<template>
  <article class="prompt-display-rich">
    <section v-for="(section, idx) in sections" :key="idx" class="prompt-display-rich__section" :class="{ 'is-fallback': section.fallback }">
      <header class="prompt-display-rich__head" @click="toggleSection(idx)">
        <div class="prompt-display-rich__title">
          <span class="prompt-display-rich__icon">{{ resolveSectionMeta(section.title).icon }}</span>
          <div>
            <strong>{{ resolveSectionMeta(section.title).alias }}</strong>
            <small v-if="!section.fallback">原文标题 {{ section.title }}</small>
          </div>
        </div>
        <span class="prompt-display-rich__toggle">{{ localCollapsed[idx] ? '展开 ▾' : '收起 ▴' }}</span>
      </header>
      <div v-if="!localCollapsed[idx]" class="prompt-display-rich__body" v-html="renderBody(section.body)" />
    </section>
  </article>
</template>
