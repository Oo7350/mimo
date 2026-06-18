<template>
  <div class="gantt-page">
    <div class="gantt-page__hero">
      <el-button @click="$router.push(`/projects/${projectId}`)" text class="gantt-page__back">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="gantt-page__hero-info">
        <h2 class="gantt-page__title">甘特图</h2>
        <p class="gantt-page__subtitle">直观查看任务计划时间线与依赖关系</p>
      </div>
      <div class="gantt-page__hero-extra">
        <el-radio-group v-model="filterType" size="small">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="STORY">故事</el-radio-button>
          <el-radio-button value="TASK">任务</el-radio-button>
          <el-radio-button value="BUG">缺陷</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div v-if="loading" class="gantt-page__loading">
      <SkeletonLoader variant="card" :count="2" />
    </div>

    <div v-else class="gantt-page__card">
      <div class="gantt-page__legend">
        <span class="lg-item"><i class="lg-dot" style="background: #4f46e5"></i> 故事</span>
        <span class="lg-item"><i class="lg-dot" style="background: #0ea5e9"></i> 任务</span>
        <span class="lg-item"><i class="lg-dot" style="background: #ef4444"></i> 缺陷</span>
        <span class="lg-item"><i class="lg-dot" style="background: #10b981"></i> 已完成</span>
        <span class="lg-item"><i class="lg-dot" style="background: #cbd5e1"></i> 依赖</span>
        <span class="gantt-page__legend-spacer" />
        <span class="gantt-page__legend-tip">
          为 Issue 设置「计划起止」后，将在此处按时间线展示，依赖关系用灰线连接
        </span>
      </div>
      <GanttChart :data="filtered" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getGanttByProject, type GanttItem } from '@/api/gantt'
import GanttChart from '@/components/common/GanttChart.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'

const route = useRoute()
const projectId = Number(route.params.id)

const loading = ref(true)
const items = ref<GanttItem[]>([])
const filterType = ref<'' | 'STORY' | 'TASK' | 'BUG'>('')

const filtered = computed(() => {
  if (!filterType.value) return items.value
  return items.value.filter(i => i.type === filterType.value)
})

async function load() {
  loading.value = true
  try {
    const res = await getGanttByProject(projectId)
    items.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.gantt-page { padding: 24px 28px; max-width: 1280px; margin: 0 auto; }
.gantt-page__hero { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.gantt-page__back { font-size: 18px; }
.gantt-page__hero-info { flex: 1; }
.gantt-page__title { font-size: 20px; font-weight: 600; margin: 0; color: #0f172a; }
.gantt-page__subtitle { font-size: 13px; color: #94a3b8; margin: 2px 0 0; }
.gantt-page__hero-extra { display: flex; align-items: center; gap: 8px; }
.gantt-page__loading { padding: 8px 0; }
.gantt-page__card {
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px 22px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}
.gantt-page__legend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.lg-item { display: flex; align-items: center; gap: 5px; }
.lg-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.gantt-page__legend-spacer { flex: 1; }
.gantt-page__legend-tip { color: #94a3b8; font-size: 12px; }
</style>
