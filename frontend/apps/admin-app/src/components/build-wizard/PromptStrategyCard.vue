<script setup>
defineProps({
  strategyKey: { type: String, required: true },
  title: { type: String, required: true },
  /**
   * 一句话标语，作为卡片的 H3 副标题（一行）。
   */
  tagline: { type: String, required: true },
  /**
   * 优势文案，固定 2 条。
   */
  pros: { type: Array, default: () => [] },
  /**
   * 取舍文案，固定 2 条。
   */
  cons: { type: Array, default: () => [] },
  /**
   * 适用场景，单行。
   */
  bestFor: { type: String, default: '' },
  icon: { type: String, default: '⚙' },
  selected: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['select'])
</script>

<template>
  <button
    type="button"
    role="radio"
    :aria-checked="selected"
    :aria-disabled="disabled"
    :tabindex="disabled ? -1 : 0"
    class="prompt-strategy-card"
    :data-selected="selected ? 'true' : 'false'"
    :data-disabled="disabled ? 'true' : 'false'"
    @click="!disabled && $emit('select')"
    @keydown.space.prevent="!disabled && $emit('select')"
    @keydown.enter.prevent="!disabled && $emit('select')"
  >
    <header class="prompt-strategy-card__header">
      <span class="prompt-strategy-card__icon" aria-hidden="true">{{ icon }}</span>
      <strong class="prompt-strategy-card__title">{{ title }}</strong>
    </header>
    <p class="prompt-strategy-card__tagline">{{ tagline }}</p>
    <ul class="prompt-strategy-card__pros">
      <li v-for="(item, idx) in pros" :key="`pro-${idx}`">
        <span aria-hidden="true">✓</span>{{ ' ' }}{{ item }}
      </li>
    </ul>
    <ul class="prompt-strategy-card__cons">
      <li v-for="(item, idx) in cons" :key="`con-${idx}`">
        <span aria-hidden="true">◯</span>{{ ' ' }}{{ item }}
      </li>
    </ul>
    <p v-if="bestFor" class="prompt-strategy-card__best-for">
      <small>适合：{{ bestFor }}</small>
    </p>
  </button>
</template>
