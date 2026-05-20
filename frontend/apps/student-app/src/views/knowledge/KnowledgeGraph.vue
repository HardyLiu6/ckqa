<!-- 知识图谱浏览页：拉真实接口 + G6 画布 + 实体详情抽屉 -->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
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

// 邻域扩展模式：merge 把新邻居叠加到当前画布；replace 只看中心节点+邻居
const expandMode = ref('merge')

// 章节视图节点 / 边（基于 communities 数组合成）
const overviewGraph = computed(() => {
  const list = store.communities ?? []
  // 按 rank 排序，rank 越大节点越大
  const ranks = list.map((c) => Number(c.rank) || 0)
  const maxRank = Math.max(1, ...ranks)
  const nodes = list.map((c) => ({
    id: `community-${c.communityId}`,
    name: c.title || `社区 ${c.communityId}`,
    type: '章节',
    communityId: c.communityId,
    // degree 字段被画布用来调节点大小：用归一化 rank 占位
    degree: Math.round(((Number(c.rank) || 0) / maxRank) * 60),
    __isCommunity: true,
    __raw: c,
  }))
  // 章节之间不画边（避免乱）
  return { nodes, edges: [] }
})

// 钻入某社区后的子图：取该社区的 topEntities + 内部 edges
const focusedGraph = computed(() => {
  const cid = store.focusedCommunityId
  if (cid === null || cid === undefined) return null
  const community = (store.communities ?? []).find((c) => c.communityId === cid)
  if (!community) return null
  const topEntities = community.topEntities ?? []
  const idSet = new Set(topEntities.map((e) => e.id))
  // 取后端 nodes 中属于该社区的实体（更稳）
  const entityNodes = (store.nodes ?? []).filter(
    (n) => n.communityId === cid || idSet.has(n.id),
  )
  // 边只保留两端都在该社区的
  const entityIds = new Set(entityNodes.map((n) => n.id))
  const edges = (store.edges ?? []).filter(
    (e) => entityIds.has(e.source) && entityIds.has(e.target),
  )
  return { nodes: entityNodes, edges, community }
})

const canvasNodes = computed(() => {
  return focusedGraph.value ? focusedGraph.value.nodes : overviewGraph.value.nodes
})
const canvasEdges = computed(() => {
  return focusedGraph.value ? focusedGraph.value.edges : overviewGraph.value.edges
})

// 知识图谱页支持两种入口：
//   1. 课程详情 / 卡片 / 我的课程 → 带 ?courseId= 进
//   2. 主导航直接进 /knowledge/graph → 通过课程选择器挑课
// URL ?courseId= 仅作为"默认选中"的优先级，切换课程时同步回写到 URL，便于分享 / 收藏。
const preferredCourseId = computed(() => {
  const raw = route.query.courseId
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim()
  }
  return ''
})

async function loadGraphForCourse(courseId) {
  if (!courseId) {
    store.activeKnowledgeBase = null
    return
  }
  const ok = await store.selectKnowledgeBaseForCourse(courseId)
  if (!ok) {
    return
  }
  await store.loadOverview({ level: 0, topN: 20 })
}

async function bootstrap() {
  store.reset()
  const chosen = await store.loadAvailableCourses(preferredCourseId.value)
  syncCourseToUrl(chosen)
  await loadGraphForCourse(chosen)
}

function syncCourseToUrl(courseId) {
  const current = preferredCourseId.value
  if (!courseId) {
    if (current) {
      const nextQuery = { ...route.query }
      delete nextQuery.courseId
      router.replace({ query: nextQuery })
    }
    return
  }
  if (current !== courseId) {
    router.replace({ query: { ...route.query, courseId } })
  }
}

async function onCourseChange(courseId) {
  if (!courseId) return
  store.selectedCourseId = courseId
  syncCourseToUrl(courseId)
  store.activeKnowledgeBase = null
  store.entityDetail = null
  store.selectedNodeId = null
  store.focusedCommunityId = null
  await loadGraphForCourse(courseId)
}

function onSelectNode(id) {
  if (!id) return
  // 章节视图：单击社区节点 → 显示社区简介，但不钻入
  if (id.startsWith('community-')) {
    const cid = Number(id.slice('community-'.length))
    const community = (store.communities ?? []).find((c) => c.communityId === cid)
    if (community) {
      store.selectedNodeId = id
      store.entityDetail = {
        id,
        name: community.title,
        type: '章节',
        description: community.summary || '该章节暂无摘要。',
        communityPath: [{ level: 0, communityId: cid, title: community.title }],
        chunkCount: community.topEntities?.length ?? 0,
        __isCommunity: true,
      }
    }
    return
  }
  // 子图视图：点实体 → 拉详情
  store.loadEntityDetail(id)
}

async function onExpandNode(id) {
  if (!id) return
  // 章节视图：双击社区 → 钻入
  if (id.startsWith('community-')) {
    const cid = Number(id.slice('community-'.length))
    store.focusCommunity(cid)
    return
  }
  // 子图视图：双击实体 → 邻域扩展
  await store.expandNeighborhood(id, { limit: 50, mode: expandMode.value })
}

function onAskQuestion(entity) {
  if (!entity?.name) {
    ElMessage.warning('当前节点缺少名称，无法跳转问答。')
    return
  }
  router.push({ path: '/qa/ask', query: { topic: entity.name } })
}

function onBackToOverview() {
  store.backToCommunityOverview()
}

