<script setup>
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
        <StatusBadge :status="service.tone" :label="service.tone" />
      </div>
      <dl class="health-service-card__facts">
        <div>
          <dt>reachable</dt>
          <dd :data-state="service.reachable ? 'success' : 'danger'">
            {{ service.reachable ? '可达' : '不可达' }}
          </dd>
        </div>
        <div>
          <dt>ready</dt>
          <dd :data-state="service.ready ? 'success' : 'warning'">
            {{ service.ready ? '就绪' : '未就绪' }}
          </dd>
        </div>
      </dl>
      <p v-if="service.message" class="health-service-card__message">{{ service.message }}</p>
      <code v-if="service.path" class="health-service-card__path">{{ service.path }}</code>
    </article>
  </div>
</template>
