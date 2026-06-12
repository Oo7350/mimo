<template>
  <div class="board">
    <!-- 顶部工具栏 -->
    <div class="board__toolbar">
      <div class="board__toolbar-left">
        <el-button class="board__back" @click="$router.push(`/projects/${projectId}`)" text>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="board__project-info">
          <h3 class="board__title">{{ projectName }}</h3>
          <span class="board__subtitle">看板 · 拖拽卡片更新状态</span>
        </div>
        <div class="segmented-control board__view-switcher">
          <button
            v-for="v in views"
            :key="v.key"
            :class="['segmented-control__item', { 'is-active': currentView === v.key }]"
            @click="currentView = v.key as any"
          >
            <el-icon><component :is="v.icon" /></el-icon>
            {{ v.label }}
          </button>
        </div>
      </div>
      <div class="board__toolbar-right">
        <el-button id="create-task-btn" type="primary" @click="showCreateIssue = true">
          <el-icon><Plus /></el-icon>新建工作项
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div v-if="currentView === 'kanban' || currentView === 'buglist'" class="board__filters">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标题、编号、指派人..."
        :prefix-icon="Search"
        clearable
        size="default"
        class="board__search"
      />
      <el-select v-model="filterAssignee" placeholder="指派人" clearable class="board__filter-select">
        <el-option v-for="a in assigneeOptions" :key="a" :label="a" :value="a" />
      </el-select>
      <el-select v-model="filterPriority" placeholder="优先级" clearable class="board__filter-select board__filter-select--sm">
        <el-option label="最高" value="HIGHEST" />
        <el-option label="高" value="HIGH" />
        <el-option label="中" value="MEDIUM" />
        <el-option label="低" value="LOW" />
        <el-option label="最低" value="LOWEST" />
      </el-select>
      <el-select v-if="currentView === 'kanban'" v-model="swimlaneMode" placeholder="泳道" class="board__filter-select board__filter-select--sm">
        <el-option label="无泳道" value="" />
        <el-option label="按指派人" value="assignee" />
        <el-option label="按史诗" value="epic" />
      </el-select>
      <div class="board__presets">
        <button
          v-for="p in filterPresets"
          :key="p.name"
          :class="['board__preset-btn', { 'is-active': activePreset === p.name }]"
          @click="applyPreset(p)"
        >
          {{ p.name }}
        </button>
      </div>
      <div class="board__type-filters">
        <button
          v-for="t in typeFilters"
          :key="t.value"
          :class="['board__type-btn', { 'is-active': filterType === t.value }, t.class]"
          @click="filterType = filterType === t.value && t.value ? '' : t.value"
        >
          {{ t.label }}
        </button>
      </div>
      <div class="board__toolbar-actions">
        <el-button class="board__report-btn" @click="$router.push(`/projects/${projectId}/reports`)">
          <el-icon><Document /></el-icon>报告
          <span v-if="recentReportCount > 0" class="board__report-badge">{{ recentReportCount }}</span>
        </el-button>
        <el-dropdown trigger="click" @command="handleQuickReport">
          <el-button type="primary">
            <el-icon><Plus /></el-icon>新建工作项
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="issue">新建工作项</el-dropdown-item>
              <el-dropdown-item command="daily" divided>生成今日日报</el-dropdown-item>
              <el-dropdown-item command="weekly">生成本周周报</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- Kanban View -->
    <div v-if="currentView === 'kanban'" class="board__kanban">
      <!-- Tab 切换栏 -->
      <div class="board__kanban-tabs">
        <button :class="['board__tab', { 'is-active': kanbanTab === 'tasks' }]" @click="switchKanbanTab('tasks')">
          <el-icon><Grid /></el-icon> 工作项
          <span class="board__tab-count">{{ taskTotal }}</span>
        </button>
        <button :class="['board__tab', { 'is-active': kanbanTab === 'bugs', 'board__tab--bug': kanbanTab === 'bugs' }]" @click="switchKanbanTab('bugs')">
          <el-icon><WarningFilled /></el-icon> 缺陷
          <span class="board__tab-count">{{ bugTotal }}</span>
        </button>
      </div>

      <!-- 故事 & 任务 区域 -->
      <div v-show="kanbanTab === 'tasks'" class="board__section">
        <div class="board__columns">
          <div
            v-for="(col, idx) in taskColumns"
            :key="'task-' + col.id"
            :class="['board__column', { 'column-drop-target': dragOverColumnId === col.id }]"
          >
            <div class="board__column-header" :style="{ borderTopColor: columnColors[idx] || columnColors[0] }">
              <div class="board__column-title">
                <span class="board__column-dot" :style="{ background: columnColors[idx] || columnColors[0] }" />
                <span class="board__column-name">{{ col.name }}</span>
              </div>
              <span class="board__column-count">{{ filteredIssues(col).length }}</span>
            </div>

            <draggable
              :list="col.issues"
              group="tasks"
              itemKey="id"
              ghost-class="issue-card-ghost"
              drag-class="issue-card-dragging"
              :pull="true"
              :put="(evt: any) => !evt?.fromEl?.classList?.contains('bug-column-body')"
              @change="(evt: any) => handleDragChange(evt, col.id)"
              @start="() => dragOverColumnId = col.id"
              @end="() => dragOverColumnId = null"
              class="board__column-body"
            >
              <template #item="{ element, index }">
                <div :class="['board__drag-item', { 'is-hidden': !issueMatchesFilter(element) }]">
                  <IssueCard
                    :issue="element"
                    @click="openIssue(element)"
                    @edit="openIssue(element)"
                    @complete="quickComplete"
                    @delete="handleDeleteIssue"
                  />
                </div>
              </template>
            </draggable>

            <div v-if="filteredIssues(col).length === 0" class="board__column-empty">
              <el-icon :size="28"><Box /></el-icon>
              <span>暂无任务</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 缺陷区域（独立状态列，独占全宽） -->
      <div v-show="kanbanTab === 'bugs'" class="board__section board__section--bug-full">
        <div class="board__columns board__columns--bugs">
          <div
            v-for="bcol in bugColumns"
            :key="bcol.id"
            class="board__column board__column--bug bug-column"
          >
            <div class="board__column-header" :style="{ borderTopColor: bcol.color }">
              <span class="board__column-name">{{ bcol.label }}</span>
              <span class="board__column-count">{{ bcol.issues.length }}</span>
            </div>

            <draggable
              :list="bcol.issues"
              group="bugs"
              itemKey="id"
              ghost-class="issue-card-ghost"
              drag-class="issue-card-dragging"
              :pull="true"
              :put="(evt: any) => evt?.fromEl?.classList?.contains('bug-column-body')"
              @change="(evt: any) => handleBugDragChange(evt, bcol.key)"
              @start="() => dragOverColumnId = bcol.id"
              @end="() => dragOverColumnId = null"
              class="board__column-body bug-column-body"
            >
              <template #item="{ element }">
                <div class="board__drag-item">
                  <IssueCard
                    :issue="element"
                    @click="openIssue(element)"
                    @edit="openIssue(element)"
                    @complete="quickComplete"
                    @delete="handleDeleteIssue"
                  />
                </div>
              </template>
            </draggable>

            <div v-if="bcol.issues.length === 0" class="board__column-empty">
              <el-icon :size="20"><Box /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 项目概览栏（看板底部，关联报告） -->
    <div v-if="currentView === 'kanban'" class="board__overview">
      <div class="board__overview-stat" @click="$router.push(`/projects/${projectId}/reports`)">
        <span class="board__overview-num">{{ boardStats.total }}</span>
        <span class="board__overview-label">总任务</span>
      </div>
      <div class="board__overview-divider" />
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--todo">{{ boardStats.todo }}</span>
        <span class="board__overview-label">待办</span>
      </div>
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--progress">{{ boardStats.inProgress }}</span>
        <span class="board__overview-label">进行中</span>
      </div>
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--done">{{ boardStats.done }}</span>
        <span class="board__overview-label">已完成</span>
      </div>
      <div class="board__overview-divider" />
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--bug">{{ boardStats.bugs }}</span>
        <span class="board__overview-label">缺陷</span>
      </div>
      <el-button size="small" text type="primary" class="board__overview-link" @click="$router.push(`/projects/${projectId}/reports`)">
        查看报告与数据看板 →
      </el-button>
    </div>

    <!-- Bug Table View -->
    <div v-else-if="currentView === 'buglist'" class="board__alt-view">
      <BugTableView
        :bugs="allBugs"
        :search-keyword="searchKeyword"
        @select="openIssue"
        @create-bug="showCreateIssue = true"
      />
    </div>

    <!-- Story Map View -->
    <div v-else-if="currentView === 'storymap'" class="board__alt-view">
      <StoryMap />
    </div>

    <IssueDetailDialog
      v-model:visible="showCreateIssue"
      :project-id="projectId"
      :team-id="teamId"
      :columns="columns"
      @created="onCreated"
    />

    <IssueDetailDialog
      v-model:visible="showEditIssue"
      :project-id="projectId"
      :team-id="teamId"
      :columns="columns"
      :issue-id="editingIssueId"
      mode="edit"
      @updated="fetchBoard"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue"
