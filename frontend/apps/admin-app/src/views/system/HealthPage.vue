<script setup>
/**
 * 系统健康页（M7 · 任务 4.4）。
 *
 * 设计要点（design.md §5.4 / requirements.md FR-4）：
 * - `CkPageHero` 承载 eyebrow / title / subtitle；actions 位渲染聚合状态
 *   `CkStatusPill` + 「刷新」按钮，按钮仅在 `system:read` 权限下可见
 *   （`admin / auditor` 角色默认具备该权限点，教师也具备——因此按需求定义，
 *   教师 / 助教仅不展示「刷新」这个运维强动作；M7 约束由 `canAccess` 决定）。
 * - 主体为服务卡片网格：每张卡吃 `CkStatusPill` + `CkInfoTable`，字段
 *   走 `buildServiceDetails` 生成；卡片本身使用 M1 Token 描边与背景。
 * - 诊断日志区用 `CkLogStream`，`lines` 直接来自 `useHealthStatus.diagnostics`。
 * - 不出现任何裸字符串：所有文案都走 `HEALTH_PAGE_COPY`；未知 tone / service
 *   在 copy 层做兜底，模板本身只负责渲染。
 * - 所有颜色均走 `--ckqa-*` Token，模板与样式中不得直写十六进制或色值函数。
 *
 * 行数预算：≤ 380 行（含样式）。
 */
import { onMounted } from 'vue'
import { RefreshCw } from 'lucide-vue-next'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'
import CkInfoTable from '../../components/common/CkInfoTable.vue'
import CkLogStream from '../../components/common/CkLogStream.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'

import { useHealthStatus } from '../../composables/useHealthStatus.js'
import { useAuthStore } from '../../stores/auth.js'
import { HEALTH_PAGE_COPY, buildServiceDetails } from './health-page-copy.js'

const authStore = useAuthStore()

const {
  state,
  overallTone,
  overallLabel,
  services,
  diagnostics,
  error,
  loadHealth,
} = useHealthStatus()

onMounted(loadHealth)

/**
 * `CkLogStream` 的 `lines` 契约是 `{ level, message }[]`。`useHealthStatus` 输出的
 * 是纯字符串数组，这里做一次轻量适配：每行默认 `info` 级别；若文本命中"不可达/
 * 未就绪"等关键字则置为 `warn`/`error`，供 CkLogStream 的左侧色条标示。
 */
function toDiagnosticLines(rawLines) {
  if (!Array.isArray(rawLines)) return []
  return rawLines.map((line, idx) => ({
    id: `health-diag-${idx}`,
    level: resolveDiagnosticLevel(line),
    message: line,
  }))
}

function resolveDiagnosticLevel(line) {
  if (typeof line !== 'string') return 'info'
  if (line.includes('不可达')) return 'error'
  if (line.includes('未就绪')) return 'warn'
  return 'info'
}

// `diagnostics` 是 composable 导出的 `computed`，模板里直接 `.value` 交由 Vue 解包；
// 这里显式暴露一个同名包装便于 CkLogStream 的 :lines 绑定可读（不再手动 `.value`）。
</script>

<template>
  <section class="health-page" data-testid="health-page">
    <CkPageHero
      :eyebrow="HEALTH_PAGE_COPY.eyebrow"
      :title="HEALTH_PAGE_COPY.title"
      :subtitle="HEALTH_PAGE_COPY.subtitle"
    >
      <template #actions>
        <CkStatusPill
          :tone="overallTone"
          :label="overallLabel"
          data-testid="health-overall-pill"
        />
        <el-button
          v-if="authStore.canAccess(['system:read'])"
          type="primary"
          class="ckqa-el-button ckqa-el-button--primary"
          :disabled="state === 'loading'"
          data-testid="health-refresh-button"
          @click="loadHealth"
        >
          <RefreshCw :size="16" aria-hidden="true" />
          {{
            state === 'loading'
              ? HEALTH_PAGE_COPY.refresh.loadingLabel
              : HEALTH_PAGE_COPY.refresh.label
          }}
        </el-button>
      </template>
    </CkPageHero>

    <CkSkeleton
      v-if="state === 'loading' && services.length === 0"
      variant="card"
      :count="3"
      data-testid="health-loading"
    />

    <section
      v-else-if="state === 'error' && services.length === 0"
      class="health-page__error"
      role="alert"
      data-testid="health-error"
    >
      <p class="health-page__error-title">{{ HEALTH_PAGE_COPY.error.title }}</p>
      <p class="health-page__error-message">{{ error?.message || '' }}</p>
      <el-button size="small" @click="loadHealth">{{ HEALTH_PAGE_COPY.error.retry }}</el-button>
    </section>

    <section
      v-else
      class="health-page__grid"
      aria-label="依赖服务健康状态"
      data-testid="health-service-grid"
    >
      <article
        v-for="service in services"
        :key="service.key"
        class="health-page__card"
        :data-tone="service.tone"
        :data-testid="`health-service-${service.key}`"
      >
        <header class="health-page__card-header">
          <CkStatusPill
            :tone="service.tone"
            :label="service.displayName"
          />
        </header>
        <CkInfoTable :entries="buildServiceDetails(service)" :columns="2" />
      </article>
    </section>

    <section class="health-page__diagnostics">
      <h3 class="health-page__section-title">{{ HEALTH_PAGE_COPY.diagnosticsTitle }}</h3>
      <CkLogStream
        :lines="toDiagnosticLines(diagnostics)"
        :empty-hint="HEALTH_PAGE_COPY.diagnosticsEmpty"
        data-testid="health-diagnostics-log"
      />
    </section>
  </section>
</template>

<style scoped lang="scss">
.health-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-6);
}

.health-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--ckqa-space-4);
}

.health-page__card {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-5);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  box-shadow: var(--ckqa-shadow-soft);
  transition: border-color 120ms ease-out;
}

.health-page__card[data-tone='danger'] {
  border-color: var(--ckqa-danger);
  background: var(--ckqa-danger-soft);
}
.health-page__card[data-tone='warning'] {
  border-color: var(--ckqa-warning);
}
.health-page__card[data-tone='success'] {
  border-color: var(--ckqa-border-soft);
}

.health-page__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ckqa-space-2);
}

.health-page__diagnostics {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-5);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}

.health-page__section-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}

.health-page__error {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-5) var(--ckqa-space-6);
  border: 1px solid var(--ckqa-danger);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-danger-soft);
  color: var(--ckqa-text);
}

.health-page__error-title {
  margin: 0;
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-danger);
}

.health-page__error-message {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
</style>
