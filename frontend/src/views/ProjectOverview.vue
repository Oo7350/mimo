<template>
  <div class="overview">
    <div class="overview__header">
      <div>
        <el-button @click="$router.push('/')" text><el-icon><ArrowLeft /></el-icon></el-button>
        <h2 class="overview__title">{{ projectName }}</h2>
      </div>
      <el-button id="create-task-btn" type="primary" @click="showCreateIssue = true">
        <el-icon><Plus /></el-icon>新建工作项
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="概览" name="overview">
        <div v-if="loading" class="overview__loading">
          <SkeletonLoader variant="card" :count="4" />
        </div>
        <div v-else class="overview__grid">
          <!-- 任务状态分布 -->
          <div class="ov-card">
            <h4>任务状态分布</h4>
            <div ref="statusChartRef" class="ov-card__chart"></div>
          </div>
          <!-- 成员工作量 -->
          <div class="ov-card">
            <h4>成员工作量</h4>
            <div v-if="memberData.length === 0" style="text-align: center; padding: 30px; color: var(--text-secondary)">
              暂无数据
            </div>
            <div v-else>
              <div ref="memberChartRef" class="ov-card__chart" style="height: 160px"></div>
              <div style="margin-top: 8px; max-height: 120px; overflow-y: auto">
                <div v-for="(m, idx) in memberData" :key="m.name" class="ov-member-row">
                  <span class="ov-member-row__name">{{ m.name }}</span>
                  <span class="ov-member-row__count">{{ m.count }} 个任务</span>
                  <el-progress
                    :percentage="memberCompletionPct(m.name)"
                    :color="memberCompletionPct(m.name) >= 60 ? '#10b981' : memberCompletionPct(m.name) >= 30 ? '#f59e0b' : '#ef4444'"
                    style="width: 80px"
                  />
                </div>
              </div>
            </div>
          </div>
          <!-- 即将到期任务 -->
          <div class="ov-card">
            <h4>即将到期</h4>
            <div v-if="upcomingTasks.length === 0" class="ov-card__empty">
              暂无近期截止的任务
            </div>
            <div v-else class="ov-card__list">
              <div
                v-for="t in upcomingTasks"
                :key="t.id"
                :class="['ov-task', { 'ov-task--overdue': isOverdue(t.dueDate) }]"
                @click="$router.push(`/projects/${projectId}/board?issue=${t.id}`)"
              >
                <span class="ov-task__key">{{ t.issueKey }}</span>
                <span class="ov-task__title">{{ t.title }}</span>
                <span class="ov-task__due">{{ t.dueDate }}</span>
              </div>
            </div>
          </div>
          <!-- 燃尽图 -->
          <div class="ov-card">
            <h4>燃尽图</h4>
            <div v-if="!activeSprint" class="ov-card__empty">暂无活跃 Sprint</div>
            <div v-else ref="burndownChartRef" class="ov-card__chart" style="height: 220px"></div>
          </div>
          <!-- 最近活动 -->
          <div class="ov-card">
            <h4>最近活动</h4>
            <div v-if="recentActivities.length === 0" class="ov-card__empty">暂无</div>
            <div v-else class="ov-card__list">
              <div v-for="a in recentActivities" :key="a.id" class="ov-activity">
                <strong>{{ a.username }}</strong> {{ a.detail }}
                <div class="ov-activity__time">{{ a.createdAt }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="看板" name="board" lazy>
        <ProjectBoardView ref="boardRef" />
      </el-tab-pane>

      <el-tab-pane label="报告" name="reports" lazy>
        <ReportListView />
      </el-tab-pane>
    </el-tabs>

    <IssueDetailDialog
      v-model:visible="showCreateIssue"
      :project-id="projectId"
      :team-id="teamId"
      :columns="columns"
      @created="onIssueCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from "vue"
import { useRoute } from "vue-router"
import * as echarts from "echarts"
import { getBoard } from "@/api/board"
import { getProjectById } from "@/api/project"
import request from "@/api/request"
import { getSprints, getBurndown } from "@/api/sprint"
import SkeletonLoader from "@/components/common/SkeletonLoader.vue"
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue"
import ProjectBoardView from "./ProjectBoard.vue"
import ReportListView from "./ReportList.vue"

const route = useRoute()
const projectId = Number(route.params.id)
const projectName = ref("")
const teamId = ref(0)
const columns = ref<any[]>([])
const loading = ref(true)
const activeTab = ref("overview")
const showCreateIssue = ref(false)

const statusChartRef = ref<HTMLElement>()
const memberChartRef = ref<HTMLElement>()
const burndownChartRef = ref<HTMLElement>()
let charts: echarts.ECharts[] = []

const activeSprint = ref<any>(null)

const allIssues = computed(() => {
  const issues: any[] = []
  columns.value.forEach((c: any) => c.issues?.forEach((i: any) => issues.push(i)))
  return issues
})

const memberData = computed(() => {
  const map: Record<string, number> = {}
  allIssues.value.forEach((i: any) => {
    const name = i.assigneeName || "未指派"
    map[name] = (map[name] || 0) + 1
  })
  return Object.entries(map)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
})

const upcomingTasks = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const limit = new Date(today)
  limit.setDate(limit.getDate() + 3)
  return allIssues.value
    .filter((i: any) => i.dueDate && i.status !== "DONE" && new Date(i.dueDate) <= limit)
    .sort((a: any, b: any) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime())
    .slice(0, 8)
})

