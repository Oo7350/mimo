<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="dialogTitle"
    width="720px"
    destroy-on-close
    class="issue-dialog"
  >
    <!--
      「下一步」条：这个工作项现在处在链路哪一段、下一步该点哪里。
      以前这条链路全靠人记住 —— 验收在 Tab 6、状态在 Tab 1 的下拉框、报告在项目菜单里，
      做完一件事之后界面上没有任何地方告诉你"然后呢"。这里把四段串起来并给出唯一的主动作。
    -->
    <div
      v-if="isEdit && form.id"
      class="next-step"
      :class="'next-step--' + nextStep.tone"
    >
      <div class="next-step__rail">
        <span
          v-for="(node, index) in chain"
          :key="node.key"
          class="next-step__node"
          :class="{ 'is-done': node.done, 'is-active': node.active }"
        >
          {{ node.label }}
          <span v-if="index < chain.length - 1" class="next-step__arrow">→</span>
        </span>
      </div>
      <div class="next-step__body">
        <div class="next-step__title">{{ nextStep.title }}</div>
        <div class="next-step__desc">{{ nextStep.desc }}</div>
      </div>
      <div class="next-step__actions">
        <el-button
          v-if="nextStep.primary"
          type="primary"
          size="small"
          :loading="loading"
          @click="nextStep.primary.action()"
        >{{ nextStep.primary.label }}</el-button>
        <el-button
          v-if="nextStep.secondary"
          size="small"
          plain
          @click="nextStep.secondary.action()"
        >{{ nextStep.secondary.label }}</el-button>
      </div>
    </div>

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
          <el-alert
            v-if="isEdit && changeGoverned"
            type="warning"
            :closable="false"
            show-icon
            title="该工作项已进入 Sprint"
            description="标题、描述、优先级、排期、故事点和验收标准等需求字段需通过「版本与变更」提交审批。状态和负责人仍可直接调整。"
            style="margin-bottom: 14px"
          />
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

          <!-- AI 润色描述：三种类型的描述都绑在 form.description 上，统一放这里一份就够 -->
          <div class="issue-dialog__ai-polish">
            <el-button
              size="small"
              :loading="polishing"
              :disabled="!form.description?.trim()"
              @click="handlePolish"
            >
              <el-icon><MagicStick /></el-icon> AI 润色描述
            </el-button>
            <span class="issue-dialog__ai-polish-hint">把草稿整理成正式表述，应用前可先预览</span>
          </div>
          <div v-if="polishedText" class="ai-polish-preview">
            <div class="ai-polish-preview__header">AI 润色结果</div>
            <div class="ai-polish-preview__body">{{ polishedText }}</div>
            <div class="ai-polish-preview__actions">
              <el-button size="small" @click="polishedText = ''">取消</el-button>
              <el-button size="small" type="primary" @click="applyPolish">应用到描述</el-button>
            </div>
          </div>

          <!-- 甘特图字段 — v2.9.3 -->
          <div class="issue-dialog__gantt-fields">
            <div class="issue-dialog__gantt-title">
              <el-icon><DataLine /></el-icon>
              <span>甘特图排期（用于项目时间线视图）</span>
            </div>
            <div class="issue-dialog__gantt-row">
              <el-form-item label="排期起">
                <el-date-picker v-model="form.planStartDate" type="date" placeholder="计划开始" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="排期止">
                <el-date-picker v-model="form.planEndDate" type="date" placeholder="计划结束" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="前置任务">
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

      <!-- Tab 5: Version and change governance -->
      <el-tab-pane v-if="isEdit && issueId" label="版本与变更" name="governance">
        <IssueVersionPanel
          :issue-id="issueId"
          :project-id="projectId"
          :issue="form"
          @updated="handleGovernanceUpdated"
        />
      </el-tab-pane>

      <!-- Tab 6: Task review loop -->
      <el-tab-pane v-if="isEdit && issueId" label="验收" name="reviews">
        <IssueReviewPanel
          ref="reviewPanelRef"
          :issue-id="issueId"
          @updated="handleGovernanceUpdated"
          @loaded="onReviewsLoaded"
        />
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
import { ref, reactive, watch, computed, nextTick } from "vue"
import { useRouter } from "vue-router"
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
import IssueVersionPanel from "./governance/IssueVersionPanel.vue"
import IssueReviewPanel from "./governance/IssueReviewPanel.vue"
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

// 「下一步」条用到的验收记录：由验收面板加载后回传，详情页不再重复请求一次
const reviewPanelRef = ref<any>(null)
const reviews = ref<any[]>([])
const router = useRouter()

// 当前用户（用于工时删除权限）
const userStore = useUserStore()
const currentUserId = computed(() => Number(userStore.userInfo?.id) || 0)

// Comments
const comments = ref<any[]>([])
const commentsLoading = ref(false)

