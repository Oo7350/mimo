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
import { avatarGradient } from "@/utils/color"
import { ElMessage, ElMessageBox } from "element-plus"
import PageHeader from "@/components/common/PageHeader.vue"
import { Delete, Grid, DataAnalysis, Document } from "@element-plus/icons-vue"

const projects = ref<any[]>([])

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
    await deleteProject(p.id)
    ElMessage.success('项目已删除')
    await fetchProjects()
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
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
  }

  &__key {
    font-family: monospace;
    font-weight: 600;
    color: var(--color-primary);
  }
}
</style>
