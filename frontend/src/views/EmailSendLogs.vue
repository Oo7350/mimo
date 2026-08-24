<template>
  <div class="email-send-logs-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>邮件发送日志</span>
          <el-tag size="small" type="info" style="margin-left: 8px">v2.13.6</el-tag>
          <div class="header-actions">
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 统计卡片 -->
      <div class="stats-row" v-if="stats">
        <div class="stat-item">
          <div class="stat-label">总数</div>
          <div class="stat-value">{{ stats.total }}</div>
        </div>
        <div class="stat-item stat-success">
          <div class="stat-label">成功</div>
          <div class="stat-value">{{ stats.success }}</div>
        </div>
        <div class="stat-item stat-failed">
          <div class="stat-label">失败</div>
          <div class="stat-value">{{ stats.failed }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">送达率</div>
          <div class="stat-value">{{ (stats.deliveryRate || 0).toFixed(1) }}%</div>
        </div>
      </div>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="用户ID">
          <el-input v-model="filters.userId" clearable placeholder="精确" style="width: 120px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.notifyType" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="t in notifyTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filters.success" clearable placeholder="全部" style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始">
          <el-date-picker v-model="filters.startTime" type="datetime" placeholder="不限" style="width: 200px" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束">
          <el-date-picker v-model="filters.endTime" type="datetime" placeholder="不限" style="width: 200px" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" v-loading="loading" style="margin-top: 12px" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column prop="toEmail" label="收件人" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fromEmail" label="发件人" min-width="180" show-overflow-tooltip />
        <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.notifyType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.success === 1" type="success" size="small">成功</el-tag>
            <el-tag v-else type="danger" size="small">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
        <el-table-column prop="error" label="错误" min-width="240" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface EmailSendLog {
  id: number
  userId: number
  toEmail: string
  accountId?: number
  fromEmail?: string
  subject: string
  notifyType: string
  relatedId?: number
  success: number
  error?: string
  durationMs?: number
  createdAt: string
}

interface Stats {
  total: number
  success: number
  failed: number
  deliveryRate: number
}

const loading = ref(false)
const logs = ref<EmailSendLog[]>([])
const total = ref(0)
const stats = ref<Stats | null>(null)

const notifyTypes = [
  { value: 'assignment', label: '任务指派' },
  { value: 'mention', label: '@提及' },
  { value: 'approval', label: '审批通知' },
  { value: 'comment', label: '评论回复' },
  { value: 'issue_status', label: '任务状态变更' },
]

const filters = reactive({
  page: 1,
  size: 20,
  userId: '',
  notifyType: '',
  success: undefined as number | undefined,
  startTime: '',
  endTime: '',
})

async function load() {
  loading.value = true
  try {
    const params: any = {
      page: filters.page,
      size: filters.size,
    }
    if (filters.userId) params.userId = filters.userId
    if (filters.notifyType) params.notifyType = filters.notifyType
    if (filters.success !== undefined && filters.success !== null) params.success = filters.success
    if (filters.startTime) params.startTime = filters.startTime
    if (filters.endTime) params.endTime = filters.endTime

    const res = await request.get('/email-send-logs', { params })
    const d = res.data?.data || {}
    logs.value = d.records || []
    total.value = d.total || 0
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e.response?.data?.message || e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const params: any = {}
    if (filters.startTime) params.startTime = filters.startTime
    if (filters.endTime) params.endTime = filters.endTime
    const res = await request.get('/email-send-logs/stats', { params })
    stats.value = res.data?.data
  } catch {
    stats.value = null
  }
}

function onSearch() {
  filters.page = 1
  load()
  loadStats()
}

function resetFilters() {
  filters.userId = ''
  filters.notifyType = ''
  filters.success = undefined
  filters.startTime = ''
  filters.endTime = ''
  filters.page = 1
  load()
  loadStats()
}

function typeLabel(t?: string) {
  if (!t) return '-'
  return notifyTypes.find(x => x.value === t)?.label || t
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').replace(/\.\d+$/, '')
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.email-send-logs-page {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions {
  margin-left: auto;
}
.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.stat-item {
  flex: 1;
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 6px;
}
.stat-item.stat-success {
  background: #f0f9eb;
}
.stat-item.stat-failed {
  background: #fef0f0;
}
.stat-label {
  color: #909399;
  font-size: 12px;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.filter-form {
  margin-top: 12px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
