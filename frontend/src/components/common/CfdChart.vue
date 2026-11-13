<template>
  <div class="cfd-chart">
    <div ref="chartRef" class="cfd-chart__canvas" :style="{ height: `${height}px` }" />
    <div v-if="!data?.points?.length" class="cfd-chart__empty">
      <div class="cfd-chart__empty-icon">🌊</div>
      <div>暂无累积流数据</div>
      <div class="cfd-chart__empty-tip">
        从 v2.15.3 起按天采集；移动任务或提交验收后会生成当日快照
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { CfdVO } from '@/api/analytics'

const props = withDefaults(defineProps<{ data: CfdVO | null; height?: number }>(), {
  height: 360,
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
  const points = props.data?.points || []
  const axisColor = dark ? '#334155' : '#e2e8f0'
  const labelColor = dark ? '#94a3b8' : '#64748b'
  const splitColor = dark ? '#273449' : '#f1f5f9'

  chart.setOption(
    {
      color: ['#909399', '#409EFF', '#E6A23C', '#67C23A'],
      grid: { left: 50, right: 22, top: 44, bottom: 42 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'line' },
      },
      legend: {
        top: 6,
        right: 4,
        itemWidth: 13,
        itemHeight: 8,
        textStyle: { color: labelColor, fontSize: 11 },
        data: ['待办', '进行中', '待验收', '已完成'],
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: points.map((point) => point.date.slice(5)),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: axisColor } },
        axisLabel: { color: labelColor, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        name: '任务数',
        minInterval: 1,
        nameTextStyle: { color: labelColor, fontSize: 11 },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: labelColor, fontSize: 11 },
        splitLine: { lineStyle: { color: splitColor, type: 'dashed' } },
      },
      series: [
        {
          name: '待办',
          type: 'line',
          stack: 'cfd',
          smooth: false,
          symbol: 'none',
          data: points.map((point) => point.todoCount),
          lineStyle: { width: 1.5 },
          areaStyle: { opacity: 0.75 },
        },
        {
          name: '进行中',
          type: 'line',
          stack: 'cfd',
          smooth: false,
          symbol: 'none',
          data: points.map((point) => point.inProgressCount),
          lineStyle: { width: 1.5 },
          areaStyle: { opacity: 0.75 },
        },
        {
          name: '待验收',
          type: 'line',
          stack: 'cfd',
          smooth: false,
          symbol: 'none',
          data: points.map((point) => point.pendingReviewCount),
          lineStyle: { width: 1.5 },
          areaStyle: { opacity: 0.75 },
        },
        {
          name: '已完成',
          type: 'line',
          stack: 'cfd',
          smooth: false,
          symbol: 'none',
          data: points.map((point) => point.doneCount),
          lineStyle: { width: 1.5 },
          areaStyle: { opacity: 0.75 },
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
.cfd-chart {
  position: relative;
  width: 100%;
}
.cfd-chart__canvas {
  width: 100%;
}
.cfd-chart__empty {
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
.cfd-chart__empty-icon {
  font-size: 34px;
}
.cfd-chart__empty-tip {
  max-width: 420px;
  color: var(--text-muted, #c0c4cc);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
</style>
