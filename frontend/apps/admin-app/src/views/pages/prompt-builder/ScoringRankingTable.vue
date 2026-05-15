<script setup>
import { computed, ref } from 'vue'
import { formatPercent, formatTokens, resolveMedalClass } from './scoring-format-model.js'

const props = defineProps({
  candidates: { type: Array, required: true },
  selectedCandidateId: { type: String, default: '' },
  highlightedCandidateId: { type: String, default: '' },
})

defineEmits(['select-candidate', 'view-detail'])

const sortKey = ref('compositeScore')

const SORT_OPTIONS = [
  { key: 'compositeScore',    label: '按综合分排序' },
  { key: 'recall',            label: '按召回率排序' },
  { key: 'precision',         label: '按准确率排序' },
  { key: 'parseSuccessRate',  label: '按解析成功率排序' },
]

const sortedCandidates = computed(() => {
  const list = [...props.candidates]
  list.sort((a, b) => (b[sortKey.value] ?? 0) - (a[sortKey.value] ?? 0))
  return list.map((c, i) => ({ ...c, rank: i + 1 }))
})
</script>

<template>
  <section class="scoring-ranking-table">
    <header class="scoring-ranking-table__head">
      <h3>候选排行榜</h3>
      <div class="scoring-ranking-table__sort">
        <span class="ann-text-muted">排序：</span>
        <select v-model="sortKey">
          <option v-for="opt in SORT_OPTIONS" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
        </select>
      </div>
    </header>

    <table>
      <thead>
        <tr>
          <th class="scoring-ranking-table__col-rank">#</th>
          <th>候选</th>
          <th>综合分</th>
          <th>解析成功率</th>
          <th>召回率</th>
          <th>准确率</th>
          <th>token 消耗</th>
          <th class="scoring-ranking-table__col-action">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in sortedCandidates"
          :key="row.candidateId"
          :class="{ 'is-highlighted': row.candidateId === highlightedCandidateId }"
          @click="$emit('view-detail', row.candidateId)"
        >
          <td>
            <div class="scoring-ranking-table__medal" :class="`medal--${resolveMedalClass(row.rank)}`">{{ row.rank }}</div>
          </td>
          <td>
            <div class="scoring-ranking-table__name">{{ row.displayNameZh }}</div>
            <code class="scoring-ranking-table__id">{{ row.candidateId }}</code>
          </td>
          <td>
            <div class="scoring-ranking-table__composite">
              <span class="value">{{ row.compositeScore.toFixed(2) }}</span>
              <div class="bar"><div :style="{ width: (row.compositeScore * 100) + '%' }"></div></div>
            </div>
          </td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.parseSuccessRate) }}</strong></td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.recall) }}</strong></td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.precision) }}</strong></td>
          <td class="scoring-ranking-table__metric">{{ formatTokens(row.tokensUsed) }}</td>
          <td class="scoring-ranking-table__col-action" @click.stop>
            <button
              v-if="row.candidateId !== selectedCandidateId"
              class="scoring-select-btn"
              @click="$emit('select-candidate', row.candidateId)"
            >
              选定
            </button>
            <span v-else class="scoring-select-btn is-selected">✓ 已选定</span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
