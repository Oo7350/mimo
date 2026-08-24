<template>
  <div class="email-accts-page">
    <div class="page-header">
      <h2>邮箱绑定</h2>
      <el-button type="primary" @click="openBind">
        <el-icon><Plus /></el-icon> 绑定新邮箱
      </el-button>
    </div>

    <!-- 已绑定账户列表 -->
    <el-card class="section" shadow="never">
      <template #header><span>已绑定的邮箱</span></template>
      <el-empty v-if="accounts.length === 0" description="尚未绑定任何邮箱" />
      <el-table v-else :data="accounts" stripe>
        <el-table-column label="邮箱地址" min-width="200">
          <template #default="{ row }">
            <el-icon><Message /></el-icon>
            <span style="margin-left: 8px">{{ row.emailAddress }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IMAP 服务器" min-width="180">
          <template #default="{ row }">{{ row.imapHost }}:{{ row.imapPort }}</template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近同步" min-width="160">
          <template #default="{ row }">{{ formatTime(row.lastSyncedAt) || '从未同步' }}</template>
        </el-table-column>
        <el-table-column label="邮件通知" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.notifyEnabled === 1" type="success" size="small">已开启</el-tag>
            <el-tag v-else type="info" size="small">关闭</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="400" align="center">
          <template #default="{ row }">
            <el-button text type="primary" @click="goInbox(row.id)">查看收件箱</el-button>
            <el-button text type="success" @click="openNotify(row)">通知偏好</el-button>
            <el-button text type="warning" @click="handleDebug(row)">诊断</el-button>
            <el-button text type="warning" @click="handleDeepDebug(row)">深度诊断</el-button>
            <el-button text type="danger" @click="handleUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预设列表 -->
    <el-card class="section" shadow="never">
      <template #header><span>常见邮箱 IMAP 配置参考</span></template>
      <el-table :data="presets" stripe>
        <el-table-column label="服务商" prop="provider" width="180" />
        <el-table-column label="IMAP 服务器" prop="imapHost" min-width="180" />
        <el-table-column label="端口" prop="imapPort" width="80" align="center" />
        <el-table-column label="提示" prop="hint" min-width="220" />
      </el-table>
    </el-card>

    <!-- 深度诊断弹窗 -->
    <el-dialog v-model="deepDebugVisible" title="深度诊断结果" width="640px" :close-on-click-modal="true">
      <el-scrollbar height="360px">
        <pre class="deep-debug-content">{{ deepDebugContent }}</pre>
      </el-scrollbar>
      <template #footer>
        <el-button @click="deepDebugVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- v2.13.3：邮件通知偏好弹窗 -->
    <el-dialog v-model="notifyVisible" title="邮件通知偏好" width="520px" :close-on-click-modal="false">
      <el-form :model="notifyForm" label-width="100px">
        <el-form-item label="当前邮箱">
          <span>{{ notifyForm.emailAddress }}</span>
        </el-form-item>
        <el-form-item label="启用通知">
          <el-switch v-model="notifyForm.enabled" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 12px; color: #999; font-size: 12px">关闭后将不再发送邮件通知</span>
        </el-form-item>
        <el-form-item label="触发事件">
          <el-checkbox-group v-model="notifyForm.typeArr">
            <el-checkbox label="assignment">任务指派</el-checkbox>
            <el-checkbox label="mention">@提及</el-checkbox>
            <el-checkbox label="approval">审批结果</el-checkbox>
            <el-checkbox label="comment">任务评论</el-checkbox>
            <el-checkbox label="issue_status">任务状态变更</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="SMTP 主机">
          <el-input v-model="notifyForm.smtpHost" placeholder="留空将按 IMAP 主机自动推断（imap.x → smtp.x）" />
        </el-form-item>
        <el-form-item label="SMTP 端口">
          <el-input-number v-model="notifyForm.smtpPort" :min="1" :max="65535" placeholder="默认 465" />
        </el-form-item>
        <el-form-item label="">
          <el-button :loading="sendingTest" @click="handleSendTest">发送测试邮件</el-button>
          <span style="margin-left: 8px; color: #999; font-size: 12px">用于验证 SMTP 配置是否正确</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingNotify" @click="handleSaveNotify">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定弹窗 -->
    <el-dialog v-model="bindVisible" title="绑定新邮箱" width="540px" :close-on-click-modal="false">
      <el-form :model="bindForm" label-width="120px">
        <el-form-item label="快速选择">
          <el-select v-model="selectedPresetIdx" placeholder="选择预设自动填充" style="width: 100%" @change="applyPreset">
            <el-option
              v-for="(p, idx) in presets"
              :key="idx"
              :label="p.provider"
              :value="idx"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱地址" required>
          <el-input v-model="bindForm.emailAddress" placeholder="yourname@qq.com" />
        </el-form-item>
        <el-form-item label="IMAP 服务器" required>
          <el-input v-model="bindForm.imapHost" placeholder="imap.qq.com" />
        </el-form-item>
        <el-form-item label="IMAP 端口" required>
          <el-input-number v-model="bindForm.imapPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="IMAP 用户名" required>
          <el-input v-model="bindForm.imapUsername" placeholder="通常等于邮箱地址" />
        </el-form-item>
        <el-form-item label="IMAP 密码" required>
          <el-input v-model="bindForm.imapPassword" type="password" show-password placeholder="授权码或应用专用密码" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="bindForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button :loading="testing" @click="handleTest">测试连接</el-button>
        <el-button type="primary" :loading="binding" @click="handleBind">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Message } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  listEmailAccounts,
  bindEmailAccount,
  unbindEmailAccount,
  testEmailConnection,
  getEmailPresets,
  updateNotifySettings,
  sendTestNotification,
  type EmailAccountVO,
  type EmailPresetVO,
  type BindEmailReq,
} from '@/api/email'
import request from '@/api/request'

