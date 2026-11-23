<template>
  <div class="approval-page">
    <PageHeader title="审批管理" subtitle="处理团队成员的审批请求" />

    <!-- 从通知点进来时高亮那条请求：否则用户落到一个列表页，还得自己找刚收到的是哪一条 -->
    <el-alert
      v-if="highlightId"
      class="approval-page__from-notif"
      type="info"
      show-icon
      closable
      :title="`来自通知：正在高亮审批 #${highlightId}`"
      @close="highlightId = null"
    />

    <el-tabs v-model="activeTab" @tab-change="fetchData">
      <el-tab-pane label="待审批" name="pending">
        <el-table
          :data="pendingList"
          v-loading="loading"
          empty-text="暂无待审批请求"
          :row-class-name="highlightRowClass"
        >
          <el-table-column prop="title" label="请求" min-width="180" />
          <el-table-column prop="requesterUsername" label="申请人" width="100" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ typeLabel(row.targetType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
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
          <el-table-column prop="title" label="请求" min-width="180" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ typeLabel(row.targetType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'"
                size="small"
              >{{ row.status === 'APPROVED' ? '通过' : row.status === 'REJECTED' ? '拒绝' : '待处理' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approverUsername" label="处理人" width="100" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPendingApprovals, getAllPendingApprovals, getMyApprovals, approveRequest, rejectRequest } from '@/api/approval'
import PageHeader from '@/components/common/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatRelativeTime } from '@/utils/constants'

const route = useRoute()

const activeTab = ref('pending')
const loading = ref(false)
const pendingList = ref<any[]>([])
const myList = ref<any[]>([])

/** 通知里带过来的审批 id，用来高亮那一行（通知点击 → /admin/approvals?id=…） */
const highlightId = ref<number | null>(
  route.query.id ? Number(route.query.id) || null : null
)

function highlightRowClass({ row }: { row: any }) {
  return highlightId.value && Number(row.id) === highlightId.value ? 'approval-row--highlight' : ''
}

function typeLabel(t: string) {
  const m: Record<string, string> = {
    PROJECT_CREATE: '创建项目', PROJECT_UPDATE: '修改项目', PROJECT_DELETE: '删除项目',
    PROJECT_MEMBER_ADD: '添加成员'
  }
  return m[t] || t
}

function formatTime(s: string) { return formatRelativeTime(s) }

async function fetchData() {
  loading.value = true
  try {
    const [p, m] = await Promise.all([getAllPendingApprovals(), getMyApprovals()])
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
  const reason = await ElMessageBox.prompt('拒绝原因（可选）', '拒绝审批', { confirmButtonText: '确定', cancelButtonText: '取消' }).then(v => v.value).catch(() => null)
  if (reason === null) return
  await rejectRequest(row.id, reason || '管理员拒绝')
  ElMessage.info('已拒绝')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.approval-page__from-notif {
  margin-bottom: 12px;
}

/* 通知带来的那条请求：用左侧色条 + 淡背景标出来，不刺眼但能一眼找到 */
:deep(.approval-row--highlight) > td {
  background: var(--mimo-highlight-bg, rgba(99, 102, 241, 0.08)) !important;
}

:deep(.approval-row--highlight) > td:first-child {
  box-shadow: inset 3px 0 0 var(--mimo-primary, #6366f1);
}
</style>
