<template>
  <div class="dashboard">
    <Aurora
      :opacity="0.32"
      speed="slow"
      :show-noise="true"
    />
    <!-- Hero — 干净文字，无渐变无光球 -->
    <div class="dash-hero">
      <div class="dash-hero__text">
        <h1>
          <ShinyText
            :text="`${greeting}，${userStore.username}`"
            color="#0f172a"
            shine-color="#7c3aed"
            :speed="3.2"
          />
        </h1>
        <p>
          <template v-if="overdueCount > 0">
            你有 <strong class="text-danger">{{ overdueCount }}</strong> 项任务已逾期，
          </template>
          <template v-if="myTasks.length">
            还有 <strong>{{ myTasks.length }}</strong> 项待办等待处理
          </template>
          <template v-else-if="!loading && dashboard?.myProjects?.length">
            当前没有待办任务
          </template>
          <template v-else-if="!loading">
            加入或创建团队，开始第一个项目
          </template>
        </p>
      </div>
      <div class="dash-hero__actions">
        <el-button type="primary" @click="$router.push('/projects')">
          进入项目
        </el-button>
        <el-button @click="$router.push('/teams')">创建团队</el-button>
      </div>
    </div>

    <!-- 统计卡片 — 纯净数字，无彩色图标 -->
    <div class="dash-stats">
      <div class="dash-stat">
        <span class="dash-stat__value">{{ dashboard?.totalIssues || 0 }}</span>
        <span class="dash-stat__label">总任务</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">{{ dashboard?.inProgressIssues || 0 }}</span>
        <span class="dash-stat__label">进行中</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">{{ dashboard?.doneIssues || 0 }}</span>
        <span class="dash-stat__label">已完成</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">{{ completionRate }}%</span>
        <span class="dash-stat__label">完成率</span>
      </div>
    </div>

    <!-- 快捷操作 — 纯文字按钮，无边框图标 -->
    <div class="dash-shortcuts">
      <button
        v-for="qa in quickActions"
        :key="qa.label"
        class="dash-sc"
        @click="$router.push(qa.route)"
      >
        <el-icon><component :is="qa.icon" /></el-icon>
        {{ qa.label }}
      </button>
    </div>

    <!-- Sprint 提示 -->
    <div v-if="dashboard?.activeSprints?.length" class="dash-banner">
      当前有 {{ dashboard.activeSprints.length }} 个活跃 Sprint：
      {{ dashboard.activeSprints.map(s => s.name).join('、') }}
      <el-button size="small" type="primary" link
        @click="$router.push(`/projects/${dashboard.activeSprints[0].projectId}/board`)"
      >查看看板 &rarr;</el-button>
    </div>

    <!-- 面板区 -->
    <div class="dash-grid">
      <!-- 我的项目 -->
      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>我的项目</span>
          <el-button size="small" type="primary" link @click="$router.push('/projects')">查看全部</el-button>
        </header>
        <div v-if="loading" class="dash-panel__bd"><SkeletonLoader variant="list-item" :count="3" /></div>
        <el-empty v-else-if="!dashboard?.myProjects?.length" description="暂无项目" :image-size="56">
          <el-button type="primary" size="small" @click="$router.push('/teams')">前往团队</el-button>
        </el-empty>
        <div v-else class="dash-panel__bd">
          <div
            v-for="p in dashboard.myProjects"
            :key="p.id"
            class="dash-row"
            @click="$router.push(`/projects/${p.id}`)"
          >
            <span class="dash-row__icon">{{ p.name.charAt(0).toUpperCase() }}</span>
            <div class="dash-row__info">
              <strong>{{ p.name }}</strong>
              <span>{{ p.key }} · {{ p.teamName }} · {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 我的待办 -->
      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>我的待办</span>
          <el-tag v-if="myTasks.length" size="small">{{ myTasks.length }}</el-tag>
        </header>
        <div v-if="tasksLoading" class="dash-panel__bd"><SkeletonLoader variant="list-item" :count="3" /></div>
        <el-empty v-else-if="!myTasks.length" description="暂无待办" :image-size="56" />
        <TransitionGroup v-else name="task-list" tag="div" class="dash-panel__bd">
          <div
            v-for="task in myTasks"
            :key="task.id"
            class="dash-row dash-row--task"
            @click="openTask(task)"
          >
            <span class="dash-row__type">{{ typeIcon(task.type) }}</span>
            <div class="dash-row__info">
              <strong>{{ task.title }}</strong>
              <span>{{ task.issueKey }}
                <template v-if="task.projectId">· {{ getProjectName(task.projectId) }}</template>
                <template v-if="task.dueDate">
                  · <em :class="{ 'text-danger': isOverdue(task.dueDate) }">{{ task.dueDate }}</em>
                </template>
              </span>
            </div>
            <el-tag size="small" :type="PRIORITY_COLOR[task.priority]" effect="plain">{{ PRIORITY_LABEL[task.priority] }}</el-tag>
            <el-button size="small" circle plain @click.stop="completeTask(task)">
              <el-icon><Check /></el-icon>
            </el-button>
          </div>
        </TransitionGroup>
      </section>
    </div>

    <!-- 近期动态 -->
    <section class="dash-panel dash-panel--full">
      <header class="dash-panel__hd"><span>近期动态</span></header>
      <div v-if="loading" class="dash-panel__bd"><SkeletonLoader variant="list-item" :count="4" /></div>
      <el-empty v-else-if="!dashboard?.recentActivities?.length" description="暂无动态" :image-size="56" />
      <div v-else class="dash-panel__bd">
        <div v-for="act in dashboard.recentActivities" :key="act.id" class="dash-row dash-row--act">
          <span class="dash-row__dot">{{ act.username.charAt(0).toUpperCase() }}</span>
          <div class="dash-row__info">
            <strong>{{ act.username }}</strong>
            <span>{{ act.detail }} · {{ actionLabel(act.action) }} · {{ act.createdAt }}</span>
          </div>
        </div>
      </div>
    </section>
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
  UserFilled, ArrowRight, Check, Clock
} from "@element-plus/icons-vue"
import type { DashboardData, IssueCard, IssueType } from "@/types"
import SkeletonLoader from "@/components/common/SkeletonLoader.vue"
import ShinyText from "@/components/common/ShinyText.vue"
import Aurora from "@/components/common/Aurora.vue"

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<DashboardData | null>(null)
const myTasks = ref<IssueCard[]>([])
const loading = ref(true)
const tasksLoading = ref(true)

