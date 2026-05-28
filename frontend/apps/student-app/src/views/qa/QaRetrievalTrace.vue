<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  events: {
    type: Array,
    default: () => [],
  },
  modeLabel: {
    type: String,
    default: '',
  },
  defaultOpen: {
    type: Boolean,
    default: false,
  },
  live: {
    type: Boolean,
    default: false,
  },
})

const isOpen = ref(props.defaultOpen)

watch(() => props.defaultOpen, (value) => {
  isOpen.value = value
})

const visibleEvents = computed(() => compactRunningEvents(props.events))
const latestEvent = computed(() => visibleEvents.value.at(-1) ?? null)

function compactRunningEvents(events) {
  const result = []
  const runningIndexes = new Map()
  for (const event of Array.isArray(events) ? events : []) {
    if (!event || typeof event !== 'object' || !event.summary) {
      continue
    }
    const type = String(event.type || 'progress')
    if (type.endsWith('_running')) {
      const existingIndex = runningIndexes.get(type)
      if (existingIndex != null) {
        result[existingIndex] = event
        continue
      }
      runningIndexes.set(type, result.length)
    }
    result.push(event)
  }
  return result
}

function formatProgressSummary(event) {
  return event?.summary || ''
}

function progressEvidenceLabel(event) {
  const count = Array.isArray(event?.evidence) ? event.evidence.length : 0
  if (!count) {
    return props.modeLabel || '检索'
  }
  return `关联依据 ${count} 条`
}

function progressEvidenceTitle(item) {
  return item?.title || item?.sourceFile || item?.source_file || item?.id || item?.ref || '课程依据'
}

function progressEvidenceSnippet(item) {
  return item?.snippet || item?.summary || item?.text || ''
}
</script>

<template>
  <section v-if="visibleEvents.length" class="retrieval-trace" :class="{ live }">
    <button
      class="retrieval-trace-head"
      type="button"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <span class="trace-pulse" aria-hidden="true"></span>
      <span class="trace-title">检索过程</span>
      <span class="trace-count">{{ visibleEvents.length }}</span>
      <span class="trace-latest">{{ formatProgressSummary(latestEvent) }}</span>
      <span class="trace-caret" aria-hidden="true">{{ isOpen ? '收起' : '展开' }}</span>
    </button>
    <Transition name="trace-fold">
      <ol v-if="isOpen" class="retrieval-trace-list">
        <li
          v-for="(event, index) in visibleEvents"
          :key="`${event.eventSeq || event.type || 'progress'}-${index}`"
          class="retrieval-trace-item"
        >
          <span class="trace-step">{{ progressEvidenceLabel(event) }}</span>
          <span class="trace-log">{{ formatProgressSummary(event) }}</span>
          <span v-if="event.evidence?.length" class="trace-evidence">
            <span
              v-for="(item, evidenceIndex) in event.evidence.slice(0, 3)"
              :key="`evidence-${index}-${evidenceIndex}`"
            >
              {{ progressEvidenceTitle(item) }}<template v-if="progressEvidenceSnippet(item)">：{{ progressEvidenceSnippet(item) }}</template>
            </span>
          </span>
        </li>
      </ol>
    </Transition>
  </section>
</template>

<style scoped lang="scss">
.retrieval-trace {
  margin-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  padding-bottom: 10px;
}

.retrieval-trace-head {
  display: grid;
  width: 100%;
  grid-template-columns: auto auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  background: rgba(240, 253, 250, 0.72);
  color: #334155;
  cursor: pointer;
  font: inherit;
  padding: 8px 10px;
  text-align: left;
}

.trace-pulse {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #14b8a6;
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

.live .trace-pulse {
  animation: tracePulse 1.3s ease-in-out infinite;
}

.trace-title {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.trace-count {
  border-radius: 999px;
  background: rgba(13, 148, 136, 0.1);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  padding: 1px 7px;
}

.trace-latest {
  min-width: 0;
  overflow: hidden;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-caret {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
}

.retrieval-trace-list {
  display: grid;
  gap: 8px;
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}

.retrieval-trace-item {
  display: grid;
  gap: 5px;
  border: 1px solid rgba(20, 184, 166, 0.18);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.9);
  padding: 9px 10px;
}

.trace-step {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.trace-log {
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.trace-evidence {
  display: grid;
  gap: 3px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.trace-fold-enter-active,
.trace-fold-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.trace-fold-enter-from,
.trace-fold-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes tracePulse {
  50% {
    box-shadow: 0 0 0 7px rgba(20, 184, 166, 0.04);
  }
}
</style>
