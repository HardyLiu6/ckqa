<script setup>
const metrics = [
  { label: '可管理课程', value: '6', tone: 'blue' },
  { label: '课程资料', value: '24', tone: 'green' },
  { label: '激活知识库', value: '4', tone: 'amber' },
  { label: '待排查任务', value: '2', tone: 'red' },
]

const recentRuns = [
  { title: '操作系统知识库', status: '索引成功', time: '09:42' },
  { title: '数据结构课程资料', status: '解析中', time: '09:18' },
  { title: '问答冒烟验证', status: '等待确认', time: '08:55' },
]
</script>

<template>
  <section class="dashboard-grid">
    <article v-for="metric in metrics" :key="metric.label" class="metric-card" :data-tone="metric.tone">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
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
          <strong>{{ item.status }}</strong>
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
