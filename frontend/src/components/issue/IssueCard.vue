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
        <el-tooltip v-if="issue.status !== 'DONE'" content="标记完成" placement="top">
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
import { ref } from 'vue'
import type { IssueCard } from '@/types'
import StoryCard from './story/StoryCard.vue'
import BugCard from './bug/BugCard.vue'
import TaskCard from './task/TaskCard.vue'
import { EditPen, Check } from '@element-plus/icons-vue'

defineProps<{ issue: IssueCard }>()
defineEmits<{
  click: [issue: IssueCard]
  edit: [issue: IssueCard]
  complete: [issue: IssueCard]
}>()

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
