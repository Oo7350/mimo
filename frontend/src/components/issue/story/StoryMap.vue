<template>
  <div class="storymap">
    <div class="storymap__header">
      <el-button @click="$router.push(`/projects/${projectId}/board`)" text>
        <el-icon><ArrowLeft /></el-icon> 返回看板
      </el-button>
      <h3 class="storymap__title">用户故事地图</h3>
    </div>

    <div v-if="loading" style="text-align: center; padding: 40px; color: var(--text-secondary)">加载中...</div>

    <div v-else-if="epicGroups.length === 0" class="storymap__empty">
      <el-empty description="暂无故事，请先在项目中创建用户故事">
        <el-button type="primary" @click="$router.push(`/projects/${projectId}/board`)">前往看板创建</el-button>
      </el-empty>
    </div>

    <div v-else class="storymap__epics">
      <div v-for="group in epicGroups" :key="group.epic || 'ungrouped'" class="storymap__epic">
        <div class="storymap__epic-header">
          <span class="storymap__epic-name">
            <el-icon><Collection /></el-icon>
            {{ group.epic || '未分组故事' }}
          </span>
          <el-tag size="small" round>{{ group.stories.length }} 个故事</el-tag>
        </div>
        <div class="storymap__story-row">
          <div
            v-for="story in group.stories"
            :key="story.id"
            class="storymap__story-card"
            :class="{ 'storymap__story-card--done': story.status === 'DONE' }"
            @click="toggleExpand(story.id)"
          >
            <div class="storymap__story-header">
              <span class="storymap__story-key">{{ story.issueKey }}</span>
              <el-tag size="small" type="success" effect="plain">STORY</el-tag>
              <span v-if="story.storyPoints" class="storymap__story-points">{{ story.storyPoints }} pts</span>
            </div>
            <div class="storymap__story-title">{{ story.title }}</div>
            <div class="storymap__story-meta">
              <span v-if="story.userRole" class="storymap__story-role">
                <el-icon :size="12"><User /></el-icon> {{ story.userRole }}
              </span>
              <AcceptanceProgress :criteria="story.acceptanceCriteria || []" />
            </div>

            <!-- Expanded sub-tasks -->
            <div v-if="expandedId === story.id" class="storymap__subtasks">
              <div v-if="story.subTasks && story.subTasks.length > 0" class="storymap__subtask-list">
                <div v-for="st in story.subTasks" :key="st.id" class="storymap__subtask-item">
                  <el-tag size="small" :type="st.status === 'DONE' ? 'success' : 'info'">
                    {{ st.issueKey }}: {{ st.title }}
                  </el-tag>
                </div>
              </div>
              <div v-else class="storymap__subtask-empty">暂无子任务</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute } from 'vue-router'
import { queryIssues } from '@/api/issue'
import type { IssueCard, AcceptanceCriterion } from '@/types'
import { ArrowLeft, Collection, User } from '@element-plus/icons-vue'

const route = useRoute()
const projectId = Number(route.params.id)
const loading = ref(true)
const stories = ref<IssueCard[]>([])
const expandedId = ref<number | null>(null)

// Inline AC progress component
const AcceptanceProgress = (props: { criteria: AcceptanceCriterion[] }) => {
  if (!props.criteria || props.criteria.length === 0) return null
  const done = props.criteria.filter(c => c.done).length
  const total = props.criteria.length
  return h('span', { style: 'font-size:11px;color:var(--text-secondary)' }, `${done}/${total} AC`)
}

const epicGroups = computed(() => {
  const map = new Map<string, IssueCard[]>()
  for (const s of stories.value) {
    const key = s.epic || 'ungrouped'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(s)
  }
  return Array.from(map.entries()).map(([epic, items]) => ({
    epic: epic === 'ungrouped' ? '' : epic,
    stories: items,
  }))
})

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

onMounted(async () => {
  try {
    const res = await queryIssues({ projectId, type: 'STORY', size: 200 })
    stories.value = res.data || []
  } catch { stories.value = [] }
  finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.storymap {
  &__header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
  &__title { font-size: 20px; font-weight: bold; color: var(--text-primary); }
  &__empty { padding: 60px 0; }
  &__epics { display: flex; flex-direction: column; gap: 24px; }
  &__epic-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 2px solid var(--border-color); }
  &__epic-name { font-size: 15px; font-weight: 700; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }
  &__story-row { display: flex; gap: 12px; flex-wrap: wrap; }
  &__story-card {
    background: var(--bg-card); border-radius: var(--border-radius-md);
    padding: 12px; width: 240px; cursor: pointer;
    border: 1px solid var(--border-color-light);
    transition: box-shadow .15s;
    &:hover { box-shadow: 0 2px 8px rgba(0,0,0,.06); }
    &--done { opacity: 0.6; }
  }
  &__story-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
  &__story-key { font-size: 11px; color: var(--text-secondary); font-family: monospace; font-weight: 600; }
  &__story-points { font-size: 11px; color: var(--text-secondary); }
  &__story-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; }
  &__story-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; }
  &__story-role { display: flex; align-items: center; gap: 3px; color: var(--text-secondary); font-style: italic; }
  &__subtasks { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--border-color-light); }
  &__subtask-list { display: flex; flex-wrap: wrap; gap: 4px; }
  &__subtask-item { font-size: 12px; }
  &__subtask-empty { font-size: 11px; color: var(--text-placeholder); }
}
</style>
