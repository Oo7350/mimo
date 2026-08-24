<template>
  <div class="inbox-page">
    <div class="inbox-header">
      <h2>收件箱</h2>
      <div class="inbox-header__actions">
        <el-select
          v-model="selectedAccountId"
          placeholder="选择邮箱账户"
          style="width: 260px"
          @change="reload"
        >
          <el-option
            v-for="a in accounts"
            :key="a.id"
            :label="a.emailAddress + (a.isDefault === 1 ? ' (默认)' : '')"
            :value="a.id"
          />
        </el-select>
        <el-input-number
          v-model="limit"
          :min="5"
          :max="100"
          :step="5"
          style="width: 120px"
          @change="reload"
        />
        <el-button type="primary" :loading="loading" @click="reload">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !currentAccount" description="尚未绑定任何邮箱，请先去「邮箱绑定」页绑定" >
      <el-button type="primary" @click="goBind">前往绑定</el-button>
    </el-empty>

    <div v-else-if="!loading && messages.length === 0" class="empty-tip">
      <el-empty description="收件箱为空" />
    </div>

    <div v-else v-loading="loading" class="inbox-body">
      <!-- 邮件列表 -->
      <div class="mail-list">
        <div
          v-for="m in messages"
          :key="m.messageSeq"
          class="mail-item"
          :class="{ 'mail-item--unread': !m.seen, 'mail-item--active': selectedSeq === m.messageSeq }"
          @click="loadDetail(m)"
        >
          <div class="mail-item__row1">
            <span class="mail-item__from">{{ formatFrom(m) }}</span>
            <span class="mail-item__date">{{ formatTime(m.sentDate) }}</span>
          </div>
          <div class="mail-item__subject">{{ m.subject || '(无主题)' }}</div>
          <div v-if="m.snippet" class="mail-item__snippet">{{ m.snippet }}</div>
        </div>
      </div>

      <!-- 邮件详情 -->
      <div class="mail-detail">
        <el-empty v-if="!detail" description="点击左侧邮件查看详情" />
        <div v-else>
          <h3 class="mail-detail__subject">{{ detail.subject || '(无主题)' }}</h3>
          <div class="mail-detail__meta">
            <span><strong>发件人：</strong>{{ formatAddr(detail.from, detail.fromPersonal) }}</span>
            <span v-if="detail.to"><strong>收件人：</strong>{{ detail.to }}</span>
            <span v-if="detail.sentDate"><strong>时间：</strong>{{ formatTime(detail.sentDate) }}</span>
          </div>
          <el-divider />
          <div v-if="detail.textBody" class="mail-detail__text">{{ detail.textBody }}</div>
          <div v-else-if="detail.htmlBody" class="mail-detail__html" v-html="sanitizedHtml"></div>
          <el-empty v-else description="(无正文)" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  listEmailAccounts,
  getInbox,
  getMailDetail,
  type EmailAccountVO,
  type MailSummaryVO,
  type MailDetailVO,
} from '@/api/email'

const route = useRoute()
const router = useRouter()
const accounts = ref<EmailAccountVO[]>([])
const selectedAccountId = ref<number | undefined>(undefined)
const currentAccount = ref<EmailAccountVO | undefined>(undefined)
const messages = ref<MailSummaryVO[]>([])
const detail = ref<MailDetailVO | null>(null)
const selectedSeq = ref<number | null>(null)
const loading = ref(false)
const limit = ref(20)

async function loadAccounts() {
  try {
    const res = await listEmailAccounts()
    accounts.value = res.data || []
    if (accounts.value.length === 0) return
    const fromQuery = route.query.accountId
    const defaultAcc = accounts.value.find(a => a.isDefault === 1) || accounts.value[0]
    if (fromQuery) {
      const matched = accounts.value.find(a => a.id === Number(fromQuery)
      )
      selectedAccountId.value = matched ? matched.id : defaultAcc.id
    } else {
      selectedAccountId.value = defaultAcc.id
    }
  } catch (e: any) {
    ElMessage.error('加载邮箱账户失败：' + (e?.message || ''))
  }
}

async function reload() {
  if (!selectedAccountId.value) return
  detail.value = null
  selectedSeq.value = null
  loading.value = true
  try {
    const res = await getInbox(selectedAccountId.value, limit.value)
    currentAccount.value = res.data?.account
    messages.value = res.data?.messages || []
  } catch (e: any) {
    ElMessage.error('拉取收件箱失败：' + (e?.message || ''))
    messages.value = []
  } finally {
    loading.value = false
  }
}

async function loadDetail(m: MailSummaryVO) {
  if (!selectedAccountId.value) return
  selectedSeq.value = m.messageSeq
  try {
    const res = await getMailDetail(selectedAccountId.value, m.messageSeq)
    detail.value = res.data
  } catch (e: any) {
    ElMessage.error('加载邮件详情失败：' + (e?.message || ''))
  }
}

function goBind() {
  router.push({ name: 'EmailAccounts' })
}

function formatFrom(m: MailSummaryVO): string {
  return formatAddr(m.from, m.fromPersonal) || '(未知发件人)'
}

function formatAddr(addr?: string, personal?: string): string {
  if (!addr) return ''
  return personal ? `${personal} <${addr}>` : addr
}

function formatTime(s?: string): string {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay
    ? d.toLocaleTimeString('zh-CN', { hour12: false })
    : d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
}

// 简单 HTML 清理：移除 script/iframe/object/embed，避免 XSS
const sanitizedHtml = computed(() => {
  if (!detail.value?.htmlBody) return ''
  return detail.value.htmlBody
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[\s\S]*?(?:\/?>)/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
})

watch(() => route.query.accountId, async (v) => {
  if (v && typeof v === 'string') {
    const matched = accounts.value.find(a => a.id === Number(v))
    if (matched) {
      selectedAccountId.value = matched.id
      await reload()
    }
  }
})

onMounted(async () => {
  await loadAccounts()
  if (selectedAccountId.value) await reload()
})
</script>

<style scoped>
.inbox-page { padding: 24px; max-width: 1280px; margin: 0 auto; height: calc(100vh - 60px); display: flex; flex-direction: column; }
.inbox-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.inbox-header h2 { margin: 0; }
.inbox-header__actions { display: flex; gap: 8px; align-items: center; }
.inbox-body { flex: 1; display: flex; gap: 12px; overflow: hidden; }
.mail-list { width: 360px; overflow-y: auto; border-right: 1px solid var(--el-border-color-lighter); padding-right: 8px; }
.mail-item { padding: 12px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.mail-item:hover { background: var(--el-fill-color-light); }
.mail-item--unread { background: var(--el-color-primary-light-9); }
.mail-item--unread .mail-item__subject { font-weight: 600; }
.mail-item--active { border-color: var(--el-color-primary); }
.mail-item__row1 { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.mail-item__from { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mail-item__date { white-space: nowrap; }
.mail-item__subject { font-size: 14px; color: var(--el-text-color-primary); margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mail-item__snippet { font-size: 12px; color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mail-detail { flex: 1; overflow-y: auto; padding: 16px; background: var(--el-bg-color-page); border-radius: 6px; }
.mail-detail__subject { margin: 0 0 8px 0; }
.mail-detail__meta { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.mail-detail__text { white-space: pre-wrap; line-height: 1.7; font-size: 14px; }
.mail-detail__html { line-height: 1.7; font-size: 14px; }
.mail-detail__html :deep(img) { max-width: 100%; }
.empty-tip { flex: 1; display: flex; align-items: center; justify-content: center; }
</style>
