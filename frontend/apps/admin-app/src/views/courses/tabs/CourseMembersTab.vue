<script setup>
import { onMounted, ref, watch } from 'vue'

import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../../components/common/CkStatusPill.vue'

import { loadModulePage } from '../../pages/module-loaders.js'
import { COURSE_PAGE_COPY } from '../course-page-copy.js'

const props = defineProps({
  course: { type: Object, required: true },
})

const state = ref({ loading: true, rows: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage(
      { name: 'course-members', params: { courseId: props.course.courseId } },
      { page: 1, size: 100 },
    )
    if (result?.requestState === 'error') {
      state.value = { loading: false, rows: [], error: result.error }
      return
    }
    state.value = { loading: false, rows: result?.rows ?? [], error: null }
  } catch (error) {
    state.value = { loading: false, rows: [], error }
  }
}

watch(() => props.course.courseId, load)
onMounted(load)

const isArchived = () => String(props.course?.status ?? '').toLowerCase() === 'archived'

function memberName(row) {
  const cell = row.cells?.[0]
  return cell?.label ?? '-'
}
function roleCell(row) {
  return row.cells?.[1] ?? { label: '-', status: 'neutral' }
}
function statusCell(row) {
  return row.cells?.[2] ?? { label: '-', status: 'neutral' }
}
function sourceLabel(row) {
  return row.cells?.[3] ?? '-'
}
function updatedAt(row) {
  return row.cells?.[4] ?? '-'
}
</script>

<template>
  <div class="course-members-tab" data-testid="course-members-tab">
    <header class="course-members-tab-header">
      <p v-if="isArchived()" class="course-members-tab-archived-hint">
        {{ COURSE_PAGE_COPY.members.archivedHint }}
      </p>
      <button v-else type="button" class="course-members-tab-add ck-pressable">
        {{ COURSE_PAGE_COPY.members.addCta }}
      </button>
    </header>

    <CkSkeleton v-if="state.loading" variant="row" :count="5" />

    <CkEmptyState
      v-else-if="state.error"
      icon="!"
      title="成员列表加载失败"
      :description="state.error?.message || ''"
    />

    <CkEmptyState
      v-else-if="!state.rows.length"
      icon="·"
      :title="COURSE_PAGE_COPY.members.emptyTitle"
      :description="COURSE_PAGE_COPY.members.emptyDescription"
    />

    <table v-else class="course-members-tab-table">
      <thead>
        <tr>
          <th>姓名</th>
          <th>角色</th>
          <th>状态</th>
          <th>来源</th>
          <th>更新时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in state.rows" :key="row.id">
          <td>
            <strong>{{ memberName(row) }}</strong>
            <small v-if="row.subtitle">{{ row.subtitle }}</small>
          </td>
          <td>
            <CkStatusPill :status="roleCell(row).status" :label="roleCell(row).label" size="sm" />
          </td>
          <td>
            <CkStatusPill :status="statusCell(row).status" :label="statusCell(row).label" size="sm" />
          </td>
          <td>{{ sourceLabel(row) }}</td>
          <td>{{ updatedAt(row) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped lang="scss">
.course-members-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.course-members-tab-header {
  display: flex;
  justify-content: flex-end;
}
.course-members-tab-archived-hint {
  margin: 0;
  padding: var(--ckqa-space-2);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.course-members-tab-add {
  padding: 6px 14px;
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
  border: none;
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  font-weight: var(--ckqa-fw-medium);
  cursor: pointer;
}
.course-members-tab-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  overflow: hidden;
  font-size: var(--ckqa-text-sm-size);
}
.course-members-tab-table th,
.course-members-tab-table td {
  padding: 10px 14px;
  text-align: left;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.course-members-tab-table th {
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-muted);
  font-weight: var(--ckqa-fw-medium);
}
.course-members-tab-table tr:last-child td {
  border-bottom: none;
}
.course-members-tab-table td small {
  display: block;
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-xs-size);
}
</style>