const isEdit = computed(() => props.mode === "edit")
const changeGoverned = computed(() =>
  isEdit.value && Boolean(form.sprintId) && form.status !== "DONE"
)

// ---- 「下一步」：把 待办 → 进行中 → 待验收 → 已完成 这条链显式化 ----
const latestReview = computed(() => {
  const list = [...reviews.value].sort((a, b) => (b.roundNo || 0) - (a.roundNo || 0))
  return list[0] || null
})

function onReviewsLoaded(list: any[]) {
  reviews.value = list || []
}

/** 四段链路节点：done=已经走过，active=现在就停在这一段 */
const chain = computed(() => {
  const status = form.status
  const reviewStatus = latestReview.value?.status ?? null
  const finished = status === "DONE"
  const reviewPassed = reviewStatus === "APPROVED" || finished
  const reviewPending = reviewStatus === "PENDING"
  return [
    { key: "todo", label: "待办", done: status !== "TODO", active: status === "TODO" },
    {
      key: "doing",
      label: "进行中",
      done: finished || reviewPassed || reviewPending,
      active: status === "IN_PROGRESS" && !reviewPassed && !reviewPending,
    },
    { key: "review", label: "待验收", done: reviewPassed, active: reviewPending },
    { key: "done", label: "已完成", done: false, active: finished },
  ]
})

/**
 * 当前唯一该做的事 + 出口。
 * 顺序很重要：先看验收状态（它比 status 更能说明"卡在哪"），再看 status。
 */
const nextStep = computed<{
  tone: string
  title: string
  desc: string
  primary?: { label: string; action: () => void }
  secondary?: { label: string; action: () => void }
}>(() => {
  const review = latestReview.value
  const status = form.status

  if (status === "DONE") {
    return {
      tone: "done",
      title: "已完成",
      desc: "这条链路走完了。想确认它对交付节奏的影响，去项目报告看这一轮的速率与累积流。",
      primary: { label: "查看项目报告", action: goReports },
      secondary: { label: "回到看板", action: () => emit("update:visible", false) },
    }
  }
  if (review?.status === "PENDING") {
    return {
      tone: "wait",
      title: "等待验收",
      desc: `第 ${review.roundNo} 轮验收已提交，等报告人或项目管理员处理；通过后就可以标记完成。`,
      primary: { label: "去验收处理", action: () => { activeTab.value = "reviews" } },
    }
  }
  if (review?.status === "REJECTED") {
    return {
      tone: "warn",
      title: "被打回，需要返工",
      desc: review.reviewNote ? `打回原因：${review.reviewNote}` : "验收未通过，按意见修改后重新提交。",
      primary: { label: "重新提交验收", action: submitReviewFromNext },
    }
  }
  if (review?.status === "APPROVED") {
    return {
      tone: "ok",
      title: "验收已通过",
      desc: "验收过了，最后一步：把状态改成已完成，这条链路就闭合了。",
      primary: { label: "标记完成", action: () => advanceTo("DONE") },
    }
  }
  if (status === "IN_PROGRESS") {
    return {
      tone: "doing",
      title: "进行中",
      desc: "做完并自测后提交验收，由报告人或项目管理员确认。不想走验收也可以直接标记完成。",
      primary: { label: "提交验收", action: submitReviewFromNext },
      secondary: { label: "直接标记完成", action: () => advanceTo("DONE") },
    }
  }
  return {
    tone: "todo",
    title: "还没开始",
    desc: "点「开始处理」把状态移到进行中，计时也从这一刻开始。",
    primary: { label: "开始处理", action: () => advanceTo("IN_PROGRESS") },
  }
})

/** 推进状态：走和"保存"同一条接口，这样计时（IssueTimerUtil）和看板列同步都不会漏 */
async function advanceTo(status: string) {
  if (!form.id) return
  const previous = form.status
  form.status = status
  onStatusChange(status)
  loading.value = true
  try {
    await updateIssue(buildPayload())
    ElMessage.success(status === "DONE" ? "已标记完成" : "已开始处理")
    emit("updated")
    await loadIssue()
  } catch (error: any) {
    form.status = previous // 失败回滚，界面不能骗人
    onStatusChange(previous)
    ElMessage.error(error?.response?.data?.message || error?.message || "状态更新失败")
  } finally {
    loading.value = false
  }
}

/** 从「下一步」直接拉起提交验收弹窗，省得让用户自己去 Tab 6 找按钮 */
async function submitReviewFromNext() {
  activeTab.value = "reviews"
  await nextTick()
  reviewPanelRef.value?.openSubmitDialog()
}

