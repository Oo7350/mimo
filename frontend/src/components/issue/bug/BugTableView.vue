<template>
  <div class="bug-table-view">
    <div class="bug-table-view__header">
      <div class="bug-table-view__filters">
        <el-select v-model="filterSeverity" size="small" clearable placeholder="严重程度" style="width: 120px">
          <el-option v-for="s in severities" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="filterBugStatus" size="small" clearable placeholder="缺陷状态" style="width: 120px">
          <el-option v-for="(label, key) in BUG_STATUS_LABEL" :key="key" :label="label" :value="key" />
        </el-select>
        <el-input v-model="localKeyword" size="small" placeholder="搜索..." clearable style="width: 160px" />
      </div>
      <el-button type="primary" size="small" @click="$emit('create-bug')">
        <el-icon><Plus /></el-icon> 新建缺陷
      </el-button>
    </div>

    <el-table
      :data="filteredBugs"
      style="width: 100%"
      height="calc(100vh - 200px)"
      @row-click="(row: any) => $emit('select', row)"
      highlight-current-row
      size="small"
    >
      <el-table-column prop="issueKey" label="编号" width="120" sortable />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="严重程度" width="100">
        <template #default="{ row }">
          <el-tag :type="sevColor(row.severity)" size="small">
            {{ sevLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="bsColor(row.bugStatus)" size="small">
            {{ bsLabel(row.bugStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="80">
        <template #default="{ row }">
          <el-tag :type="priColor(row.priority)" size="small" effect="plain">
            {{ priLabel(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assigneeName" label="指派人" width="100" />
      <el-table-column prop="foundVersion" label="发现版本" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="140" sortable />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { IssueCard, BugStatus, IssuePriority } from '@/types'
import {
  SEVERITY_LABEL, SEVERITY_COLOR,
  BUG_STATUS_LABEL, BUG_STATUS_COLOR,
  PRIORITY_LABEL, PRIORITY_COLOR,
} from '@/utils/constants'
import { Plus } from '@element-plus/icons-vue'

const props = defineProps<{
  bugs: IssueCard[]
  searchKeyword?: string
}>()

defineEmits<{
  'select': [issue: IssueCard]
  'create-bug': []
}>()

const filterSeverity = ref('')
const filterBugStatus = ref('')
const localKeyword = ref('')

const searchKeyword = computed(() => props.searchKeyword || localKeyword.value)

function sevLabel(s: string) { return (SEVERITY_LABEL as Record<string, string>)[s] || s }
function sevColor(s: string) { return (SEVERITY_COLOR as Record<string, string>)[s] || '' }
function bsLabel(s: string) { return (BUG_STATUS_LABEL as Record<string, string>)[s] || s || '新建' }
function bsColor(s: string) { return (BUG_STATUS_COLOR as Record<string, string>)[s] || '' }
function priLabel(p: string) { return (PRIORITY_LABEL as Record<string, string>)[p] || p }
function priColor(p: string) { return (PRIORITY_COLOR as Record<string, string>)[p] || '' }

const severities = [
  { label: '阻塞', value: 'BLOCKER' },
  { label: '严重', value: 'CRITICAL' },
  { label: '主要', value: 'MAJOR' },
  { label: '次要', value: 'MINOR' },
  { label: '轻微', value: 'TRIVIAL' },
]

const filteredBugs = computed(() => {
  return props.bugs.filter(b => {
    if (filterSeverity.value && b.severity !== filterSeverity.value) return false
    if (filterBugStatus.value && b.bugStatus !== filterBugStatus.value) return false
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase()
      return (
        (b.title?.toLowerCase() || '').includes(kw) ||
        (b.issueKey?.toLowerCase() || '').includes(kw) ||
        (b.assigneeName?.toLowerCase() || '').includes(kw)
      )
    }
    return true
  })
})
</script>

<style scoped lang="scss">
.bug-table-view {
  &__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  &__filters { display: flex; gap: 8px; }
}
</style>
