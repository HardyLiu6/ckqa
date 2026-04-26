<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getModulePageConfig } from './module-content.js'

const route = useRoute()

const config = computed(() => getModulePageConfig(route.name))
const activeStepKey = ref('')
const filterValues = ref({})

const activeWorkflowStep = computed(() => {
  const steps = config.value.workflowSteps ?? []
  return steps.find((step) => step.key === activeStepKey.value) ?? steps[0]
})

const filteredRows = computed(() => {
  const rows = config.value.rows ?? []
  const filters = config.value.filters ?? []

  return rows.filter((row) =>
    filters.every((filter) => {
      const selected = filterValues.value[filter.key] ?? '全部'

      if (selected === '全部') {
        return true
      }

      return row.includes(selected)
    }),
  )
})

function selectWorkflowStep(step) {
  activeStepKey.value = step.key
}
</script>

<template>
  <section class="module-hero">
    <div>
      <p class="eyebrow">{{ config.eyebrow }}</p>
      <h2>{{ route.meta.title }}</h2>
      <p>{{ config.summary }}</p>
    </div>

    <div class="button-row">
      <button class="primary-button compact" type="button">{{ config.primaryAction }}</button>
      <button class="secondary-button compact" type="button">{{ config.secondaryAction }}</button>
    </div>
  </section>

  <section v-if="config.variant === 'workflow'" class="workflow-panel">
    <ol class="workflow-steps">
      <li
        v-for="step in config.workflowSteps"
        :key="step.key"
        :class="{ active: step.key === activeWorkflowStep?.key }"
      >
        <button type="button" @click="selectWorkflowStep(step)">
          <span class="step-state" :data-state="step.state">{{ step.state }}</span>
          <strong>{{ step.label }}</strong>
          <small>{{ step.detail }}</small>
        </button>
      </li>
    </ol>

    <article class="panel workflow-focus">
      <p class="eyebrow">当前步骤</p>
      <h2>{{ activeWorkflowStep.label }}</h2>
      <p>{{ activeWorkflowStep.detail }}</p>
      <div class="workflow-actions">
        <button class="primary-button compact" type="button">执行当前步骤</button>
        <button class="secondary-button compact" type="button">查看日志</button>
      </div>
    </article>
  </section>

  <section v-else-if="config.variant === 'table'" class="panel">
    <div class="panel-heading">
      <h2>列表视图</h2>
      <span class="record-count">{{ filteredRows.length }} 条</span>
    </div>

    <div v-if="config.filters?.length" class="filter-bar">
      <label v-for="filter in config.filters" :key="filter.key">
        <span>{{ filter.label }}</span>
        <select v-model="filterValues[filter.key]">
          <option v-for="option in filter.options" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>

    <div class="data-table" role="table" :aria-label="route.meta.title">
      <div class="data-row header" role="row">
        <span v-for="column in config.columns" :key="column" role="columnheader">{{ column }}</span>
      </div>
      <div v-for="row in filteredRows" :key="row.join('-')" class="data-row" role="row">
        <span v-for="cell in row" :key="cell" role="cell">{{ cell }}</span>
      </div>
    </div>
  </section>

  <section v-else class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>关键区域</h2>
        <span class="record-count">{{ config.facts?.length ?? 0 }} 项</span>
      </div>
      <div class="field-grid">
        <div v-for="field in config.facts" :key="field" class="field-tile">
          <span>{{ field }}</span>
          <strong>待确认</strong>
        </div>
      </div>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>接入进度</h2>
      </div>
      <ol class="timeline-list">
        <li v-for="item in config.timeline" :key="item.label">
          <span class="step-state" :data-state="item.state">{{ item.state }}</span>
          <strong>{{ item.label }}</strong>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
    </article>
  </section>
</template>
