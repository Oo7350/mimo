<template>
  <div class="workflow-designer">
    <!-- 顶部工具栏 -->
    <div class="workflow-designer__header">
      <div class="workflow-designer__title">
        <el-button text @click="$router.push(`/projects/${projectId}`)">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <h3>工作流设计器</h3>
      </div>
      <div class="workflow-designer__actions">
        <el-select v-model="selectedIssueType" placeholder="选择类型" style="width: 120px">
          <el-option label="Story" value="STORY" />
          <el-option label="Task" value="TASK" />
          <el-option label="Bug" value="BUG" />
        </el-select>
        <el-select v-model="selectedTemplate" placeholder="应用模板" style="width: 150px" @change="onApplyTemplate">
          <el-option label="Scrum 模板" value="scrum" />
          <el-option label="Kanban 模板" value="kanban" />
          <el-option label="Bug 跟踪模板" value="bug" />
        </el-select>
        <el-button type="primary" @click="onSave" :loading="saving">保存工作流</el-button>
      </div>
    </div>

    <!-- 这一页解决什么问题：以前只有标题 + 两个按钮，看完不知道它管什么 -->
    <PageHint>
      工作流规定「任务在看板上能怎么移动」。<b>不配置 = 随便拖</b>；配置之后只能走下面列出的路径，
      其它拖法会被看板拦下，并告诉你还能往哪去。规则按「工作项类型」分别生效（现在看的是
      <b>{{ typeLabel }}</b> 这一套），保存后立即生效，不用重启也不用刷新。
    </PageHint>

    <!-- 现有工作流信息 -->
    <el-alert v-if="currentWorkflow && !editing" type="info" :closable="false" class="workflow-designer__alert">
      <template #title>
        当前「{{ typeLabel }}」类型已配置工作流: {{ currentWorkflow.name }}
        <el-button text type="primary" @click="startEditing">编辑</el-button>
        <el-button text type="danger" @click="onDelete">删除</el-button>
      </template>
    </el-alert>

    <!-- 只读概览：不点「编辑」也要能看懂规则到底允许什么 -->
    <div v-if="currentWorkflow && !editing" class="workflow-designer__overview">
      <div class="workflow-designer__section-title">
        <span>现在允许的移动</span>
        <el-tooltip content="节点 = 看板上的列；「终态」表示走到这一列就算结束">
          <el-icon><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="workflow-designer__node-grid">
        <div
          v-for="node in currentWorkflow.config.nodes"
          :key="node.columnId"
          class="workflow-designer__node"
          :style="{ borderColor: node.color, background: node.color + '15' }"
        >
          <span class="workflow-designer__node-name">{{ node.name }}</span>
          <el-tag v-if="node.isTerminal" size="small" type="success" effect="plain">终态</el-tag>
        </div>
      </div>

      <el-table v-if="ruleRows.length" :data="ruleRows" size="small" class="workflow-designer__rules">
        <el-table-column prop="from" label="从" width="140" />
        <el-table-column width="50" align="center">
          <template #default>→</template>
        </el-table-column>
        <el-table-column prop="to" label="到" width="140" />
        <el-table-column prop="who" label="谁可以这么做" />
      </el-table>
      <p v-else class="workflow-designer__hintline">
        这套工作流一条转换规则都没有 —— 等于所有移动都会被拦。要么加规则，要么直接删除它。
      </p>
      <p class="workflow-designer__hintline">
        看板上被挡住时，会直接告诉你「当前可以从这里去：…」;规则改了立刻生效。
      </p>
    </div>

    <!-- 设计画布 -->
    <div v-if="editing" class="workflow-designer__canvas">
      <!-- 节点列表 -->
      <div class="workflow-designer__nodes">
        <div class="workflow-designer__section-title">
          <span>看板列 (状态节点)</span>
          <el-tooltip content="把看板列拖到下面的画布，它们就是这套流程允许停留的状态">
            <el-icon><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="workflow-designer__columns-list">
          <div
            v-for="col in boardColumns"
            :key="col.id"
            class="workflow-designer__column-chip"
            :style="{ borderColor: col.color }"
            draggable="true"
            @dragstart="onDragStart($event, col)"
          >
            <span class="workflow-designer__column-dot" :style="{ background: col.color }"></span>
            {{ col.name }}
          </div>
        </div>
      </div>

      <!-- 转换规则编辑 -->
      <div class="workflow-designer__transitions">
        <div class="workflow-designer__section-title">
          <span>转换规则 (允许的状态跳转)</span>
          <el-tooltip content="每条规则 = 一次被允许的移动；「执行条件」是『谁可以这么拖』，留空表示任何人都可以">
            <el-icon><InfoFilled /></el-icon>
          </el-tooltip>
          <el-button text type="primary" @click="addTransition">
            <el-icon><Plus /></el-icon>添加规则
          </el-button>
        </div>
        <div v-if="config.transitions.length === 0" class="workflow-designer__empty">
          还没有任何转换规则 —— 这样保存下去，看板上所有移动都会被拦住。点「添加规则」开始。
        </div>
        <div
          v-for="(t, idx) in config.transitions"
          :key="idx"
          class="workflow-designer__transition-row"
        >
          <el-select v-model="t.fromColumnId" placeholder="起始状态" style="width: 140px" size="small">
            <el-option
              v-for="n in config.nodes"
              :key="n.columnId"
              :label="n.name"
              :value="n.columnId"
            />
          </el-select>
          <el-icon class="workflow-designer__arrow"><Right /></el-icon>
          <el-select v-model="t.toColumnId" placeholder="目标状态" style="width: 140px" size="small">
            <el-option
              v-for="n in config.nodes"
              :key="n.columnId"
              :label="n.name"
              :value="n.columnId"
            />
          </el-select>
          <el-select
            v-model="t.conditions"
            multiple
            placeholder="执行条件 (空=所有人)"
            style="width: 200px"
            size="small"
          >
            <el-option label="仅管理员" value="ROLE_ADMIN" />
            <el-option label="仅指派人" value="ASSIGNEE" />
            <el-option label="仅报告人" value="REPORTER" />
          </el-select>
          <el-button text type="danger" @click="removeTransition(idx)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 节点画布 -->
      <div
        class="workflow-designer__drop-zone"
        @dragover.prevent
        @drop="onDrop($event)"
      >
        <div v-if="config.nodes.length === 0" class="workflow-designer__empty">
          拖拽上方的看板列到这里，添加为工作流节点
        </div>
        <div class="workflow-designer__node-grid">
          <div
            v-for="(node, idx) in config.nodes"
            :key="node.columnId"
            class="workflow-designer__node"
            :style="{ borderColor: node.color, background: node.color + '15' }"
          >
            <span class="workflow-designer__node-name">{{ node.name }}</span>
            <el-checkbox v-model="node.isTerminal" size="small" @change="markTerminal(idx)">终态</el-checkbox>
            <el-button text type="danger" size="small" @click="removeNode(idx)">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 无工作流时显示 -->
    <div v-else-if="!currentWorkflow" class="workflow-designer__start">
      <el-empty :description="`「${typeLabel}」类型没有配置工作流 —— 这一类型的任务在看板上可以随便拖`">
        <el-button type="primary" @click="startEditing">开始配置</el-button>
      </el-empty>
      <p class="workflow-designer__hintline">
        提示：只想限制某一种工作项？回到上面的下拉框切换类型分别配置；不配置的类型永远不受限制。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Delete, Right, Close, InfoFilled } from '@element-plus/icons-vue'
