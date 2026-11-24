<!--
  SprintView — 项目下 Sprint 管理 + 燃尽图
  /projects/:id/sprints
-->
<template>
  <div class="sprint-page">
    <div class="sprint-page__hero">
      <el-button @click="$router.push(`/projects/${projectId}`)" text class="sprint-page__back">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="sprint-page__hero-info">
        <h2 class="sprint-page__title">Sprint 管理</h2>
        <p class="sprint-page__subtitle">规划迭代、跟踪燃尽、按时交付</p>
      </div>
      <RippleButton variant="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 新建 Sprint
      </RippleButton>
    </div>

    <div v-if="loading" class="sprint-page__loading">
      <SkeletonLoader variant="card" :count="3" />
    </div>

    <div v-else-if="sprints.length === 0" class="sprint-page__empty">
      <div class="sprint-page__empty-icon">🚀</div>
      <h3>还没有 Sprint</h3>
      <p>创建你的第一个 Sprint，开始敏捷迭代</p>
      <RippleButton variant="primary" size="large" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 创建 Sprint
      </RippleButton>
    </div>

    <div v-else class="sprint-page__grid">
      <!-- 左侧：Sprint 列表 -->
      <div class="sprint-list">
        <div
          v-for="s in sprints"
          :key="s.id"
          :class="['sprint-card', { 'sprint-card--active': selectedId === s.id }]"
          @click="selectSprint(s.id)"
        >
          <div class="sprint-card__header">
            <span :class="['sprint-card__status', `sprint-card__status--${s.status?.toLowerCase()}`]">
              {{ statusLabel(s.status) }}
            </span>
            <span class="sprint-card__date">{{ formatDate(s.startDate) }} ~ {{ formatDate(s.endDate) }}</span>
          </div>
          <h4 class="sprint-card__name">{{ s.name }}</h4>
          <p v-if="s.goal" class="sprint-card__goal">{{ s.goal }}</p>
          <div class="sprint-card__meta">
            <span><el-icon><Tickets /></el-icon>{{ s.totalIssues ?? 0 }} 任务</span>
            <span><el-icon><CircleCheck /></el-icon>{{ s.completedIssues ?? 0 }} 已完成</span>
            <span v-if="s.totalIssues">
              <el-icon><Aim /></el-icon>{{ Math.round(((s.completedIssues ?? 0) / s.totalIssues) * 100) }}%
            </span>
          </div>
          <div class="sprint-card__actions" @click.stop>
            <el-button
              v-if="s.status === 'PLANNING'"
              size="small"
              type="success"
              plain
              @click="openSprintPlanner(s)"
            >
              <el-icon><MagicStick /></el-icon>AI 规划
            </el-button>
            <el-button
              v-if="s.status === 'PLANNING'"
              size="small"
              type="primary"
              :loading="startingId === s.id"
              @click="handleStart(s.id)"
            >启动</el-button>
            <el-button
              v-if="s.status === 'ACTIVE'"
              size="small"
              type="success"
              :loading="completingId === s.id"
              @click="handleComplete(s.id)"
            >完成</el-button>
            <el-button
              v-if="s.status === 'ACTIVE'"
              size="small"
              :loading="snapshottingId === s.id"
              @click="handleSnapshot(s.id)"
              title="手动生成今日燃尽快照"
            >快照</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：燃尽图 -->
      <div class="sprint-detail">
        <div v-if="!selectedSprint" class="sprint-detail__empty">
          <el-icon size="48" color="#c0c4cc"><DataLine /></el-icon>
          <p>从左侧选择一个 Sprint 查看燃尽图</p>
        </div>
        <template v-else>
          <div class="sprint-detail__header">
            <div>
              <h3 class="sprint-detail__title">{{ selectedSprint.name }}</h3>
              <p v-if="selectedSprint.goal" class="sprint-detail__goal">{{ selectedSprint.goal }}</p>
            </div>
            <div class="sprint-detail__stats">
              <div class="sprint-detail__stat">
                <div class="sprint-detail__stat-value">{{ burndownData?.totalPoints ?? 0 }}</div>
                <div class="sprint-detail__stat-label">总故事点</div>
              </div>
              <div class="sprint-detail__stat">
                <div class="sprint-detail__stat-value">{{ formatDate(selectedSprint.startDate) }}</div>
                <div class="sprint-detail__stat-label">开始</div>
              </div>
              <div class="sprint-detail__stat">
                <div class="sprint-detail__stat-value">{{ formatDate(selectedSprint.endDate) }}</div>
                <div class="sprint-detail__stat-label">结束</div>
              </div>
              <div class="sprint-detail__stat">
                <div :class="['sprint-detail__stat-value', trendClass]">{{ trendLabel }}</div>
                <div class="sprint-detail__stat-label">燃尽趋势</div>
              </div>
            </div>
          </div>

          <div
            v-if="healthData"
            v-loading="healthLoading"
            :class="['sprint-health', `sprint-health--${healthData.healthLevel.toLowerCase()}`]"
          >
            <div class="sprint-health__head">
              <div class="sprint-health__score-wrap">
                <div class="sprint-health__score">{{ healthData.healthScore }}</div>
                <div>
                  <div class="sprint-health__eyebrow">Sprint 健康度</div>
                  <div class="sprint-health__status-line">
                    <el-tag :type="healthTagType(healthData.healthLevel)" effect="dark" size="small">
                      {{ healthLabel(healthData.healthLevel) }}
                    </el-tag>
                    <span>更新于 {{ formatHealthTime(healthData.calculatedAt) }}</span>
                  </div>
                </div>
              </div>
              <div class="sprint-health__actions">
                <el-button size="small" :loading="healthLoading" @click="refreshHealth">
                  刷新指标
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :loading="aiLoading"
                  @click="loadAiSummary"
                >
                  生成 AI 解读
                </el-button>
              </div>
            </div>

            <div class="health-progress">
              <div class="health-progress__row">
                <div class="health-progress__label">
                  <span>时间进度</span>
                  <b>{{ healthData.elapsedDays }}/{{ healthData.totalDays }} 天</b>
                </div>
                <div class="health-progress__track">
                  <div
                    class="health-progress__bar health-progress__bar--time"
                    :style="{ width: `${healthData.timeProgress}%` }"
                  />
                </div>
                <span class="health-progress__value">{{ healthData.timeProgress }}%</span>
              </div>
              <div class="health-progress__row">
                <div class="health-progress__label">
                  <span>工作进度</span>
                  <b>
                    {{
                      healthData.trackingBasis === 'STORY_POINTS'
                        ? `${healthData.completedStoryPoints}/${healthData.totalStoryPoints} 故事点`
                        : `${healthData.completedIssues}/${healthData.totalIssues} 任务`
                    }}
                  </b>
                </div>
                <div class="health-progress__track">
                  <div
                    class="health-progress__bar health-progress__bar--work"
                    :style="{ width: `${healthData.workProgress}%` }"
                  />
                </div>
                <span class="health-progress__value">{{ healthData.workProgress }}%</span>
              </div>
            </div>

            <div class="health-metrics">
              <div class="health-metric">
                <span>剩余故事点</span>
                <b>{{ healthData.remainingStoryPoints }}</b>
              </div>
              <div class="health-metric">
                <span>预计延期</span>
                <b :class="{ 'health-metric__danger': healthData.estimatedDelayDays > 0 }">
                  {{ healthData.estimatedDelayDays }} 天
                </b>
              </div>
              <div class="health-metric">
                <span>逾期任务</span>
                <b :class="{ 'health-metric__danger': healthData.overdueCount > 0 }">
                  {{ healthData.overdueCount }}
                </b>
              </div>
              <div class="health-metric">
                <span>阻塞任务</span>
                <b :class="{ 'health-metric__danger': healthData.blockedCount > 0 }">
                  {{ healthData.blockedCount }}
                </b>
              </div>
              <div class="health-metric">
                <span>待验收</span>
                <b>{{ healthData.pendingReviewCount }}</b>
              </div>
            </div>

            <el-collapse v-model="healthRiskOpen" class="health-collapse">
              <el-collapse-item name="risk">
                <template #title>
                  风险原因
                  <el-tag size="small" type="info">{{ healthData.riskReasons.length }}</el-tag>
                </template>
                <div class="health-reasons">
                  <div v-for="reason in healthData.riskReasons" :key="reason">
                    <span></span>{{ reason }}
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>

            <div class="health-suggestions">
              <div class="health-suggestions__title">建议动作</div>
              <div v-for="(suggestion, index) in healthData.suggestions" :key="suggestion">
                <span>{{ index + 1 }}</span>{{ suggestion }}
              </div>
            </div>

            <el-alert
              v-if="aiSummary"
              :type="aiSummary.aiAvailable ? 'info' : 'warning'"
              :closable="false"
              class="health-ai"
            >
              <template #title>
                AI 解读
                <span v-if="!aiSummary.aiAvailable">（已回退到确定性建议）</span>
              </template>
              <div class="health-ai__content">{{ aiSummary.summary }}</div>
            </el-alert>
          </div>

          <div class="sprint-detail__chart-wrap">
            <BurndownChart :data="burndownData" />
          </div>

          <!-- 团队工时分布 -->
          <div v-if="workloadData" class="sprint-detail__workload">
            <div class="sprint-detail__workload-header">
              <h4>团队工时分布</h4>
              <span class="sprint-detail__workload-total">合计 {{ workloadData.totalHours }} h</span>
            </div>
            <div v-if="workloadData.members.length === 0" class="sprint-detail__workload-empty">
              该 Sprint 下还没有工时记录
            </div>
            <div v-else class="sprint-detail__workload-list">
              <div v-for="m in workloadData.members" :key="m.userId" class="wl-row">
                <div class="wl-row__avatar" :style="{ background: avatarGradient(m.username || '?', 'user') }">
                  {{ (m.username || '?').charAt(0).toUpperCase() }}
                </div>
                <span class="wl-row__name">{{ m.username || '未知' }}</span>
                <span class="wl-row__count">{{ m.issueCount }} 任务 · {{ m.logCount }} 记录</span>
                <div class="wl-row__bar-wrap">
                  <div class="wl-row__bar" :style="{ width: workloadBarWidth(m.totalHours) + '%' }" />
                </div>
                <span class="wl-row__hours">{{ Number(m.totalHours).toFixed(1) }} h</span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 新建 Sprint 对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建 Sprint" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-position="top">
        <el-form-item label="Sprint 名称" prop="name">
          <el-input v-model="createForm.name" placeholder="例如：Sprint 12" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="Sprint 目标" prop="goal">
          <el-input v-model="createForm.goal" type="textarea" :rows="3" placeholder="本次迭代要达成的目标" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="起止时间" prop="dateRange">
          <el-date-picker
            v-model="createForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="planDrawerVisible"
      :title="`AI Sprint 规划${planSprint ? ' · ' + planSprint.name : ''}`"
      size="780px"
      append-to-body
    >
      <div v-loading="planLoading" class="planner">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="AI 仅提供候选建议，不会自动写入 Sprint"
          description="请核对任务、故事点和依赖后，手动勾选并点击「加入选中任务」。"
        />

        <div class="planner__toolbar">
          <div>
            <span>本次容量</span>
            <el-input-number v-model="planCapacity" :min="1" :max="200" />
            <span>故事点</span>
          </div>
          <el-button type="primary" plain :loading="planLoading" @click="generatePlan">
            重新生成建议
          </el-button>
        </div>

        <template v-if="planData">
          <div class="planner__summary">
            <div>
              <span>历史 Velocity</span>
              <b>{{ planData.historicalVelocity }}</b>
            </div>
            <div>
              <span>Backlog 总量</span>
              <b>{{ planData.availablePoints }}</b>
            </div>
            <div>
              <span>推荐点数</span>
              <b>{{ planData.totalRecommendedPoints }}</b>
            </div>
            <div>
              <span>当前已选</span>
              <b :class="{ 'planner__danger': selectedPlanPoints > effectivePlanCapacity }">
                {{ selectedPlanPoints }}
              </b>
            </div>
          </div>

          <el-alert
            :type="planData.aiAvailable ? 'success' : 'warning'"
            :closable="false"
            class="planner__ai-state"
            :title="planData.aiAvailable ? 'AI 建议已生成' : 'AI 暂不可用，已使用规则建议'"
            :description="planData.summary"
          />

          <div v-if="planData.risks.length" class="planner__risks">
            <div class="planner__section-title">规划风险</div>
            <div v-for="risk in planData.risks" :key="risk" class="planner__risk">
              <span></span>{{ risk }}
            </div>
          </div>

          <div class="planner__section-title">
            Backlog 候选
            <span>{{ planData.candidates.length }} 项</span>
          </div>
          <el-empty
            v-if="!planData.candidates.length"
            description="Backlog 暂无可规划工作项"
            :image-size="60"
          />
          <el-checkbox-group v-else v-model="selectedIssueIds" class="planner__list">
            <el-checkbox
              v-for="candidate in planData.candidates"
              :key="candidate.id"
              :label="candidate.id"
              class="planner-item"
            >
              <div class="planner-item__body">
                <div class="planner-item__title">
                  <span class="planner-item__key">{{ candidate.issueKey }}</span>
                  <b>{{ candidate.title }}</b>
                  <el-tag
                    v-if="recommendedIds.has(candidate.id)"
                    size="small"
                    type="success"
                    effect="plain"
                  >
                    推荐
                  </el-tag>
                  <el-tag v-if="candidate.blocked" size="small" type="danger" effect="plain">
                    依赖未完成
                  </el-tag>
                </div>
                <div class="planner-item__meta">
                  <span>{{ priorityLabel(candidate.priority) }}</span>
                  <span>{{ candidate.storyPoints ?? 0 }} 点</span>
                  <span v-if="candidate.dueDate">截止 {{ candidate.dueDate }}</span>
                  <span v-if="candidate.dependencyIssueKeys.length">
                    前置：{{ candidate.dependencyIssueKeys.join('、') }}
                  </span>
                </div>
                <div v-if="recommendedReasons[candidate.id]" class="planner-item__reason">
                  {{ recommendedReasons[candidate.id] }}
                </div>
              </div>
            </el-checkbox>
          </el-checkbox-group>
        </template>
      </div>

      <template #footer>
        <div class="planner__footer">
          <div>
            已选 {{ selectedIssueIds.length }} 项 · {{ selectedPlanPoints }} 故事点
            <span v-if="selectedPlanPoints > effectivePlanCapacity" class="planner__danger">
              已超过容量
            </span>
          </div>
          <div>
            <el-button @click="planDrawerVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="applyingPlan"
              :disabled="!selectedIssueIds.length"
              @click="applyPlan"
            >
              加入选中任务
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  ArrowLeft, Plus, Tickets, CircleCheck, Aim, DataLine, MagicStick,
} from '@element-plus/icons-vue'
import {
  getSprints, getBurndown, createSprint,
  startSprint, completeSprint, takeSnapshot,
  getSprintHealth, getSprintHealthAiSummary,
  suggestSprintPlan, applySprintPlan,
  type SprintVO, type SprintHealthVO, type SprintHealthAiSummaryVO,
  type SprintPlanVO,
} from '@/api/sprint'
import { getSprintWorkloadSummary, type SprintWorkloadVO } from '@/api/worklog'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import RippleButton from '@/components/common/RippleButton.vue'
import BurndownChart from '@/components/common/BurndownChart.vue'
import { avatarGradient } from '@/utils/color'
import { useWebSocket } from '@/composables/useWebSocket'

