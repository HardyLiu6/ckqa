<script setup>
import { computed } from 'vue'
import {
  formatPercent,
  formatTokens,
  formatDuration,
  resolveMedalClass,
  formatGateRule,
  resolveMetricColor,
} from './scoring-format-model.js'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  candidate: { type: Object, default: null },
  isSelected: { type: Boolean, default: false },
})

defineEmits(['update:modelValue'])

const metricTiles = computed(() => {
  if (!props.candidate) return []
  const c = props.candidate
  return [
    { label: '解析成功率', value: formatPercent(c.parseSuccessRate), color: resolveMetricColor(c.parseSuccessRate, 0.8) },
    { label: '召回率（校准集）', value: formatPercent(c.recall),     color: resolveMetricColor(c.recall, 0.5) },
    { label: '准确率（校准集）', value: formatPercent(c.precision),  color: resolveMetricColor(c.precision, 0.5) },
    { label: 'F1 调和均值', value: formatPercent(c.f1),               color: resolveMetricColor(c.f1, 0.5) },
    { label: '实体均数', value: c.entityCountAvg.toFixed(1),          color: 'neutral' },
    { label: '关系均数', value: c.relationCountAvg.toFixed(1),        color: 'neutral' },
  ]
})

const gateRules = computed(() => (props.candidate?.gates ?? []).map(formatGateRule))
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="candidate ? `${candidate.displayNameZh}（${candidate.candidateId}）` : ''"
    direction="rtl"
    size="380px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="candidate" class="scoring-detail-drawer">
      <header class="scoring-detail-drawer__head">
        <div class="scoring-detail-drawer__head-row">
          <div class="scoring-ranking-table__medal" :class="`medal--${resolveMedalClass(candidate.rank)}`">{{ candidate.rank }}</div>
          <span v-if="isSelected" class="ann-pill ann-pill--success">已选定</span>
        </div>
        <div class="scoring-detail-drawer__composite">
          {{ candidate.compositeScore.toFixed(2) }}
          <small>综合分</small>
        </div>
      </header>

      <section>
        <div class="scoring-detail-drawer__section-title">关键指标</div>
        <div class="scoring-detail-drawer__metric-grid">
          <div v-for="tile in metricTiles" :key="tile.label" class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">{{ tile.label }}</div>
            <div class="scoring-metric-tile__value" :class="`is-${tile.color}`">{{ tile.value }}</div>
          </div>
        </div>
      </section>

      <section>
        <div class="scoring-detail-drawer__section-title">质量门控</div>
        <ul class="scoring-gate-list">
          <li v-for="rule in gateRules" :key="rule.label" :class="{ 'is-failed': !rule.passed }">
            <span class="scoring-gate-list__icon">{{ rule.passed ? '✓' : '✗' }}</span>
            <span class="scoring-gate-list__label">{{ rule.label }}</span>
            <span class="scoring-gate-list__value">{{ rule.actualText }}</span>
          </li>
        </ul>
      </section>

      <section>
        <div class="scoring-detail-drawer__section-title">成本</div>
        <div class="scoring-detail-drawer__metric-grid">
          <div class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">token 消耗</div>
            <div class="scoring-metric-tile__value is-neutral">{{ formatTokens(candidate.tokensUsed) }}</div>
          </div>
          <div class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">耗时</div>
            <div class="scoring-metric-tile__value is-neutral">{{ formatDuration(candidate.elapsedSeconds) }}</div>
          </div>
        </div>
      </section>

      <a class="scoring-detail-drawer__sample-link">查看 20 条样本抽取详情 →</a>
    </div>
  </el-drawer>
</template>