const recentActivities = ref<any[]>([])

function isOverdue(dateStr: string) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(dateStr) < today
}

function memberCompletionPct(name: string): number {
  const total = allIssues.value.filter((i: any) => (i.assigneeName || "未指派") === name).length
  if (total === 0) return 0
  const done = allIssues.value.filter((i: any) => (i.assigneeName || "未指派") === name && i.status === "DONE").length
  return Math.round((done / total) * 100)
}

async function fetchSprintAndBurndown() {
  try {
    const res = await getSprints(projectId)
    const sprints = res.data || []
    activeSprint.value = sprints.find((s: any) => s.isActive) || null
    if (activeSprint.value) {
      const burndownRes = await getBurndown(activeSprint.value.id)
      const data = burndownRes.data
      if (data && data.points && data.points.length > 0) {
        await nextTick()
        renderBurndownChart(data)
      }
    }
  } catch { /* no sprint data */ }
}

function renderBurndownChart(data: any) {
  if (!burndownChartRef.value) return
  const c = echarts.init(burndownChartRef.value)
  charts.push(c)
  const points = data.points || []
  c.setOption({
    tooltip: { trigger: "axis" },
    legend: {
      bottom: 0,
      data: ["理想剩余", "实际剩余"],
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 10 },
    },
    grid: { top: 10, right: 10, bottom: 30, left: 40 },
    xAxis: {
      type: "category",
      data: points.map((p: any) => p.date),
      axisLabel: { fontSize: 9, rotate: 30 },
    },
    yAxis: {
      type: "value",
      axisLabel: { fontSize: 10 },
      minInterval: 1,
    },
    series: [
      {
        name: "理想剩余",
        type: "line",
        data: points.map((p: any) => p.idealRemaining),
        smooth: true,
        lineStyle: { color: "#94a3b8", type: "dashed", width: 2 },
        itemStyle: { color: "#94a3b8" },
      },
      {
        name: "实际剩余",
        type: "line",
        data: points.map((p: any) => p.actualRemaining),
        smooth: true,
        lineStyle: { color: "#4f6ef6", width: 2 },
        itemStyle: { color: "#4f6ef6" },
        areaStyle: { color: "rgba(79,110,246,0.1)" },
      },
    ],
  })
}

