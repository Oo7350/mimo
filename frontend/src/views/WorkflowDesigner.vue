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

    <PageHint>
      工作流规定「任务在看板上能怎么移动」。<b>节点就是流程上的一步</b>，看板上的列是它的落脚点：
      新增一个还没落地的节点（比如「上线」），保存时会自动为它建一列，并明确告诉你建了哪一列。
      <br />
      画布上可以直接拖节点摆位置、<b>从节点右侧的圆点拖到另一个节点</b>就连出一条允许的移动；
      点连线的中间可以给它加条件。<b>删掉整条工作流 = 随便拖</b>；配了之后只能走画出来的这些路径，
      其它拖法会被看板拦下，并告诉你还能往哪去。规则按工作项类型分别生效（现在看的是
      <b>{{ typeLabel }}</b> 这一套），保存后立即生效，不用重启也不用刷新。
      <br />
      每个节点还可以指定<b>责任人</b>：指定之后，只有这个人（或项目管理员）能把工作项从这一步交出去，
      适合「开发 → 测试 → 验收」这种一步一交接的流程；不指定就是不限制。
    </PageHint>

    <!-- 现有工作流信息 -->
    <el-alert v-if="currentWorkflow && !editing" type="info" :closable="false" class="workflow-designer__alert">
      <template #title>
        <el-tag v-if="currentWorkflow.isDefault" size="small" type="success" effect="plain" class="workflow-designer__tag">
          自动生成
        </el-tag>
        当前「{{ typeLabel }}」类型已配置工作流: {{ currentWorkflow.name }}
        <el-button text type="primary" @click="startEditing">编辑</el-button>
        <el-button text type="danger" @click="onDelete">删除</el-button>
      </template>
    </el-alert>

    <!-- 只读概览：不点「编辑」也要能看懂规则到底允许什么 -->
    <div v-if="currentWorkflow && !editing" class="workflow-designer__overview">
      <div class="workflow-designer__section-title">
        <span>这套流程有哪几步</span>
        <el-tooltip content="节点 = 流程上的一步，看板列是它站的地方；「终态」判断与看板一致（列名含「完成」或 done）">
          <el-icon><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="workflow-designer__node-grid">
        <div
          v-for="node in currentWorkflow.config.nodes"
          :key="node.nodeKey || String(node.columnId)"
          class="workflow-designer__node"
          :style="{ borderColor: node.color, background: node.color + '15' }"
        >
          <span class="workflow-designer__node-name">{{ node.name }}</span>
          <el-tag v-if="isTerminalName(node.name)" size="small" type="success" effect="plain">终态</el-tag>
          <el-tooltip v-if="node.ownerName" :content="`这一步由 ${node.ownerName} 负责推进`">
            <el-tag size="small" type="warning" effect="plain">{{ node.ownerName }}</el-tag>
          </el-tooltip>
        </div>
      </div>

      <el-table v-if="ruleRows.length" :data="ruleRows" size="small" class="workflow-designer__rules">
        <el-table-column prop="from" label="从" width="110" />
        <el-table-column width="36" align="center">
          <template #default>→</template>
        </el-table-column>
        <el-table-column prop="to" label="到" width="110" />
        <el-table-column prop="who" label="谁可以这么做" />
        <el-table-column prop="requires" label="前提（全部满足）" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.requires" size="small" type="danger" effect="plain">{{ row.requires }}</el-tag>
            <span v-else class="workflow-designer__muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="这一步谁负责" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.owner" size="small" type="warning" effect="plain">{{ row.owner }}</el-tag>
            <span v-else class="workflow-designer__muted">不限</span>
          </template>
        </el-table-column>
      </el-table>
      <p v-else class="workflow-designer__hintline">
        这套工作流一条转换规则都没有 —— 等于所有移动都会被拦。要么加规则，要么直接删除它。
      </p>
      <p class="workflow-designer__hintline">
        看板上被挡住时，会直接告诉你「当前可以从这里去：…」；规则改了立刻生效。
      </p>
    </div>

    <!-- ===== 编辑态：调色板 + 画布 ===== -->
    <div v-if="editing" class="workflow-designer__editor">
      <div class="workflow-designer__palette">
        <div class="workflow-designer__section-title">
          <span>加一步</span>
          <el-tooltip content="还没落到看板列上的节点，保存时我会自动补一列，并告诉你补了哪一列">
            <el-icon><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-button
          type="primary"
          plain
          size="small"
          class="workflow-designer__add-node"
          data-add-node
          @click="addFreeNode"
        >
          <el-icon><Plus /></el-icon>新节点（先不绑列）
        </el-button>
        <p class="workflow-designer__muted workflow-designer__palette-hint">
          也可以把下面没用的看板列拖进画布，直接给它一个落脚点：
        </p>
        <div class="workflow-designer__columns-list">
          <div
            v-for="col in unusedColumns"
            :key="col.id"
            class="workflow-designer__column-chip"
            :style="{ borderColor: col.color }"
            draggable="true"
            :data-column-chip="col.id"
            @dragstart="onDragStart($event, col)"
          >
            <span class="workflow-designer__column-dot" :style="{ background: col.color }"></span>
            {{ col.name }}
          </div>
          <p v-if="unusedColumns.length === 0" class="workflow-designer__muted">看板列都已用上了</p>
        </div>
      </div>

      <div class="workflow-designer__canvas-wrap">
        <div class="workflow-designer__section-title">
          <span>流程画布</span>
          <span class="workflow-designer__muted">拖节点摆位置 · 从右侧圆点拖到另一个节点就连一条线</span>
        </div>
        <div
          ref="canvasRef"
          class="workflow-designer__canvas"
          data-workflow-canvas
          @dragover.prevent
          @drop="onDrop"
          @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp"
          @click="onCanvasClick"
          @mouseleave="onCanvasMouseUp"
        >
          <svg class="workflow-designer__edges" :width="canvasSize.width" :height="canvasSize.height">
            <defs>
              <marker id="wf-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
              </marker>
            </defs>
            <path
              v-for="(t, idx) in config.transitions"
              :key="t.fromNodeKey + '->' + t.toNodeKey"
              :d="edgePath(t)"
              class="workflow-designer__edge"
              :class="{ 'is-selected': selectedTransition === idx, 'is-guarded': (t.prerequisites || []).length > 0 }"
              marker-end="url(#wf-arrow)"
              :data-edge="idx"
              @click.stop="selectTransition(idx)"
            />
            <path v-if="draftEdge" :d="draftEdge" class="workflow-designer__edge is-draft" />
          </svg>

          <div
            v-for="node in config.nodes"
            :key="node.nodeKey"
            class="workflow-designer__card"
            :class="{
              'is-dragging': dragging?.key === node.nodeKey,
              'is-connecting': linking !== null,
            }"
            :style="{ left: (node.x ?? 40) + 'px', top: (node.y ?? 60) + 'px', borderColor: node.color }"
            :data-node-key="node.nodeKey"
            @mouseup.stop="finishLinkTo(node)"
            @click.stop="finishLinkTo(node)"
          >
            <div class="workflow-designer__card-head">
              <!-- 拖动把手：卡片上全是输入控件，整卡拖动会把点击吞掉 -->
              <span
                class="workflow-designer__grip"
                :data-drag-handle="node.nodeKey"
                title="按住这里拖动画布上的位置"
                @mousedown.stop="startDrag($event, node)"
                >⠿</span
              >
              <input
                v-model="node.name"
                class="workflow-designer__card-name-input"
                :data-node-name="node.nodeKey"
                @mousedown.stop
              />
              <el-tag v-if="isTerminalName(node.name)" size="small" type="success" effect="plain">终态</el-tag>
            </div>
            <div class="workflow-designer__card-meta">
              <el-tag v-if="!node.columnId" size="small" type="warning" effect="plain">保存后自动建列</el-tag>
              <span v-else class="workflow-designer__muted">列：{{ columnName(node.columnId) }}</span>
              <el-button text type="danger" size="small" @click.stop="removeNode(node)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <el-select
              v-model="node.ownerUserId"
              size="small"
              clearable
              placeholder="责任人（空=不限）"
              style="width: 100%"
              @mousedown.stop
              @change="onOwnerChange(node)"
            >
              <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
            </el-select>
            <button
              class="workflow-designer__port"
              :data-port="node.nodeKey"
              title="从这里拖到另一个节点，连出一条移动（也可以点一下这个点、再点目标节点）"
              @mousedown.stop.prevent="startLink($event, node)"
              @click.stop="onPortClick($event, node)"
            />
          </div>

          <p v-if="config.nodes.length === 0" class="workflow-designer__canvas-empty">
            画布还是空的：点左侧「新节点」，或把看板列拖进来
          </p>
        </div>
      </div>

      <!-- 选中连线的设置面板 -->
      <div v-if="currentTransition" class="workflow-designer__edge-panel" data-edge-panel>
        <div class="workflow-designer__section-title">
          <span>这条移动：{{ nodeLabel(currentTransition.fromNodeKey) }} → {{ nodeLabel(currentTransition.toNodeKey) }}</span>
          <el-button text type="danger" size="small" @click="removeSelectedTransition">删除这条</el-button>        </div>
        <div class="workflow-designer__edge-fields">
          <label>
            <span>谁可以做（任一）</span>
            <el-select v-model="currentTransition.conditions" multiple placeholder="空 = 所有人" size="small" style="width: 220px">
              <el-option v-for="(label, key) in CONDITION_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </label>
          <label>
            <span>什么情况下才能做（全部）</span>
            <el-select v-model="currentTransition.prerequisites" multiple placeholder="空 = 无条件" size="small" style="width: 260px">
              <el-option v-for="(label, key) in PREREQUISITE_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </label>
        </div>
        <p class="workflow-designer__hintline">
          前提条件对「一条验收标准都没写」的工作项不生效 —— 否则不写验收标准的老任务会永远推不动。
        </p>
      </div>
      <p v-else class="workflow-designer__hintline">
        点画布上的连线可以给它加条件（现在 {{ config.transitions.length }} 条）。
      </p>
    </div>

    <!-- 无工作流时显示（仅当项目还没有看板列） -->
    <div v-else-if="!currentWorkflow" class="workflow-designer__start">
      <el-empty description="该项目还没有看板列，暂时无法自动生成工作流；先创建看板列，或直接手动加节点">
        <el-button type="primary" @click="startEditing">手动配置</el-button>
      </el-empty>
      <p class="workflow-designer__hintline">
        提示：工作流默认按看板列自动生成；想收紧某一种，用上面的下拉框切换类型单独调整。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Close, InfoFilled } from '@element-plus/icons-vue'
