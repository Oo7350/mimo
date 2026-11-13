<template>
  <div class="review-panel">
    <div class="review-panel__header">
      <div>
        <div class="review-panel__title">任务验收</div>
        <div class="review-panel__sub">
          提交完成说明和证据，由项目管理员或报告人验收
        </div>
      </div>
      <el-button type="primary" @click="dialogVisible = true">提交验收</el-button>
    </div>

    <el-empty v-if="!reviews.length" description="暂无验收记录" :image-size="60" />

    <div v-for="item in reviews" :key="item.id" class="review-card">
      <div class="review-card__top">
        <div>
          <b>第 {{ item.roundNo }} 轮验收</b>
          <span class="muted"> · {{ item.submitterName }} · {{ formatTime(item.submittedAt) }}</span>
        </div>
        <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
      </div>
      <div v-if="item.completionNote" class="review-card__note">{{ item.completionNote }}</div>
      <div v-if="item.evidenceUrls.length" class="review-card__evidence">
        <a
          v-for="(url, index) in item.evidenceUrls"
          :key="url"
          :href="url"
          target="_blank"
          rel="noreferrer"
        >
          验收证据 {{ index + 1 }}
        </a>
      </div>
      <div v-if="item.reviewNote" class="review-card__result">
        验收意见：{{ item.reviewNote }}
      </div>
      <div v-if="item.status === 'PENDING'" class="review-card__actions">
        <el-button size="small" type="success" @click="approve(item)">通过</el-button>
        <el-button size="small" type="danger" @click="reject(item)">打回</el-button>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="提交任务验收" width="560px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="完成说明">
          <el-input
            v-model="form.completionNote"
            type="textarea"
            :rows="4"
            placeholder="说明完成内容、验证方式和需要关注的点"
          />
        </el-form-item>
        <el-form-item label="验收证据链接">
          <el-input
            v-model="form.evidenceText"
            type="textarea"
            :rows="3"
            placeholder="每行一个链接，例如截图、测试报告或文档地址"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交验收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveIssueReview,
  listIssueReviews,
  rejectIssueReview,
  submitIssueReview,
} from '@/api/issue-governance'
import type { IssueReview } from '@/types/governance'

const props = defineProps<{ issueId: number }>()
const emit = defineEmits(['updated'])

const reviews = ref<IssueReview[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ completionNote: '', evidenceText: '' })

async function load() {
  if (!props.issueId) return
  const res = await listIssueReviews(props.issueId)
  reviews.value = res.data || []
}

async function submit() {
  const evidenceUrls = form.evidenceText
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (!form.completionNote.trim() && !evidenceUrls.length) {
    ElMessage.warning('请填写完成说明或提供验收证据')
    return
  }
  submitting.value = true
  try {
    await submitIssueReview(props.issueId, {
      completionNote: form.completionNote.trim(),
      evidenceUrls,
    })
    ElMessage.success('验收申请已提交')
    dialogVisible.value = false
    form.completionNote = ''
    form.evidenceText = ''
    await load()
    emit('updated')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function approve(item: IssueReview) {
  try {
    const result = await ElMessageBox.prompt('可填写验收说明', '通过验收', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPlaceholder: '验收说明（可选）',
    })
    await approveIssueReview(item.id, result.value)
    ElMessage.success('验收已通过')
    await load()
    emit('updated')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '操作失败')
  }
}

async function reject(item: IssueReview) {
  try {
    const result = await ElMessageBox.prompt('请填写打回原因', '打回验收', {
      confirmButtonText: '打回',
      cancelButtonText: '取消',
      inputPlaceholder: '打回原因',
      inputValidator: (value) => Boolean(value?.trim()) || '打回原因不能为空',
    })
    await rejectIssueReview(item.id, result.value)
    ElMessage.success('已打回')
    await load()
    emit('updated')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '操作失败')
  }
}

function statusLabel(status: string) {
  return { PENDING: '待验收', APPROVED: '已通过', REJECTED: '已打回' }[status] || status
}

function statusType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

watch(() => props.issueId, load, { immediate: true })
</script>

<style scoped>
.review-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.review-panel__title {
  font-weight: 700;
  color: var(--text-primary);
}
.review-panel__sub,
.muted {
  color: var(--text-secondary);
  font-size: 12px;
}
.review-card {
  border: 1px solid var(--border-color-light);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
}
.review-card__top,
.review-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.review-card__note,
.review-card__result {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-regular);
  white-space: pre-wrap;
}
.review-card__result {
  color: var(--text-secondary);
}
.review-card__evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.review-card__evidence a {
  color: var(--el-color-primary);
  font-size: 12px;
}
.review-card__actions {
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
