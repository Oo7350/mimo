<template>
  <!--
    任务详情抽屉（v2.19.8）。

    为什么是"抽屉"而不是直接跳看板：管理员在全员任务流里点击是为了**快速判断**——
    "这活卡在哪、写的什么、有没有验收标准"，判断完接着看下一个人。
    跳页面会把人带走，回来还要重新找位置。

    交互：右侧滑入 + 内容分段淡入（骨架 → 真数据），Esc 关闭，底部一个「在项目里打开」的出口。
  -->
  <el-drawer
    :model-value="visible"
    :size="drawerWidth"
    direction="rtl"
    :with-header="false"
    class="task-drawer"
    @update:model-value="close"
  >
    <div v-if="loading" class="task-drawer__skeleton">
      <SkeletonLoader variant="list-item" :count="2" />
      <div class="task-drawer__skeleton-grid">
        <span v-for="i in 6" :key="i" class="task-drawer__skeleton-cell" />
      </div>
      <SkeletonLoader variant="list-item" :count="3" />
    </div>

    <div v-else-if="issue" class="task-drawer__body">
      <header class="task-drawer__hd" :class="`task-drawer__hd--${issue.type.toLowerCase()}`">
        <div class="task-drawer__hd-top">
          <span class="task-drawer__type">{{ typeLabel(issue.type) }}</span>
          <span class="task-drawer__key">{{ issue.issueKey }}</span>
          <span class="task-drawer__owner-hint">{{ ownerName }} 的任务流</span>
          <button class="task-drawer__close" title="关闭（Esc）" @click="close">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <h2 class="task-drawer__title">{{ issue.title }}</h2>
        <div class="task-drawer__tags">
          <el-tag size="small" :type="statusType" effect="dark">{{ statusLabel }}</el-tag>
          <el-tag size="small" effect="plain" :type="priorityType(issue.priority)">
            {{ PRIORITY_LABEL[issue.priority] || issue.priority }}
          </el-tag>
          <el-tag v-if="overdueDays > 0" size="small" type="danger" effect="plain">
            逾期 {{ overdueDays }} 天
          </el-tag>
          <el-tag v-if="issue.storyPoints" size="small" type="info" effect="plain">
            {{ issue.storyPoints }} 点
          </el-tag>
          <el-tag v-for="label in issue.labels || []" :key="label.id" size="small" effect="plain">
            {{ label.label }}
          </el-tag>
        </div>
      </header>

      <div class="task-drawer__meta">
        <div v-for="(item, index) in metaItems" :key="item.label" class="task-drawer__meta-item" :style="{ animationDelay: `${index * 35}ms` }">
          <span class="task-drawer__meta-label">{{ item.label }}</span>
          <span class="task-drawer__meta-value" :class="{ 'is-muted': !item.value }">{{ item.value || '—' }}</span>
        </div>
      </div>

      <section v-if="issue.description" class="task-drawer__section">
        <h3 class="task-drawer__section-title">描述</h3>
        <div class="task-drawer__markdown" v-html="renderMarkdown(issue.description)" />
      </section>

      <section v-if="(issue.acceptanceCriteria || []).length" class="task-drawer__section">
        <h3 class="task-drawer__section-title">
          验收标准
          <span class="task-drawer__section-count">{{ doneCriteria }}/{{ (issue.acceptanceCriteria || []).length }}</span>
        </h3>
        <ul class="task-drawer__criteria">
          <li v-for="item in issue.acceptanceCriteria" :key="item.id" :class="{ 'is-done': item.done }">
            <el-icon><CircleCheck v-if="item.done" /><Select v-else /></el-icon>
            <span>{{ item.text }}</span>
          </li>
        </ul>
      </section>

      <section v-if="issue.type === 'BUG'" class="task-drawer__section">
        <h3 class="task-drawer__section-title">缺陷信息</h3>
        <dl class="task-drawer__kv">
          <template v-if="issue.stepsToRepro"><dt>复现步骤</dt><dd>{{ issue.stepsToRepro }}</dd></template>
          <template v-if="issue.environment"><dt>环境</dt><dd>{{ issue.environment }}</dd></template>
          <template v-if="issue.expectedResult"><dt>预期</dt><dd>{{ issue.expectedResult }}</dd></template>
          <template v-if="issue.actualResult"><dt>实际</dt><dd>{{ issue.actualResult }}</dd></template>
          <template v-if="issue.bugStatus"><dt>缺陷状态</dt><dd>{{ issue.bugStatus }}</dd></template>
        </dl>
      </section>

      <section v-if="(issue.subTasks || []).length" class="task-drawer__section">
        <h3 class="task-drawer__section-title">
          子任务
          <span class="task-drawer__section-count">{{ doneSubTasks }}/{{ (issue.subTasks || []).length }}</span>
        </h3>
        <div class="task-drawer__subtasks">
          <button
            v-for="sub in issue.subTasks"
            :key="sub.id"
            class="task-drawer__subtask"
            @click="openSubTask(sub)"
          >
            <span class="task-drawer__subtask-key">{{ sub.issueKey }}</span>
            <span class="task-drawer__subtask-title">{{ sub.title }}</span>
            <el-tag size="small" effect="plain" :type="sub.status === 'DONE' ? 'success' : 'info'">
              {{ sub.status === 'DONE' ? '已完成' : sub.status === 'IN_PROGRESS' ? '进行中' : '待开始' }}
            </el-tag>
          </button>
        </div>
      </section>

      <footer class="task-drawer__ft">
        <span class="task-drawer__ft-hint">
          {{ issue.updatedAt ? `最后更新 ${formatTime(issue.updatedAt)}` : '' }}
        </span>
        <el-button size="small" @click="close">关闭</el-button>
        <el-button size="small" type="primary" @click="openInBoard">
          在项目里打开
          <el-icon class="task-drawer__ft-icon"><Right /></el-icon>
        </el-button>
      </footer>
    </div>

    <el-empty v-else description="任务不存在或已被删除" :image-size="64" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, Close, Right, Select } from '@element-plus/icons-vue'
