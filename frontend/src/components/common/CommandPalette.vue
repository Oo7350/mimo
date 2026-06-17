<template>
  <Teleport to="body">
    <Transition name="cmd-fade">
      <div v-if="visible" class="cmd-palette" @click.self="close">
        <div class="cmd-palette__panel" @click.stop>
          <div class="cmd-palette__input-wrap">
            <el-icon class="cmd-palette__search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="cmd-palette__input"
              placeholder="搜索任务、跳转页面、快捷操作..."
              @keydown.down.prevent="moveSelection(1)"
              @keydown.up.prevent="moveSelection(-1)"
              @keydown.enter.prevent="executeSelected"
              @keydown.esc="close"
            />
            <kbd class="cmd-palette__kbd">ESC</kbd>
          </div>

          <div v-if="loading" class="cmd-palette__hint">搜索中...</div>
          <div v-else-if="!filteredItems.length" class="cmd-palette__hint">
            {{ query.trim() ? '未找到匹配结果' : '输入关键词搜索任务，或选择下方快捷操作' }}
          </div>

          <div v-else class="cmd-palette__results">
            <template v-for="group in groupedResults" :key="group.label">
              <div class="cmd-palette__group-label">{{ group.label }}</div>
              <button
                v-for="item in group.items"
                :key="item.id"
                :class="['cmd-palette__item', { 'is-active': (item.globalIndex ?? 0) === selectedIndex }]"
                @click="execute(item)"
                @mouseenter="selectedIndex = item.globalIndex ?? 0"
              >
                <div class="cmd-palette__item-icon" :style="item.iconStyle">
                  <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                  <span v-else-if="item.emoji">{{ item.emoji }}</span>
                </div>
                <div class="cmd-palette__item-body">
                  <div class="cmd-palette__item-title">{{ item.title }}</div>
                  <div v-if="item.subtitle" class="cmd-palette__item-sub">{{ item.subtitle }}</div>
                </div>
                <kbd v-if="item.shortcut" class="cmd-palette__item-kbd">{{ item.shortcut }}</kbd>
              </button>
            </template>
          </div>

          <div class="cmd-palette__footer">
            <span><kbd>↑↓</kbd> 选择</span>
            <span><kbd>Enter</kbd> 确认</span>
            <span><kbd>Ctrl K</kbd> 开关</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { queryIssues } from '@/api/issue'
import { getMyProjects } from '@/api/project'
import { useUserStore } from '@/store/user'
import { TYPE_LABEL, PRIORITY_LABEL } from '@/utils/constants'
import type { IssueType, IssuePriority } from '@/types'
import {
  Search, HomeFilled, Folder, UserFilled, Setting, Plus, Grid, Document, Calendar,
} from '@element-plus/icons-vue'
import { useCommandPalette } from '@/composables/useCommandPalette'

export interface CommandItem {
  id: string
  group: string
  title: string
  subtitle?: string
  icon?: any
  emoji?: string
  iconStyle?: Record<string, string>
  shortcut?: string
  action: () => void
  globalIndex?: number
}

const { visible, close: paletteClose } = useCommandPalette()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const query = ref('')
const loading = ref(false)
const selectedIndex = ref(0)
const inputRef = ref<HTMLInputElement>()
const issueResults = ref<any[]>([])
const projects = ref<any[]>([])

let searchTimer: ReturnType<typeof setTimeout> | null = null

function close() {
  paletteClose()
  query.value = ''
  issueResults.value = []
  selectedIndex.value = 0
}

function nav(path: string) {
  close()
  router.push(path)
}

