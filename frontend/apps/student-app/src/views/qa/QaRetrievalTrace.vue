<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  buildRetrievalTimeline,
  buildRetrievalTraceSummary,
  retrievalTraceEvidenceSnippet,
  retrievalTraceEvidenceTitle,
} from './qa-retrieval-trace-model'

const props = defineProps({
  events: {
    type: Array,
    default: () => [],
  },
  mode: {
    type: String,
    default: '',
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
  startedAt: {
    type: [String, Number],
    default: '',
  },
  finishedAt: {
    type: [String, Number],
    default: '',
  },
  hasAnswer: {
    type: Boolean,
    default: false,
  },
  sourceCount: {
    type: Number,
    default: 0,
  },
  taskStatus: {
    type: String,
    default: '',
  },
})

const isOpen = ref(props.defaultOpen)
const nowTick = ref(Date.now())
const effectiveLive = computed(() => props.live && !isTerminalTraceStatus(props.taskStatus))
let timerId = 0

watch(() => props.defaultOpen, (value) => {
  isOpen.value = value
})

watch(() => [effectiveLive.value, props.events.length], () => {
  syncTimer()
}, { immediate: true })

onBeforeUnmount(() => {
  stopTimer()
})

const timelineOptions = computed(() => ({
  live: effectiveLive.value,
  nowMs: nowTick.value,
  taskStartedAt: props.startedAt,
  taskFinishedAt: props.finishedAt,
  hasAnswer: props.hasAnswer,
  sourceCount: props.sourceCount,
  mode: props.mode || props.modeLabel,
  taskStatus: props.taskStatus,
}))

const timeline = computed(() => buildRetrievalTimeline(props.events, timelineOptions.value))
const summary = computed(() => buildRetrievalTraceSummary(timeline.value, timelineOptions.value))
const traceItems = computed(() => timeline.value.items)
const hasTrace = computed(() => traceItems.value.some((item) => item.eventTypes.length > 0))

function syncTimer() {
  if (effectiveLive.value && props.events.length) {
    if (!timerId) {
      timerId = window.setInterval(() => {
        nowTick.value = Date.now()
      }, 1000)
    }
    return
  }
  stopTimer()
}

function stopTimer() {
  if (timerId) {
    window.clearInterval(timerId)
    timerId = 0
  }
}

function progressEvidenceTitle(item) {
  return retrievalTraceEvidenceTitle(item)
}

function progressEvidenceSnippet(item) {
  return retrievalTraceEvidenceSnippet(item)
}

function isTerminalTraceStatus(status) {
  return ['success', 'done', 'completed', 'failed', 'error', 'stale', 'timeout']
    .includes(String(status ?? '').trim().toLowerCase())
}
</script>

<template>
  <section v-if="hasTrace" class="retrieval-trace" :class="{ live: effectiveLive, failed: timeline.failed }">
    <button
      class="retrieval-trace-head"
      type="button"
      :aria-expanded="isOpen"
      @click="isOpen = !isOpen"
    >
      <span class="trace-pulse" aria-hidden="true"></span>
      <span class="trace-title">检索过程</span>
      <span class="trace-count">{{ summary.countText }}</span>
      <span class="trace-latest">{{ summary.text }}</span>
      <span class="trace-caret" aria-hidden="true">{{ isOpen ? '收起' : '展开' }}</span>
    </button>
    <Transition name="trace-fold">
      <ol v-if="isOpen" class="retrieval-timeline">
        <li
          v-for="item in traceItems"
          :key="item.key"
          class="retrieval-timeline-item"
          :class="`status-${item.status}`"
        >
          <span class="timeline-marker" aria-hidden="true"></span>
          <div class="timeline-body">
            <div class="timeline-head">
              <strong>{{ item.title }}</strong>
              <span v-if="item.timeText" class="timeline-time">{{ item.timeText }}</span>
              <span v-if="item.evidenceLabel" class="timeline-evidence-label">{{ item.evidenceLabel }}</span>
            </div>
            <p>{{ item.summary }}</p>
            <div v-if="item.evidence?.length" class="trace-evidence">
              <span
                v-for="(evidence, evidenceIndex) in item.evidence"
                :key="`${item.key}-evidence-${evidenceIndex}`"
              >
                <strong class="trace-evidence-title">{{ progressEvidenceTitle(evidence) }}</strong><template v-if="progressEvidenceSnippet(evidence)">：{{ progressEvidenceSnippet(evidence) }}</template>
              </span>
            </div>
          </div>
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

.failed .retrieval-trace-head {
  background: rgba(255, 247, 237, 0.82);
}

.failed .trace-pulse {
  background: #f97316;
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.12);
}

.failed .trace-title,
.failed .trace-count {
  color: #c2410c;
}

.failed .trace-count {
  background: rgba(249, 115, 22, 0.1);
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
  white-space: nowrap;
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
  white-space: nowrap;
}

.retrieval-timeline {
  display: grid;
  gap: 0;
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.retrieval-timeline-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  column-gap: 10px;
  min-width: 0;
  position: relative;
}

.retrieval-timeline-item:not(:last-child)::before {
  position: absolute;
  top: 17px;
  bottom: -3px;
  left: 8px;
  width: 2px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  content: '';
}

.timeline-marker {
  z-index: 1;
  width: 10px;
  height: 10px;
  margin: 5px 0 0 4px;
  border: 2px solid rgba(148, 163, 184, 0.5);
  border-radius: 999px;
  background: #fff;
}

.timeline-body {
  min-width: 0;
  margin-bottom: 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
  padding: 9px 10px;
}

.timeline-head {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
  min-width: 0;
}

.timeline-head strong {
  min-width: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.timeline-time {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.timeline-evidence-label {
  justify-self: end;
  border-radius: 999px;
  background: rgba(13, 148, 136, 0.08);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.4;
  padding: 2px 7px;
  white-space: nowrap;
}

.timeline-body p {
  margin: 5px 0 0;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.trace-evidence {
  display: grid;
  gap: 3px;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.trace-evidence-title {
  color: #334155;
  font-weight: 800;
}

.status-done .timeline-marker {
  border-color: #14b8a6;
  background: #14b8a6;
  box-shadow: inset 0 0 0 2px #fff;
}

.status-active .timeline-marker {
  border-color: #0ea5e9;
  background: #0ea5e9;
  box-shadow: 0 0 0 4px rgba(14, 165, 233, 0.12);
}

.status-active .timeline-body {
  border-color: rgba(14, 165, 233, 0.22);
  background: rgba(240, 249, 255, 0.82);
}

.status-failed .timeline-marker {
  border-color: #f97316;
  background: #f97316;
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.12);
}

.status-failed .timeline-body {
  border-color: rgba(249, 115, 22, 0.24);
  background: rgba(255, 247, 237, 0.84);
}

.status-pending .timeline-body {
  opacity: 0.72;
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

@media (max-width: 640px) {
  .retrieval-trace-head {
    grid-template-columns: auto auto auto minmax(0, 1fr);
  }

  .trace-caret {
    grid-column: 4;
    justify-self: end;
  }

  .trace-latest {
    grid-column: 1 / -1;
    white-space: normal;
  }
}
</style>