import { getIssueById } from '@/api/issue'
import { renderMarkdown } from '@/utils/markdown'
import { PRIORITY_LABEL } from '@/utils/constants'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import type { IssueCard, IssuePriority, IssueType } from '@/types'

const props = defineProps<{
  visible: boolean
  issueId?: number | null
  /** 从谁的任务流点进来的，用来在头部说明上下文 */
  ownerName?: string
  /** 项目名不在工作项详情里返回，由调用方带进来（任务流里本来就有） */
  projectName?: string
}>()

const emit = defineEmits<{ (event: 'update:visible', value: boolean): void }>()

const router = useRouter()
const loading = ref(false)
const issue = ref<IssueCard | null>(null)

const drawerWidth = computed(() => (window.innerWidth < 900 ? '92%' : '620px'))

const TYPE_LABEL: Record<string, string> = { STORY: '故事', TASK: '任务', BUG: '缺陷' }
const STATUS_LABEL: Record<string, string> = {
  TODO: '待开始',
  IN_PROGRESS: '进行中',
  DONE: '已完成',
  BLOCKED: '被阻塞',
}

function typeLabel(type: IssueType | string) {
  return TYPE_LABEL[type] || String(type)
}

const statusLabel = computed(() => {
  if (!issue.value) return ''
  if (issue.value.type === 'BUG' && issue.value.bugStatus) return issue.value.bugStatus
  return STATUS_LABEL[issue.value.status] || issue.value.status || '—'
})

const statusType = computed<'success' | 'warning' | 'primary' | 'info' | 'danger'>(() => {
  const status = issue.value?.status
  if (status === 'DONE') return 'success'
  if (status === 'IN_PROGRESS') return 'primary'
  return 'info'
})

function priorityType(priority: IssuePriority) {
  if (priority === 'HIGHEST' || priority === 'HIGH') return 'danger'
  if (priority === 'LOW' || priority === 'LOWEST') return 'info'
  return 'warning'
}

const overdueDays = computed(() => {
  const due = issue.value?.dueDate
  if (!due) return 0
  const today = new Date().toISOString().slice(0, 10)
  if (due >= today) return 0
  const diff = Date.parse(today) - Date.parse(due)
  return Math.max(1, Math.round(diff / 86400000))
})

const doneCriteria = computed(
  () => (issue.value?.acceptanceCriteria || []).filter((item) => item.done).length,
)
const doneSubTasks = computed(() => (issue.value?.subTasks || []).filter((sub) => sub.status === 'DONE').length)

const metaItems = computed(() => {
  const value = issue.value
  if (!value) return []
  return [
    { label: '负责人', value: value.assigneeName || '未指派' },
    { label: '报告人', value: value.reporterName },
    { label: '所属项目', value: props.projectName || (value.projectId ? `项目 #${value.projectId}` : '') },
    { label: '看板列', value: value.columnName },
    { label: '计划截止', value: value.dueDate },
    { label: '创建时间', value: value.createdAt ? String(value.createdAt).slice(0, 10) : '' },
  ]
})

function formatTime(value: string) {
  return String(value).replace('T', ' ').slice(0, 16)
}

function close() {
  emit('update:visible', false)
}

function openInBoard() {
  if (!issue.value?.projectId) return
  const target = { path: `/projects/${issue.value.projectId}/board`, query: { issue: String(issue.value.id) } }
  close()
  router.push(target)
}

function openSubTask(sub: IssueCard) {
  if (!sub?.id) return
  // 子任务不出抽屉：同一个抽屉里换内容，返回时不用重新找位置
  load(sub.id)
}

async function load(issueId?: number | null) {
  if (!issueId) return
  loading.value = true
  issue.value = null
  try {
    const res = await getIssueById(issueId)
    issue.value = res.data
  } catch {
    issue.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.issueId] as const,
  ([visible, issueId]) => {
    if (!visible) return
    if (issueId === issue.value?.id) return
    load(issueId)
  },
  { immediate: true },
)

