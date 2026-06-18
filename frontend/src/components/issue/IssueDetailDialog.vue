<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="dialogTitle"
    width="720px"
    destroy-on-close
    class="issue-dialog"
  >
    <el-tabs v-model="activeTab" class="issue-dialog__tabs">
      <!-- Tab 1: Type-specific form -->
      <el-tab-pane :label="tab1Label" name="basic">
        <el-form :model="form" label-position="top">
          <!-- Title + Priority (common across all types) -->
          <el-form-item label="标题" required>
            <el-input v-model="form.title" :placeholder="titlePlaceholder" />
          </el-form-item>

          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="优先级">
                <div style="display: flex; gap: 6px; align-items: center">
                  <el-select v-model="form.priority" style="flex: 1">
                    <el-option label="最高" value="HIGHEST" />
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="低" value="LOW" />
                    <el-option label="最低" value="LOWEST" />
                  </el-select>
                  <el-button
                    size="small"
                    :loading="analyzingPriority"
                    :disabled="!form.title?.trim()"
                    @click="handleAnalyzePriority"
                    style="white-space: nowrap"
                  >AI分析</el-button>
                </div>
                <div v-if="aiPriority" class="ai-priority-card">
                  <div class="ai-priority-card__header">
                    <span>AI 建议优先级</span>
                    <el-tag :type="priorityTagType(aiPriority.priority)" size="small">
                      {{ priorityLabel(aiPriority.priority) }}
                    </el-tag>
                  </div>
                  <div class="ai-priority-card__reason">{{ aiPriority.reason }}</div>
                  <el-button size="small" type="primary" @click="acceptPriority">采纳</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- Type selector (only in create mode, or auto-set) -->
          <div v-if="!isEdit" class="issue-dialog__type-select">
            <el-radio-group v-model="form.type" size="default">
              <el-radio-button value="STORY">
                <el-icon><Notebook /></el-icon> 故事
              </el-radio-button>
              <el-radio-button value="TASK">
                <el-icon><Check /></el-icon> 任务
              </el-radio-button>
              <el-radio-button value="BUG">
                <el-icon><WarningFilled /></el-icon> 缺陷
              </el-radio-button>
            </el-radio-group>
          </div>

          <!-- Status (edit mode) — STORY/TASK 用通用 status，BUG 用 bugStatus -->
          <div v-if="isEdit" style="margin-bottom: 12px">
            <el-form-item v-if="form.type !== 'BUG'" label="状态">
              <el-select v-model="form.status" style="width: 200px" @change="onStatusChange">
                <el-option label="待办" value="TODO" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="DONE" />
              </el-select>
            </el-form-item>
            <el-form-item v-else label="看板列位置">
              <el-select v-model="form.status" style="width: 200px" @change="onStatusChange">
                <el-option label="待办 (NEW)" value="TODO" />
                <el-option label="进行中 (IN_PROGRESS)" value="IN_PROGRESS" />
                <el-option label="已完成 (CLOSED)" value="DONE" />
              </el-select>
              <div style="color: var(--text-secondary); font-size: 11px; margin-top: 4px">
                拖拽卡片到不同列也可自动切换状态
              </div>
            </el-form-item>
          </div>

          <!-- Type-specific form fields -->
          <StoryDialog
            v-if="form.type === 'STORY'"
            :form="form"
            :members="members"
            :is-edit="isEdit"
            @ac-add="onAcAdd"
            @ac-toggle="onAcToggle"
            @ac-remove="onAcRemove"
          />
          <BugDialog
            v-else-if="form.type === 'BUG'"
            ref="bugFormRef"
            :form="form"
            :members="members"
            :is-edit="isEdit"
          />
          <TaskDialog
            v-else
            :form="form"
            :members="members"
            :stories="stories"
          />

          <!-- 甘特图字段 — v2.9.3 -->
          <div class="issue-dialog__gantt-fields">
            <div class="issue-dialog__gantt-title">
              <el-icon><DataLine /></el-icon>
              <span>计划时间（甘特图）</span>
            </div>
            <div class="issue-dialog__gantt-row">
              <el-form-item label="计划起">
                <el-date-picker v-model="form.planStartDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="计划止">
                <el-date-picker v-model="form.planEndDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="依赖 Issue ID">
                <el-input v-model="form.dependencies" placeholder="如 1,5,8 (逗号分隔)" maxlength="200" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </el-tab-pane>

      <!-- Tab 2: Attachments (edit mode) -->
      <el-tab-pane v-if="isEdit && issueId" label="附件" name="attachments">
        <div class="issue-dialog__attachments">
          <div class="issue-dialog__attachments-header">
            <span class="issue-dialog__attachments-title">附件</span>
            <input ref="fileInput" type="file" style="display: none" @change="onFileChange" />
            <el-button size="small" type="primary" text @click="($refs.fileInput as HTMLInputElement)?.click()">
              <el-icon><Upload /></el-icon> 上传附件
            </el-button>
          </div>
          <div v-if="attachments.length === 0" class="issue-dialog__attachments-empty">暂无附件</div>
          <div v-for="att in attachments" :key="att.id" class="issue-dialog__attachment-item">
            <div class="issue-dialog__attachment-info">
              <el-icon><Document /></el-icon>
              <div>
                <div class="issue-dialog__attachment-name">{{ att.fileName }}</div>
                <div class="issue-dialog__attachment-meta">
                  {{ formatSize(att.fileSize) }} · {{ att.uploaderName }} · {{ att.createdAt }}
                </div>
              </div>
            </div>
            <div class="issue-dialog__attachment-actions">
              <el-button size="small" text @click="handleDownload(att.id)">
                <el-icon><Download /></el-icon>
              </el-button>
              <el-button size="small" text type="danger" @click="handleDeleteAttachment(att.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Comments (edit mode) -->
      <el-tab-pane v-if="isEdit && issueId" label="评论" name="comments">
        <CommentSection
          :issue-id="issueId"
          :comments="comments"
          :loading="commentsLoading"
          @comment-added="onCommentAdded"
        />
      </el-tab-pane>

      <!-- Tab 4: Worklog (edit mode) — v2.9.2 -->
      <el-tab-pane v-if="isEdit && issueId" label="工时" name="worklog">
        <WorkLogTab :issue-id="issueId" :current-user-id="currentUserId" />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="isEdit && form.type === 'STORY'" type="success" plain @click="showAiSplitter = true">
        <el-icon><MagicStick /></el-icon>AI 拆分
      </el-button>
      <el-button v-if="isEdit" type="danger" @click="handleDelete" :loading="loading">删除</el-button>
      <el-button type="primary" @click="handleSave" :loading="loading">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>

    <AiTaskSplitter
      v-model:visible="showAiSplitter"
      :project-id="projectId"
      :story-title="form.title"
      :story-description="form.description"
      :acceptance-criteria="(form.acceptanceCriteria || []).map((ac: any) => ac.content).join('\n')"
      :parent-issue-id="form.id"
      @created="emit('updated')"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from "vue"
