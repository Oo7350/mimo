<template>
  <div
    :class="['issue-card', { 'issue-card--dragging': dragging }]"
    @click="$emit('click', issue)"
  >
    <!-- 优先级色条 -->
    <div class="issue-card__bar" :style="{ background: priorityBarColor }" />

    <div class="issue-card__body">
      <!-- 头部：issueKey + 类型标签 -->
      <div class="issue-card__header">
        <span class="issue-card__key">{{ issue.issueKey }}</span>
        <el-tag size="small" :type="typeTagColor">
          <template v-if="issue.type === 'BUG'">🐛 </template>
          {{ typeLabel }}
        </el-tag>
      </div>

      <!-- 标题 -->
      <div class="issue-card__title">{{ issue.title }}</div>

      <!-- 底部行 -->
      <div class="issue-card__footer">
        <div class="issue-card__labels">
          <el-tag
            v-for="lbl in (issue.labels || [])"
            :key="lbl.id"
            size="small"
            :color="lbl.color"
            effect="dark"
            class="issue-card__tag"
          >
            {{ lbl.label }}
          </el-tag>
        </div>
        <div class="issue-card__meta">
          <span v-if="issue.storyPoints" class="issue-card__points">
            {{ issue.storyPoints }}
          </span>
          <AssigneeAvatar
            v-if="issue.assigneeName"
            :name="issue.assigneeName"
            :size="22"
          />
        </div>
      </div>
    </div>

    <!-- 悬停编辑图标 -->
    <div class="issue-card__edit" @click.stop="$emit('edit', issue)">
      <el-icon :size="14"><Edit /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { IssueCard as IssueCardType } from '@/types'
import { TYPE_LABEL, TYPE_COLOR, PRIORITY_BAR_COLOR } from '@/utils/constants'
import AssigneeAvatar from '@/components/common/AssigneeAvatar.vue'

const props = withDefaults(defineProps<{
  issue: IssueCardType
  dragging?: boolean
}>(), {
  dragging: false,
})

defineEmits<{
  click: [issue: IssueCardType]
  edit: [issue: IssueCardType]
}>()

const typeLabel = computed(() => TYPE_LABEL[props.issue.type] || props.issue.type)
const typeTagColor = computed(() => TYPE_COLOR[props.issue.type] || '')
const priorityBarColor = computed(() => PRIORITY_BAR_COLOR[props.issue.priority] || PRIORITY_BAR_COLOR.MEDIUM)
</script>

<style scoped lang="scss">
.issue-card {
  background: var(--bg-card);
  border-radius: var(--border-radius-sm);
  border: 1px solid var(--border-color-light);
  margin-bottom: 8px;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s cubic-bezier(0.4, 0, 0.2, 1), opacity var(--transition-normal);
  display: flex;
  position: relative;
  overflow: hidden;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    border-color: transparent;

    .issue-card__edit {
      opacity: 1;
    }
  }

  &--dragging {
    opacity: 0.4;
    transform: rotate(2deg);
  }

  &__bar {
    width: 3px;
    flex-shrink: 0;
    border-radius: var(--border-radius-sm) 0 0 var(--border-radius-sm);
  }

  &__body {
    padding: 10px 12px;
    flex: 1;
    min-width: 0;
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
  }

  &__key {
    font-size: 11px;
    color: var(--text-secondary);
  }

  &__title {
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text-primary);
  }

  &__footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 6px;
  }

  &__labels {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
    overflow: hidden;
    flex: 1;
  }

  &__tag {
    border: none;
    font-size: 11px;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }

  &__points {
    background: var(--color-primary-lighter);
    color: var(--color-primary);
    font-size: 11px;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: 10px;
  }

  &__edit {
    position: absolute;
    top: 6px;
    right: 6px;
    opacity: 0;
    transition: opacity var(--transition-fast);
    background: var(--bg-card);
    border-radius: 4px;
    padding: 3px;
    color: var(--text-secondary);
    cursor: pointer;
    z-index: 2;

    &:hover {
      color: var(--color-primary);
    }
  }
}
</style>
