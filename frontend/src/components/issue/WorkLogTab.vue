<!--
  WorkLogTab — Issue 详情中的工时记录 Tab
  功能：列表 / 添加 / 删除 / 计时器（开始-暂停-停止 → 自动计算小时数）
-->
<template>
  <div class="worklog">
    <!-- 顶部：累计 + 添加按钮 + 计时器 -->
    <div class="worklog__header">
      <div class="worklog__summary">
        <div class="worklog__summary-num">{{ totalHours.toFixed(2) }} <span>h</span></div>
        <div class="worklog__summary-label">累计工时</div>
      </div>
      <div class="worklog__actions">
        <RippleButton variant="primary" size="small" @click="openCreate">
          <el-icon><Plus /></el-icon> 添加工时
        </RippleButton>
      </div>
    </div>

    <!-- 计时器 -->
    <div class="worklog__timer">
      <div class="worklog__timer-display">
        <el-icon class="worklog__timer-icon" :class="{ 'worklog__timer-icon--running': timerRunning }">
          <Timer />
        </el-icon>
        <span class="worklog__timer-time">{{ formatDuration(elapsedSec) }}</span>
        <span v-if="timerStart" class="worklog__timer-since">起 {{ formatTime(timerStart) }}</span>
      </div>
      <div class="worklog__timer-actions">
        <el-button v-if="!timerRunning && !timerStart" type="primary" size="small" @click="startTimer">
          <el-icon><VideoPlay /></el-icon> 开始
        </el-button>
        <template v-else>
          <el-button v-if="timerRunning" type="warning" size="small" @click="pauseTimer">
            <el-icon><VideoPause /></el-icon> 暂停
          </el-button>
          <el-button v-else size="small" @click="resumeTimer">
            <el-icon><VideoPlay /></el-icon> 继续
          </el-button>
          <el-button type="success" size="small" @click="stopTimer">
            <el-icon><Check /></el-icon> 停止并记录
          </el-button>
          <el-button size="small" text @click="cancelTimer">取消</el-button>
        </template>
      </div>
    </div>

    <!-- 工时列表 -->
    <div v-if="logs.length === 0" class="worklog__empty">
      <div class="worklog__empty-icon">⏱️</div>
      <p>还没有工时记录</p>
      <p class="worklog__empty-tip">使用上方计时器或点击「添加工时」开始记录</p>
    </div>
    <div v-else class="worklog__list">
      <div v-for="l in logs" :key="l.id" class="worklog__item">
        <div class="worklog__item-avatar" :style="{ background: avatarGradient(l.username || '?', 'user') }">
          {{ (l.username || '?').charAt(0).toUpperCase() }}
        </div>
        <div class="worklog__item-body">
          <div class="worklog__item-row">
            <span class="worklog__item-user">{{ l.username || '未知' }}</span>
            <span class="worklog__item-hours">{{ Number(l.hours).toFixed(2) }} h</span>
          </div>
          <div class="worklog__item-row worklog__item-row--meta">
            <span class="worklog__item-date">{{ l.workDate }}</span>
            <span v-if="l.description" class="worklog__item-desc">· {{ l.description }}</span>
          </div>
        </div>
        <el-button
          v-if="canDelete(l)"
          text
          size="small"
          class="worklog__item-del"
          @click="handleDelete(l.id)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 添加工时对话框 -->
    <el-dialog v-model="showCreate" title="添加工时" width="420px" :close-on-click-modal="false">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-position="top">
        <el-form-item label="日期" prop="workDate">
          <el-date-picker v-model="createForm.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时（小时）" prop="hours">
          <el-input-number v-model="createForm.hours" :min="0.25" :step="0.25" :max="24" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明（可选）">
          <el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="本次完成了什么…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  Plus, Timer, VideoPlay, VideoPause, Check, Delete,
} from '@element-plus/icons-vue'
import {
  listWorkLogsByIssue, createWorkLog, deleteWorkLog, type WorkLogVO,
} from '@/api/worklog'
import { avatarGradient } from '@/utils/color'
import RippleButton from '@/components/common/RippleButton.vue'

const props = defineProps<{
  issueId: number
  currentUserId?: number
}>()

const logs = ref<WorkLogVO[]>([])
const totalHours = computed(() =>
  logs.value.reduce((sum, l) => sum + Number(l.hours || 0), 0))

const showCreate = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  workDate: new Date().toISOString().slice(0, 10),
  hours: 1,
  description: '',
})
const createRules: FormRules = {
  workDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  hours: [{ required: true, type: 'number', message: '请输入工时', trigger: 'blur' }],
}

// ---- 计时器状态 ----
const timerRunning = ref(false)
const timerStart = ref<number | null>(null)   // timestamp
const elapsedSec = ref(0)
let timerHandle: number | null = null

