<script setup>
defineProps({
  // 失败原因文案（已由后端清洗或前端 sanitize）
  message: { type: String, default: '构建出现异常，请参考日志定位' },
  // 失败阶段 key（用于在文案中强调是哪一步）
  stageKey: { type: String, default: '' },
  // 是否允许当前用户触发重试
  canRetry: { type: Boolean, default: true },
})

defineEmits(['retry', 'skip'])

const STAGE_LABEL_MAP = {
  material: '资料选择',
  parse: '解析检查',
  export: '生成图谱输入',
  prompt: 'Prompt 确认',
  index: '创建索引',
  qa_check: '问答验证',
}

function labelOfStage(key) {
  return STAGE_LABEL_MAP[key] ?? ''
}
</script>

<template>
  <div
    class="build-run-error-banner"
    role="alert"
    aria-live="assertive"
    data-testid="build-run-error-banner"
  >
    <div class="build-run-error-banner__headline">
      <span class="build-run-error-banner__icon" aria-hidden="true">!</span>
      <div>
        <strong v-if="stageKey">
          {{ labelOfStage(stageKey) || stageKey }} 阶段失败
        </strong>
        <strong v-else>构建过程出错</strong>
        <p class="build-run-error-banner__message">{{ message }}</p>
      </div>
    </div>
    <div v-if="canRetry" class="build-run-error-banner__actions">
      <button type="button" class="ck-pressable is-primary" @click="$emit('retry', stageKey)">
        重试当前阶段
      </button>
      <button type="button" class="ck-pressable is-ghost" @click="$emit('skip', stageKey)">
        跳过当前阶段
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.build-run-error-banner {
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  background: var(--ckqa-danger-soft);
  border: 1px solid var(--ckqa-danger);
  color: var(--ckqa-danger);
  border-radius: var(--ckqa-radius-md);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.build-run-error-banner__headline {
  display: flex;
  gap: var(--ckqa-space-3);
  align-items: flex-start;
}
.build-run-error-banner__icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ckqa-danger);
  color: var(--ckqa-surface);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: var(--ckqa-fw-semibold);
  font-size: var(--ckqa-text-sm-size);
}
.build-run-error-banner__message {
  margin: 4px 0 0;
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-sm-size);
}
.build-run-error-banner__actions {
  display: flex;
  gap: var(--ckqa-space-2);
  flex-wrap: wrap;
}
.build-run-error-banner__actions button {
  padding: 6px 14px;
  border-radius: var(--ckqa-radius-md);
  border: 1px solid var(--ckqa-danger);
  background: transparent;
  color: var(--ckqa-danger);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
}
.build-run-error-banner__actions button.is-primary {
  background: var(--ckqa-danger);
  color: var(--ckqa-surface);
}
.build-run-error-banner__actions button.is-ghost:hover {
  background: var(--ckqa-surface-muted);
}
</style>
