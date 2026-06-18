<!--
  BurndownChart — Sprint 燃尽图 (ECharts)
  折线图：理想剩余 vs 实际剩余
  接受 BurndownVO { totalPoints, points: [{date, idealRemaining, actualRemaining}] }
-->
<template>
  <div class="burndown">
    <div ref="chartRef" class="burndown__chart" />
    <div v-if="!data?.points || data.points.length === 0" class="burndown__empty">
      <div class="burndown__empty-icon">📉</div>
      <div>暂无燃尽数据</div>
      <div class="burndown__empty-tip">Sprint 启动后每日自动生成快照</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch, ref } from 'vue'
import * as echarts from 'echarts'

interface BurndownPoint {
  date: string
  idealRemaining: number
  actualRemaining: number
}
interface BurndownVO {
  sprintId?: number
  sprintName?: string
  startDate?: string
  endDate?: string
  totalPoints?: number
  points?: BurndownPoint[]
}

const props = defineProps<{ data: BurndownVO | null; height?: number }>()

const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const points = props.data?.points || []
  const dates = points.map(p => p.date.slice(5))   // MM-DD
  const ideal = points.map(p => p.idealRemaining)
  const actual = points.map(p => p.actualRemaining)

  chart.setOption({
    grid: { left: 48, right: 24, top: 32, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#4f46e5' } },
      formatter: (params: any[]) => {
        const date = params[0]?.axisValueLabel
        const lines = params.map(p => {
          const dot = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:4px"></span>`
          return `${dot}${p.marker ?? ''} ${p.seriesName}: <b>${p.value}</b> 点`
        })
        return `<div style="font-size:12px"><div style="margin-bottom:4px;font-weight:600">${date}</div>${lines.join('<br/>')}</div>`
      },
    },
    legend: {
      data: ['理想剩余', '实际剩余'],
      top: 4,
      right: 0,
      itemWidth: 14,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: '#64748b' },
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '故事点',
      nameTextStyle: { color: '#94a3b8', fontSize: 11, padding: [0, 0, 0, -28] },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
    },
    series: [
      {
        name: '理想剩余',
        type: 'line',
        data: ideal,
        smooth: false,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#94a3b8', width: 1.5, type: 'dashed' },
        itemStyle: { color: '#94a3b8' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(148,163,184,0.18)' },
          { offset: 1, color: 'rgba(148,163,184,0)' },
        ]) },
        z: 1,
      },
      {
        name: '实际剩余',
        type: 'line',
        data: actual,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#4f46e5', width: 2.5 },
        itemStyle: { color: '#4f46e5' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(79,70,229,0.18)' },
          { offset: 1, color: 'rgba(79,70,229,0)' },
        ]) },
        markLine: {
          symbol: 'none',
          data: [{ yAxis: 0, label: { show: false } }],
          lineStyle: { color: '#10b981', width: 1, type: 'solid' },
        },
        z: 2,
      },
    ],
  }, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  if (chartRef.value) {
    ro = new ResizeObserver(handleResize)
    ro.observe(chartRef.value)
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  ro?.disconnect()
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(() => props.data, () => render(), { deep: true })
</script>

<style scoped lang="scss">
.burndown {
  position: relative;
  width: 100%;
  min-height: 280px;
}
.burndown__chart {
  width: 100%;
  height: 100%;
  min-height: 280px;
}
.burndown__empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-tertiary, #94a3b8);
  font-size: 14px;
  pointer-events: none;
}
.burndown__empty-icon {
  font-size: 36px;
  margin-bottom: 4px;
}
.burndown__empty-tip {
  font-size: 12px;
  color: var(--text-tertiary, #c0c4cc);
}
</style>