const staticItems = computed<CommandItem[]>(() => {
  const pid = route.params.id ? Number(route.params.id) : null
  const items: CommandItem[] = [
    { id: 'nav-home', group: '导航', title: '工作台', icon: HomeFilled, iconStyle: { background: 'rgba(99,102,241,0.15)', color: 'var(--color-primary)' }, action: () => nav('/') },
    { id: 'nav-projects', group: '导航', title: '项目列表', icon: Folder, iconStyle: { background: 'rgba(16,185,129,0.15)', color: 'var(--color-success)' }, action: () => nav('/projects') },
    { id: 'nav-teams', group: '导航', title: '团队管理', icon: UserFilled, iconStyle: { background: 'rgba(245,158,11,0.15)', color: 'var(--color-warning)' }, action: () => nav('/teams') },
    { id: 'nav-profile', group: '导航', title: '个人设置', icon: Setting, iconStyle: { background: 'rgba(148,163,184,0.2)', color: 'var(--text-secondary)' }, action: () => nav('/profile') },
    { id: 'nav-calendar', group: '导航', title: '日历', icon: Calendar, iconStyle: { background: 'rgba(64,158,255,0.15)', color: '#409EFF' }, action: () => nav('/calendar') },
    { id: 'act-create-team', group: '快捷操作', title: '创建团队', icon: Plus, iconStyle: { background: 'rgba(139,92,246,0.15)', color: 'var(--color-accent)' }, action: () => nav('/teams') },
  ]
  if (pid) {
    items.push(
      { id: 'act-board', group: '当前项目', title: '打开看板', icon: Grid, iconStyle: { background: 'rgba(99,102,241,0.15)', color: 'var(--color-primary)' }, action: () => nav(`/projects/${pid}/board`) },
      { id: 'act-reports', group: '当前项目', title: '打开报告', icon: Document, iconStyle: { background: 'rgba(6,182,212,0.15)', color: '#06b6d4' }, action: () => nav(`/projects/${pid}/reports`) },
      { id: 'act-create-issue', group: '当前项目', title: '新建工作项', icon: Plus, iconStyle: { background: 'rgba(16,185,129,0.15)', color: 'var(--color-success)' }, action: () => nav(`/projects/${pid}/board?create=1`) },
    )
  }
  projects.value.forEach(p => {
    items.push({
      id: `proj-${p.id}`,
      group: '项目',
      title: p.name,
      subtitle: p.key,
      emoji: p.name.charAt(0).toUpperCase(),
      iconStyle: { background: 'rgba(99,102,241,0.12)', color: 'var(--color-primary)' },
      action: () => nav(`/projects/${p.id}/board`),
    })
  })
  return items
})

const issueItems = computed<CommandItem[]>(() =>
  issueResults.value.map(issue => ({
    id: `issue-${issue.id}`,
    group: '任务',
    title: `${issue.issueKey} · ${issue.title}`,
    subtitle: [TYPE_LABEL[issue.type as IssueType], issue.assigneeName, PRIORITY_LABEL[issue.priority as IssuePriority]].filter(Boolean).join(' · '),
    emoji: issue.type === 'STORY' ? '📖' : issue.type === 'BUG' ? '🐛' : '✅',
    iconStyle: {
      background: issue.type === 'BUG' ? 'rgba(239,68,68,0.12)' : issue.type === 'STORY' ? 'rgba(16,185,129,0.12)' : 'rgba(99,102,241,0.12)',
    },
    action: () => {
      const pid = issue.projectId
      if (pid) nav(`/projects/${pid}/board?issue=${issue.id}`)
      else nav(`/projects/0/board?issue=${issue.id}`)
    },
  }))
)

const allItems = computed(() => {
  const q = query.value.trim().toLowerCase()
  let items = [...staticItems.value]
  if (q) {
    items = items.filter(i =>
      i.title.toLowerCase().includes(q) ||
      i.subtitle?.toLowerCase().includes(q)
    )
  }
  if (issueItems.value.length) {
    items = [...items, ...issueItems.value]
  }
  return items.map((item, idx) => ({ ...item, globalIndex: idx }))
})

const filteredItems = computed(() => allItems.value)

