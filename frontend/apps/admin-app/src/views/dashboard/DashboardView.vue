<script setup>
import { onMounted, ref } from 'vue'
import { ArrowRight, DatabaseZap, Server } from 'lucide-vue-next'

import DataSourceChip from '../../components/common/DataSourceChip.vue'
import DiagnosticLogPanel from '../../components/common/DiagnosticLogPanel.vue'
import MetricTile from '../../components/common/MetricTile.vue'
import SkeletonCardGrid from '../../components/common/SkeletonCardGrid.vue'
import ProductionTrack from './ProductionTrack.vue'

const loading = ref(true)

const metrics = [
  { label: '可管理课程', value: '6', tone: 'running', hint: '按当前身份过滤' },
  { label: '课程资料', value: '24', tone: 'success', hint: '解析状态可追踪' },
  { label: '激活知识库', value: '4', tone: 'warning', hint: '存在可用索引' },
  { label: '问答会话', value: '128', tone: 'neutral', hint: '近 7 日示例' },
  { label: '待排查任务', value: '2', tone: 'danger', hint: '失败或超时' },
]

const recentRuns = [
  { title: '操作系统知识库', status: '索引成功', time: '09:42', tone: 'success' },
  { title: '数据结构课程资料', status: '解析中', time: '09:18', tone: 'running' },
  { title: '问答冒烟验证', status: '等待确认', time: '08:55', tone: 'pending' },
]

const productionSteps = [
  { key: 'material', counts: { done: 24 } },
  { key: 'parse', counts: { done: 18, failed: 2 } },
  { key: 'export', counts: { running: 3, done: 12 } },
  { key: 'index', counts: { running: 1, done: 8 } },
  { key: 'activate', counts: { done: 4 } },
  { key: 'smoke', counts: { pending: 3 } },
]

const diagnosticLines = [
  '[parse] material:OS-2024 第 3 章图片 OCR 置信度偏低',
  '[index] kb:data-structure 等待 output/lancedb 刷新',
  '[qa] smoke-run#17 命中率低于阈值，建议复测',
]

onMounted(() => {
  // 模拟数据加载完成（后续接入真实 API 时替换为实际请求状态）
  requestAnimationFrame(() => {
    loading.value = false
  })
})
</script>

<template>
  <section class="overview-strip">
    <div>
      <p class="eyebrow">Operations Snapshot</p>
      <h2>知识库生产链路</h2>
      <p>从课程资料到索引激活和问答验证，首屏只保留最需要扫读的状态。</p>
    </div>
    <div class="page-title-actions">
      <DataSourceChip source="mock" />
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        tag="router-link"
        to="/app/knowledge-bases"
      >
        <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
        进入构建
      </el-button>
    </div>
  </section>

  <Transition name="skeleton-fade">
    <SkeletonCardGrid
      v-if="loading"
      :cards="5"
      :columns="5"
    />
    <!-- list-stagger：指标卡片逐条渐入，Requirements 4.2 -->
    <TransitionGroup
      v-else
      name="list-stagger"
      tag="section"
      class="dashboard-grid"
      appear
    >
      <MetricTile
        v-for="(metric, index) in metrics"
        :key="metric.label"
        :label="metric.label"
        :value="metric.value"
        :hint="metric.hint"
        :tone="metric.tone"
        :style="{ '--stagger-index': index }"
      />
    </TransitionGroup>
  </Transition>

  <ProductionTrack :nodes="productionSteps" />

  <section class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>最近索引与解析</h2>
        <el-button class="ckqa-link-button" link type="primary" tag="router-link" to="/app/knowledge-bases">
          <DatabaseZap class="button-icon" :size="15" aria-hidden="true" />
          查看知识库
        </el-button>
      </div>
      <!-- list-stagger：最近索引与解析列表逐条渐入，Requirements 4.2 -->
      <TransitionGroup
        name="list-stagger"
        tag="ul"
        class="event-list"
        appear
      >
        <li
          v-for="(item, index) in recentRuns"
          :key="item.title"
          :style="{ '--stagger-index': index }"
        >
          <span>{{ item.title }}</span>
          <strong :data-tone="item.tone">{{ item.status }}</strong>
          <time>{{ item.time }}</time>
        </li>
      </TransitionGroup>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>系统摘要</h2>
        <el-button class="ckqa-link-button" link type="primary" tag="router-link" to="/app/system">
          <Server class="button-icon" :size="15" aria-hidden="true" />
          进入系统页
        </el-button>
      </div>
      <dl class="health-summary">
        <div>
          <dt>Java API</dt>
          <dd>待刷新</dd>
        </div>
        <div>
          <dt>GraphRAG 输出</dt>
          <dd>待刷新</dd>
        </div>
        <div>
          <dt>PDF 解析链路</dt>
          <dd>待刷新</dd>
        </div>
      </dl>
    </article>
  </section>

  <DiagnosticLogPanel
    title="异常摘要"
    :lines="diagnosticLines"
    :actions="[{ label: '查看问答会话', to: '/app/qa-sessions', icon: ArrowRight }]"
  />
</template>
