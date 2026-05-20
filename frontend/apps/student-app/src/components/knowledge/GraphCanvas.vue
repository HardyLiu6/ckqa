<!-- 知识图谱画布封装：内部使用 G6 v5，外层不感知具体实现 -->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import { Graph } from '@antv/g6'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  selectedId: { type: String, default: null },
})

const emit = defineEmits(['select', 'expand'])

const containerRef = ref(null)
let graph = null
let resizeObserver = null

const COMMUNITY_COLORS = [
  '#0d9488', '#6366f1', '#f97316', '#0ea5e9', '#a855f7',
  '#14b8a6', '#ef4444', '#22c55e', '#eab308', '#ec4899',
]

function pickColor(communityId) {
  if (communityId === null || communityId === undefined) return '#0d9488'
  const n = Number(communityId)
  if (!Number.isFinite(n)) return '#0d9488'
  return COMMUNITY_COLORS[Math.abs(n) % COMMUNITY_COLORS.length]
}

function clampLabel(raw, max = 14) {
  if (!raw) return ''
  const text = String(raw)
  return text.length > max ? text.slice(0, max - 1) + '…' : text
}

// 后端实体 name 是 GraphRAG 抽取的原文（多为大写英文：PROCESS、SEMAPHORE...），
// 该名称必须保留以匹配问答检索；但展示标签可以做轻量本地化：
// 1. 章节节点 → 直接展示完整 title（截到 18 字符）
// 2. 名称含中文 → 直接用 name
// 3. 纯英文 + 有 type → 用 type 中文 + 缩写后缀（如 "核心概念·SEM"）
// 4. 都没有 → 显示 type 或 id 短串
const PURE_ASCII = /^[A-Za-z0-9 _\-/().,'"]+$/
function buildDisplayName(node) {
  if (node?.__isCommunity) {
    return clampLabel(node?.name ?? '', 18)
  }
  const name = (node?.name ?? '').trim()
  const type = (node?.type ?? '').trim()
  const hasChinese = /[\u4e00-\u9fa5]/.test(name)
  if (hasChinese) return clampLabel(name)
  if (name && type && PURE_ASCII.test(name)) {
    const shortName = name.length > 8 ? name.slice(0, 7) + '…' : name
    return clampLabel(`${type}·${shortName}`)
  }
  if (name) return clampLabel(name)
  if (type) return clampLabel(type)
  return clampLabel(node?.id ?? '')
}

// 章节视图节点全部是 community，使用圆形布局；
// 子图视图（实体）使用 force 布局
function buildLayout(isCommunityView) {
  if (isCommunityView) {
    return {
      type: 'circular',
      ordering: 'topology',
      angleRatio: 1,
      animation: false,
    }
  }
  return {
    type: 'force',
    preventOverlap: true,
    // 碰撞半径较大确保节点之间留白
    nodeSize: 90,
    nodeSpacing: 30,
    linkDistance: 180,
    nodeStrength: -500,
    edgeStrength: 0.2,
    // 用更大的初始 alpha 重新激活布局，确保新节点被推开
    alpha: 0.5,
    alphaDecay: 0.04,
    alphaMin: 0.005,
    velocityDecay: 0.4,
    animation: false,
    // 让 G6 在节点重叠时强制分散
    clustering: false,
  }
}

function toGraphData(nodes, edges) {
  const nodeIds = new Set((nodes ?? []).map((n) => n?.id).filter(Boolean).map(String))
  const communityCount = (nodes ?? []).filter((n) => n?.__isCommunity).length
  const isCommunityView = nodes && nodes.length > 0 && communityCount === nodes.length
  const total = (nodes ?? []).length || 1
  return {
    isCommunityView,
    nodes: (nodes ?? []).map((node, idx) => {
      // 给每个节点一个确定但分散的初始坐标，避免 force 布局中所有节点
      // 都从 (0,0) 出发导致互相重合分不开。用极坐标分散在一个圆内。
      const angle = (idx / total) * Math.PI * 2
      const radius = 200 + (idx % 7) * 30
      return {
        id: String(node.id),
        // G6 v5 force 布局支持节点上指定初始 x/y
        x: Math.cos(angle) * radius,
        y: Math.sin(angle) * radius,
        data: {
          name: node.name ?? '',
          type: node.type ?? '',
          communityId: node.communityId ?? null,
          degree: node.degree ?? 0,
          labelText: buildDisplayName(node),
          color: pickColor(node.communityId),
          isCommunity: !!node.__isCommunity,
        },
      }
    }),
    edges: (edges ?? [])
      .filter((edge) => edge?.source && edge?.target
        && nodeIds.has(String(edge.source))
        && nodeIds.has(String(edge.target)))
      .map((edge) => ({
        id: String(edge.id),
        source: String(edge.source),
        target: String(edge.target),
        data: { weight: edge.weight ?? 0, description: edge.description ?? '' },
      })),
  }
}

async function renderAndFit() {
  if (!graph) return
  await graph.render()
  await graph.fitView()
}

onMounted(async () => {
  await nextTick()
  if (!containerRef.value) {
    console.error('[GraphCanvas] container ref missing')
    return
  }
  const rect = containerRef.value.getBoundingClientRect()
  const width = Math.max(400, Math.round(rect.width))
  const height = Math.max(360, Math.round(rect.height))

  const initialData = toGraphData(props.nodes, props.edges)

  try {
    graph = new Graph({
      container: containerRef.value,
      width,
      height,
      autoResize: true,
      data: initialData,
      layout: buildLayout(initialData.isCommunityView),
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
      node: {
        style: {
          // 章节节点更大（34~70），实体节点中等（22~36）
          size: (datum) => {
            const isCommunity = !!datum?.data?.isCommunity
            const degree = datum?.data?.degree ?? 0
            if (isCommunity) {
              return Math.max(34, Math.min(70, 30 + degree * 0.6))
            }
            return Math.max(22, Math.min(36, 18 + Math.log2(degree + 1) * 4))
          },
          fill: (datum) => datum?.data?.color ?? '#0d9488',
          stroke: '#ffffff',
          lineWidth: (datum) => (datum?.data?.isCommunity ? 2.5 : 1.5),
          labelText: (datum) => datum?.data?.labelText ?? datum?.id,
          labelPlacement: 'bottom',
          labelOffsetY: 6,
          labelFill: '#0f172a',
          labelFontSize: (datum) => (datum?.data?.isCommunity ? 12 : 11),
          labelFontWeight: (datum) => (datum?.data?.isCommunity ? 600 : 400),
          labelBackground: true,
          labelBackgroundFill: 'rgba(255,255,255,0.9)',
          labelBackgroundRadius: 4,
        },
        state: {
          selected: { stroke: '#f97316', lineWidth: 3 },
        },
      },
      edge: {
        style: {
          stroke: '#cbd5e1',
          lineWidth: 1,
          endArrow: true,
          endArrowSize: 6,
        },
      },
    })

    graph.on('node:click', (evt) => {
      const id = evt?.target?.id
      if (id) emit('select', id)
    })
    graph.on('node:dblclick', (evt) => {
      const id = evt?.target?.id
      if (id) emit('expand', id)
    })

    await renderAndFit()
  } catch (err) {
    console.error('[GraphCanvas] init failed:', err)
  }

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (!graph || !containerRef.value) return
      const r = containerRef.value.getBoundingClientRect()
      const w = Math.max(200, Math.round(r.width))
      const h = Math.max(200, Math.round(r.height))
      try {
        graph.setSize(w, h)
        graph.fitView()
      } catch (err) {
        console.error('[GraphCanvas] resize failed:', err)
      }
    })
    resizeObserver.observe(containerRef.value)
  }
})

