<template>
  <el-dialog
    v-model:visible="visible"
    title="AI 智能创建任务"
    width="680px"
    destroy-on-close
    class="ai-creator-dialog"
    @open="onOpen"
    @close="onClose"
  >
    <!-- Step 1: 自然语言输入 -->
    <div v-if="step === 'input'" class="ai-creator__step">
      <div class="ai-creator__input-area">
        <div class="ai-creator__examples">
          <span class="ai-creator__examples-label">试试这样说:</span>
          <button v-for="ex in examples" :key="ex" class="ai-creator__example-btn" @click="fillExample(ex)">
            {{ ex }}
          </button>
        </div>
        <el-input
          ref="inputRef"
          v-model="userInput"
          type="textarea"
          :rows="4"
          placeholder="用自然语言描述你要创建的任务，例如：&#10;用户登录后页面白屏，Chrome浏览器复现，需要紧急修复"
          resize="none"
        />
        <div class="ai-creator__actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="parsing"
            :disabled="!userInput.trim()"
            @click="handleParse"
          >
            <el-icon><MagicStick /></el-icon>
            AI 解析
          </el-button>
        </div>
      </div>
    </div>

    <!-- Step 2: 解析结果预览 + 编辑 -->
    <div v-else-if="step === 'preview'" class="ai-creator__step">
      <div class="ai-creator__preview-header">
        <el-tag :type="parsedResult?.type === 'BUG' ? 'danger' : parsedResult?.type === 'STORY' ? '' : 'success'" size="large">
          {{ typeLabel(parsedResult?.type) }}
        </el-tag>
        <el-tag :type="priorityTagType(parsedResult?.priority)" effect="plain">
          {{ priorityLabel(parsedResult?.priority) }}
        </el-tag>
        <span v-if="parsedResult?.storyPoints" class="ai-creator__sp">{{ parsedResult.storyPoints }} SP</span>
        <span class="ai-creator__confidence">
          置信度: {{ Math.round((parsedResult?.confidence || 0) * 100) }}%
        </span>
      </div>

      <el-form label-position="top" class="ai-creator__form">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="editForm.type" style="width: 100%">
                <el-option label="故事 (STORY)" value="STORY" />
                <el-option label="任务 (TASK)" value="TASK" />
                <el-option label="缺陷 (BUG)" value="BUG" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-select v-model="editForm.priority" style="width: 100%">
                <el-option label="最高" value="HIGHEST" />
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
                <el-option label="最低" value="LOWEST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="故事点">
              <el-input-number v-model="editForm.storyPoints" :min="0" :max="21" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="5" />
        </el-form-item>

        <el-form-item v-if="editForm.labels?.length" label="标签">
          <div class="ai-creator__tags">
            <el-tag v-for="label in editForm.labels" :key="label" size="small" effect="plain">
              {{ label }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item v-if="editForm.dueDateSuggestion" label="建议截止日期">
          <el-date-picker v-model="editForm.dueDate" type="date" placeholder="选择日期" style="width: 200px" />
        </el-form-item>

        <!-- 子任务建议 -->
        <el-form-item v-if="subTasks.length > 0" label="AI 建议子任务">
          <div class="ai-creator__subtasks">
            <div v-for="(st, i) in subTasks" :key="i" class="ai-creator__subtask">
              <el-checkbox v-model="st.selected">{{ st.title }}</el-checkbox>
              <el-tag size="small" effect="plain">{{ st.estimate }} SP</el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <div v-if="parsedResult?.disclaimer" class="ai-creator__disclaimer">
        <el-icon><InfoFilled /></el-icon> {{ parsedResult.disclaimer }}
      </div>
    </div>

    <!-- Step 3: 创建中/完成 -->
    <div v-else-if="step === 'creating'" class="ai-creator__step ai-creator__center">
      <el-icon :size="40" class="is-loading"><Loading /></el-icon>
      <p>正在创建任务...</p>
    </div>

    <template #footer>
      <template v-if="step === 'preview'">
        <el-button @click="step = 'input'">返回修改输入</el-button>
        <el-button @click="handleReparse" :loading="parsing">
          <el-icon><Refresh /></el-icon> 重新解析
        </el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          <el-icon><Check /></el-icon> 创建任务
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Loading, Check, Refresh, InfoFilled } from '@element-plus/icons-vue'
import { parseIssue, type ParsedIssue } from '@/api/ai'
import { createIssue } from '@/api/issue'

const props = defineProps<{
  visible: boolean
  projectId: number
  teamId?: number
}>()

const emit = defineEmits(['update:visible', 'created'])

// v-model wrapper for prop
const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// 状态
const step = ref<'input' | 'preview' | 'creating'>('input')
const userInput = ref('')
const parsing = ref(false)
const creating = ref(false)
const inputRef = ref<any>(null)

// 解析结果
const parsedResult = ref<ParsedIssue | null>(null)

