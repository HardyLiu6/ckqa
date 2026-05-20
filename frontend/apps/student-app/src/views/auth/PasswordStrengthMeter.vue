<script setup>
// 密码强度条
// 评分规则集中在 utils/password-strength.js，便于在注册、修改密码等场景复用
import { computed } from 'vue'
import { evaluatePasswordStrength } from '@/utils/password-strength'

const props = defineProps({
  password: {
    type: String,
    default: '',
  },
})

const evaluation = computed(() => evaluatePasswordStrength(props.password))

const segments = [0, 1, 2, 3]
</script>

<template>
  <div
    v-if="password"
    class="password-strength"
    :class="`password-strength--${evaluation.level}`"
    role="status"
    aria-live="polite"
  >
    <div class="password-strength__bars" aria-hidden="true">
      <span
        v-for="index in segments"
        :key="index"
        class="password-strength__bar"
        :class="{ 'password-strength__bar--on': index < evaluation.score }"
      />
    </div>
    <p class="password-strength__hint">
      <span class="password-strength__label">{{ evaluation.label }}</span>
      <span class="password-strength__tip">{{ evaluation.tip }}</span>
    </p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.password-strength {
  display: grid;
  gap: 8px;
  margin-top: -4px;
}

.password-strength__bars {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.password-strength__bar {
  height: 4px;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.16);
  transition: background-color 200ms ease;
}

.password-strength--weak .password-strength__bar--on {
  background: #f87171;
}

.password-strength--fair .password-strength__bar--on {
  background: #fbbf24;
}

.password-strength--good .password-strength__bar--on {
  background: #38bdf8;
}

.password-strength--strong .password-strength__bar--on {
  background: #34d399;
}

.password-strength__hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.password-strength__label {
  font-weight: 800;
  color: #fff;
}

.password-strength--weak .password-strength__label {
  color: #fecaca;
}

.password-strength--fair .password-strength__label {
  color: #fde68a;
}

.password-strength--good .password-strength__label {
  color: #bae6fd;
}

.password-strength--strong .password-strength__label {
  color: #bbf7d0;
}
</style>