import PageHint from '@/components/common/PageHint.vue'
import {
  getActiveWorkflow,
  saveWorkflow,
  deleteWorkflow,
  applyTemplate,
  CONDITION_LABEL,
  PREREQUISITE_LABEL,
  type WorkflowConfig,
  type WorkflowNode,
  type WorkflowTransition,
  type WorkflowVO,
} from '@/api/workflow'
import { getBoard } from '@/api/board'
import { getProjectById } from '@/api/project'
import { getMembers } from '@/api/team'

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
/** 团队/项目成员：给节点责任人下拉用（责任人只能从成员里挑） */
const members = ref<{ userId: number; username: string }[]>([])

const canvasRef = ref<HTMLElement | null>(null)
const canvasSize = ref({ width: 880, height: 520 })
/** 正在拖动的节点（记录指针与卡片左上角的偏移，避免卡片"跳"到指针下） */
const dragging = ref<{ key: string; dx: number; dy: number } | null>(null)
/** 正在连线：从哪个节点出发、指针现在在哪 */
const linking = ref<{ fromKey: string; x: number; y: number } | null>(null)
const selectedTransition = ref<number | null>(null)

/** 卡片尺寸：连线端点按这个算，改样式时一起改 */
const CARD_W = 200
const CARD_H = 116
const PORT_Y = 26