import { createIssue, getIssueById, updateIssue, deleteIssue, addAcceptanceCriteria, updateAcceptanceCriteria, deleteAcceptanceCriteria } from "@/api/issue"
import { getIssueAttachments, uploadAttachment, downloadAttachment, deleteAttachment } from "@/api/attachment"
import { getCommentsByIssue } from "@/api/comment"
import { getMembers } from "@/api/team"
import { queryIssues } from "@/api/issue"
import { ElMessage, ElMessageBox } from "element-plus"
import { renderMarkdown } from "@/utils/markdown"
import { polishDescription, analyzePriority } from "@/api/ai"
import { TYPE_LABEL, BUG_STATUS_LABEL } from "@/utils/constants"
import StoryDialog from "./story/StoryDialog.vue"
import BugDialog from "./bug/BugDialog.vue"
import TaskDialog from "./task/TaskDialog.vue"
import AiTaskSplitter from "./AiTaskSplitter.vue"
import CommentSection from "./shared/CommentSection.vue"
import WorkLogTab from "./WorkLogTab.vue"
import { useUserStore } from "@/store/user"
import { Notebook, Check, WarningFilled, Upload, Document, Download, Delete, MagicStick, DataLine } from "@element-plus/icons-vue"
import type { IssueCard, AcceptanceCriterion } from "@/types"

const props = defineProps<{
  visible: boolean
  projectId: number
  teamId?: number
  columns: any[]
  issueId?: number
  mode?: string
}>()

const emit = defineEmits(["update:visible", "created", "updated"])

const activeTab = ref("basic")
const loading = ref(false)
const attachments = ref<any[]>([])
const members = ref<any[]>([])
const stories = ref<IssueCard[]>([])
const bugFormRef = ref<any>(null)
const showAiSplitter = ref(false)

