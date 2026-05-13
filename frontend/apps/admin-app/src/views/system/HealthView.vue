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
const diagnosticLines = computed(() => {
  if (!healthPayload.value) return ['等待 GET /api/v1/system/health 返回']

  return normalizedHealth.value.services.map((service) => {
    const readyState = service.ready ? 'ready' : 'not-ready'
    const reachState = service.reachable ? 'reachable' : 'unreachable'
    return `[${service.key}] ${reachState} / ${readyState} ${service.message || ''}`.trim()
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
      <p class="eyebrow">System Health</p>
      <h2>Java 编排入口健康检查</h2>
      <p>聚合 MySQL、PDF 解析、GraphRAG 输出和问答服务状态。</p>
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
        {{ loading ? '刷新中' : '刷新健康' }}
      </el-button>
    </div>
  </section>

  <section class="panel health-panel">
    <div class="panel-heading">
      <div>
        <h2>系统健康</h2>
        <p>Java `/api/v1/system/health`</p>
      </div>
      <StatusBadge
        :status="requestState === 'idle' ? 'blocked' : normalizedHealth.overallStatus"
        :label="requestState === 'idle' ? 'idle' : normalizedHealth.overallStatus"
      />
    </div>

    <p v-if="requestState === 'idle'" class="empty-state">等待自动刷新健康检查。</p>
    <Transition name="skeleton-fade">
      <SkeletonCardGrid v-if="requestState === 'loading'" :cards="4" :columns="2" />
    </Transition>
    <p v-if="requestState === 'error'" class="inline-error">{{ errorMessage }}</p>

    <HealthMatrix v-if="healthPayload" :services="normalizedHealth.services" />

    <details v-if="healthPayload" class="raw-json-details">
      <summary>原始响应 JSON</summary>
      <pre class="json-view">{{ JSON.stringify(normalizedHealth.raw, null, 2) }}</pre>
    </details>
  </section>

  <DiagnosticLogPanel title="健康诊断" :lines="diagnosticLines" />
</template>