const TYPE_ICONS: Record<IssueType, string> = { STORY: 'S', TASK: 'T', BUG: 'B' }

const quickActions = [
  { label: '新建任务', icon: 'Plus', route: '/projects' },
  { label: '看板视图', icon: 'Grid', route: '/projects/board' },
  { label: 'Sprint', icon: 'DataAnalysis', route: '/projects/sprint' },
  { label: '报告', icon: 'Document', route: '/reports' },
  { label: '团队管理', icon: 'UserFilled', route: '/teams' },
]

function typeIcon(t: string) { return TYPE_ICONS[t as IssueType] || '?' }

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

const ACTION_MAP: Record<string, string> = {
  CREATE: '创建', UPDATE: '更新', MOVE: '移动',
  DELETE: '删除', COMMENT: '评论', ASSIGN: '指派',
}
function actionLabel(a: string) { return ACTION_MAP[a] || a }

async function fetchDashboard() {
  loading.value = true
  try { const res = await request.get("/dashboard"); dashboard.value = res.data }
  catch { /* */ }
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
    if (dashboard.value) dashboard.value.doneIssues = (dashboard.value.doneIssues || 0) + 1
  } catch { /* */ }
}

function openTask(task: IssueCard) {
  router.push(`/projects/${task.projectId || 0}/board?issue=${task.id}`)
}

onMounted(() => { fetchDashboard(); fetchMyTasks() })
</script>

<style scoped lang="scss">
/* ===== 全局：零阴影，中性灰调，Linear/Notion 风格 ===== */
.dashboard {
  position: relative;     // 让 Aurora 绝对定位有锚点
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;        // 给 Aurora 一些呼吸空间
}

