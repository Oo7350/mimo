<template>
  <div
    class="issue-card-wrapper"
    @click="$emit('click', issue)"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
  >
    <StoryCard v-if="issue.type === 'STORY'" :issue="issue" />
    <BugCard v-else-if="issue.type === 'BUG'" :issue="issue" />
    <TaskCard v-else :issue="issue" />

    <!-- Hover 快捷操作 -->
    <Transition name="actions-fade">
      <div v-if="hovering" class="issue-card__actions" @click.stop @mousedown.stop>
        <el-tooltip v-if="!isDone" content="标记完成" placement="top">
          <button class="issue-card__action issue-card__action--done" @click.stop="$emit('complete', issue)">
            <el-icon :size="14"><Check /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip content="编辑" placement="top">
          <button class="issue-card__action" @click.stop="$emit('edit', issue)">
            <el-icon :size="14"><EditPen /></el-icon>
          </button>
        </el-tooltip>
        <el-popconfirm title="确定删除此工作项？" confirm-button-text="确定" cancel-button-text="取消" @confirm="$emit('delete', issue)">
          <template #reference>
            <el-tooltip content="删除" placement="top">
              <button class="issue-card__action issue-card__action--danger">
                <el-icon :size="14"><Delete /></el-icon>
              </button>
            </el-tooltip>
          </template>
        </el-popconfirm>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { IssueCard } from '@/types'
import StoryCard from './story/StoryCard.vue'
import BugCard from './bug/BugCard.vue'
import TaskCard from './task/TaskCard.vue'
import { EditPen, Check, Delete } from '@element-plus/icons-vue'

const props = defineProps<{ issue: IssueCard }>()
defineEmits<{
  click: [issue: IssueCard]
  edit: [issue: IssueCard]
  complete: [issue: IssueCard]
  delete: [issue: IssueCard]
}>()

// BUG 类型用 bugStatus 判断，其他类型用 status 判断
const isDone = computed(() => {
  if (props.issue.type === 'BUG') return props.issue.bugStatus === 'CLOSED'
  return props.issue.status === 'DONE'
})

const hovering = ref(false)
</script>

<style scoped lang="scss">
.issue-card-wrapper {
  position: relative;
  margin-bottom: 8px;
}

.issue-card__actions {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  gap: 4px;
  z-index: 2;
}

.issue-card__action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 7px;
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all var(--transition-fast);

  &:hover {
    color: var(--color-primary);
    transform: scale(1.08);
  }

  &--done:hover {
    color: var(--color-success);
  }

  &--danger:hover {
    color: var(--color-danger, #ef4444);
  }
}

.actions-fade-enter-active,
.actions-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.actions-fade-enter-from,
.actions-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
