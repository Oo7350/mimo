<template>
  <div class="level-manage">
    <div class="level-manage__header">
      <h2>用户等级管理</h2>
      <p class="level-manage__desc">管理员可在此设置用户等级 (L1-L4)</p>
    </div>

    <!-- 等级说明 -->
    <div class="level-legend">
      <div v-for="item in levelList" :key="item.level" class="level-legend__item">
        <span class="level-badge" :style="{ background: item.color }">
          {{ item.name }}
        </span>
        <span class="level-legend__label">{{ item.label }}</span>
      </div>
    </div>

    <!-- 用户列表 -->
    <el-table :data="userLevels" v-loading="loading" stripe>
      <el-table-column label="用户" width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <div class="user-avatar" :style="{ background: getAvatarColor(row.userId) }">
              {{ row.username?.charAt(0)?.toUpperCase() }}
            </div>
            <span>{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="当前等级" width="150" align="center">
        <template #default="{ row }">
          <span
            class="level-badge"
            :class="'level-badge--' + row.levelName.toLowerCase()"
            :style="{ background: row.badgeColor, color: '#fff' }"
          >
            <template v-if="row.badgeIcon === 'crown'">👑</template>
            <template v-else-if="row.badgeIcon === 'medal'">🏅</template>
            <template v-else-if="row.badgeIcon === 'star'">⭐</template>
            {{ row.levelName }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" align="center">
        <template #default="{ row }">
          <el-select
            v-model="selectedLevels[row.userId]"
            placeholder="选择等级"
            size="small"
            style="width: 120px"
            @change="(val) => handleLevelChange(row.userId, val)"
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
import { getAllUserLevels, setUserLevel } from '@/api/approval'
import { ElMessage } from 'element-plus'
import { avatarGradient } from '@/utils/color'

const loading = ref(false)
const userLevels = ref<any[]>([])
const selectedLevels = reactive<Record<number, number>>({})

const levelList = [
  { level: 1, name: 'L1', color: '#909399', label: '初级成员' },
  { level: 2, name: 'L2', color: '#67C23A', label: '正式成员' },
  { level: 3, name: 'L3', color: '#409EFF', label: '资深成员' },
  { level: 4, name: 'L4', color: '#E6A23C', label: '核心成员' },
]

function getAvatarColor(userId: number) {
  return avatarGradient(String(userId))
}

async function fetchUserLevels() {
  loading.value = true
  try {
    const res = await getAllUserLevels()
    userLevels.value = res.data || []
    userLevels.value.forEach((u: any) => {
      selectedLevels[u.userId] = u.level
    })
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleLevelChange(userId: number, newLevel: number) {
  try {
    await setUserLevel(userId, newLevel)
    ElMessage.success('等级已更新')
    fetchUserLevels()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '设置失败')
  }
}

onMounted(() => {
  fetchUserLevels()
})
</script>

<style lang="scss" scoped>
.level-manage {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;

  &__header {
    margin-bottom: 24px;

    h2 {
      margin: 0 0 8px;
      font-size: 20px;
    }

    &__desc {
      color: var(--text-secondary);
      font-size: 14px;
      margin: 0;
    }
  }
}

.level-legend {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: 8px;

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
  }
}

.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;

  &--l1 {
    opacity: 0.8;
  }

  &--l2 {
    box-shadow: 0 0 8px rgba(103, 194, 58, 0.3);
  }

  &--l3 {
    box-shadow: 0 0 8px rgba(64, 158, 255, 0.3);
  }

  &--l4 {
    background: linear-gradient(135deg, #E6A23C, #F56C6C) !important;
    animation: shimmer 2s infinite;
    border: 1px solid rgba(255, 215, 0, 0.5);
  }
}

@keyframes shimmer {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.15); }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
</style>
