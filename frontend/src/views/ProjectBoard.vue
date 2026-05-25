<template>
  <div class="board">
    <!-- 顶部栏 -->
    <div class="board__header">
      <div class="board__header-left">
        <el-button @click="$router.push('/')" text>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <h3 class="board__title">{{ projectName }}</h3>
      </div>
      <div class="board__header-right">
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
        <el-select v-model="filterType" placeholder="类型" size="small" clearable style="width: 90px">
          <el-option label="故事" value="STORY" />
          <el-option label="任务" value="TASK" />
          <el-option label="缺陷" value="BUG" />
        </el-select>
        <el-button @click="$router.push(`/projects/${projectId}/reports`)">
          <el-icon><Document /></el-icon>报告
        </el-button>
        <el-button id="create-task-btn" type="primary" @click="showCreateIssue = true">
          <el-icon><Plus /></el-icon>新建任务
        </el-button>
      </div>
    </div>

    <!-- 看板列 -->
    <div class="board__columns">
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

    <!-- 新建任务 Dialog -->
    <IssueDetailDialog
      v-model:visible="showCreateIssue"
      :project-id="projectId"
      :team-id="teamId"
      :columns="columns"
      @created="fetchBoard"
    />

    <!-- 编辑任务 Dialog -->
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
import { updateIssue } from "@/api/issue"
import draggable from "vuedraggable"
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue"
import IssueCard from "@/components/issue/IssueCard.vue"
import { Search } from "@element-plus/icons-vue"

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

const assigneeOptions = computed(() => {
  const names = new Set<string>()
  columns.value.forEach((c: any) => c.issues?.forEach((i: any) => {
    if (i.assigneeName) names.add(i.assigneeName)
  }))
  return Array.from(names).sort()
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
    const newStatus = targetCol ? columnToStatus(targetCol.name) : issue.status
    await Promise.all([
      moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: newIndex }),
      updateIssue({ id: issue.id, status: newStatus }),
    ])
    issue.status = newStatus
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