const route = useRoute()
const projectId = Number(route.params.id)

const loading = ref(true)
const sprints = ref<SprintVO[]>([])
const selectedId = ref<number | null>(null)
const burndownData = ref<any>(null)
const burndownLoading = ref(false)
const workloadData = ref<SprintWorkloadVO | null>(null)
const healthData = ref<SprintHealthVO | null>(null)
const healthLoading = ref(false)
const aiLoading = ref(false)
const aiSummary = ref<SprintHealthAiSummaryVO | null>(null)
const healthRiskOpen = ref<string[]>(['risk'])
const { subscribe: wsSubscribe, unsubscribe: wsUnsubscribe } = useWebSocket()
const boardTopic = `/topic/board/${projectId}`
let healthTimer: ReturnType<typeof setInterval> | undefined
const planDrawerVisible = ref(false)
const planLoading = ref(false)
const applyingPlan = ref(false)
const planSprint = ref<SprintVO | null>(null)
const planData = ref<SprintPlanVO | null>(null)
const planCapacity = ref<number>()
const selectedIssueIds = ref<number[]>([])

const showCreateDialog = ref(false)
const creating = ref(false)
const startingId = ref<number | null>(null)
const completingId = ref<number | null>(null)
const snapshottingId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  name: '',
  goal: '',
  dateRange: [] as string[],
})
const createRules: FormRules = {
  name: [{ required: true, message: '请输入 Sprint 名称', trigger: 'blur' }],
  dateRange: [{ required: true, message: '请选择起止时间', trigger: 'change' }],
}

