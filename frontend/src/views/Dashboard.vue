<template>
  <div class="dashboard">
    <!-- Hero 欢迎区 -->
    <div class="dashboard__hero">
      <div class="dashboard__hero-text">
        <h1>{{ greeting }}，{{ userStore.username }}</h1>
        <p>
          <template v-if="overdueCount > 0">
            你有 <strong class="text-danger">{{ overdueCount }}</strong> 项任务已逾期，
          </template>
          <template v-if="myTasks.length">
            还有 <strong>{{ myTasks.length }}</strong> 项待办等待处理
          </template>
          <template v-else-if="!loading">
            所有任务都已处理完毕，干得漂亮！
          </template>
        </p>
      </div>
      <div class="dashboard__hero-actions">
        <el-button type="primary" @click="$router.push('/projects')">
          <el-icon><Grid /></el-icon> 进入项目
        </el-button>
        <el-button @click="$router.push('/teams')">
          <el-icon><Plus /></el-icon> 创建团队
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="dashboard__stats">
      <StatCard
        title="总任务"
        :value="dashboard?.totalIssues || 0"
        accent-color="var(--color-primary)"
        icon-bg="var(--gradient-primary)"
        icon="Odometer"
        :loading="loading"
      />
      <StatCard
        title="进行中"
        :value="dashboard?.inProgressIssues || 0"
        accent-color="var(--color-warning)"
        icon-bg="linear-gradient(135deg, #f59e0b, #fbbf24)"
        icon="Loading"
        :loading="loading"
      />
      <StatCard
        title="已完成"
        :value="dashboard?.doneIssues || 0"
        accent-color="var(--color-success)"
        icon-bg="linear-gradient(135deg, #10b981, #34d399)"
        icon="CircleCheck"
        :loading="loading"
      />
      <StatCard
        title="完成率"
        :value="completionRate"
        suffix="%"
        accent-color="var(--color-accent)"
        icon-bg="linear-gradient(135deg, #8b5cf6, #a78bfa)"
        icon="TrendCharts"
        :loading="loading"
      />
    </div>

    <!-- 快捷操作 -->
    <div class="dashboard__quick-actions">
      <div
        v-for="qa in quickActions"
        :key="qa.label"
        class="dashboard__qa-item"
        @click="$router.push(qa.route)"
      >
        <div class="dashboard__qa-icon" :style="{ background: qa.bg }">
          <el-icon><component :is="qa.icon" /></el-icon>
        </div>
        <span>{{ qa.label }}</span>
      </div>
    </div>

    <!-- 活跃 Sprint 提示 -->
    <div v-if="dashboard?.activeSprints?.length" class="dashboard__sprint-banner">
      <el-icon><DataAnalysis /></el-icon>
      <span>
        当前有 <strong>{{ dashboard.activeSprints.length }}</strong> 个活跃 Sprint：
        {{ dashboard.activeSprints.map(s => s.name).join('、') }}
      </span>
      <el-button
        size="small"
        text
        type="primary"
        @click="$router.push(`/projects/${dashboard.activeSprints[0].projectId}/board`)"
      >
        查看看板 →
      </el-button>
    </div>

    <div class="dashboard__panels">
      <!-- 我的项目 -->
      <div class="dashboard__panel">
        <div class="panel-header">
          <div class="panel-header__left">
            <div class="panel-header__icon" style="background: rgba(99,102,241,0.12); color: var(--color-primary)">
              <el-icon><Folder /></el-icon>
            </div>
            <span>我的项目</span>
          </div>
          <el-button size="small" text type="primary" @click="$router.push('/projects')">
            查看全部
          </el-button>
        </div>
        <div v-if="loading" class="panel-body">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty v-else-if="!dashboard?.myProjects?.length" description="暂无项目，去团队创建一个吧" :image-size="64">
          <el-button type="primary" size="small" @click="$router.push('/teams')">前往团队</el-button>
        </el-empty>
        <div v-else class="panel-body">
          <div
            v-for="p in dashboard.myProjects"
            :key="p.id"
            class="project-item"
            @click="$router.push(`/projects/${p.id}`)"
          >
            <div class="project-item__avatar" :style="{ background: avatarGradient(p.name) }">
              {{ p.name.charAt(0).toUpperCase() }}
            </div>
            <div class="project-item__info">
              <div class="project-item__name">{{ p.name }}</div>
              <div class="project-item__meta">
                <el-tag size="small" :type="p.template === 'SCRUM' ? '' : 'warning'" effect="plain">
                  {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}
                </el-tag>
                <span class="project-item__key">{{ p.key }}</span>
                <span>{{ p.teamName }}</span>
              </div>
            </div>
            <el-icon class="project-item__arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- 我的待办 -->
      <div class="dashboard__panel">
        <div class="panel-header">
          <div class="panel-header__left">
            <div class="panel-header__icon" style="background: rgba(245,158,11,0.12); color: var(--color-warning)">
              <el-icon><List /></el-icon>
            </div>
            <span>我的待办</span>
            <el-tag v-if="myTasks.length" size="small" round type="warning">
              {{ myTasks.length }}
            </el-tag>
          </div>
        </div>
        <div v-if="tasksLoading" class="panel-body">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty v-else-if="!myTasks.length" description="暂无待办，享受轻松时刻" :image-size="64" />
        <TransitionGroup v-else name="task-list" tag="div" class="panel-body">
          <div
            v-for="task in myTasks"
            :key="task.id"
            class="task-item"
            @click="openTask(task)"
          >
            <div class="task-item__left">
              <span class="task-item__type-badge" :style="{ background: typeColor(task.type) }">
                {{ typeIcon(task.type) }}
              </span>
              <div>
                <div class="task-item__title">{{ task.title }}</div>
                <div class="task-item__sub">
                  <span class="task-item__key">{{ task.issueKey }}</span>
                  <span v-if="task.projectId" class="task-item__project">{{ getProjectName(task.projectId) }}</span>
                  <span
                    v-if="task.dueDate"
                    :class="['task-item__due', { 'task-item__due--overdue': isOverdue(task.dueDate) }]"
                  >
                    {{ isOverdue(task.dueDate) ? '⚠' : '📅' }} {{ task.dueDate }}
                  </span>
                </div>
              </div>
            </div>
            <div class="task-item__right">
              <el-tag :type="PRIORITY_COLOR[task.priority]" size="small" effect="plain">
                {{ PRIORITY_LABEL[task.priority] }}
              </el-tag>
              <el-button
                size="small" circle class="task-item__done"
                @click.stop="completeTask(task)"
              >
                <el-icon><Check /></el-icon>
              </el-button>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </div>

    <!-- 近期动态 -->
    <div class="dashboard__panel dashboard__panel--full">
      <div class="panel-header">
        <div class="panel-header__left">
          <div class="panel-header__icon" style="background: rgba(148,163,184,0.15); color: var(--text-secondary)">
            <el-icon><Clock /></el-icon>
          </div>
          <span>近期动态</span>
        </div>
      </div>
      <div v-if="loading" class="panel-body">
        <SkeletonLoader variant="list-item" :count="4" />
      </div>
      <el-empty v-else-if="!dashboard?.recentActivities?.length" description="暂无动态" :image-size="64" />
      <div v-else class="panel-body activity-list">
        <div v-for="act in dashboard.recentActivities" :key="act.id" class="activity-item">
          <div class="activity-item__avatar" :style="{ background: avatarGradient(act.username) }">
            {{ act.username.charAt(0).toUpperCase() }}
          </div>
          <div class="activity-item__content">
            <div class="activity-item__text">
              <strong>{{ act.username }}</strong> {{ act.detail }}
            </div>
            <div class="activity-item__time">{{ act.createdAt }}</div>
          </div>
          <div class="activity-item__badge" :style="{ background: actionColor(act.action) }">
            {{ actionLabel(act.action) }}
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import { useUserStore } from "@/store/user"
import request from "@/api/request"
import { queryIssues, updateIssue } from "@/api/issue"
import { ElMessage } from "element-plus"
import { PRIORITY_LABEL, PRIORITY_COLOR } from "@/utils/constants"
import {
  Grid, Plus, Folder, List, Odometer, Loading,
  CircleCheck, TrendCharts, DataAnalysis, Document,
  UserFilled, ArrowRight, Check, Clock, Medal
} from "@element-plus/icons-vue"
import { avatarGradient } from "@/utils/color"
import type { DashboardData, IssueCard, IssueType } from "@/types"
import StatCard from "@/components/common/StatCard.vue"
import SkeletonLoader from "@/components/common/SkeletonLoader.vue"
import UserBadge from "@/components/common/UserBadge.vue"

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<DashboardData | null>(null)
const myTasks = ref<IssueCard[]>([])
const loading = ref(true)
const tasksLoading = ref(true)


