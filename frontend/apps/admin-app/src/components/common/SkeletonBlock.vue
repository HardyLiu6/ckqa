<script setup>
const props = defineProps({
  rows: { type: Number, default: 3 },
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="skeleton-block" :class="{ compact }" aria-hidden="true">
    <span v-for="index in props.rows" :key="index" class="skeleton-row" />
  </div>
</template>
