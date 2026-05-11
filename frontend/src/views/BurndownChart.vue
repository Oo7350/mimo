<template>
  <div>
    <el-button @click="$router.back()" text><el-icon><ArrowLeft /></el-icon> 返回 Sprint</el-button>

    <div style="margin: 16px 0">
      <h2>{{ data?.sprintName }} - 燃尽图</h2>
      <div style="color: #909399">
        {{ data?.startDate }} ~ {{ data?.endDate }} | 总故事点: {{ data?.totalPoints }}
      </div>
    </div>

    <el-card shadow="hover">
      <div ref="chartRef" style="width: 100%; height: 450px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from "vue";
import { useRoute } from "vue-router";
import { getBurndown } from "@/api/sprint";
import * as echarts from "echarts";

const route = useRoute();
const sprintId = Number(route.params.sprintId);
const projectId = Number(route.params.id);
const chartRef = ref<HTMLDivElement>();
const data = ref<any>(null);
let chart: echarts.ECharts | null = null;

async function fetchData() {
  const res = await getBurndown(sprintId);
  data.value = res.data;
  await nextTick();
  renderChart();
}

function renderChart() {
  if (!chartRef.value || !data.value) return;
  if (!chart) chart = echarts.init(chartRef.value);

  const points = data.value.points || [];
  const dates = points.map((p: any) => p.date);
  const ideal = points.map((p: any) => p.idealRemaining);
  const actual = points.map((p: any) => p.actualRemaining);

  chart.setOption({
    tooltip: { trigger: "axis" },
    legend: { data: ["理想剩余", "实际剩余"] },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value", name: "故事点" },
    series: [
      {
        name: "理想剩余",
        type: "line",
        data: ideal,
        lineStyle: { type: "dashed", color: "#909399" },
        itemStyle: { color: "#909399" },
      },
      {
        name: "实际剩余",
        type: "line",
        data: actual,
        areaStyle: { color: "rgba(64, 158, 255, 0.15)" },
        itemStyle: { color: "#409EFF" },
      },
    ],
  });
}

watch(() => data.value, renderChart);

onMounted(fetchData);
</script>
