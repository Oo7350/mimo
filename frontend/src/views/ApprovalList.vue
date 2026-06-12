<template>
  <div class="approval-page">
    <PageHeader title="审批管理" subtitle="处理团队成员的审批请求" />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" v-loading="loading" empty-text="暂无待审批请求">
          <el-table-column prop="title" label="请求" min-width="200" />
          <el-table-column prop="requesterName" label="申请人" width="120" />
          <el-table-column prop="targetType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ typeLabel(row.targetType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
              <el-button size="small" type="danger" @click="handleReject(row)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="我的申请" name="mine">
        <el-table :data="myList" v-loading="loading" empty-text="暂无申请">
          <el-table-column prop="title" label="请求" min-width="200" />
          <el-table-column prop="targetType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ typeLabel(row.targetType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
                {{ row.status === 'APPROVED' ? '已通过' : row.status === 'REJECTED' ? '已拒绝' : '待处理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="handlerName" label="处理人" width="120" />
          <el-table-column prop="createdAt" label="时间" width="160" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPendingApprovals, getMyApprovals, approveRequest, rejectRequest } from '@/api/approval'
import PageHeader from '@/components/common/PageHeader.vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('pending')
const loading = ref(false)
const pendingList = ref<any[]>([])
const myList = ref<any[]>([])

function typeLabel(t: string) {
  const m: Record<string, string> = {
    PROJECT_CREATE: '创建项目', PROJECT_UPDATE: '修改项目', PROJECT_DELETE: '删除项目'
  }
  return m[t] || t
}

async function fetchData() {
  loading.value = true
  try {
    const [p, m] = await Promise.all([getPendingApprovals(0), getMyApprovals()])
    pendingList.value = p.data || []
    myList.value = m.data || []
  } catch { /* */ }
  finally { loading.value = false }
}

async function handleApprove(row: any) {
  await approveRequest(row.id)
  ElMessage.success('已通过')
  fetchData()
}

async function handleReject(row: any) {
  await rejectRequest(row.id, '管理员拒绝')
  ElMessage.info('已拒绝')
  fetchData()
}

onMounted(fetchData)
</script>
