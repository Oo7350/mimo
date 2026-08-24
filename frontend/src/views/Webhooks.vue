<template>
  <div class="webhooks-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span>第三方 Webhook 集成</span>
            <el-tag size="small" type="info" style="margin-left: 8px">v2.13.5</el-tag>
          </div>
          <div class="header-right">
            <el-select v-model="filters.projectId" placeholder="项目过滤" clearable style="width: 200px" @change="load">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-checkbox v-model="filters.includeDisabled" @change="load" style="margin-left: 12px">含已禁用</el-checkbox>
            <el-button :loading="loading" @click="load" style="margin-left: 8px">刷新</el-button>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon> 新建 Webhook
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="list.length === 0 && !loading" description="暂无 Webhook 配置，点击右上角创建" />
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="url" label="URL" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-family: monospace">{{ row.url }}</span>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.projectId" size="small">项目#{{ row.projectId }}</el-tag>
            <el-tag v-else-if="row.teamId" size="small" type="warning">团队#{{ row.teamId }}</el-tag>
            <el-tag v-else size="small" type="success">全局</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订阅事件" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="ev in splitEvents(row.events)" :key="ev" size="small" style="margin: 2px">{{ eventLabel(ev) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.enabled === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDeliveries(row)">投递日志</el-button>
            <el-button text type="success" :loading="testingId === row.id" @click="handleTest(row)">测试投递</el-button>
            <el-button text type="warning" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? '新建 Webhook' : `编辑 Webhook #${form.id}`"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：钉钉机器人 / Jenkins 触发器" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="URL" required>
          <el-input v-model="form.url" placeholder="https://example.com/webhook" />
          <div class="hint">仅支持 http/https，POST + JSON</div>
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input v-model="form.secret" placeholder="可选，留空不签名" show-password />
          <div class="hint">填写后会在 Header 中带 X-Mimo-Signature (HMAC-SHA256 hex)</div>
        </el-form-item>
        <el-form-item label="订阅事件" required>
          <el-checkbox-group v-model="eventsArr">
            <el-checkbox v-for="e in WEBHOOK_EVENTS" :key="e.value" :label="e.value">{{ e.label }}</el-checkbox>
          </el-checkbox-group>
          <div class="hint">至少选一项，匹配的事件会触发投递</div>
        </el-form-item>
        <el-form-item label="生效范围">
          <el-radio-group v-model="form.scope">
            <el-radio label="global">全局</el-radio>
            <el-radio label="project">指定项目</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 'project'" label="项目">
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 投递日志弹窗 -->
    <el-dialog v-model="deliveriesVisible" :title="`Webhook #${currentHook?.id} 投递日志`" width="900px">
      <el-table :data="deliveries" v-loading="deliveriesLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="eventType" label="事件" width="140" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.success === 1" type="success" size="small">成功</el-tag>
            <el-tag v-else type="danger" size="small">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="HTTP" width="80" align="center" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="error" label="错误" min-width="220" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="showPayload(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="deliveriesVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 负载详情 -->
    <el-dialog v-model="payloadVisible" title="投递详情" width="680px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="事件">{{ currentDelivery?.eventType }}</el-descriptions-item>
        <el-descriptions-item label="HTTP 状态">{{ currentDelivery?.statusCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentDelivery?.durationMs || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag v-if="currentDelivery?.success === 1" type="success" size="small">成功</el-tag>
          <el-tag v-else type="danger" size="small">失败</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="错误">{{ currentDelivery?.error || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求体">
          <pre class="payload-pre">{{ formatJson(currentDelivery?.payload) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应体">
          <pre class="payload-pre">{{ currentDelivery?.responseBody || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="payloadVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listWebhooks,
  createWebhook,
  updateWebhook,
  deleteWebhook,
  testWebhook,
  listDeliveries,
  WEBHOOK_EVENTS,
  type WebhookConfig,
  type WebhookDeliveryLog,
} from '@/api/webhook'
import request from '@/api/request'

interface ProjectVO {
  id: number
  name: string
}

const loading = ref(false)
const saving = ref(false)
const testingId = ref<number | null>(null)
const list = ref<WebhookConfig[]>([])
const total = ref(0)
const projects = ref<ProjectVO[]>([])
const filters = reactive({
  page: 1,
  size: 20,
  projectId: undefined as number | undefined,
  teamId: undefined as number | undefined,
  includeDisabled: false,
})

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const eventsArr = ref<string[]>([])
const form = reactive<WebhookConfig & { scope: 'global' | 'project' }>({
  id: undefined,
  name: '',
  url: '',
  secret: '',
  events: '',
  enabled: 1,
  projectId: undefined,
  teamId: undefined,
  scope: 'global',
})

const deliveriesVisible = ref(false)
const deliveriesLoading = ref(false)
const deliveries = ref<WebhookDeliveryLog[]>([])
const currentHook = ref<WebhookConfig | null>(null)

const payloadVisible = ref(false)
const currentDelivery = ref<WebhookDeliveryLog | null>(null)

async function loadProjects() {
  try {
    const res = await request.get('/projects', { params: { size: 200 } })
    projects.value = res.data?.data?.records || res.data?.data || []
  } catch {
    projects.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listWebhooks({
      page: filters.page,
      size: filters.size,
      projectId: filters.projectId,
      teamId: filters.teamId,
      includeDisabled: filters.includeDisabled,
    })
    const d = res.data?.data || {}
    list.value = d.records || []
    total.value = d.total || 0
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function splitEvents(s?: string) {
  return (s || '').split(',').map(x => x.trim()).filter(Boolean)
}

function eventLabel(v: string) {
  return WEBHOOK_EVENTS.find(e => e.value === v)?.label || v
}

function resetForm() {
  form.id = undefined
  form.name = ''
  form.url = ''
  form.secret = ''
  form.events = ''
  form.enabled = 1
  form.projectId = undefined
  form.teamId = undefined
  form.scope = 'global'
  eventsArr.value = []
}

function openCreate() {
  formMode.value = 'create'
  resetForm()
  formVisible.value = true
}

function openEdit(row: WebhookConfig) {
  formMode.value = 'edit'
  Object.assign(form, row)
  form.scope = row.projectId ? 'project' : 'global'
  eventsArr.value = splitEvents(row.events)
  formVisible.value = true
}

async function handleSave() {
  if (!form.name?.trim()) return ElMessage.warning('请填写名称')
  if (!form.url?.startsWith('http')) return ElMessage.warning('URL 必须以 http 开头')
  if (eventsArr.value.length === 0) return ElMessage.warning('请至少选择一个事件')
  if (form.scope === 'project' && !form.projectId) return ElMessage.warning('请选择项目')

  form.events = eventsArr.value.join(',')
  form.teamId = undefined
  if (form.scope === 'global') form.projectId = undefined

  saving.value = true
  try {
    if (formMode.value === 'create') {
      const res = await createWebhook({ ...form })
      ElMessage.success('已创建 #' + (res.data?.data?.id || '?'))
    } else {
      await updateWebhook(form.id!, { ...form })
      ElMessage.success('已更新')
    }
    formVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message || e))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: WebhookConfig) {
  try {
    await ElMessageBox.confirm(`确认删除 Webhook "${row.name}"？`, '提示', { type: 'warning' })
    await deleteWebhook(row.id!)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e.message || e))
  }
}

async function handleTest(row: WebhookConfig) {
  testingId.value = row.id!
  try {
    const res = await testWebhook(row.id!)
    const d = res.data?.data || {}
    if (d.success === 1 || d.statusCode) {
      ElMessage.success(`测试投递完成：HTTP ${d.statusCode || '-'}`)
    } else {
      ElMessage.warning(`测试失败：${d.error || '未知'}`)
    }
  } catch (e: any) {
    ElMessage.error('测试失败：' + (e.response?.data?.message || e.message || e))
  } finally {
    testingId.value = null
  }
}

async function openDeliveries(row: WebhookConfig) {
  currentHook.value = row
  deliveriesVisible.value = true
  deliveriesLoading.value = true
  try {
    const res = await listDeliveries(row.id!, { page: 1, size: 20 })
    const d = res.data?.data || {}
    deliveries.value = d.records || []
  } catch (e: any) {
    ElMessage.error('加载日志失败：' + (e.message || e))
  } finally {
    deliveriesLoading.value = false
  }
}

function showPayload(row: WebhookDeliveryLog) {
  currentDelivery.value = row
  payloadVisible.value = true
}

function formatJson(s?: string) {
  if (!s) return '-'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').replace(/\.\d+$/, '')
}

onMounted(() => {
  loadProjects()
  load()
})
</script>

<style scoped>
.webhooks-page {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.header-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.hint {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.payload-pre {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
