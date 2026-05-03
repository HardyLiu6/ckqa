<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  blocks: { type: Object, default: () => ({}) },
})
</script>

<template>
  <section class="build-step-panel prompt-confirm-panel">
    <div>
      <p class="eyebrow">Prompt Strategy</p>
      <h3>沿用 GraphRAG 当前活动提示词</h3>
      <p>首轮不新增后端提示词接口，本步骤只确认本次索引采用当前 GraphRAG 配置中的活动提示词。</p>
    </div>
    <StatusBadge :status="blocks.prompt?.status" :label="blocks.prompt?.confirmed ? '已确认' : '待确认'" />
  </section>
</template>
