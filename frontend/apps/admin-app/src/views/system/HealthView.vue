<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'

import { createApiError } from '../../api/client.js'
import { getSystemHealth } from '../../api/system.js'
import DataSourceChip from '../../components/common/DataSourceChip.vue'
import DiagnosticLogPanel from '../../components/common/DiagnosticLogPanel.vue'
import SkeletonCardGrid from '../../components/common/SkeletonCardGrid.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import HealthMatrix from '../../components/system/HealthMatrix.vue'
import { normalizeHealthResponse } from './health-model.js'

const requestState = ref('idle')
const errorMessage = ref('')
const healthPayload = ref(null)
const refreshedAt = ref('')

const loading = computed(() => requestState.value === 'loading')
const normalizedHealth = computed(() => normalizeHealthResponse(healthPayload.value || {}))
const rawJson = computed(() => JSON.stringify(normalizedHealth.value.raw, null, 2))

const countTiles = computed(() => {
  const { counts } = normalizedHealth.value
  return [
    { key: 'ok', tone: 'success', label: '正常', value: counts.ok },
    { key: 'warning', tone: 'warning', label: '未就绪', value: counts.warning },
    { key: 'danger', tone: 'danger', label: '异常', value: counts.danger },
    { key: 'total', tone: 'neutral', label: '检查项', value: counts.total },
  ]
})

const diagnosticLines = computed(() => {
  if (!healthPayload.value) return ['正在等待系统健康检查结果……']

  return normalizedHealth.value.services.map((service) => {
    const detail = service.tone !== 'success' && service.message ? `（${service.message}）` : ''
    return `${service.label} · ${service.statusLabel}${detail}`
  })
})

async function loadHealth() {
  requestState.value = 'loading'
  errorMessage.value = ''

  try {
    healthPayload.value = await getSystemHealth()
    refreshedAt.value = new Date().toISOString()
    requestState.value = 'success'
  } catch (error) {
    errorMessage.value = createApiError(error).message
    requestState.value = 'error'
  } finally {
    if (requestState.value === 'loading') {
      requestState.value = 'idle'
    }
  }
}

onMounted(loadHealth)
</script>

<template>
  <section class="module-hero">
    <div>
      <p class="eyebrow">系统健康</p>
      <h2>平台运行状态</h2>
      <p>实时检查数据库、缓存、知识库与问答引擎等核心组件的连通与就绪情况。</p>
    </div>
    <div class="page-title-actions">
      <DataSourceChip source="live" :refreshed-at="refreshedAt" />
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        native-type="button"
        :disabled="loading"
        @click="loadHealth"
      >
        <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
        {{ loading ? '刷新中' : '刷新状态' }}
      </el-button>
    </div>
  </section>

  <section
    v-if="healthPayload"
    class="panel health-overview"
    :data-tone="normalizedHealth.overallTone"
  >
    <div class="health-overview__summary">
      <StatusBadge
        :status="normalizedHealth.overallTone"
        :label="normalizedHealth.overallLabel"
      />
      <p class="health-overview__hint">{{ normalizedHealth.overallHint }}</p>
    </div>
    <dl class="health-overview__counts">
      <div v-for="tile in countTiles" :key="tile.key" :data-tone="tile.tone">
        <dt>{{ tile.label }}</dt>
        <dd>{{ tile.value }}</dd>
      </div>
    </dl>
  </section>

  <section class="panel health-panel">
    <div class="panel-heading">
      <div>
        <h2>组件检查明细</h2>
        <p>逐项查看各核心组件的连通与就绪状态。</p>
      </div>
    </div>

    <p v-if="requestState === 'idle'" class="empty-state">正在加载系统健康检查……</p>
    <Transition name="skeleton-fade">
      <SkeletonCardGrid v-if="requestState === 'loading'" :cards="6" :columns="2" />
    </Transition>
    <p v-if="requestState === 'error'" class="inline-error">{{ errorMessage }}</p>

    <HealthMatrix v-if="healthPayload" :services="normalizedHealth.services" />

    <details v-if="healthPayload" class="raw-json-details">
      <summary>查看原始检查数据（JSON）</summary>
      <p class="raw-json-details__hint">以下为接口返回的完整原始数据，供技术排查使用。</p>
      <pre class="json-view">{{ rawJson }}</pre>
    </details>
  </section>

  <DiagnosticLogPanel title="健康诊断记录" :lines="diagnosticLines" />
</template>