import PageHint from '@/components/common/PageHint.vue'
import {
  listWorkflows,
  getActiveWorkflow,
  saveWorkflow,
  deleteWorkflow,
  applyTemplate,
  type WorkflowConfig,
  type WorkflowNode,
  type WorkflowTransition,
  type WorkflowVO,
} from '@/api/workflow'
import { getBoard } from '@/api/board'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const selectedIssueType = ref('STORY')
const selectedTemplate = ref('')
const editing = ref(false)
const saving = ref(false)
const currentWorkflow = ref<WorkflowVO | null>(null)
const boardColumns = ref<{ id: number; name: string; color: string }[]>([])
const config = ref<WorkflowConfig>({ nodes: [], transitions: [] })

const TYPE_LABEL: Record<string, string> = { STORY: '需求（Story）', TASK: '任务（Task）', BUG: '缺陷（Bug）' }
const typeLabel = computed(() => TYPE_LABEL[selectedIssueType.value] || selectedIssueType.value)

/** 只读概览用：把保存好的转换规则翻成人话（从哪 → 到哪 → 谁能做） */
const ruleRows = computed(() => {
  const wf = currentWorkflow.value
  if (!wf?.config) return []
  const nameOf = (columnId: number) =>
    wf.config.nodes.find((n) => n.columnId === columnId)?.name || `列#${columnId}`
  const whoText = (conditions?: string[]) => {
    if (!conditions || conditions.length === 0) return '任何成员'
    return conditions
      .map((c) => ({ ROLE_ADMIN: '仅管理员', ASSIGNEE: '仅指派人', REPORTER: '仅报告人' })[c] || c)
      .join('、')
  }
  return (wf.config.transitions || []).map((t) => ({
    from: nameOf(t.fromColumnId),
    to: nameOf(t.toColumnId),
    who: whoText(t.conditions),
  }))
})

watch(selectedIssueType, () => loadWorkflow())

onMounted(async () => {
  await loadBoardColumns()
  await loadWorkflow()
})

async function loadBoardColumns() {
  const res = await getBoard(projectId)
  boardColumns.value = res.data.columns.map((c: any) => ({
    id: c.id,
    name: c.name,
    color: c.color,
  }))
}

