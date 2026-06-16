<template>
  <div class="board">
    <!-- 椤堕儴宸ュ叿鏍?-->
    <div class="board__toolbar">
      <div class="board__toolbar-left">
        <el-button class="board__back" @click="$router.push(`/projects/${projectId}`)" text>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="board__project-info">
          <h3 class="board__title">{{ projectName }}</h3>
          <span class="board__subtitle">鐪嬫澘 路 鎷栨嫿鍗＄墖鏇存柊鐘舵€?/span>
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
          <el-icon><Plus /></el-icon>鏂板缓宸ヤ綔椤?        </el-button>
      </div>
    </div>

    <!-- 绛涢€夋爮 -->
    <div v-if="currentView === 'kanban' || currentView === 'buglist'" class="board__filters">
      <el-input
        v-model="searchKeyword"
        placeholder="鎼滅储鏍囬銆佺紪鍙枫€佹寚娲句汉..."
        :prefix-icon="Search"
        clearable
        size="default"
        class="board__search"
      />
      <el-select v-model="filterAssignee" placeholder="鎸囨淳浜? clearable class="board__filter-select">
        <el-option v-for="a in assigneeOptions" :key="a" :label="a" :value="a" />
      </el-select>
      <el-select v-model="filterPriority" placeholder="浼樺厛绾? clearable class="board__filter-select board__filter-select--sm">
        <el-option label="鏈€楂? value="HIGHEST" />
        <el-option label="楂? value="HIGH" />
        <el-option label="涓? value="MEDIUM" />
        <el-option label="浣? value="LOW" />
        <el-option label="鏈€浣? value="LOWEST" />
      </el-select>
      <el-select v-if="currentView === 'kanban'" v-model="swimlaneMode" placeholder="娉抽亾" class="board__filter-select board__filter-select--sm">
        <el-option label="鏃犳吵閬? value="" />
        <el-option label="鎸夋寚娲句汉" value="assignee" />
        <el-option label="鎸夊彶璇? value="epic" />
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
          <el-icon><Document /></el-icon>鎶ュ憡
          <span v-if="recentReportCount > 0" class="board__report-badge">{{ recentReportCount }}</span>
        </el-button>
        <el-dropdown trigger="click" @command="handleQuickReport">
          <el-button type="primary">
            <el-icon><Plus /></el-icon>鏂板缓宸ヤ綔椤?          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="issue">鏂板缓宸ヤ綔椤?/el-dropdown-item>
              <el-dropdown-item command="daily" divided>鐢熸垚浠婃棩鏃ユ姤</el-dropdown-item>
              <el-dropdown-item command="weekly">鐢熸垚鏈懆鍛ㄦ姤</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- Kanban View -->
    <div v-if="currentView === 'kanban'" class="board__kanban">
      <!-- Tab 鍒囨崲鏍?-->
      <div class="board__kanban-tabs">
        <button :class="['board__tab', { 'is-active': kanbanTab === 'tasks' }]" @click="switchKanbanTab('tasks')">
          <el-icon><Grid /></el-icon> 宸ヤ綔椤?          <span class="board__tab-count">{{ taskTotal }}</span>
        </button>
        <button :class="['board__tab', { 'is-active': kanbanTab === 'bugs', 'board__tab--bug': kanbanTab === 'bugs' }]" @click="switchKanbanTab('bugs')">
          <el-icon><WarningFilled /></el-icon> 缂洪櫡
          <span class="board__tab-count">{{ bugTotal }}</span>
        </button>
      </div>

      <!-- 鏁呬簨 & 浠诲姟 鍖哄煙 -->
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
                  />
                </div>
              </template>
            </draggable>

            <div v-if="filteredIssues(col).length === 0" class="board__column-empty">
              <el-icon :size="28"><Box /></el-icon>
              <span>鏆傛棤浠诲姟</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 缂洪櫡鍖哄煙锛堢嫭绔嬬姸鎬佸垪锛岀嫭鍗犲叏瀹斤級 -->
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

    <!-- 椤圭洰姒傝鏍忥紙鐪嬫澘搴曢儴锛屽叧鑱旀姤鍛婏級 -->
    <div v-if="currentView === 'kanban'" class="board__overview">
      <div class="board__overview-stat" @click="$router.push(`/projects/${projectId}/reports`)">
        <span class="board__overview-num">{{ boardStats.total }}</span>
        <span class="board__overview-label">鎬讳换鍔?/span>
      </div>
      <div class="board__overview-divider" />
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--todo">{{ boardStats.todo }}</span>
        <span class="board__overview-label">寰呭姙</span>
      </div>
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--progress">{{ boardStats.inProgress }}</span>
        <span class="board__overview-label">杩涜涓?/span>
      </div>
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--done">{{ boardStats.done }}</span>
        <span class="board__overview-label">宸插畬鎴?/span>
      </div>
      <div class="board__overview-divider" />
      <div class="board__overview-stat">
        <span class="board__overview-num board__overview-num--bug">{{ boardStats.bugs }}</span>
        <span class="board__overview-label">缂洪櫡</span>
      </div>
      <el-button size="small" text type="primary" class="board__overview-link" @click="$router.push(`/projects/${projectId}/reports`)">
        鏌ョ湅鎶ュ憡涓庢暟鎹湅鏉?鈫?      </el-button>
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
import { updateIssue } from "@/api/issue"
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
const activePreset = ref("鍏ㄩ儴")
const currentView = ref<'kanban' | 'buglist' | 'storymap'>('kanban')
const recentReportCount = ref(0)

