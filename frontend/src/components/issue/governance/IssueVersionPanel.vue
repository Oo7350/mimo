<template>
  <div class="governance-panel">
    <div class="governance-panel__header">
      <div>
        <div class="governance-panel__title">版本与变更</div>
        <div class="governance-panel__sub">
          当前版本 v{{ currentVersion }} · 活跃工作项的需求字段修改必须审批
        </div>
      </div>
      <el-button type="primary" :disabled="!latestVersion" @click="openChangeDialog">
        申请变更
      </el-button>
    </div>

    <el-alert
      v-if="changeRequests.some((item) => item.status === 'PENDING')"
      type="warning"
      :closable="false"
      show-icon
      title="该工作项有待处理的变更申请"
      description="待审批期间不能重复提交新的变更。"
      style="margin-bottom: 14px"
    />

    <section class="governance-section">
      <div class="governance-section__title">变更申请</div>
      <el-empty
        v-if="!changeRequests.length"
        description="暂无变更申请"
        :image-size="60"
      />
      <div v-for="item in changeRequests" :key="item.id" class="request-card">
        <div class="request-card__top">
          <div>
            <b>基于 v{{ item.baseVersion }} 的变更申请</b>
            <span class="muted"> · {{ item.applicantName }} · {{ formatTime(item.createdAt) }}</span>
          </div>
          <div class="request-card__top-right">
            <el-button link type="primary" size="small" @click="openImpact(item)">
              影响分析
            </el-button>
            <el-tag :type="statusType(item.status)" size="small">
              {{ statusLabel(item.status) }}
            </el-tag>
          </div>
        </div>
        <div class="request-card__reason">{{ item.changeReason }}</div>
        <el-alert
          v-if="item.versionConflict && item.status === 'PENDING'"
          type="error"
          :closable="false"
          title="版本已变化，需要驳回后重新提交"
          style="margin: 10px 0"
        />
        <div class="diff-table">
          <div class="diff-row diff-row--head">
            <span>字段</span><span>变更前</span><span>变更后</span>
          </div>
          <div v-for="diff in item.diffs" :key="diff.field" class="diff-row">
            <span>{{ fieldLabel(diff.field) }}</span>
            <span class="diff-before">{{ displayValue(diff.before) }}</span>
            <span class="diff-after">{{ displayValue(diff.after) }}</span>
          </div>
        </div>
        <div v-if="item.reviewNote" class="request-card__note">
          审批说明：{{ item.reviewNote }}
        </div>
        <div v-if="item.status === 'PENDING'" class="request-card__actions">
          <el-button size="small" @click="withdraw(item)">撤回</el-button>
          <el-button size="small" type="success" @click="approve(item)">通过</el-button>
          <el-button size="small" type="danger" @click="reject(item)">驳回</el-button>
        </div>
      </div>
    </section>

    <section class="governance-section">
      <div class="governance-section__title">版本对比</div>
      <div class="version-compare">
        <el-select v-model="fromVersion" placeholder="选择旧版本" style="width: 150px">
          <el-option
            v-for="version in versions"
            :key="version.id"
            :label="`v${version.versionNo}`"
            :value="version.versionNo"
          />
        </el-select>
        <span class="muted">→</span>
        <el-select v-model="toVersion" placeholder="选择新版本" style="width: 150px">
          <el-option
            v-for="version in versions"
            :key="version.id"
            :label="`v${version.versionNo}`"
            :value="version.versionNo"
          />
        </el-select>
        <el-button :disabled="!fromVersion || !toVersion || fromVersion === toVersion" @click="loadDiff">
          对比
        </el-button>
      </div>
      <div v-if="versionDiffs.length" class="diff-table" style="margin-top: 12px">
        <div class="diff-row diff-row--head">
          <span>字段</span><span>旧值</span><span>新值</span>
        </div>
        <div v-for="diff in versionDiffs" :key="diff.field" class="diff-row">
          <span>{{ fieldLabel(diff.field) }}</span>
          <span class="diff-before">{{ displayValue(diff.before) }}</span>
          <span class="diff-after">{{ displayValue(diff.after) }}</span>
        </div>
      </div>
    </section>

    <section class="governance-section">
      <div class="governance-section__title">版本历史</div>
      <el-timeline>
        <el-timeline-item
          v-for="version in versions"
          :key="version.id"
          :timestamp="formatTime(version.createdAt)"
          placement="top"
        >
          <div class="version-item">
            <b>v{{ version.versionNo }}</b>
            <span>{{ version.changeSummary || version.source }}</span>
            <span class="muted"> · {{ version.operatorName }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
    </section>

    <el-dialog v-model="changeDialogVisible" title="提交需求变更" width="620px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="变更理由" required>
          <el-input
            v-model="changeForm.changeReason"
            type="textarea"
            :rows="3"
            placeholder="说明为什么要变更，以及影响范围"
          />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="changeForm.title" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-select v-model="changeForm.priority" style="width: 100%">
                <el-option label="最高" value="HIGHEST" />
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
                <el-option label="最低" value="LOWEST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="截止日期">
              <el-date-picker
                v-model="changeForm.dueDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="故事点">
              <el-input-number v-model="changeForm.storyPoints" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="changeForm.description" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="验收标准（每行一条）">
          <el-input
            v-model="changeForm.acceptanceText"
            type="textarea"
            :rows="4"
            placeholder="每行一条验收标准"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitChange">提交审批</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="impactVisible"
      :title="`变更影响分析${impact ? ' · ' + impact.issueKey : ''}`"
      width="820px"
      append-to-body
    >
      <div v-loading="impactLoading" class="impact-dialog">
        <template v-if="impact">
          <div class="impact-header">
            <div>
              <b>{{ impact.issueTitle }}</b>
              <div class="muted">
                只读计算，不会修改任务、Sprint 或变更申请数据
              </div>
            </div>
            <el-tag :type="riskType(impact.riskLevel)" effect="dark" size="large">
              {{ riskLabel(impact.riskLevel) }}
            </el-tag>
          </div>

          <div class="impact-stats">
            <div class="impact-stat">
              <span>受影响工作项</span>
              <b>{{ impact.affectedIssueCount }}</b>
            </div>
            <div class="impact-stat">
              <span>其中任务</span>
              <b>{{ impact.affectedTaskCount }}</b>
            </div>
            <div class="impact-stat">
              <span>已完成 / 返工风险</span>
              <b>{{ impact.completedTaskCount }}</b>
            </div>
            <div class="impact-stat">
              <span>故事点变化</span>
              <b>{{ signedNumber(impact.storyPointDelta) }}</b>
            </div>
          </div>

          <div class="impact-grid">
            <div class="impact-field">
              <span>Sprint 范围</span>
              <b>
                {{ impact.currentSprintName || '未分配' }}
                <template v-if="impact.sprintScopeChanged">
                  → {{ impact.proposedSprintName || '未分配' }}
                </template>
              </b>
            </div>
            <div class="impact-field">
              <span>截止日期</span>
              <b>
                {{ impact.currentDueDate || '未设置' }}
                <template v-if="impact.currentDueDate !== impact.proposedDueDate">
                  → {{ impact.proposedDueDate || '未设置' }}
                </template>
              </b>
            </div>
            <div class="impact-field">
              <span>进行中 Sprint</span>
              <b>{{ impact.activeSprintAffected ? '受影响' : '不受影响' }}</b>
            </div>
            <div class="impact-field">
              <span>工作量变化</span>
              <b>{{ signedNumber(impact.workloadDelta) }} 故事点</b>
            </div>
          </div>

          <section class="impact-section">
            <div class="impact-section__title">风险原因</div>
            <div v-if="impact.riskReasons.length" class="risk-list">
              <div v-for="reason in impact.riskReasons" :key="reason" class="risk-item">
                <span class="risk-dot"></span>
                {{ reason }}
              </div>
            </div>
            <el-empty v-else description="未识别到明显风险" :image-size="50" />
          </section>

          <section v-if="impact.affectedSprints.length" class="impact-section">
            <div class="impact-section__title">涉及 Sprint</div>
            <div class="sprint-list">
              <el-button
                v-for="sprint in impact.affectedSprints"
                :key="sprint.id"
                size="small"
                :type="sprint.active ? 'warning' : 'default'"
                plain
                @click="openSprint(sprint.id)"
              >
                {{ sprint.name }}
                <span v-if="sprint.active"> · 进行中</span>
              </el-button>
            </div>
          </section>

          <section class="impact-section">
            <div class="impact-section__title">受影响工作项</div>
            <el-table
              v-if="impact.affectedIssues.length"
              :data="impact.affectedIssues"
              size="small"
              max-height="280"
              @row-click="openIssue"
            >
              <el-table-column prop="issueKey" label="编号" width="100" />
              <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="80" />
              <el-table-column label="关系" width="100">
                <template #default="{ row }">{{ relationLabel(row.relation) }}</template>
              </el-table-column>
              <el-table-column label="Sprint" min-width="120">
                <template #default="{ row }">{{ row.sprintName || '未分配' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.completed" type="success" size="small">已完成</el-tag>
                  <span v-else>{{ row.status || '未设置' }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="未发现关联任务或下游依赖项" :image-size="50" />
          </section>
        </template>
      </div>
      <template #footer>
        <el-button @click="impactVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveIssueChangeRequest,
  diffIssueVersions,
  getIssueChangeRequestImpact,
  listIssueChangeRequests,
  listIssueVersions,
  rejectIssueChangeRequest,
  submitIssueChangeRequest,
  withdrawIssueChangeRequest,
} from '@/api/issue-governance'
import type {
  AffectedIssue,
  FieldDiff,
  IssueChangeRequest,
  IssueImpact,
  IssueVersion,
} from '@/types/governance'

const props = defineProps<{ issueId: number; projectId?: number; issue: any }>()
const emit = defineEmits(['updated'])
const router = useRouter()

const versions = ref<IssueVersion[]>([])
const changeRequests = ref<IssueChangeRequest[]>([])
const versionDiffs = ref<FieldDiff[]>([])
const fromVersion = ref<number>()
const toVersion = ref<number>()
const changeDialogVisible = ref(false)
const submitting = ref(false)
const impactVisible = ref(false)
const impactLoading = ref(false)
const impact = ref<IssueImpact>()

const latestVersion = computed(() => versions.value[0])
const currentVersion = computed(() => latestVersion.value?.versionNo || 0)

const changeForm = reactive({
  title: '',
  description: '',
  priority: 'MEDIUM',
  dueDate: '',
  storyPoints: undefined as number | undefined,
  acceptanceText: '',
  changeReason: '',
})

async function load() {
  if (!props.issueId) return
  const [versionRes, changeRes] = await Promise.all([
    listIssueVersions(props.issueId),
    listIssueChangeRequests(props.issueId),
  ])
  versions.value = versionRes.data || []
  changeRequests.value = changeRes.data || []
  if (versions.value.length > 1) {
    toVersion.value = versions.value[0]?.versionNo
    fromVersion.value = versions.value[1]?.versionNo
  } else if (versions.value.length === 1) {
    fromVersion.value = versions.value[0]?.versionNo
    toVersion.value = versions.value[0]?.versionNo
  }
}

function openChangeDialog() {
  Object.assign(changeForm, {
    title: props.issue?.title || '',
    description: props.issue?.description || '',
    priority: props.issue?.priority || 'MEDIUM',
    dueDate: props.issue?.dueDate || '',
    storyPoints: props.issue?.storyPoints ?? undefined,
    acceptanceText: Array.isArray(latestVersion.value?.snapshot?.acceptanceCriteria)
      ? (latestVersion.value?.snapshot?.acceptanceCriteria as any[])
          .map((item) => item?.text || '')
          .filter(Boolean)
          .join('\n')
      : '',
    changeReason: '',
  })
  changeDialogVisible.value = true
}

function normalized(value: unknown) {
  if (value === '' || value === undefined) return null
  return value
}

async function submitChange() {
  if (!latestVersion.value) return
  if (!changeForm.changeReason.trim()) {
    ElMessage.warning('请填写变更理由')
    return
  }
  const snapshot = latestVersion.value.snapshot
  const proposed: Record<string, unknown> = {}
  const fields: Array<keyof typeof changeForm> = [
    'title',
    'description',
    'priority',
    'dueDate',
    'storyPoints',
  ]
  fields.forEach((field) => {
    if (normalized(changeForm[field]) !== normalized(snapshot[field])) {
      proposed[field] = normalized(changeForm[field])
    }
  })
  const existing = Array.isArray(snapshot.acceptanceCriteria)
    ? (snapshot.acceptanceCriteria as any[])
    : []
  const acceptanceCriteria = changeForm.acceptanceText
    .split(/\r?\n/)
    .map((text) => text.trim())
    .filter(Boolean)
    .map((text, index) => ({
      ...(existing[index] || {}),
      id: existing[index]?.id || `ac-${Date.now()}-${index}`,
      text,
      done: Boolean(existing[index]?.done),
    }))
  if (JSON.stringify(acceptanceCriteria) !== JSON.stringify(existing)) {
    proposed.acceptanceCriteria = acceptanceCriteria
  }
  if (!Object.keys(proposed).length) {
    ElMessage.info('没有检测到字段变化')
    return
  }
  submitting.value = true
  try {
    await submitIssueChangeRequest(props.issueId, {
      baseVersion: currentVersion.value,
      proposedChanges: proposed,
      changeReason: changeForm.changeReason.trim(),
    })
    ElMessage.success('变更申请已提交')
    changeDialogVisible.value = false
    await load()
    emit('updated')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function approve(item: IssueChangeRequest) {
  try {
    const result = await ElMessageBox.prompt('可填写审批说明', '通过变更申请', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPlaceholder: '审批说明（可选）',
    })
    await approveIssueChangeRequest(item.id, result.value)
    ElMessage.success('变更已应用')
    await load()
    emit('updated')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '审批失败')
  }
}

async function reject(item: IssueChangeRequest) {
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因', '驳回变更申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
      inputValidator: (value) => Boolean(value?.trim()) || '驳回原因不能为空',
    })
    await rejectIssueChangeRequest(item.id, result.value)
    ElMessage.success('已驳回')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '操作失败')
  }
}