import { useRoute } from "vue-router"
import { useUserStore } from "@/store/user"
import { getBoard, moveIssue } from "@/api/board"
import { getProjectById } from "@/api/project"
import { updateIssue, deleteIssue } from "@/api/issue"
import { avatarGradient } from "@/utils/color"
import draggable from "vuedraggable"
import { ElMessage } from "element-plus"
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue"
import IssueCard from "@/components/issue/IssueCard.vue"
import BugTableView from "@/components/issue/bug/BugTableView.vue"
import StoryMap from "@/components/issue/story/StoryMap.vue"
import { Search, Grid, List, Connection, Box, Document, WarningFilled } from "@element-plus/icons-vue"
import { useWebSocket } from "@/composables/useWebSocket"
import { getReports, generateReport } from "@/api/report"

const route = useRoute()
const userStore = useUserStore()
const projectId = Number(route.params.id)
const projectName = ref("")
const teamId = ref<number>(0)
const columns = ref<any[]>([])
const showCreateIssue = ref(false)
const showEditIssue = ref(false)
const editingIssueId = ref<number>(0)
const dragOverColumnId = ref<string | number | null>(null)
const searchKeyword = ref("")
const filterAssignee = ref("")
const filterPriority = ref("")
const filterType = ref("")
const swimlaneMode = ref<'assignee' | 'epic' | ''>('')
const activePreset = ref("全部")
const currentView = ref<'kanban' | 'buglist' | 'storymap'>('kanban')
const recentReportCount = ref(0)

