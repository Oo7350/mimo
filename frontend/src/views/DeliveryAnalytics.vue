<template>
  <div class="delivery-page">
    <div class="delivery-hero">
      <el-button text class="delivery-hero__back" @click="$router.push(`/projects/${projectId}`)">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="delivery-hero__text">
        <h2>团队交付分析</h2>
        <p>Velocity 与累积流图 · 用历史节奏支持迭代规划</p>
      </div>
      <el-button :loading="refreshing" @click="refreshAll">
        <el-icon><Refresh /></el-icon>
        刷新数据
      </el-button>
    </div>

    <div class="delivery-summary">
      <div class="delivery-summary__item">
        <span>平均 Velocity</span>
        <b>{{ velocityData?.averageCompletedPoints ?? 0 }}</b>
        <small>近 {{ velocityData?.averageSprintCount || 0 }} 个已结束 Sprint</small>
      </div>
      <div class="delivery-summary__item">
        <span>展示 Sprint</span>
        <b>{{ velocityData?.sprints?.length || 0 }}</b>
        <small>最近 6 个 ACTIVE / COMPLETED Sprint</small>
      </div>
      <div class="delivery-summary__item delivery-summary__item--wide">
        <span>CFD 当前快照</span>
        <b>{{ currentCfdTotal }}</b>
        <small>{{ cfdRangeLabel }}</small>
      </div>
    </div>

    <section class="delivery-card">
      <div class="delivery-card__header">
        <div>
          <h3>Velocity</h3>
          <p>比较每个 Sprint 的承诺故事点与实际完成故事点</p>
        </div>
        <el-tag effect="plain" type="info">
          平均 {{ velocityData?.averageCompletedPoints ?? 0 }} 点
        </el-tag>
      </div>
      <VelocityChart :data="velocityData" />
      <div v-if="velocityData?.sprints?.length" class="velocity-table">
        <div class="velocity-table__row velocity-table__row--head">
          <span>Sprint</span><span>承诺</span><span>完成</span><span>完成率</span>
        </div>
        <div
          v-for="sprint in velocityData.sprints"
          :key="sprint.sprintId"
          class="velocity-table__row"
        >
          <span>
            <b>{{ sprint.sprintName }}</b>
            <small>{{ sprint.startDate }} ~ {{ sprint.endDate }}</small>
          </span>
          <span>{{ sprint.committedPoints }}</span>
          <span>{{ sprint.completedPoints }}</span>
          <span>{{ sprint.completionRate }}%</span>
        </div>
      </div>
    </section>

    <section class="delivery-card">
      <div class="delivery-card__header delivery-card__header--filters">
        <div>
          <h3>累积流图 CFD</h3>
          <p>按天观察待办、进行中、待验收和已完成任务累积变化</p>
        </div>
        <div class="cfd-filters">
          <el-select
            v-model="selectedSprintId"
            clearable
            placeholder="全部 Sprint"
            style="width: 170px"
            @change="handleSprintChange"
          >
            <el-option
              v-for="sprint in sprints"
              :key="sprint.id"
              :label="sprint.name"
              :value="sprint.id"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            style="width: 260px"
            @change="loadCfd"
          />
        </div>
      </div>
      <div v-loading="cfdLoading" class="cfd-wrap">
        <CfdChart :data="cfdData" />
      </div>
      <div class="delivery-note">
        CFD 从 v2.15.3 上线后按天保存快照。页面打开时也会刷新当天数据；历史日期仅展示已采集记录，不使用当前状态反推。
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { getProjectVelocity, getProjectCfd, type VelocityVO, type CfdVO } from '@/api/analytics'
import { getSprints, type SprintVO } from '@/api/sprint'
import VelocityChart from '@/components/common/VelocityChart.vue'
import CfdChart from '@/components/common/CfdChart.vue'

const route = useRoute()
const projectId = Number(route.params.id)
const velocityData = ref<VelocityVO | null>(null)
const cfdData = ref<CfdVO | null>(null)
const sprints = ref<SprintVO[]>([])
const selectedSprintId = ref<number | null>(null)
const dateRange = ref<[string, string]>(defaultRange())
const refreshing = ref(false)
const cfdLoading = ref(false)

