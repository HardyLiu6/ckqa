<!-- 知识图谱画布封装：内部使用 G6 v5，外层不感知具体实现 -->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph } from '@antv/g6'

const props = defineProps({
  nodes: {
    type: Array,
    default: () => [],
  },
  edges: {
    type: Array,
    default: () => [],
  },
  selectedId: {
    type: String,
    default: null,
  },
})

const emit = defineEmits(['select', 'expand'])

const containerRef = ref(null)
let graph = null

// 把后端返回的 GraphNodeResponse / GraphEdgeResponse 转成 G6 v5 期望的数据形态
function toGraphData(nodes, edges) {
  return {
    nodes: (nodes ?? []).map((node) => ({
      id: node.id,
      data: {
        name: node.name ?? '',
        type: node.type ?? '',
        communityId: node.communityId ?? null,
        degree: node.degree ?? 0,
      },
      style: {
        labelText: node.name || node.id,
      },
    })),
    edges: (edges ?? [])
      .filter((edge) => edge.source && edge.target)
      .map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        data: {
          weight: edge.weight ?? 0,
          description: edge.description ?? '',
        },
      })),
  }
}

function attachListeners(instance) {
  instance.on('node:click', (evt) => {
    const id = evt?.target?.id
    if (id) {
      emit('select', id)
    }
  })
  instance.on('node:dblclick', (evt) => {
    const id = evt?.target?.id
    if (id) {
      emit('expand', id)
    }
  })
}

function highlightSelection() {
  if (!graph || !props.selectedId) {
    return
  }
  try {
    graph.setElementState(props.selectedId, 'selected')
  } catch (err) {
    // G6 在节点尚未渲染完成时 setElementState 可能抛错，吞掉避免影响主流程
  }
}

onMounted(() => {
  if (!containerRef.value) {
    return
  }
  graph = new Graph({
    container: containerRef.value,
    autoFit: 'view',
    data: toGraphData(props.nodes, props.edges),
    layout: {
      type: 'force',
      preventOverlap: true,
      linkDistance: 90,
      nodeStrength: -50,
    },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
    node: {
      style: {
        size: 28,
        fill: '#0d9488',
        stroke: '#0f766e',
        labelText: (d) => d?.style?.labelText ?? d?.id,
        labelPlacement: 'center',
        labelFill: '#ffffff',
        labelFontSize: 11,
      },
      state: {
        selected: {
          fill: '#5eead4',
          stroke: '#0f766e',
          lineWidth: 3,
        },
      },
    },
    edge: {
      style: {
        stroke: '#94a3b8',
        endArrow: true,
        endArrowSize: 8,
      },
    },
  })
  graph.render().then(highlightSelection).catch(() => {})
  attachListeners(graph)
})

watch(
  () => [props.nodes, props.edges],
  ([nodes, edges]) => {
    if (!graph) {
      return
    }
    graph.setData(toGraphData(nodes, edges))
    graph.render().then(highlightSelection).catch(() => {})
  },
  { deep: true },
)

watch(
  () => props.selectedId,
  () => highlightSelection(),
)

onBeforeUnmount(() => {
  if (graph) {
    try {
      graph.destroy()
    } catch (err) {
      // 防御性处理
    }
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
  min-height: 360px;
}
</style>