const columnColors = ['#6366f1', '#f59e0b', '#10b981']

// 缂洪櫡鐙珛鐘舵€佸垪瀹氫箟锛堣櫄鎷熷垪锛屼笉渚濊禆 DB board_columns锛?const BUG_STATUS_FLOW = [
  { key: 'NEW', label: '鏂板缓', color: '#94a3b8' },
  { key: 'CONFIRMED', label: '宸茬‘璁?, color: '#3b82f6' },
  { key: 'IN_PROGRESS', label: '澶勭悊涓?, color: '#f59e0b' },
  { key: 'RESOLVED', label: '宸茶В鍐?, color: '#8b5cf6' },
  { key: 'VERIFIED', label: '宸查獙璇?, color: '#06b6d4' },
  { key: 'CLOSED', label: '宸插叧闂?, color: '#10b981' },
]

// 浠庣湡瀹炲垪涓彁鍙栫己闄峰崱鐗囷紝鎸?bugStatus 鍒嗙粍鍒拌櫄鎷熷垪
const bugColumns = computed(() => {
  // 鏀堕泦鎵€鏈?BUG 绫诲瀷 issue
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

// 鏁呬簨/浠诲姟鍒楋紙杩囨护鎺?BUG 绫诲瀷锛?const taskColumns = computed(() =>
  columns.value.map((col: any) => ({
    ...col,
    issues: (col.issues || []).filter((i: any) => i.type !== 'BUG'),
  }))
)

// 鐪嬫澘鍐?Tab 鍒囨崲
const kanbanTab = ref<'tasks' | 'bugs'>('tasks')
const taskTotal = computed(() => taskColumns.value.reduce((s: number, c: any) => s + (c.issues?.length || 0), 0))
const bugTotal = computed(() => bugColumns.value.reduce((s: number, c: any) => s + (c.issues?.length || 0), 0))

function switchKanbanTab(tab: 'tasks' | 'bugs') {
  kanbanTab.value = tab
  // 鑱斿姩绫诲瀷绛涢€?  filterType.value = tab === 'bugs' ? 'BUG' : ''
}

const views = [
  { key: 'kanban', label: '鐪嬫澘', icon: Grid },
  { key: 'buglist', label: '缂洪櫡鍒楄〃', icon: List },
  { key: 'storymap', label: '鏁呬簨鍦板浘', icon: Connection },
]

const typeFilters = [
  { value: '', label: '鍏ㄩ儴', class: '' },
  { value: 'STORY', label: '鏁呬簨', class: 'board__type-btn--story' },
  { value: 'TASK', label: '浠诲姟', class: 'board__type-btn--task' },
  { value: 'BUG', label: '缂洪櫡', class: 'board__type-btn--bug' },
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

// 鐪嬫澘搴曢儴缁熻锛堝叧鑱旀姤鍛婃暟鎹級
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
  if (swimlaneMode.value === 'assignee') return issue.assigneeName || '鏈寚娲?
  if (swimlaneMode.value === 'epic') {
    if (issue.type === 'STORY') return issue.epic || '鏈垎绫诲彶璇?
    return '鈥?鍏朵粬绫诲瀷 鈥?
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
  { name: '鍏ㄩ儴', apply: () => { clearFilters(); kanbanTab.value = 'tasks' } },
  { name: '缂洪櫡', apply: () => { clearFilters(); filterType.value = 'BUG'; kanbanTab.value = 'bugs' } },
  { name: '鏁呬簨', apply: () => { clearFilters(); filterType.value = 'STORY'; kanbanTab.value = 'tasks' } },
  { name: '鎴戠殑', apply: () => { clearFilters(); filterAssignee.value = userStore.username || '' } },
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
    // 杩?澶╃殑鎶ュ憡
    return d >= new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)
  }).length
}

// 蹇嵎鎿嶄綔锛氭柊寤哄伐浣滈」 / 鐢熸垚鎶ュ憡
function handleQuickReport(cmd: string) {
  if (cmd === 'issue') {
    showCreateIssue.value = true
  } else {
    const type = cmd === 'daily' ? 'DAILY' : 'WEEKLY'
    generateReport({ projectId, type })
      .then(() => ElMessage.success(`${type === 'DAILY' ? '鏃ユ姤' : '鍛ㄦ姤'}宸茬敓鎴恅))
      .catch(() => {})
  }
}

function columnToStatus(colName: string): string {
  if (colName.includes('寰呭姙') || colName.includes('todo')) return 'TODO'
  if (colName.includes('瀹屾垚') || colName.includes('done')) return 'DONE'
  return 'IN_PROGRESS'
}

async function handleDragChange(evt: any, targetColId: number) {
  // 璺ㄥ垪鎷栨嫿锛氬崱鐗囦粠鍏朵粬鍒楄繘鍏ュ綋鍓嶅垪
  if (evt.added) {
    const issue = evt.added.element
    if (!issue?.id) return
    const targetCol = columns.value.find((c: any) => c.id === targetColId)
    if (!targetCol) return
    // 鍏堟洿鏂版湰鍦扮姸鎬侊紙涔愯鏇存柊锛?    if (issue.type === 'BUG') {
      issue.bugStatus = bugStatusToColumn(targetCol.name)
    } else {
      issue.status = columnToStatus(targetCol.name)
    }
    try {
      await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: evt.added.newIndex ?? 0 })
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || "绉诲姩澶辫触"
      ElMessage.error(msg)
      await fetchBoard()
    }
  }
  // 鍚屽垪鍐呮帓搴忥細鍗＄墖鍦ㄥ綋鍓嶅垪涓敼鍙樹綅缃?  else if (evt.moved) {
    const issue = evt.moved.element
    if (!issue?.id) return
    try {
      await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: evt.moved.newIndex ?? 0 })
    } catch (e: any) {
      ElMessage.error("鎺掑簭澶辫触锛屾鍦ㄦ仮澶?..")
      await fetchBoard()
    }
  }
}

// BUG 鐨?bugStatus 鈫?鍒楀悕鏄犲皠锛堝弽鍚戯級鈥?淇濈暀鍏煎
function bugStatusToColumn(colName: string): string {
  const lower = (colName || '').toLowerCase()
  if (lower.includes('done') || lower.includes('瀹屾垚')) return 'CLOSED'
  if (lower.includes('progress') || lower.includes('杩涜')) return 'IN_PROGRESS'
  return 'NEW' // 寰呭姙鍒?鈫?NEW/CONFIRMED
}

// 缂洪櫡鍦ㄧ嫭绔嬬姸鎬佸垪闂存嫋鎷斤紙鏇存柊 bugStatus锛?async function handleBugDragChange(evt: any, targetBugStatus: string) {
  if (!evt.added) return
  const issue = evt.added.element
  if (!issue?.id || issue.type !== 'BUG') return
  // 涔愯鏇存柊
  issue.bugStatus = targetBugStatus
  try {
    await updateIssue({
      id: issue.id,
      bugStatus: targetBugStatus,
      status: targetBugStatus === 'CLOSED' ? 'DONE' : 'IN_PROGRESS',
    })
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || "鐘舵€佹洿鏂板け璐?
    ElMessage.error(msg)
    await fetchBoard()
  }
}

async function quickComplete(issue: any) {
  try {
    if (issue.type === 'BUG') {
      // 缂洪櫡锛氭爣璁颁负宸插叧闂紙CLOSED锛?      await updateIssue({ id: issue.id, bugStatus: 'CLOSED', status: 'DONE' })
    } else {
      // 鏁呬簨/浠诲姟锛氭壘鍒?瀹屾垚"鍒?      const doneCol = columns.value.find((c: any) => {
        const n = (c.name || '').toLowerCase()
        return n.includes('done') || n.includes('瀹屾垚')
      })
      await updateIssue({
        id: issue.id,
        status: 'DONE',
        columnId: doneCol?.id ?? issue.columnId,
      })
    }
    ElMessage.success(`${issue.issueKey} 宸叉爣璁板畬鎴恅)
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
          // BUG 绫诲瀷鍚屾 bugStatus锛屽叾浠栫被鍨嬪悓姝?status
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
    margin-bottom: 18px;
    gap: 16px;
    flex-wrap: wrap;
  }

  &__toolbar-left {
    display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  }

  &__toolbar-right { display: flex; align-items: center; gap: 10px; }

  &__report-btn { position: relative;

    .board__report-badge {
      position: absolute; top: -2px; right: -6px;
      min-width: 16px; height: 16px; padding: 0 4px;
      border-radius: 8px;
      background: var(--color-danger, #ef4444);
      color: #fff; font-size: 10px; font-weight: 700;
      line-height: 16px; text-align: center;
    }
  }

  &__back {
    width: 34px; height: 34px;
    border-radius: 9px;
    display: flex; align-items: center; justify-content: center;
    color: var(--text-secondary); transition: all 0.15s;

    &:hover { background: var(--bg-hover); color: var(--text-primary); }
  }

  &__project-info { margin-right: 8px; }
  &__title { font-size: 20px; font-weight: 750; color: var(--text-primary); letter-spacing: -0.4px; line-height: 1.2; }
  &__subtitle { font-size: 12px; color: var(--text-secondary); margin-top: 1px; }
  &__view-switcher { margin-left: 6px; }

  // 绛涢€夋爮
  &__filters {
    display: flex; align-items: center; gap: 10px;
    flex-wrap: wrap;
    padding: 12px 18px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 11px;
    margin-bottom: 18px;
  }

  &__search { width: 220px; }
  &__filter-select { width: 120px; &--sm { width: 100px; } }
  &__type-filters { display: flex; gap: 6px; }
  &__presets { display: flex; gap: 6px; margin-left: auto; }

  &__preset-btn {
    padding: 5px 12px; border-radius: 7px; font-size: 12px; font-weight: 600;
    border: 1px solid var(--border-color);
    background: var(--bg-page); color: var(--text-secondary);
    cursor: pointer; transition: all 0.15s;

    &:hover { border-color: var(--text-muted); color: var(--text-primary); }
    &.is-active {
      background: var(--bg-subtle); border-color: var(--text-muted);
      color: var(--text-primary); font-weight: 700;
    }
  }

  &__swimlane-header {
    display: flex; align-items: center; gap: 8px;
    padding: 8px 6px 6px; margin-top: 4px;
    font-size: 12px; font-weight: 700;
    color: var(--text-regular);
    border-bottom: 1.5px dashed var(--border-color);
    margin-bottom: 6px;
  }

  &__swimlane-avatar {
    width: 22px; height: 22px; border-radius: 6px;
    display: flex; align-items: center; justify-content: center;
    color: #fff; font-size: 10px; font-weight: 700; flex-shrink: 0;
    background: #f7b955; color: #fff;
  }

  &__swimlane-count {
    margin-left: auto; font-size: 10px; font-weight: 600;
    color: var(--text-secondary);
    background: var(--bg-card); padding: 2px 7px;
    border-radius: 7px; border: 1px solid var(--border-color);
  }

  &__type-btn {
    padding: 5px 12px; border-radius: 7px; font-size: 12px; font-weight: 600;
    border: 1px solid var(--border-color);
    background: var(--bg-page); color: var(--text-secondary);
    cursor: pointer; transition: all 0.15s;

    &:hover { border-color: var(--text-muted); color: var(--text-primary); }
    &.is-active {
      color: #fff !important; font-weight: 700;
    }
    &--story.is-active { background: var(--type-story); border-color: var(--type-story); }
    &--task.is-active { background: var(--type-task); border-color: var(--type-task); }
    &--bug.is-active { background: var(--type-bug); border-color: var(--type-bug); }
  }

  // 鐪嬫澘鍒?  &__columns {
    display: flex; gap: 16px; overflow-x: auto;
    min-height: calc(70vh - 80px); padding-bottom: 16px;
    align-items: flex-start;
  }

  &__column {
    flex: 0 0 310px;
    background: var(--bg-column);
    border: 1px solid var(--border-color);
    border-radius: 11px;
    display: flex; flex-direction: column;
    max-height: calc(100vh - 210px);
    transition: border-color 0.15s;
    overflow: hidden;

    &:hover { border-color: rgba(0,0,0,0.12); }
  }

  &__column-header {
    display: flex; justify-content: space-between; align-items: center;
    padding: 14px 16px 10px;
    border-top: 3px solid;
    border-radius: 11px 11px 0 0;
  }

  &__column-title { display: flex; align-items: center; gap: 8px; }
  &__column-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
  &__column-name { font-weight: 700; font-size: 13.5px; color: var(--text-primary); letter-spacing: -0.15px; }
  &__column-count {
    font-size: 11px; font-weight: 700; color: var(--text-secondary);
    background: var(--bg-page); padding: 2px 9px;
    border-radius: 8px; border: 1px solid var(--border-color);
  }

  &__column-body { flex: 1; overflow-y: auto; padding: 0 12px 12px; min-height: 70px; }

  &__column-empty {
    display: flex; flex-direction: column; align-items: center; gap: 8px;
    padding: 30px 16px; color: var(--text-placeholder); font-size: 12.5px;
    .el-icon { opacity: 0.4; }
  }

  &__alt-view {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 11px; padding: 18px;
  }

  &__column--drag-over { border-color: var(--text-muted) !important; }

  // Tab 鍒囨崲
  &__kanban-tabs { display: flex; gap: 5px; margin-bottom: 18px; padding: 0 2px; }

  &__tab {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 20px;
    border: 1px solid var(--border-color);
    border-radius: 9px;
    background: transparent;
    font-size: 13px; font-weight: 600;
    color: var(--text-secondary);
    cursor: pointer; transition: all 0.15s;

    &:hover { background: var(--bg-hover); color: var(--text-primary); }
    &.is-active { background: var(--text-primary); color: #fff; border-color: var(--text-primary); font-weight: 700; }
    &--bug.is-active { background: var(--color-danger); border-color: var(--color-danger); }
  }

  &__tab-count { font-size: 11px; opacity: 0.75; font-weight: 650; }

  // 缂洪櫡鍏ㄥ鍒?  &__section--bug-full .board__columns--bugs {
    gap: 12px;
    .board__column {
      flex: 0 0 calc((100% - 60px) / 6); min-width: 150px;
      max-height: calc(100vh - 260px);
      .board__column-header { padding: 10px 12px 8px; border-top-width: 3px; }
      .board__column-name { font-size: 12.5px; }
      .board__column-body { padding: 0 8px 8px; min-height: 45px; }
      .board__column-empty { padding: 18px 8px; font-size: 11px; }
    }
  }

  &__drag-item.is-hidden { visibility: hidden; height: 0; overflow: hidden; padding: 0 !important; margin: 0 !important; pointer-events: none; }

  // 搴曢儴姒傝
  &__overview {
    display: flex; align-items: center; gap: 20px;
    margin-top: 18px; padding: 14px 22px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 11px;
  }

  &__overview-stat {
    display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: default;
    &:first-of-type:hover .board__overview-num { color: var(--text-primary); }
  }

  &__overview-num {
    font-size: 24px; font-weight: 800; color: var(--text-primary);
    line-height: 1; letter-spacing: -0.8px; transition: color 0.15s;
    &--todo { color: #94a3b8; }
    &--progress { color: #d97706; }
    &--done { color: #059669; }
    &--bug { color: #dc2626; }
  }

  &__overview-label { font-size: 11px; color: var(--text-muted); font-weight: 550; }
  &__overview-divider { width: 1px; height: 28px; background: var(--border-color); flex-shrink: 0; }
  &__overview-link { margin-left: auto; font-weight: 700; font-size: 12.5px; }
}

// 鎷栨嫿
:global(.issue-card-ghost) {
  opacity: 0.35;
  background: var(--bg-hover) !important;
  border: 2px dashed var(--border-color) !important;
  border-radius: var(--border-radius-md) !important;
}
:global(.issue-card-dragging) { opacity: 0.35 !important; transform: scale(0.98); }
</style>

      min-width: 16px; height: 16px; padding: 0 4px;
      border-radius: 8px;
      background: var(--color-danger, #ef4444);
      color: #fff; font-size: 10px; font-weight: 700;
      line-height: 16px; text-align: center;
      box-shadow: 0 2px 6px rgba(239, 68, 68, 0.5);
    }
  }

  &__back {
    width: 38px; height: 38px;
    border-radius: 11px;
    display: flex; align-items: center; justify-content: center;
