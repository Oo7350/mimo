<template>
  <div class="overview">
    <!-- 项目头部 -->
    <div class="overview__hero">
      <div class="overview__hero-left">
        <el-button @click="$router.push('/')" text class="overview__back">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="overview__project-avatar" :style="{ background: avatarGradient(projectName, 'project') }">
          {{ projectName.charAt(0).toUpperCase() }}
        </div>
        <div>
          <h2 class="overview__title">{{ projectName }}</h2>
          <p class="overview__subtitle">项目概览 · 数据仪表盘</p>
        </div>
      </div>
      <div class="overview__hero-actions">
        <el-button @click="showCreateCalendarEvent = true">
          <el-icon><Calendar /></el-icon>新建日程
        </el-button>
        <el-button id="create-task-btn" type="primary" @click="showCreateIssue = true">
          <el-icon><Plus /></el-icon>新建工作项
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="overview__tabs" @tab-change="onTabChange">
      <el-tab-pane label="概览" name="overview">
        <div v-if="loading" class="overview__loading">
          <SkeletonLoader variant="card" :count="4" />
        </div>
        <template v-else>
          <!-- 统计摘要 -->
          <div class="overview__stats">
            <div class="stat-pill">
              <div class="stat-pill__icon" style="background: rgba(99,102,241,0.12); color: var(--color-primary)">
                <el-icon><Odometer /></el-icon>
              </div>
              <div>
                <div class="stat-pill__value" style="color: var(--color-primary)">{{ allIssues.length }}</div>
                <div class="stat-pill__label">总任务</div>
              </div>
            </div>
            <div class="stat-pill">
              <div class="stat-pill__icon" style="background: rgba(245,158,11,0.12); color: var(--color-warning)">
                <el-icon><Loading /></el-icon>
              </div>
              <div>
                <div class="stat-pill__value" style="color: var(--color-warning)">{{ statusCount.IN_PROGRESS }}</div>
                <div class="stat-pill__label">进行中</div>
              </div>
            </div>
            <div class="stat-pill">
              <div class="stat-pill__icon" style="background: rgba(16,185,129,0.12); color: var(--color-success)">
                <el-icon><CircleCheck /></el-icon>
              </div>
              <div>
                <div class="stat-pill__value" style="color: var(--color-success)">{{ statusCount.DONE }}</div>
                <div class="stat-pill__label">已完成</div>
              </div>
            </div>
            <div class="stat-pill">
              <div class="stat-pill__icon" style="background: rgba(139,92,246,0.12); color: var(--color-accent)">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div>
                <div class="stat-pill__value" style="color: var(--color-accent)">{{ completionRate }}%</div>
                <div class="stat-pill__label">完成率</div>
              </div>
            </div>
          </div>

          <div class="overview__grid">
            <!-- 任务状态分布 -->
            <div class="ov-card">
              <div class="ov-card__header">
                <div class="ov-card__header-icon" style="background: rgba(99,102,241,0.12); color: var(--color-primary)">
                  <el-icon><PieChart /></el-icon>
                </div>
                <span>任务状态分布</span>
              </div>
              <div ref="statusChartRef" class="ov-card__chart"></div>
            </div>

            <!-- 成员工作量 -->
            <div class="ov-card">
              <div class="ov-card__header">
                <div class="ov-card__header-icon" style="background: rgba(245,158,11,0.12); color: var(--color-warning)">
                  <el-icon><User /></el-icon>
                </div>
                <span>成员工作量</span>
              </div>
              <div v-if="memberData.length === 0" class="ov-card__empty">暂无数据</div>
              <div v-else>
                <div ref="memberChartRef" class="ov-card__chart ov-card__chart--sm"></div>
                <div class="ov-member-list">
                  <div v-for="m in memberData" :key="m.name" class="ov-member-row">
                    <div class="ov-member-row__avatar" :style="{ background: avatarGradient(m.name, 'user') }">
                      {{ m.name.charAt(0) }}
                    </div>
                    <span class="ov-member-row__name">{{ m.name }}</span>
                    <span class="ov-member-row__count">{{ m.count }} 个</span>
                    <el-progress
                      :percentage="memberCompletionPct(m.name)"
                      :color="progressColor(memberCompletionPct(m.name))"
                      :stroke-width="6"
                      style="flex: 1"
                    />
                  </div>
                </div>
              </div>
            </div>

            <!-- 即将到期 -->
            <div class="ov-card">
              <div class="ov-card__header">
                <div class="ov-card__header-icon" style="background: rgba(239,68,68,0.12); color: var(--color-danger)">
                  <el-icon><AlarmClock /></el-icon>
                </div>
                <span>即将到期</span>
                <el-tag v-if="upcomingTasks.length" size="small" type="danger" effect="plain" round>
                  {{ upcomingTasks.length }}
                </el-tag>
              </div>
              <div v-if="upcomingTasks.length === 0" class="ov-card__empty">暂无近期截止的任务 🎉</div>
              <div v-else class="ov-card__list">
                <div
                  v-for="t in upcomingTasks"
                  :key="t.id"
                  :class="['ov-task', { 'ov-task--overdue': isOverdue(t.dueDate) }]"
                  @click="$router.push(`/projects/${projectId}/board?issue=${t.id}`)"
                >
                  <span class="ov-task__type">{{ typeIcon(t.type) }}</span>
                  <span class="ov-task__key">{{ t.issueKey }}</span>
                  <span class="ov-task__title">{{ t.title }}</span>
                  <span class="ov-task__due">{{ t.dueDate }}</span>
                </div>
              </div>
            </div>

            <!-- 项目进度概览（替代原 Sprint 燃尽图） -->
            <div class="ov-card">
              <div class="ov-card__header">
                <div class="ov-card__header-icon" style="background: rgba(6,182,212,0.12); color: #06b6d4">
                  <el-icon><DataAnalysis /></el-icon>
                </div>
                <span>项目进度</span>
              </div>
              <div v-if="stats" ref="progressChartRef" class="ov-card__chart"></div>
              <div v-else class="ov-card__empty">暂无任务数据</div>
            </div>

            <!-- 最近活动 -->
            <div class="ov-card ov-card--wide">
              <div class="ov-card__header">
                <div class="ov-card__header-icon" style="background: rgba(148,163,184,0.15); color: var(--text-secondary)">
                  <el-icon><Clock /></el-icon>
                </div>
                <span>最近活动</span>
              </div>
              <div v-if="recentActivities.length === 0" class="ov-card__empty">暂无动态</div>
              <div v-else class="ov-activity-list">
                <div v-for="a in recentActivities" :key="a.id" class="ov-activity-item">
                  <div class="ov-activity-item__avatar" :style="{ background: avatarGradient(a.username, 'user') }">
                    {{ a.username.charAt(0).toUpperCase() }}
                  </div>
                  <div class="ov-activity-item__content">
                    <div class="ov-activity-item__text">
                      <strong>{{ a.username }}</strong> {{ a.detail }}
                    </div>
                    <div class="ov-activity-item__time">{{ a.createdAt }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
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

    <EventForm
      v-model:visible="showCreateCalendarEvent"
      :event="null"
      :default-date="new Date()"
      :teams="projectTeams"
      :projects="[{ id: projectId, name: projectName }]"
      @saved="onCalendarEventSaved"
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
import { avatarGradient } from "@/utils/color"
import SkeletonLoader from "@/components/common/SkeletonLoader.vue"
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue"
import ProjectBoardView from "./ProjectBoard.vue"
import ReportListView from "./ReportList.vue"
import EventForm from "./EventForm.vue"
import { getMyTeams } from "@/api/team"
import { ElMessage } from "element-plus"

const route = useRoute()
const projectId = Number(route.params.id)
const projectName = ref("")
const teamId = ref(0)
const columns = ref<any[]>([])
const loading = ref(true)
const activeTab = ref("overview")
const showCreateIssue = ref(false)
const showCreateCalendarEvent = ref(false)
const projectTeams = ref<{ id: number; name: string }[]>([])

const statusChartRef = ref<HTMLElement>()
const memberChartRef = ref<HTMLElement>()
const progressChartRef = ref<HTMLElement>()  // 替代原 burndownChartRef
let charts: echarts.ECharts[] = []

const recentActivities = ref<any[]>([])
const stats = ref<any>(null)  // 项目统计（替代 activeSprint）

const TYPE_ICONS: Record<string, string> = { STORY: '📖', TASK: '✅', BUG: '🐛' }
function typeIcon(t: string) { return TYPE_ICONS[t] || '📋' }

const allIssues = computed(() => {
  const issues: any[] = []
  columns.value.forEach((c: any) => c.issues?.forEach((i: any) => issues.push(i)))
  return issues
})

const statusCount = computed(() => {
  const count = { TODO: 0, IN_PROGRESS: 0, DONE: 0 }
  allIssues.value.forEach((i: any) => {
    if (count[i.status as keyof typeof count] !== undefined) count[i.status as keyof typeof count]++
  })
  return count
})

const completionRate = computed(() => {
  const total = allIssues.value.length
  if (total === 0) return 0
  return Math.round((statusCount.value.DONE / total) * 100)
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

function progressColor(pct: number) {
  if (pct >= 60) return '#10b981'
  if (pct >= 30) return '#f59e0b'
  return '#ef4444'
}

async function fetchProjectStats() {
  try {
    // 使用通用项目统计接口（不依赖 Sprint）
    const res = await request.get(`/api/reports/stats/${projectId}`)
    stats.value = res.data
    await nextTick()
    renderProgressChart()
  } catch { /* no data */ }
}

/** 项目进度图 — 替代原 Sprint 燃尽图 */
function renderProgressChart() {
  if (!progressChartRef.value || !stats.value) return
  const c = echarts.init(progressChartRef.value)
  charts.push(c)
  const so = stats.value.statusOverview || {}
  const total = so.totalCount || 1
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center', itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: false,
      label: {
        show: true,
        position: 'center',
        formatter: () => `{total|${total}}\n{label|任务总数}`,
        rich: { total: { fontSize: 26, fontWeight: 'bold' }, label: { fontSize: 11, color: '#94a3b8', padding: [6,0,0,0] } },
      },
      data: [
        { name: '待办', value: so.todoCount || 0, itemStyle: { color: '#94a3b8' } },
        { name: '进行中', value: so.inProgressCount || 0, itemStyle: { color: '#f59e0b' } },
        { name: '已完成', value: so.doneCount || 0, itemStyle: { color: '#10b981' } },
      ],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.15)' } },
    }],
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
    recentActivities.value = (dashRes.data?.recentActivities || []).slice(0, 6)
  } finally {
    loading.value = false
  }
  fetchProjectStats() // 替代原 fetchSprintAndBurndown
}