const TYPE_ICONS: Record<IssueType, string> = { STORY: '📖', TASK: '✅', BUG: '🐛' }
const TYPE_COLORS: Record<IssueType, string> = {
  STORY: 'rgba(16,185,129,0.15)',
  TASK: 'rgba(99,102,241,0.15)',
  BUG: 'rgba(239,68,68,0.15)',
}

// 快捷操作入口
const quickActions = [
  { label: '新建任务', icon: 'Plus', route: '/projects', bg: 'linear-gradient(135deg, #6366f1, #818cf8)' },
  { label: '看板视图', icon: 'Grid', route: '/projects', bg: 'linear-gradient(135deg, #10b981, #34d399)' },
  { label: 'Sprint', icon: 'DataAnalysis', route: '/projects', bg: 'linear-gradient(135deg, #f59e0b, #fbbf24)' },
  { label: '报告', icon: 'Document', route: '/projects', bg: 'linear-gradient(135deg, #8b5cf6, #a78bfa)' },
  { label: '团队管理', icon: 'UserFilled', route: '/teams', bg: 'linear-gradient(135deg, #06b6d4, #22d3ee)' },
]

function typeIcon(t: string) { return TYPE_ICONS[t as IssueType] || '📋' }
function typeColor(t: string) { return TYPE_COLORS[t as IssueType] || 'rgba(99,102,241,0.15)' }

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const completionRate = computed(() => {
  const total = dashboard.value?.totalIssues || 0
  const done = dashboard.value?.doneIssues || 0
  if (total === 0) return 0
  return Math.round((done / total) * 100)
})

