<script setup>
const metrics = [
  { label: '可管理课程', value: '6', tone: 'blue', detail: '按当前身份过滤' },
  { label: '课程资料', value: '24', tone: 'green', detail: '解析状态可追踪' },
  { label: '激活知识库', value: '4', tone: 'amber', detail: '存在可用索引' },
  { label: '待排查任务', value: '2', tone: 'red', detail: '失败或超时' },
]

const recentRuns = [
  { title: '操作系统知识库', status: '索引成功', time: '09:42', tone: 'success' },
  { title: '数据结构课程资料', status: '解析中', time: '09:18', tone: 'running' },
  { title: '问答冒烟验证', status: '等待确认', time: '08:55', tone: 'pending' },
]

const productionSteps = [
  { label: '课程资料', value: '24', state: 'ready' },
  { label: 'PDF 解析', value: '18 done', state: 'ready' },
  { label: 'GraphRAG 导出', value: '12 ready', state: 'running' },
  { label: '索引激活', value: '4 active', state: 'ready' },
  { label: '问答验证', value: '3 smoke', state: 'pending' },
]
</script>

<template>
  <section class="overview-strip">
    <div>
      <p class="eyebrow">Operations Snapshot</p>
      <h2>知识库生产链路</h2>
      <p>从课程资料到索引激活和问答验证，首屏只保留最需要扫读的状态。</p>
    </div>
    <RouterLink class="primary-button compact" to="/app/knowledge-bases">进入构建</RouterLink>
  </section>

  <section class="dashboard-grid">
    <article v-for="metric in metrics" :key="metric.label" class="metric-card" :data-tone="metric.tone">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.detail }}</small>
    </article>
  </section>

  <section class="flow-rail" aria-label="知识库生产链路">
    <article v-for="step in productionSteps" :key="step.label" :data-state="step.state">
      <span>{{ step.label }}</span>
      <strong>{{ step.value }}</strong>
    </article>
  </section>

  <section class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>最近索引与解析</h2>
        <RouterLink to="/app/knowledge-bases">查看知识库</RouterLink>
      </div>
      <ul class="event-list">
        <li v-for="item in recentRuns" :key="item.title">
          <span>{{ item.title }}</span>
          <strong :data-tone="item.tone">{{ item.status }}</strong>
          <time>{{ item.time }}</time>
        </li>
      </ul>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>系统摘要</h2>
        <RouterLink to="/app/system">进入系统页</RouterLink>
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
</template>
