<template>
  <div class="report-page">
    <el-tabs v-model="activeTab" class="report-page__tabs">
      <el-tab-pane label="报告列表" name="list">
        <PageHeader title="日报 / 周报" subtitle="生成、查看和导出项目报告">
          <template #action>
            <div class="report-page__actions">
              <el-select v-model="filterType" style="width: 120px" @change="fetchReports">
                <el-option label="全部" value="" />
                <el-option label="日报" value="DAILY" />
                <el-option label="周报" value="WEEKLY" />
              </el-select>
              <el-button type="primary" @click="showGenerate = true">
                <el-icon><Plus /></el-icon> 生成报告
              </el-button>
            </div>
          </template>
        </PageHeader>

        <div class="settings-card">
          <div class="settings-card__body settings-card__body--flush">
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
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="viewReport(row)">查看</el-button>
          <el-button v-if="row.status !== 'SUBMITTED'" size="small" type="success" @click="handleSubmit(row.id)">
            提交
          </el-button>
          <el-button size="small" @click="exportToPdf(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>
          </div>
        </div>

    <el-empty v-if="reports.length === 0" description="暂无报告">
      <template #image>
        <el-icon :size="60" color="#c0c4cc"><Document /></el-icon>
      </template>
      <template #description>
        <p style="color: var(--text-secondary); margin-bottom: 12px">还没有生成过报告</p>
        <p style="color: var(--text-secondary); font-size: 12px">点击上方「生成报告」按钮创建日报或周报</p>
      </template>
      <el-button type="primary" @click="showGenerate = true">
        <el-icon><Plus /></el-icon> 立即生成
      </el-button>
    </el-empty>

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
        <el-button @click="exportPdfFromView">导出 PDF</el-button>
        <el-button type="primary" @click="handleUpdateContent">保存</el-button>
      </template>
      <template #footer v-else>
        <el-button @click="exportPdfFromView">导出 PDF</el-button>
      </template>
    </el-dialog>
      </el-tab-pane>

      <!-- 数据看板 Tab -->
      <el-tab-pane label="数据看板" name="stats">
        <PageHeader title="数据看板" subtitle="项目报告数据可视化分析" />
        <div v-if="statsLoading" class="report-page__loading">加载中...</div>
        <div v-else class="stats-grid">
          <!-- 任务类型分布（始终有数据） -->
          <div class="stats-card">
            <h3>任务类型分布</h3>
            <div ref="typeChartRef" class="stats-chart"></div>
          </div>
          <!-- 状态概览（始终有数据） -->
          <div class="stats-card">
            <h3>任务状态概览</h3>
            <div ref="statusChartRef" class="stats-chart"></div>
          </div>
          <!-- 每日活动（始终有数据） -->
          <div class="stats-card stats-card--wide">
            <h3>近 7 天任务活动</h3>
            <div ref="activityChartRef" class="stats-chart"></div>
          </div>

          <!-- 原有图表：仅在数据可用时显示 -->
          <div v-if="hasWeeklyData" class="stats-card">
            <h3>本周完成任务趋势</h3>
            <div ref="weeklyChartRef" class="stats-chart"></div>
          </div>
          <div v-if="hasMemberData" class="stats-card">
            <h3>成员任务完成分布</h3>
            <div ref="memberChartRef" class="stats-chart"></div>
          </div>
          <div v-if="hasSprintData" class="stats-card">
            <h3>Sprint 故事点完成率</h3>
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
import { Plus, Document, User, TrendCharts, DataAnalysis } from "@element-plus/icons-vue";
import html2pdf from "html2pdf.js";
import PageHeader from "@/components/common/PageHeader.vue";

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

async function exportToPdf(report: any) {
  viewingReport.value = report;
  viewingContent.value = report.content || "";
  showViewDialog.value = true;
  await nextTick();
  setTimeout(() => exportPdfFromView(), 300);
}

async function exportPdfFromView() {
  if (!viewingReport.value) return;
  const el = document.querySelector(".markdown-body") as HTMLElement;
  if (!el) { ElMessage.error("无法获取报告内容"); return; }
  const reportDate = viewingReport.value.reportDate || "report";
  const filename = `report-${reportDate}.pdf`;
  const opt = {
    margin: [10, 10, 10, 10],
    filename,
    image: { type: "jpeg", quality: 0.98 },
    html2canvas: { scale: 2, useCORS: true },
    jsPDF: { unit: "mm", format: "a4", orientation: "portrait" as const },
  };
  try {
    await (html2pdf() as any).set(opt).from(el).save();
    ElMessage.success("PDF 已导出");
  } catch (e) {
    ElMessage.error("导出失败");
  }
}

onMounted(fetchReports);

