<template>
  <el-container class="mimo-layout">
    <!-- 顶栏 -->
    <el-header class="mimo-header" height="56px">
      <div class="mimo-header__left">
        <div class="mimo-header__logo" @click="$router.push('/')">
          <svg class="mimo-header__logo-icon" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="rgba(255,255,255,0.2)" />
            <rect x="7" y="8" width="5" height="16" rx="2" fill="white" opacity="0.95" />
            <rect x="14" y="8" width="5" height="11" rx="2" fill="white" opacity="0.75" />
            <rect x="21" y="8" width="5" height="14" rx="2" fill="white" opacity="0.55" />
          </svg>
          <span>Mimo</span>
        </div>
        <BreadcrumbNav />
        <!-- 项目切换下拉（仅在看板/Sprint/报告页面显示） -->
        <div v-if="isProjectPage" class="project-switcher" style="margin-left: 12px">
          <el-select
            :model-value="currentProjectId"
            placeholder="切换项目"
            size="small"
            style="width: 200px"
            @change="switchProject"
          >
            <el-option
              v-for="p in myProjects"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </div>
      </div>
      <div class="mimo-header__center">
        <button class="cmd-trigger" @click="openCommandPalette">
          <el-icon><Search /></el-icon>
          <span>搜索...</span>
          <kbd>Ctrl K</kbd>
        </button>
      </div>
      <div class="mimo-header__right">
        <el-tooltip :content="appStore.darkMode ? '切换浅色模式' : '切换深色模式'" placement="bottom">
          <el-icon :size="20" class="mimo-header__theme-toggle" @click="appStore.toggleDarkMode()">
            <Sunny v-if="appStore.darkMode" />
            <Moon v-else />
          </el-icon>
        </el-tooltip>
        <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
          <el-icon :size="20" class="mimo-header__bell" @click="toggleNotifications">
            <Bell />
          </el-icon>
        </el-badge>
        <div v-if="showNotifications" class="notification-dropdown" @click.stop>
          <div class="notification-dropdown__header">
            <span>通知</span>
            <el-button v-if="unreadCount > 0" text size="small" @click="handleMarkAllRead">
              全部已读
            </el-button>
          </div>
          <div v-if="notifLoading" class="notification-dropdown__empty">
            加载中...
          </div>
          <div v-else-if="!notifications.length" class="notification-dropdown__empty">
            <el-empty description="暂无通知" :image-size="60" />
          </div>
          <div v-else class="notification-dropdown__list">
            <div
              v-for="n in notifications"
              :key="n.id"
              :class="['notif-item', { 'notif-item--unread': !n.isRead }]"
              @click="handleNotifClick(n)"
            >
              <div class="notif-item__dot" v-if="!n.isRead" />
              <div class="notif-item__content">
                <div class="notif-item__title">{{ n.title }}</div>
                <div class="notif-item__text">{{ n.content }}</div>
                <div class="notif-item__time">{{ formatRelativeTime(n.createdAt) }}</div>
              </div>
            </div>
          </div>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="mimo-header__user">
            <el-avatar :size="30" icon="UserFilled" />
            <span>{{ userStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item v-if="!tourDone" command="tour">功能引导</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <!-- 侧边栏（深色主题） -->
      <el-aside class="mimo-sidebar" :style="{ width: sidebarWidth }">
        <div class="mimo-sidebar__wrapper">
          <el-menu
            :default-active="activeMenu"
            :collapse="isCollapsed"
            router
            class="mimo-sidebar__menu"
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
            <template v-if="userStore.userInfo?.role === 'ROLE_ADMIN'">
              <el-menu-item index="/admin/levels">
                <el-icon><Medal /></el-icon>
                <span>等级管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/approvals">
                <el-icon><Checked /></el-icon>
                <span>审批管理</span>
              </el-menu-item>
            </template>
          </el-menu>

          <div class="mimo-sidebar__toggle" @click="appStore.toggleSidebar()">
            <el-icon :size="18">
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
        </div>
      </el-aside>

      <el-main class="mimo-content">
        <router-view />
      </el-main>
    </el-container>

    <CommandPalette />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { ElMessageBox, ElNotification } from 'element-plus'
import { getMyProjects } from '@/api/project'
import { getIssueById } from '@/api/issue'
import { getNotifications } from '@/api/notification'
import type { ProjectVO } from '@/types'
import BreadcrumbNav from './BreadcrumbNav.vue'
import CommandPalette from '@/components/common/CommandPalette.vue'
import { useTour } from '@/composables/useTour'
import { useNotifications } from '@/composables/useNotifications'
import { useWebSocket } from '@/composables/useWebSocket'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { Moon, Sunny, Medal, Checked } from '@element-plus/icons-vue'
import { formatRelativeTime } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { startTour, isTourCompleted } = useTour()
const { unreadCount, notifications, loading: notifLoading, fetchNotifications, handleMarkRead, handleMarkAllRead } = useNotifications()
const { connect: wsConnect, disconnect: wsDisconnect } = useWebSocket()
const { open: openCommandPalette } = useCommandPalette()

const isCollapsed = computed(() => appStore.sidebarCollapsed)
const sidebarWidth = computed(() => isCollapsed.value ? '64px' : '230px')
const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/projects/')) return '/projects'
  if (p.startsWith('/teams')) return '/teams'
  if (p === '/profile') return '/profile'
  return '/'
})

