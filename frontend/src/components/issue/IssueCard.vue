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
import { EditPen, Check } from '@element-plus/icons-vue'

const props = defineProps<{ issue: IssueCard }>()
defineEmits<{
  click: [issue: IssueCard]
  edit: [issue: IssueCard]
  complete: [issue: IssueCard]
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
  margin-bottom: 10px;
  border-radius: var(--border-radius-md);
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);

  &:hover {
    transform: translateY(-3px) scale(1.01);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.1), 0 2px 6px rgba(79, 70, 229, 0.06);
  }
}

.issue-card__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 5px;
  z-index: 10;
}

.issue-card__action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow:
    0 4px 14px rgba(0, 0, 0, 0.12),
    0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

  &:hover {
    color: var(--color-primary);
    transform: scale(1.15) rotate(-5deg);
    box-shadow: 0 6px 18px rgba(79, 70, 229, 0.25);
  }

  &--done:hover {
    color: var(--color-success);
    box-shadow: 0 6px 18px rgba(16, 185, 129, 0.25);
  }

  &--danger:hover {
    color: var(--color-danger, #ef4444);
  }
}

.actions-fade-enter-active,
.actions-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1) ease;
}
.actions-fade-enter-from,
.actions-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.85);
}
</style>