const columnColors = ['#6366f1', '#f59e0b', '#10b981']

// 缺陷独立状态列定义（虚拟列，不依赖 DB board_columns）
const BUG_STATUS_FLOW = [
  { key: 'NEW', label: '新建', color: '#94a3b8' },
  { key: 'CONFIRMED', label: '已确认', color: '#3b82f6' },
  { key: 'IN_PROGRESS', label: '处理中', color: '#f59e0b' },
  { key: 'RESOLVED', label: '已解决', color: '#8b5cf6' },
  { key: 'VERIFIED', label: '已验证', color: '#06b6d4' },
  { key: 'CLOSED', label: '已关闭', color: '#10b981' },
]

// 从真实列中提取缺陷卡片，按 bugStatus 分组到虚拟列
const bugColumns = computed(() => {
  // 收集所有 BUG 类型 issue
  const allBugs: any[] = []
  columns.value.forEach((col: any) => {
    ;(col.issues || []).forEach((i: any) => {
      if (i.type === 'BUG') allBugs.push(i)
    })
  })
  return BUG_STATUS_FLOW.map(def => ({
    ...def,
    id: `bug-${def.key}`,
    issues: allBugs.filter((i: any) => (i.bugStatus || 'NEW') === def.key),
  }))
})

// 故事/任务列（过滤掉 BUG 类型）
const taskColumns = computed(() =>
  columns.value.map((col: any) => ({
    ...col,
    issues: (col.issues || []).filter((i: any) => i.type !== 'BUG'),
  }))
)