function goReports() {
  emit("update:visible", false)
  router.push(`/projects/${props.projectId}/reports`)
}

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
    reviews.value = [] // 换工作项时先清空，别让上一条的验收状态影响「下一步」
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
  if (changeGoverned.value) {
    activeTab.value = "governance"
    ElMessage.warning("请通过变更申请修改验收标准")
    return
  }
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
  if (changeGoverned.value) {
    activeTab.value = "governance"
    ElMessage.warning("请通过变更申请修改验收标准")
    return
  }
  if (isEdit.value && form.id) {
    const res = await updateAcceptanceCriteria(form.id, criteriaId, { done })
    form.acceptanceCriteria = res.data
  } else {
    const ac = form.acceptanceCriteria.find((a: AcceptanceCriterion) => a.id === criteriaId)
    if (ac) ac.done = done
  }
}

async function onAcRemove(criteriaId: string) {
  if (changeGoverned.value) {
    activeTab.value = "governance"
    ElMessage.warning("请通过变更申请修改验收标准")
    return
  }
  if (isEdit.value && form.id) {
    const res = await deleteAcceptanceCriteria(form.id, criteriaId)
    form.acceptanceCriteria = res.data
  } else {
    form.acceptanceCriteria = form.acceptanceCriteria.filter((a: AcceptanceCriterion) => a.id !== criteriaId)
  }
}

// ---- Save ----
/**
 * 组装提交载荷。抽出来是因为「保存」和「下一步」里的状态推进必须走**同一份**数据，
 * 否则两条路会在看板列、bugStatus、依赖字段上慢慢分叉。
 */
function buildPayload() {
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
  return data
}

async function handleSave() {
  if (!form.title) { ElMessage.warning("请输入标题"); return }
  loading.value = true
  try {
    const data = buildPayload()

    if (isEdit.value) {
      await updateIssue(data)
      emit("updated")
    } else {
      await createIssue(data)
      emit("created")
    }
    ElMessage.success(isEdit.value ? "保存成功" : "创建成功")
    emit("update:visible", false)
  } catch (error: any) {
    const status = error?.response?.status
    const message = error?.response?.data?.message || error?.message || "保存失败"
    if (status === 409 || String(message).includes("变更申请")) {
      activeTab.value = "governance"
      ElMessage.warning("该工作项已进入 Sprint，请在「版本与变更」中提交变更申请")
    } else {
      ElMessage.error(message)
    }
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

async function handleGovernanceUpdated() {
  if (isEdit.value && props.issueId) {
    await loadIssue()
  }
  emit("updated")
}
</script>

<style scoped lang="scss">
/* 「下一步」条 —— 四段链路 + 当前唯一该做的动作 */
.next-step {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  padding: 12px 14px;
  margin-bottom: 14px;
  border-radius: 10px;
  border: 1px solid var(--border-color-light);
  border-left: 3px solid var(--el-color-primary);
  background: var(--color-primary-lighter, rgba(99, 102, 241, 0.06));

  &--todo { border-left-color: var(--el-color-info); }
  &--doing { border-left-color: var(--el-color-primary); }
  &--wait { border-left-color: var(--el-color-warning); }
  &--warn { border-left-color: var(--el-color-danger); background: rgba(245, 108, 108, 0.06); }
  &--ok { border-left-color: var(--el-color-success); background: rgba(103, 194, 58, 0.06); }
  &--done { border-left-color: var(--el-color-success); }

  &__rail {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    font-size: 12px;
    color: var(--text-secondary);
  }

  &__node {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 2px 8px;
    border-radius: 999px;
    border: 1px solid transparent;

    &.is-done {
      color: var(--el-color-success);
    }

    &.is-active {
      color: var(--el-color-primary);
      font-weight: 600;
      border-color: currentColor;
      background: rgba(255, 255, 255, 0.6);
    }
  }

  &__arrow {
    color: var(--border-color);
  }

  &__body {
    flex: 1 1 260px;
    min-width: 0;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__desc {
    margin-top: 2px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--text-secondary);
  }

  &__actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }
}

.issue-dialog {
  &__tabs { :deep(.el-tabs__header) { margin-bottom: 12px; } }
  &__gantt-fields {
    margin-top: 16px;
    padding: 14px 16px;
    background: var(--color-primary-lighter);
    border-radius: 10px;
    border: 1px solid var(--border-color);
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

.issue-dialog__ai-polish {
  display: flex; align-items: center; gap: 10px; margin: 4px 0 10px;
  flex-wrap: wrap;
}
.issue-dialog__ai-polish-hint {
  font-size: 11px; color: var(--text-muted);
}
.ai-polish-preview {
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-md);
  background: var(--bg-hover);
  padding: 10px 12px;
  margin-bottom: 12px;
  &__header {
    font-size: 12px; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px;
  }
  &__body {
    font-size: 13px; color: var(--text-primary); white-space: pre-wrap;
    max-height: 200px; overflow-y: auto; line-height: 1.6;
  }
  &__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
}
</style>
