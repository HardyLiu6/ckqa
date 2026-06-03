<script setup>
import { Plug, Activity } from 'lucide-vue-next'

import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  services: { type: Array, required: true },
})
</script>

<template>
  <div class="health-matrix">
    <article
      v-for="service in services"
      :key="service.key"
      class="health-service-card"
      :data-tone="service.tone"
    >
      <div class="health-service-card__heading">
        <h3>{{ service.label }}</h3>
        <StatusBadge :status="service.tone" :label="service.statusLabel" />
      </div>

      <span class="health-service-card__kind">{{ service.kind }}</span>

      <p class="health-service-card__detail">{{ service.detail }}</p>

      <div class="health-service-card__facts">
        <span class="health-fact" :data-state="service.reachable ? 'success' : 'danger'">
          <Plug :size="13" aria-hidden="true" />
          连通 · {{ service.reachable ? '已连接' : '未连接' }}
        </span>
        <span class="health-fact" :data-state="service.ready ? 'success' : 'warning'">
          <Activity :size="13" aria-hidden="true" />
          就绪 · {{ service.ready ? '已就绪' : '未就绪' }}
        </span>
      </div>

      <template v-if="service.tone !== 'success'">
        <p class="health-service-card__hint">{{ service.hint }}</p>
        <code v-if="service.message" class="health-service-card__message">{{ service.message }}</code>
      </template>
    </article>
  </div>
</template>
