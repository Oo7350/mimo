<template>
  <div class="comment-section">
    <!-- Comment list -->
    <div v-if="loading" style="text-align: center; padding: 20px; color: var(--text-secondary)">
      加载中...
    </div>
    <div v-else-if="comments.length === 0" class="comment-section__empty">
      暂无评论，快来添加第一条吧
    </div>
    <div v-else class="comment-section__list">
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <div class="comment-item__avatar">
          <el-avatar :size="28">{{ c.username?.[0] }}</el-avatar>
        </div>
        <div class="comment-item__body">
          <div class="comment-item__header">
            <strong>{{ c.username }}</strong>
            <span class="comment-item__time">{{ c.createdAt }}</span>
            <el-button
              v-if="c.userId === currentUserId"
              type="danger"
              link
              size="small"
              :loading="deletingId === c.id"
              @click="handleDelete(c.id)"
              style="margin-left: auto; padding: 0 4px;"
            >删除</el-button>
          </div>
          <div class="comment-item__content" v-html="renderMarkdown(c.content)"></div>
        </div>
      </div>
    </div>

    <!-- Comment input -->
    <div class="comment-section__input">
      <el-input
        v-model="text"
        type="textarea"
        :rows="2"
        placeholder="输入评论，Ctrl+Enter 发送"
        @keydown="onKeydown"
      />
      <el-button
        type="primary"
        size="small"
        :loading="sending"
        @click="send"
        style="margin-top: 8px"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { createComment, deleteComment } from '@/api/comment'
import { renderMarkdown } from '@/utils/markdown'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  issueId: number
  comments: any[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'comment-added': [comment: any]
  'comment-deleted': [commentId: number]
}>()

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)
const text = ref('')
const sending = ref(false)
const deletingId = ref<number | null>(null)

async function send() {
  if (!text.value.trim()) return
  sending.value = true
  try {
    const res = await createComment({ issueId: props.issueId, content: text.value.trim() })
    emit('comment-added', res.data)
    text.value = ''
  } catch { /* handled by interceptor */ }
  finally { sending.value = false }
}

async function handleDelete(commentId: number) {
  try {
    await ElMessageBox.confirm('确定删除这条评论？', '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  deletingId.value = commentId
  try {
    await deleteComment(commentId)
    emit('comment-deleted', commentId)
    ElMessage.success('已删除')
  } catch { /* handled by interceptor */ }
  finally { deletingId.value = null }
}

function onKeydown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    send()
  }
}
</script>

<style scoped lang="scss">
.comment-section {
  min-height: 120px;
  &__empty { text-align: center; padding: 24px; color: var(--text-secondary); font-size: 14px; }
  &__list { max-height: 280px; overflow-y: auto; margin-bottom: 12px; }
  &__input { border-top: 1px solid var(--border-color-light); padding-top: 12px; }
}
.comment-item {
  display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--border-color-light);
  &:last-child { border-bottom: none; }
  &__avatar { flex-shrink: 0; }
  &__body { flex: 1; min-width: 0; }
  &__header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 4px;
    strong { font-size: 13px; color: var(--text-primary); }
  }
  &__time { font-size: 11px; color: var(--text-placeholder); }
  &__content { font-size: 14px; color: var(--text-regular); white-space: pre-wrap; word-break: break-word; }
}
</style>
