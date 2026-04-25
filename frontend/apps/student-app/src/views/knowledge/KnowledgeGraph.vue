<!-- 知识图谱 · SVG 交互式视觉稿 -->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import kgData from '@/mock/knowledge.json'
import { ArrowRight, Plus, Minus } from '@element-plus/icons-vue'

const router = useRouter()

const nodes = ref([...kgData.nodes])
const edges = ref([...kgData.edges])
const selectedId = ref('os')

const selected = computed(() => {
  const base = nodes.value.find((n) => n.id === selectedId.value)
  const detail = kgData.details[selectedId.value] || null
  return { ...base, detail }
})

// 视口变换
const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

// 拖拽状态
const isDragging = ref(false)
let lastX = 0
let lastY = 0

function onMouseDown(e) {
  isDragging.value = true
  lastX = e.clientX
  lastY = e.clientY
}
function onMouseMove(e) {
  if (!isDragging.value) return
  offsetX.value += e.clientX - lastX
  offsetY.value += e.clientY - lastY
  lastX = e.clientX
  lastY = e.clientY
}
function onMouseUp() { isDragging.value = false }

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY > 0 ? 0.9 : 1.1
  scale.value = Math.max(0.5, Math.min(2.5, scale.value * factor))
}

function zoom(factor) {
  scale.value = Math.max(0.5, Math.min(2.5, scale.value * factor))
}
function reset() {
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

function selectNode(id) {
  selectedId.value = id
}

function nodeColor(type) {
  if (type === 'root') return '#0d9488'
  if (type === 'concept') return '#14b8a6'
  if (type === 'instance') return '#2dd4bf'
  if (type === 'error') return '#f59e0b'
  return '#64748b'
}

function goQA() {
  if (!selected.value?.label) return
  router.push({ path: '/qa/ask', query: { topic: selected.value.label } })
}
</script>

<template>
  <div class="kg-page">
    <!-- 画布 -->
    <div
      class="canvas"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
      @wheel="onWheel"
    >
      <svg class="canvas-svg" viewBox="0 0 800 600" preserveAspectRatio="xMidYMid meet">
        <defs>
          <radialGradient id="nodeHalo">
            <stop offset="0%" stop-color="#5eead4" stop-opacity="0.7" />
            <stop offset="100%" stop-color="#0d9488" stop-opacity="0" />
          </radialGradient>
        </defs>

        <g :transform="`translate(${offsetX}, ${offsetY}) scale(${scale})`">
          <!-- 连线 -->
          <line
            v-for="(e, i) in edges"
            :key="i"
            :x1="nodes.find(n => n.id === e.from).x"
            :y1="nodes.find(n => n.id === e.from).y"
            :x2="nodes.find(n => n.id === e.to).x"
            :y2="nodes.find(n => n.id === e.to).y"
            stroke="rgba(13, 148, 136, 0.35)"
            stroke-width="1.2"
          />
          <!-- 节点 -->
          <g
            v-for="n in nodes"
            :key="n.id"
            class="node-group"
            :class="{ selected: selectedId === n.id }"
            @click.stop="selectNode(n.id)"
          >
            <circle :cx="n.x" :cy="n.y" :r="n.r * 2" fill="url(#nodeHalo)" />
            <circle
              :cx="n.x"
              :cy="n.y"
              :r="n.r"
              :fill="nodeColor(n.type)"
              stroke="#fff"
              stroke-width="2"
            />
            <text
              :x="n.x"
              :y="n.y + 4"
              text-anchor="middle"
              fill="#fff"
              :font-size="n.r > 12 ? 11 : 9"
              font-weight="600"
              font-family="Manrope"
              style="pointer-events: none;"
            >{{ n.label }}</text>
          </g>
        </g>
      </svg>

      <!-- 工具栏 -->
      <div class="tools">
        <button class="tool-btn" title="放大" @click="zoom(1.2)"><el-icon><Plus /></el-icon></button>
        <button class="tool-btn" title="缩小" @click="zoom(0.8)"><el-icon><Minus /></el-icon></button>
        <button class="tool-btn" title="重置" @click="reset">⊕</button>
      </div>
    </div>

    <!-- 右侧详情面板 -->
    <GlassCard tier="base" padding="md" class="detail-panel">
      <div v-if="selected" class="detail-content">
        <div class="detail-head">
          <span class="detail-dot" :style="{ background: nodeColor(selected.type) }"></span>
          <h3 class="detail-name">{{ selected.label }}</h3>
        </div>
        <div class="detail-tags">
          <ModuleTag module="knowledge" size="sm">{{ selected.type }}</ModuleTag>
          <ModuleTag v-if="selected.detail?.errorCount" module="analysis" size="sm">
            {{ selected.detail.errorCount }} 个错题
          </ModuleTag>
        </div>
        <p class="detail-desc">
          {{ selected.detail?.desc || '该节点暂无详细描述。' }}
        </p>

        <div v-if="selected.detail?.related?.length" class="related">
          <div class="related-label">关联知识点</div>
          <div class="related-list">
            <div
              v-for="r in selected.detail.related"
              :key="r"
              class="related-item"
            >{{ r }}</div>
          </div>
        </div>

        <GlowButton size="md" block @click="goQA">
          去问答
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.kg-page {
  --module-color-500: #0d9488;
  --module-color-700: #0f766e;
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 16px;
  padding: 16px;
  height: calc(100vh - 64px - 32px);

  @media (max-width: $bp-laptop) {
    grid-template-columns: 1fr;
    height: auto;
  }
}

.canvas {
  position: relative;
  background: linear-gradient(180deg, #f0fdfa, #fff);
  border: 1px solid rgba(13, 148, 136, 0.1);
  border-radius: $radius-xl;
  overflow: hidden;
  cursor: grab;

  &:active { cursor: grabbing; }

  .canvas-svg {
    width: 100%;
    height: 100%;
    user-select: none;
  }
}

.node-group {
  cursor: pointer;

  &:hover circle:last-of-type {
    filter: brightness(1.1);
  }
  &.selected circle:last-of-type {
    filter: drop-shadow(0 0 12px rgba(45, 212, 191, 0.8));
  }
}

.tools {
  position: absolute;
  top: 16px; right: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-btn {
  width: 32px; height: 32px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(13, 148, 136, 0.2);
  border-radius: $radius-md;
  color: #0d9488;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: #fff;
    box-shadow: 0 2px 8px rgba(13, 148, 136, 0.2);
  }
}

.detail-panel {
  border-color: rgba(13, 148, 136, 0.2) !important;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .detail-dot {
    width: 10px; height: 10px;
    border-radius: 50%;
    box-shadow: 0 0 8px currentColor;
  }
  .detail-name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
}

.detail-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.detail-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.65;
  margin-bottom: 16px;
}

.related {
  margin-bottom: 16px;

  .related-label {
    font-size: 11px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 6px;
  }
  .related-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .related-item {
    padding: 6px 10px;
    background: rgba(13, 148, 136, 0.05);
    border-left: 2px solid #14b8a6;
    border-radius: 0 $radius-md $radius-md 0;
    font-size: 12px;
    color: #0f172a;
  }
}
</style>
