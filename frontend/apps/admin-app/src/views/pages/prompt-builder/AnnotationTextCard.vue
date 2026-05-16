<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationTextCard.vue -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  computeSelectionRange,
  splitTextByEntitySpans,
} from './text-selection-model.js'

const props = defineProps({
  /** 原文 */
  text: { type: String, default: '' },
  /** 已确认实体（用于高亮）；含 spanStart/spanEnd 的会被渲染 */
  entities: { type: Array, default: () => [] },
})

const emit = defineEmits(['request-add-entity'])

const segments = computed(() => splitTextByEntitySpans(props.text, props.entities))

const floatingButton = ref({
  visible: false,
  x: 0,
  y: 0,
  selectedText: '',
  spanStart: 0,
  spanEnd: 0,
})

const textRef = ref(null)
const floatingBtnRef = ref(null)

function handleMouseUp(event) {
  const sel = window.getSelection()
  if (!sel || sel.toString().trim().length === 0) {
    floatingButton.value.visible = false
    return
  }
  const selectedText = sel.toString()
  // 计算选区相对原文的 offset：
  // 选区可能跨多个 highlight/plain 段，但所有段都在 textRef 内。
  // 通过创建 range pre-clone 测量 textRef 内的 textContent 长度差得到 offset。
  const range = sel.getRangeAt(0)
  const preRange = range.cloneRange()
  preRange.selectNodeContents(textRef.value)
  preRange.setEnd(range.startContainer, range.startOffset)
  const selectionStart = preRange.toString().length

  const computed = computeSelectionRange(props.text, selectedText, selectionStart)
  if (!computed) {
    floatingButton.value.visible = false
    return
  }

  // 按钮位置：紧贴选区右下角
  const rect = range.getBoundingClientRect()
  floatingButton.value = {
    visible: true,
    x: rect.right + 4,
    y: rect.bottom + 4,
    selectedText: selectedText.trim(),
    spanStart: computed.spanStart,
    spanEnd: computed.spanEnd,
  }
}

function handleAddEntityClick() {
  emit('request-add-entity', {
    name: floatingButton.value.selectedText,
    spanStart: floatingButton.value.spanStart,
    spanEnd: floatingButton.value.spanEnd,
  })
  floatingButton.value.visible = false
  // 清除选区，避免再次点 mouseup 触发
  window.getSelection()?.removeAllRanges()
}

/**
 * 文档级 mousedown 监听：点击发生在浮动按钮和原文卡之外时关闭按钮。
 * 用 mousedown 而非 click，避免被浏览器选区清除前的 click 误触。
 */
function handleDocumentMousedown(event) {
  if (!floatingButton.value.visible) return
  const target = event.target
  const isInsideText = textRef.value?.contains(target)
  const isInsideButton = floatingBtnRef.value?.contains(target)
  if (!isInsideText && !isInsideButton) {
    floatingButton.value.visible = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleDocumentMousedown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentMousedown)
})
</script>

<template>
  <article class="annotation-text-card">
    <header class="annotation-text-card__head">
      <span class="ann-text-tiny">原文</span>
    </header>
    <div ref="textRef" class="annotation-text-card__body" @mouseup="handleMouseUp">
      <template v-for="(seg, idx) in segments" :key="idx">
        <span v-if="seg.type === 'highlight'" class="annotation-text-card__highlight">{{ seg.text }}</span>
        <template v-else>{{ seg.text }}</template>
      </template>
    </div>
    <Teleport to="body">
      <button
        v-if="floatingButton.visible"
        ref="floatingBtnRef"
        class="annotation-text-card__floating-btn"
        :style="{ position: 'fixed', left: floatingButton.x + 'px', top: floatingButton.y + 'px' }"
        @click="handleAddEntityClick"
      >
        + 添加为实体
      </button>
    </Teleport>
  </article>
</template>

<style scoped>
.annotation-text-card__highlight {
  background: #ddd6fe;
  color: #6d28d9;
  border-radius: 2px;
  padding: 0 2px;
  font-weight: 500;
}
.annotation-text-card__floating-btn {
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10000;
}
.annotation-text-card__floating-btn:hover {
  background: #4f46e5;
}
</style>
