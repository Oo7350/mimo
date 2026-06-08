<template>
  <div class="bug-card" :style="{ borderLeftColor: TYPE_BAR_COLOR.BUG }">
    <div class="bug-card__header">
      <span class="bug-card__key">{{ issue.issueKey }}</span>
      <el-tag size="small" type="danger" effect="plain">
        <el-icon :size="12"><WarningFilled /></el-icon> {{ TYPE_LABEL.BUG }}
      </el-tag>
      <el-tag v-if="issue.severity" size="small" :type="sevColor" effect="dark">
        {{ SEVERITY_LABEL[issue.severity] || issue.severity }}
      </el-tag>
      <el-tag v-if="issue.bugStatus" size="small" :type="BUG_STATUS_COLOR[issue.bugStatus]" effect="plain">
        {{ BUG_STATUS_LABEL[issue.bugStatus] || issue.bugStatus }}
      </el-tag>
    </div>
    <div class="bug-card__title">{{ issue.title }}</div>
    <div class="bug-card__meta">
      <span v-if="issue.environment" class="bug-card__env" :title="issue.environment">
        <el-icon :size="12"><Monitor /></el-icon> {{ issue.environment }}
      </span>
      <span v-if="issue.foundVersion" class="bug-card__version">v{{ issue.foundVersion }}</span>
      <span v-if="issue.assigneeName" class="bug-card__assignee">
        <el-avatar :size="16" :src="issue.assigneeAvatar">{{ issue.assigneeName?.[0] }}</el-avatar>
        {{ issue.assigneeName }}
      </span>
    </div>
    <div class="bug-card__footer">
      <div class="bug-card__labels">
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
import {
  TYPE_LABEL, TYPE_BAR_COLOR,
  SEVERITY_LABEL, SEVERITY_COLOR,
  BUG_STATUS_LABEL, BUG_STATUS_COLOR
} from '@/utils/constants'
import { WarningFilled, Monitor } from '@element-plus/icons-vue'

const props = defineProps<{ issue: IssueCard }>()

const sevColor = computed(() => SEVERITY_COLOR[props.issue.severity || ''] || '')
</script>

<style scoped lang="scss">
.bug-card {
  background: var(--bg-card);
  border-radius: var(--border-radius-sm);
  padding: 10px 12px;
  border-left: 3px solid;
  margin-bottom: 8px;
  cursor: pointer;
  transition: transform .15s, box-shadow .15s;
  position: relative;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0,0,0,.08);
  }
  &__header { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; flex-wrap: wrap; }
  &__key { font-size: 11px; color: var(--text-secondary); font-family: monospace; font-weight: 600; margin-right: 4px; }
  &__title {
    font-weight: 600; font-size: 14px; color: var(--text-primary);
    margin-bottom: 6px; line-height: 1.4;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  &__meta { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; flex-wrap: wrap; }
  &__env { display: flex; align-items: center; gap: 3px; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &__version { font-family: monospace; }
  &__assignee { display: flex; align-items: center; gap: 4px; }
  &__footer { display: flex; justify-content: space-between; align-items: center; }
  &__labels { display: flex; gap: 3px; flex-wrap: wrap; max-width: 70%; }
}
</style>