function renderCharts() {
  disposeCharts()
  if (!statusChartRef.value) return
  const c1 = echarts.init(statusChartRef.value)
  charts.push(c1)
  c1.setOption({
    tooltip: { trigger: "item", formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{
      type: "pie",
      radius: ["48%", "72%"],
      center: ["50%", "44%"],
      padAngle: 3,
      itemStyle: { borderRadius: 6 },
      data: [
        { name: "待办", value: statusCount.value.TODO, itemStyle: { color: "#6366f1" } },
        { name: "进行中", value: statusCount.value.IN_PROGRESS, itemStyle: { color: "#f59e0b" } },
        { name: "已完成", value: statusCount.value.DONE, itemStyle: { color: "#10b981" } },
      ],
      label: { fontSize: 11, formatter: '{b}\n{c}' },
      emphasis: { scaleSize: 8 },
    }],
  })

  if (memberData.value.length > 0 && memberChartRef.value) {
    const c2 = echarts.init(memberChartRef.value)
    charts.push(c2)
    c2.setOption({
      tooltip: { trigger: "axis" },
      grid: { left: 70, right: 16, top: 8, bottom: 20 },
      xAxis: { type: "value", minInterval: 1, axisLabel: { fontSize: 10, color: '#94a3b8' }, splitLine: { lineStyle: { color: '#f1f5f9' } } },
      yAxis: {
        type: "category",
        data: memberData.value.map((m: any) => m.name).reverse(),
        axisLabel: { fontSize: 11, color: '#64748b' },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [{
        type: "bar",
        data: memberData.value.map((m: any) => m.count).reverse(),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#6366f1' },
            { offset: 1, color: '#818cf8' },
          ]),
          borderRadius: [0, 6, 6, 0],
        },
        barWidth: 16,
      }],
    })
  }
}