async function withdraw(item: IssueChangeRequest) {
  try {
    await ElMessageBox.confirm('确定撤回这条变更申请吗？', '撤回申请', {
      type: 'warning',
    })
    await withdrawIssueChangeRequest(item.id)
    ElMessage.success('已撤回')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '撤回失败')
  }
}

async function openImpact(item: IssueChangeRequest) {
  impactVisible.value = true
  impactLoading.value = true
  impact.value = undefined
  try {
    const res = await getIssueChangeRequestImpact(item.id)
    impact.value = res.data
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '影响分析加载失败')
    impactVisible.value = false
  } finally {
    impactLoading.value = false
  }
}

function openIssue(issue: AffectedIssue) {
  const projectId = props.projectId || props.issue?.projectId
  if (!projectId) return
  impactVisible.value = false
  router.push({ path: `/projects/${projectId}/board`, query: { issue: issue.id } })
}

function openSprint(sprintId: number) {
  const projectId = props.projectId || props.issue?.projectId
  if (!projectId) return
  impactVisible.value = false
  router.push(`/projects/${projectId}/sprints/${sprintId}/burndown`)
}

async function loadDiff() {
  if (!fromVersion.value || !toVersion.value) return
  const res = await diffIssueVersions(props.issueId, fromVersion.value, toVersion.value)
  versionDiffs.value = res.data || []
}

