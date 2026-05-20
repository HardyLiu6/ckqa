<!-- 知识图谱图例面板 -->
<script setup>
defineProps({
  /** 社区颜色映射 [{ name, color }] */
  communities: { type: Array, default: () => [] },
})
</script>

<template>
  <div class="graph-legend">
    <div class="legend-title">图例说明</div>
    <div class="legend-section">
      <div class="legend-subtitle">节点大小</div>
      <div class="legend-item">
        <span class="dot dot-lg" />
        <span>章节节点（按重要性缩放）</span>
      </div>
      <div class="legend-item">
        <span class="dot dot-sm" />
        <span>实体节点（按关联度缩放）</span>
      </div>
    </div>
    <div class="legend-section">
      <div class="legend-subtitle">节点颜色 = 所属章节</div>
      <div v-for="c in communities.slice(0, 8)" :key="c.name" class="legend-item">
        <span class="dot" :style="{ background: c.color }" />
        <span class="legend-label">{{ c.name }}</span>
      </div>
      <div v-if="communities.length > 8" class="legend-item">
        <span class="dot" style="background: #94a3b8" />
        <span class="legend-label">其他 {{ communities.length - 8 }} 个章节</span>
      </div>
    </div>
    <div class="legend-section">
      <div class="legend-subtitle">交互</div>
      <div class="legend-item"><span class="icon">👆</span><span>单击：查看详情</span></div>
      <div class="legend-item"><span class="icon">👆👆</span><span>双击：展开子节点</span></div>
      <div class="legend-item"><span class="icon">🖱️</span><span>拖拽：移动画布/节点</span></div>
      <div class="legend-item"><span class="icon">🔍</span><span>滚轮：缩放</span></div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.graph-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 11px;
  color: #475569;
  max-width: 220px;
  max-height: 320px;
  overflow-y: auto;
  z-index: 10;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.legend-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.legend-section {
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f1f5f9;

  &:last-child {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
  }
}

.legend-subtitle {
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
  line-height: 1.4;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #0d9488;

  &.dot-lg {
    width: 14px;
    height: 14px;
  }

  &.dot-sm {
    width: 8px;
    height: 8px;
  }
}

.icon {
  width: 16px;
  text-align: center;
  flex-shrink: 0;
}

.legend-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