/* --- Hero --- */
.dash-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 36px 0 28px;

  h1 {
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.5px;
    margin: 0 0 6px;
    color: var(--text-primary);
  }

  p {
    margin: 0;
    font-size: 14px;
    line-height: 1.55;
    color: var(--text-secondary);
    strong { font-weight: 650; color: var(--text-primary); }
    .text-danger { color: var(--color-danger); }
  }

  &__actions {
    display: flex;
    gap: 10px;
    flex-shrink: 0;

    .el-button {
      border-radius: 8px;
      font-weight: 600;
      font-size: 13.5px;
      transition: opacity 0.2s;
      &:hover { opacity: 0.85; }
    }
  }
}

/* --- 统计行 --- */
.dash-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.dash-stat {
  padding: 20px 22px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: border-color 0.2s;

  &:hover { border-color: var(--text-muted); }

  &__value {
    font-size: 32px;
    font-weight: 800;
    letter-spacing: -1px;
    line-height: 1;
    color: var(--text-primary);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    font-size: 12.5px;
    color: var(--text-muted);
    font-weight: 550;
    margin-top: 4px;
  }
}

/* --- 快捷操作 --- 纯文字，无背景色块 */
.dash-shortcuts {
  display: flex;
  gap: 4px;
  margin-bottom: 24px;
  padding: 14px 18px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  overflow-x: auto;

  &::-webkit-scrollbar { height: 0; }
}

.dash-sc {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
  transition: all 0.15s ease;

  .el-icon { font-size: 15px; }

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &:active { transform: scale(0.97); }
}

/* --- Banner --- */
.dash-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  margin-bottom: 24px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-subtle);
  font-size: 13.5px;
  color: var(--text-secondary);

  strong { color: var(--text-primary); font-weight: 650; }
  .el-button { margin-left: auto; flex-shrink: 0; }
}

/* --- 面板网格 --- */
.dash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

/* --- 面板 --- 零阴影，靠边框区分层级 */
.dash-panel {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-card);
  overflow: hidden;
  transition: border-color 0.2s;

  &:hover { border-color: rgba(0, 0, 0, 0.12); }

  &--full { margin-bottom: 0; }
}

.dash-panel__hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color-light);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.15px;
  color: var(--text-primary);
}

.dash-panel__bd {
  padding: 8px 20px 18px;
}

/* --- 行项 --- */
.dash-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  transition: background 0.15s;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--bg-hover); border-radius: 6px; margin: 0 -4px; padding-left: 4px; padding-right: 4px; }

  &__icon,
  &__dot {
    width: 34px; height: 34px;
    border-radius: 9px;
    background: #475569;
    color: #fff;
    display: flex; align-items: center; justify-content: center;
    font-size: 13px;
    font-weight: 750;
    flex-shrink: 0;
  }

  &__type {
    width: 28px; height: 28px;
    border-radius: 7px;
    background: var(--bg-subtle);
    color: var(--text-muted);
    display: flex; align-items: center; justify-content: center;
    font-size: 11px;
    font-weight: 800;
    flex-shrink: 0;
  }

  &__info {
    flex: 1; min-width: 0;
    display: flex; flex-direction: column; gap: 2px;

    strong {
      font-size: 14px;
      font-weight: 650;
      color: var(--text-primary);
      letter-spacing: -0.15px;
    }

    span {
      font-size: 12px;
      color: var(--text-muted);
      em { font-style: normal; }
    }
  }

  &--task .el-tag { flex-shrink: 0; }
  &--task .el-button { flex-shrink: 0; opacity: 0; transition: opacity 0.15s; }
  &--task:hover .el-button { opacity: 1; }
}

/* --- 动画 --- */
.task-list-enter-active,
.task-list-leave-active { transition: all 0.25s ease; }
.task-list-enter-from { opacity: 0; transform: translateX(12px); }
.task-list-leave-to { opacity: 0; transform: translateX(-12px); }

@media (max-width: 900px) {
  .dash-stats { grid-template-columns: repeat(2, 1fr); }
  .dash-grid { grid-template-columns: 1fr; }
  .dash-hero { flex-direction: column; align-items: flex-start; gap: 16px; }
}
</style>
