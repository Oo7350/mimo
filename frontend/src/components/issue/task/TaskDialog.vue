<template>
  <div class="task-form">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="指派人">
          <el-select v-model="form.assigneeId" style="width: 100%" clearable filterable placeholder="选择成员">
            <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="故事点">
          <el-input-number v-model="form.storyPoints" :min="0" style="width: 100%" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.dueDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="所属故事" v-if="stories.length > 0">
          <el-select v-model="form.parentId" style="width: 100%" clearable placeholder="可选">
            <el-option v-for="s in stories" :key="s.id" :label="`${s.issueKey}: ${s.title}`" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item label="详细描述">
      <el-input v-model="form.description" type="textarea" :rows="3" placeholder="任务描述..." />
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
import { ref } from 'vue'
import type { IssueFormData, IssueCard } from '@/types'

const props = defineProps<{
  form: IssueFormData
  members: { userId: number; username: string }[]
  stories: IssueCard[]
}>()

const labelInput = ref('')

function addLabel() {
  if (labelInput.value && !props.form.labels?.includes(labelInput.value)) {
    props.form.labels.push(labelInput.value)
    labelInput.value = ''
  }
}
</script>

<style scoped lang="scss">
.task-form { /* placeholder */ }
</style>
