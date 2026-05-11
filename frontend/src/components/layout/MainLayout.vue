<template>
  <el-container class="mimo-layout">
    <!-- 顶栏 -->
    <el-header class="mimo-header" height="56px">
      <div class="logo">
        <el-icon :size="24"><Monitor /></el-icon>
        <span>Mimo</span>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click" @command="handleCommand">
          <span style="cursor: pointer; display: flex; align-items: center; gap: 6px">
            <el-avatar :size="32" icon="UserFilled" />
            <span>{{ userStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <!-- 侧边栏 -->
      <el-aside class="mimo-sidebar" :width="isCollapsed ? '64px' : '220px'">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          router
          background-color="#fff"
          text-color="#303133"
          active-text-color="#409EFF"
          style="border-right: none"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-sub-menu index="projects">
            <template #title>
              <el-icon><Folder /></el-icon>
              <span>项目管理</span>
            </template>
            <el-menu-item index="/projects">
              <el-icon><List /></el-icon>
              <span>项目列表</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/teams">
            <el-icon><UserFilled /></el-icon>
            <span>团队管理</span>
          </el-menu-item>
          <el-menu-item index="/profile">
            <el-icon><Setting /></el-icon>
            <span>个人信息</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容 -->
      <el-main class="mimo-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { useAppStore } from "@/store/app";
import { ElMessageBox } from "element-plus";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const appStore = useAppStore();

const isCollapsed = computed(() => appStore.sidebarCollapsed);
const activeMenu = computed(() => route.path);

function handleCommand(command: string) {
  if (command === "profile") {
    router.push("/profile");
  } else if (command === "logout") {
    ElMessageBox.confirm("确定退出登录吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }).then(() => {
      userStore.logout();
    });
  }
}
</script>