const TYPE_LABEL: Record<string, string> = { STORY: '需求（Story）', TASK: '任务（Task）', BUG: '缺陷（Bug）' }
const typeLabel = computed(() => TYPE_LABEL[selectedIssueType.value] || selectedIssueType.value)

/** 还没被任何节点用到的看板列（只能拖进画布一次） */
const unusedColumns = computed(() =>
  boardColumns.value.filter((col) => !config.value.nodes.some((n) => n.columnId === col.id)),
)

const currentTransition = computed<WorkflowTransition | null>(() =>
  selectedTransition.value === null ? null : config.value.transitions[selectedTransition.value] || null,
)

/** 只读概览用：把保存好的转换规则翻成人话（从哪 → 到哪 → 谁能做 + 前提） */
const ruleRows = computed(() => {
  const wf = currentWorkflow.value
  if (!wf?.config) return []
  const nameOf = (nodeKey?: string | null, columnId?: number | null) => {
    const nodes = wf.config.nodes || []
    const hit =
      nodes.find((n) => nodeKey && n.nodeKey === nodeKey) ||
      nodes.find((n) => columnId != null && n.columnId === columnId)
    return hit?.name || `列#${columnId ?? nodeKey}`
  }
  const whoText = (conditions?: string[]) => {
    if (!conditions || conditions.length === 0) return '任何成员'
    return conditions.map((c) => CONDITION_LABEL[c] || c).join('、')
  }
  const requiresText = (prerequisites?: string[]) => {
    if (!prerequisites || prerequisites.length === 0) return ''
    return prerequisites.map((p) => PREREQUISITE_LABEL[p] || p).join('、')
  }
  return (wf.config.transitions || []).map((t) => ({
    from: nameOf(t.fromNodeKey, t.fromColumnId),
    to: nameOf(t.toNodeKey, t.toColumnId),
    who: whoText(t.conditions),
    requires: requiresText(t.prerequisites),
    owner: ownerOf(t.fromNodeKey, t.fromColumnId),
  }))
})