const selectedSprint = computed(() => sprints.value.find(s => s.id === selectedId.value))
const recommendedIds = computed(() =>
  new Set((planData.value?.recommendedTasks || []).map((task) => task.id)),
)
const recommendedReasons = computed<Record<number, string>>(() =>
  Object.fromEntries(
    (planData.value?.recommendedTasks || []).map((task) => [task.id, task.reason]),
  ),
)
const selectedPlanPoints = computed(() => {
  const points = new Map(
    (planData.value?.candidates || []).map((candidate) => [
      candidate.id,
      candidate.storyPoints || 0,
    ]),
  )
  return selectedIssueIds.value.reduce((sum, id) => sum + (points.get(id) || 0), 0)
})
const effectivePlanCapacity = computed(
  () => planCapacity.value ?? planData.value?.capacityPoints ?? 0,
)

const trendClass = computed(() => {
  const points = burndownData.value?.points || []
  if (points.length < 2) return ''
  const last = points[points.length - 1]
  const ideal = last.idealRemaining
  if (last.actualRemaining > ideal) return 'sprint-detail__stat-value--danger'
  if (last.actualRemaining < ideal) return 'sprint-detail__stat-value--success'
  return ''
})
const trendLabel = computed(() => {
  const points = burndownData.value?.points || []
  if (points.length < 2) return '-'
  const last = points[points.length - 1]
  const ideal = last.idealRemaining
  if (last.actualRemaining > ideal) return '滞后'
  if (last.actualRemaining < ideal) return '领先'
  return '正常'
})

