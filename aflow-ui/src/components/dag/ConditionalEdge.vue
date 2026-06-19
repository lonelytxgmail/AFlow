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
      class="conditional-edge-label"
    >
      {{ conditionSummary }}
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
  stroke: '#409EFF',
  strokeWidth: 2,
  strokeDasharray: '6 4',
}))

const conditionSummary = computed(() => {
  const condition = props.data?.condition as string | undefined
  if (!condition) return 'condition'
  return condition.length > 20 ? condition.substring(0, 18) + '...' : condition
})
</script>

<style>
.conditional-edge-label {
  font-size: 10px;
  font-weight: 500;
  color: #409EFF;
  background: #ECF5FF;
  border: 1px solid #B3D8FF;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
