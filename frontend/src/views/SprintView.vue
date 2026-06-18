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
          <div class="sprint-detail__chart-wrap">
            <BurndownChart :data="burndownData" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  ArrowLeft, Plus, Tickets, CircleCheck, Aim, DataLine,
} from '@element-plus/icons-vue'
import {
  getSprints, getBurndown, createSprint,
  startSprint, completeSprint, takeSnapshot,
  type SprintVO,
} from '@/api/sprint'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import RippleButton from '@/components/common/RippleButton.vue'
import BurndownChart from '@/components/common/BurndownChart.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const loading = ref(true)
const sprints = ref<SprintVO[]>([])
const selectedId = ref<number | null>(null)
const burndownData = ref<any>(null)
const burndownLoading = ref(false)

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
}

function selectSprint(id: number) {
  selectedId.value = id
  loadBurndown(id)
}

async function handleCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  if (createForm.value.dateRange.length !== 2) return
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
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '生成失败')
  } finally {
    snapshottingId.value = null
  }
}

watch(selectedId, (id) => {
  if (id && !burndownLoading.value) {
    // noop - loadBurndown already called by selectSprint
  }
})

onMounted(async () => {
  await loadSprints()
  if (selectedId.value) loadBurndown(selectedId.value)
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
  border-bottom: 1px solid #f1f5f9;
}
.sprint-page__back {
  margin-right: 4px;
  font-size: 18px;
  color: #64748b;
}
.sprint-page__hero-info { flex: 1; }
.sprint-page__title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px 0;
}
.sprint-page__subtitle {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}
.sprint-page__loading { padding: 40px 0; }

.sprint-page__empty {
  text-align: center;
  padding: 80px 20px;
  color: #94a3b8;
}
.sprint-page__empty-icon { font-size: 56px; margin-bottom: 16px; }
.sprint-page__empty h3 { color: #475569; font-size: 18px; margin: 0 0 8px 0; font-weight: 600; }
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
  background: #fff;
  border: 1px solid #e2e8f0;
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
  border-color: #4f46e5;
  box-shadow: 0 0 0 2px rgba(79,70,229,0.1);
}
.sprint-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sprint-card__status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
}
.sprint-card__status--planning { background: rgba(245,158,11,0.1); color: #f59e0b; }
.sprint-card__status--active { background: rgba(16,185,129,0.1); color: #10b981; }
.sprint-card__status--completed { background: rgba(99,102,241,0.1); color: #6366f1; }
.sprint-card__date {
  font-size: 12px;
  color: #94a3b8;
}
.sprint-card__name {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 6px 0;
  line-height: 1.4;
}
.sprint-card__goal {
  font-size: 12px;
  color: #64748b;
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
  color: #64748b;
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
  background: #fff;
  border: 1px solid #e2e8f0;
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
  color: #94a3b8;
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
  color: #0f172a;
  margin: 0 0 4px 0;
}
.sprint-detail__goal {
  font-size: 13px;
  color: #64748b;
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
  color: #0f172a;
  line-height: 1.2;
}
.sprint-detail__stat-value--success { color: #10b981; }
.sprint-detail__stat-value--danger  { color: #ef4444; }
.sprint-detail__stat-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.sprint-detail__chart-wrap {
  width: 100%;
  min-height: 320px;
}

[data-theme='dark'] {
  .sprint-card { background: #1e1e2d; border-color: #2d2d44; }
  .sprint-card__name, .sprint-detail__title, .sprint-detail__stat-value { color: #e2e8f0; }
  .sprint-card__goal, .sprint-detail__goal, .sprint-page__subtitle, .sprint-card__meta { color: #94a3b8; }
  .sprint-detail { background: #1e1e2d; border-color: #2d2d44; }
  .sprint-page__hero { border-bottom-color: #2d2d44; }
}
</style>