const overdueCount = computed(() =>
  myTasks.value.filter(t => t.dueDate && isOverdue(t.dueDate)).length
)

function getProjectName(projectId: number): string {
  return dashboard.value?.myProjects?.find(p => p.id === projectId)?.name || ''
}

function isOverdue(dateStr: string): boolean {
  if (!dateStr) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(dateStr) < today
}

const ACTION_MAP: Record<string, { label: string; color: string }> = {
  CREATE: { label: '创建', color: 'rgba(16,185,129,0.12)' },
  UPDATE: { label: '更新', color: 'rgba(99,102,241,0.12)' },
  MOVE: { label: '移动', color: 'rgba(245,158,11,0.12)' },
  DELETE: { label: '删除', color: 'rgba(239,68,68,0.12)' },
  COMMENT: { label: '评论', color: 'rgba(139,92,246,0.12)' },
  ASSIGN: { label: '指派', color: 'rgba(6,182,212,0.12)' },
}

function actionLabel(action: string) {
  return ACTION_MAP[action]?.label || action
}
function actionColor(action: string) {
  return ACTION_MAP[action]?.color || 'rgba(148,163,184,0.12)'
}

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await request.get("/dashboard")
    dashboard.value = res.data
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

async function fetchMyTasks() {
  tasksLoading.value = true
  try {
    const uid = userStore.userInfo?.id
    if (!uid) return
    const res = await queryIssues({ assigneeId: uid, status: "TODO", page: 1, size: 50 })
    myTasks.value = (res.data || []).slice(0, 20)
  } catch { myTasks.value = [] }
  finally { tasksLoading.value = false }
}

async function completeTask(task: IssueCard) {
  try {
    await updateIssue({ id: task.id, status: "DONE" as any })
    ElMessage.success(`${task.issueKey} 已完成`)
    myTasks.value = myTasks.value.filter(t => t.id !== task.id)
    if (dashboard.value) {
      dashboard.value.doneIssues = (dashboard.value.doneIssues || 0) + 1
    }
  } catch { /* handled */ }
}

function openTask(task: IssueCard) {
  router.push(`/projects/${task.projectId || 0}/board?issue=${task.id}`)
}

onMounted(() => { fetchDashboard(); fetchMyTasks() })
</script>

<style scoped lang="scss">
.dashboard {
  max-width: 1200px;
  margin: 0 auto;

  &__hero {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 28px 32px;
    margin-bottom: 24px;
    background: var(--gradient-hero);
    border-radius: var(--border-radius-xl);
    color: #fff;
    box-shadow: 0 8px 32px rgba(99, 102, 241, 0.3);
    gap: 20px;
    flex-wrap: wrap;

    h1 {
      font-size: 26px;
      font-weight: 800;
      letter-spacing: -0.5px;
      margin-bottom: 6px;
    }

    p {
      font-size: 14px;
      opacity: 0.85;

      strong { opacity: 1; font-weight: 700; }
      .text-danger { color: #fca5a5; }
    }
  }

  &__hero-actions {
    display: flex;
    gap: 10px;
    flex-shrink: 0;

    .el-button--primary {
      background: rgba(255,255,255,0.95);
      color: var(--color-primary-dark);
      border: none;
      font-weight: 600;
      &:hover { background: #fff; }
    }

    .el-button:not(.el-button--primary) {
      background: rgba(255,255,255,0.15);
      color: #fff;
      border: 1px solid rgba(255,255,255,0.3);
      &:hover { background: rgba(255,255,255,0.25); }
    }
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 20px;
  }

  &__quick-actions {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-lg);
    box-shadow: var(--shadow-sm);
    overflow-x: auto;
  }

  &__qa-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border-radius: var(--border-radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    white-space: nowrap;
    font-size: 13px;
    font-weight: 500;
    color: var(--text-primary);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      .dashboard__qa-icon { transform: scale(1.1); }
    }
  }

  &__qa-icon {
    width: 28px;
    height: 28px;
    border-radius: 7px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 14px;
    transition: transform var(--transition-fast);
  }

  &__sprint-banner {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 18px;
    margin-bottom: 20px;
    background: rgba(99, 102, 241, 0.06);
    border: 1px solid rgba(99, 102, 241, 0.15);
    border-radius: var(--border-radius-md);
    font-size: 13px;
    color: var(--text-regular);

    .el-icon { color: var(--color-primary); font-size: 18px; }
    strong { color: var(--color-primary); }
    .el-button { margin-left: auto; }
  }

  &__panels {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }

  &__panel {
    background: var(--bg-card);
    border-radius: var(--border-radius-lg);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    overflow: hidden;

    &--full { margin-bottom: 0; }
  }
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: 15px;

  &__left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
  }
}

