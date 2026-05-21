<!-- 知识图谱画布：接收带坐标的 nodes/edges，纯渲染 + 交互事件 -->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'

const props = defineProps({
  /** 节点数组，每个节点必须有 id, x, y；可选 data.* */
  nodes: { type: Array, default: () => [] },
  /** 边数组，每条边必须有 id, source, target */
  edges: { type: Array, default: () => [] },
  /** 当前选中节点 id */
  selectedId: { type: String, default: null },
  /** 需要画布平移聚焦到的节点 id */
  focusNodeId: { type: String, default: null },
})

const emit = defineEmits(['select', 'expand'])

const containerRef = ref(null)
let graph = null
let resizeObserver = null
let disposed = false
let graphCtorPromise = null

function loadGraphCtor() {
  if (!graphCtorPromise) {
    graphCtorPromise = import('@antv/g6').then((module) => module.Graph)
  }
  return graphCtorPromise
}

function buildGraphData(nodes, edges) {
  const nodeIds = new Set((nodes ?? []).map((n) => String(n?.id)).filter(Boolean))
  return {
    nodes: (nodes ?? []).map((n) => ({
      id: String(n.id),
      style: {
        x: n.x ?? 0,
        y: n.y ?? 0,
        size: n.size ?? 28,
        fill: n.color ?? '#0d9488',
        stroke: n.stroke ?? '#ffffff',
        lineWidth: n.lineWidth ?? 1.5,
        labelText: n.label ?? '',
        labelPlacement: 'bottom',
        labelOffsetY: 6,
        labelFill: '#0f172a',
        labelFontSize: n.labelFontSize ?? 11,
        labelFontWeight: n.labelFontWeight ?? 400,
        labelBackground: true,
        labelBackgroundFill: 'rgba(255,255,255,0.9)',
        labelBackgroundRadius: 4,
      },
    })),
    edges: (edges ?? [])
      .filter((e) => e?.source && e?.target && nodeIds.has(String(e.source)) && nodeIds.has(String(e.target)))
      .map((e) => ({
        id: String(e.id),
        source: String(e.source),
        target: String(e.target),
        style: {
          stroke: e.color ?? '#cbd5e1',
          lineWidth: 1,
          endArrow: true,
          endArrowSize: 6,
        },
      })),
  }
}

onMounted(async () => {
  await nextTick()
  if (!containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  const width = Math.max(400, Math.round(rect.width))
  const height = Math.max(360, Math.round(rect.height))

  try {
    const Graph = await loadGraphCtor()
    if (disposed || !containerRef.value) return

    graph = new Graph({
      container: containerRef.value,
      width,
      height,
      autoResize: true,
      // 不使用内置布局——坐标由外层计算好
      layout: null,
      data: buildGraphData(props.nodes, props.edges),
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
      node: {
        state: {
          selected: { stroke: '#f97316', lineWidth: 3 },
        },
      },
      edge: {},
    })

    graph.on('node:click', (evt) => {
      const id = evt?.target?.id
      if (id) emit('select', id)
    })
    graph.on('node:dblclick', (evt) => {
      const id = evt?.target?.id
      if (id) emit('expand', id)
    })

    await graph.render()
    await graph.fitView()
    doFocus()
  } catch (err) {
    console.error('[GraphCanvas] init failed:', err)
  }

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (!graph || !containerRef.value) return
      const r = containerRef.value.getBoundingClientRect()
      try {
        graph.setSize(Math.max(200, Math.round(r.width)), Math.max(200, Math.round(r.height)))
        graph.fitView()
      } catch { /* */ }
    })
    resizeObserver.observe(containerRef.value)
  }
})

watch(
  () => [props.nodes, props.edges],
  async () => {
    if (!graph) return
    try {
      graph.setData(buildGraphData(props.nodes, props.edges))
      await graph.render()
      await graph.fitView()
      doFocus()
    } catch (err) {
      console.error('[GraphCanvas] update failed:', err)
    }
  },
  { deep: true },
)

watch(() => props.selectedId, (id) => {
  if (!graph || !id) return
  try { graph.setElementState(String(id), 'selected') } catch { /* */ }
})

watch(() => props.focusNodeId, () => doFocus())

function doFocus() {
  if (!graph || !props.focusNodeId) return
  try {
    graph.focusElement(String(props.focusNodeId), { animation: { duration: 300 } })
  } catch { /* */ }
}

onBeforeUnmount(() => {
  disposed = true
  if (resizeObserver) { try { resizeObserver.disconnect() } catch { /* */ } }
  if (graph) { try { graph.destroy() } catch { /* */ } }
  graph = null
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
