<!-- 选中实体的右侧详情抽屉 -->
<script setup>
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'

const props = defineProps({
  entity: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['ask-question', 'expand'])

const description = computed(() => props.entity?.description ?? '')
const communityPath = computed(() => props.entity?.communityPath ?? [])
const chunkCount = computed(() => props.entity?.chunkCount ?? 0)
</script>

<template>
  <GlassCard tier="base" padding="md" class="entity-detail">
    <template v-if="entity">
      <header class="entity-head">
        <span class="dot" />
        <h3 class="name">{{ entity.name || entity.id }}</h3>
      </header>
      <div class="meta">
        <ModuleTag v-if="entity.type" module="knowledge" size="sm">
          {{ entity.type }}
        </ModuleTag>
        <ModuleTag module="analysis" size="sm">
          {{ chunkCount }} 个相关原文
        </ModuleTag>
      </div>

      <p v-if="description" class="desc">{{ description }}</p>
      <p v-else class="desc desc--empty">该节点暂无描述。</p>

      <section v-if="communityPath.length" class="path">
        <div class="path-label">所属社区</div>
        <ol class="path-list">
          <li v-for="(item, idx) in communityPath" :key="`${item.level}-${item.communityId}`">
            <span class="path-level">L{{ item.level }}</span>
            <span class="path-title">{{ item.title || `社区 ${item.communityId}` }}</span>
            <span v-if="idx < communityPath.length - 1" class="path-sep">›</span>
          </li>
        </ol>
      </section>

      <div class="actions">
        <GlowButton size="md" block @click="emit('ask-question', entity)">
          以这个概念去问答
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
        <button class="ghost" type="button" @click="emit('expand', entity)">
          扩展 1 跳邻域
        </button>
      </div>
    </template>
    <template v-else>
      <p class="placeholder">点选画布上的节点查看详情。</p>
    </template>
  </GlassCard>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.entity-detail {
  --module-color-500: #0d9488;
  border-color: rgba(13, 148, 136, 0.2) !important;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.entity-head {
  display: flex;
  align-items: center;
  gap: 8px;

  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #0d9488;
    box-shadow: 0 0 8px rgba(13, 148, 136, 0.7);
  }

  .name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin: 0;
  }
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.desc {
  font-size: 13px;
  line-height: 1.65;
  color: #475569;
  margin: 0;
  max-height: 240px;
  overflow-y: auto;
}

.desc--empty {
  color: #94a3b8;
}

.path {
  .path-label {
    font-size: 11px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 6px;
  }

  .path-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    list-style: none;
    padding: 0;
    margin: 0;

    li {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #0f172a;
    }
  }

  .path-level {
    color: #64748b;
    font-weight: 600;
  }

  .path-title {
    background: rgba(13, 148, 136, 0.06);
    padding: 2px 8px;
    border-radius: $radius-sm;
  }

  .path-sep {
    color: #94a3b8;
  }
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ghost {
  border: 1px dashed rgba(13, 148, 136, 0.4);
  background: transparent;
  color: #0d9488;
  padding: 8px 0;
  border-radius: $radius-md;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    background: rgba(13, 148, 136, 0.06);
  }
}

.placeholder {
  color: #94a3b8;
  font-size: 13px;
  margin: 0;
}
</style>
