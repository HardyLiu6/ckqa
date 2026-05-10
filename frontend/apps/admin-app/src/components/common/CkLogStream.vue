<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import {
  normalizeLogLines,
  resolveLevelTone,
  shouldAutoFollow,
} from './log-stream-model.js'

const props = defineProps({
  // 原始日志行；组件内部走 normalizeLogLines 清洗
  lines: { type: Array, default: () => [] },
  // 显示密度：紧凑模式（12px）用于向导右侧面板；宽松（13px）用于资料详情
  densityCompact: { type: Boolean, default: false },
  // 日志缓冲最大条数
  cap: { type: Number, default: 500 },
  // 最大可视高度（超出出滚动条）
  maxHeight: { type: String, default: '320px' },
  // 空态提示语；构建向导启动前用 "提交后将在这里实时显示构建过程"
  emptyHint: { type: String, default: '暂无日志输出' },
})

// 规范化后的日志行（含术语清洗 + 截断）
const normalized = computed(() => normalizeLogLines(props.lines, { cap: props.cap }))

// 容器引用 + 用户上滚标记
const containerRef = ref(null)
const lastUserScrollTs = ref(0)

// 日志更新时按 shouldAutoFollow 决定是否跟随
watch(
  normalized,
  async (next, prev) => {
    // 只有行数增加时才需要重新判断跟随
    if (!next?.length || next.length === (prev?.length ?? 0)) return
    await nextTick()
    const now = Date.now()
    if (shouldAutoFollow(next, lastUserScrollTs.value, now)) {
      scrollToBottom()
    }
  },
  { deep: false },
)

onMounted(() => {
  scrollToBottom()
})

function scrollToBottom() {
  const el = containerRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function handleScroll(event) {
  const el = event.target
  if (!el) return
  // 距离底部 > 24px 视为用户手动上滚
  const gap = el.scrollHeight - el.scrollTop - el.clientHeight
  if (gap > 24) {
    lastUserScrollTs.value = Date.now()
  } else {
    // 回到底部则清除暂停
    lastUserScrollTs.value = 0
  }
}

defineExpose({ scrollToBottom })
</script>

<template>
  <section
    class="ck-log-stream"
    :data-density="densityCompact ? 'compact' : 'comfortable'"
    data-testid="log-stream"
  >
    <header v-if="$slots.header" class="ck-log-stream-header">
      <slot name="header" />
    </header>
    <div
      v-if="normalized.length === 0"
      class="ck-log-stream-empty"
      role="status"
      aria-live="polite"
    >
      {{ emptyHint }}
    </div>
    <div
      v-else
      ref="containerRef"
      class="ck-log-stream-body"
      :style="{ maxHeight }"
      aria-live="polite"
      role="log"
      @scroll="handleScroll"
    >
      <div
        v-for="line in normalized"
        :key="line.id"
        class="ck-log-line"
        :data-level="line.level"
        :data-tone="resolveLevelTone(line.level)"
      >
        <span class="ck-log-line-level">{{ line.level.toUpperCase() }}</span>
        <span class="ck-log-line-message">{{ line.message }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ck-log-stream {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  overflow: hidden;
}
.ck-log-stream-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ckqa-border-soft);
  background: var(--ckqa-surface-muted);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.ck-log-stream-empty {
  padding: var(--ckqa-space-6);
  text-align: center;
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-sm-size);
}
.ck-log-stream-body {
  font-family: var(--ckqa-font-mono);
  font-size: var(--ckqa-text-sm-size);
  line-height: 1.65;
  overflow-y: auto;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  scrollbar-width: thin;
}
.ck-log-stream[data-density='compact'] .ck-log-stream-body {
  font-size: var(--ckqa-text-xs-size);
  line-height: 1.55;
  padding: 6px 10px;
}
.ck-log-line {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 8px;
  padding: 2px 0;
  border-left: 3px solid transparent;
  padding-left: 6px;
}
.ck-log-line[data-level='error'] {
  border-left-color: var(--ckqa-danger);
  background: var(--ckqa-danger-soft);
  color: var(--ckqa-danger);
}
.ck-log-line[data-level='warn'] {
  border-left-color: var(--ckqa-warning);
  color: var(--ckqa-warning);
}
.ck-log-line[data-level='debug'] {
  color: var(--ckqa-text-weak);
}
.ck-log-line-level {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
}
.ck-log-line[data-level='error'] .ck-log-line-level {
  color: var(--ckqa-danger);
}
.ck-log-line[data-level='warn'] .ck-log-line-level {
  color: var(--ckqa-warning);
}
.ck-log-line-message {
  word-break: break-word;
  white-space: pre-wrap;
  color: inherit;
}
@media (prefers-reduced-motion: reduce) {
  .ck-log-stream-body {
    scroll-behavior: auto;
  }
}
</style>