/** 某节点的责任人显示名（没有就返回空串） */
function ownerOf(nodeKey?: string | null, columnId?: number | null): string {
  const nodes = currentWorkflow.value?.config?.nodes || []
  const node =
    nodes.find((n) => nodeKey && n.nodeKey === nodeKey) ||
    nodes.find((n) => columnId != null && n.columnId === columnId)
  if (!node?.ownerUserId) return ''
  if (node.ownerName) return node.ownerName
  return members.value.find((m) => m.userId === node.ownerUserId)?.username || `用户#${node.ownerUserId}`
}

/**
 * 「终态」的判定与看板一致：列名含「完成」或 done。
 *
 * 以前这是一个可以随便勾的复选框，但看板/工作台判终态用的是列名规则，
 * 勾了不生效 —— 那是个会撒谎的开关，所以改成只读徽标 + 说明。
 */
function isTerminalName(name?: string | null): boolean {
  if (!name) return false
  return name.includes('完成') || name.toLowerCase().trim() === 'done'
}

function columnName(columnId?: number | null): string {
  if (!columnId) return '（未落地）'
  return boardColumns.value.find((c) => c.id === columnId)?.name || `列#${columnId}`
}

function nodeLabel(nodeKey?: string | null): string {
  const node = config.value.nodes.find((n) => n.nodeKey === nodeKey)
  return node?.name || '（已删除）'
}

watch(selectedIssueType, () => loadWorkflow())

onMounted(async () => {
  await Promise.all([loadBoardColumns(), loadMembers()])
  await loadWorkflow()
})

/** 拉项目成员（责任人下拉的来源）。失败不阻塞工作流编辑，只是选不了人 */
async function loadMembers() {
  try {
    const res = await getProjectById(projectId)
    const teamId = res.data?.teamId
    if (!teamId) return
    const list = await getMembers(teamId)
    members.value = (list.data || []).map((m: any) => ({
      userId: m.userId,
      username: m.username,
    }))
  } catch {
    members.value = []
  }
}

/** 选完责任人后把显示名一起放进去，保存前界面就能看到名字 */
function onOwnerChange(node: WorkflowNode) {
  const hit = members.value.find((m) => m.userId === node.ownerUserId)
  node.ownerName = hit ? hit.username : null
}

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
    config.value = normalizeConfig(JSON.parse(JSON.stringify(currentWorkflow.value.config)))
  } else {
    config.value = { nodes: [], transitions: [] }
  }
  selectedTransition.value = null
  editing.value = true
}