function disposeCharts() {
  charts.forEach((c) => c.dispose())
  charts = []
}

function onTabChange(name: string | number) {
  if (name === "overview") nextTick(renderCharts)
}

function onIssueCreated() { fetchData().then(() => nextTick(renderCharts)) }
function onCalendarEventSaved() { ElMessage.success('日程已创建，可在日历中查看') }

onMounted(() => {
  fetchData().then(() => nextTick(renderCharts))
  getMyTeams().then(res => { projectTeams.value = res.data || [] }).catch(() => {})
})
</script>

<style scoped lang="scss">
.overview {
  &__hero {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    gap: 16px;
    flex-wrap: wrap;
  }

  &__hero-left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  &__hero-actions {
    display: flex;
    gap: 8px;
  }

  &__back { color: var(--text-secondary); }

  &__project-avatar {
    width: 48px;
    height: 48px;
    border-radius: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 800;
    font-size: 20px;
    flex-shrink: 0;
  }

  &__title {
    font-size: 22px;
    font-weight: 800;
    letter-spacing: -0.3px;
    color: var(--text-primary);
  }

  &__subtitle {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 2px;
  }

  &__tabs { margin-top: 4px; }

  &__loading { padding: 16px 0; }

  &__stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 14px;
    margin-bottom: 16px;
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
  }
}

