<script setup>
import { computed } from 'vue'
import { TOTAL_SAMPLES_PER_CANDIDATE } from './scoring-progress-model.js'

const props = defineProps({
  candidate: { type: Object, required: true },
  progress: { type: Object, required: true },
  index: { type: Number, required: true },
})

// 总样本数：优先用 progress.extractTotal（来自后端 c.extract.total，
// build_audit_extraction_set.py 实际产出条数 < 20 时分母会跟着小）。
// 兜底 TOTAL_SAMPLES_PER_CANDIDATE=20 给后端旧记录或前端 mock 用。
const sampleTotal = computed(() => {
  const t = Number(props.progress.extractTotal)
  return Number.isFinite(t) && t > 0 ? t : TOTAL_SAMPLES_PER_CANDIDATE
})

const isQueued     = computed(() => props.progress.status === 'queued')
const isExtracting = computed(() => props.progress.status === 'extracting')
const isScoring    = computed(() => props.progress.status === 'scoring')
const isDone       = computed(() => props.progress.status === 'done')

const extractPercent = computed(() =>
  Math.round((props.progress.extractDone / sampleTotal.value) * 100)
)

const extractStatusLabel = computed(() => {
  if (isDone.value || isScoring.value) return '✓ 抽取完成'
  if (isExtracting.value) return '↺ 抽取中'
  return '— 排队'
})

const scoreStatusLabel = computed(() => {
  if (isDone.value) return '✓ 完成'
  if (isScoring.value) return '↺ 评分中'
  return '— 排队'
})
</script>

<template>
  <div
    class="candidate-matrix-row"
    :class="{
      'is-queued':     isQueued,
      'is-extracting': isExtracting,
      'is-scoring':    isScoring,
      'is-done':       isDone,
    }"
  >
    <div class="candidate-matrix-row__name">
      <strong>{{ candidate.displayNameZh }}</strong>
      <small>{{ candidate.description }}</small>
    </div>

    <div class="candidate-matrix-row__stage">
      <span class="ann-text-tiny">抽取</span>
      <div class="candidate-matrix-row__bar">
        <div :class="['fill', isDone || isScoring ? 'is-done' : 'is-running']" :style="{ width: extractPercent + '%' }"></div>
      </div>
      <span class="ann-text-tiny">{{ progress.extractDone }} / {{ sampleTotal }}<span v-if="isExtracting && progress.extractEstimated" class="ann-text-muted">（估算）</span></span>
    </div>

    <div class="candidate-matrix-row__status">
      <span class="ann-pill" :class="{ 'ann-pill--success': isDone || isScoring, 'ann-pill--running': isExtracting }">
        {{ extractStatusLabel }}
      </span>
    </div>

    <div class="candidate-matrix-row__stage">
      <span class="ann-text-tiny">评分</span>
      <div class="candidate-matrix-row__bar">
        <div
          :class="['fill', isDone ? 'is-done' : isScoring ? 'is-running' : '']"
          :style="{ width: isDone ? '100%' : isScoring ? '50%' : '0%' }"
        ></div>
      </div>
    </div>

    <div class="candidate-matrix-row__status">
      <span class="ann-pill" :class="{ 'ann-pill--success': isDone, 'ann-pill--running': isScoring }">
        {{ scoreStatusLabel }}
      </span>
    </div>
  </div>
</template>
