<template>
  <div class="metric-card" :style="cardStyle">
    <div class="metric-card__header">
      <span class="metric-card__title">{{ title }}</span>
      <el-icon v-if="icon" class="metric-card__icon" :style="iconStyle" :size="18">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="metric-card__value">{{ value }}</div>
    <div v-if="trend" class="metric-card__trend" :class="trendClass">
      <span class="metric-card__trend-arrow">{{ trendArrow }}</span>
      <span v-if="trendValue" class="metric-card__trend-value">{{ trendValue }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

export interface MetricCardProps {
  title: string
  value: string | number
  trend?: 'up' | 'down' | 'flat'
  trendValue?: string
  icon?: Component
  color?: string
}

const props = withDefaults(defineProps<MetricCardProps>(), {
  trend: undefined,
  trendValue: undefined,
  icon: undefined,
  color: undefined
})

const trendClass = computed(() => {
  if (!props.trend) return ''
  return `metric-card__trend--${props.trend}`
})

const trendArrow = computed(() => {
  switch (props.trend) {
    case 'up':
      return '↑'
    case 'down':
      return '↓'
    case 'flat':
      return '→'
    default:
      return ''
  }
})

const cardStyle = computed(() => {
  if (!props.color) return {}
  return {
    '--metric-card-accent': props.color
  }
})

const iconStyle = computed(() => {
  if (!props.color) return {}
  return { color: props.color }
})
</script>

<style scoped>
.metric-card {
  --metric-card-accent: #409eff;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.3s ease, transform 0.2s ease;
  cursor: default;
}

.metric-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.metric-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.metric-card__title {
  font-size: 14px;
  color: #909399;
  line-height: 1.4;
}

.metric-card__icon {
  color: var(--metric-card-accent);
  opacity: 0.8;
}

.metric-card__value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
  margin-bottom: 8px;
}

.metric-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
}

.metric-card__trend--up {
  color: #67c23a;
}

.metric-card__trend--down {
  color: #f56c6c;
}

.metric-card__trend--flat {
  color: #909399;
}

.metric-card__trend-arrow {
  font-size: 14px;
}

.metric-card__trend-value {
  font-size: 13px;
}
</style>
