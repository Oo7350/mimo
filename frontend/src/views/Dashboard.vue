<template>
  <div>
    <div style="margin-bottom: 16px">
      <h2>工作台</h2>
      <p style="color: #909399">欢迎回来，{{ userStore.username }}</p>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ dashboard?.totalIssues || 0 }}</div>
            <div class="stat-label">总任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value" style="color: #409EFF">{{ dashboard?.inProgressIssues || 0 }}</div>
            <div class="stat-label">进行中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value" style="color: #67C23A">{{ dashboard?.doneIssues || 0 }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 我的项目 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>我的项目</span>
              <el-button size="small" text @click="$router.push('/projects')">查看全部</el-button>
            </div>
          </template>
          <div v-if="!dashboard?.myProjects?.length" style="color: #909399; text-align: center; padding: 20px">
            暂无项目
          </div>
          <div v-for="p in (dashboard?.myProjects || [])" :key="p.id"
               style="padding: 10px 0; border-bottom: 1px solid #ebeef5; cursor: pointer"
               @click="$router.push(`/projects/${p.id}/board`)">
            <div style="font-weight: bold">{{ p.name }}</div>
            <div style="color: #909399; font-size: 12px">{{ p.key }} | {{ p.teamName }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 活跃 Sprint -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>活跃 Sprint</span>
            </div>
          </template>
          <div v-if="!dashboard?.activeSprints?.length" style="color: #909399; text-align: center; padding: 20px">
            暂无活跃 Sprint
          </div>
          <div v-for="s in (dashboard?.activeSprints || [])" :key="s.id"
               style="padding: 10px 0; border-bottom: 1px solid #ebeef5; cursor: pointer"
               @click="$router.push(`/projects/${s.projectId}/sprints/${s.id}/burndown`)">
            <div style="font-weight: bold">{{ s.name }}</div>
            <div style="color: #909399; font-size: 12px">{{ s.projectName }}</div>
            <el-progress :percentage="s.totalIssues ? Math.round(s.completedIssues / s.totalIssues * 100) : 0"
                         :stroke-width="6" style="margin-top: 6px" />
            <div style="font-size: 11px; color: #c0c4cc">{{ s.completedIssues }} / {{ s.totalIssues }} 个任务</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 近期动态 -->
    <el-card shadow="hover" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>近期动态</span>
        </div>
      </template>
      <el-timeline v-if="dashboard?.recentActivities?.length">
        <el-timeline-item
          v-for="act in dashboard.recentActivities"
          :key="act.id"
          :timestamp="act.createdAt"
          placement="top"
        >
          <strong>{{ act.username }}</strong> {{ act.detail }}
        </el-timeline-item>
      </el-timeline>
      <div v-else style="color: #909399; text-align: center; padding: 20px">暂无动态</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useUserStore } from "@/store/user";
import request from "@/api/request";

const userStore = useUserStore();
const dashboard = ref<any>(null);

async function fetchDashboard() {
  const res = await request.get("/dashboard");
  dashboard.value = res.data;
}

onMounted(fetchDashboard);
</script>

<style scoped>
.stat-card { text-align: center; padding: 12px 0; }
.stat-value { font-size: 32px; font-weight: bold; }
.stat-label { color: #909399; margin-top: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