/** 前端也补一遍身份与坐标：历史配置可能没有 nodeKey，画布靠它连线 */
function normalizeConfig(raw: WorkflowConfig): WorkflowConfig {
  const nodes = (raw.nodes || []).map((n, i) => ({
    ...n,
    nodeKey: n.nodeKey || (n.columnId != null ? `col-${n.columnId}` : `n-local-${i}`),
    x: n.x ?? 40 + i * 220,
    y: n.y ?? 80,
  }))
  const transitions = (raw.transitions || []).map((t) => {
    const from =
      nodes.find((n) => n.nodeKey === t.fromNodeKey) ||
      nodes.find((n) => n.columnId != null && n.columnId === t.fromColumnId)
    const to =
      nodes.find((n) => n.nodeKey === t.toNodeKey) ||
      nodes.find((n) => n.columnId != null && n.columnId === t.toColumnId)
    return {
      ...t,
      fromNodeKey: from?.nodeKey,
      toNodeKey: to?.nodeKey,
      conditions: t.conditions || [],
      prerequisites: t.prerequisites || [],
    }
  })
  return { nodes, transitions } as WorkflowConfig
}

/** 新增一个还没落地的节点：名字先给个占位，保存时后端会为它建列 */
function addFreeNode() {
  const index = config.value.nodes.length + 1
  config.value.nodes.push({
    nodeKey: `n-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
    columnId: null,
    name: `新阶段 ${index}`,
    color: '#909399',
    isTerminal: false,
    x: 40 + (config.value.nodes.length % 4) * 220,
    y: 80 + Math.floor(config.value.nodes.length / 4) * 150,
  })
}

function onDragStart(e: DragEvent, col: { id: number; name: string; color: string }) {
  e.dataTransfer?.setData('columnId', String(col.id))
  e.dataTransfer?.setData('name', col.name)
  e.dataTransfer?.setData('color', col.color)
}

function onDrop(e: DragEvent) {
  const columnId = Number(e.dataTransfer?.getData('columnId'))
  if (!columnId) return
  const name = e.dataTransfer?.getData('name') || columnName(columnId)
  const color = e.dataTransfer?.getData('color') || '#409EFF'
  if (config.value.nodes.some((n) => n.columnId === columnId)) {
    ElMessage.warning('该列已经在流程里了')
    return
  }
  const point = toCanvasPoint(e)
  config.value.nodes.push({
    nodeKey: `col-${columnId}`,
    columnId,
    name,
    color,
    isTerminal: isTerminalName(name),
    x: Math.max(0, point.x - CARD_W / 2),
    y: Math.max(0, point.y - PORT_Y),
  })
}

function removeNode(node: WorkflowNode) {
  config.value.nodes = config.value.nodes.filter((n) => n.nodeKey !== node.nodeKey)
  // 相关的连线一起删，否则保存时会指向不存在的节点
  config.value.transitions = config.value.transitions.filter(
    (t) => t.fromNodeKey !== node.nodeKey && t.toNodeKey !== node.nodeKey,
  )
  selectedTransition.value = null
}

// ===== 画布交互 =====

/** client 坐标 → 画布坐标（考虑滚动） */
function toCanvasPoint(e: MouseEvent): { x: number; y: number } {
  const el = canvasRef.value
  if (!el) return { x: 0, y: 0 }
  const rect = el.getBoundingClientRect()
  return {
    x: e.clientX - rect.left + el.scrollLeft,
    y: e.clientY - rect.top + el.scrollTop,
  }
}

function startDrag(e: MouseEvent, node: WorkflowNode) {
  if (linking.value) return
  const point = toCanvasPoint(e)
  dragging.value = {
    key: node.nodeKey!,
    dx: point.x - (node.x ?? 0),
    dy: point.y - (node.y ?? 0),
  }
}

function startLink(e: MouseEvent, node: WorkflowNode) {
  const point = toCanvasPoint(e)
  linking.value = { fromKey: node.nodeKey!, x: point.x, y: point.y }
}

/**
 * 圆点也可以"点一下再点终点"：拖动要在目标节点上松手，触屏/手抖都不好使。
 * 已经在连线状态时，再点同一个圆点 = 取消。
 */
function onPortClick(_e: MouseEvent, node: WorkflowNode) {
  if (linking.value && linking.value.fromKey === node.nodeKey) {
    linking.value = null
    return
  }
  if (!linking.value) {
    const current = config.value.nodes.find((n) => n.nodeKey === node.nodeKey)
    linking.value = {
      fromKey: node.nodeKey!,
      x: (current?.x ?? 0) + CARD_W,
      y: (current?.y ?? 0) + PORT_Y,
    }
  }
}

function onCanvasMouseMove(e: MouseEvent) {
  const point = toCanvasPoint(e)
  if (dragging.value) {
    const node = config.value.nodes.find((n) => n.nodeKey === dragging.value!.key)
    if (node) {
      node.x = Math.max(0, Math.round(point.x - dragging.value.dx))
      node.y = Math.max(0, Math.round(point.y - dragging.value.dy))
    }
    return
  }
  if (linking.value) {
    linking.value = { ...linking.value, x: point.x, y: point.y }
  }
}

function onCanvasMouseUp() {
  dragging.value = null
}

/**
 * 点空白处才算"放弃连线"。
 *
 * 这里踩过一个坑：原先 mouseup 就清 linking，结果从圆点拖到目标节点时，
 * mouseup 在冒泡到画布后先把 linking 清掉，目标节点的 click 再执行已经晚了——
 * 表现就是"拖了半天没连上"。所以拖/点两条路都要能成立：起点的圆点既可以拖，
 * 也可以点一下再点终点。
 */
function onCanvasClick() {
  linking.value = null
}

function finishLinkTo(node: WorkflowNode) {
  if (!linking.value || linking.value.fromKey === node.nodeKey) return
  addTransition(linking.value.fromKey, node.nodeKey!)
  linking.value = null
}

/** 建一条移动；重复连线不重复添加（用户会以为保存失败） */
function addTransition(fromKey: string, toKey: string) {
  const exists = config.value.transitions.some((t) => t.fromNodeKey === fromKey && t.toNodeKey === toKey)
  if (exists) {
    ElMessage.info('这两步之间已经有线了，点线可以改条件')
    return
  }
  config.value.transitions.push({
    fromNodeKey: fromKey,
    toNodeKey: toKey,
    conditions: [],
    prerequisites: [],
  })
  selectedTransition.value = config.value.transitions.length - 1
}

function selectTransition(idx: number) {
  selectedTransition.value = idx
}

function removeTransition(idx: number) {
  config.value.transitions.splice(idx, 1)
  selectedTransition.value = null
}

function removeSelectedTransition() {
  if (selectedTransition.value === null) return
  removeTransition(selectedTransition.value)
}

/** 连线路径：从起点的右侧圆点连到终点的左侧中点 */
function edgePath(t: WorkflowTransition): string {
  const from = config.value.nodes.find((n) => n.nodeKey === t.fromNodeKey)
  const to = config.value.nodes.find((n) => n.nodeKey === t.toNodeKey)
  if (!from || !to) return ''
  const x1 = (from.x ?? 0) + CARD_W
  const y1 = (from.y ?? 0) + PORT_Y
  const x2 = to.x ?? 0
  const y2 = (to.y ?? 0) + PORT_Y
  const dx = Math.max(40, Math.abs(x2 - x1) / 2)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}

/** 正在拖的这条草稿线 */
const draftEdge = computed(() => {
  const link = linking.value
  if (!link) return ''
  const from = config.value.nodes.find((n) => n.nodeKey === link.fromKey)
  if (!from) return ''
  const x1 = (from.x ?? 0) + CARD_W
  const y1 = (from.y ?? 0) + PORT_Y
  const dx = Math.max(40, Math.abs(link.x - x1) / 2)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${link.x - dx} ${link.y}, ${link.x} ${link.y}`
})

// ===== 保存 / 模板 / 删除 =====

async function onApplyTemplate(templateName: string) {
  if (!templateName) return
  try {
    await ElMessageBox.confirm(`确定要应用「${templateName}」模板吗？这将覆盖当前配置`, '确认')
    const res = await applyTemplate(projectId, selectedIssueType.value, templateName)
    currentWorkflow.value = res.data
    config.value = normalizeConfig(JSON.parse(JSON.stringify(res.data.config)))
    editing.value = true
    await loadBoardColumns()
    ElMessage.success('模板已套用，确认或修改后点「保存工作流」才真正生效')
  } catch {
    selectedTemplate.value = ''
  }
}

async function onSave() {
  if (config.value.nodes.length === 0) {
    ElMessage.warning('请至少加一个节点')
    return
  }
  const nameless = config.value.nodes.find((n) => !n.name || !n.name.trim())
  if (nameless) {
    ElMessage.warning('有节点还没填名字：它会成为看板上的一列，名字不能空着')
    return
  }
  saving.value = true
  try {
    // 终态按看板同一口径算（列名含「完成」/done），交给后端校验"至少一个终态"
    config.value.nodes.forEach((n) => {
      n.isTerminal = isTerminalName(n.name)
    })
    const res = await saveWorkflow({
      projectId,
      issueType: selectedIssueType.value,
      name: `${selectedIssueType.value} 工作流`,
      config: config.value,
    })
    currentWorkflow.value = res.data
    editing.value = false
    await loadBoardColumns()

    const created = res.data?.createdColumns || []
    const ruleCount = (res.data?.config?.transitions || []).length
    const createdText = created.length
      ? `为「${created.join('」「')}」自动创建了看板列（它们原本没有落脚的地方）。`
      : ''
    ElMessage({
      type: created.length ? 'warning' : 'success',
      duration: created.length ? 6000 : 3000,
      message:
        (ruleCount > 0
          ? `已保存并生效：现在看板上「${typeLabel.value}」只能按这 ${ruleCount} 条规则移动。`
          : `已保存：这套工作流没有任何转换规则，看板上「${typeLabel.value}」将无法移动。`) + createdText,
    })
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
  max-width: 1100px;
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
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin: 8px 0 0;
  }

  &__muted {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 8px;

    span + span {
      font-weight: 400;
    }
  }

  &__node-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 6px;
    border: 1px solid;
    border-radius: 6px;
    padding: 6px 10px;
    font-size: 13px;
  }

  &__editor {
    display: grid;
    grid-template-columns: 220px 1fr;
    gap: 16px;
    align-items: start;
  }

  &__palette {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-bg-color);
  }

  &__add-node {
    width: 100%;
  }

  &__palette-hint {
    margin: 10px 0 6px;
  }

  &__columns-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__column-chip {
    display: flex;
    align-items: center;
    gap: 6px;
    border: 1px solid;
    border-radius: 6px;
    padding: 5px 8px;
    font-size: 12px;
    cursor: grab;
    background: var(--el-fill-color-lighter);
  }

  &__column-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  &__canvas-wrap {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-bg-color);
  }

  &__canvas {
    position: relative;
    height: 520px;
    overflow: auto;
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
    background:
      radial-gradient(circle, var(--el-border-color) 1px, transparent 1px) 0 0 / 20px 20px;
  }

  &__canvas-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  &__edges {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  &__edge {
    fill: none;
    stroke: var(--el-color-primary);
    stroke-width: 2;
    color: var(--el-color-primary);
    pointer-events: stroke;
    cursor: pointer;

    &.is-selected {
      stroke-width: 3;
      stroke: var(--el-color-warning);
      color: var(--el-color-warning);
    }

    &.is-guarded {
      stroke-dasharray: 6 4;
    }

    &.is-draft {
      stroke: var(--el-text-color-secondary);
      stroke-dasharray: 4 4;
      pointer-events: none;
    }
  }

  &__card {
    position: absolute;
    width: 200px;
    min-height: 116px;
    padding: 8px 10px;
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    background: var(--el-bg-color);
    box-shadow: var(--el-box-shadow-light);
    cursor: move;
    user-select: none;

    &.is-dragging {
      z-index: 5;
      box-shadow: var(--el-box-shadow);
    }

    &.is-connecting {
      cursor: pointer;
    }
  }

  &__card-head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
  }

  &__grip {
    cursor: move;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1;
    padding: 2px 2px 2px 0;
    user-select: none;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__card-name-input {
    flex: 1;
    min-width: 0;
    border: 1px solid transparent;
    border-radius: 4px;
    background: transparent;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    padding: 2px 4px;

    &:hover,
    &:focus {
      border-color: var(--el-border-color);
      outline: none;
      background: var(--el-fill-color-lighter);
    }
  }

  &__card-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    margin-bottom: 6px;
    font-size: 12px;
  }

  &__port {
    position: absolute;
    right: -9px;
    top: 20px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 2px solid var(--el-color-primary);
    background: var(--el-bg-color);
    cursor: crosshair;
    padding: 0;

    &:hover {
      background: var(--el-color-primary);
    }
  }

  &__edge-panel {
    grid-column: 1 / -1;
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 12px;
    background: var(--el-bg-color);
  }

  &__edge-fields {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;

    label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: var(--el-text-color-regular);
    }
  }

  &__start {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    padding: 24px;
    background: var(--el-bg-color);
  }

  &__tag {
    margin-right: 6px;
  }
}
</style>