const currentCfdTotal = computed(() => {
  const points = cfdData.value?.points || []
  return points.length ? points[points.length - 1].totalCount : 0
})
const cfdRangeLabel = computed(() => {
  if (!cfdData.value) return '尚未加载'
  return `${cfdData.value.fromDate} ~ ${cfdData.value.toDate}`
})

function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 13)
  return [formatIso(start), formatIso(end)]
}

function formatIso(date: Date) {
  return date.toISOString().slice(0, 10)
}

function clampDate(date: string, minDate?: string, maxDate?: string) {
  if (minDate && date < minDate) return minDate
  if (maxDate && date > maxDate) return maxDate
  return date
}

async function loadCfd() {
  cfdLoading.value = true
  try {
    const [from, to] = dateRange.value
    const response = await getProjectCfd(projectId, {
      sprintId: selectedSprintId.value || undefined,
      from,
      to,
    })
    cfdData.value = response.data
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '累积流图加载失败')
  } finally {
    cfdLoading.value = false
  }
}

async function handleSprintChange(sprintId?: number | null) {
  const sprint = sprints.value.find((item) => item.id === sprintId)
  const today = formatIso(new Date())
  if (sprint) {
    dateRange.value = [
      sprint.startDate,
      clampDate(today, sprint.startDate, sprint.endDate),
    ]
  } else {
    dateRange.value = defaultRange()
  }
  await loadCfd()
}

async function refreshAll() {
  refreshing.value = true
  try {
    const [sprintResponse, velocityResponse] = await Promise.all([
      getSprints(projectId),
      getProjectVelocity(projectId, 6),
    ])
    sprints.value = sprintResponse.data || []
    velocityData.value = velocityResponse.data
    await loadCfd()
    ElMessage.success('交付分析已刷新')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '交付分析加载失败')
  } finally {
    refreshing.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped lang="scss">
.delivery-page {
  max-width: 1320px;
  margin: 0 auto;
  padding: 24px 28px;
}
.delivery-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border-color-light);
}
.delivery-hero__back {
  font-size: 18px;
}
.delivery-hero__text {
  flex: 1;
}
.delivery-hero__text h2 {
  margin: 0 0 3px;
  color: var(--text-primary);
  font-size: 22px;
}
.delivery-hero__text p,
.delivery-card__header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.delivery-summary {
  display: grid;
  grid-template-columns: 1fr 1fr 1.4fr;
  gap: 14px;
  margin: 20px 0;
}
.delivery-summary__item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 16px 18px;
  border: 1px solid var(--border-color-light);
  border-radius: 11px;
  background: var(--bg-card);
}
.delivery-summary__item span {
  color: var(--text-secondary);
  font-size: 12px;
}
.delivery-summary__item b {
  color: var(--text-primary);
  font-size: 26px;
  line-height: 1.15;
}
.delivery-summary__item small {
  color: var(--text-muted);
  font-size: 11px;
}
.delivery-card {
  margin-bottom: 18px;
  padding: 20px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  background: var(--bg-card);
}
.delivery-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.delivery-card__header h3 {
  margin: 0 0 4px;
  color: var(--text-primary);
  font-size: 16px;
}
.delivery-card__header--filters {
  flex-wrap: wrap;
}
.cfd-filters {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}
.cfd-wrap {
  min-height: 360px;
}
.velocity-table {
  margin-top: 10px;
  border-top: 1px solid var(--border-color-light);
}
.velocity-table__row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 90px 90px 90px;
  gap: 10px;
  align-items: center;
  padding: 9px 6px;
  border-bottom: 1px solid var(--border-color-light);
  color: var(--text-secondary);
  font-size: 12px;
}
.velocity-table__row:last-child {
  border-bottom: 0;
}
.velocity-table__row--head {
  color: var(--text-muted);
  font-weight: 600;
  background: var(--bg-subtle);
}
.velocity-table__row > span:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.velocity-table__row b {
  color: var(--text-primary);
  font-size: 13px;
}
.velocity-table__row small {
  color: var(--text-muted);
  font-size: 10px;
}
.delivery-note {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.55;
}
@media (max-width: 900px) {
  .delivery-page {
    padding: 18px;
  }
  .delivery-summary {
    grid-template-columns: 1fr;
  }
  .velocity-table__row {
    grid-template-columns: minmax(160px, 1fr) repeat(3, 60px);
  }
}
</style>