async function loadWorkflow() {
  const res = await getActiveWorkflow(projectId, selectedIssueType.value)
  currentWorkflow.value = res.data
  editing.value = false
}

function startEditing() {
  if (currentWorkflow.value) {
    config.value = JSON.parse(JSON.stringify(currentWorkflow.value.config))
  } else {
    config.value = { nodes: [], transitions: [] }
  }
  editing.value = true
}

function onDragStart(e: DragEvent, col: { id: number; name: string; color: string }) {
  e.dataTransfer?.setData('columnId', String(col.id))
  e.dataTransfer?.setData('name', col.name)
  e.dataTransfer?.setData('color', col.color)
}

function onDrop(e: DragEvent) {
  const columnId = Number(e.dataTransfer?.getData('columnId'))
  const name = e.dataTransfer?.getData('name') || ''
  const color = e.dataTransfer?.getData('color') || '#409EFF'
  if (!columnId) return
  if (config.value.nodes.some((n) => n.columnId === columnId)) {
    ElMessage.warning('该列已在节点列表中')
    return
  }
  config.value.nodes.push({ columnId, name, color, isTerminal: false })
}

function removeNode(idx: number) {
  const removed = config.value.nodes[idx]
  config.value.nodes.splice(idx, 1)
  // 移除相关转换规则
  config.value.transitions = config.value.transitions.filter(
    (t) => t.fromColumnId !== removed.columnId && t.toColumnId !== removed.columnId,
  )
}

function markTerminal(idx: number) {
  // 单选逻辑：终态只允许一个
  if (config.value.nodes[idx].isTerminal) {
    config.value.nodes.forEach((n, i) => {
      if (i !== idx) n.isTerminal = false
    })
  }
}

function addTransition() {
  config.value.transitions.push({
    fromColumnId: config.value.nodes[0]?.columnId || 0,
    toColumnId: config.value.nodes[1]?.columnId || 0,
    conditions: [],
  })
}

function removeTransition(idx: number) {
  config.value.transitions.splice(idx, 1)
}

async function onApplyTemplate(templateName: string) {
  if (!templateName) return
  try {
    await ElMessageBox.confirm(`确定要应用「${templateName}」模板吗？这将覆盖当前配置`, '确认')
    const res = await applyTemplate(projectId, selectedIssueType.value, templateName)
    currentWorkflow.value = res.data
    config.value = JSON.parse(JSON.stringify(res.data.config))
    editing.value = true
    ElMessage.success('模板已套用，确认或修改后点「保存工作流」才真正生效')
  } catch {
    selectedTemplate.value = ''
  }
}

async function onSave() {
  if (config.value.nodes.length === 0) {
    ElMessage.warning('请至少添加一个节点')
    return
  }
  saving.value = true
  try {
    const res = await saveWorkflow({
      projectId,
      issueType: selectedIssueType.value,
      name: `${selectedIssueType.value} 工作流`,
      config: config.value,
    })
    currentWorkflow.value = res.data
    editing.value = false
    const ruleCount = (res.data?.config?.transitions || []).length
    ElMessage.success(
      ruleCount > 0
        ? `已保存并生效：现在看板上「${typeLabel.value}」只能按这 ${ruleCount} 条规则移动`
        : `已保存：这套工作流没有任何转换规则，看板上「${typeLabel.value}」将无法移动`,
    )
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  if (!currentWorkflow.value) return
  try {
    await ElMessageBox.confirm('确定删除该工作流？删除后任务移动将不受限制', '确认删除')
    await deleteWorkflow(currentWorkflow.value.id)
    currentWorkflow.value = null
    ElMessage.success('删除成功')
  } catch {
    // 取消
  }
}
</script>

<style scoped lang="scss">
.workflow-designer {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    h3 {
      margin: 0;
    }
  }

  &__actions {
    display: flex;
    gap: 8px;
  }

  &__alert {
    margin-bottom: 16px;
  }

  &__overview {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 16px;
    background: var(--el-bg-color);
  }

  &__rules {
    margin-top: 12px;
  }

  &__hintline {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: var(--el-text-color-secondary);
  }

  &__canvas {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    margin-bottom: 12px;
  }

  &__columns-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__column-chip {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border: 2px dashed;
    border-radius: 6px;
    cursor: grab;
    font-size: 13px;
    background: var(--el-bg-color);

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__column-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  &__drop-zone {
    min-height: 120px;
    border: 2px dashed var(--el-border-color);
    border-radius: 8px;
    padding: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__node-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    width: 100%;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border: 2px solid;
    border-radius: 8px;
    font-size: 13px;
  }

  &__transitions {
    border-top: 1px solid var(--el-border-color);
    padding-top: 16px;
  }

  &__transition-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__arrow {
    font-size: 16px;
    color: var(--el-text-color-secondary);
  }

  &__empty {
    color: var(--el-text-color-secondary);
    font-size: 14px;
    text-align: center;
    padding: 20px;
  }

  &__start {
    padding: 60px 0;
  }
}
</style>
