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
  // 仅刷新图谱数据，保留课程选择器列表
  store.activeKnowledgeBase = null
  store.entityDetail = null
  store.selectedNodeId = null
  await loadGraphForCourse(courseId)
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

// URL ?courseId= 由外部链接变化时也同步刷新（极少触发，但保证行为一致）
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

  .kg-head-left {
    flex: 1;
    min-width: 0;
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
