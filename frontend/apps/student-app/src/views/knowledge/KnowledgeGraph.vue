<!-- 知识图谱浏览页 -->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import GraphCanvas from '@/components/knowledge/GraphCanvas.vue'
import GraphLegend from '@/components/knowledge/GraphLegend.vue'
import EntityDetailPanel from '@/components/knowledge/EntityDetailPanel.vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import { useGraphStore, GRAPH_STATE } from '@/stores/graph'

const route = useRoute()
const router = useRouter()
const store = useGraphStore()

const expandMode = ref('merge')
const focusNodeId = ref(null)

// ========== 颜色 ==========
const COLORS = [
  '#0d9488', '#6366f1', '#f97316', '#0ea5e9', '#a855f7',
  '#14b8a6', '#ef4444', '#22c55e', '#eab308', '#ec4899',
]
function pickColor(communityId) {
  if (communityId == null) return '#0d9488'
  return COLORS[Math.abs(Number(communityId)) % COLORS.length]
}
function clampLabel(raw, max = 16) {
  if (!raw) return ''
  return raw.length > max ? raw.slice(0, max - 1) + '…' : raw
}

// ========== 坐标计算 ==========
// 所有节点坐标由这里计算好，GraphCanvas 只负责渲染
const renderedNodes = ref([])
const renderedEdges = ref([])

// 章节节点坐标：circular 布局
function layoutCommunities(communities, cx = 400, cy = 300, radius = 250) {
  const n = communities.length
  return communities.map((c, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    const rank = Number(c.rank) || 1
    const maxRank = Math.max(...communities.map((x) => Number(x.rank) || 1), 1)
    const size = 30 + (rank / maxRank) * 40
    return {
      id: `community-${c.communityId}`,
      x: cx + Math.cos(angle) * radius,
      y: cy + Math.sin(angle) * radius,
      size,
      color: pickColor(c.communityId),
      stroke: '#ffffff',
      lineWidth: 2.5,
      label: clampLabel(c.title || `社区 ${c.communityId}`, 16),
      labelFontSize: 12,
      labelFontWeight: 600,
      __isCommunity: true,
      __raw: c,
    }
  })
}

// 子节点坐标：围绕父节点极坐标分布
function layoutChildrenAround(parentNode, children, radiusOffset = 120) {
  const n = children.length
  const px = parentNode.x
  const py = parentNode.y
  return children.map((child, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    return {
      id: child.id,
      x: px + Math.cos(angle) * radiusOffset,
      y: py + Math.sin(angle) * radiusOffset,
      size: 22,
      color: pickColor(child.communityId ?? parentNode.__raw?.communityId),
      stroke: '#ffffff',
      lineWidth: 1.5,
      label: clampLabel(child.name || child.id, 14),
      labelFontSize: 11,
      labelFontWeight: 400,
    }
  })
}

function buildInitialView() {
  const communities = store.communities ?? []
  if (communities.length === 0) {
    renderedNodes.value = []
    renderedEdges.value = []
    return
  }
  renderedNodes.value = layoutCommunities(communities)
  renderedEdges.value = []
  focusNodeId.value = null
}

function expandCommunityNode(communityNodeId) {
  const cid = Number(communityNodeId.replace('community-', ''))
  const community = (store.communities ?? []).find((c) => c.communityId === cid)
  if (!community) return

  const topEntities = community.topEntities ?? []
  if (topEntities.length === 0) return

  const parentNode = renderedNodes.value.find((n) => n.id === communityNodeId)
  if (!parentNode) return

  if (expandMode.value === 'replace') {
    // 聚焦模式：只显示该章节 + 子节点
    const children = layoutChildrenAround(parentNode, topEntities)
    renderedNodes.value = [parentNode, ...children]
    renderedEdges.value = children.map((child) => ({
      id: `edge-${communityNodeId}-${child.id}`,
      source: communityNodeId,
      target: child.id,
      color: '#94a3b8',
    }))
  } else {
    // 叠加模式：在现有图上追加子节点
    const existingIds = new Set(renderedNodes.value.map((n) => n.id))
    const newChildren = topEntities.filter((e) => !existingIds.has(e.id))
    if (newChildren.length === 0) return
    const children = layoutChildrenAround(parentNode, newChildren)
    renderedNodes.value = [...renderedNodes.value, ...children]
    const newEdges = children.map((child) => ({
      id: `edge-${communityNodeId}-${child.id}`,
      source: communityNodeId,
      target: child.id,
      color: '#94a3b8',
    }))
    renderedEdges.value = [...renderedEdges.value, ...newEdges]
  }
  focusNodeId.value = communityNodeId
}

