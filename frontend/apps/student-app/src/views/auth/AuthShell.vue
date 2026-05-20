<script setup>
// 认证页共用视觉壳：左侧 hero 文案 + 右侧玻璃卡片
// 登录、注册、忘记密码三页通过 slot 注入差异内容，保持整套视觉风格统一
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ChatDotRound } from '@element-plus/icons-vue'

const props = defineProps({
  // 左侧大标题
  heroTitle: {
    type: String,
    required: true,
  },
  // 左侧副文案
  heroSubtitle: {
    type: String,
    required: true,
  },
  // 左上角的 kicker 标签，例如 LOGIN / REGISTER / RESET
  heroKicker: {
    type: String,
    default: 'CKQA Student',
  },
  // 卡片头部 kicker
  cardKicker: {
    type: String,
    default: '',
  },
  // 卡片头部主标题
  cardTitle: {
    type: String,
    required: true,
  },
})

const cardKickerText = computed(() => props.cardKicker || props.heroKicker)
</script>

<template>
  <main class="auth-shell">
    <RouterLink class="auth-shell__brand" to="/" aria-label="返回介绍页">
      <span class="auth-shell__mark">
        <el-icon :size="19"><ChatDotRound /></el-icon>
      </span>
      <span>智课问答</span>
    </RouterLink>

    <section class="auth-shell__hero" aria-labelledby="auth-shell-title">
      <div class="auth-shell__copy">
        <p class="auth-shell__kicker">{{ heroKicker }}</p>
        <h1 id="auth-shell-title">{{ heroTitle }}</h1>
        <p class="auth-shell__subtitle">{{ heroSubtitle }}</p>

        <ul class="auth-shell__highlights">
          <li>
            <span class="auth-shell__bullet" aria-hidden="true">01</span>
            <div>
              <strong>问答 + 图谱一体</strong>
              <span>把课程问答和知识图谱拉到同一个学习空间</span>
            </div>
          </li>
          <li>
            <span class="auth-shell__bullet" aria-hidden="true">02</span>
            <div>
              <strong>多端会话不丢</strong>
              <span>登录态可在 7 天内自动续期，复习不被打断</span>
            </div>
          </li>
          <li>
            <span class="auth-shell__bullet" aria-hidden="true">03</span>
            <div>
              <strong>账号安全可控</strong>
              <span>支持邮箱验证码与密码强度提示，关键操作有迹可循</span>
            </div>
          </li>
        </ul>
      </div>

      <div class="auth-shell__card">
        <header class="auth-shell__card-header">
          <p class="auth-shell__kicker">{{ cardKickerText }}</p>
          <h2>{{ cardTitle }}</h2>
        </header>

        <slot name="card-actions" />

        <slot />

        <footer v-if="$slots.footer" class="auth-shell__card-footer">
          <slot name="footer" />
        </footer>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/breakpoints' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

.auth-shell {
  position: relative;
  min-height: 100vh;
  padding: 28px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 14% 18%, rgba(45, 212, 191, 0.22), transparent 28%),
    radial-gradient(circle at 86% 14%, rgba(251, 146, 60, 0.18), transparent 24%),
    linear-gradient(135deg, #111827 0%, #17142a 48%, #0f172a 100%);
}

.auth-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.7), transparent 78%);
}

.auth-shell::after {
  // 右侧卡片背后的微光，让玻璃感更明显
  content: '';
  position: absolute;
  top: 22%;
  right: -120px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.36), transparent 60%);
  filter: blur(40px);
  pointer-events: none;
}

.auth-shell__brand {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-weight: 800;
}

.auth-shell__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}

.auth-shell__hero {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  align-items: center;
  gap: 56px;
  width: min(1180px, 100%);
  min-height: calc(100vh - 96px);
  margin: 0 auto;
}

.auth-shell__copy {
  display: grid;
  gap: 18px;
  max-width: 620px;
}

.auth-shell__kicker {
  margin: 0;
  color: #5eead4;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.auth-shell h1 {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: clamp(40px, 5.4vw, 68px);
  line-height: 1.04;
  letter-spacing: -0.01em;
}

.auth-shell__subtitle {
  max-width: 540px;
  margin: 0;
  color: rgba(255, 255, 255, 0.74);
  font-size: 17px;
  line-height: 1.78;
}

.auth-shell__highlights {
  display: grid;
  gap: 14px;
  margin: 18px 0 0;
  padding: 0;
}

.auth-shell__highlights li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: $radius-xl;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(12px);
}

.auth-shell__highlights li > div {
  display: grid;
  gap: 4px;
}

.auth-shell__highlights strong {
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.auth-shell__highlights span {
  color: rgba(255, 255, 255, 0.66);
  font-size: 13px;
  line-height: 1.6;
}

.auth-shell__bullet {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: $radius-md;
  background: linear-gradient(135deg, rgba(94, 234, 212, 0.28), rgba(59, 130, 246, 0.32));
  color: #f0fdfa;
  font-weight: 900;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.auth-shell__card {
  display: grid;
  gap: 18px;
  padding: 28px 26px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: $radius-2xl;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.32);
  backdrop-filter: blur(22px);
}

.auth-shell__card-header {
  display: grid;
  gap: 4px;
}

.auth-shell__card-header h2 {
  margin: 0;
  color: #fff;
  font-size: 24px;
  font-weight: 800;
}

.auth-shell__card-footer {
  display: grid;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
  text-align: center;
}

@media (max-width: $bp-laptop) {
  .auth-shell__highlights {
    display: none;
  }
}

@media (max-width: $bp-tablet) {
  .auth-shell {
    padding: 20px;
  }

  .auth-shell__hero {
    grid-template-columns: 1fr;
    gap: 28px;
    align-content: center;
    min-height: calc(100vh - 84px);
  }

  .auth-shell h1 {
    font-size: 38px;
  }
}
</style>
