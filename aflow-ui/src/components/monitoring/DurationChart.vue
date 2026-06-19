<template>
  <div class="duration-chart">
    <v-chart :option="chartOption" autoresize style="height: 300px" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ComposeOption } from 'echarts/core'
import type { BarSeriesOption } from 'echarts/charts'
import type {
  TitleComponentOption,
  TooltipComponentOption,
  GridComponentOption,
  LegendComponentOption,
} from 'echarts/components'
import type { NodeDurationEntry } from '@/api/types'

// Register required ECharts modules
use([TitleComponent, TooltipComponent, GridComponent, LegendComponent, BarChart, CanvasRenderer])

type EChartsOption = ComposeOption<
  | TitleComponentOption
  | TooltipComponentOption
  | GridComponentOption
  | LegendComponentOption
  | BarSeriesOption
>

const props = defineProps<{
  data: NodeDurationEntry[]
}>()

const chartOption = computed<EChartsOption>(() => {
  const nodeTypes = props.data.map((entry) => entry.nodeType)
  const avgDurations = props.data.map((entry) => entry.avgDurationMs)
  const p95Durations = props.data.map((entry) => entry.p95DurationMs)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params: unknown) {
        const items = params as Array<{
          seriesName: string
          value: number
          marker: string
          axisValueLabel: string
        }>
        if (!items || items.length === 0) return ''
        let html = `<strong>${items[0].axisValueLabel}</strong><br/>`
        for (const item of items) {
          html += `${item.marker} ${item.seriesName}: <strong>${item.value.toFixed(1)}ms</strong><br/>`
        }
        return html
      },
    },
    legend: {
      data: ['平均耗时', 'P95 耗时'],
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: 40,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: nodeTypes,
      axisLabel: {
        rotate: nodeTypes.length > 6 ? 30 : 0,
      },
    },
    yAxis: {
      type: 'value',
      name: 'ms',
      nameLocation: 'end',
    },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        data: avgDurations,
        itemStyle: { color: '#409eff' },
        barMaxWidth: 40,
      },
      {
        name: 'P95 耗时',
        type: 'bar',
        data: p95Durations,
        itemStyle: { color: '#e6a23c' },
        barMaxWidth: 40,
      },
    ],
  }
})
</script>

<style scoped>
.duration-chart {
  width: 100%;
}
</style>
