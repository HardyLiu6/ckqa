<script setup>
/**
 * ResumeBuildDialog
 *
 * 在知识库列表点击「构建」按钮时弹出，展示该知识库下未完成的 buildRun 卡片。
 * 用户可：
 * - 点击某张卡片继续对应的构建流程（带 buildRunId 跳转）
 * - 点击底部「开始新构建」直接进入新建流程
 *
 * 视觉风格与项目其他对话框（编辑 / 删除）保持一致：
 * .dialog-backdrop > .creation-dialog 居中弹层，不使用 ElDialog 避免 overlay/teleport 冲突。
 */
import { computed, watch } from 'vue'
import { ElButton } from 'element-plus'
import { Plus, X } from 'lucide-vue-next'
import StatusBadge from '../common/StatusBadge.vue'
import { formatRelativeTime } from '../../views/pages/resume-build-model.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  knowledgeBaseName: { type: String, default: '' },
  candidates: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'resume', 'start-new'])

const hasCandidates = computed(() => props.candidates.length > 0)

function close() {
  emit('update:visible', false)
}

function handleResume(card) {
  emit('resume', card)
  close()
}

function handleStartNew() {
  emit('start-new')
  close()
}

// 弹出时锁定 body 滚动，关闭时恢复，避免长卡片列表与背景同时滚动
watch(() => props.visible, (next) => {
  if (typeof document === 'undefined') {
    return
  }
  document.body.style.overflow = next ? 'hidden' : ''
}, { immediate: true })
</script>

<template>
  <div
    v-if="visible"
    class="dialog-backdrop"
    role="presentation"
    @click.self="close"
  >
    <section
      class="creation-dialog resume-build-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="resume-build-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Build Pipeline</p>
          <h2 id="resume-build-dialog-title">
            {{ knowledgeBaseName ? `${knowledgeBaseName} · 构建流水线` : '构建流水线' }}
          </h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          aria-label="关闭对话框"
          @click="close"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          关闭
        </el-button>
      </div>

      <p class="resume-build-dialog__lead">
        <template v-if="loading">正在检查未完成的构建…</template>
        <template v-else-if="hasCandidates">
          检测到 {{ candidates.length }} 个未完成的构建流水线，可以继续上次的进度，也可以开始一次新的构建。
        </template>
        <template v-else>
          没有未完成的构建流水线，点击下方按钮直接开启新一次构建。
        </template>
      </p>

      <div v-if="loading" class="resume-build-dialog__placeholder">
        <span class="resume-build-dialog__spinner" aria-hidden="true" />
        <span>加载中</span>
      </div>

      <div v-else-if="hasCandidates" class="resume-build-cards">
        <button
          v-for="card in candidates"
          :key="card.id"
          type="button"
          class="resume-build-card"
          :data-status="card.status"
          @click="handleResume(card)"
        >
          <div class="resume-build-card__head">
            <StatusBadge :status="card.status" :label="card.statusLabel" />
            <span class="resume-build-card__version">{{ card.buildVersion }}</span>
          </div>
          <div class="resume-build-card__body">
            <strong>当前阶段 · {{ card.stageLabel }}</strong>
            <small>更新于 {{ formatRelativeTime(card.updatedAt) }}</small>
          </div>
          <span class="resume-build-card__cta">继续此构建 →</span>
        </button>
      </div>

      <div v-else class="resume-build-dialog__empty">
        <p>暂无未完成的构建流水线。</p>
      </div>

      <div class="creation-form__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          @click="close"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          @click="handleStartNew"
        >
          <Plus class="button-icon" :size="16" aria-hidden="true" />
          开始新构建
        </el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 通过 :deep 让对话框宽度比通用 creation-dialog 略窄、内容更紧凑 */
.resume-build-dialog {
  width: min(640px, 100%);
  max-height: min(680px, calc(100vh - 48px));
}

.resume-build-dialog__lead {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.resume-build-dialog__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-6);
  color: var(--ckqa-text-muted);
  font-size: 13px;
}

.resume-build-dialog__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid color-mix(in srgb, var(--ckqa-accent) 20%, var(--ckqa-border));
  border-top-color: var(--ckqa-accent);
  border-radius: 50%;
  animation: resume-build-spinner 0.8s linear infinite;
}

@keyframes resume-build-spinner {
  to {
    transform: rotate(360deg);
  }
}

.resume-build-dialog__empty {
  padding: var(--ckqa-space-6);
  border: 1px dashed var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-muted);
  font-size: 13px;
  text-align: center;
}

.resume-build-cards {
  display: grid;
  gap: var(--ckqa-space-3);
}

.resume-build-card {
  display: grid;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--ckqa-duration-fast, 150ms) var(--ckqa-ease-standard, ease),
    transform var(--ckqa-duration-fast, 150ms) var(--ckqa-ease-standard, ease),
    box-shadow var(--ckqa-duration-fast, 150ms) var(--ckqa-ease-standard, ease);
}

.resume-build-card:hover,
.resume-build-card:focus-visible {
  border-color: color-mix(in srgb, var(--ckqa-accent) 36%, var(--ckqa-border));
  box-shadow: 0 14px 28px color-mix(in srgb, var(--ckqa-accent) 12%, transparent);
  transform: translateY(-2px);
  outline: none;
}

.resume-build-card[data-status='running'] {
  border-left: 3px solid var(--ckqa-running, #2563eb);
}

.resume-build-card[data-status='pending'] {
  border-left: 3px solid #b7791f;
}

.resume-build-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ckqa-space-2);
}

.resume-build-card__version {
  color: var(--ckqa-text-muted);
  font-family: var(--ckqa-font-mono);
  font-size: 12px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.resume-build-card__body {
  display: grid;
  gap: var(--ckqa-space-1);
}

.resume-build-card__body strong {
  color: var(--ckqa-text);
  font-size: 14px;
  font-weight: 700;
}

.resume-build-card__body small {
  color: var(--ckqa-text-muted);
  font-size: 12px;
}

.resume-build-card__cta {
  margin-top: var(--ckqa-space-1);
  color: var(--ckqa-accent-strong);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}
</style>