async function fetchData() {
  loading.value = true
  try {
    const [boardRes, projRes, dashRes] = await Promise.all([
      getBoard(projectId),
      getProjectById(projectId),
      request.get("/dashboard"),
    ])
    columns.value = boardRes.data?.columns || []
    projectName.value = projRes.data?.name || ""
    teamId.value = projRes.data?.teamId || 0
    // Filter activities for this project
    const allActs = dashRes.data?.recentActivities || []
    recentActivities.value = allActs.slice(0, 5)
  } finally {
    loading.value = false
  }
  fetchSprintAndBurndown()
}

function renderCharts() {
  disposeCharts()
  if (!statusChartRef.value) return
  const c1 = echarts.init(statusChartRef.value)
  charts.push(c1)
  const statusCount: Record<string, number> = { TODO: 0, IN_PROGRESS: 0, DONE: 0 }
  allIssues.value.forEach((i: any) => { if (statusCount[i.status] !== undefined) statusCount[i.status]++ })
  c1.setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{
      type: "pie", radius: ["50%", "75%"], center: ["50%", "45%"],
      data: [
        { name: "待办", value: statusCount.TODO, itemStyle: { color: "#4f6ef6" } },
        { name: "进行中", value: statusCount.IN_PROGRESS, itemStyle: { color: "#f59e0b" } },
        { name: "已完成", value: statusCount.DONE, itemStyle: { color: "#10b981" } },
      ],
      label: { fontSize: 11 },
    }],
  })

  if (memberData.value.length > 0 && memberChartRef.value) {
    const c2 = echarts.init(memberChartRef.value)
    charts.push(c2)
    c2.setOption({
      tooltip: { trigger: "axis" },
      grid: { left: 60, right: 10, top: 8, bottom: 20 },
      xAxis: { type: "value", minInterval: 1, axisLabel: { fontSize: 10 } },
      yAxis: { type: "category", data: memberData.value.map((m: any) => m.name).reverse(), axisLabel: { fontSize: 10 } },
      series: [{
        type: "bar", data: memberData.value.map((m: any) => m.count).reverse(),
        itemStyle: { color: "#4f6ef6", borderRadius: [0, 4, 4, 0] },
        barWidth: 14,
      }],
    })
  }
}

function disposeCharts() {
  charts.forEach((c) => c.dispose())
  charts = []
}

function onTabChange(name: string | number) {
  if (name === "overview") {
    nextTick(renderCharts)
  }
}

function onIssueCreated() {
  fetchData()
}

onMounted(() => {
  fetchData().then(() => nextTick(renderCharts))
})
</script>

<style scoped lang="scss">
.overview {
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    > div { display: flex; align-items: center; gap: 4px; }
  }
  &__title { font-size: 20px; font-weight: 700; }
  &__loading { padding: 16px 0; }
  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
    margin-top: 12px;
  }
}

.ov-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 16px;
  h4 { font-size: 14px; font-weight: 600; margin-bottom: 10px; color: var(--text-primary); }
  &__chart { width: 100%; height: 210px; }
  &__empty { text-align: center; padding: 24px; color: var(--text-secondary); font-size: 13px; }
  &__list { max-height: 220px; overflow-y: auto; }
}

.ov-task {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  font-size: 13px;
  &:last-child { border-bottom: none; }
  &:hover { color: var(--color-primary); }
  &--overdue &__due { color: var(--color-danger); font-weight: 600; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; flex-shrink: 0; }
  &__title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &__due { font-size: 11px; color: var(--text-secondary); flex-shrink: 0; }
}

.ov-activity {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color-light);
  font-size: 13px;
  &:last-child { border-bottom: none; }
  &__time { font-size: 11px; color: var(--text-placeholder); margin-top: 2px; }
}

.ov-member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  &__name { flex-shrink: 0; width: 60px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &__count { color: var(--text-secondary); flex-shrink: 0; }
}
</style>
