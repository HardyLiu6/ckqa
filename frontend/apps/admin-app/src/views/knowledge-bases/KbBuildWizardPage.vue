<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import ModulePage from '../pages/ModulePage.vue'
import {
  resolveBuildRunIdQuery,
} from '../pages/module-page-model.js'
import { useBuildRunStream } from '../../composables/useBuildRunStream.js'
import { useBuildStageTimeline } from '../../composables/useBuildStageTimeline.js'
import { useBuildWizardRun } from '../../composables/useBuildWizardRun.js'
import { authStore } from '../../stores/auth.js'
import { getBuildRun, getKnowledgeBase } from '../../api/knowledge-bases.js'

import BuildRunLivePanel from './components/BuildRunLivePanel.vue'
import {
  resolveCanManageRun,
  resolveReadonly,
  isBuildRunTerminal,
} from './build-wizard-page-model.js'
import { KB_BUILD_COPY } from './kb-build-copy.js'

// KbBuildWizardPage 的核心职责：
// 1) 外层走 ConsoleLayout（shell），页面内部用 CSS Grid 做 7fr/5fr 分屏
// 2) 左栏 = 现有 ModulePage（完整 6 步表单 + 业务状态机，保持不重写）
// 3) 右栏 = BuildRunLivePanel（阶段时间线 + 日志流 + 重试/取消）
//
// 左右两栏通过 URL 中的 buildRunId 做单向同步：
// - ModulePage 启动构建后会写 buildRunId 到 route.query
// - 右侧 watch(buildRunId) 重启 useBuildRunStream 订阅
//
// 这样既实现了 M5 plan 的"分屏 + 实时面板"视觉目标，
// 又保留了 ModulePage 里所有已验证的 6 步流水线逻辑。

const route = useRoute()

const buildRunId = computed(() => resolveBuildRunIdQuery(route.query))
const knowledgeBase = ref(null)
const runCache = ref(null)

const { state: streamState, start, stop, refresh } = useBuildRunStream({
  buildRunId: buildRunId.value,
  pollIntervalMs: 5000,
  getBuildRun,
})

const { timeline, activeKey, currentPct, overallPct } = useBuildStageTimeline(() => streamState)

const wizard = useBuildWizardRun({ buildRunId: buildRunId.value })

const readonly = computed(() =>
  resolveReadonly({
    currentUser: authStore.state.currentUser,
    kb: knowledgeBase.value ?? {},
    canAccess: authStore.canAccess,
  }),
)

const canAct = computed(() =>
  !readonly.value
  && resolveCanManageRun({
    currentUser: authStore.state.currentUser,
    run: runCache.value,
    canAccess: authStore.canAccess,
  }),
)

const emptyHint = computed(() =>
  buildRunId.value ? '等待构建阶段更新' : KB_BUILD_COPY.placeholder,
)

const failedStageKey = computed(() => streamState.stages.find((s) => s.state === 'failed')?.key ?? '')

// buildRunId 变化时，重启订阅
watch(buildRunId, (next) => {
  wizard.setBuildRunId(next)
  if (next) {
    start({ buildRunId: next })
  } else {
    stop()
  }
})

// kbId 变化时，加载 KB 基础信息（用于角色判定）
watch(
  () => route.params.kbId,
  async (kbId) => {
    if (!kbId) {
      knowledgeBase.value = null
      return
    }
    try {
      knowledgeBase.value = await getKnowledgeBase(kbId)
    } catch {
      knowledgeBase.value = null
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (buildRunId.value) {
    start({ buildRunId: buildRunId.value })
    refresh().then((snapshot) => {
      if (snapshot) runCache.value = snapshot
    })
  }
})

async function onRetry(stageKey) {
  if (!stageKey) return
  const result = await wizard.retry(stageKey, {})
  if (result.status === 'success') {
    refresh()
  }
}

// 跳过阶段暂无后端独立接口；先记录到 console，为后续接入预留扩展点
function onSkip(stageKey) {
  console.warn('[KbBuildWizardPage] 跳过阶段功能暂未接入', stageKey)
}

async function onCancel() {
  if (!isBuildRunTerminal(streamState.status)) {
    await wizard.cancel()
    refresh()
  }
}
</script>

<template>
  <div class="kb-build-wizard-page" data-testid="kb-build-wizard-page">
    <section class="kb-build-wizard-page__form">
      <!--
        Form 侧完全复用 ModulePage（6 步 BuildStep* + primaryAction 长任务 + SSE）。
        ModulePage 内部检测到 route.name === 'knowledge-base-build' 时会自动进入 workflow 分支。
      -->
      <ModulePage />
    </section>

    <aside class="kb-build-wizard-page__live" aria-label="构建实时面板区">
      <BuildRunLivePanel
        :status="streamState.status"
        :timeline="timeline"
        :active-key="activeKey"
        :current-pct="currentPct"
        :overall-pct="overallPct"
        :logs="streamState.logs"
        :failure-reason="streamState.failureReason"
        :failed-stage-key="failedStageKey"
        :can-act="canAct"
        :mode="streamState.mode"
        :empty-hint="emptyHint"
        @retry="onRetry"
        @skip="onSkip"
        @cancel="onCancel"
      />
    </aside>
  </div>
</template>

<style scoped lang="scss">
.kb-build-wizard-page {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: var(--ckqa-space-5);
  align-items: flex-start;
}
.kb-build-wizard-page__form {
  min-width: 0;
}
.kb-build-wizard-page__live {
  position: sticky;
  top: 92px; // Topbar(52) + breadcrumb 余量
  align-self: flex-start;
  max-height: calc(100vh - 92px - 24px);
  overflow-y: auto;
}
@media (max-width: 1280px) {
  .kb-build-wizard-page {
    grid-template-columns: 1fr;
  }
  .kb-build-wizard-page__live {
    position: static;
    max-height: none;
  }
}
</style>
