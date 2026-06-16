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
    <AiChatPanel />
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
import AiChatPanel from '@/components/common/AiChatPanel.vue'
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
/* ═══════════════════════════════════════
   Header — Frosted Glass
   ═══════════════════════════════════════ */
.mimo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(24px) saturate(1.8);
  -webkit-backdrop-filter: blur(24px) saturate(1.8);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 100;
  position: sticky;
  top: 0;
  transition: background 0.3s ease;

  :deep(.dark) &,
  .dark & {
    background: rgba(15, 17, 28, 0.78);
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }

  &__left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex: 1;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    transition: opacity 0.2s ease;

    &:hover { opacity: 0.8; }
  }

  &__logo-icon {
    width: 32px;
    height: 32px;
    filter: drop-shadow(0 2px 6px rgba(79, 70, 229, 0.3));
  }

  &__logo span {
    font-size: 20px;
    font-weight: 800;
    letter-spacing: -0.8px;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &__center {
    flex: 0 0 auto;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 18px;
    flex: 1;
    justify-content: flex-end;
  }

  &__theme-toggle,
  &__bell {
    cursor: pointer;
    color: var(--text-secondary);
    transition: opacity 0.15s;
    padding: 6px;
    border-radius: 10px;

    &:hover {
      color: var(--text-primary);
      background: var(--bg-hover);
      opacity: 0.85;
    }
    &:active { transform: scale(0.95); }
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    padding: 5px 14px 5px 5px;
    border-radius: 12px;
    transition: background 0.15s;
    font-weight: 600;
    font-size: 14px;
    color: var(--text-primary);

    &:hover { background: var(--bg-hover); }
  }
}

/* Command trigger */
.cmd-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  color: var(--text-placeholder);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s;
  min-width: 220px;

  &:hover { border-color: var(--text-muted); }

  kbd {
    margin-left: auto;
    background: var(--bg-page);
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-mono);
    border: 1px solid var(--border-color);
  }
}

/* ═══════════════════════════════════════
   Sidebar — Premium Dark
   ═══════════════════════════════════════ */
.mimo-sidebar {
  background: #0f1117;
  border-right: 1px solid rgba(255, 255, 255, 0.04);
  transition: width 0.25s ease;
  overflow: hidden;

  &__wrapper {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  &__menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 12px 8px;
    border-right: none !important;
    background: transparent !important;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      color: rgba(255, 255, 255, 0.55);
      border-radius: 12px;
      margin-bottom: 4px;
      height: 44px;
      line-height: 44px;
      font-weight: 550;
      font-size: 14px;
      letter-spacing: -0.1px;
      transition: background 0.15s;

      .el-icon {
        font-size: 18px;
        transition: transform 0.2s ease;
      }

      &:hover {
        background: rgba(255, 255, 255, 0.06);
        color: rgba(255, 255, 255, 0.85);

        .el-icon { transform: scale(1.1); }
      }
    }

    :deep(.el-menu-item.is-active) {
      background: rgba(255, 255, 255, 0.1) !important;
      color: #fff !important;
      font-weight: 700;

      .el-icon { color: #fff; }
    }

    :deep(.el-sub-menu .el-menu-item) {
      padding-left: 52px !important;
      height: 40px;
      line-height: 40px;
      font-size: 13px;
    }
  }

  &__toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
    color: rgba(255, 255, 255, 0.3);
    cursor: pointer;
    transition: all 0.25s ease;
    border-top: 1px solid rgba(255, 255, 255, 0.04);

    &:hover {
      color: rgba(255, 255, 255, 0.7);
      background: rgba(255, 255, 255, 0.04);
    }
  }
}

/* ═══════════════════════════════════════
   Content
   ═══════════════════════════════════════ */
.mimo-content {
  background: var(--bg-page);
  padding: 28px 32px;
  min-height: calc(100vh - 56px);
}

/* ═══════════════════════════════════════
   Notification Dropdown
   ═══════════════════════════════════════ */
.notification-dropdown {
  position: absolute;
  top: 52px;
  right: 60px;
  width: 380px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  z-index: 200;
  overflow: hidden;
  animation: notif-enter 0.25s ease;

  @keyframes notif-enter {
    from { opacity: 0; transform: translateY(-6px); }
    to { opacity: 1; transform: translateY(0); }
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px 12px;
    font-weight: 700;
    font-size: 15px;
    border-bottom: 1px solid var(--border-color-light);
    letter-spacing: -0.2px;
  }

  &__list {
    max-height: 360px;
    overflow-y: auto;
  }

  &__empty {
    padding: 28px 16px;
    text-align: center;
    color: var(--text-secondary);
    font-size: 14px;
  }
}

.notif-item {
  display: flex;
  padding: 14px 20px;
  cursor: pointer;
  transition: background 0.2s ease;
  border-bottom: 1px solid var(--border-color-light);
  gap: 12px;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--bg-hover); }

  &--unread {
    background: var(--bg-subtle);
  }

  &__dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--color-primary);
    margin-top: 7px;
    flex-shrink: 0;
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: -0.1px;
  }

  &__text {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 3px;
    line-height: 1.5;
  }

  &__time {
    font-size: 11px;
    color: var(--text-placeholder);
    margin-top: 5px;
  }
}

/* Project switcher */
.project-switcher {
  :deep(.el-select) {
    .el-input__wrapper {
      border-radius: 10px;
      box-shadow: 0 0 0 1px var(--border-color);
      transition: border-color 0.15s;

      &:hover {
        border-color: var(--text-muted);
      }
    }
  }
}
</style>
