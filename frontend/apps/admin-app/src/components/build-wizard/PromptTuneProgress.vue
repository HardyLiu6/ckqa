<script setup>
import { computed } from 'vue'
import {
  resolveProgressPercentage,
  resolvePrimaryAction,
  resolveStageLabel,
  selectLatestLogTail,
} from './prompt-tune-progress-model.js'

/**
 * 自动调优进度展示。
 *
 * status 取值与后端 PromptTuneRunResponse 一致：
 * - not_started：尚未触发
 * - pending：已入队等待 worker 拾取
 * - running：worker 正在执行
 * - success：调优完成（含缓存命中）
 * - failed：失败
 * - cancelled：被取消（暂未启用）
 */
const props = defineProps({
  state: { type: Object, default: null },
  triggering: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['trigger', 'retry', 'regenerate'])

const status = computed(() => props.state?.status ?? 'not_started')
const cacheHit = computed(() => Boolean(props.state?.cacheHit))
const stage = computed(() => props.state?.progressStage ?? '')
const lastHeartbeat = computed(() => {
  if (!props.state?.lastHeartbeatAt) return ''
  try {
    return new Date(props.state.lastHeartbeatAt).toLocaleTimeString('zh-CN')
  } catch (e) {
    return ''
  }
})
const finishedAt = computed(() => {
  if (!props.state?.finishedAt) return ''
  try {
    return new Date(props.state.finishedAt).toLocaleString('zh-CN')
  } catch (e) {
    return ''
  }
})
const stageLabel = computed(() => resolveStageLabel(stage.value))
const progressPercentage = computed(() => resolveProgressPercentage(status.value, stage.value))
const latestLogTail = computed(() => selectLatestLogTail(props.state?.latestLogs))
const primaryAction = computed(() => resolvePrimaryAction(status.value))
</script>

<template>
  <div class="prompt-tune-progress" :data-status="status">
    <template v-if="status === 'not_started'">
      <p class="prompt-tune-progress__primary">本次选材尚未生成自动调优提示词。</p>
      <p class="prompt-tune-progress__secondary">点击下方按钮启动 GraphRAG 官方调优；通常需要 10-20 分钟。</p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        :loading="triggering"
        :disabled="disabled || triggering"
        @click="$emit('trigger')"
      >
        {{ primaryAction?.label ?? '开始调优' }}
      </el-button>
    </template>

    <template v-else-if="status === 'pending' || status === 'running'">
      <p class="prompt-tune-progress__primary">
        正在为本次选材生成自动调优提示词…
      </p>
      <p class="prompt-tune-progress__secondary">
        {{ stageLabel }}
        <span v-if="lastHeartbeat"> · 最近心跳 {{ lastHeartbeat }}</span>
      </p>
      <el-progress
        :percentage="progressPercentage"
        :status="status === 'pending' ? null : 'success'"
      />
      <details v-if="latestLogTail.length" class="prompt-tune-progress__logs">
        <summary>查看最近日志</summary>
        <pre>{{ latestLogTail.join('\n') }}</pre>
      </details>
    </template>

    <template v-else-if="status === 'success'">
      <p class="prompt-tune-progress__primary">
        ✅ 已生成自动调优提示词
        <span v-if="cacheHit">（复用历史缓存，无需重新生成）</span>
      </p>
      <p class="prompt-tune-progress__secondary">
        <span v-if="finishedAt">完成于 {{ finishedAt }}</span>
      </p>
      <p class="prompt-tune-progress__hint">点击「确认提示词策略」即可使用本次产物。</p>
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        :loading="triggering"
        :disabled="disabled || triggering"
        @click="$emit('regenerate')"
      >
        {{ primaryAction?.label ?? '重新生成' }}
      </el-button>
    </template>

    <template v-else-if="status === 'failed'">
      <p class="prompt-tune-progress__primary">❌ 自动调优失败</p>
      <p class="prompt-tune-progress__secondary">
        {{ props.state?.errorMessage ?? '请查看后端日志获取详细错误' }}
      </p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        :loading="triggering"
        :disabled="disabled || triggering"
        @click="$emit('retry')"
      >
        {{ primaryAction?.label ?? '重试' }}
      </el-button>
      <details v-if="latestLogTail.length" class="prompt-tune-progress__logs">
        <summary>查看最近日志</summary>
        <pre>{{ latestLogTail.join('\n') }}</pre>
      </details>
    </template>
  </div>
</template>

<style scoped>
.prompt-tune-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.prompt-tune-progress__primary {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}

.prompt-tune-progress__secondary {
  font-size: 12px;
  color: var(--ckqa-text-secondary, #606266);
  margin: 0;
}

.prompt-tune-progress__logs summary {
  cursor: pointer;
  font-size: 12px;
  color: var(--ckqa-text-secondary, #606266);
}

.prompt-tune-progress__logs pre {
  max-height: 160px;
  overflow: auto;
  font-size: 11px;
  background: var(--ckqa-surface-2, #f5f7fa);
  padding: 8px;
  border-radius: 4px;
  white-space: pre-wrap;
}
</style>
