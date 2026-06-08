<template>
  <div class="story-card" :style="{ borderLeftColor: TYPE_BAR_COLOR.STORY }">
    <div class="story-card__header">
      <div class="story-card__key-row">
        <el-icon :size="14"><Notebook /></el-icon>
        <span class="story-card__key">{{ issue.issueKey }}</span>
        <el-tag size="small" type="success" effect="plain">{{ TYPE_LABEL.STORY }}</el-tag>
        <el-tag v-if="issue.epic" size="small" type="info" effect="plain">{{ issue.epic }}</el-tag>
      </div>
    </div>
    <div class="story-card__title">{{ issue.title }}</div>
    <div class="story-card__meta">
      <span v-if="issue.userRole" class="story-card__role">{{ issue.userRole }}</span>
      <span v-if="issue.storyPoints" class="story-card__points">
        <el-icon :size="12"><Coin /></el-icon> {{ issue.storyPoints }} pts
      </span>
    </div>
    <!-- Acceptance criteria progress -->
    <div v-if="acCount > 0" class="story-card__progress">
      <el-progress :percentage="acPercent" :stroke-width="6" :show-text="false" />
      <span class="story-card__progress-text">{{ acDone }}/{{ acCount }} 验收标准</span>
    </div>
    <!-- Sub-tasks count -->
    <div v-if="issue.subTasks && issue.subTasks.length > 0" class="story-card__subtasks">
      <el-icon :size="12"><List /></el-icon>
      <span>{{ issue.subTasks.length }} 个子任务</span>
    </div>
    <div class="story-card__footer">
      <div class="story-card__labels">
        <el-tag v-for="l in issue.labels" :key="l.id" size="small" :color="l.color" effect="light">
          {{ l.label }}
        </el-tag>
      </div>
      <div class="story-card__assignee" v-if="issue.assigneeName">
        <el-avatar :size="20" :src="issue.assigneeAvatar">{{ issue.assigneeName?.[0] }}</el-avatar>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { IssueCard } from '@/types'
import { TYPE_LABEL, TYPE_BAR_COLOR } from '@/utils/constants'
import { Notebook, Coin, List } from '@element-plus/icons-vue'

const props = defineProps<{ issue: IssueCard }>()

const acList = computed(() => props.issue.acceptanceCriteria || [])
const acCount = computed(() => acList.value.length)
const acDone = computed(() => acList.value.filter(a => a.done).length)
const acPercent = computed(() => acCount.value > 0 ? Math.round((acDone.value / acCount.value) * 100) : 0)
</script>

<style scoped lang="scss">
.story-card {
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
  &__header { margin-bottom: 6px; }
  &__key-row { display: flex; align-items: center; gap: 6px; font-size: 12px; }
  &__key { color: var(--text-secondary); font-family: monospace; font-weight: 600; }
  &__title {
    font-weight: 600; font-size: 14px; color: var(--text-primary);
    margin-bottom: 6px; line-height: 1.4;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  &__meta { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }
  &__role { font-style: italic; }
  &__points { display: flex; align-items: center; gap: 2px; }
  &__progress { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
  &__progress-text { font-size: 11px; color: var(--text-secondary); white-space: nowrap; }
  &__subtasks { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }
  &__footer { display: flex; justify-content: space-between; align-items: center; }
  &__labels { display: flex; gap: 3px; flex-wrap: wrap; max-width: 70%; }
  &__assignee { flex-shrink: 0; }
}
</style>
