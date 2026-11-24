<template>
  <!--
    全员任务流（v2.19.8）。

    这是管理员的工作台，回答一个个人工作台回答不了的问题：**「现在每个人手上有什么、卡在哪」**。
    设计取舍：
    - 按人分列（不是把所有人的任务混成一个长列表）——混在一起看不出谁堆活；
    - 空闲的人也占一格（空着本身是信息，团队 3 人里 2 人没活，管理员得知道）；
    - 未指派的单独一栏，且排在成员列之前——那是"没人认领、但必须有人做"的一类；
    - 负载标签写清依据（`loadReason`），不给"红黄绿但不解释"的灯。
  -->
  <div class="team-flow">
    <!-- 概览 -->
    <div class="team-flow__summary">
      <div
        v-for="(item, index) in summaryItems"
        :key="item.key"
        class="team-flow__stat"
        :class="`team-flow__stat--${item.tone}`"
        :style="{ animationDelay: `${index * 60}ms` }"
      >
        <span class="team-flow__stat-value">{{ item.value }}</span>
        <span class="team-flow__stat-label">{{ item.label }}</span>
      </div>
    </div>

    <!-- 工具条：搜索 + 筛选 + 排序 -->
    <div class="team-flow__toolbar">
      <el-input
        v-model="keyword"
        class="team-flow__search"
        size="small"
        placeholder="搜索成员或任务标题"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="team-flow__filters">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          :class="['team-flow__chip', { 'is-active': riskFilter === option.value }]"
          @click="riskFilter = option.value"
        >
          {{ option.label }}
          <span v-if="option.count !== undefined" class="team-flow__chip-count">{{ option.count }}</span>
        </button>
      </div>
      <el-radio-group v-model="sortMode" size="small" class="team-flow__sort">
        <el-radio-button value="load">按负载</el-radio-button>
        <el-radio-button value="name">按姓名</el-radio-button>
        <el-radio-button value="overdue">按逾期</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 未指派 -->
    <section v-if="unassignedVisible.length" class="team-flow__unassigned">
      <header class="team-flow__unassigned-hd">
        <span class="team-flow__unassigned-title">
          <el-icon><WarningFilled /></el-icon>
          未指派 · {{ unassignedVisible.length }} 项
        </span>
        <span class="team-flow__unassigned-hint">没人认领，但总得有人做</span>
      </header>
      <div class="team-flow__unassigned-list">
        <button
          v-for="(task, index) in unassignedVisible"
          :key="task.id"
          class="team-flow__unassigned-item"
          :style="{ animationDelay: `${index * 45}ms` }"
          @click="emit('open-task', { id: task.id, projectId: task.projectId }, '未指派')"
        >
          <span class="team-flow__type" :class="`team-flow__type--${task.type.toLowerCase()}`">
            {{ typeLetter(task.type) }}
          </span>
          <span class="team-flow__unassigned-name">{{ task.title }}</span>
          <span class="team-flow__unassigned-meta">{{ task.issueKey }}</span>
          <el-tag v-if="task.dueDate" size="small" effect="plain" type="info">
            {{ task.dueDate }}
          </el-tag>
        </button>
      </div>
    </section>

    <!-- 成员流 -->
    <div v-if="visibleMembers.length" class="team-flow__grid">
      <article
        v-for="(member, memberIndex) in visibleMembers"
        :key="member.userId"
        class="member-stream"
        :class="[`member-stream--${member.loadLevel.toLowerCase()}`, { 'is-idle': !member.tasks.length }]"
        :style="{ animationDelay: `${memberIndex * 70}ms` }"
      >
        <header class="member-stream__hd">
          <div
            class="member-stream__avatar"
            :style="{ background: avatarGradient(member.username, 'user') }"
          >
            {{ member.username.charAt(0).toUpperCase() }}
            <span v-if="member.online" class="member-stream__online" title="当前在线" />
          </div>
          <div class="member-stream__who">
            <div class="member-stream__name-row">
              <span class="member-stream__name">{{ member.username }}</span>
              <AdminBadge v-if="member.admin" size="small" icon-only quiet />
            </div>
            <span class="member-stream__load" :title="member.loadReason">{{ loadLabel(member.loadLevel) }}</span>
          </div>
          <div class="member-stream__counts">
            <span class="member-stream__count" :title="`未完成 ${member.openCount} 项`">
              {{ member.openCount }}
            </span>
            <span v-if="member.overdueTaskCount" class="member-stream__count member-stream__count--danger">
              {{ member.overdueTaskCount }} 逾期
            </span>
          </div>
        </header>

        <!-- 负载条：宽度 = 相对最忙的那个人，让"谁堆活"一眼可比 -->
        <div class="member-stream__bar" :title="member.loadReason">
          <span
            class="member-stream__bar-fill"
            :style="{ width: loadWidth(member) + '%' }"
          />
        </div>

        <div v-if="!member.tasks.length" class="member-stream__empty">
          当前没有分配给他的未完成任务
        </div>

        <TransitionGroup v-else name="flow-task" tag="div" class="member-stream__tasks">
          <button
            v-for="(task, taskIndex) in member.tasks"
            :key="task.id"
            class="flow-task"
            :class="{ 'flow-task--overdue': isOverdue(task), 'flow-task--doing': task.status === 'IN_PROGRESS' }"
            :style="{ animationDelay: `${memberIndex * 70 + taskIndex * 40}ms` }"
            @click="emit('open-task', task, member.username)"
          >
            <span class="flow-task__type" :class="`flow-task__type--${task.type.toLowerCase()}`">
              {{ typeLetter(task.type) }}
            </span>
            <span class="flow-task__main">
              <span class="flow-task__title">{{ task.title }}</span>
              <span class="flow-task__meta">
                {{ task.issueKey }}
                <template v-if="task.projectName"> · {{ task.projectName }}</template>
                <template v-if="task.dueDate"> · 截止 {{ task.dueDate }}</template>
              </span>
            </span>
            <el-tag
              v-if="task.riskLevel !== 'LOW'"
              size="small"
              effect="plain"
              :type="task.riskLevel === 'HIGH' ? 'danger' : 'warning'"
            >
              {{ task.riskLevel === 'HIGH' ? '高风险' : '关注' }}
            </el-tag>
            <el-icon class="flow-task__arrow"><ArrowRight /></el-icon>
          </button>
        </TransitionGroup>
      </article>
    </div>

    <el-empty
      v-else
      :description="emptyText"
      :image-size="64"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Search, WarningFilled } from '@element-plus/icons-vue'