// Stats Tab
const activeTab = ref("list");
const statsLoading = ref(false);
// 新增通用图表 refs
const typeChartRef = ref<HTMLElement>();
const statusChartRef = ref<HTMLElement>();
const activityChartRef = ref<HTMLElement>();
// 原有图表 refs
const weeklyChartRef = ref<HTMLElement>();
const memberChartRef = ref<HTMLElement>();
const sprintChartRef = ref<HTMLElement>();
// 数据看板空状态判断
const hasWeeklyData = ref(false);
const hasMemberData = ref(false);
const hasSprintData = ref(false);
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
    // 原有数据（条件显示）
    const weekly = data.weeklyCompleted || [];
    const members = data.memberDistribution || [];
    const sprints = data.sprintVelocity || [];
    hasWeeklyData.value = weekly.some((i: any) => i.count > 0);
    hasMemberData.value = members.length > 0;
    hasSprintData.value = sprints.length > 0;
    await nextTick();
    disposeCharts();
    // 始终渲染的通用图表
    renderTypeChart(data.typeDistribution || []);
    renderStatusChart(data.statusOverview || {});
    renderActivityChart(data.weeklyActivity || []);
    // 条件渲染的原有图表
    if (hasWeeklyData.value) renderWeeklyChart(weekly);
    if (hasMemberData.value) renderMemberChart(members);
    if (hasSprintData.value) renderSprintChart(sprints);
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
      { name: '已完成', type: 'bar', data: d.map((i: any) => i.completedPoints), itemStyle: { color: '#6366f1', borderRadius: [4,4,0,0] } },
    ],
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  });
}

// === 新增：通用图表（始终有数据）===

/** 任务类型分布 — 饼图 */
function renderTypeChart(d: any[]) {
  if (!typeChartRef.value) return;
  const c = echarts.init(typeChartRef.value);
  charts.push(c);
  const colors = ['#6366f1', '#ef4444', '#10b981'];
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 5, top: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      label: { show: true, formatter: '{b}\n{c}', fontSize: 11 },
      data: d.map((item: any) => ({ name: item.label, value: item.count })),
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      color: colors,
    }],
  });
}

/** 状态概览 — 环形进度 */
function renderStatusChart(overview: any) {
  if (!statusChartRef.value) return;
  const c = echarts.init(statusChartRef.value);
  charts.push(c);
  const total = overview.totalCount || 1;
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    legend: { orient: 'vertical', right: 5, top: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['35%', '50%'],
      label: {
        show: true,
        position: 'center',
        formatter: () => `{total|${total}}\n{label|任务总数}`,
        rich: { total: { fontSize: 24, fontWeight: 'bold', color: '#0f172a' }, label: { fontSize: 11, color: '#94a3b8', padding: [4, 0, 0, 0] } },
      },
      data: [
        { name: '待办', value: overview.todoCount || 0, itemStyle: { color: '#94a3b8' } },
        { name: '进行中', value: overview.inProgressCount || 0, itemStyle: { color: '#f59e0b' } },
        { name: '已完成', value: overview.doneCount || 0, itemStyle: { color: '#10b981' } },
      ],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    }],
  });
}

/** 每日活动 — 柱状堆叠图 */
function renderActivityChart(d: any[]) {
  if (!activityChartRef.value) return;
  const c = echarts.init(activityChartRef.value);
  charts.push(c);
  c.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 15, right: 16, bottom: 25, left: 38 },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [
      { name: '新建', type: 'bar', stack: 'activity', data: d.map((i: any) => i.created), itemStyle: { color: '#6366f1', borderRadius: [3,3,0,0] }, barMaxWidth: 28 },
      { name: '更新', type: 'bar', stack: 'activity', data: d.map((i: any) => i.updated), itemStyle: { color: '#a78bfa', borderRadius: [0,0,3,3] }, barMaxWidth: 28 },
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
.report-page {
  max-width: 1100px;
  margin: 0 auto;
}

.report-page__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.report-page__loading {
  text-align: center;
  padding: 40px;
  color: var(--text-secondary);
}

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
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--transition-normal);
}
.stats-card:hover {
  box-shadow: var(--shadow-md);
}
.stats-card--wide {
  grid-column: 1 / -1; /* 占满整行 */
}
.stats-card:nth-child(3) {
  grid-column: 1 / -1;
}
.stats-card h3 {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--text-primary);
}
.stats-card__empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  text-align: center;
  p {
    margin: 10px 0 4px;
    font-size: 13px;
    color: var(--text-secondary);
  }
}
.stats-card__empty-sub {
  font-size: 11px;
  color: var(--text-secondary);
  opacity: 0.7;
}
.stats-chart {
  width: 100%;
  height: 240px;
}
.stats-card:nth-child(3) .stats-chart {
  height: 280px;
}

.settings-card__body--flush {
  padding: 0;
}
</style>
