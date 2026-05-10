<script setup>
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { KB_PAGE_COPY } from '../kb-page-copy.js'

// 验证记录（QA smoke 结果）；后端接口暂未开放统一列表，
// 暂时采用空态占位 + 跳转到知识库验证页的 CTA。后端就绪后可在此接入。
defineProps({
  // 预留 records[]；当前只用于空态判断
  records: { type: Array, default: () => [] },
})
</script>

<template>
  <section class="kb-validation-tab" data-testid="kb-validation-tab">
    <CkEmptyState
      icon="◐"
      :title="KB_PAGE_COPY.detail.validationEmpty"
      description="完成索引构建后，可以在构建向导最后一步发起知识库验证。"
    >
      <template #extra>
        <RouterLink to="/app/qa-smoke" class="kb-validation-tab__link ck-pressable">
          前往知识库验证
        </RouterLink>
      </template>
    </CkEmptyState>
  </section>
</template>

<style scoped lang="scss">
.kb-validation-tab__link {
  margin-top: var(--ckqa-space-3);
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border: 1px solid var(--ckqa-accent);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-accent-strong);
  text-decoration: none;
  font-size: var(--ckqa-text-sm-size);
}
.kb-validation-tab__link:hover {
  background: var(--ckqa-accent-soft);
}
</style>