import AdminBadge from '@/components/common/AdminBadge.vue'
import { avatarGradient } from '@/utils/color'
import type { MemberLoadLevel, MemberStream, TeamWorkbenchData, WorkbenchTask } from '@/types/workbench'

const props = defineProps<{
  data: TeamWorkbenchData | null
}>()

const emit = defineEmits<{
  (event: 'open-task', task: { id: number; projectId: number } & Partial<WorkbenchTask>, ownerName: string): void
}>()

const keyword = ref('')
const riskFilter = ref<'all' | 'overdue' | 'doing' | 'idle'>('all')
const sortMode = ref<'load' | 'name' | 'overdue'>('load')

const summary = computed(() => props.data?.summary)
const members = computed(() => props.data?.members || [])

const summaryItems = computed(() => [
  { key: 'members', label: '成员', value: summary.value?.memberCount ?? 0, tone: 'neutral' },
  { key: 'online', label: '在线', value: summary.value?.onlineMemberCount ?? 0, tone: 'success' },
  { key: 'open', label: '未完成', value: summary.value?.openTaskCount ?? 0, tone: 'neutral' },
  { key: 'doing', label: '进行中', value: summary.value?.inProgressTaskCount ?? 0, tone: 'primary' },
  { key: 'overdue', label: '逾期', value: summary.value?.overdueTaskCount ?? 0, tone: 'danger' },
  { key: 'idle', label: '空闲成员', value: (summary.value?.memberCount ?? 0) - (summary.value?.busyMemberCount ?? 0), tone: 'muted' },
])

/** 逾期项按"当前日期"现算，避免后端返回的 riskLevel 与前端筛选口径不一致 */
function isOverdue(task: { dueDate?: string; riskLevel?: string }) {
  if (!task.dueDate) return false
  return task.dueDate < new Date().toISOString().slice(0, 10)
}

const filterOptions = computed(() => [
  { label: '全部', value: 'all' as const, count: members.value.length },
  {
    label: '有逾期',
    value: 'overdue' as const,
    count: members.value.filter((member) => member.overdueTaskCount > 0).length,
  },
  {
    label: '进行中',
    value: 'doing' as const,
    count: members.value.filter((member) => member.inProgressCount > 0).length,
  },
  {
    label: '空闲',
    value: 'idle' as const,
    count: members.value.filter((member) => member.openCount === 0).length,
  },
])

const matchedMembers = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  let list = members.value
  if (riskFilter.value === 'overdue') list = list.filter((member) => member.overdueTaskCount > 0)
  else if (riskFilter.value === 'doing') list = list.filter((member) => member.inProgressCount > 0)
  else if (riskFilter.value === 'idle') list = list.filter((member) => member.openCount === 0)
  if (text) {
    list = list.filter(
      (member) =>
        member.username.toLowerCase().includes(text) ||
        member.tasks.some((task) => task.title.toLowerCase().includes(text)),
    )
  }
  return [...list]
})

