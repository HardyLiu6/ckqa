<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  samples: { type: Array, required: true },
  activeSampleId: { type: String, default: '' },
})

defineEmits(['select-sample'])

const filter = ref('all')

const counters = computed(() => {
  const total = props.samples.length
  const done = props.samples.filter((s) => s.status === 'done').length
  return {
    total,
    done,
    progress: total > 0 ? Math.round((done / total) * 100) : 0,
    high:   props.samples.filter((s) => s.auditPriority === 'high').length,
    high_done: props.samples.filter((s) => s.auditPriority === 'high' && s.status === 'done').length,
    medium: props.samples.filter((s) => s.auditPriority === 'medium').length,
    medium_done: props.samples.filter((s) => s.auditPriority === 'medium' && s.status === 'done').length,
    low:    props.samples.filter((s) => s.auditPriority === 'low').length,
    low_done: props.samples.filter((s) => s.auditPriority === 'low' && s.status === 'done').length,
  }
})

const visible = computed(() => {
  if (filter.value === 'all') return props.samples
  if (filter.value === 'not_started') return props.samples.filter((s) => s.status === 'not_started')
  return props.samples.filter((s) => s.auditPriority === filter.value)
})

function statusLabel(status) {
  return ({
    not_started: '未开始',
    in_progress: '进行中',
    done:        '已完成',
    skipped:     '已跳过',
  })[status] ?? status
}

function priorityLabel(p) {
  return ({ high: '高', medium: '中', low: '低' })[p] ?? p
}
</script>

<template>
  <aside class="annotation-sample-rail">
    <header class="annotation-sample-rail__head">
      <div class="annotation-sample-rail__title">
        <strong>校准集</strong>
        <span class="ann-pill ann-pill--accent">{{ counters.done }} / {{ counters.total }}</span>
      </div>
      <div class="annotation-sample-rail__progress">
        <div :style="{ width: counters.progress + '%' }"></div>
      </div>
      <div class="annotation-sample-rail__counter">
        高 {{ counters.high_done }}/{{ counters.high }} · 中 {{ counters.medium_done }}/{{ counters.medium }} · 低 {{ counters.low_done }}/{{ counters.low }}
      </div>
    </header>

    <nav class="annotation-sample-rail__filter">
      <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button>
      <button :class="{ active: filter === 'high' }" @click="filter = 'high'">高</button>
      <button :class="{ active: filter === 'medium' }" @click="filter = 'medium'">中</button>
      <button :class="{ active: filter === 'not_started' }" @click="filter = 'not_started'">未标</button>
    </nav>

    <ul class="annotation-sample-rail__list">
      <li
        v-for="sample in visible"
        :key="sample.id"
        :class="{
          'is-active': sample.id === activeSampleId,
          'is-done':   sample.status === 'done',
          'is-skipped': sample.status === 'skipped',
        }"
        @click="$emit('select-sample', sample.id)"
      >
        <div class="annotation-sample-rail__row">
          <span class="annotation-sample-rail__id">{{ sample.id }}</span>
          <span class="ann-pill" :class="`ann-pill--${sample.auditPriority}`">
            {{ priorityLabel(sample.auditPriority) }}
          </span>
        </div>
        <div class="annotation-sample-rail__name">
          {{ sample.headingPath?.[sample.headingPath.length - 1] ?? '(无标题)' }}
        </div>
        <div class="annotation-sample-rail__hint">
          {{ statusLabel(sample.status) }}
          <template v-if="sample.status === 'in_progress'">
            · 实体 {{ sample.goldEntities.length }} 关系 {{ sample.goldRelations.length }}
          </template>
          <template v-else-if="sample.status === 'done'">
            · 实体 {{ sample.goldEntities.length }} 关系 {{ sample.goldRelations.length }}
          </template>
        </div>
      </li>
    </ul>
  </aside>
</template>