// 看板内 Tab 切换
const kanbanTab = ref<'tasks' | 'bugs'>('tasks')
const taskTotal = computed(() => taskColumns.value.reduce((s: number, c: any) => s + (c.issues?.length || 0), 0))
const bugTotal = computed(() => bugColumns.value.reduce((s: number, c: any) => s + (c.issues?.length || 0), 0))

function switchKanbanTab(tab: 'tasks' | 'bugs') {
  kanbanTab.value = tab
  // 联动类型筛选
  filterType.value = tab === 'bugs' ? 'BUG' : ''
}

const views = [
  { key: 'kanban', label: '看板', icon: Grid },
  { key: 'buglist', label: '缺陷列表', icon: List },
  { key: 'storymap', label: '故事地图', icon: Connection },
]

const typeFilters = [
  { value: '', label: '全部', class: '' },
  { value: 'STORY', label: '故事', class: 'board__type-btn--story' },
  { value: 'TASK', label: '任务', class: 'board__type-btn--task' },
  { value: 'BUG', label: '缺陷', class: 'board__type-btn--bug' },
]

const { subscribe: wsSubscribe } = useWebSocket()

const assigneeOptions = computed(() => {
  const names = new Set<string>()
  columns.value.forEach((c: any) => c.issues?.forEach((i: any) => {
    if (i.assigneeName) names.add(i.assigneeName)
  }))
  return Array.from(names).sort()
})

const allBugs = computed(() => {
  const bugs: any[] = []
  columns.value.forEach((c: any) => c.issues?.forEach((i: any) => {
    if (i.type === 'BUG') bugs.push(i)
  }))
  return bugs
})

// 看板底部统计（关联报告数据）
const boardStats = computed(() => {
  let total = 0, todo = 0, inProgress = 0, done = 0, bugs = 0
  columns.value.forEach((c: any) => {
    (c.issues || []).forEach((i: any) => {
      total++
      if (i.type === 'BUG') { bugs++ }
      else if (i.status === 'TODO') { todo++ }
      else if (i.status === 'DONE') { done++ }
      else { inProgress++ }
    })
  })
  return { total, todo, inProgress, done, bugs }
})

const issueMatchesFilter = (issue: any) => {
  if (filterAssignee.value && issue.assigneeName !== filterAssignee.value) return false
  if (filterPriority.value && issue.priority !== filterPriority.value) return false
  if (filterType.value && issue.type !== filterType.value) return false
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    return (
      issue.title?.toLowerCase().includes(kw) ||
      issue.issueKey?.toLowerCase().includes(kw) ||
      issue.assigneeName?.toLowerCase().includes(kw) ||
      (issue.labels || []).some((l: any) => l.label?.toLowerCase().includes(kw))
    )
  }
  return true
}

const filteredIssues = (col: any) =>
  col.issues?.filter((i: any) => issueMatchesFilter(i)) || []

function laneKey(issue: any): string {
  if (swimlaneMode.value === 'assignee') return issue.assigneeName || '未指派'
  if (swimlaneMode.value === 'epic') {
    if (issue.type === 'STORY') return issue.epic || '未分类史诗'
    return '— 其他类型 —'
  }
  return ''
}

function laneLabel(issue: any) { return laneKey(issue) }

function sortedVisibleIssues(col: any) {
  const list = filteredIssues(col)
  if (!swimlaneMode.value) return list
  return [...list].sort((a, b) => laneKey(a).localeCompare(laneKey(b), 'zh'))
}

function shouldShowLaneHeader(col: any, issue: any): boolean {
  if (!swimlaneMode.value) return false
  const sorted = sortedVisibleIssues(col)
  const idx = sorted.findIndex((i: any) => i.id === issue.id)
  if (idx <= 0) return true
  return laneKey(sorted[idx - 1]) !== laneKey(issue)
}

