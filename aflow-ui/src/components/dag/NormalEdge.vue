<template>
  <BaseEdge
    :id="id"
    :path="edgePath"
    :marker-end="markerEnd"
    :style="edgeStyle"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, getBezierPath, type EdgeProps } from '@vue-flow/core'

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

const edgeStyle = computed(() => ({
  stroke: '#409EFF',
  strokeWidth: 2,
}))
</script>
