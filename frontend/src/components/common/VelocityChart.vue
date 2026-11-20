<template>
  <div class="velocity-chart">
    <div ref="chartRef" class="velocity-chart__canvas" :style="{ height: `${height}px` }" />
    <div v-if="!data?.sprints?.length" class="velocity-chart__empty">
      <div class="velocity-chart__empty-icon">📊</div>
      <div>暂无 Velocity 数据</div>
      <div class="velocity-chart__empty-tip">完成至少一个 Sprint 后开始积累历史速度</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from "@/utils/echarts"
import type { VelocityVO } from '@/api/analytics'

const props = withDefaults(defineProps<{ data: VelocityVO | null; height?: number }>(), {
  height: 320,
})

const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

function isDark() {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const dark = isDark()
  const sprints = props.data?.sprints || []
  const average = props.data?.averageCompletedPoints || 0

  chart.setOption(
    {
      grid: { left: 52, right: 24, top: 42, bottom: 42 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any[]) => {
          const name = params[0]?.axisValueLabel || ''
          const lines = params.map(
            (item) =>
              `${item.marker}${item.seriesName}: <b>${item.value}</b> 故事点`,
          )
          return `<div style="font-size:12px"><b>${name}</b><br/>${lines.join('<br/>')}</div>`
        },
      },
      legend: {
        top: 5,
        right: 4,
        itemWidth: 14,
        itemHeight: 8,
        textStyle: { color: dark ? '#94a3b8' : '#64748b', fontSize: 11 },
      },
      xAxis: {
        type: 'category',
        data: sprints.map((sprint) => sprint.sprintName),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: dark ? '#334155' : '#e2e8f0' } },
        axisLabel: {
          color: dark ? '#94a3b8' : '#64748b',
          fontSize: 11,
          interval: 0,
          width: 90,
          overflow: 'truncate',
        },
      },
      yAxis: {
        type: 'value',
        name: '故事点',
        nameTextStyle: { color: dark ? '#94a3b8' : '#64748b', fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: dark ? '#94a3b8' : '#64748b', fontSize: 11 },
        splitLine: {
          lineStyle: { color: dark ? '#273449' : '#f1f5f9', type: 'dashed' },
        },
      },
      series: [
        {
          name: '承诺点数',
          type: 'bar',
          data: sprints.map((sprint) => sprint.committedPoints),
          barMaxWidth: 30,
          itemStyle: { color: dark ? '#475569' : '#cbd5e1', borderRadius: [5, 5, 0, 0] },
        },
        {
          name: '完成点数',
          type: 'bar',
          data: sprints.map((sprint) => sprint.completedPoints),
          barMaxWidth: 30,
          itemStyle: { color: '#6366f1', borderRadius: [5, 5, 0, 0] },
          markLine: average > 0
            ? {
                symbol: 'none',
                silent: true,
                lineStyle: { color: '#10b981', type: 'dashed', width: 1.5 },
                label: {
                  color: '#059669',
                  formatter: `平均 ${average}`,
                  position: 'insideEndTop',
                },
                data: [{ yAxis: average }],
              }
            : undefined,
        },
      ],
    },
    true,
  )
}

function resize() {
  chart?.resize()
}

function handleThemeChange() {
  render()
}

onMounted(() => {
  render()
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(chartRef.value)
  }
  themeObserver = new MutationObserver(handleThemeChange)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

watch(() => props.data, render, { deep: true })
</script>

<style scoped>
.velocity-chart {
  position: relative;
  width: 100%;
}
.velocity-chart__canvas {
  width: 100%;
}
.velocity-chart__empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--text-tertiary, #94a3b8);
  font-size: 14px;
  pointer-events: none;
}
.velocity-chart__empty-icon {
  font-size: 34px;
}
.velocity-chart__empty-tip {
  color: var(--text-muted, #c0c4cc);
  font-size: 12px;
}
</style>
