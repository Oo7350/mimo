<template>
  <div class="board">
    <!-- 顶部栏 -->
    <div class="board__header">
      <div class="board__header-left">
        <el-button @click="$router.push('/')" text>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <h3 class="board__title">{{ projectName }}</h3>
        <!-- View switcher -->
        <div class="board__view-switcher">
          <el-button
            :type="currentView === 'kanban' ? 'primary' : ''"
            size="small"
            @click="currentView = 'kanban'"
          >
            <el-icon><Grid /></el-icon> 看板
          </el-button>
          <el-button
            :type="currentView === 'buglist' ? 'primary' : ''"
            size="small"
            @click="currentView = 'buglist'"
          >
            <el-icon><List /></el-icon> 缺陷列表
          </el-button>
          <el-button
            :type="currentView === 'storymap' ? 'primary' : ''"
            size="small"
            @click="currentView = 'storymap'"
          >
            <el-icon><Connection /></el-icon> 故事地图
          </el-button>
        </div>
      </div>
      <div class="board__header-right">
        <template v-if="currentView === 'kanban' || currentView === 'buglist'">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索..."
            :prefix-icon="Search"
            clearable
            size="small"
            style="width: 160px"
          />
          <el-select v-model="filterAssignee" placeholder="指派人" size="small" clearable style="width: 110px">
            <el-option v-for="a in assigneeOptions" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="filterPriority" placeholder="优先级" size="small" clearable style="width: 90px">
            <el-option label="最高" value="HIGHEST" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
            <el-option label="最低" value="LOWEST" />
          </el-select>
          <!-- Type quick filter buttons -->
          <div class="board__type-filters">
            <el-button
              :type="filterType === '' ? 'primary' : ''"
              size="small"
              @click="filterType = ''"
            >全部</el-button>
            <el-button
              :type="filterType === 'STORY' ? 'success' : ''"
              size="small"
              @click="filterType = filterType === 'STORY' ? '' : 'STORY'"
            >故事</el-button>
            <el-button
              :type="filterType === 'TASK' ? '' : ''"
              size="small"
              :class="{ 'is-active': filterType === 'TASK' }"
              @click="filterType = filterType === 'TASK' ? '' : 'TASK'"
              style="border-color: var(--color-primary); color: var(--color-primary);"
              v-if="filterType === '' || filterType === 'TASK'"
              :plain="filterType !== 'TASK'"
            >任务</el-button>
            <el-button
              :type="filterType === 'BUG' ? 'danger' : ''"
              size="small"
              @click="filterType = filterType === 'BUG' ? '' : 'BUG'"
            >缺陷</el-button>
          </div>
          <el-button @click="$router.push(`/projects/${projectId}/reports`)">
            <el-icon><Document /></el-icon>报告
          </el-button>
        </template>
        <el-button id="create-task-btn" type="primary" @click="showCreateIssue = true">
          <el-icon><Plus /></el-icon>新建工作项
        </el-button>
      </div>
    </div>

    <!-- Kanban View -->
    <div v-if="currentView === 'kanban'" class="board__columns">
      <div
        v-for="(col, idx) in columns"
        :key="col.id"
        :class="['board__column', { 'column-drop-target': dragOverColumnId === col.id }]"
        :style="columnBarStyle(idx)"
      >
        <!-- 列头 -->
        <div class="board__column-header">
          <div class="board__column-title">
            <span class="board__column-dot" :style="{ background: col.color }" />
            <span class="board__column-name">{{ col.name }}</span>
            <el-tag size="small" round>{{ filteredIssues(col).length }}</el-tag>
          </div>
        </div>

        <!-- 任务卡片列表 -->
        <draggable
          :list="col.issues"
          group="issues"
          itemKey="id"
          ghost-class="issue-card-ghost"
          drag-class="issue-card-dragging"
          @change="(evt: any) => handleDragChange(evt, col.id)"
          @start="() => dragOverColumnId = col.id"
          @end="() => dragOverColumnId = null"
          class="board__column-body"
        >
          <template #item="{ element }">
            <div v-if="issueMatchesFilter(element)">
              <IssueCard
                :issue="element"
                @click="openIssue(element)"
                @edit="openIssue(element)"
              />
            </div>
          </template>
        </draggable>
      </div>
    </div>

    <!-- Bug Table View -->
    <div v-else-if="currentView === 'buglist'">
      <BugTableView
        :bugs="allBugs"
        :search-keyword="searchKeyword"
        @select="openIssue"
        @create-bug="showCreateIssue = true"
      />
    </div>

    <!-- Story Map View -->
    <div v-else-if="currentView === 'storymap'">
      <StoryMap />
    </div>

    <!-- 新建/编辑 Dialog -->
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
import { ref, computed, onMounted } from "vue"
import { useRoute } from "vue-router"
import { getBoard, moveIssue } from "@/api/board"
import { getProjectById } from "@/api/project"
import draggable from "vuedraggable"
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue"
import IssueCard from "@/components/issue/IssueCard.vue"
import BugTableView from "@/components/issue/bug/BugTableView.vue"
import StoryMap from "@/components/issue/story/StoryMap.vue"
import { Search, Grid, List, Connection } from "@element-plus/icons-vue"
import { useWebSocket } from "@/composables/useWebSocket"