async function expandEntityNode(entityId) {
  if (!store.activeKnowledgeBase) return
  const data = await store.fetchNeighborhoodRaw(entityId, { limit: 30 })
  if (!data) return

  const parentNode = renderedNodes.value.find((n) => n.id === entityId)
  if (!parentNode) return

  const neighbors = (data.nodes ?? []).filter((n) => n.id !== entityId)

  if (expandMode.value === 'replace') {
    const children = layoutChildrenAround(parentNode, neighbors)
    renderedNodes.value = [parentNode, ...children]
    renderedEdges.value = (data.edges ?? []).map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      color: '#94a3b8',
    }))
  } else {
    const existingIds = new Set(renderedNodes.value.map((n) => n.id))
    const newNeighbors = neighbors.filter((n) => !existingIds.has(n.id))
    const children = layoutChildrenAround(parentNode, newNeighbors)
    renderedNodes.value = [...renderedNodes.value, ...children]
    const existingEdgeIds = new Set(renderedEdges.value.map((e) => e.id))
    const newEdges = (data.edges ?? [])
      .filter((e) => !existingEdgeIds.has(e.id))
      .map((e) => ({ id: e.id, source: e.source, target: e.target, color: '#94a3b8' }))
    renderedEdges.value = [...renderedEdges.value, ...newEdges]
  }
  focusNodeId.value = entityId
}

// ========== 事件处理 ==========
function onSelectNode(id) {
  if (!id) return
  if (id.startsWith('community-')) {
    const cid = Number(id.replace('community-', ''))
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
      }
    }
    return
  }
  store.loadEntityDetail(id)
}

async function onExpandNode(id) {
  if (!id) return
  if (id.startsWith('community-')) {
    expandCommunityNode(id)
    return
  }
  await expandEntityNode(id)
}

function onAskQuestion(entity) {
  if (!entity?.name) {
    ElMessage.warning('当前节点缺少名称，无法跳转问答。')
    return
  }
  router.push({ path: '/qa/ask', query: { topic: entity.name } })
}

function onBackToOverview() {
  buildInitialView()
  store.selectedNodeId = null
  store.entityDetail = null
}

// ========== 课程选择 ==========
const preferredCourseId = computed(() => {
  const raw = route.query.courseId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : ''
})

async function loadGraphForCourse(courseId) {
  if (!courseId) return
  const ok = await store.selectKnowledgeBaseForCourse(courseId)
  if (!ok) return
  await store.loadOverview({ level: 0, topN: 20 })
  buildInitialView()
}

async function bootstrap() {
  store.reset()
  renderedNodes.value = []
  renderedEdges.value = []
  const chosen = await store.loadAvailableCourses(preferredCourseId.value)
  if (chosen && chosen !== preferredCourseId.value) {
    router.replace({ query: { ...route.query, courseId: chosen } })
  }
  await loadGraphForCourse(chosen)
}

async function onCourseChange(courseId) {
  if (!courseId) return
  store.selectedCourseId = courseId
  router.replace({ query: { ...route.query, courseId } })
  store.activeKnowledgeBase = null
  store.entityDetail = null
  store.selectedNodeId = null
  renderedNodes.value = []
  renderedEdges.value = []
  await loadGraphForCourse(courseId)
}

watch(() => store.errorMessage, (msg) => {
  if (msg && store.state !== GRAPH_STATE.ERROR) ElMessage.warning(msg)
})

onMounted(bootstrap)

// ========== 图例数据 ==========
const legendCommunities = computed(() => {
  return (store.communities ?? []).map((c) => ({
    name: clampLabel(c.title || `社区 ${c.communityId}`, 20),
    color: pickColor(c.communityId),
  }))
})

