<script setup>
import { computed } from 'vue'

const props = defineProps({
  strategy: { type: String, default: 'default' },
  customDraftReady: { type: Boolean, default: false },
  customDraft: { type: Object, default: null },
  graphragTunedSummary: { type: Object, default: null },
  disabled: { type: Boolean, default: false },
})

defineEmits(['goto-builder'])

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
      <p class="prompt-strategy-detail__primary">将使用系统默认的 GraphRAG 提示词进行索引构建。</p>
      <p class="prompt-strategy-detail__secondary">覆盖实体抽取、描述总结、社区报告等 5 个核心提示词。</p>
    </template>

    <template v-else-if="variant === 'graphrag_tuned'">
      <p class="prompt-strategy-detail__primary">将使用 GraphRAG 自动调优生成的提示词进行索引构建。</p>
      <p class="prompt-strategy-detail__secondary">
        {{ graphragTunedSummary?.name ?? '本课程当前激活的自动调优结果' }}
      </p>
    </template>

    <template v-else-if="variant === 'custom_pipeline_empty'">
      <p class="prompt-strategy-detail__primary">尚未构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">点击下方按钮进入独立页面，按 3 步流程设计本次构建使用的提示词。</p>
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
      <p class="prompt-strategy-detail__primary">已构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">
        上次保存于 {{ draftSummary?.updated ?? '未知时间' }} · 已修改 1 个提示词块（实体抽取）
      </p>
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
