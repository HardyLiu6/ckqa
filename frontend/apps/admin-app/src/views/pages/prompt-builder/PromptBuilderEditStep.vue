<script setup>
import { computed, ref } from 'vue'
import { utf8ByteLength, formatBytes } from './byte-counter.js'

const props = defineProps({
  extractGraphContent: { type: String, default: '' },
  templateContent: { type: String, default: '' },
  maxBytes: { type: Number, default: 32 * 1024 },
})

const emit = defineEmits(['update:extract-graph-content'])

const lockedExpanded = ref(false)

const LOCKED_PROMPTS = [
  { name: '描述总结提示词',   file: 'summarize_descriptions.txt' },
  { name: '社区报告 · 图',    file: 'community_report_graph.txt' },
  { name: '社区报告 · 文',    file: 'community_report_text.txt' },
  { name: '声明抽取提示词',   file: 'extract_claims.txt' },
]

const PLACEHOLDER_VARS = ['{entity_types}', '{tuple_delimiter}', '{language}']

const byteCount = computed(() => utf8ByteLength(props.extractGraphContent))
const overLimit = computed(() => byteCount.value > props.maxBytes)
const isModified = computed(() => props.extractGraphContent !== props.templateContent)

function handleInput(event) {
  emit('update:extract-graph-content', event.target.value)
}

function restoreTemplate() {
  emit('update:extract-graph-content', props.templateContent)
}
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>分块编辑</h3>
      <p>本期开放编辑「实体抽取提示词」。其余提示词调优能力将在后续版本陆续开放。</p>
    </header>

    <article class="prompt-block prompt-block--active">
      <header class="prompt-block__head">
        <div>
          <strong class="prompt-block__name">实体抽取提示词</strong>
          <small class="prompt-block__file">extract_graph.txt</small>
        </div>
        <span class="prompt-block__tag" :data-tone="isModified ? 'accent' : 'neutral'">
          {{ isModified ? '可编辑 · 已修改' : '可编辑' }}
        </span>
      </header>

      <div class="prompt-block__toolbar">
        <button type="button" class="toolbar-pill" :disabled="!isModified" @click="restoreTemplate"
                :title="templateContent ? '还原至种子模板内容' : '清空编辑内容（本期种子内容未接入）'">
          {{ templateContent ? '还原至模板' : '清空内容' }}
        </button>
        <span class="toolbar-pill toolbar-pill--readonly">占位变量：{{ PLACEHOLDER_VARS.join(' · ') }}</span>
      </div>

      <textarea
        class="prompt-block__editor"
        :value="extractGraphContent"
        spellcheck="false"
        rows="18"
        aria-label="实体抽取提示词内容"
        @input="handleInput"
      />

      <div class="prompt-block__meta">
        <span :data-over="overLimit ? 'true' : 'false'">
          已输入 {{ formatBytes(byteCount) }} / {{ formatBytes(maxBytes) }}
        </span>
        <span v-if="overLimit" class="prompt-block__meta-warn">超出上限，保存会被拒绝</span>
      </div>
    </article>

    <article class="locked-notice">
      <header class="locked-notice__head" @click="lockedExpanded = !lockedExpanded" role="button" tabindex="0"
              @keydown.enter.prevent="lockedExpanded = !lockedExpanded">
        <div class="locked-notice__title">
          <span aria-hidden="true">🔒</span>
          <span>其余 4 个提示词调优能力暂未开放</span>
        </div>
        <span class="locked-notice__toggle">{{ lockedExpanded ? '收起 ▴' : '展开查看 ▾' }}</span>
      </header>
      <div v-if="lockedExpanded" class="locked-notice__body">
        <ul class="locked-list">
          <li v-for="item in LOCKED_PROMPTS" :key="item.file">
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ item.file }}</small>
            </div>
            <span class="locked-tag">未开放</span>
          </li>
        </ul>
        <p class="locked-notice__hint">这些提示词本次构建将沿用上一步「选模板」中所选起点的默认内容，不会被你修改。</p>
      </div>
    </article>
  </section>
</template>
