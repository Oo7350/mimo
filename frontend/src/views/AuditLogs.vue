<template>
  <div class="audit-logs-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>操作审计日志</span>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="用户ID">
          <el-input v-model="filters.userId" clearable placeholder="精确" style="width: 120px" />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="filters.targetType" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="t in targetTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="filters.action" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求ID">
          <el-input v-model="filters.requestId" clearable placeholder="串联同请求" style="width: 220px" />
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
          <el-button @click="exportCsv" :loading="exporting">导出 CSV</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" v-loading="loading" style="margin-top: 12px" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column label="对象" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ row.targetType || '-' }}</el-tag>
            <span v-if="row.targetId" style="margin-left: 6px">#{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="请求ID" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-family: monospace; font-size: 12px; color: #999">{{ row.requestId || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="load"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface AuditLog {
  id: number
  userId: number
  username: string
  projectId?: number
  targetType: string
  targetId?: number
  action: string
  detail?: string
  ipAddress?: string
  userAgent?: string
  diffJson?: string
  requestId?: string
  createdAt: string
}

const loading = ref(false)
const exporting = ref(false)
const logs = ref<AuditLog[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const targetTypes = ['ISSUE', 'PROJECT', 'SPRINT', 'TEAM', 'ROLE', 'USER', 'APPROVAL', 'COMMENT', 'WIKI']
const actions = ['CREATE', 'UPDATE', 'DELETE', 'MOVE', 'ASSIGN', 'APPROVE', 'REJECT']

const filters = reactive({
  userId: '',
  targetType: '',
  action: '',
  requestId: '',
  startTime: '',
  endTime: '',
})

function buildParams() {
  const p: Record<string, string | number> = { page: page.value, size: size.value }
  if (filters.userId) p.userId = filters.userId
  if (filters.targetType) p.targetType = filters.targetType
  if (filters.action) p.action = filters.action
  if (filters.requestId) p.requestId = filters.requestId
  if (filters.startTime) p.startTime = filters.startTime
  if (filters.endTime) p.endTime = filters.endTime
  return p
}

async function load() {
  loading.value = true
  try {
    const res = await request.get('/audit-logs', { params: buildParams() })
    logs.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e: any) {
    ElMessage.error('查询失败：' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.userId = ''
  filters.targetType = ''
  filters.action = ''
  filters.requestId = ''
  filters.startTime = ''
  filters.endTime = ''
  page.value = 1
  load()
}

function formatTime(s: string) {
  if (!s) return ''
  return s.replace('T', ' ').substring(0, 19)
}

function exportCsv() {
  exporting.value = true
  try {
    const headers = ['ID', '用户', '对象类型', '对象ID', '动作', '详情', 'IP', 'UA', '请求ID', '时间']
    const rows = logs.value.map(l => [
      l.id, l.username, l.targetType, l.targetId || '', l.action,
      (l.detail || '').replace(/[\r\n,]/g, ' '),
      l.ipAddress || '',
      (l.userAgent || '').replace(/[\r\n,]/g, ' '),
      l.requestId || '',
      formatTime(l.createdAt)
    ])
    const csv = [headers, ...rows].map(r => r.map(c => `"${c}"`).join(',')).join('\n')
    // 加 BOM 让 Excel 正确识别 UTF-8
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `audit-logs-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${rows.length} 条日志`)
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.audit-logs-page { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-form :deep(.el-form-item) { margin-bottom: 8px; }
</style>
