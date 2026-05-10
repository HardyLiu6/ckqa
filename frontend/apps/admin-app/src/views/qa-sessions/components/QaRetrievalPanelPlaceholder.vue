<script setup>
import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import { QA_SESSION_PAGE_COPY } from '../qa-session-copy.js'

defineProps({
  // 自定义兜底提示；M6b 下基于 message.taskStatus 决定（如"检索失败"）
  hint: { type: String, default: '' },
})
</script>

<template>
  <aside
    class="qa-retrieval-panel-placeholder ck-glass-card"
    aria-live="polite"
    data-testid="qa-retrieval-placeholder"
  >
    <header class="qa-retrieval-panel-placeholder__header">
      <span class="qa-retrieval-panel-placeholder__eyebrow">
        {{ QA_SESSION_PAGE_COPY.detail.retrievalPanelTitle }}
      </span>
      <strong>{{ QA_SESSION_PAGE_COPY.detail.retrievalPanelPlaceholderTitle }}</strong>
      <p>{{ hint || QA_SESSION_PAGE_COPY.detail.retrievalPanelPlaceholderHint }}</p>
    </header>

    <section class="qa-retrieval-panel-placeholder__section">
      <span class="qa-retrieval-panel-placeholder__section-title">子问题拆分</span>
      <CkSkeleton variant="text" :count="2" />
    </section>

    <section class="qa-retrieval-panel-placeholder__section">
      <span class="qa-retrieval-panel-placeholder__section-title">检索片段 TOP 5</span>
      <CkSkeleton variant="text" :count="3" />
    </section>

    <section class="qa-retrieval-panel-placeholder__section">
      <span class="qa-retrieval-panel-placeholder__section-title">调用链耗时分布</span>
      <CkSkeleton variant="text" :count="1" />
    </section>

    <section class="qa-retrieval-panel-placeholder__section">
      <span class="qa-retrieval-panel-placeholder__section-title">出处</span>
      <CkSkeleton variant="text" :count="2" />
    </section>

    <footer class="qa-retrieval-panel-placeholder__footer">
      <RouterLink
        to="/app/retrieval-logs"
        class="qa-retrieval-panel-placeholder__link"
        data-testid="qa-retrieval-placeholder-link"
      >
        {{ QA_SESSION_PAGE_COPY.detail.retrievalPanelLearnMore }} →
      </RouterLink>
    </footer>
  </aside>
</template>

<style scoped lang="scss">
.qa-retrieval-panel-placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
  padding: var(--ckqa-space-4);
  color: var(--ckqa-text-muted);
}
.qa-retrieval-panel-placeholder__header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.qa-retrieval-panel-placeholder__eyebrow {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
}
.qa-retrieval-panel-placeholder__header strong {
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-semibold);
}
.qa-retrieval-panel-placeholder__header p {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  line-height: 1.65;
}
.qa-retrieval-panel-placeholder__section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--ckqa-space-2) 0;
  border-top: 1px dashed var(--ckqa-border-soft);
}
.qa-retrieval-panel-placeholder__section-title {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  font-weight: var(--ckqa-fw-medium);
}
.qa-retrieval-panel-placeholder__footer {
  margin-top: auto;
}
.qa-retrieval-panel-placeholder__link {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: var(--ckqa-radius-full);
  border: 1px solid var(--ckqa-border);
  background: transparent;
  color: var(--ckqa-accent-strong);
  font-size: var(--ckqa-text-sm-size);
  text-decoration: none;
}
.qa-retrieval-panel-placeholder__link:hover {
  background: var(--ckqa-accent-soft);
  border-color: var(--ckqa-accent);
}
</style>
