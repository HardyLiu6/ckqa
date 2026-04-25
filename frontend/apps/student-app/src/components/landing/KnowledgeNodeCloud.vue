<!-- Hero 背景动态知识节点云 · Canvas 绘制 · 详见设计稿 §7.1 动效 ① -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const canvasRef = ref(null)
let ctx = null
let rafId = null
let nodes = []
let edges = []
let mouseX = -9999
let mouseY = -9999
let isVisible = true
let visibilityObserver = null

const NODE_COUNT = 20
const MAX_EDGES = 40
const CURSOR_RADIUS = 120
const DRIFT_MIN = 0.3
const DRIFT_MAX = 0.6

function initNodes(width, height) {
  nodes = []
  for (let i = 0; i < NODE_COUNT; i++) {
    nodes.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * (DRIFT_MAX - DRIFT_MIN) * 2,
      vy: (Math.random() - 0.5) * (DRIFT_MAX - DRIFT_MIN) * 2,
      radius: 2 + Math.random() * 3,
      baseRadius: 2 + Math.random() * 3,
    })
  }
  buildEdges()
}

function buildEdges() {
  edges = []
  for (let i = 0; i < nodes.length; i++) {
    const distances = []
    for (let j = 0; j < nodes.length; j++) {
      if (i === j) continue
      const dx = nodes[i].x - nodes[j].x
      const dy = nodes[i].y - nodes[j].y
      distances.push({ j, d: dx * dx + dy * dy })
    }
    distances.sort((a, b) => a.d - b.d)
    for (let k = 0; k < 2 && edges.length < MAX_EDGES; k++) {
      const pair = [i, distances[k].j].sort((a, b) => a - b).join('-')
      if (!edges.find((e) => e.key === pair)) {
        edges.push({ key: pair, a: i, b: distances[k].j })
      }
    }
  }
}

function distanceToCursor(node) {
  const dx = node.x - mouseX
  const dy = node.y - mouseY
  return Math.sqrt(dx * dx + dy * dy)
}

function step(width, height) {
  ctx.clearRect(0, 0, width, height)

  for (const n of nodes) {
    n.x += n.vx
    n.y += n.vy
    if (n.x < 0 || n.x > width) n.vx *= -1
    if (n.y < 0 || n.y > height) n.vy *= -1

    const d = distanceToCursor(n)
    const boost = d < CURSOR_RADIUS ? 1 + (1 - d / CURSOR_RADIUS) * 0.2 : 1
    n.radius = n.baseRadius * boost
  }

  for (const e of edges) {
    const a = nodes[e.a]
    const b = nodes[e.b]
    const da = distanceToCursor(a)
    const db = distanceToCursor(b)
    const nearCursor = da < CURSOR_RADIUS || db < CURSOR_RADIUS
    ctx.strokeStyle = nearCursor ? 'rgba(165, 180, 252, 0.9)' : 'rgba(99, 102, 241, 0.3)'
    ctx.lineWidth = nearCursor ? 1.2 : 0.8
    ctx.beginPath()
    ctx.moveTo(a.x, a.y)
    ctx.lineTo(b.x, b.y)
    ctx.stroke()
  }

  for (const n of nodes) {
    const d = distanceToCursor(n)
    const nearCursor = d < CURSOR_RADIUS
    const alpha = nearCursor ? 0.95 : 0.6
    const glow = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.radius * 6)
    glow.addColorStop(0, `rgba(165, 180, 252, ${alpha * 0.6})`)
    glow.addColorStop(1, 'rgba(99, 102, 241, 0)')
    ctx.fillStyle = glow
    ctx.beginPath()
    ctx.arc(n.x, n.y, n.radius * 6, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = `rgba(196, 181, 253, ${alpha})`
    ctx.beginPath()
    ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2)
    ctx.fill()
  }
}

function loop() {
  if (!isVisible || !canvasRef.value) {
    rafId = requestAnimationFrame(loop)
    return
  }
  const canvas = canvasRef.value
  step(canvas.width, canvas.height)
  rafId = requestAnimationFrame(loop)
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  ctx.scale(dpr, dpr)
  initNodes(rect.width, rect.height)
}

function onMouseMove(e) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  mouseX = e.clientX - rect.left
  mouseY = e.clientY - rect.top
}

onMounted(() => {
  const canvas = canvasRef.value
  ctx = canvas.getContext('2d')
  resize()
  window.addEventListener('resize', resize)
  canvas.addEventListener('mousemove', onMouseMove)

  // 视窗不可见时暂停
  visibilityObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => { isVisible = entry.isIntersecting })
  })
  visibilityObserver.observe(canvas)

  loop()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
  canvasRef.value?.removeEventListener('mousemove', onMouseMove)
  visibilityObserver?.disconnect()
})
</script>

<template>
  <canvas ref="canvasRef" class="node-cloud" aria-hidden="true"></canvas>
</template>

<style scoped>
.node-cloud {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .node-cloud { opacity: 0.3; }
}
</style>