const showNotifications = ref(false)

async function toggleNotifications() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) await fetchNotifications()
}

async function handleNotifClick(n: any) {
  if (!n.isRead) await handleMarkRead(n.id)
  showNotifications.value = false
  if (n.relatedId && n.relatedType === 'ISSUE') {
    try {
      const res = await getIssueById(n.relatedId)
      const projectId = res.data?.projectId
      if (projectId) {
        router.push(`/projects/${projectId}/board?issue=${n.relatedId}`)
        return
      }
    } catch { /* fallback */ }
  }
  router.push('/')
}
const myProjects = ref<ProjectVO[]>([])
const tourDone = ref(localStorage.getItem('mimo_tour_completed') === 'true')

const isProjectPage = computed(() => route.path.includes('/projects/'))
const currentProjectId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})

function switchProject(projectId: number) {
  const proj = myProjects.value.find(p => p.id === projectId)
  if (proj) {
    appStore.setCurrentProject(proj.id, proj.name)
    router.push(`/projects/${projectId}/board`)
  }
}

function handleCommand(command: string) {
  if (command === 'profile') router.push('/profile')
  else if (command === 'tour') { showNotifications.value = false; startTour() }
  else if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(() => { wsDisconnect(); userStore.logout() })
  }
}

async function fetchMyProjects() {
  try {
    const res = await getMyProjects()
    myProjects.value = res.data || []
  } catch { myProjects.value = [] }
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.mimo-header__bell') && !target.closest('.notification-dropdown')) {
    showNotifications.value = false
  }
}

function checkTourTrigger() {
  if (!isTourCompleted() && route.path.includes('/projects/') && route.path.includes('/board')) {
    setTimeout(() => startTour(), 600)
  }
}

onMounted(() => {
  fetchMyProjects()
  document.addEventListener('click', handleClickOutside)
  checkTourTrigger()
  // Connect WebSocket
  wsConnect()
  // 检查审批结果通知
  checkApprovalNotifications()
})

/** 检查是否有未读的审批通知，有则弹窗提示 */
async function checkApprovalNotifications() {
  try {
    const res = await getNotifications(10)
    const list = res.data || []
    const approvalNotifs = list.filter((n: any) =>
      !n.isRead && n.type?.startsWith('APPROVAL_')
    )
    if (approvalNotifs.length > 0) {
      approvalNotifs.forEach((n: any, idx: number) => {
        setTimeout(() => {
          ElNotification({
            title: n.title,
            message: n.content,
            type: n.type === 'APPROVAL_APPROVED' ? 'success' : 'warning',
            duration: 6000,
            position: 'top-right',
          })
        }, idx * 500) // 错开显示，避免重叠
      })
    }
  } catch { /* ignore */ }
}

watch(() => route.path, () => checkTourTrigger())
</script>

<style scoped lang="scss">
.mimo-sidebar {
  &__wrapper {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  &__menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 8px 0;
  }
}

.notification-dropdown {
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px 8px;
    font-weight: 600;
    font-size: 14px;
    border-bottom: 1px solid var(--border-color-light);
  }

  &__list {
    max-height: 360px;
    overflow-y: auto;
  }

  &__empty {
    padding: 24px 16px;
    text-align: center;
    color: var(--text-secondary);
    font-size: 14px;
  }
}

.notif-item {
  display: flex;
  padding: 12px 16px;
  cursor: pointer;
  transition: background var(--transition-fast);
  border-bottom: 1px solid var(--border-color-light);
  gap: 10px;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--bg-hover); }

  &--unread {
    background: rgba(79, 110, 246, 0.04);
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-primary);
    margin-top: 6px;
    flex-shrink: 0;
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__text {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
  }

  &__time {
    font-size: 11px;
    color: var(--text-placeholder);
    margin-top: 4px;
  }
}
</style>