const route = useRoute()
const projectId = Number(route.params.id)
const projectName = ref("")
const teamId = ref<number>(0)
const columns = ref<any[]>([])
const showCreateIssue = ref(false)
const showEditIssue = ref(false)
const editingIssueId = ref<number>(0)
const dragOverColumnId = ref<number | null>(null)
const searchKeyword = ref("")
const filterAssignee = ref("")
const filterPriority = ref("")
const filterType = ref("")
const currentView = ref<'kanban' | 'buglist' | 'storymap'>('kanban')

const { subscribe: wsSubscribe, unsubscribe: wsUnsubscribe } = useWebSocket()

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

function columnBarStyle(idx: number) {
  const colors = ['var(--color-primary)', 'var(--color-warning)', 'var(--color-success)']
  return { borderTop: `3px solid ${colors[idx] || colors[0]}` }
}

function onCreated() {
  fetchBoard()
}

async function fetchBoard() {
  const [boardRes, projRes] = await Promise.all([
    getBoard(projectId),
    getProjectById(projectId),
  ])
  columns.value = boardRes.data?.columns || []
  projectName.value = projRes.data?.name || ""
  teamId.value = projRes.data?.teamId || 0
}

function columnToStatus(colName: string): string {
  if (colName.includes('待办') || colName.includes('todo')) return 'TODO'
  if (colName.includes('完成') || colName.includes('done')) return 'DONE'
  return 'IN_PROGRESS'
}

async function handleDragChange(evt: any, targetColId: number) {
  if (evt.added) {
    const issue = evt.added.element
    const newIndex = evt.added.newIndex
    const targetCol = columns.value.find((c: any) => c.id === targetColId)
    // Optimistic UI: update status locally immediately
    if (targetCol) {
      issue.status = columnToStatus(targetCol.name)
    }
    // Backend handles status sync + activity log + WebSocket broadcast
    await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: newIndex })
  }
}

function handleBoardSync(event: any) {
  if (!event || !event.type) return
  switch (event.type) {
    case "ISSUE_MOVED": {
      // Remove from all columns, then add to target
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
          movedIssue.status = columnToStatus(columns.value.find((c: any) => c.id === event.targetColumnId)?.name || "")
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

// Task deeplink: ?issue=123 opens the issue dialog
function checkDeeplink() {
  const q = route.query.issue
  if (q) {
    editingIssueId.value = Number(q)
    showEditIssue.value = true
  }
}

onMounted(async () => {
  await fetchBoard()
  checkDeeplink()
  // Subscribe to board real-time sync
  wsSubscribe(`/topic/board/${projectId}`, handleBoardSync)
})
</script>

<style scoped lang="scss">
.board {
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__view-switcher {
    display: flex;
    gap: 4px;
    margin-left: 12px;
  }

  &__type-filters {
    display: flex;
    gap: 4px;
  }

  &__title {
    font-size: 18px;
    font-weight: bold;
    color: var(--text-primary);
  }

  &__header-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  &__columns {
    display: flex;
    gap: 16px;
    overflow-x: auto;
    min-height: 70vh;
    padding-bottom: 16px;
    align-items: flex-start;
  }

  &__column {
    flex: 0 0 300px;
    background: var(--bg-column);
    border-radius: var(--border-radius-md);
    padding: 12px;
    transition: border var(--transition-fast), background var(--transition-fast);
  }

  &__column-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  &__column-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__column-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    display: inline-block;
    flex-shrink: 0;
  }

  &__column-name {
    font-weight: bold;
    color: var(--text-primary);
  }

  &__column-body {
    min-height: 60px;
  }
}
</style>
