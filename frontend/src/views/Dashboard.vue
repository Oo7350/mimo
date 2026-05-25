<template>
  <div class="dashboard">
    <div class="dashboard__welcome">
      <h2>工作台</h2>
      <p>欢迎回来，{{ userStore.username }}</p>
    </div>

    <!-- 统计卡片 -->
    <div class="dashboard__stats">
      <StatCard
        title="总任务"
        :value="dashboard?.totalIssues || 0"
        accent-color="var(--color-primary)"
        icon-bg="rgba(79,110,246,0.12)"
        icon="Odometer"
        :loading="loading"
      />
      <StatCard
        title="进行中"
        :value="dashboard?.inProgressIssues || 0"
        accent-color="var(--color-warning)"
        icon-bg="rgba(245,158,11,0.12)"
        icon="Loading"
        :loading="loading"
      />
      <StatCard
        title="已完成"
        :value="dashboard?.doneIssues || 0"
        accent-color="var(--color-success)"
        icon-bg="rgba(16,185,129,0.12)"
        icon="CircleCheck"
        :loading="loading"
      />
    </div>

    <div class="dashboard__panels">
      <!-- 我的项目 -->
      <div class="dashboard__panel">
        <div class="panel-header">
          <div class="panel-header__left">
            <div class="panel-header__dot" style="background: var(--color-primary)" />
            <span>我的项目</span>
          </div>
          <el-button size="small" text type="primary" @click="$router.push('/projects')">
            查看全部
          </el-button>
        </div>
        <div v-if="loading" class="panel-body">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty v-else-if="!dashboard?.myProjects?.length" description="暂无项目" :image-size="64" />
        <div v-else class="panel-body">
          <div
            v-for="p in dashboard.myProjects"
            :key="p.id"
            class="project-item"
            @click="$router.push(`/projects/${p.id}`)"
          >
            <div class="project-item__avatar">
              {{ p.name.charAt(0).toUpperCase() }}
            </div>
            <div class="project-item__info">
              <div class="project-item__name">{{ p.name }}</div>
              <div class="project-item__meta">
                <el-tag size="small" :type="p.template === 'SCRUM' ? '' : 'warning'">
                  {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}
                </el-tag>
                <span>{{ p.key }}</span>
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
            <div class="panel-header__dot" style="background: var(--color-warning)" />
            <span>我的待办</span>
            <el-tag v-if="myTasks.length" size="small" round type="warning">
              {{ myTasks.length }}
            </el-tag>
          </div>
        </div>
        <div v-if="tasksLoading" class="panel-body">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty v-else-if="!myTasks.length" description="暂无待办任务" :image-size="64" />
        <TransitionGroup v-else name="task-list" tag="div" class="panel-body">
          <div
            v-for="task in myTasks"
            :key="task.id"
            class="task-item"
            @click="openTask(task)"
          >
            <div class="task-item__left">
              <span class="task-item__icon">{{ typeIcon(task.type) }}</span>
              <div>
                <div class="task-item__title">{{ task.title }}</div>
                <div class="task-item__sub">
                  <span class="task-item__key">{{ task.issueKey }}</span>
                  <span v-if="task.projectId" class="task-item__project">{{ getProjectName(task.projectId) }}</span>
                  <span
                    v-if="task.dueDate"
                    :class="['task-item__due', { 'task-item__due--overdue': isOverdue(task.dueDate) }]"
                  >
                    📅 {{ task.dueDate }}
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
    <div class="dashboard__panel">
      <div class="panel-header">
        <div class="panel-header__left">
          <div class="panel-header__dot" style="background: var(--text-secondary)" />
          <span>近期动态</span>
        </div>
      </div>
      <div v-if="loading" class="panel-body">
        <SkeletonLoader variant="list-item" :count="4" />
      </div>
      <el-empty v-else-if="!dashboard?.recentActivities?.length" description="暂无动态" :image-size="64" />
      <el-timeline v-else class="panel-body">
        <el-timeline-item
          v-for="act in dashboard.recentActivities"
          :key="act.id"
          :timestamp="act.createdAt"
          placement="top"
        >
          <strong>{{ act.username }}</strong> {{ act.detail }}
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { useRouter } from "vue-router"
import { useUserStore } from "@/store/user"
import request from "@/api/request"
import { queryIssues, updateIssue } from "@/api/issue"
import { ElMessage } from "element-plus"
import { TYPE_LABEL, PRIORITY_LABEL, PRIORITY_COLOR } from "@/utils/constants"
import type { DashboardData, IssueCard, IssueType } from "@/types"
import StatCard from "@/components/common/StatCard.vue"
import SkeletonLoader from "@/components/common/SkeletonLoader.vue"

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<DashboardData | null>(null)
const myTasks = ref<IssueCard[]>([])
const loading = ref(true)
const tasksLoading = ref(true)

const TYPE_ICONS: Record<IssueType, string> = { STORY: '📖', TASK: '✅', BUG: '🐛' }
function typeIcon(t: string) { return TYPE_ICONS[t as IssueType] || '📋' }

function getProjectName(projectId: number): string {
  const proj = dashboard.value?.myProjects?.find(p => p.id === projectId)
  return proj?.name || ''
}

function isOverdue(dateStr: string): boolean {
  if (!dateStr) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(dateStr) < today
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
  max-width: 1100px;
  margin: 0 auto;

  &__welcome {
    margin-bottom: 24px;
    h2 { font-size: 24px; font-weight: 700; }
    p { color: var(--text-secondary); margin-top: 4px; font-size: 14px; }
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-bottom: 24px;
  }

  &__panels {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 24px;
  }

  &__panel {
    background: var(--bg-card);
    border-radius: var(--border-radius-lg);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    margin-bottom: 16px;
    overflow: hidden;
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

  &__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }
}

.panel-body {
  padding: 8px 20px 16px;
}

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
  }

  &__avatar {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 14px;
    flex-shrink: 0;
  }

  &__info { flex: 1; min-width: 0; }
  &__name { font-weight: 600; font-size: 14px; color: var(--text-primary); }
  &__meta {
    display: flex; align-items: center; gap: 8px;
    font-size: 12px; color: var(--text-secondary); margin-top: 3px;
  }

  &__arrow { color: var(--text-placeholder); flex-shrink: 0; }
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
  &__icon { font-size: 18px; flex-shrink: 0; }
  &__title { font-size: 14px; font-weight: 500; color: var(--text-primary); }
  &__sub { display: flex; align-items: center; gap: 10px; margin-top: 2px; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; }
  &__project { font-size: 11px; color: var(--color-primary); background: rgba(79,110,246,0.08); padding: 1px 6px; border-radius: 4px; }
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

.task-list-enter-active,
.task-list-leave-active { transition: all 0.3s ease; }
.task-list-enter-from { opacity: 0; transform: translateX(16px); }
.task-list-leave-to { opacity: 0; transform: translateX(-16px); }
</style>