watch(
  () => store.errorMessage,
  (msg) => {
    if (msg && store.state !== GRAPH_STATE.ERROR) {
      ElMessage.warning(msg)
    }
  },
)

watch(preferredCourseId, async (next) => {
  if (next && next !== store.selectedCourseId) {
    await onCourseChange(next)
  }
})

onMounted(bootstrap)

const showCanvas = computed(
  () => store.state === GRAPH_STATE.READY || store.state === GRAPH_STATE.LOADING,
)
const showEmpty = computed(() => store.state === GRAPH_STATE.EMPTY)
const showNoActive = computed(() => store.state === GRAPH_STATE.NO_ACTIVE_INDEX)
const showError = computed(() => store.state === GRAPH_STATE.ERROR)
const noCoursesAvailable = computed(
  () => !store.coursesLoading && store.availableCourses.length === 0,
)
const courseNotChosen = computed(
  () => store.availableCourses.length > 0 && !store.selectedCourseId,
)

const focusedCommunityTitle = computed(() => {
  if (!focusedGraph.value) return ''
  return focusedGraph.value.community.title || `社区 ${focusedGraph.value.community.communityId}`
})

const breadcrumbHint = computed(() => {
  if (focusedGraph.value) {
    return `章节视图 › ${focusedCommunityTitle.value}`
  }
  if (store.communities?.length) {
    return `章节视图（${store.communities.length} 个章节，双击节点进入）`
  }
  return ''
})
</script>

<template>
  <div class="kg-page">
    <main class="kg-main">
      <header class="kg-head">
        <div class="kg-head-left">
          <h1 class="title">知识图谱</h1>
          <p class="sub">
            <template v-if="store.activeKnowledgeBase">
              当前知识库：{{ store.activeKnowledgeBase.name }}（KB #{{ store.activeKnowledgeBase.id }}，激活索引
              #{{ store.activeKnowledgeBase.activeIndexRunId }}）
            </template>
            <template v-else>课程概念网络浏览</template>
          </p>
          <p v-if="breadcrumbHint" class="breadcrumb">
            <span
              :class="['crumb', { clickable: focusedGraph }]"
              @click="onBackToOverview"
            >全部章节</span>
            <template v-if="focusedGraph">
              <span class="sep">›</span>
              <span class="crumb current">{{ focusedCommunityTitle }}</span>
            </template>
          </p>
        </div>
        <div class="kg-head-right">
          <el-select
            v-if="store.availableCourses.length > 0"
            :model-value="store.selectedCourseId"
            placeholder="请选择课程"
            class="course-select"
            :loading="store.coursesLoading"
            @update:model-value="onCourseChange"
          >
            <el-option
              v-for="course in store.availableCourses"
              :key="course.courseId"
              :label="course.courseName || course.courseId"
              :value="course.courseId"
            >
              <span>{{ course.courseName || course.courseId }}</span>
              <span v-if="course.memberStatus === 'member'" class="course-badge">已加入</span>
              <span v-else-if="course.accessPolicy === 'public'" class="course-badge alt">公开</span>
            </el-option>
          </el-select>
          <GlowButton
            v-if="focusedGraph"
            size="md"
            variant="ghost"
            @click="onBackToOverview"
          >
            返回章节
          </GlowButton>
          <el-radio-group
            v-model="expandMode"
            size="default"
            class="expand-mode-toggle"
            :disabled="!focusedGraph"
          >
            <el-radio-button value="merge">叠加</el-radio-button>
            <el-radio-button value="replace">聚焦</el-radio-button>
          </el-radio-group>
          <GlowButton size="md" :disabled="!store.selectedCourseId" @click="bootstrap">
            重新加载
          </GlowButton>
        </div>
      </header>

      <GlassCard tier="base" padding="md" class="canvas-card">
        <div v-if="noCoursesAvailable" class="state-block">
          <h3>暂无可见课程</h3>
          <p>当前账户尚未加入任何课程，请联系教师邀请加入，或先去课程列表浏览公开课程。</p>
          <GlowButton size="sm" @click="$router.push('/course/list')">去课程列表</GlowButton>
        </div>
        <div v-else-if="courseNotChosen" class="state-block">
          <h3>请先选择课程</h3>
          <p>从右上角选择一门课程开始浏览图谱。</p>
        </div>
        <div v-else-if="showNoActive" class="state-block">
          <h3>所选课程暂无可用知识库</h3>
          <p>当前课程下还没有激活的索引（activeIndexRunId 为空），等管理员构建并激活索引后再来浏览。</p>
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
            :nodes="canvasNodes"
            :edges="canvasEdges"
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

  .kg-head-left {
    flex: 1;
    min-width: 0;
  }

  .breadcrumb {
    margin: 6px 0 0;
    font-size: 12px;
    color: #64748b;
    display: flex;
    align-items: center;
    gap: 6px;

    .crumb.clickable {
      cursor: pointer;
      color: #0d9488;
      &:hover { text-decoration: underline; }
    }
    .crumb.current { color: #0f172a; font-weight: 500; }
    .sep { color: #94a3b8; }
  }

  .kg-head-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
  }

  .course-select {
    width: 240px;
  }

  .expand-mode-toggle {
    margin: 0 4px;
  }

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

.course-badge {
  margin-left: 8px;
  font-size: 11px;
  color: #0d9488;
  background: rgba(13, 148, 136, 0.12);
  padding: 1px 6px;
  border-radius: 8px;

  &.alt {
    color: #6366f1;
    background: rgba(99, 102, 241, 0.12);
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
