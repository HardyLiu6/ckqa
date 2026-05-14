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
 *
 * 模板与 PromptStrategyDetail 内联保持一致的 3 列布局：
 * 标题（fixed） / 正文（flex 1） / 操作或进度条（fixed 240）。
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
    <!-- 未触发：标题 / 提示文案 / 开始按钮 -->
    <template v-if="status === 'not_started'">
      <div class="prompt-strategy-detail__title">✨ 已选「自动调优提示词」</div>
      <div class="prompt-strategy-detail__body">
        本次选材尚未生成调优产物。<br>
        GraphRAG 会按 12 个阶段自动学习样本，约 15 分钟。
      </div>
      <div class="prompt-strategy-detail__action">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          :loading="triggering"
          :disabled="disabled || triggering"
          @click="$emit('trigger')"
        >
          {{ primaryAction?.label ?? '开始调优' }}
        </el-button>
      </div>
    </template>

    <!-- 进行中：标题 / 阶段文案 / 进度条 -->
    <template v-else-if="status === 'pending' || status === 'running'">
      <div class="prompt-strategy-detail__title">✨ 正在生成调优产物</div>
      <div class="prompt-strategy-detail__body">
        当前：{{ stageLabel }}<span v-if="lastHeartbeat"><br>心跳 {{ lastHeartbeat }}</span>
        <details v-if="latestLogTail.length" class="prompt-tune-progress__logs">
          <summary>查看最近日志</summary>
          <pre>{{ latestLogTail.join('\n') }}</pre>
        </details>
      </div>
      <div class="prompt-strategy-detail__action prompt-tune-progress__action">
        <el-progress
          :percentage="progressPercentage"
          :status="status === 'pending' ? null : 'success'"
          :stroke-width="10"
        />
      </div>
    </template>

    <!-- 成功：标题 / 完成时间 + hint / 重新生成 -->
    <template v-else-if="status === 'success'">
      <div class="prompt-strategy-detail__title">
        ✓ 已生成调优产物
        <span v-if="cacheHit" class="prompt-strategy-detail__title-hint">（命中缓存）</span>
      </div>
      <div class="prompt-strategy-detail__body">
        <span v-if="finishedAt">完成于 {{ finishedAt }}</span><br>
        <span class="prompt-strategy-detail__hint-inline">点击「确认提示词策略」即可使用本次产物</span>
      </div>
      <div class="prompt-strategy-detail__action">
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          :loading="triggering"
          :disabled="disabled || triggering"
          @click="$emit('regenerate')"
        >
          {{ primaryAction?.label ?? '重新生成' }}
        </el-button>
      </div>
    </template>

    <!-- 失败：标题 / 错误信息 / 重试 -->
    <template v-else-if="status === 'failed'">
      <div class="prompt-strategy-detail__title prompt-strategy-detail__title--danger">
        ❌ 自动调优失败
      </div>
      <div class="prompt-strategy-detail__body">
        {{ props.state?.errorMessage ?? '请查看后端日志获取详细错误' }}
        <details v-if="latestLogTail.length" class="prompt-tune-progress__logs">
          <summary>查看最近日志</summary>
          <pre>{{ latestLogTail.join('\n') }}</pre>
        </details>
      </div>
      <div class="prompt-strategy-detail__action">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          :loading="triggering"
          :disabled="disabled || triggering"
          @click="$emit('retry')"
        >
          {{ primaryAction?.label ?? '重试' }}
        </el-button>
      </div>
    </template>
  </div>
</template>