function formatDuration(sec: number) {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${m}:${String(s).padStart(2, '0')}`
}
function formatTime(ts: number) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function startTimer() {
  timerStart.value = Date.now()
  elapsedSec.value = 0
  timerRunning.value = true
  timerHandle = window.setInterval(() => {
    elapsedSec.value = Math.floor((Date.now() - (timerStart.value || Date.now())) / 1000)
  }, 1000)
}
function pauseTimer() {
  timerRunning.value = false
  if (timerHandle) { window.clearInterval(timerHandle); timerHandle = null }
}
function resumeTimer() {
  if (!timerStart.value) return
  // 调整 start 跳过已暂停时间（保持 elapsedSec 不变）
  timerRunning.value = true
  timerHandle = window.setInterval(() => {
    elapsedSec.value = Math.floor((Date.now() - (timerStart.value || Date.now())) / 1000)
  }, 1000)
}
function stopTimer() {
  pauseTimer()
  const hours = +(elapsedSec.value / 3600).toFixed(2)
  if (hours <= 0) {
    ElMessage.warning('计时时间过短')
    return
  }
  // 打开创建对话框预填
  createForm.value = {
    workDate: new Date().toISOString().slice(0, 10),
    hours,
    description: `计时器记录 (${formatDuration(elapsedSec.value)})`,
  }
  showCreate.value = true
  resetTimer()
}
function cancelTimer() {
  pauseTimer()
  resetTimer()
}
function resetTimer() {
  timerStart.value = null
  elapsedSec.value = 0
  timerRunning.value = false
}

onBeforeUnmount(() => {
  if (timerHandle) window.clearInterval(timerHandle)
})

// ---- 加载 ----
async function load() {
  if (!props.issueId) return
  try {
    const res = await listWorkLogsByIssue(props.issueId)
    logs.value = (res.data || []) as WorkLogVO[]
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '工时加载失败')
  }
}

function openCreate() {
  createForm.value = {
    workDate: new Date().toISOString().slice(0, 10),
    hours: 1,
    description: '',
  }
  showCreate.value = true
}

async function handleCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  creating.value = true
  try {
    await createWorkLog({ issueId: props.issueId, ...createForm.value })
    ElMessage.success('工时已记录')
    showCreate.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    creating.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该工时记录？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteWorkLog(id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
  }
}

function canDelete(l: WorkLogVO) {
  return !props.currentUserId || l.userId === props.currentUserId
}

watch(() => props.issueId, () => load(), { immediate: false })
onMounted(() => load())
</script>

<style scoped lang="scss">
.worklog { padding: 8px 0; }

.worklog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.worklog__summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.worklog__summary-num {
  font-size: 24px;
  font-weight: 700;
  color: #4f46e5;
}
.worklog__summary-num span {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 400;
  margin-left: 2px;
}
.worklog__summary-label {
  font-size: 12px;
  color: #94a3b8;
}

.worklog__timer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  margin-bottom: 16px;
}
.worklog__timer-display {
  display: flex;
  align-items: center;
  gap: 10px;
}
.worklog__timer-icon {
  font-size: 18px;
  color: #94a3b8;
  transition: color 0.2s;
}
.worklog__timer-icon--running {
  color: #4f46e5;
  animation: pulse 1.4s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.45; }
}
.worklog__timer-time {
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.5px;
}
.worklog__timer-since {
  font-size: 11px;
  color: #94a3b8;
}
.worklog__timer-actions {
  display: flex;
  gap: 6px;
}

.worklog__empty {
  text-align: center;
  padding: 40px 20px;
  color: #94a3b8;
}
.worklog__empty-icon { font-size: 40px; margin-bottom: 8px; }
.worklog__empty-tip { font-size: 12px; color: #c0c4cc; margin-top: 4px; }

.worklog__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.worklog__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  transition: all 0.15s;
}
.worklog__item:hover { border-color: #e2e8f0; }
.worklog__item-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  flex-shrink: 0;
}
.worklog__item-body { flex: 1; min-width: 0; }
.worklog__item-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.worklog__item-row--meta { margin-top: 2px; }
.worklog__item-user {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.worklog__item-hours {
  font-size: 14px;
  font-weight: 700;
  color: #4f46e5;
  margin-left: auto;
}
.worklog__item-date {
  font-size: 12px;
  color: #94a3b8;
}
.worklog__item-desc {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.worklog__item-del {
  color: #c0c4cc;
  transition: color 0.15s;
}
.worklog__item-del:hover { color: #ef4444; }

[data-theme='dark'] {
  .worklog__item { background: #252538; border-color: #2d2d44; }
  .worklog__item-user, .worklog__timer-time { color: #e2e8f0; }
  .worklog__timer { background: linear-gradient(135deg, #1e1b4b 0%, #2e1065 100%); border-color: #312e81; }
}
</style>