// 当前用户（用于工时删除权限）
const userStore = useUserStore()
const currentUserId = computed(() => Number(userStore.userInfo?.id) || 0)

// Comments
const comments = ref<any[]>([])
const commentsLoading = ref(false)

const isEdit = computed(() => props.mode === "edit")

const dialogTitle = computed(() => {
  if (isEdit.value) {
    const type = form.type || 'TASK'
    return `编辑${TYPE_LABEL[type as keyof typeof TYPE_LABEL] || '任务'}`
  }
  return '新建工作项'
})

const tab1Label = computed(() => {
  if (isEdit.value) return '基本信息'
  return '创建'
})

const titlePlaceholder = computed(() => {
  switch (form.type) {
    case 'STORY': return '用户故事标题，如: 作为管理员，我希望导出报表...'
    case 'BUG': return '缺陷标题，如: 登录页面在Chrome下白屏'
    default: return '任务标题'
  }
})

const form = reactive<any>({
  title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
  columnId: null, assigneeId: null, storyPoints: null,
  dueDate: null, description: "", severity: null, stepsToRepro: null,
  labels: [], sprintId: null,
  // 甘特图 — v2.9.3
  planStartDate: null,
  planEndDate: null,
  dependencies: "",   // 逗号分隔的 Issue ID 字符串, e.g. "1,5,8"
  userRole: "", userGoal: "", businessValue: "",
  acceptanceCriteria: [] as AcceptanceCriterion[], epic: "",
  parentId: null,
  bugStatus: null, environment: "",
  expectedResult: "", actualResult: "",
  foundVersion: "", fixedVersion: "",
})

async function loadIssue() {
  if (!props.issueId) return
  const res = await getIssueById(props.issueId)
  const issue = res.data
  Object.assign(form, {
    id: issue.id, title: issue.title, type: issue.type,
    priority: issue.priority, status: issue.status,
    columnId: issue.columnId, assigneeId: issue.assigneeId,
    storyPoints: issue.storyPoints, dueDate: issue.dueDate,
    description: issue.description || "",
    severity: issue.severity, stepsToRepro: issue.stepsToRepro,
    labels: (issue.labels || []).map((l: any) => l.label),
    sprintId: issue.sprintId,
    planStartDate: issue.planStartDate, planEndDate: issue.planEndDate,
    // 后端返回 JSON 字符串, 转换为 UI 用的逗号分隔
    dependencies: (() => {
      if (!issue.dependencies) return ""
      try {
        const arr = JSON.parse(issue.dependencies) as number[]
        return Array.isArray(arr) ? arr.join(",") : ""
      } catch { return "" }
    })(),
    userRole: issue.userRole || "", userGoal: issue.userGoal || "",
    businessValue: issue.businessValue || "",
    acceptanceCriteria: issue.acceptanceCriteria || [],
    epic: issue.epic || "", parentId: issue.parentId,
    bugStatus: issue.bugStatus,
    environment: issue.environment || "",
    expectedResult: issue.expectedResult || "",
    actualResult: issue.actualResult || "",
    foundVersion: issue.foundVersion || "",
    fixedVersion: issue.fixedVersion || "",
  })
  await fetchAttachments()
}

async function fetchAttachments() {
  if (!props.issueId) return
  const res = await getIssueAttachments(props.issueId)
  attachments.value = res.data || []
}

async function fetchComments() {
  if (!props.issueId) return
  commentsLoading.value = true
  try {
    const res = await getCommentsByIssue(props.issueId)
    comments.value = res.data || []
  } catch { comments.value = [] }
  finally { commentsLoading.value = false }
}

function onCommentAdded(comment: any) {
  comments.value.unshift(comment)
}

async function fetchStories() {
  if (!props.projectId) return
  try {
    const res = await queryIssues({ projectId: props.projectId, type: 'STORY', size: 100 })
    stories.value = res.data || []
  } catch { stories.value = [] }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !props.issueId) return
  try {
    await uploadAttachment(props.issueId, file)
    ElMessage.success("附件上传成功")
    await fetchAttachments()
  } finally { input.value = "" }
}

async function handleDownload(attId: number) {
  const res = await downloadAttachment(attId)
  const blob = res.data as Blob
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  const disposition = (res as any).headers?.["content-disposition"]
  const match = disposition?.match(/filename="(.+)"/)
  link.download = match?.[1] || "file"
  link.click()
  window.URL.revokeObjectURL(url)
}

