<template>
  <el-dialog
    :model-value="visible"
    :title="event ? '编辑事件' : '新建事件'"
    width="520px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-position="top">
      <el-row :gutter="16">
        <el-col :span="24">
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" placeholder="输入事件标题" maxlength="100" show-word-limit />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="类型" prop="eventType">
            <el-radio-group v-model="form.eventType" @change="onTypeChange">
              <el-radio-button value="CUSTOM">自定义</el-radio-button>
              <el-radio-button value="MEETING">会议</el-radio-button>
              <el-radio-button value="REMINDER">提醒</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              format="YYYY-MM-DD HH:mm"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              format="YYYY-MM-DD HH:mm"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="">
            <el-checkbox v-model="form.allDay">全天事件</el-checkbox>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="提醒">
            <el-select v-model="form.reminderMinutes" placeholder="不提醒" clearable style="width:100%">
              <el-option label="不提醒" :value="0" />
              <el-option label="5分钟前" :value="5" />
              <el-option label="15分钟前" :value="15" />
              <el-option label="30分钟前" :value="30" />
              <el-option label="1小时前" :value="60" />
              <el-option label="1天前" :value="1440" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="关联项目">
            <el-select v-model="form.projectId" placeholder="无关联项目" clearable style="width:100%">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="地点">
            <el-input v-model="form.location" placeholder="输入地点（可选）" maxlength="200" />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="添加描述（可选）" maxlength="500" show-word-limit />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- Color preview -->
    <div class="color-preview">
      <span class="color-preview__label">颜色</span>
      <div class="color-preview__swatches">
        <div
          v-for="c in colorOptions"
          :key="c.value"
          class="swatch"
          :class="{ active: form.color === c.value }"
          :style="{ backgroundColor: c.value }"
          @click="form.color = c.value"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ event ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { createCalendarEvent, updateCalendarEvent, type CreateCalendarEvent, type CalendarEvent } from '@/api/calendar'

const props = defineProps<{
  visible: boolean
  event: CalendarEvent | null
  defaultDate: Date | null
  projects: { id: number; name: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref()
const saving = ref(false)

const defaultForm = (): CreateCalendarEvent => ({
  title: '',
  description: '',
  startTime: '',
  endTime: '',
  allDay: false,
  eventType: 'CUSTOM',
  projectId: undefined,
  location: '',
  reminderMinutes: 15,
})

const form = ref<CreateCalendarEvent>(defaultForm())

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

const colorOptions = [
  { value: '#409EFF', name: '蓝' },
  { value: '#67C23A', name: '绿' },
  { value: '#E6A23C', name: '橙' },
  { value: '#F56C6C', name: '红' },
  { value: '#9B59B6', name: '紫' },
  { value: '#1ABC9C', name: '青' },
]

function onTypeChange(type: string) {
  const map: Record<string, string> = {
    CUSTOM: '#909399',
    MEETING: '#409EFF',
    REMINDER: '#E6A23C',
  }
  if (!props.event) form.color = map[type] || '#909399'
}

function resetForm() {
  form.value = defaultForm()
}

// Watch for event changes to populate edit mode
watch(() => props.visible, (val) => {
  if (val && props.event) {
    // Edit mode - populate form
    const e = props.event
    form.value = {
      title: e.title,
      description: e.description || '',
      startTime: e.startTime,
      endTime: e.endTime,
      allDay: e.allDay,
      eventType: e.eventType === 'TASK_DEADLINE' ? 'CUSTOM' : e.eventType || 'CUSTOM',
      projectId: e.projectId ?? undefined,
      location: e.location || '',
      reminderMinutes: e.reminderMinutes ?? 15,
    }
    form.value.color = e.color
  } else if (val) {
    // Create mode with default date
    form.value = defaultForm()
    if (props.defaultDate) {
      const d = props.defaultDate
      const pad = (n: number) => String(n).padStart(2, '0')
      const iso = `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
      form.value.startTime = iso
      const end = new Date(d.getTime() + 3600000)
      form.value.endTime = `${end.getFullYear()}-${pad(end.getMonth()+1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}:00`
    }
    form.value.color = '#409EFF'
  }
}, { immediate: true })

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch { return }

  saving.value = true
  try {
    if (props.event) {
      await updateCalendarEvent(props.event.id, form.value)
    } else {
      await createCalendarEvent(form.value)
    }
    emit('saved')
    emit('update:visible', false)
  } catch {}
  finally { saving.value = false }
}
</script>

<style scoped lang="scss">
.color-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  padding: 8px 0;
  border-top: 1px solid #f0f0f0;

  &__label {
    font-size: 13px;
    color: #606266;
    min-width: 36px;
  }

  &__swatches {
    display: flex;
    gap: 8px;
  }
}

.swatch {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.15s, border-color 0.15s;

  &:hover { transform: scale(1.15); }
  &.active {
    border-color: #303133;
    transform: scale(1.15);
  }
}
</style>