function formatDate(d?: string) {
  if (!d) return '-'
  return d.slice(5)   // MM-DD
}

function statusLabel(s?: string) {
  return s === 'PLANNING' ? '规划中' : s === 'ACTIVE' ? '进行中' : s === 'COMPLETED' ? '已完成' : (s || '未知')
}

function priorityLabel(priority?: string) {
  return {
    HIGHEST: '最高优先级',
    HIGH: '高优先级',
    MEDIUM: '中优先级',
    LOW: '低优先级',
    LOWEST: '最低优先级',
  }[priority || ''] || (priority || '未设置')
}

function healthLabel(level?: string) {
  return level === 'AT_RISK' ? '高风险' : level === 'WATCH' ? '关注' : '健康'
}

function healthTagType(level?: string) {
  if (level === 'AT_RISK') return 'danger'
  if (level === 'WATCH') return 'warning'
  return 'success'
}

function formatHealthTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(11, 16)
}

async function loadSprints() {
  loading.value = true
  try {
    const res = await getSprints(projectId)
    sprints.value = (res.data || []) as SprintVO[]
    // 自动选中：ACTIVE > 最新 COMPLETED
    if (!selectedId.value) {
      const active = sprints.value.find(s => s.status === 'ACTIVE')
      if (active) {
        selectedId.value = active.id
      } else if (sprints.value.length > 0) {
        selectedId.value = sprints.value[0].id
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadBurndown(sprintId: number) {
  burndownLoading.value = true
  try {
    const res = await getBurndown(sprintId)
    burndownData.value = res.data
  } catch (e: any) {
    burndownData.value = null
    ElMessage.warning(e?.response?.data?.message || e?.message || '燃尽图加载失败')
  } finally {
    burndownLoading.value = false
  }
  loadWorkload(sprintId)
}

async function loadWorkload(sprintId: number) {
  try {
    const res = await getSprintWorkloadSummary(sprintId)
    workloadData.value = res.data
  } catch { workloadData.value = null }
}

async function loadHealth(sprintId: number, silent = false) {
  if (!silent) healthLoading.value = true
  try {
    const res = await getSprintHealth(sprintId)
    if (selectedId.value === sprintId) {
      healthData.value = res.data
      aiSummary.value = null
    }
  } catch (e: any) {
    if (!silent) {
      healthData.value = null
      ElMessage.warning(e?.response?.data?.message || e?.message || '健康度加载失败')
    }
  } finally {
    if (!silent) healthLoading.value = false
  }
}

async function refreshHealth() {
  if (!selectedId.value) return
  await loadHealth(selectedId.value)
}

async function loadAiSummary() {
  if (!selectedId.value) return
  aiLoading.value = true
  try {
    const res = await getSprintHealthAiSummary(selectedId.value)
    aiSummary.value = res.data
  } catch (e: any) {
    aiSummary.value = null
    ElMessage.warning(e?.response?.data?.message || e?.message || 'AI 解读暂不可用')
  } finally {
    aiLoading.value = false
  }
}

function workloadBarWidth(hours: number) {
  const max = workloadData.value?.members?.[0]?.totalHours || 0
  if (!max || !hours) return 0
  return Math.min(100, Math.round((hours / max) * 100))
}

function selectSprint(id: number) {
  selectedId.value = id
  healthData.value = null
  aiSummary.value = null
  loadBurndown(id)
  loadHealth(id)
}

async function handleCreate() {
  if (!createFormRef.value) return
  // validate() 校验不过时是 reject：不接住它就是一个未处理的 Promise 异常
  // （控制台报 pageerror，错误被悄悄吞掉）。表单红字照常会显示，但没人知道异常发生过。
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  // 理论上 validate 通过就不会走到这里；留着兜底，别再出现"点了没反应"。
  if (createForm.value.dateRange.length !== 2) {
    ElMessage.warning('请选择 Sprint 的起止时间')
    return
  }
  creating.value = true
  try {
    const [startDate, endDate] = createForm.value.dateRange
    await createSprint({
      projectId,
      name: createForm.value.name,
      goal: createForm.value.goal,
      startDate,
      endDate,
    })
    ElMessage.success('Sprint 已创建')
    showCreateDialog.value = false
    createForm.value = { name: '', goal: '', dateRange: [] }
    await loadSprints()
    if (selectedId.value) {
      loadBurndown(selectedId.value)
      loadHealth(selectedId.value)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleStart(id: number) {
  startingId.value = id
  try {
    await startSprint(id)
    ElMessage.success('Sprint 已启动')
    await loadSprints()
    selectedId.value = id
    loadBurndown(id)
    loadHealth(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '启动失败')
  } finally {
    startingId.value = null
  }
}

async function handleComplete(id: number) {
  try {
    await ElMessageBox.confirm('完成 Sprint 后未完成的任务将回退到 Backlog，确定继续？', '完成 Sprint', {
      type: 'warning',
      confirmButtonText: '完成',
    })
  } catch { return }
  completingId.value = id
  try {
    await completeSprint(id)
    ElMessage.success('Sprint 已完成')
    await loadSprints()
    loadBurndown(id)
    loadHealth(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '完成失败')
  } finally {
    completingId.value = null
  }
}

async function handleSnapshot(id: number) {
  snapshottingId.value = id
  try {
    await takeSnapshot(id)
    ElMessage.success('今日快照已生成')
    loadBurndown(id)
    loadHealth(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '生成失败')
  } finally {
    snapshottingId.value = null
  }
}

async function openSprintPlanner(sprint: SprintVO) {
  planSprint.value = sprint
  planCapacity.value = undefined
  planData.value = null
  selectedIssueIds.value = []
  planDrawerVisible.value = true
  await generatePlan()
}

async function generatePlan() {
  if (!planSprint.value) return
  planLoading.value = true
  try {
    const response = await suggestSprintPlan({
      projectId,
      sprintId: planSprint.value.id,
      capacityPoints: planCapacity.value && planCapacity.value > 0
        ? planCapacity.value
        : undefined,
    })
    planData.value = response.data
    planCapacity.value = response.data.capacityPoints
    selectedIssueIds.value = response.data.recommendedTasks.map((task) => task.id)
  } catch (error: any) {
    planData.value = null
    ElMessage.error(error?.response?.data?.message || error?.message || 'Sprint 规划建议生成失败')
  } finally {
    planLoading.value = false
  }
}

async function applyPlan() {
  if (!planSprint.value || !selectedIssueIds.value.length) return
  if (selectedPlanPoints.value > effectivePlanCapacity.value) {
    try {
      await ElMessageBox.confirm(
        `已选 ${selectedPlanPoints.value} 点，超过容量 ${effectivePlanCapacity.value} 点，确定继续？`,
        '超过 Sprint 容量',
        { type: 'warning', confirmButtonText: '继续加入' },
      )
    } catch {
      return
    }
  }
  applyingPlan.value = true
  try {
    const response = await applySprintPlan(planSprint.value.id, selectedIssueIds.value)
    ElMessage.success(
      `已加入 ${response.data.appliedCount} 项，共 ${response.data.totalStoryPoints} 故事点`,
    )
    const sprintId = planSprint.value.id
    planDrawerVisible.value = false
    await loadSprints()
    selectedId.value = sprintId
    loadBurndown(sprintId)
    loadHealth(sprintId)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '加入 Sprint 失败')
  } finally {
    applyingPlan.value = false
  }
}

onMounted(async () => {
  await loadSprints()
  if (selectedId.value) {
    loadBurndown(selectedId.value)
    loadHealth(selectedId.value)
  }
  wsSubscribe(boardTopic, () => {
    if (selectedId.value) loadHealth(selectedId.value, true)
  })
  healthTimer = setInterval(() => {
    if (selectedId.value) loadHealth(selectedId.value, true)
  }, 60000)
})

onUnmounted(() => {
  wsUnsubscribe(boardTopic)
  if (healthTimer) clearInterval(healthTimer)
})
</script>

<style scoped lang="scss">
.sprint-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 28px;
}
.sprint-page__hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color-light);
}
.sprint-page__back {
  margin-right: 4px;
  font-size: 18px;
  color: var(--text-secondary);
}
.sprint-page__hero-info { flex: 1; }
.sprint-page__title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}
.sprint-page__subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}
.sprint-page__loading { padding: 40px 0; }

.sprint-page__empty {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-muted);
}
.sprint-page__empty-icon { font-size: 56px; margin-bottom: 16px; }
.sprint-page__empty h3 { color: var(--text-regular); font-size: 18px; margin: 0 0 8px 0; font-weight: 600; }
.sprint-page__empty p { margin: 0 0 20px 0; font-size: 14px; }

