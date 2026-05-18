<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ShieldCheck } from 'lucide-vue-next'

/**
 * Cloudflare Turnstile 人机验证占位组件。
 *
 * - 当 props.siteKey 为空时（开发态默认）：显示「✓ 已通过开发态人机验证」占位条，
 *   不实际加载 Turnstile widget；emit('verified', null) 让上层登录可用。
 * - 当 props.siteKey 有值时：动态加载 Turnstile script，挂载 widget；
 *   widget 完成校验后 emit('verified', token)，过期或失败 emit('verified', null)。
 *
 * 上线步骤：在前端 .env 配置 VITE_TURNSTILE_SITE_KEY；后端
 * application.properties 配置 ckqa.security.turnstile.enabled=true 与 secret-key。
 */
const props = defineProps({
  siteKey: { type: String, default: '' },
})

const emit = defineEmits(['verified'])

const widgetEl = ref(null)
const widgetId = ref(null)
const verified = ref(false)

const enabled = computed(() => Boolean(props.siteKey?.trim()))

const TURNSTILE_SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js'

function loadTurnstileScript() {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined') {
      reject(new Error('non-browser environment'))
      return
    }
    if (window.turnstile) {
      resolve(window.turnstile)
      return
    }
    const existing = document.querySelector(`script[src="${TURNSTILE_SCRIPT_URL}"]`)
    if (existing) {
      existing.addEventListener('load', () => resolve(window.turnstile))
      existing.addEventListener('error', reject)
      return
    }
    const script = document.createElement('script')
    script.src = TURNSTILE_SCRIPT_URL
    script.async = true
    script.defer = true
    script.onload = () => resolve(window.turnstile)
    script.onerror = reject
    document.head.appendChild(script)
  })
}

async function mountWidget() {
  if (!enabled.value || !widgetEl.value) return
  try {
    const turnstile = await loadTurnstileScript()
    if (!turnstile) return
    widgetId.value = turnstile.render(widgetEl.value, {
      sitekey: props.siteKey.trim(),
      theme: 'light',
      size: 'flexible',
      callback: (token) => {
        verified.value = true
        emit('verified', token)
      },
      'expired-callback': () => {
        verified.value = false
        emit('verified', null)
      },
      'error-callback': () => {
        verified.value = false
        emit('verified', null)
      },
    })
  } catch (error) {
    // 加载失败时降级为「未通过」，登录按钮保持可点（后端会兜底拒绝）
    console.warn('[HumanCheck] turnstile 加载失败', error)
  }
}

function resetWidget() {
  if (typeof window === 'undefined') return
  const turnstile = window.turnstile
  if (turnstile && widgetId.value != null) {
    turnstile.reset(widgetId.value)
  }
  verified.value = false
  emit('verified', null)
}

defineExpose({ resetWidget })

onMounted(() => {
  if (!enabled.value) {
    // 开发态：直接 emit verified=null 表示无 token 但也不阻塞登录
    emit('verified', null)
    return
  }
  mountWidget()
})

onUnmounted(() => {
  if (typeof window === 'undefined') return
  const turnstile = window.turnstile
  if (turnstile && widgetId.value != null) {
    try {
      turnstile.remove(widgetId.value)
    } catch {
      // ignore
    }
  }
})

watch(() => props.siteKey, () => {
  if (enabled.value) {
    mountWidget()
  }
})
</script>

<template>
  <div class="human-check">
    <div v-if="enabled" ref="widgetEl" class="human-check__widget"></div>
    <div v-else class="human-check__placeholder" role="status" aria-live="polite">
      <ShieldCheck :size="14" aria-hidden="true" />
      <span>已通过开发态人机验证</span>
    </div>
  </div>
</template>

<style scoped>
.human-check {
  margin-top: 8px;
}
.human-check__widget {
  min-height: 65px;
}
.human-check__placeholder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.08);
  border: 1px dashed rgba(99, 102, 241, 0.32);
  color: rgba(30, 27, 75, 0.65);
  font-size: 11px;
  letter-spacing: 0.02em;
}
</style>
