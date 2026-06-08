<template>
  <div class="task-card" :style="{ borderLeftColor: TYPE_BAR_COLOR.TASK }">
    <div class="task-card__header">
      <span class="task-card__key">{{ issue.issueKey }}</span>
      <el-tag size="small" type="primary" effect="plain">{{ TYPE_LABEL.TASK }}</el-tag>
      <span v-if="issue.parentIssueKey" class="task-card__parent">&larr; {{ issue.parentIssueKey }}</span>
    </div>
    <div class="task-card__title">{{ issue.title }}</div>
    <div class="task-card__meta">
      <span v-if="issue.assigneeName" class="task-card__assignee-label">
        <el-avatar :size="16" :src="issue.assigneeAvatar">{{ issue.assigneeName?.[0] }}</el-avatar>
        {{ issue.assigneeName }}
      </span>
      <span v-if="issue.dueDate" :class="['task-card__due', { 'is-overdue': isOverdue }]">
        <el-icon :size="12"><Calendar /></el-icon>
        {{ issue.dueDate }}
      </span>
      <span v-if="issue.storyPoints" class="task-card__points">{{ issue.storyPoints }} pts</span>
    </div>
    <div class="task-card__footer">
      <div class="task-card__labels">
        <el-tag v-for="l in issue.labels" :key="l.id" size="small" :color="l.color" effect="light">
          {{ l.label }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { IssueCard } from '@/types'
import { TYPE_LABEL, TYPE_BAR_COLOR } from '@/utils/constants'
import { Calendar } from '@element-plus/icons-vue'

const props = defineProps<{ issue: IssueCard }>()

const isOverdue = computed(() => {
  if (!props.issue.dueDate) return false
  return new Date(props.issue.dueDate) < new Date()
})
</script>

<style scoped lang="scss">
.task-card {
  background: var(--bg-card);
  border-radius: var(--border-radius-sm);
  padding: 10px 12px;
  border-left: 3px solid;
  margin-bottom: 8px;
  cursor: pointer;
  transition: transform .15s, box-shadow .15s;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0,0,0,.08);
  }
  &__header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; font-weight: 600; }
  &__parent { font-size: 10px; color: var(--text-secondary); }
  &__title {
    font-weight: 600; font-size: 14px; color: var(--text-primary);
    margin-bottom: 6px; line-height: 1.4;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  &__meta { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; flex-wrap: wrap; }
  &__assignee-label { display: flex; align-items: center; gap: 4px; }
  &__due { display: flex; align-items: center; gap: 3px; }
  &__due.is-overdue { color: var(--color-danger); font-weight: 600; }
  &__points { font-weight: 600; }
  &__footer { display: flex; }
  &__labels { display: flex; gap: 3px; flex-wrap: wrap; }
}
</style>