async function handleDeleteAttachment(attId: number) {
  await deleteAttachment(attId)
  ElMessage.success("附件已删除")
  await fetchAttachments()
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + " B"
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB"
  return (bytes / (1024 * 1024)).toFixed(1) + " MB"
}

// ---- AI helpers ----
const polishing = ref(false)
const polishedText = ref("")
const analyzingPriority = ref(false)
const aiPriority = ref<{ priority: string; reason: string } | null>(null)

async function handlePolish() {
  if (!form.description?.trim()) return
  polishing.value = true
  polishedText.value = ""
  try {
    polishedText.value = await polishDescription(form.description.trim())
  } catch (e: any) { ElMessage.error(e.message || "AI 润色失败") }
  finally { polishing.value = false }
}

function applyPolish() {
  form.description = polishedText.value
  polishedText.value = ""
}

async function handleAnalyzePriority() {
  if (!form.title?.trim()) return
  analyzingPriority.value = true
  aiPriority.value = null
  try {
    aiPriority.value = await analyzePriority(form.title.trim(), (form.description || "").trim())
  } catch (e: any) { ElMessage.error(e.message || "AI 分析失败") }
  finally { analyzingPriority.value = false }
}

function acceptPriority() {
  if (aiPriority.value) {
    form.priority = aiPriority.value.priority
    aiPriority.value = null
    ElMessage.success("已采纳 AI 建议优先级")
  }
}

function priorityLabel(p: string) {
  const map: Record<string, string> = { HIGHEST: "最高", HIGH: "高", MEDIUM: "中", LOW: "低", LOWEST: "最低" }
  return map[p] || p
}

function priorityTagType(p: string) {
  if (p === "HIGHEST" || p === "HIGH") return "danger"
  if (p === "MEDIUM") return "warning"
  return "info"
}

async function fetchMembers() {
  if (!props.teamId) return
  try {
    const res = await getMembers(props.teamId)
    members.value = res.data || []
  } catch { members.value = [] }
}

// ---- Watch dialog open ----
watch(() => props.visible, async (v) => {
  if (v) {
    activeTab.value = "basic"
    polishedText.value = ""
    aiPriority.value = null
    fetchMembers()
    if (!isEdit.value) fetchStories()
    Object.assign(form, {
      id: null, title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
      columnId: null, assigneeId: null, storyPoints: null,
      dueDate: null, description: "", severity: null, stepsToRepro: null,
      labels: [], sprintId: null,
      planStartDate: null, planEndDate: null, dependencies: "",
      userRole: "", userGoal: "", businessValue: "",
      acceptanceCriteria: [], epic: "", parentId: null,
      bugStatus: null, environment: "",
      expectedResult: "", actualResult: "",
      foundVersion: "", fixedVersion: "",
    })
    if (isEdit.value) { await loadIssue(); fetchComments() }
  }
})

// ---- Status to column mapping ----
function statusToColumn(status: string): number | null {
  const cols = props.columns || []
  if (cols.length === 0) return null
  for (const c of cols) {
    const name = c.name
    if (status === 'TODO' && (name.includes('待办') || name.includes('todo'))) return c.id
    if (status === 'IN_PROGRESS' && (name.includes('进行') || name.includes('in progress'))) return c.id
    if (status === 'DONE' && (name.includes('完成') || name.includes('done'))) return c.id
  }
  if (status === 'TODO') return cols[0].id
  if (status === 'DONE') return cols[cols.length - 1].id
  if (cols.length >= 2) return cols[1].id
  return cols[0].id
}

function onStatusChange(newStatus: string) {
  const colId = statusToColumn(newStatus)
  if (colId != null) form.columnId = colId
  // BUG 类型：同步 bugStatus
  if (form.type === 'BUG') {
    if (newStatus === 'DONE') form.bugStatus = 'CLOSED'
    else if (newStatus === 'IN_PROGRESS') form.bugStatus = 'IN_PROGRESS'
    else form.bugStatus = 'NEW'
  }
}

// ---- Acceptance Criteria handlers ----
async function onAcAdd(text: string) {
  if (isEdit.value && form.id) {
    const res = await addAcceptanceCriteria(form.id, { text })
    form.acceptanceCriteria = res.data
  } else {
    // Create mode: handle locally
    const ac: AcceptanceCriterion = { id: 'ac-' + Date.now(), text, done: false }
    form.acceptanceCriteria.push(ac)
  }
}

