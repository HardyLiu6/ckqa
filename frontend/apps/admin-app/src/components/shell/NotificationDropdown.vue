<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'
import {
  mergeFeed,
  hasUnseenFailures,
  formatFeedItem,
} from './notification-feed-model.js'

const STORAGE_KEY = 'ckqa-admin-notifications-last-seen'
const POLL_INTERVAL = 60_000
const isBrowser = typeof window !== 'undefined' && typeof localStorage !== 'undefined'

const open = ref(false)
const running = ref([])
const failed = ref([])
const lastSeenAt = ref(isBrowser ? Number(localStorage.getItem(STORAGE_KEY)) || 0 : 0)

let timer = null

async function fetchFeed() {
  try {
    const [runningRes, failedRes] = await Promise.all([
      fetch('/api/v1/index-runs?status=running').then((r) => r.json()).catch(() => ({ data: [] })),
      fetch('/api/v1/material-parse-tasks?status=failed&since=24h').then((r) => r.json()).catch(() => ({ data: [] })),
    ])
    running.value = (runningRes.data || []).map((r) => ({
      id: r.id, kind: 'index-run', status: 'running',
      title: r.title || `知识库构建 #${r.id}`, updatedAt: new Date(r.updatedAt).getTime(),
    }))
    failed.value = (failedRes.data || []).map((f) => ({
      id: f.id, kind: 'parse-task', status: 'failed',
      title: f.title || `资料解析 #${f.id}`, updatedAt: new Date(f.updatedAt).getTime(),
    }))
  } catch {
    // 静默：通知是辅助信息，不能因错误打扰用户
  }
}

const feed = computed(() => mergeFeed(running.value, failed.value).map((it) => formatFeedItem(it)))
const hasNew = computed(() => hasUnseenFailures(failed.value, lastSeenAt.value))

function toggle() {
  open.value = !open.value
  if (open.value) {
    lastSeenAt.value = Date.now()
    if (isBrowser) localStorage.setItem(STORAGE_KEY, String(lastSeenAt.value))
  }
}

onMounted(() => {
  fetchFeed()
  timer = setInterval(fetchFeed, POLL_INTERVAL)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="notif-dd">
    <button
      class="notif-dd-trigger"
      type="button"
      :aria-expanded="open"
      aria-label="通知"
      @click="toggle"
    >
      <span aria-hidden="true">🔔</span>
      <span v-if="hasNew" class="notif-dd-dot" aria-hidden="true" />
    </button>
    <div v-if="open" class="notif-dd-panel" role="menu">
      <header class="notif-dd-header">运行 / 失败 · 最近 24 小时</header>
      <ul v-if="feed.length" class="notif-dd-list">
        <li
          v-for="item in feed"
          :key="item.id"
          :class="`tone-${item.tone}`"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.subtitle }}</span>
        </li>
      </ul>
      <p v-else class="notif-dd-empty">暂无活跃任务</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notif-dd { position: relative; }
.notif-dd-trigger {
  position: relative;
  width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  cursor: pointer;
}
.notif-dd-dot {
  position: absolute; top: 5px; right: 5px;
  width: 8px; height: 8px;
  border-radius: 50%; background: var(--ckqa-danger);
  box-shadow: 0 0 0 2px var(--ckqa-surface);
}
.notif-dd-panel {
  position: absolute; right: 0; top: calc(100% + 6px); z-index: 30;
  width: 320px; padding: 10px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
}
.notif-dd-header {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  padding: 4px 6px 8px;
}
.notif-dd-list { list-style: none; margin: 0; padding: 0; max-height: 360px; overflow-y: auto; }
.notif-dd-list li {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 6px;
  border-top: 1px solid var(--ckqa-border-soft);
  font-size: var(--ckqa-text-sm-size);
}
.notif-dd-list li.tone-danger strong { color: var(--ckqa-danger); }
.notif-dd-list li.tone-running strong { color: var(--ckqa-running); }
.notif-dd-empty {
  padding: 14px 6px;
  text-align: center;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
</style>
