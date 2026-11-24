<template>
  <div class="issue-redirect">
    <div v-if="state === 'loading'" class="issue-redirect__box">
      <el-icon class="is-loading issue-redirect__spinner"><Loading /></el-icon>
      <p class="issue-redirect__text">正在打开工作项…</p>
    </div>

    <div v-else class="issue-redirect__box">
      <el-icon class="issue-redirect__icon"><WarningFilled /></el-icon>
      <p class="issue-redirect__title">打不开这个工作项</p>
      <p class="issue-redirect__text">{{ message }}</p>
      <div class="issue-redirect__actions">
        <el-button type="primary" @click="router.push('/')">回工作台</el-button>
        <el-button @click="router.push('/projects')">去项目列表</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作项落地页 `/issues/:id`
 *
 * 存在的理由：通知邮件（EmailNotificationService#buildActionUrl）、站内通知、
 * 以及任何外部链接都只拿得到 issueId，而看板详情是 `/projects/{projectId}/board?issue={id}`。
 * 以前后端直接拼 `/issues/{id}`，前端却没有这条路由 —— 点开一律落到登录页或工作台，链接等于作废。
 *
 * 这里只做一件事：用 issueId 反查 projectId，然后 replace 到看板并打开详情。
 * 用 replace 而不是 push，避免用户"返回"时又回到这个中转页。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getIssueById } from '@/api/issue'
import { Loading, WarningFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const state = ref<'loading' | 'error'>('loading')
const message = ref('')

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id || Number.isNaN(id)) {
    state.value = 'error'
    message.value = '链接里的工作项编号不对'
    return
  }
  try {
    const res = await getIssueById(id)
    const projectId = res.data?.projectId
    if (!projectId) {
      state.value = 'error'
      message.value = '这个工作项没有关联项目，可能已被删除'
      return
    }
    await router.replace(`/projects/${projectId}/board?issue=${id}`)
  } catch (e: any) {
    state.value = 'error'
    // 后端对"无权访问"返回 403，对不存在返回 404 —— 都给出可执行的下一步，而不是白屏
    message.value = e?.response?.status === 403
      ? '你没有这个工作项的访问权限，请联系项目管理员'
      : '工作项不存在，或者已经被删除'
  }
})
</script>

<style scoped>
.issue-redirect {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 24px;
}

.issue-redirect__box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  max-width: 420px;
  text-align: center;
}

.issue-redirect__spinner,
.issue-redirect__icon {
  font-size: 34px;
  color: var(--text-secondary);
}

.issue-redirect__icon {
  color: var(--color-warning);
}

.issue-redirect__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.issue-redirect__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.issue-redirect__actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}
</style>
