<template>
  <div>
    <el-button @click="$router.back()" text><el-icon><ArrowLeft /></el-icon> 返回看板</el-button>

    <el-tabs v-model="activeTab" style="margin-top: 16px">
      <el-tab-pane label="报告列表" name="list">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <h2>日报/周报</h2>
          <div style="display: flex; gap: 8px">
            <el-select v-model="filterType" style="width: 120px" @change="fetchReports">
              <el-option label="全部" value="" />
              <el-option label="日报" value="DAILY" />
              <el-option label="周报" value="WEEKLY" />
            </el-select>
            <el-button type="primary" @click="showGenerate = true">生成报告</el-button>
          </div>
        </div>

    <el-table :data="reports" stripe>
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'DAILY' ? '' : 'warning'" size="small">
            {{ row.type === 'DAILY' ? '日报' : '周报' }}
          </el-tag>
        </template>
    </el-table-column>
      <el-table-column prop="reportDate" label="日期" width="130" />
      <el-table-column prop="projectName" label="项目" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'info'" size="small">
            {{ row.status === 'SUBMITTED' ? '已提交' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="viewReport(row)">查看</el-button>
          <el-button v-if="row.status !== 'SUBMITTED'" size="small" type="success" @click="handleSubmit(row.id)">
            提交
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="reports.length === 0" description="暂无报告" />

    <!-- 生成报告 Dialog -->
    <el-dialog v-model="showGenerate" title="生成报告" width="400px">
      <el-form label-position="top">
        <el-form-item label="类型">
          <el-select v-model="genType" style="width: 100%">
            <el-option label="日报" value="DAILY" />
            <el-option label="周报" value="WEEKLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="genDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerate = false">取消</el-button>
        <el-button type="primary" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 查看报告 Dialog -->
    <el-dialog v-model="showViewDialog" title="报告详情" width="600px">
      <div class="markdown-body" v-html="renderMarkdown(viewingContent)"></div>
      <template #footer v-if="viewingReport?.status !== 'SUBMITTED'">
        <el-button type="primary" @click="handleUpdateContent">保存</el-button>
      </template>
    </el-dialog>
      </el-tab-pane>

      <!-- 数据看板 Tab -->
      <el-tab-pane label="数据看板" name="stats">
        <div v-if="statsLoading" style="text-align: center; padding: 40px; color: var(--text-secondary)">
          加载中...
        </div>
        <div v-else class="stats-grid">
          <!-- 本周完成任务趋势 -->
          <div class="stats-card">
            <h3>本周完成任务趋势</h3>
            <div ref="weeklyChartRef" class="stats-chart"></div>
          </div>
          <!-- 成员任务完成分布 -->
          <div class="stats-card">
            <h3>成员任务完成分布</h3>
            <div ref="memberChartRef" class="stats-chart"></div>
          </div>
          <!-- Sprint 故事点完成率 -->
          <div class="stats-card">
            <h3>最近 Sprint 故事点完成率</h3>
            <div ref="sprintChartRef" class="stats-chart"></div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from "vue";
import * as echarts from "echarts";
import { useRoute } from "vue-router";
import { generateReport, getReports, submitReport, updateReport, getProjectStats } from "@/api/report";
import { renderMarkdown } from "@/utils/markdown";
import { ElMessage } from "element-plus";

const route = useRoute();
const projectId = Number(route.params.id);
const reports = ref<any[]>([]);
const filterType = ref("");
const showGenerate = ref(false);
const genType = ref("DAILY");
const genDate = ref("");
const showViewDialog = ref(false);
const viewingReport = ref<any>(null);
const viewingContent = ref("");

async function fetchReports() {
  const res = await getReports({ projectId, type: filterType.value || undefined });
  reports.value = res.data || [];
}

async function handleGenerate() {
  await generateReport({ projectId, type: genType.value, reportDate: genDate.value || undefined });
  ElMessage.success("报告已生成");
  showGenerate.value = false;
  await fetchReports();
}

function viewReport(report: any) {
  viewingReport.value = report;
  viewingContent.value = report.content || "";
  showViewDialog.value = true;
}

async function handleSubmit(id: number) {
  await submitReport(id);
  ElMessage.success("已提交");
  await fetchReports();
}

async function handleUpdateContent() {
  if (!viewingReport.value) return;
  await updateReport({ id: viewingReport.value.id, content: viewingContent.value });
  ElMessage.success("已保存");
  showViewDialog.value = false;
  await fetchReports();
}

onMounted(fetchReports);

// Stats Tab
const activeTab = ref("list");
const statsLoading = ref(false);
const weeklyChartRef = ref<HTMLElement>();
const memberChartRef = ref<HTMLElement>();
const sprintChartRef = ref<HTMLElement>();
let charts: echarts.ECharts[] = [];

function disposeCharts() {
  charts.forEach(c => c.dispose());
  charts = [];
}

async function loadStats() {
  statsLoading.value = true;
  try {
    const res = await getProjectStats(projectId);
    const data = res.data;
    await nextTick();
    disposeCharts();
    renderWeeklyChart(data.weeklyCompleted || []);
    renderMemberChart(data.memberDistribution || []);
    renderSprintChart(data.sprintVelocity || []);
  } catch { /* handled */ }
  finally { statsLoading.value = false; }
}

function renderWeeklyChart(d: any[]) {
  if (!weeklyChartRef.value) return;
  const c = echarts.init(weeklyChartRef.value);
  charts.push(c);
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 10, right: 10, bottom: 20, left: 36 },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'line', data: d.map((i: any) => i.count),
      smooth: true, lineStyle: { color: '#4f6ef6', width: 2 },
      areaStyle: { color: 'rgba(79,110,246,0.1)' },
      itemStyle: { color: '#4f6ef6' },
    }],
  });
}

function renderMemberChart(d: any[]) {
  if (!memberChartRef.value || !d.length) return;
  const c = echarts.init(memberChartRef.value);
  charts.push(c);
  c.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['45%', '72%'],
      data: d.map((i: any) => ({ name: i.username, value: i.count })),
      label: { fontSize: 11 },
      emphasis: { label: { fontWeight: 'bold' } },
    }],
    color: ['#4f6ef6', '#10b981', '#f59e0b', '#ef4444', '#6366f1', '#ec4899', '#84cc16', '#06b6d4'],
  });
}

function renderSprintChart(d: any[]) {
  if (!sprintChartRef.value || !d.length) return;
  const c = echarts.init(sprintChartRef.value);
  charts.push(c);
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 10, right: 10, bottom: 20, left: 36 },
    xAxis: { type: 'category', data: d.map((i: any) => i.sprintName), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [
      { name: '总故事点', type: 'bar', data: d.map((i: any) => i.totalPoints), itemStyle: { color: '#cbd5e1', borderRadius: [4,4,0,0] }, barGap: '10%' },
      { name: '已完成', type: 'bar', data: d.map((i: any) => i.completedPoints), itemStyle: { color: '#4f6ef6', borderRadius: [4,4,0,0] } },
    ],
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  });
}

// Watch tab switch to load stats
import { watch } from "vue";
watch(activeTab, (v) => {
  if (v === 'stats') loadStats();
});
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.stats-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 16px;
}
.stats-card:nth-child(3) {
  grid-column: 1 / -1;
}
.stats-card h3 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}
.stats-chart {
  width: 100%;
  height: 240px;
}
.stats-card:nth-child(3) .stats-chart {
  height: 280px;
}
</style>