const maxOpen = computed(() => Math.max(1, ...members.value.map((member) => member.openCount || 0)))

const visibleMembers = computed(() => {
  const list = matchedMembers.value
  if (sortMode.value === 'name') {
    return [...list].sort((left, right) => left.username.localeCompare(right.username, 'zh-Hans-CN'))
  }
  if (sortMode.value === 'overdue') {
    return [...list].sort((left, right) => right.overdueTaskCount - left.overdueTaskCount)
  }
  return [...list].sort((left, right) => right.openCount - left.openCount)
})

const unassignedVisible = computed(() => {
  const list = props.data?.summary?.unassignedTasks || []
  const text = keyword.value.trim().toLowerCase()
  if (!text) return list
  return list.filter(
    (task) => task.title.toLowerCase().includes(text) || (task.issueKey || '').toLowerCase().includes(text),
  )
})

const emptyText = computed(() => {
  if (!members.value.length) return '这个范围内还没有团队成员'
  if (keyword.value.trim()) return `没有匹配「${keyword.value.trim()}」的成员或任务`
  if (riskFilter.value === 'overdue') return '当前没有成员手上有逾期任务'
  if (riskFilter.value === 'doing') return '当前没有成员有进行中的任务'
  if (riskFilter.value === 'idle') return '没有人是空闲的，全员都有活'
  return '暂无数据'
})

function loadWidth(member: MemberStream) {
  const ratio = (member.openCount || 0) / maxOpen.value
  // 最少给 6%，否则 1 项和 0 项在视觉上一样，"有一点活"就看不出来了
  return Math.max(6, Math.round(ratio * 100))
}

function loadLabel(level: MemberLoadLevel) {
  return {
    OVERLOADED: '超载',
    BUSY: '偏忙',
    NORMAL: '正常',
    IDLE: '空闲',
  }[level]
}

function typeLetter(type: string) {
  if (type === 'STORY') return 'S'
  if (type === 'BUG') return 'B'
  return 'T'
}
</script>

<style scoped lang="scss">
.team-flow {
  display: flex;
  flex-direction: column;
  gap: 14px;

  &__summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(104px, 1fr));
    gap: 8px;
  }

  &__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 12px;
    background: var(--bg-subtle);
    border: 1px solid var(--border-light);
    animation: stat-in 0.42s ease-out both;

    &--danger { background: var(--bg-danger-soft); border-color: rgba(220, 38, 38, 0.22); }
    &--success { background: var(--bg-success-soft); border-color: rgba(5, 150, 105, 0.2); }
    &--primary { background: var(--color-primary-lighter); border-color: rgba(79, 70, 229, 0.2); }
    &--muted { background: var(--bg-body); }
  }

  &__stat-value {
    font-size: 20px;
    font-weight: 800;
    line-height: 1.1;
    color: var(--text-primary);
    font-variant-numeric: tabular-nums;
  }

  &__stat-label {
    font-size: 12px;
    /* 用 secondary 而不是 muted：淡色统计卡底上 muted 只有 2.95:1（实测卡在 3:1 边缘下） */
    color: var(--text-secondary);
  }

  &__toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__search {
    width: 220px;
  }

  &__filters {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 4px 10px;
    font-size: 12px;
    border-radius: 999px;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.18s ease;

    &:hover {
      border-color: var(--color-primary);
      color: var(--color-primary);
    }

    &.is-active {
      background: var(--color-primary-solid);
      border-color: var(--color-primary-solid);
      color: #fff;
    }
  }

  &__chip-count {
    font-variant-numeric: tabular-nums;
    opacity: 0.75;
  }

  &__sort {
    margin-left: auto;
  }

  /* ---------- 未指派 ---------- */
  &__unassigned {
    border-radius: 14px;
    border: 1px dashed rgba(220, 38, 38, 0.35);
    background: var(--bg-danger-soft);
    padding: 10px 12px;
  }

  &__unassigned-hd {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__unassigned-title {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    font-weight: 700;
    color: var(--color-danger);
  }

  &__unassigned-hint {
    font-size: 12px;
    color: var(--text-muted);
  }

  &__unassigned-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 168px;
    overflow-y: auto;
  }

  &__unassigned-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border: none;
    border-radius: 8px;
    background: var(--bg-card);
    color: var(--text-primary);
    font-size: 13px;
    text-align: left;
    cursor: pointer;
    animation: task-in 0.38s ease-out both;
    transition: transform 0.16s ease, box-shadow 0.16s ease;

    &:hover {
      transform: translateX(2px);
      box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
    }
  }

  &__unassigned-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__unassigned-meta {
    font-size: 12px;
    color: var(--text-muted);
    font-variant-numeric: tabular-nums;
  }

  &__type {
    width: 18px;
    height: 18px;
    border-radius: 5px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 800;
    color: #fff;
    flex-shrink: 0;

    &--story { background: #2563eb; }
    &--task { background: #047857; }
    &--bug { background: #b91c1c; }
  }

  /* ---------- 成员列 ---------- */
  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(286px, 1fr));
    gap: 12px;
  }
}

