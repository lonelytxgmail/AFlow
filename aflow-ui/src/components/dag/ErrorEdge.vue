<template>
  <BaseEdge
    :id="id"
    :path="edgePath"
    :marker-end="markerEnd"
    :style="edgeStyle"
  />
  <EdgeLabelRenderer>
    <div
      :style="{
        position: 'absolute',
        transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
        pointerEvents: 'all',
      }"
      class="error-edge-label"
    >
      error
    </div>
  </EdgeLabelRenderer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps>()

const pathParams = computed(() =>
  getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition,
  })
)

const edgePath = computed(() => pathParams.value[0])
const labelX = computed(() => pathParams.value[1])
const labelY = computed(() => pathParams.value[2])

const edgeStyle = computed(() => ({
  stroke: '#F56C6C',
  strokeWidth: 2,
  strokeDasharray: '6 4',
}))
</script>

<style>
.error-edge-label {
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #F56C6C;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
</style>
