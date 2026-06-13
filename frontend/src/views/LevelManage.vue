<template>
  <div class="level-manage">
    <PageHeader title="用户等级管理" subtitle="设置用户的 L1-L4 铭牌等级" />

    <div class="level-legend">
      <div v-for="item in levelList" :key="item.level" class="level-legend__item">
        <UserBadge :level="item" size="small" />
        <span class="level-legend__label">{{ item.label }}</span>
      </div>
    </div>

    <el-table :data="userLevels" v-loading="loading" stripe>
      <el-table-column label="用户" width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="28" :style="{ background: avatarGradient(row.username || 'U') }">
              {{ (row.username || '?').charAt(0).toUpperCase() }}
            </el-avatar>
            <span>{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前铭牌" width="150" align="center">
        <template #default="{ row }">
          <UserBadge :level="row" size="medium" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center">
        <template #default="{ row }">
          <el-select
            :model-value="selectedLevels[row.userId]"
            placeholder="选择等级"
            size="small"
            style="width: 110px"
            @change="(val: any) => handleLevelChange(row.userId, val)"
          >
            <el-option v-for="l in [1, 2, 3, 4]" :key="l" :label="'L' + l" :value="l" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getAllUserLevels, setUserLevel } from '@/api/user-level'
import { ElMessage } from 'element-plus'
import { avatarGradient } from '@/utils/color'
import PageHeader from '@/components/common/PageHeader.vue'
import UserBadge from '@/components/common/UserBadge.vue'

const loading = ref(false)
const userLevels = ref<any[]>([])
const selectedLevels = reactive<Record<number, number>>({})

const levelList = [
  { level: 1, levelName: 'L1', badgeColor: '#909399', label: '新手' },
  { level: 2, levelName: 'L2', badgeColor: '#67C23A', label: '进阶', badgeIcon: 'star' },
  { level: 3, levelName: 'L3', badgeColor: '#409EFF', label: '资深', badgeIcon: 'medal' },
  { level: 4, levelName: 'L4', badgeColor: '#E6A23C', label: '大师', badgeIcon: 'crown' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getAllUserLevels()
    userLevels.value = res.data || []
    userLevels.value.forEach((u: any) => { selectedLevels[u.userId] = u.level })
  } catch { /* */ }
  finally { loading.value = false }
}

async function handleLevelChange(userId: number, newLevel: number) {
  try {
    await setUserLevel(userId, newLevel)
    ElMessage.success(`已更新为 L${newLevel}`)
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '设置失败')
  }
}

onMounted(fetchData)
</script>

<style lang="scss" scoped>
.level-manage { padding: 24px; max-width: 800px; margin: 0 auto; }
.level-legend { display: flex; gap: 16px; margin-bottom: 20px; padding: 12px 16px; background: var(--bg-card); border-radius: 8px; flex-wrap: wrap; }
.level-legend__item { display: flex; align-items: center; gap: 6px; }
.level-legend__label { font-size: 13px; color: var(--text-secondary); }
.user-cell { display: flex; align-items: center; gap: 10px; }
</style>
