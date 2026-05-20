// 学生端知识图谱 Pinia store
// 仅 MVP 阶段所需的最小状态：当前知识库 / 节点 / 边 / 选中节点 / 加载状态
// 多跳邻域历史栈、社区钻取留给后续 PR

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

import {
  fetchCourseKnowledgeBases,
  fetchEntityDetail,
  fetchEntityNeighborhood,
  fetchGraphOverview,
} from '@/api/graph'
import { listCourses } from '@/api/courses'

export const GRAPH_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  READY: 'ready',
  EMPTY: 'empty',
  NO_ACTIVE_INDEX: 'no-active-index',
  ERROR: 'error',
})

export const useGraphStore = defineStore('graph', () => {
  // 当前学生可见课程清单（图谱页课程选择器用）
  const availableCourses = ref([])
  const selectedCourseId = ref('')
  const coursesLoading = ref(false)

  const knowledgeBases = ref([])
  const activeKnowledgeBase = ref(null)

  // 章节级社区（默认视图）+ 当前钻入的社区 id
  const communities = ref([])
  const focusedCommunityId = ref(null)

  // 画布数据：以 id 为键去重，避免 G6 changeData 时把同一节点重复加入
  const nodesById = ref(new Map())
  const edgesById = ref(new Map())

  const selectedNodeId = ref(null)
  const entityDetail = ref(null) // { id, name, type, description, communityPath, chunkCount }

  const state = ref(GRAPH_STATE.IDLE)
  const errorMessage = ref('')

  const nodes = computed(() => Array.from(nodesById.value.values()))
  const edges = computed(() => Array.from(edgesById.value.values()))

  function reset() {
    availableCourses.value = []
    selectedCourseId.value = ''
    coursesLoading.value = false
    knowledgeBases.value = []
    activeKnowledgeBase.value = null
    communities.value = []
    focusedCommunityId.value = null
    nodesById.value = new Map()
    edgesById.value = new Map()
    selectedNodeId.value = null
    entityDetail.value = null
    state.value = GRAPH_STATE.IDLE
    errorMessage.value = ''
  }

  function setError(err, fallback = '加载失败，请稍后重试') {
    state.value = GRAPH_STATE.ERROR
    errorMessage.value = err?.message || fallback
  }

  /**
   * 拉取学生当前可见的课程列表，挑选默认课程。
   *
   * @param {string} preferredCourseId 来自 URL ?courseId= 的优先课程
   * @returns {Promise<string>} 实际选定的 courseId（可能为空）
   */
  async function loadAvailableCourses(preferredCourseId = '') {
    coursesLoading.value = true
    try {
      const result = await listCourses({ size: 50 })
      const items = Array.isArray(result?.items) ? result.items : []
      availableCourses.value = items
      if (items.length === 0) {
        selectedCourseId.value = ''
        return ''
      }
      // 默认优先级：URL 指定 -> 已加入 + 有激活索引 -> 已加入 -> 第一个
      const matchByCourseId = (id) => items.find((c) => c?.courseId === id)
      let chosen = preferredCourseId ? matchByCourseId(preferredCourseId) : null
      if (!chosen) {
        chosen = items.find(
          (c) => c?.memberStatus === 'member' && (c?.activeKnowledgeBaseCount ?? 0) > 0,
        )
      }
      if (!chosen) {
        chosen = items.find((c) => c?.memberStatus === 'member')
      }
      if (!chosen) {
        chosen = items[0]
      }
      selectedCourseId.value = chosen?.courseId ?? ''
      return selectedCourseId.value
    } catch (err) {
      availableCourses.value = []
      selectedCourseId.value = ''
      setError(err, '获取可见课程失败')
      return ''
    } finally {
      coursesLoading.value = false
    }
  }

  /**
   * 进入图谱页时调用：拉课程知识库摘要并选取激活索引就绪的第一个 KB。
   *
   * @param {string} courseId
   * @returns {Promise<boolean>} 是否成功选到可用知识库
   */
  async function selectKnowledgeBaseForCourse(courseId) {
    if (!courseId) {
      state.value = GRAPH_STATE.NO_ACTIVE_INDEX
      return false
    }
    state.value = GRAPH_STATE.LOADING
    errorMessage.value = ''
    try {
      const list = await fetchCourseKnowledgeBases(courseId)
      knowledgeBases.value = Array.isArray(list) ? list : []
      const ready = knowledgeBases.value.find((kb) => kb && kb.activeIndexRunId != null)
      if (!ready) {
        activeKnowledgeBase.value = null
        state.value = GRAPH_STATE.NO_ACTIVE_INDEX
        return false
      }
      activeKnowledgeBase.value = ready
      return true
    } catch (err) {
      setError(err, '获取课程知识库失败')
      return false
    }
  }

  /**
   * 加载图谱总览数据。
   */
  async function loadOverview({ level = 0, topN } = {}) {
    if (!activeKnowledgeBase.value) {
      state.value = GRAPH_STATE.NO_ACTIVE_INDEX
      return
    }
    state.value = GRAPH_STATE.LOADING
    errorMessage.value = ''
    try {
      const data = await fetchGraphOverview(activeKnowledgeBase.value.id, { level, topN })
      communities.value = Array.isArray(data?.communities) ? data.communities : []
      focusedCommunityId.value = null
      mergeNodes(data?.nodes ?? [], { replace: true })
      mergeEdges(data?.edges ?? [], { replace: true })
      if (communities.value.length === 0 && nodesById.value.size === 0) {
        state.value = GRAPH_STATE.EMPTY
      } else {
        state.value = GRAPH_STATE.READY
      }
    } catch (err) {
      setError(err, '加载知识图谱失败')
    }
  }

  /**
   * 钻入指定社区，让画布只展示这个社区下的实体子图。
   * 不发新请求，直接基于已有 nodes/edges 过滤。
   */
  function focusCommunity(communityId) {
    focusedCommunityId.value = communityId
    selectedNodeId.value = null
    entityDetail.value = null
  }

  /**
   * 退出社区视图，回到顶层章节视图。
   */
  function backToCommunityOverview() {
    focusedCommunityId.value = null
    selectedNodeId.value = null
    entityDetail.value = null
  }

  /**
   * 叠加模式：把社区的 topEntities 添加到 store.nodes，不清空已有节点。
   * 同时加一条虚拟边从 community 合成节点到每个 topEntity。
   */
  function addCommunityChildren(community) {
    if (!community) return
    const topEntities = community.topEntities ?? []
    mergeNodes(topEntities)
    const communityNodeId = `community-${community.communityId}`
    const newEdges = topEntities
      .filter((e) => e && e.id)
      .map((e) => ({
        id: `link-${communityNodeId}-${e.id}`,
        source: communityNodeId,
        target: e.id,
        weight: 0,
        description: '',
      }))
    mergeEdges(newEdges)
  }

  /**
   * 聚焦模式：清空画布，只放该社区节点 + topEntities。
   */
  function replaceWithCommunityFocus(community) {
    if (!community) return
    const topEntities = community.topEntities ?? []
    mergeNodes(topEntities, { replace: true })
    const communityNodeId = `community-${community.communityId}`
    const newEdges = topEntities
      .filter((e) => e && e.id)
      .map((e) => ({
        id: `link-${communityNodeId}-${e.id}`,
        source: communityNodeId,
        target: e.id,
        weight: 0,
        description: '',
      }))
    mergeEdges(newEdges, { replace: true })
    selectedNodeId.value = null
    entityDetail.value = null
  }

  /**
   * 重置探索：清空实体节点/边，回到纯章节视图。
   */
  function resetExploration() {
    nodesById.value = new Map()
    edgesById.value = new Map()
    selectedNodeId.value = null
    entityDetail.value = null
  }

  /**
   * 拉取实体详情（点选节点时调用）。
   */
  async function loadEntityDetail(entityId) {
    if (!activeKnowledgeBase.value || !entityId) {
      return
    }
    selectedNodeId.value = entityId
    try {
      entityDetail.value = await fetchEntityDetail(activeKnowledgeBase.value.id, entityId)
    } catch (err) {
      entityDetail.value = null
      // 详情失败不抛到全局；以错误消息形式提示，画布不动
      errorMessage.value = err?.message || '加载实体详情失败'
    }
  }

  /**
   * 双击节点扩展邻域，把新节点 / 新边并入画布。
   * @param {{ limit?: number, mode?: 'merge' | 'replace' }} options
   *   mode='merge'  默认，新邻居叠加到已有图上
   *   mode='replace' 只显示中心节点 + 邻居，旧节点全部清空
   */
  async function expandNeighborhood(entityId, { limit, mode = 'merge' } = {}) {
    if (!activeKnowledgeBase.value || !entityId) {
      return
    }
    try {
      const data = await fetchEntityNeighborhood(
        activeKnowledgeBase.value.id,
        entityId,
        { depth: 1, limit },
      )
      const replace = mode === 'replace'
      mergeNodes(data?.nodes ?? [], { replace })
      mergeEdges(data?.edges ?? [], { replace })
    } catch (err) {
      errorMessage.value = err?.message || '扩展邻域失败'
    }
  }

  /**
   * 拉取邻域原始数据（不写入 store），供视图层自行计算坐标。
   */
  async function fetchNeighborhoodRaw(entityId, { limit } = {}) {
    if (!activeKnowledgeBase.value || !entityId) return null
    try {
      return await fetchEntityNeighborhood(
        activeKnowledgeBase.value.id,
        entityId,
        { depth: 1, limit },
      )
    } catch (err) {
      errorMessage.value = err?.message || '扩展邻域失败'
      return null
    }
  }

  function mergeNodes(list, { replace = false } = {}) {
    if (replace) {
      nodesById.value = new Map()
    }
    const next = new Map(nodesById.value)
    for (const node of list) {
      if (node && node.id) {
        next.set(node.id, node)
      }
    }
    nodesById.value = next
  }

  function mergeEdges(list, { replace = false } = {}) {
    if (replace) {
      edgesById.value = new Map()
    }
    const next = new Map(edgesById.value)
    for (const edge of list) {
      if (edge && edge.id) {
        next.set(edge.id, edge)
      }
    }
    edgesById.value = next
  }

  return {
    availableCourses,
    selectedCourseId,
    coursesLoading,
    knowledgeBases,
    activeKnowledgeBase,
    communities,
    focusedCommunityId,
    selectedNodeId,
    entityDetail,
    state,
    errorMessage,
    nodes,
    edges,
    reset,
    loadAvailableCourses,
    selectKnowledgeBaseForCourse,
    loadOverview,
    loadEntityDetail,
    expandNeighborhood,
    fetchNeighborhoodRaw,
    focusCommunity,
    backToCommunityOverview,
    addCommunityChildren,
    replaceWithCommunityFocus,
    resetExploration,
  }
})