.sprint-page__grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}
@media (max-width: 900px) {
  .sprint-page__grid { grid-template-columns: 1fr; }
}

.sprint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 4px;
}
.sprint-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.18s;
}
.sprint-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 8px rgba(79,70,229,0.08);
}
.sprint-card--active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(79,70,229,0.1);
}
.sprint-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sprint-card__status {
  /* 12px 起：11px 在本站"小字"体检里计 1 处 */
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
}
.sprint-card__status--planning { background: var(--bg-warning-soft); color: var(--color-warning); }
/* #10b981 亮绿压浅绿底只有 2.31:1 —— 压深到 #059669（约 4.5:1） */
.sprint-card__status--active { background: rgba(16,185,129,0.1); color: #059669; }
.sprint-card__status--completed { background: rgba(99,102,241,0.1); color: #6366f1; }
.sprint-card__date {
  font-size: 12px;
  color: var(--text-muted);
}
.sprint-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px 0;
  line-height: 1.4;
}
.sprint-card__goal {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0 0 10px 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sprint-card__meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}
.sprint-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.sprint-card__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sprint-detail {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 24px;
  position: sticky;
  top: 16px;
  min-height: 480px;
}
.sprint-detail__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 380px;
  color: var(--text-muted);
  font-size: 14px;
}
.sprint-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.sprint-detail__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}
.sprint-detail__goal {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
  max-width: 460px;
  line-height: 1.5;
}
.sprint-detail__stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.sprint-detail__stat {
  text-align: right;
}
.sprint-detail__stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}
.sprint-detail__stat-value--success { color: #10b981; }
.sprint-detail__stat-value--danger  { color: #ef4444; }
.sprint-detail__stat-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.sprint-health {
  margin-bottom: 22px;
  padding: 18px;
  border: 1px solid var(--border-color);
  border-left: 4px solid #10b981;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #ffffff 68%);
}
.sprint-health--watch {
  border-left-color: #f59e0b;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 68%);
}
.sprint-health--at_risk {
  border-left-color: #ef4444;
  background: linear-gradient(135deg, #fef2f2 0%, #ffffff 68%);
}
.sprint-health__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.sprint-health__score-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sprint-health__score {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--bg-success-soft);
  color: var(--color-success);
  font-size: 24px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.sprint-health--watch .sprint-health__score {
  background: var(--bg-warning-soft);
  color: #d97706;
}
.sprint-health--at_risk .sprint-health__score {
  background: var(--bg-danger-soft);
  color: var(--color-danger);
}
.sprint-health__eyebrow {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 700;
}
.sprint-health__status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 11px;
}
.sprint-health__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.health-progress {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 18px;
}
.health-progress__row {
  display: grid;
  grid-template-columns: 190px 1fr 50px;
  align-items: center;
  gap: 12px;
}
.health-progress__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}
.health-progress__label b {
  color: var(--text-primary);
  font-size: 11px;
  font-weight: 600;
}
.health-progress__track {
  height: 9px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}
.health-progress__bar {
  height: 100%;
  border-radius: inherit;
  transition: width 0.4s ease;
}
.health-progress__bar--time {
  background: #94a3b8;
}
.health-progress__bar--work {
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
}
.health-progress__value {
  color: var(--text-regular);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}
.health-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}
.health-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 11px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}
.health-metric span {
  color: var(--text-muted);
  font-size: 10px;
}
.health-metric b {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 750;
}
.health-metric__danger {
  color: var(--color-danger) !important;
}
.health-collapse {
  margin-top: 14px;
  border-top: 1px solid #e2e8f0;
  border-bottom: 0;
}
.health-reasons {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--text-regular);
  font-size: 12px;
}
.health-reasons div {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.health-reasons span {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  margin-top: 6px;
  border-radius: 50%;
  background: #f59e0b;
}
.health-suggestions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding: 12px;
  border-radius: 9px;
  background: var(--bg-subtle);
}
.health-suggestions__title {
  color: var(--text-regular);
  font-size: 12px;
  font-weight: 700;
}
.health-suggestions > div:not(.health-suggestions__title) {
  display: flex;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.health-suggestions > div > span {
  flex: 0 0 auto;
  width: 17px;
  height: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e0e7ff;
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
}
.health-ai {
  margin-top: 12px;
}
.health-ai__content {
  margin-top: 4px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.planner {
  min-height: 300px;
}
.planner__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
}
.planner__toolbar > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}
.planner__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}
.planner__summary > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 11px;
  border-radius: 8px;
  background: var(--bg-subtle);
}
.planner__summary span {
  color: var(--text-muted);
  font-size: 10px;
}
.planner__summary b {
  color: var(--text-primary);
  font-size: 18px;
}
.planner__ai-state {
  margin-top: 12px;
}
.planner__risks {
  margin-top: 14px;
}
.planner__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 9px;
  color: var(--text-regular);
  font-size: 13px;
  font-weight: 700;
}
.planner__section-title span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 500;
}
.planner__risk {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-bottom: 5px;
  color: var(--text-secondary);
  font-size: 12px;
}
.planner__risk span {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  margin-top: 6px;
  border-radius: 50%;
  background: #f59e0b;
}
.planner__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.planner-item {
  width: 100%;
  height: auto;
  margin: 0;
  padding: 11px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  align-items: flex-start;
}
.planner-item :deep(.el-checkbox__input) {
  margin-top: 3px;
}
.planner-item :deep(.el-checkbox__label) {
  flex: 1;
  min-width: 0;
  white-space: normal;
}
.planner-item__body {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.planner-item__title {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.planner-item__title b {
  color: var(--text-primary);
  font-size: 13px;
}
.planner-item__key {
  color: var(--text-secondary);
  font-size: 11px;
}
.planner-item__meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-muted);
  font-size: 11px;
}
.planner-item__reason {
  padding: 7px 9px;
  border-radius: 6px;
  background: #f0fdf4;
  color: #15803d;
  font-size: 11px;
  line-height: 1.5;
}
.planner__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}
.planner__danger {
  color: var(--color-danger) !important;
}
.sprint-detail__chart-wrap {
  width: 100%;
  min-height: 320px;
}

