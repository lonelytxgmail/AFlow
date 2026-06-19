<template>
  <div
    v-if="visible"
    class="edge-type-selector"
    :style="positionStyle"
    @click.stop
  >
    <div class="edge-type-selector__title">选择边类型</div>
    <div class="edge-type-selector__options">
      <div
        class="edge-type-option"
        @click="select('normal')"
      >
        <span class="option-indicator normal-indicator"></span>
        <span class="option-label">Normal</span>
        <span class="option-desc">普通连接</span>
      </div>
      <div
        class="edge-type-option"
        @click="select('error')"
      >
        <span class="option-indicator error-indicator"></span>
        <span class="option-label">Error</span>
        <span class="option-desc">错误处理</span>
      </div>
      <div
        class="edge-type-option"
        @click="select('conditional')"
      >
        <span class="option-indicator conditional-indicator"></span>
        <span class="option-label">Conditional</span>
        <span class="option-desc">条件路由</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EdgeType } from '@/types'

const props = defineProps<{
  visible: boolean
  position: { x: number; y: number }
}>()

const emit = defineEmits<{
  select: [type: EdgeType]
  cancel: []
}>()

const positionStyle = computed(() => ({
  left: `${props.position.x}px`,
  top: `${props.position.y}px`,
}))

function select(type: EdgeType) {
  emit('select', type)
}
</script>

<style scoped>
.edge-type-selector {
  position: absolute;
  z-index: 200;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 8px 0;
  min-width: 160px;
  animation: fadeIn 0.15s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.edge-type-selector__title {
  font-size: 12px;
  color: #909399;
  padding: 4px 12px 8px;
  border-bottom: 1px solid #f2f3f5;
  margin-bottom: 4px;
}

.edge-type-selector__options {
  display: flex;
  flex-direction: column;
}

.edge-type-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.edge-type-option:hover {
  background: #f5f7fa;
}

.option-indicator {
  width: 20px;
  height: 3px;
  border-radius: 2px;
  flex-shrink: 0;
}

.normal-indicator {
  background: #409EFF;
}

.error-indicator {
  background: #F56C6C;
  background-image: repeating-linear-gradient(
    90deg,
    #F56C6C 0px,
    #F56C6C 4px,
    transparent 4px,
    transparent 7px
  );
}

.conditional-indicator {
  background: #409EFF;
  background-image: repeating-linear-gradient(
    90deg,
    #409EFF 0px,
    #409EFF 4px,
    transparent 4px,
    transparent 7px
  );
}

.option-label {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.option-desc {
  font-size: 11px;
  color: #909399;
  margin-left: auto;
}
</style>
