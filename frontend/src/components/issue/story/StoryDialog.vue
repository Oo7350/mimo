<template>
  <div class="story-form">
    <!-- User Story 3-part format -->
    <div class="story-form__three-parts">
      <span class="story-form__label">作为</span>
      <el-input v-model="form.userRole" placeholder="用户角色，如: 管理员" />
      <span class="story-form__label">我希望</span>
      <el-input v-model="form.userGoal" placeholder="功能描述，如: 导出月度报告" />
      <span class="story-form__label">以便</span>
      <el-input v-model="form.businessValue" placeholder="业务价值，如: 分析团队效率" />
    </div>

    <!-- Auto-composed title preview -->
    <div class="story-form__title-preview" v-if="form.userRole || form.userGoal || form.businessValue">
      <span class="story-form__title-label">故事标题预览:</span>
      <span class="story-form__title-text">{{ composedTitle }}</span>
    </div>

    <el-row :gutter="16" style="margin-top: 12px">
      <el-col :span="8">
        <el-form-item label="史诗/特性">
          <el-input v-model="form.epic" placeholder="如: 报表模块" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="故事点">
          <el-select v-model="form.storyPoints" style="width: 100%" clearable>
            <el-option v-for="p in storyPointOptions" :key="p" :label="p + ' pts'" :value="p" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="指派人">
          <el-select v-model="form.assigneeId" style="width: 100%" clearable filterable placeholder="选择成员">
            <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.dueDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item label="详细描述">
      <el-input v-model="form.description" type="textarea" :rows="3" placeholder="补充描述..." />
    </el-form-item>

    <!-- Acceptance Criteria -->
    <el-form-item label="">
      <AcceptanceCriteria
        v-model="form.acceptanceCriteria"
        :disabled="false"
        @add="onAcAdd"
        @toggle="onAcToggle"
        @remove="onAcRemove"
      />
    </el-form-item>

    <el-form-item label="标签">
      <el-input v-model="labelInput" placeholder="输入标签后回车添加" @keyup.enter="addLabel" />
      <div style="margin-top: 8px; display: flex; gap: 4px; flex-wrap: wrap">
        <el-tag v-for="(l, i) in form.labels" :key="i" closable @close="form.labels.splice(i, 1)">{{ l }}</el-tag>
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { IssueFormData, AcceptanceCriterion } from '@/types'
import AcceptanceCriteria from './AcceptanceCriteria.vue'

const props = defineProps<{
  form: IssueFormData
  members: { userId: number; username: string }[]
  isEdit: boolean
}>()

const emit = defineEmits<{
  'ac-add': [text: string]
  'ac-toggle': [criteriaId: string, done: boolean]
  'ac-remove': [criteriaId: string]
}>()

const labelInput = ref('')

const storyPointOptions = [1, 2, 3, 5, 8, 13, 21]

const composedTitle = computed(() => {
  const parts: string[] = []
  if (props.form.userRole) parts.push(`作为${props.form.userRole}`)
  if (props.form.userGoal) parts.push(`我希望${props.form.userGoal}`)
  if (props.form.businessValue) parts.push(`以便${props.form.businessValue}`)
  return parts.join('，')
})

function addLabel() {
  if (labelInput.value && !props.form.labels.includes(labelInput.value)) {
    props.form.labels.push(labelInput.value)
    labelInput.value = ''
  }
}

function onAcAdd(text: string) { emit('ac-add', text) }
function onAcToggle(id: string, done: boolean) { emit('ac-toggle', id, done) }
function onAcRemove(id: string) { emit('ac-remove', id) }
</script>

<style scoped lang="scss">
.story-form {
  &__three-parts {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 6px 8px;
    align-items: center;
    background: var(--bg-column);
    border-radius: var(--border-radius-md);
    padding: 12px;
    margin-bottom: 8px;
  }
  &__label {
    font-size: 12px; color: var(--text-secondary); white-space: nowrap;
    text-align: right; font-weight: 600;
  }
  &__title-preview {
    padding: 8px 12px; background: rgba(79,110,246,0.06);
    border-radius: var(--border-radius-sm); margin-bottom: 4px;
  }
  &__title-label { font-size: 11px; color: var(--text-placeholder); margin-right: 8px; }
  &__title-text { font-size: 13px; color: var(--text-primary); font-weight: 500; }
}
</style>
