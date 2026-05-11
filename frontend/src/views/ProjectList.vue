<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>项目列表</h2>
      <el-button type="primary" @click="$router.push('/teams')">创建项目</el-button>
    </div>

    <el-row :gutter="20">
      <el-col v-for="p in projects" :key="p.id" :span="8">
        <el-card shadow="hover" style="margin-bottom: 20px; cursor: pointer"
                 @click="$router.push(`/projects/${p.id}/board`)">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: bold">{{ p.name }}</span>
              <el-tag :type="p.template === 'SCRUM' ? '' : 'warning'" size="small">
                {{ p.template === 'SCRUM' ? 'Scrum' : 'Kanban' }}
              </el-tag>
            </div>
          </template>
          <p style="color: #909399; font-size: 13px; margin-bottom: 8px">{{ p.key }}</p>
          <p style="color: #c0c4cc; font-size: 12px">{{ p.teamName }}</p>
          <div style="margin-top: 12px; display: flex; gap: 8px">
            <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/board`)">
              <el-icon><Grid /></el-icon> 看板
            </el-button>
            <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/sprints`)">
              <el-icon><DataAnalysis /></el-icon> Sprint
            </el-button>
            <el-button size="small" @click.stop="$router.push(`/projects/${p.id}/reports`)">
              <el-icon><Document /></el-icon> 报告
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="projects.length === 0" description="暂无参与的项目">
      <el-button type="primary" @click="$router.push('/teams')">前往团队创建</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { getMyProjects } from "@/api/project";

const projects = ref<any[]>([]);

async function fetchProjects() {
  const res = await getMyProjects();
  projects.value = res.data || [];
}

onMounted(fetchProjects);
</script>
