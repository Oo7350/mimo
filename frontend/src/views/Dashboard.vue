<template>
  <div class="dashboard">
    <!-- Hero — 干净文字，无装饰背景 -->
    <div class="dash-hero">
      <div class="dash-hero__text">
        <h1>
          <ShinyText
            :text="`${greeting}，${userStore.username}`"
            color="var(--text-primary)"
            shine-color="var(--color-accent)"
            :speed="3.2"
          />
        </h1>
        <p>
          <template v-if="workbench?.overdue?.length">
            你有 <strong class="text-danger">{{ overdueCount }}</strong> 项任务已逾期，
          </template>
          <template v-if="tasks.length">
            当前共有 <strong>{{ tasks.length }}</strong> 项个人任务
          </template>
          <template v-else-if="!loading && dashboard?.myProjects?.length">
            当前没有待办任务
          </template>
          <template v-else-if="!loading">
            加入或创建团队，开始第一个项目
          </template>
          <template v-if="pendingCount > 0">
            · <strong>{{ pendingCount }}</strong> 项审批或验收等待你处理
          </template>
        </p>
      </div>
      <div class="dash-hero__actions">
        <RippleButton variant="primary" @click="$router.push('/projects')">
          进入项目
        </RippleButton>
        <RippleButton variant="default" @click="$router.push('/teams')">
          创建团队
        </RippleButton>
      </div>
    </div>

    <!-- 统计卡片 — 数字滚动入场 -->
    <div class="dash-stats">
      <div class="dash-stat">
        <span class="dash-stat__value">
          <CountUp :to="tasks.length" :duration="1.4" />
        </span>
        <span class="dash-stat__label">我的待办</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">
          <CountUp :to="workbench?.dueToday?.length || 0" :duration="1.4" />
        </span>
        <span class="dash-stat__label">今日到期</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">
          <CountUp :to="pendingCount" :duration="1.4" />
        </span>
        <span class="dash-stat__label">待我处理</span>
      </div>
      <div class="dash-stat">
        <span class="dash-stat__value">
          {{ weeklySummary.workHours }}
        </span>
        <span class="dash-stat__label">本周工时</span>
      </div>
    </div>

    <!--
      接下来做什么 —— 把"平台有什么功能"换成"你现在该做什么"。
      空库时给三步上手清单（建项目 → 建迭代 → 拉任务），有数据时给优先级最高的三件事。
      以前这些信息散在下面四五个面板里，用户得自己扫一遍才知道先干哪个。
    -->
    <div v-if="!loading" class="dash-next">
      <div class="dash-next__hd">
        <span class="dash-next__title">接下来做什么</span>
        <span class="dash-next__hint">{{ nextUpHint }}</span>
      </div>

      <!-- 空库：三步上手 -->
      <ol v-if="!hasProjects" class="dash-next__steps">
        <li>
          <span class="dash-next__no">1</span>
          <div class="dash-next__step-text">
            <b>建一个项目</b>
            <span>任务、迭代、报告、Wiki 都挂在项目下面，这是第一步</span>
          </div>
          <el-button size="small" type="primary" @click="$router.push('/projects')">去建项目</el-button>
        </li>
        <li>
          <span class="dash-next__no">2</span>
          <div class="dash-next__step-text">
            <b>建一个迭代（Sprint）</b>
            <span>把这一两周要做的事圈起来，才能看速率和燃尽</span>
          </div>
          <span class="dash-next__muted">建项目后可用</span>
        </li>
        <li>
          <span class="dash-next__no">3</span>
          <div class="dash-next__step-text">
            <b>在待办列建第一个任务</b>
            <span>可以直接写一句话，也可以让 AI 从草稿里拆任务</span>
          </div>
          <span class="dash-next__muted">建迭代后可用</span>
        </li>
      </ol>

      <!-- 有数据：三件最该做的事 -->
      <div v-else class="dash-next__list">
        <button
          v-for="item in nextUps"
          :key="item.key"
          class="dash-next__item"
          @click="item.action()"
        >
          <el-tag :type="item.tagType" size="small" effect="light">{{ item.tag }}</el-tag>
          <span class="dash-next__item-title">{{ item.title }}</span>
          <span class="dash-next__item-meta">{{ item.meta }}</span>
          <span class="dash-next__item-go">去处理 →</span>
        </button>
        <div v-if="!nextUps.length" class="dash-next__empty">
          没有卡在你这儿的事。
          <el-button size="small" type="primary" link @click="$router.push('/projects')">
            去项目里挑一件
          </el-button>
        </div>
      </div>
    </div>

    <!-- 快捷操作 — 纯文字按钮，无边框图标 -->
    <div class="dash-shortcuts">
      <button
        v-for="qa in quickActions"
        :key="qa.label"
        class="dash-sc"
        :title="qa.hint || undefined"
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
      <el-button
        size="small"
        type="primary"
        link
        @click="$router.push(`/projects/${dashboard.activeSprints[0].projectId}/board`)"
      >
        查看看板 &rarr;
      </el-button>
    </div>

    <!-- 个人任务与待办 -->
    <div class="dash-grid">
      <section class="dash-panel dash-panel--tasks">
        <header class="dash-panel__hd">
          <span>我的任务</span>
          <el-button size="small" type="primary" link @click="$router.push('/projects')">
            跨项目查看
          </el-button>
        </header>
        <div class="task-tabs">
          <button
            v-for="tab in taskTabs"
            :key="tab.value"
            :class="['task-tab', { 'task-tab--active': taskTab === tab.value }]"
            @click="taskTab = tab.value"
          >
            {{ tab.label }}
            <span>{{ tab.count }}</span>
          </button>
        </div>
        <div v-if="workbenchLoading" class="dash-panel__bd">
          <SkeletonLoader variant="list-item" :count="4" />
        </div>
        <el-empty
          v-else-if="!visibleTasks.length"
          :description="emptyTaskText"
          :image-size="56"
        />
        <TransitionGroup v-else name="task-list" tag="div" class="dash-panel__bd">
          <div
            v-for="task in visibleTasks"
            :key="task.id"
            class="dash-row dash-row--task"
            @click="openTask(task)"
          >
            <span class="dash-row__type">{{ typeIcon(task.type) }}</span>
            <div class="dash-row__info">
              <strong>{{ task.title }}</strong>
              <span>
                {{ task.issueKey }}
                <template v-if="task.projectName"> · {{ task.projectName }}</template>
                <template v-if="task.sprintName"> · {{ task.sprintName }}</template>
                <template v-if="task.dueDate"> · {{ task.dueDate }}</template>
              </span>
              <span v-if="task.riskReasons.length" class="risk-reason">
                {{ task.riskReasons.join('；') }}
              </span>
            </div>
            <el-tag
              v-if="task.riskLevel !== 'LOW'"
              size="small"
              :type="task.riskLevel === 'HIGH' ? 'danger' : 'warning'"
              effect="plain"
            >
              {{ task.riskLevel === 'HIGH' ? '高风险' : '关注' }}
            </el-tag>
            <el-tag size="small" :type="priorityType(task.priority)" effect="plain">
              {{ PRIORITY_LABEL[task.priority] }}
            </el-tag>
          </div>
        </TransitionGroup>
      </section>

      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>待我处理</span>
          <el-tag v-if="pendingCount" size="small" type="warning">{{ pendingCount }}</el-tag>
        </header>
        <div v-if="workbenchLoading" class="dash-panel__bd">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty
          v-else-if="!pendingItems.length"
          description="暂无待审批或待验收事项"
          :image-size="56"
        />
        <div v-else class="dash-panel__bd">
          <div
            v-for="item in pendingItems"
            :key="`${item.kind}-${item.id}`"
            class="dash-row"
            @click="openReview(item)"
          >
            <span class="dash-row__type">{{ item.kind === 'CHANGE' ? '审' : '验' }}</span>
            <div class="dash-row__info">
              <strong>{{ item.issueTitle }}</strong>
              <span>
                {{ item.kind === 'CHANGE' ? '变更审批' : '任务验收' }}
                · {{ item.issueKey }}
                <template v-if="item.projectName"> · {{ item.projectName }}</template>
              </span>
              <span v-if="item.summary" class="pending-summary">{{ item.summary }}</span>
            </div>
            <span class="pending-time">{{ formatTime(item.submittedAt) }}</span>
          </div>
        </div>
      </section>
    </div>

    <!-- 周总结与驳回事项 -->
    <div class="dash-grid">
      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>本周总结</span>
          <span class="week-range">{{ weeklySummary.weekStart }} ~ {{ weeklySummary.weekEnd }}</span>
        </header>
        <div class="dash-panel__bd weekly-panel">
          <div class="weekly-metrics">
            <div class="weekly-metric">
              <span>登记工时</span>
              <b>{{ weeklySummary.workHours }}h</b>
            </div>
            <div class="weekly-metric">
              <span>完成任务</span>
              <b>{{ weeklySummary.completedTaskCount }}</b>
            </div>
            <div class="weekly-metric">
              <span>Sprint 故事点</span>
              <b>
                {{ weeklySummary.activeSprintDoneStoryPoints }}/{{
                  weeklySummary.activeSprintStoryPoints
                }}
              </b>
            </div>
          </div>
          <el-empty
            v-if="!weeklySummary.activeSprints.length"
            description="当前没有进行中的 Sprint 投入"
            :image-size="48"
          />
          <div v-else class="sprint-efforts">
            <div
              v-for="sprint in weeklySummary.activeSprints"
              :key="sprint.id"
              class="sprint-effort"
              @click="openSprint(sprint.projectId, sprint.id)"
            >
              <div class="sprint-effort__head">
                <strong>{{ sprint.name }}</strong>
                <span>{{ sprint.projectName }}</span>
              </div>
              <div class="sprint-effort__meta">
                <span>{{ sprint.assignedTaskCount }} 项任务</span>
                <span>{{ sprint.doneStoryPoints }}/{{ sprint.storyPoints }} 故事点</span>
                <span>{{ sprint.weekHours }}h</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>最近被驳回</span>
          <el-tag v-if="rejectedItems.length" size="small" type="danger">
            {{ rejectedItems.length }}
          </el-tag>
        </header>
        <div v-if="workbenchLoading" class="dash-panel__bd">
          <SkeletonLoader variant="list-item" :count="2" />
        </div>
        <el-empty
          v-else-if="!rejectedItems.length"
          description="没有被驳回的变更或验收"
          :image-size="56"
        />
        <div v-else class="dash-panel__bd">
          <div
            v-for="item in rejectedItems"
            :key="`${item.kind}-${item.id}`"
            class="dash-row"
            @click="openRejected(item)"
          >
            <span class="dash-row__type dash-row__type--danger">
              {{ item.kind === 'CHANGE' ? '变' : '验' }}
            </span>
            <div class="dash-row__info">
              <strong>{{ item.issueTitle }}</strong>
              <span>
                {{ item.kind === 'CHANGE' ? '变更被驳回' : '验收被打回' }}
                · {{ item.issueKey }}
                <template v-if="item.projectName"> · {{ item.projectName }}</template>
              </span>
              <span class="rejected-reason">{{ item.reason || '未填写原因' }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="dash-grid">
      <section class="dash-panel">
        <header class="dash-panel__hd">
          <span>我的项目</span>
          <el-button size="small" type="primary" link @click="$router.push('/projects')">
            查看全部
          </el-button>
        </header>
        <div v-if="loading" class="dash-panel__bd">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty
          v-else-if="!dashboard?.myProjects?.length"
          description="暂无项目"
          :image-size="56"
        >
          <el-button type="primary" size="small" @click="$router.push('/teams')">
            前往团队
          </el-button>
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
              <span>
                {{ p.key }} · {{ p.teamName }} ·
                {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <section class="dash-panel">
        <header class="dash-panel__hd"><span>近期动态</span></header>
        <div v-if="loading" class="dash-panel__bd">
          <SkeletonLoader variant="list-item" :count="3" />
        </div>
        <el-empty
          v-else-if="!dashboard?.recentActivities?.length"
          description="暂无动态"
          :image-size="56"
        />
        <div v-else class="dash-panel__bd">
          <div
            v-for="act in dashboard.recentActivities.slice(0, 6)"
            :key="act.id"
            class="dash-row dash-row--act"
          >
            <span class="dash-row__dot">{{ act.username.charAt(0).toUpperCase() }}</span>
            <div class="dash-row__info">
              <strong>{{ act.username }}</strong>
              <span>{{ act.detail }} · {{ actionLabel(act.action) }} · {{ act.createdAt }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import request from '@/api/request'
import { getMyWorkbench } from '@/api/workbench'
import { PRIORITY_LABEL } from '@/utils/constants'
import {
  Grid,
  Plus,
  DataAnalysis,
  Document,
  UserFilled,
} from '@element-plus/icons-vue'
import type { DashboardData, IssueType } from '@/types'
import type {
  PendingWorkbenchReview,
  RejectedWorkbenchItem,
  WorkbenchData,
  WorkbenchTask,
  WorkbenchWeeklySummary,
} from '@/types/workbench'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import ShinyText from '@/components/common/ShinyText.vue'
import CountUp from '@/components/common/CountUp.vue'
import RippleButton from '@/components/common/RippleButton.vue'

type TaskTab = 'all' | 'today' | 'overdue'

const router = useRouter()
const userStore = useUserStore()
const dashboard = ref<DashboardData | null>(null)
const workbench = ref<WorkbenchData | null>(null)
const loading = ref(true)
const workbenchLoading = ref(true)
const taskTab = ref<TaskTab>('all')

const TYPE_ICONS: Record<IssueType, string> = { STORY: 'S', TASK: 'T', BUG: 'B' }
const EMPTY_WEEKLY: WorkbenchWeeklySummary = {
  weekStart: '',
  weekEnd: '',
  workHours: 0,
  completedTaskCount: 0,
  activeSprintCount: 0,
  activeSprintStoryPoints: 0,
  activeSprintDoneStoryPoints: 0,
  activeSprintHours: 0,
  activeSprints: [],
}

/**
 * 快捷入口必须真的到得了地方。
 *
 * 以前 5 个入口里 4 个 `route: '/projects'`：点「看板视图」「Sprint」「项目报告」都只是回到项目列表，
 * 用户以为功能没做（KNOWN-014）。看板/迭代/报表本来就挂在具体项目下，所以这里取
 * 「我的项目」第一个（接口按最近活跃返回）作为默认落地项目；没有项目时退回项目列表并给出提示文案。
 */
const quickProjectId = computed(() => dashboard.value?.myProjects?.[0]?.id ?? null)
const quickProjectName = computed(() => dashboard.value?.myProjects?.[0]?.name ?? '')

const quickActions = computed(() => {
  const pid = quickProjectId.value
  const noProjectHint = '先创建或加入一个项目'
  return [
    { label: '项目管理', icon: Plus, route: '/projects', hint: '' },
    {
      label: '看板视图',
      icon: Grid,
      route: pid ? `/projects/${pid}/board` : '/projects',
      hint: pid ? `打开「${quickProjectName.value}」的看板` : noProjectHint,
    },
    {
      label: 'Sprint',
      icon: DataAnalysis,
      route: pid ? `/projects/${pid}/sprints` : '/projects',
      hint: pid ? `打开「${quickProjectName.value}」的迭代列表` : noProjectHint,
    },
    {
      label: '项目报告',
      icon: Document,
      route: pid ? `/projects/${pid}/reports` : '/projects',
      hint: pid ? `打开「${quickProjectName.value}」的报告列表` : noProjectHint,
    },
    { label: '团队管理', icon: UserFilled, route: '/teams', hint: '' },
  ]
})

const tasks = computed(() => workbench.value?.tasks || [])
const overdueCount = computed(() => workbench.value?.overdue?.length || 0)
const weeklySummary = computed(() => workbench.value?.weeklySummary || EMPTY_WEEKLY)
const rejectedItems = computed(() => workbench.value?.rejectedItems || [])
const pendingItems = computed(() =>
  [
    ...(workbench.value?.pendingChangeReviews || []),
    ...(workbench.value?.pendingTaskReviews || []),
  ].sort((left, right) => (right.submittedAt || '').localeCompare(left.submittedAt || '')),
)
const pendingCount = computed(() => pendingItems.value.length)
const hasProjects = computed(() => (dashboard.value?.myProjects?.length || 0) > 0)

/**
 * 「接下来做什么」的三件事。
 *
 * 排序即优先级：被打回（卡着别人的确认）→ 待我验收/审批（别人等着我）→ 已逾期 → 进行中 → 待办。
 * 每一项都直接跳到对应工作项，不再让用户在四五个面板之间自己找。
 */
const nextUps = computed(() => {
  const items: Array<{
    key: string
    tag: string
    tagType: 'danger' | 'warning' | 'primary' | 'info' | 'success'
    title: string
    meta: string
    action: () => void
  }> = []

  const rejected = rejectedItems.value[0]
  if (rejected) {
    items.push({
      key: `reject-${rejected.kind}-${rejected.id}`,
      tag: '被打回',
      tagType: 'danger',
      title: `${rejected.issueKey || ''} ${rejected.issueTitle || ''}`.trim(),
      meta: `${rejected.kind === 'CHANGE' ? '变更' : '验收'}被打回：${rejected.reason || '未填原因'}`,
      action: () => openRejected(rejected),
    })
  }

  const pending = pendingItems.value[0]
  if (pending) {
    items.push({
      key: `pending-${pending.kind}-${pending.id}`,
      tag: '待我处理',
      tagType: 'warning',
      title: `${pending.issueKey || ''} ${pending.issueTitle || ''}`.trim(),
      meta: pending.kind === 'CHANGE' ? '有变更等你审批' : '有验收等你确认',
      action: () => openReview(pending),
    })
  }

  const overdue = (workbench.value?.overdue || [])[0]
  if (overdue) {
    items.push({
      key: `overdue-${overdue.id}`,
      tag: '已逾期',
      tagType: 'danger',
      title: `${overdue.issueKey || ''} ${overdue.title || ''}`.trim(),
      meta: overdue.dueDate ? `截止 ${overdue.dueDate}，已经过了` : '已经过了计划截止时间',
      action: () => openTask(overdue),
    })
  }

  const doing = tasks.value.find((task) => task.status === 'IN_PROGRESS')
  if (doing) {
    items.push({
      key: `doing-${doing.id}`,
      tag: '进行中',
      tagType: 'primary',
      title: `${doing.issueKey || ''} ${doing.title || ''}`.trim(),
      meta: '接着往下做（进行中的任务会自动计时）',
      action: () => openTask(doing),
    })
  }

  const todo = tasks.value.find((task) => task.status === 'TODO')
  if (todo && items.length < 3) {
    items.push({
      key: `todo-${todo.id}`,
      tag: '待开始',
      tagType: 'info',
      title: `${todo.issueKey || ''} ${todo.title || ''}`.trim(),
      meta: '还没开始，从这一项入手',
      action: () => openTask(todo),
    })
  }

  return items.slice(0, 3)
})

const nextUpHint = computed(() => {
  if (!hasProjects.value) return '第一次用：三步就能跑起来'
  return nextUps.value.length ? '按"卡住别人 → 卡住自己 → 快到期"排的序' : '当前没有卡在你这儿的事'
})
const taskTabs = computed(() => [
  { label: '全部', value: 'all' as const, count: tasks.value.length },
  { label: '今日到期', value: 'today' as const, count: workbench.value?.dueToday?.length || 0 },
  { label: '已逾期', value: 'overdue' as const, count: overdueCount.value },
])
const visibleTasks = computed(() => {
  if (taskTab.value === 'today') return workbench.value?.dueToday || []
  if (taskTab.value === 'overdue') return workbench.value?.overdue || []
  return tasks.value
})
const emptyTaskText = computed(() => {
  if (taskTab.value === 'today') return '今天没有到期任务'
  if (taskTab.value === 'overdue') return '没有逾期任务'
  return '当前没有分配给你的待办任务'
})

function typeIcon(type: string) {
  return TYPE_ICONS[type as IssueType] || '?'
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const ACTION_MAP: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  MOVE: '移动',
  DELETE: '删除',
  COMMENT: '评论',
  ASSIGN: '指派',
}

function actionLabel(action: string) {
  return ACTION_MAP[action] || action
}

function priorityType(priority: WorkbenchTask['priority']) {
  if (priority === 'HIGHEST') return 'danger'
  if (priority === 'HIGH') return 'warning'
  if (priority === 'LOW' || priority === 'LOWEST') return 'info'
  return 'primary'
}

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

async function fetchDashboard() {
  loading.value = true
  try {
    const response = await request.get('/dashboard')
    dashboard.value = response.data
  } catch {
    dashboard.value = null
  } finally {
    loading.value = false
  }
}

async function fetchWorkbench() {
  workbenchLoading.value = true
  try {
    const response = await getMyWorkbench()
    workbench.value = response.data
  } catch {
    workbench.value = null
  } finally {
    workbenchLoading.value = false
  }
}

function openIssue(projectId: number, issueId: number) {
  router.push(`/projects/${projectId}/board?issue=${issueId}`)
}

function openTask(task: WorkbenchTask) {
  openIssue(task.projectId, task.id)
}

function openReview(item: PendingWorkbenchReview) {
  openIssue(item.projectId, item.issueId)
}

function openRejected(item: RejectedWorkbenchItem) {
  openIssue(item.projectId, item.issueId)
}

function openSprint(projectId: number, sprintId: number) {
  router.push(`/projects/${projectId}/sprints/${sprintId}/burndown`)
}

onMounted(() => {
  fetchDashboard()
  fetchWorkbench()
})
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

/* --- 接下来做什么 --- */
.dash-next {
  margin-bottom: 16px;
  padding: 16px 18px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
}

.dash-next__hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.dash-next__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.dash-next__hint {
  font-size: 12px;
  color: var(--text-secondary);
}

.dash-next__steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.dash-next__no {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.dash-next__step-text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;

  b {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  span {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.dash-next__muted {
  font-size: 12px;
  color: var(--text-secondary);
}

.dash-next__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dash-next__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: background 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: var(--bg-hover, rgba(0, 0, 0, 0.03));
    border-color: var(--border-color);
  }
}

.dash-next__item-title {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dash-next__item-meta {
  flex-shrink: 0;
  max-width: 42%;
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dash-next__item-go {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-accent, var(--el-color-primary));
}

.dash-next__empty {
  font-size: 13px;
  color: var(--text-secondary);
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

.task-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 20px 0;
}

.task-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  span {
    min-width: 18px;
    padding: 1px 5px;
    border-radius: 999px;
    background: var(--bg-subtle);
    font-size: 10px;
    text-align: center;
  }

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &--active {
    background: var(--bg-subtle);
    color: var(--text-primary);
  }
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
  &--task .el-tag + .el-tag { margin-left: 2px; }
}

.risk-reason,
.rejected-reason {
  color: #c45656 !important;
}

.pending-summary {
  max-width: 430px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-time,
.week-range {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 11px;
}

.week-range {
  font-weight: 500;
}

.dash-row__type--danger {
  color: #c45656;
  background: #fff0f0;
}

.weekly-panel {
  padding-top: 16px;
}

.weekly-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.weekly-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 9px;
  background: var(--bg-subtle);

  span {
    color: var(--text-muted);
    font-size: 11px;
  }

  b {
    color: var(--text-primary);
    font-size: 20px;
    font-variant-numeric: tabular-nums;
  }
}

.sprint-efforts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sprint-effort {
  padding: 10px 12px;
  border: 1px solid var(--border-color-light);
  border-radius: 9px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    border-color: var(--border-color);
    background: var(--bg-hover);
  }

  &__head,
  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  &__head {
    strong {
      color: var(--text-primary);
      font-size: 13px;
    }

    span {
      color: var(--text-muted);
      font-size: 11px;
    }
  }

  &__meta {
    margin-top: 7px;
    color: var(--text-secondary);
    font-size: 11px;
  }
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
  .weekly-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .pending-time { display: none; }
}
</style>