// 可编辑表单 (从解析结果初始化)
const editForm = reactive({
  title: '',
  type: 'TASK' as any,
  priority: 'MEDIUM' as any,
  storyPoints: null as number | null,
  description: '',
  labels: [] as string[],
  dueDateSuggestion: '' as string | undefined,
  dueDate: '' as string | undefined,
})

// 子任务
const subTasks = reactive<Array<{ title: string; estimate: number; selected: boolean }>>([])

const examples = [
  '用户登录后页面白屏，Chrome复现，紧急',
  '首页加载超过5秒，移动端更严重，本周需解决',
  '作为用户我希望导出Excel报表以便财务对账',
]

function onOpen() {
  step.value = 'input'
  userInput.value = ''
  parsedResult.value = null
  nextTick(() => inputRef.value?.focus())
}

function onClose() {
  emit('update:visible', false)
}

function fillExample(text: string) {
  userInput.value = text
}

async function handleParse() {
  if (!userInput.value.trim()) return
  parsing.value = true
  try {
    const result = await parseIssue(userInput.value.trim())
    parsedResult.value = result

    // 填充编辑表单
    editForm.title = result.title || ''
    editForm.type = result.type || 'TASK'
    editForm.priority = result.priority || 'MEDIUM'
    editForm.storyPoints = result.storyPoints ?? null
    editForm.description = result.description || ''
    editForm.labels = result.labels || []
    editForm.dueDateSuggestion = result.dueDateSuggestion
    editForm.dueDate = result.dueDateSuggestion || undefined

    // 子任务
    subTasks.length = 0
    if (result.subTaskSuggestions) {
      for (const st of result.subTaskSuggestions) {
        subTasks.push({ ...st, selected: true })
      }
    }

    step.value = 'preview'
  } catch (e: any) {
    ElMessage.error('AI 解析失败: ' + (e.message || '未知错误'))
  } finally {
    parsing.value = false
  }
}

function handleReparse() {
  handleParse()
}

async function handleCreate() {
  creating.value = true
  step.value = 'creating'

  try {
    const payload: any = {
      projectId: props.projectId,
      title: editForm.title,
      type: editForm.type,
      priority: editForm.priority,
      description: editForm.description,
      labels: editForm.labels,
    }
    if (editForm.storyPoints) payload.storyPoints = editForm.storyPoints
    if (editForm.dueDate) payload.dueDate = editForm.dueDate

    await createIssue(payload)
    ElMessage.success('任务创建成功')
    emit('created')
    emit('update:visible', false)
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
    step.value = 'preview'
  } finally {
    creating.value = false
  }
}

// --- 辅助 ---

function typeLabel(type?: string): string {
  switch (type) {
    case 'STORY': return '用户故事'
    case 'TASK': return '任务'
    case 'BUG': return '缺陷'
    default: return '任务'
  }
}

function priorityTagType(priority?: string): string {
  switch (priority) {
    case 'HIGHEST': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return ''
    case 'LOW': return 'info'
    case 'LOWEST': return 'info'
    default: return ''
  }
}

function priorityLabel(priority?: string): string {
  switch (priority) {
    case 'HIGHEST': return '最高'
    case 'HIGH': return '高'
    case 'MEDIUM': return '中'
    case 'LOW': return '低'
    case 'LOWEST': return '最低'
    default: return '中'
  }
}
</script>

<style scoped>
.ai-creator-dialog :deep(.el-dialog__body) { padding: 20px 24px; }

.ai-creator__step { min-height: 300px; }

.ai-creator__input-area { display: flex; flex-direction: column; gap: 16px; }

.ai-creator__examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.ai-creator__examples-label {
  font-size: 12px;
  color: var(--text-secondary, #94a3b8);
}
.ai-creator__example-btn {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-color, #e4e7ed);
  background: transparent;
  color: var(--text-secondary, #606266);
  cursor: pointer;
  transition: all 0.2s;
}
.ai-creator__example-btn:hover {
  border-color: #6366f1;
  color: #6366f1;
  background: rgba(99,102,241,0.06);
}

.ai-creator__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 预览区 */
.ai-creator__preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}
.ai-creator__sp {
  font-size: 13px;
  font-weight: 600;
  color: #6366f1;
}
.ai-creator__confidence {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-secondary, #94a3b8);
}

.ai-creator__form { max-height: 50vh; overflow-y: auto; padding-right: 4px; }

.ai-creator__tags { display: flex; gap: 6px; flex-wrap: wrap; }

.ai-creator__subtasks { display: flex; flex-direction: column; gap: 8px; }
.ai-creator__subtask {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(99,102,241,0.04);
  border: 1px solid rgba(99,102,241,0.12);
}

.ai-creator__disclaimer {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(230,162,60,0.08);
  color: #b88230;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-creator__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 200px;
  color: var(--text-secondary, #94a3b8);
}
</style>
