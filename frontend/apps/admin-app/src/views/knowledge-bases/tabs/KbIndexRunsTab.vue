<script setup>
import { computed } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../../components/common/CkStatusPill.vue'

import { KB_PAGE_COPY } from '../kb-page-copy.js'

const props = defineProps({
  // 索引版本列表（倒序）
  indexRuns: { type: Array, default: () => [] },
  // 当前激活的 indexRunId（用于在对应行显示"已激活"）
  activeIndexRunId: { type: [Number, String, null], default: null },
})

const emit = defineEmits(['activate'])

const rows = computed(() =>
  (Array.isArray(props.indexRuns) ? props.indexRuns : []).map((run) => {
    const id = run.id ?? run.indexRunId
    const status = String(run.status ?? '').toLowerCase()
    return {
      id,
      to: id ? `/app/index-runs/${encodeURIComponent(id)}` : null,
      version: run.indexVersion ?? `#${id}`,
      status,
      statusTone: mapStatusTone(status),
      statusLabel: mapStatusLabel(status),
      startedAt: run.startedAt ?? run.createdAt ?? '-',
      durationText: resolveDuration(run),
      operator: run.operatorName ?? run.requestedBy ?? '-',
      isActive: id && String(id) === String(props.activeIndexRunId),
      canActivate: status === 'success' && id && String(id) !== String(props.activeIndexRunId),
    }
  }),
)

function mapStatusTone(status) {
  if (status === 'success') return 'success'
  if (status === 'running' || status === 'processing') return 'running'
  if (status === 'failed') return 'danger'
  return 'neutral'
}

function mapStatusLabel(status) {
  if (status === 'success') return '成功'
  if (status === 'running') return '构建中'
  if (status === 'failed') return '失败'
  if (status === 'pending') return '等待中'
  return status || '未知'
}

function resolveDuration(run = {}) {
  const start = Date.parse(run.startedAt ?? run.createdAt ?? '')
  const end = Date.parse(run.finishedAt ?? run.updatedAt ?? '')
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return '-'
  const seconds = Math.round((end - start) / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const restSec = seconds % 60
  return restSec ? `${minutes}m ${restSec}s` : `${minutes}m`
}
</script>

<template>
  <section class="kb-index-runs-tab" data-testid="kb-index-runs-tab">
    <CkEmptyState
      v-if="!rows.length"
      icon="◻"
      :title="KB_PAGE_COPY.detail.indexRunsEmpty"
    />
    <table v-else class="kb-index-runs-tab__table">
      <thead>
        <tr>
          <th>版本</th>
          <th>状态</th>
          <th>开始时间</th>
          <th>耗时</th>
          <th>启动人</th>
          <th aria-label="操作"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id" :data-active="row.isActive || null">
          <td>
            <RouterLink v-if="row.to" :to="row.to" class="kb-index-runs-tab__link">
              {{ row.version }}
            </RouterLink>
            <span v-else>{{ row.version }}</span>
          </td>
          <td>
            <CkStatusPill :tone="row.statusTone" :label="row.statusLabel" size="sm" />
            <CkStatusPill
              v-if="row.isActive"
              tone="success"
              label="当前激活"
              size="sm"
              class="kb-index-runs-tab__active-pill"
            />
          </td>
          <td>{{ row.startedAt }}</td>
          <td>{{ row.durationText }}</td>
          <td>{{ row.operator }}</td>
          <td>
            <button
              v-if="row.canActivate"
              type="button"
              class="ck-pressable kb-index-runs-tab__activate"
              @click="emit('activate', row.id)"
            >
              激活此版本
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped lang="scss">
.kb-index-runs-tab__table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--ckqa-text-sm-size);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  overflow: hidden;
}
.kb-index-runs-tab__table th,
.kb-index-runs-tab__table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.kb-index-runs-tab__table tr:last-child td {
  border-bottom: none;
}
.kb-index-runs-tab__table th {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
  background: var(--ckqa-surface-muted);
}
.kb-index-runs-tab__link {
  color: var(--ckqa-accent);
  text-decoration: none;
}
.kb-index-runs-tab__link:hover {
  text-decoration: underline;
}
.kb-index-runs-tab__active-pill {
  margin-left: 6px;
}
.kb-index-runs-tab__activate {
  padding: 4px 10px;
  border-radius: var(--ckqa-radius-md);
  border: 1px solid var(--ckqa-accent);
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
  cursor: pointer;
  font-size: var(--ckqa-text-xs-size);
}
.kb-index-runs-tab__activate:hover {
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
}
</style>
