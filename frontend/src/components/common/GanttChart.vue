<template>
  <div ref="rootRef" class="gantt">
    <div ref="chartRef" class="gantt__chart" />
    <div v-if="data.length === 0" class="gantt__empty">
      <div class="gantt__empty-icon">📅</div>
      <div>暂无甘特数据</div>
      <div class="gantt__empty-tip">为 Issue 设置计划起止日期后自动展示</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch, ref } from 'vue'
import * as echarts from 'echarts'

interface GanttItem {
  id: number
  issueKey?: string
  title: string
  type?: string
  priority?: string
  status?: string
  assigneeName?: string
  planStartDate: string
  planEndDate: string
  dependencies?: string
}

const props = defineProps<{ data: GanttItem[]; height?: number }>()
const rootRef = ref<HTMLElement | null>(null)
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null

const TYPE_COLOR: Record<string, string> = {
  STORY: '#4f46e5',
  TASK: '#0ea5e9',
  BUG: '#ef4444',
  EPIC: '#a855f7',
}
const STATUS_COLOR: Record<string, string> = {
  TODO: '#94a3b8',
  IN_PROGRESS: '#f59e0b',
  IN_REVIEW: '#3b82f6',
  DONE: '#10b981',
  CLOSED: '#6b7280',
}

function barColor(item: GanttItem) {
  return STATUS_COLOR[item.status || ''] || TYPE_COLOR[item.type || ''] || '#4f46e5'
}

function pctProgress(item: GanttItem) {
  if (item.status === 'DONE' || item.status === 'CLOSED') return 100
  const start = new Date(item.planStartDate).getTime()
  const end = new Date(item.planEndDate).getTime()
  const today = Date.now()
  if (end <= start) return 0
  if (today <= start) return 0
  if (today >= end) return 80
  return Math.min(80, Math.round(((today - start) / (end - start)) * 80))
}

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  if (!props.data || props.data.length === 0) {
    chart.clear()
    return
  }

  const sorted = [...props.data].sort((a, b) =>
    a.planStartDate.localeCompare(b.planStartDate))

  // categories: 任务名
  const categories = sorted.map(it => it.issueKey ? `${it.issueKey} ${it.title}` : it.title)

  // renderItem 自定义矩形条
  function renderBar(_api: any, _idx: number) {
    return {
      type: 'group',
      children: [
        { type: 'rect', shape: { x: 0, y: 0, width: 1, height: 18, r: 4 }, style: { fill: '#eef2ff' } },
        { type: 'rect', shape: { x: 0, y: 0, width: 1, height: 18, r: 4 },
          style: { fill: barColor(sorted[_idx]), opacity: 0.85 } },
      ],
    }
  }
  // 让 ECharts 根据数据值 (start, duration) 自动计算 width
  // 我们用 ECharts 自带的 custom series 配合 encode
  const starts = sorted.map(it => new Date(it.planStartDate).getTime())
  const ends = sorted.map(it => new Date(it.planEndDate).getTime())

  // 依赖线
  const idIndex = new Map<number, number>()
  sorted.forEach((it, i) => idIndex.set(it.id, i))
  const links: any[] = []
  sorted.forEach((it, idx) => {
    if (!it.dependencies) return
    let depIds: number[] = []
    try { depIds = JSON.parse(it.dependencies) } catch { return }
    depIds.forEach(depId => {
      if (idIndex.has(depId)) {
        links.push({ source: idIndex.get(depId), target: idx })
      }
    })
  })

  chart.setOption({
    grid: { left: 200, right: 24, top: 16, bottom: 36, containLabel: false },
    tooltip: {
      formatter: (params: any) => {
        const it = sorted[params.dataIndex]
        const color = barColor(it)
        return `<div style="font-size:12px">
          <div style="font-weight:600;margin-bottom:4px">${it.issueKey || ''} ${it.title}</div>
          <div>起：<b>${it.planStartDate}</b></div>
          <div>止：<b>${it.planEndDate}</b></div>
          <div>状态：<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:4px"></span>${it.status || '-'}</div>
          ${it.assigneeName ? `<div>负责人：${it.assigneeName}</div>` : ''}
        </div>`
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { show: true, lineStyle: { color: '#f1f5f9', type: 'dashed' } },
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#475569', fontSize: 12 },
    },
    series: [
      {
        type: 'custom',
        encode: { x: [1, 2], y: 0 },
        data: sorted.map((it, i) => [i, starts[i], ends[i], pctProgress(it)]),
        renderItem: (params: any, api: any) => {
          const idx = api.value(0) as number
          const start = api.coord([api.value(1), idx])
          const end = api.coord([api.value(2), idx])
          const height = 16
          const width = Math.max(8, end[0] - start[0])
          const progress = api.value(3) as number
          const color = barColor(sorted[idx])
          return {
            type: 'group',
            children: [
              { type: 'rect', shape: { x: start[0], y: start[1] - height / 2, width, height, r: 4 },
                style: { fill: '#eef2ff' } },
              { type: 'rect',
                shape: { x: start[0], y: start[1] - height / 2, width: width * progress / 100, height, r: 4 },
                style: { fill: color, opacity: 0.9 } },
              { type: 'text',
                style: {
                  x: start[0] + width + 6, y: start[1], text: `${sorted[idx].planStartDate.slice(5)} → ${sorted[idx].planEndDate.slice(5)}`,
                  textVerticalAlign: 'middle', textAlign: 'left', fill: '#64748b', font: '11px sans-serif' } },
            ],
          }
        },
        encode_visualMap: false,
        z: 5,
      },
      // 依赖关系（直线 / 箭头）：用 graph 系列
      ...(links.length > 0 ? [{
        type: 'graph',
        coordinateSystem: 'cartesian2d',
        data: sorted.map((it, i) => ({ name: it.id, value: i })),
        links: links.map(l => ({ source: sorted[l.source].id, target: sorted[l.target].id, lineStyle: { color: '#cbd5e1', width: 1.2, curveness: 0.2 } })),
        symbol: 'none',
        lineStyle: { color: '#cbd5e1', width: 1.2, curveness: 0.2 },
        z: 1,
        silent: true,
        animation: false,
      }] : []),
    ],
  }, true)
}

onMounted(() => {
  render()
  if (rootRef.value) {
    ro = new ResizeObserver(() => chart?.resize())
    ro.observe(rootRef.value)
  }
})
onBeforeUnmount(() => {
  ro?.disconnect()
  chart?.dispose()
  chart = null
})
watch(() => props.data, render, { deep: true })
</script>

<style scoped>
.gantt {
  width: 100%;
  position: relative;
}
.gantt__chart {
  width: 100%;
  min-height: 360px;
}
.gantt__empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
  pointer-events: none;
}
.gantt__empty-icon {
  font-size: 40px;
  margin-bottom: 8px;
  opacity: 0.4;
}
.gantt__empty-tip {
  font-size: 12px;
  margin-top: 4px;
  color: #cbd5e1;
}
</style>