// ========== 状态 ==========
const showCanvas = computed(() => store.state === GRAPH_STATE.READY || store.state === GRAPH_STATE.LOADING)
const showEmpty = computed(() => store.state === GRAPH_STATE.EMPTY)
const showNoActive = computed(() => store.state === GRAPH_STATE.NO_ACTIVE_INDEX)
const showError = computed(() => store.state === GRAPH_STATE.ERROR)
const noCoursesAvailable = computed(() => !store.coursesLoading && store.availableCourses.length === 0)
const courseNotChosen = computed(() => store.availableCourses.length > 0 && !store.selectedCourseId)
const isExploring = computed(() => renderedNodes.value.some((n) => !n.__isCommunity))
</script>

<template>
  <div class="kg-page">
    <main class="kg-main">
      <header class="kg-head">
        <div class="kg-head-left">
          <h1 class="title">知识图谱</h1>
          <p class="sub">
            <template v-if="store.activeKnowledgeBase">
              当前知识库：{{ store.activeKnowledgeBase.name }}
            </template>
            <template v-else>课程概念网络浏览</template>
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
            />
          </el-select>
          <el-radio-group v-model="expandMode" size="small" class="expand-mode-toggle">
            <el-radio-button value="merge">叠加</el-radio-button>
            <el-radio-button value="replace">聚焦</el-radio-button>
          </el-radio-group>
          <GlowButton v-if="isExploring" size="md" variant="ghost" @click="onBackToOverview">
            返回章节
          </GlowButton>
          <GlowButton size="md" :disabled="!store.selectedCourseId" @click="bootstrap">
            重新加载
          </GlowButton>
        </div>
      </header>

      <GlassCard tier="base" padding="md" class="canvas-card">
        <div v-if="noCoursesAvailable" class="state-block">
          <h3>暂无可见课程</h3>
          <p>当前账户尚未加入任何课程。</p>
          <GlowButton size="sm" @click="$router.push('/course/list')">去课程列表</GlowButton>
        </div>
        <div v-else-if="courseNotChosen" class="state-block">
          <h3>请先选择课程</h3>
          <p>从右上角选择一门课程开始浏览图谱。</p>
        </div>
        <div v-else-if="showNoActive" class="state-block">
          <h3>所选课程暂无可用知识库</h3>
          <p>等管理员构建并激活索引后再来浏览。</p>
        </div>
        <div v-else-if="showError" class="state-block error">
          <h3>加载失败</h3>
          <p>{{ store.errorMessage || '请稍后重试。' }}</p>
          <GlowButton size="sm" @click="bootstrap">重试</GlowButton>
        </div>
        <div v-else-if="showEmpty" class="state-block">
          <h3>当前社区暂无可视节点</h3>
        </div>

        <div v-show="showCanvas" class="canvas-wrap">
          <div v-if="store.state === GRAPH_STATE.LOADING" class="loading-mask">加载中…</div>
          <GraphCanvas
            :nodes="renderedNodes"
            :edges="renderedEdges"
            :selected-id="store.selectedNodeId"
            :focus-node-id="focusNodeId"
            @select="onSelectNode"
            @expand="onExpandNode"
          />
          <GraphLegend :communities="legendCommunities" />
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
  flex-wrap: wrap;

  .kg-head-left { flex: 1; min-width: 0; }
  .kg-head-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
    flex-wrap: wrap;
  }

  .course-select { width: 200px; }
  .expand-mode-toggle { margin: 0; }

  .title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 800;
    color: #0f172a;
    margin: 0;
  }
  .sub { margin: 4px 0 0; font-size: 13px; color: #64748b; }
}

.canvas-card {
  flex: 1;
  display: flex;
  min-height: 420px;
  border-color: rgba(13, 148, 136, 0.2) !important;
  position: relative;
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
  z-index: 5;
}

.state-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  text-align: center;
  h3 { margin: 0; color: #0f172a; font-size: 16px; }
  p { margin: 0; color: #64748b; font-size: 13px; line-height: 1.6; }
  &.error h3 { color: #b91c1c; }
}

.kg-aside {
  position: sticky;
  top: 80px;
  align-self: flex-start;
}
</style>