const router = useRouter()
const accounts = ref<EmailAccountVO[]>([])
const presets = ref<EmailPresetVO[]>([])
const bindVisible = ref(false)
const binding = ref(false)
const testing = ref(false)
const deepDebugVisible = ref(false)
const deepDebugContent = ref('')

// v2.13.3：邮件通知偏好弹窗状态
const notifyVisible = ref(false)
const savingNotify = ref(false)
const sendingTest = ref(false)
const notifyForm = reactive<{
  accountId: number | null
  emailAddress: string
  enabled: number
  typeArr: string[]
  smtpHost: string
  smtpPort: number | null
}>({
  accountId: null,
  emailAddress: '',
  enabled: 0,
  typeArr: [],
  smtpHost: '',
  smtpPort: null,
})
const selectedPresetIdx = ref<number | null>(null)
const bindForm = reactive<BindEmailReq>({
  emailAddress: '',
  imapHost: '',
  imapPort: 993,
  imapUsername: '',
  imapPassword: '',
  isDefault: 0,
})

async function loadAccounts() {
  try {
    const res = await listEmailAccounts()
    accounts.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.message || ''))
  }
}

async function loadPresets() {
  try {
    const res = await getEmailPresets()
    presets.value = res.data || []
  } catch (e: any) {
    // 不弹消息框避免打扰用户，但打印到控制台便于排查
    console.warn('[EmailAccounts] loadPresets failed:', e?.message || e)
  }
}

function applyPreset(idx: number) {
  const p = presets.value[idx]
  if (!p) return
  bindForm.imapHost = p.imapHost
  bindForm.imapPort = p.imapPort
  if (!bindForm.emailAddress) {
    // 不强填，让用户自己输
  }
}

function openBind() {
  Object.assign(bindForm, {
    emailAddress: '',
    imapHost: '',
    imapPort: 993,
    imapUsername: '',
    imapPassword: '',
    isDefault: accounts.value.length === 0 ? 1 : 0,
  })
  selectedPresetIdx.value = null
  // 兜底：若 onMounted 时后端未启动或 token 未就绪导致 presets 没拉到，
  // 在打开弹窗时再拉一次，保证下拉有数据
  if (presets.value.length === 0) {
    loadPresets()
  }
  bindVisible.value = true
}

async function handleBind() {
  if (!bindForm.emailAddress.trim()) return ElMessage.warning('请输入邮箱地址')
  if (!bindForm.imapHost.trim()) return ElMessage.warning('请输入 IMAP 服务器')
  if (!bindForm.imapUsername.trim()) return ElMessage.warning('请输入 IMAP 用户名')
  if (!bindForm.imapPassword) return ElMessage.warning('请输入 IMAP 密码')
  binding.value = true
  try {
    await bindEmailAccount({ ...bindForm })
    ElMessage.success('绑定成功')
    bindVisible.value = false
    await loadAccounts()
  } catch (e: any) {
    ElMessage.error('绑定失败：' + (e?.message || ''))
  } finally {
    binding.value = false
  }
}