function laneIssueCount(col: any, issue: any): number {
  const key = laneKey(issue)
  return filteredIssues(col).filter((i: any) => laneKey(i) === key).length
}

function sortColumnsByLane() {
  columns.value.forEach((col: any) => {
    col.issues?.sort((a: any, b: any) => laneKey(a).localeCompare(laneKey(b), 'zh'))
  })
}

interface FilterPreset {
  name: string
  apply: () => void
}

const filterPresets = computed<FilterPreset[]>(() => [
  { name: '全部', apply: () => { clearFilters(); kanbanTab.value = 'tasks' } },
  { name: '缺陷', apply: () => { clearFilters(); filterType.value = 'BUG'; kanbanTab.value = 'bugs' } },
  { name: '故事', apply: () => { clearFilters(); filterType.value = 'STORY'; kanbanTab.value = 'tasks' } },
  { name: '我的', apply: () => { clearFilters(); filterAssignee.value = userStore.username || '' } },
])

function clearFilters() {
  searchKeyword.value = ''
  filterAssignee.value = ''
  filterPriority.value = ''
  filterType.value = ''
}

function applyPreset(p: FilterPreset) {
  activePreset.value = p.name
  p.apply()
}

watch([searchKeyword, filterAssignee, filterPriority, filterType], () => {
  activePreset.value = ''
})

watch(swimlaneMode, (mode) => {
  if (mode) sortColumnsByLane()
  else fetchBoard()
})

function onCreated() { fetchBoard() }

async function fetchBoard() {
  const [boardRes, projRes, reportRes] = await Promise.all([
    getBoard(projectId),
    getProjectById(projectId),
    getReports({ projectId }).catch(() => ({ data: [] })),
  ])
  columns.value = boardRes.data?.columns || []
  projectName.value = projRes.data?.name || ""
  teamId.value = projRes.data?.teamId || 0
  recentReportCount.value = (reportRes.data || []).filter((r: any) => {
    const d = r.reportDate || ''
    // 近7天的报告
    return d >= new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)
  }).length
}

// 快捷操作：新建工作项 / 生成报告
function handleQuickReport(cmd: string) {
  if (cmd === 'issue') {
    showCreateIssue.value = true
  } else {
    const type = cmd === 'daily' ? 'DAILY' : 'WEEKLY'
    generateReport({ projectId, type })
      .then(() => ElMessage.success(`${type === 'DAILY' ? '日报' : '周报'}已生成`))
      .catch(() => {})
  }
}

function columnToStatus(colName: string): string {
  if (colName.includes('待办') || colName.includes('todo')) return 'TODO'
  if (colName.includes('完成') || colName.includes('done')) return 'DONE'
  return 'IN_PROGRESS'
}

async function handleDragChange(evt: any, targetColId: number) {
  // 跨列拖拽：卡片从其他列进入当前列
  if (evt.added) {
    const issue = evt.added.element
    if (!issue?.id) return
    const targetCol = columns.value.find((c: any) => c.id === targetColId)
    if (!targetCol) return
    // 先更新本地状态（乐观更新）
    if (issue.type === 'BUG') {
      issue.bugStatus = bugStatusToColumn(targetCol.name)
    } else {
      issue.status = columnToStatus(targetCol.name)
    }
    try {
      await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: evt.added.newIndex ?? 0 })
    } catch (e: any) {
      ElMessage.error("移动失败，正在恢复...")
      await fetchBoard()
    }
  }
  // 同列内排序：卡片在当前列中改变位置
  else if (evt.moved) {
    const issue = evt.moved.element
    if (!issue?.id) return
    try {
      await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: evt.moved.newIndex ?? 0 })
    } catch (e: any) {
      ElMessage.error("排序失败，正在恢复...")
      await fetchBoard()
    }
  }
}

// BUG 的 bugStatus → 列名映射（反向）— 保留兼容
function bugStatusToColumn(colName: string): string {
  const lower = (colName || '').toLowerCase()
  if (lower.includes('done') || lower.includes('完成')) return 'CLOSED'
  if (lower.includes('progress') || lower.includes('进行')) return 'IN_PROGRESS'
  return 'NEW' // 待办列 → NEW/CONFIRMED
}

