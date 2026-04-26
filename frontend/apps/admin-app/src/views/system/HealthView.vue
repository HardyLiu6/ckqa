<script setup>
import { ref } from 'vue'

import { http } from '../../axios/index.js'

const loading = ref(false)
const errorMessage = ref('')
const healthPayload = ref(null)

async function loadHealth() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await http.get('/system/health')
    healthPayload.value = response.data?.data ?? response.data
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="module-hero">
    <div>
      <p class="eyebrow">System Health</p>
      <h2>Java 编排入口健康检查</h2>
      <p>聚合 MySQL、PDF 解析、GraphRAG 输出和问答服务状态。</p>
    </div>
    <button class="primary-button compact" type="button" :disabled="loading" @click="loadHealth">
      {{ loading ? '刷新中' : '刷新健康' }}
    </button>
  </section>

  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2>系统健康</h2>
        <p>Java `/api/v1/system/health`</p>
      </div>
    </div>

    <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

    <pre v-if="healthPayload" class="json-view">{{ JSON.stringify(healthPayload, null, 2) }}</pre>

    <dl v-else class="health-summary">
      <div>
        <dt>MySQL</dt>
        <dd>等待刷新</dd>
      </div>
      <div>
        <dt>pdf_ingest</dt>
        <dd>等待刷新</dd>
      </div>
      <div>
        <dt>graphrag_pipeline</dt>
        <dd>等待刷新</dd>
      </div>
    </dl>
  </section>
</template>