function statusLabel(status: string) {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[status] || status
}

function statusType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'WITHDRAWN') return 'info'
  return 'warning'
}

function riskLabel(level: string) {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }[level] || level
}

function riskType(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

function relationLabel(relation: string) {
  return {
    SUBTASK: '子任务',
    DEPENDENT: '下游依赖',
    NEW_DEPENDENCY: '新增前置',
  }[relation] || relation
}

function signedNumber(value: number) {
  if (!value) return '0'
  return value > 0 ? `+${value}` : String(value)
}

const fieldNames: Record<string, string> = {
  title: '标题',
  description: '描述',
  priority: '优先级',
  dueDate: '截止日期',
  storyPoints: '故事点',
  sprintId: 'Sprint',
  planStartDate: '计划开始',
  planEndDate: '计划结束',
  dependencies: '前置任务',
  acceptanceCriteria: '验收标准',
  userRole: '用户角色',
  userGoal: '用户目标',
  businessValue: '业务价值',
  epic: '史诗',
}

function fieldLabel(field: string) {
  return fieldNames[field] || field
}

function displayValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '未设置'
  if (Array.isArray(value)) {
    return value
      .map((item: any) => (typeof item === 'object' ? item.text || JSON.stringify(item) : String(item)))
      .join('；')
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

watch(() => props.issueId, load, { immediate: true })
</script>

<style scoped>
.governance-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.governance-panel__title,
.governance-section__title {
  font-weight: 700;
  color: var(--text-primary);
}
.governance-panel__sub,
.muted {
  color: var(--text-secondary);
  font-size: 12px;
}
.governance-section {
  margin-bottom: 22px;
}
.governance-section__title {
  font-size: 14px;
  margin-bottom: 10px;
}
.request-card {
  border: 1px solid var(--border-color-light);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
}
.request-card__top,
.request-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.request-card__top-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.request-card__reason {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-regular);
}
.request-card__note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.request-card__actions {
  justify-content: flex-end;
  margin-top: 10px;
}
.diff-table {
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
  overflow: hidden;
  margin-top: 10px;
}
.diff-row {
  display: grid;
  grid-template-columns: 110px 1fr 1fr;
  gap: 10px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--border-color-light);
  font-size: 12px;
}
.diff-row:last-child {
  border-bottom: 0;
}
.diff-row--head {
  background: var(--bg-subtle);
  font-weight: 600;
}
.diff-before {
  color: #c45656;
}
.diff-after {
  color: #258f67;
}
.version-compare {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.version-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.impact-dialog {
  min-height: 120px;
}
.impact-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color-light);
}
.impact-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0;
}
.impact-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  background: var(--bg-subtle);
}
.impact-stat span,
.impact-field span {
  color: var(--text-secondary);
  font-size: 12px;
}
.impact-stat b {
  font-size: 20px;
  color: var(--text-primary);
}
.impact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.impact-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
}
.impact-field b {
  font-size: 13px;
  color: var(--text-primary);
}
.impact-section {
  margin-top: 18px;
}
.impact-section__title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.risk-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 11px;
  border-radius: 8px;
  color: var(--text-regular);
  background: var(--bg-subtle);
  font-size: 13px;
}
.risk-dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  margin-top: 6px;
  border-radius: 50%;
  background: #e6a23c;
}
.sprint-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 700px) {
  .impact-stats,
  .impact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
