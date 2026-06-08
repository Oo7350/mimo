<template>
  <div class="bug-form">
    <!-- Severity and Bug Status on top -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="严重程度">
          <el-select v-model="form.severity" style="width: 100%">
            <el-option v-for="s in severities" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="缺陷状态" v-if="isEdit">
          <el-select v-model="form.bugStatus" style="width: 100%">
            <el-option
              v-for="s in allowedBugStatuses"
              :key="s"
              :label="BUG_STATUS_LABEL[s]"
              :value="s"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- Environment info -->
    <el-form-item label="环境信息">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-input v-model="os" placeholder="操作系统" />
        </el-col>
        <el-col :span="8">
          <el-input v-model="browser" placeholder="浏览器" />
        </el-col>
        <el-col :span="8">
          <el-input v-model="appVersion" placeholder="App 版本" />
        </el-col>
      </el-row>
    </el-form-item>

    <!-- Expected vs Actual result side-by-side -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="期望结果">
          <el-input v-model="form.expectedResult" type="textarea" :rows="3" placeholder="期望的正确行为..." />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="实际结果">
          <el-input v-model="form.actualResult" type="textarea" :rows="3" placeholder="实际的错误行为..." />
        </el-form-item>
      </el-col>
    </el-row>

    <!-- Steps to reproduce -->
    <el-form-item label="复现步骤">
      <el-input v-model="form.stepsToRepro" type="textarea" :rows="3" placeholder="1. 打开...\n2. 点击...\n3. 观察..." />
    </el-form-item>

    <!-- Version info + assignee -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="发现版本">
          <el-input v-model="form.foundVersion" placeholder="如: 1.2.0" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="修复版本">
          <el-input v-model="form.fixedVersion" placeholder="如: 1.2.1" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="指派人">
          <el-select v-model="form.assigneeId" style="width: 100%" clearable filterable placeholder="选择成员">
            <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- Description -->
    <el-form-item label="补充描述">
      <el-input v-model="form.description" type="textarea" :rows="2" placeholder="补充信息..." />
    </el-form-item>

    <!-- Labels -->
    <el-form-item label="标签">
      <el-input v-model="labelInput" placeholder="输入标签后回车添加" @keyup.enter="addLabel" />
      <div style="margin-top: 8px; display: flex; gap: 4px; flex-wrap: wrap">
        <el-tag v-for="(l, i) in form.labels" :key="i" closable @close="form.labels.splice(i, 1)">{{ l }}</el-tag>
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { IssueFormData, BugStatus } from '@/types'
import { BUG_STATUS_LABEL, BUG_TRANSITIONS } from '@/utils/constants'

const props = defineProps<{
  form: IssueFormData
  members: { userId: number; username: string }[]
  isEdit: boolean
}>()

const labelInput = ref('')
const os = ref('')
const browser = ref('')
const appVersion = ref('')

// Watch form.environment to pre-populate on edit
watch(() => props.form.environment, (val) => {
  if (val) {
    const parts = val.split(';').map(s => s.trim()).filter(Boolean)
    if (parts.length >= 1 && !os.value) os.value = parts[0]
    if (parts.length >= 2 && !browser.value) browser.value = parts[1]
    if (parts.length >= 3 && !appVersion.value) appVersion.value = parts[2]
  }
}, { immediate: true })

const severities = [
  { label: '阻塞 (Blocker)', value: 'BLOCKER' },
  { label: '严重 (Critical)', value: 'CRITICAL' },
  { label: '主要 (Major)', value: 'MAJOR' },
  { label: '次要 (Minor)', value: 'MINOR' },
  { label: '轻微 (Trivial)', value: 'TRIVIAL' },
]

// Read environment from form.environment field on load
const currentEnv = computed(() => {
  if (!props.form.environment) return []
  return props.form.environment.split(';').map(s => s.trim())
})

// Build environment string from 3 inputs
const envString = computed(() => {
  const parts = [os.value, browser.value, appVersion.value].filter(Boolean)
  return parts.join('; ') || undefined
})

const allowedBugStatuses = computed<BugStatus[]>(() => {
  const current = (props.form.bugStatus || 'NEW') as BugStatus
  return BUG_TRANSITIONS[current] || []
})

function addLabel() {
  if (labelInput.value && !props.form.labels.includes(labelInput.value)) {
    props.form.labels.push(labelInput.value)
    labelInput.value = ''
  }
}

// Expose envString for parent to consume before save
defineExpose({ envString })
</script>

<style scoped lang="scss">
.bug-form { /* placeholder */ }
</style>
