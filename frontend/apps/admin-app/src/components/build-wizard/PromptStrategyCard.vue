<script setup>
defineProps({
  strategyKey: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
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
    <span class="prompt-strategy-card__icon" aria-hidden="true">{{ icon }}</span>
    <span class="prompt-strategy-card__body">
      <strong class="prompt-strategy-card__title">{{ title }}</strong>
      <span class="prompt-strategy-card__desc">{{ description }}</span>
    </span>
  </button>
</template>
