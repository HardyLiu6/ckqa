<script setup>
// 第三方登录占位
// 当前学生端原型还未对接微信 / QQ / GitHub OAuth，先以视觉占位呈现
// 后续接入 Java `/api/v1/auth/oauth2/{provider}/authorize` 时，把 toast 替换为实际跳转即可
import { ElMessage } from 'element-plus'

const providers = [
  { id: 'wechat', label: '微信', tone: 'wechat', glyph: 'W' },
  { id: 'qq', label: 'QQ', tone: 'qq', glyph: 'Q' },
  { id: 'github', label: 'GitHub', tone: 'github', glyph: 'G' },
]

function handleClick(provider) {
  ElMessage({
    type: 'info',
    message: `${provider.label} 登录正在对接中，敬请期待`,
    grouping: true,
  })
}
</script>

<template>
  <div class="third-party">
    <div class="third-party__divider" role="presentation">
      <span>或使用第三方账号继续</span>
    </div>
    <div class="third-party__list" role="group" aria-label="第三方登录占位">
      <button
        v-for="provider in providers"
        :key="provider.id"
        type="button"
        class="third-party__btn"
        :class="`third-party__btn--${provider.tone}`"
        :aria-label="`使用 ${provider.label} 登录（占位，暂未开放）`"
        @click="handleClick(provider)"
      >
        <span class="third-party__glyph" aria-hidden="true">{{ provider.glyph }}</span>
        <span class="third-party__label">{{ provider.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

.third-party {
  display: grid;
  gap: 14px;
}

.third-party__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.third-party__divider::before,
.third-party__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.18), transparent);
}

.third-party__list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.third-party__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.08);
  color: #f8fafc;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, background $duration-fast $ease-out,
    border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;
}

.third-party__btn:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.28);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.32);
}

.third-party__btn:focus-visible {
  outline: 2px solid #5eead4;
  outline-offset: 2px;
}

.third-party__glyph {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border-radius: 50%;
  font-weight: 900;
  font-size: 12px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
}

.third-party__btn--wechat .third-party__glyph {
  background: #07c160;
}

.third-party__btn--qq .third-party__glyph {
  background: #1989fa;
}

.third-party__btn--github .third-party__glyph {
  background: #24292f;
}
</style>