const groupedResults = computed(() => {
  const map = new Map<string, CommandItem[]>()
  filteredItems.value.forEach(item => {
    if (!map.has(item.group)) map.set(item.group, [])
    map.get(item.group)!.push(item)
  })
  return Array.from(map.entries()).map(([label, items]) => ({ label, items }))
})

async function searchIssues(keyword: string) {
  if (!keyword.trim()) {
    issueResults.value = []
    return
  }
  loading.value = true
  try {
    const res = await queryIssues({ keyword, page: 1, size: 15 })
    issueResults.value = res.data || []
  } catch {
    issueResults.value = []
  } finally {
    loading.value = false
  }
}

watch(query, (q) => {
  selectedIndex.value = 0
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => searchIssues(q), 280)
})

watch(visible, async (v) => {
  if (v) {
    try {
      const res = await getMyProjects()
      projects.value = res.data || []
    } catch { projects.value = [] }
    await nextTick()
    inputRef.value?.focus()
  } else {
    query.value = ''
    issueResults.value = []
  }
})

function moveSelection(delta: number) {
  const len = filteredItems.value.length
  if (!len) return
  selectedIndex.value = (selectedIndex.value + delta + len) % len
}

function execute(item: CommandItem) {
  item.action()
}

function executeSelected() {
  const item = filteredItems.value[selectedIndex.value]
  if (item) execute(item)
}
</script>

<style scoped lang="scss">
.cmd-palette {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(6px);

  &__panel {
    width: 560px;
    max-width: calc(100vw - 32px);
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-xl);
    box-shadow: var(--shadow-dialog);
    overflow: hidden;
  }

  &__input-wrap {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 16px 18px;
    border-bottom: 1px solid var(--border-color-light);
  }

  &__search-icon {
    color: var(--text-secondary);
    font-size: 18px;
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    font-size: 15px;
    color: var(--text-primary);
    font-family: var(--font-family);

    &::placeholder { color: var(--text-placeholder); }
  }

  &__kbd {
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 5px;
    background: var(--bg-page);
    border: 1px solid var(--border-color);
    color: var(--text-secondary);
    font-family: inherit;
  }

  &__hint {
    padding: 32px 18px;
    text-align: center;
    color: var(--text-secondary);
    font-size: 13px;
  }

  &__results {
    max-height: 360px;
    overflow-y: auto;
    padding: 8px;
  }

  &__group-label {
    padding: 8px 10px 4px;
    font-size: 11px;
    font-weight: 700;
    color: var(--text-placeholder);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 10px 12px;
    border: none;
    border-radius: var(--border-radius-md);
    background: transparent;
    cursor: pointer;
    text-align: left;
    transition: background var(--transition-fast);

    &:hover, &.is-active {
      background: var(--bg-hover);
    }
  }

  &__item-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    flex-shrink: 0;
  }

  &__item-body { flex: 1; min-width: 0; }

  &__item-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__item-sub {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__item-kbd {
    font-size: 10px;
    padding: 2px 5px;
    border-radius: 4px;
    background: var(--bg-page);
    border: 1px solid var(--border-color);
    color: var(--text-placeholder);
  }

  &__footer {
    display: flex;
    gap: 16px;
    padding: 10px 18px;
    border-top: 1px solid var(--border-color-light);
    font-size: 11px;
    color: var(--text-placeholder);

    kbd {
      padding: 1px 4px;
      border-radius: 3px;
      background: var(--bg-page);
      border: 1px solid var(--border-color);
      margin-right: 4px;
    }
  }
}

.cmd-fade-enter-active,
.cmd-fade-leave-active {
  transition: opacity 0.15s ease;
  .cmd-palette__panel {
    transition: transform 0.15s ease, opacity 0.15s ease;
  }
}
.cmd-fade-enter-from,
.cmd-fade-leave-to {
  opacity: 0;
  .cmd-palette__panel {
    transform: scale(0.96) translateY(-8px);
    opacity: 0;
  }
}
</style>
