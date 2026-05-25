<template>
  <div
    ref="chartRef"
    class="burndown-mini"
    :style="{ width: width + 'px', height: height + 'px' }"
    @click="$emit('click')"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { getBurndown } from '@/api/sprint'

const props = withDefaults(defineProps<{
  sprintId: number
  width?: number
  height?: number
}>(), {
  width: 220,
  height: 100,
})

defineEmits<{
  click: []
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

async function initChart() {
  if (!chartRef.value) return

  try {
    const res = await getBurndown(props.sprintId)
    const data = res.data
    if (!data?.points?.length) return

    const dates = data.points.map((p: any) => p.date)
    const ideal = data.points.map((p: any) => p.idealRemaining)
    const actual = data.points.map((p: any) => p.actualRemaining)

    chart = echarts.init(chartRef.value)
    chart.setOption({
      grid: { top: 4, right: 4, bottom: 4, left: 4 },
      xAxis: { show: false, data: dates },
      yAxis: { show: false },
      series: [
        {
          type: 'line',
          data: ideal,
          smooth: true,
          lineStyle: { color: '#c0c4cc', type: 'dashed', width: 1 },
          symbol: 'none',
        },
        {
          type: 'line',
          data: actual,
          smooth: true,
          lineStyle: { color: '#409EFF', width: 1.5 },
          areaStyle: { color: 'rgba(64, 158, 255, 0.1)' },
          symbol: 'none',
        },
      ],
    })
  } catch {
    // sprint has no burndown data yet
  }
}

onMounted(initChart)
onUnmounted(() => {
  chart?.dispose()
})
</script>

<style scoped lang="scss">
.burndown-mini {
  cursor: pointer;
  border-radius: var(--border-radius-sm);
  overflow: hidden;
  background: var(--bg-page);
}
</style>