.panel-body { padding: 8px 20px 16px; }

.project-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: all var(--transition-fast);

  &:last-child { border-bottom: none; }
  &:hover {
    background: var(--bg-hover);
    margin: 0 -20px;
    padding-left: 20px;
    padding-right: 20px;
    border-radius: var(--border-radius-sm);
  }

  &__avatar {
    width: 40px;
    height: 40px;
    border-radius: 11px;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 15px;
    flex-shrink: 0;
  }

  &__info { flex: 1; min-width: 0; }
  &__name { font-weight: 600; font-size: 14px; color: var(--text-primary); }
  &__meta {
    display: flex; align-items: center; gap: 8px;
    font-size: 12px; color: var(--text-secondary); margin-top: 4px;
  }
  &__key {
    font-family: monospace;
    background: var(--bg-page);
    padding: 1px 6px;
    border-radius: 4px;
  }
  &__arrow { color: var(--text-placeholder); flex-shrink: 0; transition: transform var(--transition-fast); }
  &:hover &__arrow { transform: translateX(3px); color: var(--color-primary); }
}

.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: all var(--transition-fast);

  &:last-child { border-bottom: none; }
  &:hover {
    background: var(--bg-hover);
    margin: 0 -20px;
    padding-left: 20px;
    padding-right: 20px;
    border-radius: var(--border-radius-sm);
  }

  &__left { display: flex; align-items: center; gap: 12px; }
  &__type-badge {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    flex-shrink: 0;
  }
  &__title { font-size: 14px; font-weight: 500; color: var(--text-primary); }
  &__sub { display: flex; align-items: center; gap: 10px; margin-top: 3px; flex-wrap: wrap; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; }
  &__project {
    font-size: 11px; color: var(--color-primary);
    background: rgba(99,102,241,0.08); padding: 1px 6px; border-radius: 4px;
  }
  &__due { font-size: 11px; color: var(--text-secondary); }
  &__due--overdue { color: var(--color-danger); font-weight: 600; }
  &__right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
  &__done {
    opacity: 0;
    transition: all var(--transition-fast);
    .task-item:hover & { opacity: 1; }
    &:hover { transform: scale(1.15); color: var(--color-success) !important; }
  }
}

.activity-list { padding: 12px 20px 16px; }

.activity-item {
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

  &__content { flex: 1; min-width: 0; }
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
  &__badge {
    font-size: 11px;
    font-weight: 600;
    padding: 3px 8px;
    border-radius: 6px;
    color: var(--text-regular);
    flex-shrink: 0;
    margin-top: 2px;
  }
}

.task-list-enter-active,
.task-list-leave-active { transition: all 0.3s ease; }
.task-list-enter-from { opacity: 0; transform: translateX(16px); }
.task-list-leave-to { opacity: 0; transform: translateX(-16px); }

@media (max-width: 1024px) {
  .dashboard__stats { grid-template-columns: repeat(2, 1fr); }
  .dashboard__panels { grid-template-columns: 1fr; }
}

// ========== 等级管理表格 ==========
.level-legend {
  display: flex;
  align-items: center;
  gap: 16px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: var(--text-secondary);
  }

  .legend-dot {
    width: 12px;
    height: 12px;
    border-radius: 3px;
    flex-shrink: 0;
  }
}

.level-table-wrap { padding: 4px 20px 16px; }

.level-table {
  width: 100%;
  border-collapse: collapse;

  th, td {
    padding: 12px 16px;
    text-align: left;
    border-bottom: 1px solid var(--border-color-light);
  }

  th {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    background: var(--bg-page);
  }

  tbody tr:hover {
    background: var(--bg-hover);
  }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .user-avatar {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 700;
    font-size: 13px;
    flex-shrink: 0;
  }

  span {
    font-weight: 500;
    color: var(--text-primary);
  }
}
</style>