// 缺陷在独立状态列间拖拽（更新 bugStatus）
async function handleBugDragChange(evt: any, targetBugStatus: string) {
  if (!evt.added) return
  const issue = evt.added.element
  if (!issue?.id || issue.type !== 'BUG') return
  // 乐观更新
  issue.bugStatus = targetBugStatus
  try {
    await updateIssue({
      id: issue.id,
      bugStatus: targetBugStatus,
      status: targetBugStatus === 'CLOSED' ? 'DONE' : 'IN_PROGRESS',
    })
  } catch {
    ElMessage.error("状态更新失败，正在恢复...")
    await fetchBoard()
  }
}

async function quickComplete(issue: any) {
  try {
    if (issue.type === 'BUG') {
      // 缺陷：标记为已关闭（CLOSED）
      await updateIssue({ id: issue.id, bugStatus: 'CLOSED', status: 'DONE' })
    } else {
      // 故事/任务：找到"完成"列
      const doneCol = columns.value.find((c: any) => {
        const n = (c.name || '').toLowerCase()
        return n.includes('done') || n.includes('完成')
      })
      await updateIssue({
        id: issue.id,
        status: 'DONE',
        columnId: doneCol?.id ?? issue.columnId,
      })
    }
    ElMessage.success(`${issue.issueKey} 已标记完成`)
    await fetchBoard()
  } catch { /* handled */ }
}

async function handleDeleteIssue(issue: any) {
  try {
    await deleteIssue(issue.id)
    ElMessage.success(`${issue.issueKey} 已删除`)
    await fetchBoard()
  } catch { /* handled */ }
}

function handleBoardSync(event: any) {
  if (!event || !event.type) return
  switch (event.type) {
    case "ISSUE_MOVED": {
      let movedIssue: any = null
      for (const col of columns.value) {
        const idx = col.issues?.findIndex((i: any) => i.id === event.issueId)
        if (idx !== undefined && idx >= 0) {
          movedIssue = col.issues.splice(idx, 1)[0]
          break
        }
      }
      if (movedIssue) {
        const targetCol = columns.value.find((c: any) => c.id === event.targetColumnId)
        if (targetCol) {
          movedIssue.columnId = event.targetColumnId
          // BUG 类型同步 bugStatus，其他类型同步 status
          if (movedIssue.type === 'BUG') {
            movedIssue.bugStatus = bugStatusToColumn(targetCol.name)
            movedIssue.status = columnToStatus(targetCol.name)
          } else {
            movedIssue.status = columnToStatus(targetCol.name)
          }
          targetCol.issues?.push(movedIssue)
        }
      }
      break
    }
    case "ISSUE_CREATED": {
      if (event.issue) {
        const col = columns.value.find((c: any) => c.id === event.issue.columnId)
        if (col) col.issues?.push(event.issue)
      }
      break
    }
    case "ISSUE_DELETED": {
      for (const col of columns.value) {
        const idx = col.issues?.findIndex((i: any) => i.id === event.issueId)
        if (idx !== undefined && idx >= 0) {
          col.issues.splice(idx, 1)
          break
        }
      }
      break
    }
  }
}

function openIssue(issue: any) {
  editingIssueId.value = issue.id
  showEditIssue.value = true
}

function checkDeeplink() {
  if (route.query.create === '1') {
    showCreateIssue.value = true
  }
  const q = route.query.issue
  if (q) {
    editingIssueId.value = Number(q)
    showEditIssue.value = true
  }
}

onMounted(async () => {
  await fetchBoard()
  checkDeeplink()
  wsSubscribe(`/topic/board/${projectId}`, handleBoardSync)
})
</script>