/**
 * Esc 关闭。
 *
 * 不依赖 Element 抽屉自带的 `close-on-press-escape`：那个监听挂在抽屉容器上，
 * 焦点不在抽屉里时（刚用鼠标点完卡片就是这样）按键收不到 —— e2e 实测按 Esc 关不掉。
 * 这里挂 window 级监听，打开时注册、关闭/卸载时摘掉，不留常驻监听。
 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.visible) {
    event.stopPropagation()
    close()
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) window.addEventListener('keydown', onKeydown, true)
    else window.removeEventListener('keydown', onKeydown, true)
  },
)

onUnmounted(() => window.removeEventListener('keydown', onKeydown, true))
</script>

<style scoped lang="scss">
.task-drawer {
  &__body {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 4px 2px 8px;
    animation: drawer-content-in 0.34s ease-out both;
  }

  &__skeleton {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 8px 2px;
  }

  &__skeleton-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  &__skeleton-cell {
    height: 44px;
    border-radius: 10px;
    background: var(--bg-subtle);
    animation: skeleton-pulse 1.2s ease-in-out infinite;
  }

  /* ---------- 头部 ---------- */
  &__hd {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 16px 16px;
    border-radius: 14px;
    background: var(--bg-subtle);
    border: 1px solid var(--border-light);
    border-left: 3px solid var(--color-primary);

    &--story { border-left-color: #2563eb; }
    &--task { border-left-color: #047857; }
    &--bug { border-left-color: #b91c1c; }
  }

  &__hd-top {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__type {
    font-size: 11.5px;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    background: var(--color-primary-lighter);
    color: var(--color-primary);
  }

  &__key {
    font-size: 12px;
    color: var(--text-muted);
    font-variant-numeric: tabular-nums;
  }

  &__owner-hint {
    font-size: 12px;
    color: var(--text-placeholder);
  }

  &__close {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: var(--text-secondary);
    cursor: pointer;
    transition: background 0.16s ease, color 0.16s ease;

    &:hover {
      background: var(--bg-hover);
      color: var(--text-primary);
    }
  }

  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 800;
    line-height: 1.4;
    color: var(--text-primary);
  }

  &__tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  /* ---------- 字段 ---------- */
  &__meta {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  &__meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--bg-card);
    border: 1px solid var(--border-light);
    animation: drawer-content-in 0.34s ease-out both;
  }

  &__meta-label {
    font-size: 11.5px;
    color: var(--text-muted);
  }

  &__meta-value {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.is-muted {
      color: var(--text-placeholder);
      font-weight: 500;
    }
  }

  /* ---------- 分区 ---------- */
  &__section {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
  }

  &__section-count {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-muted);
    font-variant-numeric: tabular-nums;
  }

  &__markdown {
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--text-regular);
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--bg-card);
    border: 1px solid var(--border-light);

    :deep(p) { margin: 0 0 8px; }
    :deep(p:last-child) { margin-bottom: 0; }
    :deep(code) {
      padding: 1px 5px;
      border-radius: 5px;
      background: var(--bg-subtle-strong);
      font-size: 12.5px;
    }
    :deep(pre) {
      padding: 10px;
      border-radius: 8px;
      background: var(--bg-subtle-strong);
      overflow-x: auto;
    }
    :deep(ul), :deep(ol) { padding-left: 20px; margin: 0 0 8px; }
    :deep(a) { color: var(--color-primary); }
  }

  &__criteria {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;

    li {
      display: flex;
      align-items: flex-start;
      gap: 7px;
      font-size: 13px;
      color: var(--text-regular);

      &.is-done {
        color: var(--text-muted);
        text-decoration: line-through;
      }

      .el-icon {
        margin-top: 2px;
        color: var(--text-placeholder);
      }

      &.is-done .el-icon {
        color: var(--color-success);
      }
    }
  }

  &__kv {
    margin: 0;
    display: grid;
    grid-template-columns: 76px 1fr;
    gap: 6px 10px;
    font-size: 13px;

    dt {
      color: var(--text-muted);
      font-size: 12px;
    }

    dd {
      margin: 0;
      color: var(--text-regular);
      white-space: pre-wrap;
    }
  }

  &__subtasks {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  &__subtask {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 9px;
    border: 1px solid var(--border-light);
    border-radius: 9px;
    background: var(--bg-card);
    cursor: pointer;
    text-align: left;
    transition: border-color 0.16s ease, transform 0.16s ease;

    &:hover {
      border-color: var(--color-primary-light);
      transform: translateX(2px);
    }
  }

  &__subtask-key {
    font-size: 11.5px;
    color: var(--text-muted);
    font-variant-numeric: tabular-nums;
  }

  &__subtask-title {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* ---------- 底部 ---------- */
  &__ft {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid var(--border-light);
  }

  &__ft-hint {
    flex: 1;
    font-size: 12px;
    color: var(--text-placeholder);
  }

  &__ft-icon {
    margin-left: 4px;
  }
}

@keyframes drawer-content-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .task-drawer__body,
  .task-drawer__meta-item,
  .task-drawer__skeleton-cell {
    animation: none;
  }
}
</style>
