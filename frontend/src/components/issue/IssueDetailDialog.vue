<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="mode === 'edit' ? '编辑任务' : '新建任务'"
    width="680px"
    destroy-on-close
    class="issue-dialog"
  >
    <el-tabs v-model="activeTab" class="issue-dialog__tabs">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-form :model="form" label-position="top">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" placeholder="任务标题" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="类型">
                <el-select v-model="form.type" style="width: 100%">
                  <el-option label="故事" value="STORY" />
                  <el-option label="任务" value="TASK" />
                  <el-option label="缺陷" value="BUG" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="优先级">
                <el-select v-model="form.priority" style="width: 100%">
                  <el-option label="最高" value="HIGHEST" />
                  <el-option label="高" value="HIGH" />
                  <el-option label="中" value="MEDIUM" />
                  <el-option label="低" value="LOW" />
                  <el-option label="最低" value="LOWEST" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="状态" v-if="mode === 'edit'">
                <el-select v-model="form.status" style="width: 100%" @change="onStatusChange">
                  <el-option label="待办" value="TODO" />
                  <el-option label="进行中" value="IN_PROGRESS" />
                  <el-option label="已完成" value="DONE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="指派人ID">
                <el-input-number v-model="form.assigneeId" :min="1" style="width: 100%" placeholder="用户ID" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="故事点">
                <el-input-number v-model="form.storyPoints" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="截止日期">
            <el-date-picker v-model="form.dueDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab 2: 详细描述与附件 -->
      <el-tab-pane label="详细描述与附件" name="details">
        <el-form label-position="top">
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细描述..." />
          </el-form-item>

          <template v-if="form.type === 'BUG'">
            <el-form-item label="严重程度">
              <el-select v-model="form.severity" style="width: 100%">
                <el-option label="阻塞" value="BLOCKER" />
                <el-option label="严重" value="CRITICAL" />
                <el-option label="主要" value="MAJOR" />
                <el-option label="次要" value="MINOR" />
                <el-option label="轻微" value="TRIVIAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="复现步骤">
              <el-input v-model="form.stepsToRepro" type="textarea" :rows="3" placeholder="描述复现步骤..." />
            </el-form-item>
          </template>

          <el-form-item label="标签">
            <el-input v-model="labelInput" placeholder="输入标签后回车添加" @keyup.enter="addLabel" />
            <div style="margin-top: 8px; display: flex; gap: 4px; flex-wrap: wrap">
              <el-tag v-for="(l, i) in form.labels" :key="i" closable @close="form.labels.splice(i, 1)">
                {{ l }}
              </el-tag>
            </div>
          </el-form-item>
        </el-form>

        <!-- 附件管理（仅编辑模式） -->
        <div v-if="mode === 'edit' && issueId" class="issue-dialog__attachments">
          <el-divider />
          <div class="issue-dialog__attachments-header">
            <span class="issue-dialog__attachments-title">附件</span>
            <input
              ref="fileInput"
              type="file"
              style="display: none"
              @change="onFileChange"
            />
            <el-button size="small" type="primary" text @click="($refs.fileInput as HTMLInputElement)?.click()">
              <el-icon><Upload /></el-icon> 上传附件
            </el-button>
          </div>
          <div v-if="attachments.length === 0" class="issue-dialog__attachments-empty">暂无附件</div>
          <div
            v-for="att in attachments"
            :key="att.id"
            class="issue-dialog__attachment-item"
          >
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

      <!-- Tab 3: 评论（仅编辑模式） -->
      <el-tab-pane v-if="mode === 'edit' && issueId" label="评论" name="comments">
        <div class="issue-dialog__comments">
          <!-- 评论列表 -->
          <div v-if="commentsLoading" style="text-align: center; padding: 20px; color: var(--text-secondary)">
            加载中...
          </div>
          <div v-else-if="comments.length === 0" class="issue-dialog__comments-empty">
            暂无评论，快来添加第一条吧
          </div>
          <div v-else class="issue-dialog__comments-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <AssigneeAvatar :name="c.username" :size="32" />
              <div class="comment-item__body">
                <div class="comment-item__header">
                  <strong>{{ c.username }}</strong>
                  <span class="comment-item__time">{{ c.createdAt }}</span>
                </div>
                <div class="comment-item__content">{{ c.content }}</div>
              </div>
            </div>
          </div>

          <!-- 评论输入 -->
          <div class="comment-input">
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="2"
              placeholder="输入评论，Ctrl+Enter 发送"
              @keydown="onCommentKeydown"
            />
            <el-button
              type="primary"
              size="small"
              :loading="commentSending"
              @click="sendComment"
              style="margin-top: 8px"
            >
              发送
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="mode === 'edit'" type="danger" @click="handleDelete" :loading="loading">删除</el-button>
      <el-button type="primary" @click="handleSave" :loading="loading">
        {{ mode === 'edit' ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from "vue"
import { createIssue, getIssueById, updateIssue, deleteIssue } from "@/api/issue"
import { getIssueAttachments, uploadAttachment, downloadAttachment, deleteAttachment } from "@/api/attachment"
import { createComment, getCommentsByIssue } from "@/api/comment"
import { ElMessage } from "element-plus"
import AssigneeAvatar from "@/components/common/AssigneeAvatar.vue"

const props = defineProps<{
  visible: boolean
  projectId: number
  columns: any[]
  issueId?: number
  mode?: string
}>()

const emit = defineEmits(["update:visible", "created", "updated"])

const activeTab = ref("basic")
const loading = ref(false)
const labelInput = ref("")
const attachments = ref<any[]>([])

// Comments
const comments = ref<any[]>([])
const commentText = ref("")
const commentSending = ref(false)
const commentsLoading = ref(false)

const form = reactive<any>({
  title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
  columnId: null, assigneeId: null, storyPoints: null,
  dueDate: null, description: "", severity: null, stepsToRepro: null,
  labels: [],
})

async function loadIssue() {
  if (!props.issueId) return
  const res = await getIssueById(props.issueId)
  const issue = res.data
  Object.assign(form, {
    id: issue.id,
    title: issue.title,
    type: issue.type,
    priority: issue.priority,
    status: issue.status,
    columnId: issue.columnId,
    assigneeId: issue.assigneeId,
    storyPoints: issue.storyPoints,
    dueDate: issue.dueDate,
    description: issue.description,
    severity: issue.severity,
    stepsToRepro: issue.stepsToRepro,
    labels: (issue.labels || []).map((l: any) => l.label),
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

async function sendComment() {
  if (!commentText.value.trim() || !props.issueId) return
  commentSending.value = true
  try {
    const res = await createComment({ issueId: props.issueId, content: commentText.value.trim() })
    comments.value.unshift(res.data)
    commentText.value = ""
  } catch { /* handled by interceptor */ }
  finally { commentSending.value = false }
}

function onCommentKeydown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    sendComment()
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !props.issueId) return
  try {
    await uploadAttachment(props.issueId, file)
    ElMessage.success("附件上传成功")
    await fetchAttachments()
  } finally {
    input.value = ""
  }
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

watch(() => props.visible, async (v) => {
  if (v) {
    activeTab.value = "basic"
    Object.assign(form, {
      id: null, title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
      columnId: null, assigneeId: null, storyPoints: null,
      dueDate: null, description: "", severity: null, stepsToRepro: null,
      labels: [],
    })
    if (props.mode === "edit") { await loadIssue(); fetchComments() }
  }
})

// Map status to column by name keyword matching
function statusToColumn(status: string): number | null {
  const cols = props.columns || []
  if (cols.length === 0) return null
  // Match by name keywords first
  for (const c of cols) {
    const name = c.name
    if (status === 'TODO' && (name.includes('待办') || name.includes('todo'))) return c.id
    if (status === 'IN_PROGRESS' && (name.includes('进行') || name.includes('in progress'))) return c.id
    if (status === 'DONE' && (name.includes('完成') || name.includes('done'))) return c.id
  }
  // Fallback to position-based
  if (status === 'TODO') return cols[0].id
  if (status === 'DONE') return cols[cols.length - 1].id
  if (cols.length >= 2) return cols[1].id
  return cols[0].id
}

function onStatusChange(newStatus: string) {
  const colId = statusToColumn(newStatus)
  if (colId != null) form.columnId = colId
}

function addLabel() {
  if (labelInput.value && !form.labels.includes(labelInput.value)) {
    form.labels.push(labelInput.value)
    labelInput.value = ""
  }
}

async function handleSave() {
  if (!form.title) { ElMessage.warning("请输入标题"); return }
  loading.value = true
  try {
    // Create mode: auto-assign to first (TODO) column
    const colId = props.mode !== "edit" ? statusToColumn("TODO") : form.columnId
    const data = { ...form, projectId: props.projectId, columnId: colId }
    if (props.mode === "edit") {
      await updateIssue(data)
      emit("updated")
    } else {
      await createIssue(data)
      emit("created")
    }
    ElMessage.success(props.mode === "edit" ? "保存成功" : "创建成功")
    emit("update:visible", false)
  } finally {
    loading.value = false
  }
}

async function handleDelete() {
  await deleteIssue(form.id)
  ElMessage.success("已删除")
  emit("update:visible", false)
  emit("updated")
}
</script>

<style scoped lang="scss">
.issue-dialog {
  &__tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }
  }

  &__attachments {
    margin-top: 0;
  }

  &__attachments-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  &__attachments-title {
    font-weight: bold;
    font-size: 14px;
    color: var(--text-primary);
  }

  &__attachments-empty {
    color: var(--text-secondary);
    font-size: 13px;
  }

  &__attachment-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid var(--border-color-light);

    &:last-child {
      border-bottom: none;
    }
  }

  &__attachment-info {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__attachment-name {
    font-size: 13px;
    color: var(--text-primary);
  }

  &__attachment-meta {
    font-size: 11px;
    color: var(--text-secondary);
  }

  &__attachment-actions {
    display: flex;
  }

  &__comments {
    min-height: 120px;
  }

  &__comments-empty {
    text-align: center;
    padding: 24px;
    color: var(--text-secondary);
    font-size: 14px;
  }

  &__comments-list {
    max-height: 240px;
    overflow-y: auto;
    margin-bottom: 12px;
  }
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);

  &:last-child { border-bottom: none; }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 4px;

    strong { font-size: 13px; color: var(--text-primary); }
  }

  &__time {
    font-size: 11px;
    color: var(--text-placeholder);
  }

  &__content {
    font-size: 14px;
    color: var(--text-regular);
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.comment-input {
  border-top: 1px solid var(--border-color-light);
  padding-top: 12px;
}
</style>
