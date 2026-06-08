<template>
  <div @click="$emit('click', issue)" @mouseenter="hovering = true" @mouseleave="hovering = false" class="issue-card-wrapper">
    <StoryCard v-if="issue.type === 'STORY'" :issue="issue" />
    <BugCard v-else-if="issue.type === 'BUG'" :issue="issue" />
    <TaskCard v-else :issue="issue" />
    <!-- Edit icon overlay -->
    <div v-if="hovering" class="issue-card__edit-icon" @click.stop="$emit('edit', issue)">
      <el-icon :size="14"><EditPen /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { IssueCard } from '@/types'
import StoryCard from './story/StoryCard.vue'
import BugCard from './bug/BugCard.vue'
import TaskCard from './task/TaskCard.vue'
import { EditPen } from '@element-plus/icons-vue'

defineProps<{ issue: IssueCard }>()
defineEmits<{
  click: [issue: IssueCard]
  edit: [issue: IssueCard]
}>()

const hovering = ref(false)
</script>

<style scoped lang="scss">
.issue-card-wrapper { position: relative; }
.issue-card__edit-icon {
  position: absolute; top: 6px; right: 6px;
  background: var(--bg-card); border-radius: 50%; padding: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,.12); cursor: pointer;
  color: var(--text-secondary); z-index: 2;
  &:hover { color: var(--color-primary); }
}
</style>
