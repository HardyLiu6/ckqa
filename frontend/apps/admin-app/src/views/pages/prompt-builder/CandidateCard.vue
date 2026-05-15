<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

defineEmits(['toggle', 'view-prompt'])

const tokenBarPercent = computed(() => {
  const max = 10_000
  return Math.min(100, Math.round((props.candidate.estimatedTokenPerCall / max) * 100))
})

const tokenColorClass = computed(() => {
  const t = props.candidate.estimatedTokenPerCall
  if (t < 4000) return 'green'
  if (t < 7000) return 'yellow'
  return 'red'
})

const formattedToken = computed(() =>
  `~${(props.candidate.estimatedTokenPerCall / 1000).toFixed(1)}k`
)

const formattedSize = computed(() =>
  `${(props.candidate.promptSizeBytes / 1024).toFixed(1)} KB`
)
</script>

<template>
  <article
    class="candidate-card"
    :class="{
      'is-selected':    selected,
      'is-recommended': candidate.isRecommended,
    }"
    @click="$emit('toggle', candidate.candidateId)"
  >
    <span v-if="candidate.isRecommended" class="candidate-card__rec-badge">✦ 推荐</span>

    <header class="candidate-card__head">
      <div class="candidate-card__title">
        <h4>{{ candidate.displayNameZh }}</h4>
        <code class="candidate-card__id">{{ candidate.candidateId }}</code>
      </div>
      <span class="candidate-card__checkbox" :class="{ checked: selected }">
        <template v-if="selected">✓</template>
      </span>
    </header>

    <p class="candidate-card__desc">{{ candidate.description }}</p>

    <div class="candidate-card__traits">
      <span v-for="trait in candidate.traits" :key="trait.key" class="cand-pill">
        {{ trait.label }}
      </span>
    </div>

    <dl class="candidate-card__meta">
      <div><dt>大小</dt><dd>{{ formattedSize }}</dd></div>
      <div><dt>schema</dt><dd>{{ candidate.schemaUsed ? '✓' : '—' }}</dd></div>
      <div><dt>few-shot</dt><dd>{{ candidate.fewshotExampleCount > 0 ? `${candidate.fewshotExampleCount} 例` : '—' }}</dd></div>
      <div><dt>来源</dt><dd>{{ candidate.basePromptSource }}</dd></div>
    </dl>

    <div class="candidate-card__token">
      <span class="ann-text-tiny">单次调用 token</span>
      <div class="candidate-card__token-bar">
        <div :class="`fill fill--${tokenColorClass}`" :style="{ width: tokenBarPercent + '%' }"></div>
      </div>
      <span class="candidate-card__token-value" :class="`is-${tokenColorClass}`">{{ formattedToken }}</span>
    </div>

    <button
      class="candidate-card__view-btn"
      @click.stop="$emit('view-prompt', candidate.candidateId)"
    >
      查看完整提示词 →
    </button>
  </article>
</template>