<style scoped lang="scss">
.board {
  &__toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    gap: 16px;
    flex-wrap: wrap;
  }

  &__toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__toolbar-right {
    flex-shrink: 0;
  }

  &__toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  &__report-btn {
    position: relative;

    .board__report-badge {
      position: absolute;
      top: -2px;
      right: -6px;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 8px;
      background: var(--color-danger, #ef4444);
      color: #fff;
      font-size: 10px;
      font-weight: 700;
      line-height: 16px;
      text-align: center;
    }
  }

  &__back {
    color: var(--text-secondary);
    &:hover { color: var(--color-primary); }
  }

  &__project-info {
    margin-right: 8px;
  }

  &__title {
    font-size: 20px;
    font-weight: 800;
    color: var(--text-primary);
    letter-spacing: -0.3px;
    line-height: 1.2;
  }

  &__subtitle {
    font-size: 12px;
    color: var(--text-secondary);
  }

  &__view-switcher {
    margin-left: 4px;
  }

  &__filters {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 12px 16px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-lg);
    margin-bottom: 16px;
    box-shadow: var(--shadow-sm);
  }

  &__search {
    width: 220px;
  }

  &__filter-select {
    width: 120px;
    &--sm { width: 100px; }
  }

  &__type-filters {
    display: flex;
    gap: 6px;
  }

  &__presets {
    display: flex;
    gap: 6px;
    margin-left: auto;
  }

  &__preset-btn {
    padding: 5px 12px;
    border-radius: 7px;
    font-size: 12px;
    font-weight: 500;
    border: 1px solid var(--border-color);
    background: var(--bg-page);
    color: var(--text-secondary);
    cursor: pointer;
    transition: all var(--transition-fast);

    &:hover { border-color: var(--color-primary); color: var(--color-primary); }
    &.is-active {
      background: rgba(99, 102, 241, 0.1);
      border-color: var(--color-primary);
      color: var(--color-primary);
      font-weight: 600;
    }
  }

  &__swimlane-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 4px 6px;
    margin-top: 4px;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-regular);
    border-bottom: 1px dashed var(--border-color);
    margin-bottom: 6px;
  }

  &__swimlane-avatar {
    width: 22px;
    height: 22px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    flex-shrink: 0;
  }

  &__swimlane-count {
    margin-left: auto;
    font-size: 10px;
    font-weight: 600;
    color: var(--text-secondary);
    background: var(--bg-card);
    padding: 1px 7px;
    border-radius: 8px;
    border: 1px solid var(--border-color);
  }

  &__type-btn {
    padding: 5px 12px;
    border-radius: 7px;
    font-size: 12px;
    font-weight: 600;
    border: 1px solid var(--border-color);
    background: var(--bg-page);
    color: var(--text-secondary);
    cursor: pointer;
    transition: all var(--transition-fast);

    &:hover { border-color: var(--color-primary); color: var(--color-primary); }
    &.is-active {
      background: var(--color-primary);
      border-color: var(--color-primary);
      color: #fff;
    }
    &--story.is-active { background: var(--type-story); border-color: var(--type-story); }
    &--task.is-active { background: var(--type-task); border-color: var(--type-task); }
    &--bug.is-active { background: var(--type-bug); border-color: var(--type-bug); }
  }

  &__columns {
    display: flex;
    gap: 16px;
    overflow-x: auto;
    min-height: calc(70vh - 80px);
    padding-bottom: 16px;
    align-items: flex-start;
  }

  &__column {
    flex: 0 0 310px;
    background: var(--bg-column);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-lg);
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 220px);
    transition: border var(--transition-fast), box-shadow var(--transition-fast);

    &:hover {
      box-shadow: var(--shadow-sm);
    }
  }

  &__column-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14px 16px 10px;
    border-top: 3px solid;
    border-radius: var(--border-radius-lg) var(--border-radius-lg) 0 0;
  }

  &__column-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__column-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__column-name {
    font-weight: 700;
    font-size: 14px;
    color: var(--text-primary);
  }

  &__column-count {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-secondary);
    background: var(--bg-card);
    padding: 2px 9px;
    border-radius: 10px;
    border: 1px solid var(--border-color);
  }

  &__column-body {
    flex: 1;
    overflow-y: auto;
    padding: 0 12px 12px;
    min-height: 80px;
  }

  &__column-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 32px 16px;
    color: var(--text-placeholder);
    font-size: 13px;

    .el-icon { opacity: 0.5; }
  }

  &__alt-view {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-lg);
    padding: 16px;
    box-shadow: var(--shadow-sm);
  }

  // 拖拽增强视觉反馈
  &__column--drag-over {
    border-color: var(--color-primary) !important;
    box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15), var(--shadow-md) !important;
  }

  // ===== 看板内 Tab 切换 =====
  &__kanban-tabs {
    display: flex;
    gap: 4px;
    margin-bottom: 16px;
    padding: 0 2px;
  }

  &__tab {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 18px;
    border: 1px solid var(--border-color);
    border-radius: 10px;
    background: transparent;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: var(--bg-card);
      color: var(--text-primary);
    }

    &.is-active {
      background: var(--color-primary, #6366f1);
      color: #fff;
      border-color: var(--color-primary, #6366f1);
      box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
    }

    &--bug.is-active {
      background: var(--color-danger, #ef4444);
      border-color: var(--color-danger, #ef4444);
      box-shadow: 0 2px 8px rgba(239, 68, 68, 0.25);
    }
  }

  &__tab-count {
    font-size: 11px;
    opacity: 0.75;
    font-weight: 700;
  }

  // ===== 缺陷独占全宽 =====
  &__section--bug-full {
    .board__columns--bugs {
      gap: 12px;

      .board__column {
        flex: 0 0 calc((100% - 60px) / 6);  /* 6列均分 */
        min-width: 150px;
        max-height: calc(100vh - 280px);

        .board__column-header {
          padding: 10px 12px 8px;
          border-top-width: 3px;
        }

        .board__column-name {
          font-size: 13px;
        }

        .board__column-body {
          padding: 0 8px 8px;
          min-height: 50px;
        }

        .board__column-empty {
          padding: 20px 8px;
          font-size: 11px;
        }
      }
    }
  }

  // 筛选隐藏：用 visibility+height:0 保持元素在文档流中，避免破坏 sortablejs 索引计算
  &__drag-item {
    &.is-hidden {
      visibility: hidden;
      height: 0;
      overflow: hidden;
      padding: 0 !important;
      margin: 0 !important;
      border: none !important;
      pointer-events: none;
    }
  }

  // 底部项目概览栏（关联报告入口）
  &__overview {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-top: 16px;
    padding: 14px 20px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-lg);
    box-shadow: var(--shadow-sm);
  }

  &__overview-stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    cursor: default;

    &:first-of-type {
      cursor: pointer;
      &:hover .board__overview-num { color: var(--color-primary); }
    }
  }

  &__overview-num {
    font-size: 22px;
    font-weight: 800;
    color: var(--text-primary);
    line-height: 1.1;
    transition: color 0.2s;

    &--todo { color: #94a3b8; }
    &--progress { color: #f59e0b; }
    &--done { color: #10b981; }
    &--bug { color: #ef4444; }
  }

  &__overview-label {
    font-size: 11px;
    color: var(--text-secondary);
    font-weight: 500;
  }

  &__overview-divider {
    width: 1px;
    height: 28px;
    background: var(--border-color);
    flex-shrink: 0;
  }

  &__overview-link {
    margin-left: auto;
    font-weight: 600;
    letter-spacing: 0.3px;
  }
}

// 全局拖拽样式（sortable.js 使用，不能用 scoped）
:global(.issue-card-ghost) {
  opacity: 0.4;
  background: rgba(99, 102, 241, 0.06) !important;
  border: 2px dashed var(--color-primary) !important;
  border-radius: var(--border-radius-md) !important;
  transform: scale(0.98);
}

:global(.issue-card-dragging) {
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18), 0 4px 8px rgba(0, 0, 0, 0.08) !important;
  transform: rotate(2deg) scale(1.03);
  opacity: 0.95 !important;
  cursor: grabbing !important;
  z-index: 1000;
}
</style>