async function handleTest() {
  if (!bindForm.imapHost.trim()) return ElMessage.warning('请输入 IMAP 服务器')
  if (!bindForm.imapUsername.trim()) return ElMessage.warning('请输入 IMAP 用户名')
  if (!bindForm.imapPassword) return ElMessage.warning('请输入 IMAP 密码')
  testing.value = true
  try {
    await testEmailConnection({ ...bindForm })
    ElMessage.success('连接成功：IMAP 凭据有效')
  } catch (e: any) {
    ElMessage.error('连接失败：' + (e?.message || ''))
  } finally {
    testing.value = false
  }
}

async function handleUnbind(row: EmailAccountVO) {
  try {
    await ElMessageBox.confirm(
      `确认解绑「${row.emailAddress}」吗？此操作不会影响真实邮箱账户。`,
      '提示',
      { type: 'warning' },
    )
  } catch { return }
  try {
    await unbindEmailAccount(row.id)
    ElMessage.success('已解绑')
    await loadAccounts()
  } catch (e: any) {
    ElMessage.error('解绑失败：' + (e?.message || ''))
  }
}

function goInbox(accountId: number) {
  router.push({ name: 'EmailInbox', query: { accountId: String(accountId) } })
}

// v2.13.3：打开通知偏好弹窗，回填当前账户的设置
function openNotify(row: EmailAccountVO) {
  notifyForm.accountId = row.id
  notifyForm.emailAddress = row.emailAddress
  notifyForm.enabled = row.notifyEnabled ?? 0
  notifyForm.typeArr = (row.notifyTypes || '')
    .split(',')
    .map((s: string) => s.trim())
    .filter((s: string) => s.length > 0)
  notifyForm.smtpHost = row.smtpHost || ''
  notifyForm.smtpPort = row.smtpPort ?? null
  notifyVisible.value = true
}

async function handleSaveNotify() {
  if (notifyForm.accountId == null) return
  savingNotify.value = true
  try {
    await updateNotifySettings(notifyForm.accountId, {
      enabled: notifyForm.enabled,
      types: notifyForm.typeArr.join(','),
      smtpHost: notifyForm.smtpHost,
      smtpPort: notifyForm.smtpPort ?? undefined,
    })
    ElMessage.success('通知偏好已保存')
    notifyVisible.value = false
    await loadAccounts()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    savingNotify.value = false
  }
}

async function handleSendTest() {
  if (notifyForm.accountId == null) return
  // 若用户改了 SMTP 配置但还没保存，提示先保存
  sendingTest.value = true
  try {
    const res = await sendTestNotification(notifyForm.accountId)
    ElMessage.success(res.message || '测试邮件已发送')
  } catch (e: any) {
    ElMessage.error('发送失败：' + (e?.message || '请检查 SMTP 配置或授权码'))
  } finally {
    sendingTest.value = false
  }
}

async function handleDebug(row: EmailAccountVO) {
  try {
    const res = await request.get(`/email/accounts/${row.id}/debug`)
    ElMessage.success(res.data || '无数据')
  } catch (e: any) {
    ElMessage.error('诊断失败：' + (e?.message || ''))
  }
}

async function handleDeepDebug(row: EmailAccountVO) {
  deepDebugVisible.value = true
  deepDebugContent.value = '诊断中...'
  try {
    const res = await request.get(`/email/accounts/${row.id}/deepdebug`, { timeout: 30000 })
    deepDebugContent.value = res.data || '无数据'
  } catch (e: any) {
    deepDebugContent.value = '诊断失败：' + (e?.message || '网络异常，可能 IMAP 连接超时')
  }
}

function formatTime(s?: string): string {
  if (!s) return ''
  const d = new Date(s)
  return isNaN(d.getTime()) ? '' : d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAccounts()
  loadPresets()
})
</script>

<style scoped>
.email-accts-page { padding: 24px; max-width: 1080px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.section { margin-bottom: 16px; }
.deep-debug-content {
  margin: 0;
  padding: 12px;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f6f8fa;
  border-radius: 4px;
}
</style>
