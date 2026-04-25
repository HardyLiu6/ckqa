<!-- frontend/apps/student-app/src/views/qa/index.vue -->
<!-- 问答 / 提问 · Purple · Module Layout（侧栏由 ModuleLayout 注入） -->
<script setup>
import { ref, nextTick, computed } from 'vue'
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { Position } from '@element-plus/icons-vue'

const messages = ref([
  {
    id: 1,
    role: 'user',
    content: '什么是进程间通信的管道方式？',
    time: '10:23',
  },
  {
    id: 2,
    role: 'ai',
    sources: [{ label: '操作系统 · 第 3 章' }],
    content: '管道（Pipe）是一种半双工通信机制，在 Unix 系统中以字节流形式传递数据。管道分为匿名管道（pipe）和命名管道（FIFO）两类……',
    related: ['信号量', '消息队列', '共享内存'],
    time: '10:23',
  },
])

const input = ref('')
const mainRef = ref(null)

function send() {
  const text = input.value.trim()
  if (!text) return
  const id = Date.now()
  messages.value.push({ id, role: 'user', content: text, time: '刚刚' })
  input.value = ''

  setTimeout(() => {
    messages.value.push({
      id: id + 1,
      role: 'ai',
      sources: [{ label: '课程参考' }],
      content: '（示例回答）这是一个基于知识图谱的模拟回复，后续接入后端会替换为真实内容。',
      related: ['相关知识点 1', '相关知识点 2'],
      time: '刚刚',
    })
    nextTick(() => mainRef.value?.scrollTo({ top: mainRef.value.scrollHeight, behavior: 'smooth' }))
  }, 800)
}

const isEmpty = computed(() => messages.value.length === 0)
</script>

<template>
  <div class="qa-ask-page">
    <div class="qa-halo"></div>

    <div ref="mainRef" class="qa-main">
      <div v-if="isEmpty" class="empty-state">
        <div class="empty-icon">💬</div>
        <div class="empty-title">从一个问题开始</div>
        <div class="empty-desc">试试："什么是进程同步？"</div>
      </div>

      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="`role-${msg.role}`"
        >
          <!-- 用户 -->
          <div v-if="msg.role === 'user'" class="bubble user-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>

          <!-- AI -->
          <GlassCard v-else tier="base" padding="md" class="ai-bubble">
            <div v-if="msg.sources?.length" class="msg-sources">
              <ModuleTag v-for="(s, i) in msg.sources" :key="i" module="qa" size="sm">📖 {{ s.label }}</ModuleTag>
            </div>
            <div class="msg-text ai-text">{{ msg.content }}</div>
            <div v-if="msg.related?.length" class="msg-related">
              <span class="related-label">相关：</span>
              <span v-for="r in msg.related" :key="r" class="related-chip">{{ r }}</span>
            </div>
            <div class="msg-time">{{ msg.time }}</div>
          </GlassCard>
        </div>
      </template>
    </div>

    <!-- 输入 -->
    <div class="qa-input-wrap">
      <GlassCard tier="base" padding="none" class="qa-input-card">
        <input
          v-model="input"
          class="qa-input"
          placeholder="继续追问，或换个话题…"
          @keyup.enter="send"
        />
        <button class="qa-send" :disabled="!input.trim()" @click="send">
          <el-icon :size="18"><Position /></el-icon>
        </button>
      </GlassCard>
    </div>
  </div>
</template>
<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.qa-ask-page {
  --module-color-500: #9333ea;
  --module-color-700: #7e22ce;

  position: relative;
  min-height: calc(100vh - 64px);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qa-halo {
  position: absolute;
  width: 420px; height: 420px;
  background: radial-gradient(circle, rgba(147, 51, 234, 0.2), transparent 60%);
  border-radius: 50%;
  top: -100px; right: -80px;
  filter: blur(40px);
  pointer-events: none;
}

.qa-main {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scroll-behavior: smooth;
}

.empty-state {
  text-align: center;
  padding: 80px 32px;

  .empty-icon { font-size: 56px; margin-bottom: 16px; }
  .empty-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 8px;
  }
  .empty-desc { font-size: 14px; color: #64748b; }
}

.msg-row { display: flex; }
.role-user { justify-content: flex-end; }
.role-ai { justify-content: flex-start; }
.user-bubble {
  max-width: 70%;
  padding: 10px 14px;
  background: linear-gradient(135deg, #9333ea, #a855f7);
  color: #fff;
  border-radius: $radius-xl $radius-xl 2px $radius-xl;
  box-shadow: 0 4px 16px rgba(147, 51, 234, 0.25);

  .msg-text { font-size: 14px; line-height: 1.6; }
  .msg-time { font-size: 11px; color: rgba(255, 255, 255, 0.8); margin-top: 4px; text-align: right; }
}

.ai-bubble {
  max-width: 80%;
  border-color: rgba(147, 51, 234, 0.25) !important;
  box-shadow: 0 8px 32px rgba(147, 51, 234, 0.12);
  border-radius: $radius-xl $radius-xl $radius-xl 2px !important;

  .msg-sources {
    display: flex;
    gap: 6px;
    margin-bottom: 10px;
  }
  .ai-text {
    font-size: 14px;
    line-height: 1.65;
    color: #0f172a;
  }
  .msg-related {
    margin-top: 12px;
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;

    .related-label {
      font-size: 11px;
      color: #64748b;
      font-weight: 600;
    }
    .related-chip {
      padding: 2px 8px;
      background: rgba(147, 51, 234, 0.05);
      border: 1px solid rgba(147, 51, 234, 0.15);
      color: #7e22ce;
      font-size: 11px;
      border-radius: $radius-md;
    }
  }
  .msg-time {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 8px;
  }
}

.qa-input-wrap {
  position: sticky;
  bottom: 16px;
}

.qa-input-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 16px !important;
  border-color: rgba(147, 51, 234, 0.35) !important;
  box-shadow: 0 0 0 4px rgba(147, 51, 234, 0.1), 0 8px 24px rgba(147, 51, 234, 0.15);

  .qa-input {
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    font-family: inherit;
    font-size: 14px;
    color: #0f172a;
    &::placeholder { color: #9ca3af; }
  }

  .qa-send {
    width: 40px; height: 40px;
    background: linear-gradient(135deg, #9333ea, #a855f7);
    border: 0;
    border-radius: $radius-lg;
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 16px rgba(147, 51, 234, 0.35);
    transition: transform $duration-fast $ease-out;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 8px 24px rgba(147, 51, 234, 0.5);
    }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
}
</style>