.sprint-detail__workload {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px dashed #e2e8f0;
}
.sprint-detail__workload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.sprint-detail__workload-header h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.sprint-detail__workload-total {
  font-size: 13px;
  color: var(--color-primary);
  font-weight: 600;
}
.sprint-detail__workload-empty {
  text-align: center;
  padding: 24px 12px;
  color: var(--text-muted);
  font-size: 13px;
  background: var(--bg-subtle);
  border-radius: 8px;
}
.sprint-detail__workload-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.wl-row {
  display: grid;
  grid-template-columns: 32px 100px 1fr 60px;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.wl-row__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 13px;
}
.wl-row__name {
  color: var(--text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wl-row__count {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wl-row__bar-wrap {
  position: relative;
  height: 8px;
  background: var(--bg-subtle);
  border-radius: 4px;
  overflow: hidden;
}
.wl-row__bar {
  height: 100%;
  background: linear-gradient(90deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 4px;
  transition: width 0.3s;
}
.wl-row__hours {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  text-align: right;
}

[data-theme='dark'] {
  .sprint-card { background: #1e1e2d; border-color: #2d2d44; }
  .sprint-card__name, .sprint-detail__title, .sprint-detail__stat-value { color: #e2e8f0; }
  .sprint-card__goal, .sprint-detail__goal, .sprint-page__subtitle, .sprint-card__meta { color: var(--text-muted); }
  .sprint-detail { background: #1e1e2d; border-color: #2d2d44; }
  .sprint-page__hero { border-bottom-color: #2d2d44; }
  .sprint-health { border-color: #2d2d44; background: #1e1e2d; }
  .sprint-health__eyebrow, .health-progress__label b, .health-metric b { color: #e2e8f0; }
  .health-progress__track { background: #2d2d44; }
  .health-metric { border-color: #2d2d44; background: #252537; }
  .health-collapse { border-top-color: #2d2d44; }
  .health-suggestions { background: #252537; }
  .health-suggestions__title { color: #cbd5e1; }
  .health-suggestions > div:not(.health-suggestions__title),
  .health-reasons { color: var(--text-muted); }
  .planner__toolbar,
  .planner-item { border-color: #2d2d44; }
  .planner__summary > div { background: #252537; }
  .planner__summary b,
  .planner-item__title b { color: #e2e8f0; }
  .planner-item__reason { background: rgba(16,185,129,0.12); color: #6ee7b7; }
}

@media (max-width: 900px) {
  .health-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .health-progress__row { grid-template-columns: 1fr 50px; }
  .health-progress__label { grid-column: 1 / -1; }
  .planner__summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .planner__toolbar,
  .planner__footer { align-items: flex-start; flex-direction: column; }
}
</style>