async function onAcToggle(criteriaId: string, done: boolean) {
  if (isEdit.value && form.id) {
    const res = await updateAcceptanceCriteria(form.id, criteriaId, { done })
    form.acceptanceCriteria = res.data
  } else {
    const ac = form.acceptanceCriteria.find((a: AcceptanceCriterion) => a.id === criteriaId)
    if (ac) ac.done = done
  }
}

async function onAcRemove(criteriaId: string) {
  if (isEdit.value && form.id) {
    const res = await deleteAcceptanceCriteria(form.id, criteriaId)
    form.acceptanceCriteria = res.data
  } else {
    form.acceptanceCriteria = form.acceptanceCriteria.filter((a: AcceptanceCriterion) => a.id !== criteriaId)
  }
}

// ---- Save ----
async function handleSave() {
  if (!form.title) { ElMessage.warning("请输入标题"); return }
  loading.value = true
  try {
    const colId = !isEdit.value ? statusToColumn("TODO") : form.columnId

    // Build environment from BugDialog if BUG type
    let env = form.environment
    if (form.type === 'BUG' && bugFormRef.value?.envString) {
      env = bugFormRef.value.envString
    }

    const data: any = {
      ...form,
      projectId: props.projectId,
      columnId: colId,
      environment: env,
    }

    // 甘特图 — v2.9.3: dependencies 转 JSON 数组
    if (data.dependencies && typeof data.dependencies === 'string') {
      const ids = data.dependencies.split(',').map((s: string) => Number(s.trim())).filter((n: number) => !!n)
      data.dependencies = JSON.stringify(ids)
    } else {
      data.dependencies = null
    }

    // BUG 类型：确保 status 和 bugStatus 同步
    if (form.type === 'BUG' && isEdit.value) {
      if (form.status === 'DONE') data.bugStatus = form.bugStatus || 'CLOSED'
      else if (form.status === 'IN_PROGRESS') data.bugStatus = form.bugStatus || 'IN_PROGRESS'
      else data.bugStatus = form.bugStatus || 'NEW'
    }

    // For create mode, strip edit-only fields
    if (!isEdit.value) {
      delete data.id
      delete data.status
      delete data.bugStatus
    }

    if (isEdit.value) {
      await updateIssue(data)
      emit("updated")
    } else {
      await createIssue(data)
      emit("created")
    }
    ElMessage.success(isEdit.value ? "保存成功" : "创建成功")
    emit("update:visible", false)
  } finally { loading.value = false }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定要删除此任务吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }
  try {
    await deleteIssue(form.id)
    ElMessage.success("已删除")
    emit("update:visible", false)
    emit("updated")
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || "删除失败")
  }
}
</script>

<style scoped lang="scss">
.issue-dialog {
  &__tabs { :deep(.el-tabs__header) { margin-bottom: 12px; } }
  &__gantt-fields {
    margin-top: 16px;
    padding: 14px 16px;
    background: linear-gradient(135deg, #f5f3ff 0%, #eef2ff 100%);
    border-radius: 10px;
    border: 1px solid #e0e7ff;
  }
  &__gantt-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #4f46e5;
    margin-bottom: 10px;
  }
  &__gantt-row {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 12px;
  }
  &__type-select { margin-bottom: 12px; }
  &__attachments { margin-top: 0; }
  &__attachments-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  &__attachments-title { font-weight: bold; font-size: 14px; color: var(--text-primary); }
  &__attachments-empty { color: var(--text-secondary); font-size: 13px; }
  &__attachment-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 8px 0; border-bottom: 1px solid var(--border-color-light);
    &:last-child { border-bottom: none; }
  }
  &__attachment-info { display: flex; align-items: center; gap: 8px; }
  &__attachment-name { font-size: 13px; color: var(--text-primary); }
  &__attachment-meta { font-size: 11px; color: var(--text-secondary); }
  &__attachment-actions { display: flex; }
}
.ai-priority-card {
  margin-top: 8px; background: rgba(79,110,246,0.04);
  border: 1px solid rgba(79,110,246,0.2); border-radius: var(--border-radius-md);
  padding: 10px 12px;
  &__header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; span { font-size: 12px; font-weight: 600; color: var(--text-secondary); } }
  &__reason { font-size: 12px; color: var(--text-regular); margin-bottom: 8px; }
}
</style>
