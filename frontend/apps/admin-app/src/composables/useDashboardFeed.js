import { reactive, readonly } from 'vue'

import { http } from '../axios/index.js'
import { unwrapApiResponse } from '../api/client.js'

const POLL_INTERVAL = 60_000

export function mergeDashboardFeed({ indexRuns, parseTasks } = {}) {
  const safeIndexRuns = Array.isArray(indexRuns) ? indexRuns : []
  const safeParseTasks = Array.isArray(parseTasks) ? parseTasks : []
  const events = []
  const tasks = []
  for (const run of safeIndexRuns) {
    if (run.status === 'running') {
      tasks.push({
        id: run.id,
        title: `${run.kbName} 索引`,
        status: 'running',
        progress: run.progress || 0,
        startedAt: run.startedAt || run.updatedAt,
        to: `/app/index-runs/${encodeURIComponent(run.id)}`,
      })
    }
    events.push({
      id: `index-${run.id}`,
      type: `build.${run.status}`,
      title: `${run.kbName} 构建${formatStatusLabel(run.status)}`,
      sub: '',
      when: run.updatedAt,
      to: `/app/index-runs/${encodeURIComponent(run.id)}`,
    })
  }
  for (const task of safeParseTasks) {
    if (task.status === 'running') {
      tasks.push({
        id: `parse-${task.id}`,
        title: `${task.materialName} 解析`,
        status: 'running',
        progress: task.progress || 0,
        startedAt: task.startedAt || task.updatedAt,
        to: `/app/materials/${encodeURIComponent(task.materialId)}`,
      })
    } else {
      events.push({
        id: `parse-${task.id}`,
        type: `parse.${task.status}`,
        title: `${task.materialName} 解析${formatStatusLabel(task.status)}`,
        sub: '',
        when: task.updatedAt,
        to: `/app/materials/${encodeURIComponent(task.materialId)}`,
      })
    }
  }
  return { events, tasks }
}

function formatStatusLabel(status) {
  const map = { success: '成功', failed: '失败', running: '中', cancelled: '已取消', pending: '待开始' }
  return map[status] || status
}

export function useDashboardFeed({ scopeStore }) {
  const state = reactive({
    events: [],
    tasks: [],
    loading: false,
    error: null,
  })
  let timer = null

  async function refresh() {
    state.loading = true
    state.error = null
    try {
      const params = scopeStore.requestParams()
      const [indexRunsRes, parseTasksRes] = await Promise.all([
        http.get('/index-runs', { params: { ...params, since: '24h', pageSize: 50 } }).then(unwrapApiResponse).catch(() => ({})),
        http.get('/material-parse-tasks', { params: { ...params, since: '24h', pageSize: 50 } }).then(unwrapApiResponse).catch(() => ({})),
      ])
      const merged = mergeDashboardFeed({
        indexRuns: indexRunsRes.items || [],
        parseTasks: parseTasksRes.items || [],
      })
      state.events = merged.events
      state.tasks = merged.tasks
    } catch (error) {
      state.error = error
    } finally {
      state.loading = false
    }
  }

  function start() {
    refresh()
    timer = setInterval(refresh, POLL_INTERVAL)
  }
  function stop() {
    if (timer) clearInterval(timer)
    timer = null
  }

  return { state: readonly(state), refresh, start, stop }
}