.member-stream {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  animation: stream-in 0.46s cubic-bezier(0.22, 0.8, 0.3, 1) both;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: var(--color-primary-light);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  }

  &--overloaded { border-left: 3px solid var(--color-danger); }
  &--busy { border-left: 3px solid var(--color-warning); }
  &--normal { border-left: 3px solid var(--color-success); }
  &--idle { border-left: 3px solid var(--border-color); background: var(--bg-subtle); }

  &__hd {
    display: flex;
    align-items: center;
    gap: 9px;
  }

  &__avatar {
    position: relative;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 700;
    font-size: 14px;
    flex-shrink: 0;
  }

  &__online {
    position: absolute;
    right: -2px;
    bottom: -2px;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: #22c55e;
    border: 2px solid var(--bg-card);
  }

  &__who {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    flex: 1;
  }

  &__name-row {
    display: flex;
    align-items: center;
    gap: 5px;
  }

  &__name {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__load {
    font-size: 11.5px;
    color: var(--text-muted);
  }

  &__counts {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    font-variant-numeric: tabular-nums;
  }

  &__count {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);

    &--danger {
      font-size: 11px;
      color: var(--color-danger);
      font-weight: 600;
    }
  }

  &__bar {
    height: 4px;
    border-radius: 999px;
    background: var(--bg-subtle-strong);
    overflow: hidden;
  }

  &__bar-fill {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--color-primary), var(--color-primary-light));
    transition: width 0.6s cubic-bezier(0.22, 0.8, 0.3, 1);
  }

  &--overloaded &__bar-fill {
    background: linear-gradient(90deg, var(--color-danger), #f97316);
  }

  &--busy &__bar-fill {
    background: linear-gradient(90deg, var(--color-warning), #f59e0b);
  }

  &--idle &__bar-fill {
    background: var(--border-color);
  }

  &__empty {
    padding: 12px 4px;
    font-size: 12px;
    /* placeholder 色压 bg-body 只有 2.76:1（实测）；"没有任务"这句是正经信息，按正文色给 */
    color: var(--text-muted);
    text-align: center;
    border-radius: 8px;
    background: var(--bg-body);
  }

  &__tasks {
    display: flex;
    flex-direction: column;
    gap: 5px;
    max-height: 420px;
    overflow-y: auto;
    padding-right: 2px;
  }
}

.flow-task {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 7px 8px;
  border-radius: 9px;
  border: 1px solid transparent;
  background: var(--bg-subtle);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  animation: task-in 0.4s ease-out both;
  transition: transform 0.16s ease, background 0.16s ease, border-color 0.16s ease;

  &:hover {
    transform: translateX(2px);
    background: var(--bg-hover);
    border-color: var(--color-primary-light);
  }

  &--overdue {
    background: var(--bg-danger-soft);
  }

  &--doing {
    box-shadow: inset 2px 0 0 var(--color-primary);
  }

  &__type {
    width: 18px;
    height: 18px;
    border-radius: 5px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 800;
    color: #fff;
    flex-shrink: 0;

    &--story { background: #2563eb; }
    &--task { background: #047857; }
    &--bug { background: #b91c1c; }
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    flex: 1;
  }

  &__title {
    font-size: 12.5px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    font-size: 11.5px;
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__arrow {
    color: var(--text-placeholder);
    font-size: 12px;
    opacity: 0;
    transform: translateX(-3px);
    transition: opacity 0.16s ease, transform 0.16s ease;
  }

  &:hover &__arrow {
    opacity: 1;
    transform: translateX(0);
  }
}

/* ---------- 动画 ---------- */
@keyframes stream-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes task-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes stat-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.flow-task-enter-active,
.flow-task-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.flow-task-enter-from,
.flow-task-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (prefers-reduced-motion: reduce) {
  .team-flow__stat,
  .member-stream,
  .flow-task,
  .team-flow__unassigned-item {
    animation: none;
  }
}
</style>
