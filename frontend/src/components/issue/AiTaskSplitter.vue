<template>
  <el-dialog
    v-model:visible="visible"
    title="AI 拆分子任务"
    width="640px"
    destroy-on-close
    class="ai-splitter-dialog"
  >
    <div class="ai-splitter__info">
      <div class="ai-splitter__story-title">
        <el-tag type="primary" size="small">Story</el-tag>
        {{ storyTitle }}
      </div>
      <p class="ai-splitter__desc">{{ storyDescription || '无描述' }}</p>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="ai-splitter__loading">
      <el-icon :size="32" class="is-loading"><Loading /></el-icon>
      <p>AI 正在分析并拆分任务...</p>
    </div>

    <!-- 拆分结果 -->
    <div v-else-if="tasks.length > 0" class="ai-splitter__results">
      <div class="ai-splitter__header">
        <span>建议拆分为 {{ tasks.length }} 个子任务</span>
        <el-button size="small" text @click="toggleAll">
          {{ allSelected ? '取消全选' : '全选' }}
        </el-button>
      </div>

      <div class="ai-splitter__list">
        <TransitionGroup name="split-list">
          <div
            v-for="(task, index) in tasks"
            :key="index"
            :class="['ai-splitter__item', { 'is-selected': task.selected }]"
          >
            <div class="ai-splitter__item-left">
              <el-checkbox v-model="task.selected" />
              <span class="ai-splitter__item-index">{{ index + 1 }}</span>
            </div>
            <div class="ai-splitter__item-body">
              <div class="ai-splitter__item-title">
                <el-tag size="small" :type="task.type === 'BUG' ? 'danger' : 'success'" effect="plain">
                  {{ task.type === 'BUG' ? 'Bug' : 'Task' }}
                </el-tag>
                {{ task.title }}
              </div>
              <p v-if="task.description" class="ai-splitter__item-desc">{{ task.description }}</p>
              <div v-if="task.dependency" class="ai-splitter__item-dep">
                <el-icon><Link /></el-icon> 依赖: {{ task.dependency }}
              </div>
            </div>
            <div class="ai-splitter__item-right">
              <span class="ai-splitter__item-sp">{{ task.estimate }} SP</span>
            </div>
          </div>
        </TransitionGroup>
      </div>

      <div class="ai-splitter__summary">
        总计: {{ selectedCount }} 个任务 · {{ selectedPoints }} 故事点
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="ai-splitter__empty">
      <el-empty description="未获取到拆分建议" :image-size="60">
        <el-button @click="handleSplit">重新分析</el-button>
      </el-empty>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="selectedCount === 0"
        :loading="creating"
        @click="handleCreate"
      >
        创建选中项 ({{ selectedCount }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Link } from '@element-plus/icons-vue'
import { suggestSubTasks, type SuggestedTask } from '@/api/ai'
import { createIssue } from '@/api/issue'

const props = defineProps<{
  visible: boolean
  projectId: number
  storyTitle: string
  storyDescription?: string
  acceptanceCriteria?: string
  parentIssueId?: number
}>()

const emit = defineEmits(['update:visible', 'created'])

// v-model wrapper for prop
const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const loading = ref(false)
const creating = ref(false)
const tasks = ref<Array<SuggestedTask & { selected: boolean }>>([])

const selectedCount = computed(() => tasks.value.filter(t => t.selected).length)
const selectedPoints = computed(() =>
  tasks.value.filter(t => t.selected).reduce((sum, t) => sum + (t.estimate || 0), 0)
)
const allSelected = computed(() => tasks.value.length > 0 && tasks.value.every(t => t.selected))

watch(() => props.visible, (v) => {
  if (v && props.storyTitle) handleSplit()
})

async function handleSplit() {
  loading.value = true
  try {
    const result = await suggestSubTasks({
      title: props.storyTitle,
      description: props.storyDescription,
      acceptanceCriteria: props.acceptanceCriteria,
    })
    tasks.value = result.map(t => ({ ...t, selected: true }))
  } catch (e: any) {
    ElMessage.error('AI 拆分失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function toggleAll() {
  const val = !allSelected.value
  tasks.value.forEach(t => { t.selected = val })
}

async function handleCreate() {
  creating.value = true
  try {
    const selected = tasks.value.filter(t => t.selected)
    let created = 0
    for (const st of selected) {
      await createIssue({
        projectId: props.projectId,
        title: st.title,
        type: st.type === 'BUG' ? 'BUG' : 'TASK',
        priority: 'MEDIUM',
        storyPoints: st.estimate,
        description: st.description || '',
        parentId: props.parentIssueId,
      })
      created++
    }
    ElMessage.success(`成功创建 ${created} 个子任务`)
    emit('created')
    emit('update:visible', false)
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.ai-splitter__info {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(99,102,241,0.04);
  border: 1px solid rgba(99,102,241,0.12);
}
.ai-splitter__story-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}
.ai-splitter__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary, #94a3b8);
}

.ai-splitter__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: var(--text-secondary, #94a3b8);
}

.ai-splitter__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
}

.ai-splitter__list { display: flex; flex-direction: column; gap: 8px; max-height: 45vh; overflow-y: auto; }

.ai-splitter__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color, #f0f0f0);
  transition: all 0.2s;
}
.ai-splitter__item.is-selected {
  border-color: rgba(99,102,241,0.35);
  background: rgba(99,102,241,0.03);
}
.ai-splitter__item-left {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-top: 2px;
}
.ai-splitter__item-index {
  font-size: 11px;
  color: var(--text-tertiary, #c0c4cc);
  min-width: 18px;
  text-align: center;
}
.ai-splitter__item-body { flex: 1; min-width: 0; }
.ai-splitter__item-title {
  font-size: 14px;
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ai-splitter__item-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary, #94a3b8);
  line-height: 1.5;
}
.ai-splitter__item-dep {
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 3px;
}
.ai-splitter__item-right {
  padding-top: 2px;
}
.ai-splitter__item-sp {
  font-size: 13px;
  font-weight: 600;
  color: #6366f1;
  white-space: nowrap;
}

.ai-splitter__summary {
  margin-top: 12px;
  text-align: right;
  font-size: 13px;
  color: var(--text-secondary, #94a3b8);
  padding-top: 8px;
  border-top: 1px dashed var(--border-color, #f0f0f0);
}

/* 过渡动画 */
.split-list-enter-active { transition: all 0.3s ease-out; }
.split-list-leave-active { transition: all 0.2s ease-in; }
.split-list-enter-from { opacity: 0; transform: translateY(-8px); }
.split-list-leave-to { opacity: 0; transform: translateX(16px); }
</style>