watch(
  () => [props.nodes, props.edges],
  async ([nodes, edges]) => {
    if (!graph) return
    const data = toGraphData(nodes, edges)
    try {
      // 切换视图时同时切换布局；setData 后必须显式 layout() 才能让新数据生效
      graph.setOptions({ layout: buildLayout(data.isCommunityView) })
      graph.setData(data)
      await graph.render()
      // 关键：重新跑布局，使用我们在 toGraphData 里给每个节点设置的初始 x/y 作为起点
      // 否则旧节点的位置会被保留导致新节点叠在旧节点上
      try { await graph.layout() } catch (e) { /* 部分布局不支持单独 layout 调用 */ }
      await graph.fitView()
    } catch (err) {
      console.error('[GraphCanvas] update failed:', err)
    }
  },
  { deep: true },
)

watch(
  () => props.selectedId,
  (id) => {
    if (!graph || !id) return
    try { graph.setElementState(String(id), 'selected') }
    catch (err) { console.error('[GraphCanvas] highlight failed:', err) }
  },
)

onBeforeUnmount(() => {
  if (resizeObserver) {
    try { resizeObserver.disconnect() } catch { /* */ }
    resizeObserver = null
  }
  if (graph) {
    try { graph.destroy() } catch { /* */ }
    graph = null
  }
})
</script>

<template>
  <div ref="containerRef" class="graph-canvas" />
</template>

<style scoped>
.graph-canvas {
  width: 100%;
  height: 100%;
  min-height: 480px;
  position: relative;
}
</style>
