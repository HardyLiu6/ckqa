<!-- 知识图谱浏览页：拉真实接口 + G6 画布 + 实体详情抽屉 -->
<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import GraphCanvas from '@/components/knowledge/GraphCanvas.vue'
import EntityDetailPanel from '@/components/knowledge/EntityDetailPanel.vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import { useGraphStore, GRAPH_STATE } from '@/stores/graph'

const route = useRoute()
const router = useRouter()
const store = useGraphStore()

// MVP：courseId 从 query 取，例如 /knowledge/graph?courseId=os
// 未提供时直接走"未开放"降级，避免画布空转。
const courseId = computed(() => {
  const raw = route.query.courseId
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim()
  }
  return ''
})

async function bootstrap() {
  store.reset()
  if (!courseId.value) {
    return
  }
  const ok = await store.selectKnowledgeBaseForCourse(courseId.value)
  if (!ok) {
    return
  }
  await store.loadOverview({ level: 0, topN: 20 })
}

function onSelectNode(id) {
  if (!id) return
  store.loadEntityDetail(id)
}

async function onExpandNode(id) {
  if (!id) return
  await store.expandNeighborhood(id, { limit: 50 })
}

function onAskQuestion(entity) {
  if (!entity?.name) {
    ElMessage.warning('当前节点缺少名称，无法跳转问答。')
    return
  }
  router.push({ path: '/qa/ask', query: { topic: entity.name } })
}

watch(
  () => store.errorMessage,
  (msg) => {
    if (msg && store.state !== GRAPH_STATE.ERROR) {
      ElMessage.warning(msg)
    }
  },
)

onMounted(bootstrap)

const showCanvas = computed(
  () => store.state === GRAPH_STATE.READY || store.state === GRAPH_STATE.LOADING,
)
const showEmpty = computed(() => store.state === GRAPH_STATE.EMPTY)
const showNoActive = computed(() => store.state === GRAPH_STATE.NO_ACTIVE_INDEX)
const showError = computed(() => store.state === GRAPH_STATE.ERROR)
const missingCourseId = computed(() => !courseId.value)
</script>

<template>
  <div class="kg-page">
    <main class="kg-main">
      <header class="kg-head">
        <div>
          <h1 class="title">知识图谱</h1>
          <p class="sub">
            <template v-if="store.activeKnowledgeBase">
              当前知识库：{{ store.activeKnowledgeBase.name }}（KB #{{ store.activeKnowledgeBase.id }}，激活索引
              #{{ store.activeKnowledgeBase.activeIndexRunId }}）
            </template>
            <template v-else>课程概念网络浏览</template>
          </p>
        </div>
        <GlowButton size="md" :disabled="!store.activeKnowledgeBase" @click="bootstrap">
          重新加载
        </GlowButton>
      </header>

      <GlassCard tier="base" padding="md" class="canvas-card">
        <div v-if="missingCourseId" class="state-block">
          <h3>缺少课程上下文</h3>
          <p>
            知识图谱需要明确的课程入口。请通过课程详情页或 <code>?courseId=os</code>
            参数进入图谱页。
          </p>
        </div>
        <div v-else-if="showNoActive" class="state-block">
          <h3>当前课程暂无可用知识库</h3>
          <p>所选课程还没有激活的索引（activeIndexRunId 为空），等管理员构建并激活索引后再来浏览。</p>
        </div>
        <div v-else-if="showError" class="state-block error">
          <h3>加载失败</h3>
          <p>{{ store.errorMessage || '请稍后重试。' }}</p>
          <GlowButton size="sm" @click="bootstrap">重试</GlowButton>
        </div>
        <div v-else-if="showEmpty" class="state-block">
          <h3>当前社区暂无可视节点</h3>
          <p>说明该层级的社区或实体还未生成，可换个层级或返回首页。</p>
        </div>

        <div v-show="showCanvas" class="canvas-wrap">
          <div v-if="store.state === GRAPH_STATE.LOADING" class="loading-mask">加载中…</div>
          <GraphCanvas
            :nodes="store.nodes"
            :edges="store.edges"
            :selected-id="store.selectedNodeId"
            @select="onSelectNode"
            @expand="onExpandNode"
          />
        </div>
      </GlassCard>
    </main>

    <aside class="kg-aside">
      <EntityDetailPanel
        :entity="store.entityDetail"
        @ask-question="onAskQuestion"
        @expand="(entity) => onExpandNode(entity?.id)"
      />
    </aside>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.kg-page {
  --module-color-500: #0d9488;
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 16px;
  padding: 16px;
  height: calc(100vh - 64px - 32px);

  @media (max-width: $bp-laptop) {
    grid-template-columns: 1fr;
    height: auto;
  }
}

.kg-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.kg-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  .title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 800;
    color: #0f172a;
    margin: 0;
  }

  .sub {
    margin: 4px 0 0;
    font-size: 13px;
    color: #64748b;
  }
}

.canvas-card {
  flex: 1;
  display: flex;
  min-height: 420px;
  border-color: rgba(13, 148, 136, 0.2) !important;
}

.canvas-wrap {
  position: relative;
  flex: 1;
  min-height: 360px;
}

.loading-mask {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0d9488;
  font-size: 13px;
  z-index: 1;
}

.state-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  text-align: center;

  h3 {
    margin: 0;
    color: #0f172a;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 13px;
    line-height: 1.6;
  }

  &.error h3 {
    color: #b91c1c;
  }
}

.kg-aside {
  position: sticky;
  top: 80px;
  align-self: flex-start;
}
</style>