.ov-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 16px 18px;
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--transition-normal);

  &:hover { box-shadow: var(--shadow-md); }

  &--wide { grid-column: 1 / -1; }

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 12px;
  }

  &__header-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
  }

  &__badge {
    margin-left: auto;
    font-size: 11px;
    font-weight: 600;
    color: var(--color-primary);
    background: rgba(99,102,241,0.1);
    padding: 3px 10px;
    border-radius: 8px;
  }

  &__chart {
    width: 100%;
    height: 220px;

    &--sm { height: 140px; }
  }

  &__empty {
    text-align: center;
    padding: 32px 16px;
    color: var(--text-secondary);
    font-size: 13px;
  }

  &__list { max-height: 240px; overflow-y: auto; }
}

.ov-task {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  font-size: 13px;
  transition: background var(--transition-fast);

  &:last-child { border-bottom: none; }
  &:hover { color: var(--color-primary); }

  &--overdue .ov-task__due { color: var(--color-danger); font-weight: 700; }

  &__type { font-size: 14px; flex-shrink: 0; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; flex-shrink: 0; }
  &__title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &__due { font-size: 11px; color: var(--text-secondary); flex-shrink: 0; }
}

.ov-member-list {
  margin-top: 8px;
  max-height: 140px;
  overflow-y: auto;
}

.ov-member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;

  &__avatar {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    flex-shrink: 0;
  }

  &__name {
    width: 56px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  &__count {
    color: var(--text-secondary);
    flex-shrink: 0;
    width: 36px;
  }
}

.ov-activity-list { max-height: 260px; overflow-y: auto; }

.ov-activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);

  &:last-child { border-bottom: none; }

  &__avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;
  }

  &__text {
    font-size: 13px;
    color: var(--text-regular);
    line-height: 1.5;
    strong { color: var(--text-primary); }
  }

  &__time {
    font-size: 11px;
    color: var(--text-placeholder);
    margin-top: 3px;
  }
}

@media (max-width: 1024px) {
  .overview__stats { grid-template-columns: repeat(2, 1fr); }
  .overview__grid { grid-template-columns: 1fr; }
}
</style>
