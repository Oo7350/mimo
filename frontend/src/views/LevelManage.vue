<template>
  <div class="level-manage">
    <PageHeader title="用户等级管理" subtitle="配置 L1-L4 铭牌权限体系" />

    <!-- 等级权限卡片 -->
    <div class="level-cards">
      <div v-for="item in levelList" :key="item.level" class="level-card" :class="'level-card--l' + item.level">
        <div class="level-card__header">
          <UserBadge :level="item" size="large" />
          <div class="level-card__title-group">
            <h3 class="level-card__name">{{ item.label }}</h3>
            <span class="level-card__code">{{ item.levelName }}</span>
          </div>
        </div>
        <p class="level-card__desc">{{ item.description }}</p>
        <ul class="level-card__perms">
          <li v-for="(perm, pi) in item.permissions" :key="pi" class="level-card__perm">
            <el-icon><component :is="perm.icon || 'CircleCheck'" /></el-icon>
            {{ perm.text }}
            <span v-if="perm.tag" class="level-card__perm-tag" :style="{ background: perm.tagColor }">{{ perm.tag }}</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 用户等级分配 -->
    <div class="user-section">
      <div class="user-section__header">
        <h3>成员等级分配</h3>
        <span class="user-section__count">{{ userLevels.length }} 位成员</span>
      </div>

      <div class="user-table-wrap">
        <el-table :data="userLevels" v-loading="loading" stripe class="level-table">
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <div class="user-cell__avatar" :style="{ background: avatarGradient(row.username || 'U', 'user') }">
                  {{ (row.username || '?').charAt(0).toUpperCase() }}
                </div>
                <div class="user-cell__info">
                  <span class="user-cell__name">{{ row.username }}</span>
                  <span class="user-cell__email">{{ row.email || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="当前铭牌" width="160" align="center">
            <template #default="{ row }">
              <UserBadge :level="row" size="medium" />
            </template>
          </el-table-column>
          <el-table-column label="调整等级" width="180" align="center">
            <template #default="{ row }">
              <el-select
                :model-value="selectedLevels[row.userId]"
                placeholder="选择等级"
                size="default"
                @change="(val: any) => handleLevelChange(row.userId, val)"
              >
                <el-option v-for="l in levelList" :key="l.level" :label="l.label + ' (' + l.levelName + ')'" :value="l.level">
                  <div style="display:flex;align-items:center;gap:8px">
                    <UserBadge :level="l" size="small" />
                    <span>{{ l.label }}</span>
                  </div>
                </el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="当前权限摘要" min-width="260">
            <template #default="{ row }">
              <div class="perm-summary">
                <span
                  v-for="(p, i) in getLevelPerms(row.level)"
                  :key="i"
                  class="perm-chip"
                >{{ p }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getAllUserLevels, setUserLevel } from '@/api/user-level'
import { ElMessage } from 'element-plus'
import {
  CircleCheck, View, EditPen, Delete,
  Plus, Setting, Key, UserFilled, DataAnalysis,
  Stamp, Bell, Connection
} from '@element-plus/icons-vue'
import { avatarGradient } from '@/utils/color'
import PageHeader from '@/components/common/PageHeader.vue'
import UserBadge from '@/components/common/UserBadge.vue'

const loading = ref(false)
const userLevels = ref<any[]>([])
const selectedLevels = reactive<Record<number, number>>({})

const levelList = [
  {
    level: 1,
    levelName: 'L1',
    badgeColor: '#909399',
    label: '新手',
    description: '刚加入团队的新成员，拥有基础浏览和参与权限。',
    permissions: [
      { text: '查看项目与看板', icon: 'View' },
      { text: '查看 Issue 详情', icon: 'View' },
      { text: '发表评论', icon: 'EditPen', tag: '仅自己创建', tagColor: 'rgba(144,147,153,0.15)' },
      { text: '被指派任务时收到通知', icon: 'Bell' },
    ],
  },
  {
    level: 2,
    levelName: 'L2',
    badgeColor: '#67C23A',
    label: '进阶',
    description: '已熟悉工作流程，可独立完成日常任务操作。',
    badgeIcon: 'star',
    permissions: [
      { text: '创建 / 编辑自己的 Issue', icon: 'EditPen' },
      { text: '移动看板卡片（状态变更）', icon: 'Connection' },
      { text: '上传附件（单文件 ≤ 5MB）', icon: 'Plus' },
      { text: '查看 Sprint 报告', icon: 'DataAnalysis' },
      { text: '邀请成员加入项目', icon: 'UserFilled', tag: '需审批', tagColor: 'rgba(103,194,58,0.15)' },
    ],
  },
  {
    level: 3,
    levelName: 'L3',
    badgeColor: '#409EFF',
    label: '资深',
    description: '核心团队成员，可协助管理 Sprint 和任务分配。',
    badgeIcon: 'medal',
    permissions: [
      { text: '编辑任意 Issue', icon: 'EditPen' },
      { text: '创建 / 编辑 / 删除 Sprint', icon: 'Setting' },
      { text: '指派 Issue 给他人', icon: 'UserFilled' },
      { text: '管理项目标签与优先级', icon: 'Stamp' },
      { text: '审批 L2 成员的邀请请求', icon: 'Key', tag: '代理审批', tagColor: 'rgba(64,158,255,0.15)' },
      { text: '导出项目数据报表', icon: 'DataAnalysis' },
    ],
  },
  {
    level: 4,
    levelName: 'L4',
    badgeColor: '#E6A23C',
    label: '大师',
    description: '团队领袖级用户，拥有项目管理层面的完整控制权。',
    badgeIcon: 'crown',
    permissions: [
      { text: '全部 L1-L3 权限', icon: 'CircleCheck' },
      { text: '删除 Issue 与 Sprint', icon: 'Delete' },
      { text: '设置项目归档与关闭', icon: 'Setting' },
      { text: '管理所有成员的等级', icon: 'Key' },
      { text: '审批所有待处理请求', icon: 'Stamp' },
      { text: '修改项目模板与工作流', icon: 'Connection', tag: '高风险', tagColor: 'rgba(230,162,60,0.2)' },
    ],
  },
]

function getLevelPerms(level: number) {
  const item = levelList.find(l => l.level === level)
  return item ? item.permissions.map(p => p.text).slice(0, 4) : []
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getAllUserLevels()
    userLevels.value = res.data || []
  } catch { /* */ }

  // Fallback: if API returns no data, use mock for demo
  if (!userLevels.value.length) {
    userLevels.value = [
      { userId: 1, username: 'admin', email: 'admin@mimo.com', level: 4, levelName: 'L4', badgeColor: '#E6A23C', badgeIcon: 'crown' },
      { userId: 2, username: 'zhangsan', email: 'zhangsan@mimo.com', level: 3, levelName: 'L3', badgeColor: '#409EFF', badgeIcon: 'medal' },
      { userId: 3, username: 'lisi', email: 'lisi@mimo.com', level: 2, levelName: 'L2', badgeColor: '#67C23A', badgeIcon: 'star' },
      { userId: 4, username: 'wangwu', email: 'wangwu@mimo.com', level: 1, levelName: 'L1', badgeColor: '#909399' },
      { userId: 5, username: 'zhaoliu', email: 'zhaoliu@mimo.com', level: 2, levelName: 'L2', badgeColor: '#67C23A', badgeIcon: 'star' },
      { userId: 6, username: 'sunqi', email: 'sunqi@mimo.com', level: 3, levelName: 'L3', badgeColor: '#409EFF', badgeIcon: 'medal' },
    ]
  }
  userLevels.value.forEach((u: any) => { selectedLevels[u.userId] = u.level })
  loading.value = false
}

async function handleLevelChange(userId: number, newLevel: number) {
  try {
    await setUserLevel(userId, newLevel)
    const label = levelList.find(l => l.level === newLevel)?.label || `L${newLevel}`
    ElMessage.success(`已更新为 ${label}`)
    await fetchData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '设置失败')
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.level-manage {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

/* ===== 等级权限卡片 ===== */
.level-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.level-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 22px 18px;
  transition: border-color 0.15s;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 3px;
    opacity: 0;
    transition: opacity 0.25s ease;
  }

  &:hover {
    border-color: rgba(0,0,0,0.12);

    &::before { opacity: 1; }
  }

  &--l1::before { background: linear-gradient(90deg, #909399, #b1b3b8); }
  &--l2::before { background: linear-gradient(90deg, #67C23A, #85ce61); }
  &--l3::before { background: linear-gradient(90deg, #409EFF, #66b1ff); }
  &--l4::before {
    background: linear-gradient(135deg, #E6A23C, #F56C6C);
    &::after {
      content: '';
      position: absolute;
      top: -30px; right: -30px;
      width: 100px; height: 100px;
      background: radial-gradient(circle, rgba(230,162,60,0.12), transparent 70%);
      pointer-events: none;
    }
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }

  &__title-group {
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 17px;
    font-weight: 800;
    letter-spacing: -0.3px;
    margin: 0;
    line-height: 1.2;
  }

  &__code {
    font-size: 11px;
    font-weight: 700;
    color: var(--text-muted);
    letter-spacing: 0.5px;
  }

  &__desc {
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.55;
    margin: 0 0 14px;
  }

  &__perms {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 7px;
  }

  &__perm {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 12.5px;
    font-weight: 550;
    color: var(--text-primary);
    line-height: 1.35;

    .el-icon {
      font-size: 14px;
      flex-shrink: 0;
      color: var(--color-primary);
      opacity: 0.75;
    }
  }

  &__perm-tag {
    font-size: 10px;
    font-weight: 700;
    padding: 1px 6px;
    border-radius: 5px;
    margin-left: auto;
    white-space: nowrap;
  }
/* ===== 用户区域 ===== */
.user-section {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  overflow: hidden;
}
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 24px;
    border-bottom: 1px solid var(--border-color-light);

    h3 {
      font-size: 16px;
      font-weight: 750;
      margin: 0;
      letter-spacing: -0.2px;
    }
  }

  &__count {
    font-size: 13px;
    color: var(--text-muted);
    font-weight: 600;
  }
}

.user-table-wrap {
  padding: 0 24px 20px;
}

/* 用户单元格 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;

  &__avatar {
    width: 36px; height: 36px;
    border-radius: 10px;
    display: flex; align-items: center; justify-content: center;
    font-size: 14px;
    font-weight: 700;
    color: #fff;
    flex-shrink: 0;
    background: #f7b955;
  }

  &__info {
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 14px;
    font-weight: 650;
  }

  &__email {
    font-size: 11.5px;
    color: var(--text-muted);
  }
}

/* 权限摘要 */
.perm-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.perm-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  white-space: nowrap;
}

/* 表格样式覆盖 */
:deep(.level-table) {
  --el-table-border-color: var(--border-color-light);
  --el-table-header-bg-color: transparent;
  --el-table-row-hover-bg-color: var(--bg-subtle);

  .el-table__header th {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-secondary);
    letter-spacing: 0.2px;
  }

  .el-table__body tr {
    transition: background 0.2s ease;
  }

  .el-select {
    width: 100%;
  }
}

/* 响应式：小屏幕时卡片改为 2 列 */
@media (max-width: 900px) {
  .level-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 600px) {
  .level-cards {
    grid-template-columns: 1fr;
  }
}
</style>
