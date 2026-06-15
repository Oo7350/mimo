<template>
  <div class="project-list">
    <PageHeader title="项目列表" subtitle="你参与的所有项目，快速进入看板或报告">
      <template #action>
        <el-button type="primary" @click="$router.push('/teams')">
          <el-icon><Plus /></el-icon> 创建项目
        </el-button>
      </template>
    </PageHeader>

    <div v-if="projects.length" class="project-list__grid">
      <div
        v-for="p in projects"
        :key="p.id"
        class="list-card"
        @click="$router.push(`/projects/${p.id}/board`)"
      >
        <div class="list-card__header">
          <div class="list-card__avatar" :style="{ background: avatarGradient(p.name) }">
            {{ p.name.charAt(0).toUpperCase() }}
          </div>
          <div class="list-card__title">{{ p.name }}</div>
          <el-tag :type="p.template === 'SCRUM' ? '' : 'warning'" size="small" effect="plain">
            {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}
          </el-tag>
        </div>
        <p class="list-card__desc">
          <span class="project-list__key">{{ p.key }}</span>
          · {{ p.teamName }}
        </p>
        <div class="list-card__actions">
          <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/board`)">
            <el-icon><Grid /></el-icon> 看板
          </el-button>
          <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/sprints`)">
            <el-icon><DataAnalysis /></el-icon> Sprint
          </el-button>
          <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/reports`)">
            <el-icon><Document /></el-icon> 报告
          </el-button>
          <el-button
            type="danger"
            size="small"
            text
            :icon="Delete"
            @click.stop="handleDelete(p)"
          >删除</el-button>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无参与的项目">
      <el-button type="primary" @click="$router.push('/teams')">前往团队创建</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { getMyProjects, deleteProject } from "@/api/project"
import { createApproval } from "@/api/approval"
import { avatarGradient } from "@/utils/color"
import { ElMessage, ElMessageBox } from "element-plus"
import PageHeader from "@/components/common/PageHeader.vue"
import { Delete, Grid, DataAnalysis, Document } from "@element-plus/icons-vue"
import { useUserStore } from "@/store/user"

const userStore = useUserStore()
const projects = ref<any[]>([])

// 当前用户是否为系统管理员
const isAdmin = () => userStore.userInfo?.role === 'ROLE_ADMIN'

async function fetchProjects() {
  const res = await getMyProjects()
  projects.value = res.data || []
}

async function handleDelete(p: any) {
  try {
    await ElMessageBox.confirm(`确定删除项目「${p.name}」？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // 非管理员需要走审批流程
    if (!isAdmin()) {
      try {
        await createApproval({
          teamId: p.teamId || 0,
          projectId: p.id,
          targetType: 'PROJECT_DELETE',
          title: `删除项目: ${p.name}`,
          description: `用户 ${userStore.username} 申请删除项目`,
          dataJson: JSON.stringify({ projectId: p.id }),
        })
        ElMessage.success('审批请求已提交，等待管理员审核')
      } catch (e) {
        console.error('创建审批请求失败:', e)
        ElMessage.error('提交审批请求失败，请稍后重试')
      }
      return
    }

    // 管理员直接删除（后端返回403/500时自动降级为审批）
    try {
      await deleteProject(p.id)
      ElMessage.success('项目已删除')
      await fetchProjects()
    } catch (e: any) {
      const status = e?.response?.status
      if (status === 403 || status >= 500) {
        await createApproval({
          teamId: p.teamId || 0,
          projectId: p.id,
          targetType: 'PROJECT_DELETE',
          title: `删除项目: ${p.name}`,
          description: `用户 ${userStore.username} 申请删除项目`,
          dataJson: JSON.stringify({ projectId: p.id }),
        })
        ElMessage.success('已转为审批请求，等待管理员审核')
        return
      }
      throw e
    }
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

onMounted(fetchProjects)
</script>

<style scoped lang="scss">
.project-list {
  max-width: 1100px;
  margin: 0 auto;

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
    gap: 20px;
  }

  &__key {
    font-family: var(--font-mono);
    font-weight: 700;
    color: var(--color-primary);
    letter-spacing: -0.3px;
  }
}

.list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 24px;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  overflow: hidden;

  // Top accent line
  &::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 3px;
    background: linear-gradient(90deg, #4f46e5, #7c3aed);
    opacity: 0;
    transition: opacity 0.35s ease;
  }

  &:hover {
    transform: translateY(-6px) scale(1.01);
    box-shadow: var(--shadow-card-hover);
    border-color: rgba(79, 70, 229, 0.18);

    &::before { opacity: 1; }

    .list-card__avatar { transform: scale(1.08) rotate(-3deg); }
  }

  &:active { transform: translateY(-2px) scale(0.99); }

  &__header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 12px;
  }

  &__avatar {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 800;
    font-size: 20px;
    flex-shrink: 0;
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
    transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &__title {
    font-size: 17px;
    font-weight: 750;
    color: var(--text-primary);
    letter-spacing: -0.3px;
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin-bottom: 16px;
    line-height: 1.55;
  }

  &__actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;

    .el-button {
      border-radius: 9px;
      font-weight: 600;
      font-size: 12.5px;
      transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

      &:hover {
        transform: translateY(-2px) scale(1.03);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }
    }
  }
}
</style>
