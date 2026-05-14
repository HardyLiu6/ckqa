<script setup>
import { computed } from 'vue'
import PromptTuneProgress from './PromptTuneProgress.vue'

const props = defineProps({
  strategy: { type: String, default: 'default' },
  customDraftReady: { type: Boolean, default: false },
  customDraft: { type: Object, default: null },
  graphragTunedSummary: { type: Object, default: null },
  promptTuneState: { type: Object, default: null },
  promptTuneTriggering: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['goto-builder', 'prompt-tune-trigger', 'prompt-tune-retry', 'prompt-tune-regenerate'])

const variant = computed(() => {
  if (props.strategy === 'default') return 'default'
  if (props.strategy === 'graphrag_tuned') return 'graphrag_tuned'
  return props.customDraftReady ? 'custom_pipeline_ready' : 'custom_pipeline_empty'
})

const draftSummary = computed(() => {
  if (!props.customDraft) return null
  const updated = props.customDraft.updatedAt
    ? new Date(props.customDraft.updatedAt).toLocaleString('zh-CN')
    : '未知时间'
  return { updated }
})
</script>

<template>
  <div class="prompt-strategy-detail" :data-variant="variant">
    <template v-if="variant === 'default'">
      <p class="prompt-strategy-detail__primary">⚙ 已选「默认提示词」</p>
      <p class="prompt-strategy-detail__secondary">
        点击「确认提示词策略」即可进入索引构建。<br>
        graphrag 会按通用模板抽取实体与关系。
      </p>
      <p class="prompt-strategy-detail__hint">无需额外操作。</p>
    </template>

    <template v-else-if="variant === 'graphrag_tuned'">
      <PromptTuneProgress
        :state="promptTuneState"
        :triggering="promptTuneTriggering"
        :disabled="disabled"
        @trigger="$emit('prompt-tune-trigger')"
        @retry="$emit('prompt-tune-retry')"
        @regenerate="$emit('prompt-tune-regenerate')"
      />
    </template>

    <template v-else-if="variant === 'custom_pipeline_empty'">
      <p class="prompt-strategy-detail__primary">🛠 已选「手动调优提示词」</p>
      <p class="prompt-strategy-detail__secondary">
        尚未构建草稿。本次构建专属，不复用历史。<br>
        从默认或自动调优为种子继续编辑实体抽取规则。
      </p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        :disabled="disabled"
        @click="$emit('goto-builder')"
      >
        前往构建
      </el-button>
    </template>

    <template v-else-if="variant === 'custom_pipeline_ready'">
      <p class="prompt-strategy-detail__primary">🛠 已构建手动调优提示词</p>
      <p class="prompt-strategy-detail__secondary">
        上次保存于 {{ draftSummary?.updated ?? '未知时间' }} · 已修改 1 个提示词块（实体抽取）
      </p>
      <p class="prompt-strategy-detail__hint">点击「确认提示词策略」即可使用本草稿。</p>
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        :disabled="disabled"
        @click="$emit('goto-builder')"
      >
        编辑提示词
      </el-button>
    </template>
  </div>
</template>
