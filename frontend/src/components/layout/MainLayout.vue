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
            <el-avatar :size="30" icon="UserFilled" :class="{ 'is-admin': isAdminAccount }" />
            <span class="mimo-header__username">{{ userStore.username }}</span>
            <!-- 管理员在顶栏就有身份标记：不然"我能看全员任务流"这件事只有自己知道 -->
            <AdminBadge v-if="isAdminAccount" size="small" :label="adminLabel" />
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <!-- 引导入口常驻：看过一次也能重看，且分主题，避免在错的页面讲错的东西 -->
              <el-dropdown-item command="tour:welcome" :divided="false">新手引导 · 快速上手</el-dropdown-item>
              <el-dropdown-item command="tour:board">新手引导 · 看板实操</el-dropdown-item>
              <el-dropdown-item command="tour:collab">新手引导 · 协作与治理</el-dropdown-item>
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
            <!-- 进入项目后：左侧切换成项目内导航，不再显示全局功能清单 -->
            <template v-if="currentProjectId">
              <div class="mimo-nav-back" @click="router.push('/projects')">
                <el-icon><ArrowLeft /></el-icon>
                <span v-show="!isCollapsed">全部项目</span>
              </div>
              <div v-show="!isCollapsed" class="mimo-nav-project">{{ currentProjectName }}</div>
              <el-menu-item v-for="item in projectNavItems" :key="item.path" :index="item.path">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </el-menu-item>
            </template>

            <!-- 全局：我的工作 / 项目 / 团队 / 设置 四层，管理类功能全部收进「设置」 -->
            <template v-else>
              <template v-for="group in visibleNavGroups" :key="group.key">
                <div v-show="!isCollapsed" class="mimo-nav-label">{{ group.label }}</div>
                <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </el-menu-item>
              </template>
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
        <router-view v-slot="{ Component }">
          <!-- 页面级 key：切换项目/页面时重挂载页面组件，但保留外层布局与连接 -->
          <component :is="Component" :key="route.path" />
        </router-view>
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
import { usePermissionStore } from '@/store/permission'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getMyProjects } from '@/api/project'
import { getNotifications } from '@/api/notification'
import type { ProjectVO } from '@/types'
import BreadcrumbNav from './BreadcrumbNav.vue'
import AdminBadge from '@/components/common/AdminBadge.vue'
import CommandPalette from '@/components/common/CommandPalette.vue'
import AiChatPanel from '@/components/common/AiChatPanel.vue'
import { useTour, abortTourOnNavigate, type TourTopic } from '@/composables/useTour'
import { useNotifications } from '@/composables/useNotifications'
import { useWebSocket } from '@/composables/useWebSocket'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { Moon, Sunny, Medal, Checked, Document, Connection, Message, Postcard } from '@element-plus/icons-vue'
// 三层导航用到的图标（menu 里通过 <component :is="item.icon"> 渲染，需是组件引用）
import {
  HomeFilled,
  Calendar,
  BellFilled,
  Folder,
  UserFilled,
  Setting,
  Lock,
  Grid,
  Tickets,
  TrendCharts,
  Notebook,
  DataAnalysis,
  Share,
  Collection,
} from '@element-plus/icons-vue'
import { formatRelativeTime } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { startTour, maybeAutoTour } = useTour()
const { unreadCount, notifications, loading: notifLoading, fetchNotifications, handleMarkRead, handleMarkAllRead } = useNotifications()
const { connect: wsConnect, disconnect: wsDisconnect } = useWebSocket()
const { open: openCommandPalette } = useCommandPalette()

const isCollapsed = computed(() => appStore.sidebarCollapsed)
const sidebarWidth = computed(() => isCollapsed.value ? '64px' : '230px')

// 全局三层导航：我的工作 / 项目 / 团队 / 设置（管理类功能不再平铺在一级菜单）
const navGroups = [
  {
    key: 'work',
    label: '我的工作',
    items: [
      { label: '工作台', path: '/', icon: HomeFilled },
      { label: '日历', path: '/calendar', icon: Calendar },
      { label: '公告', path: '/announcements', icon: BellFilled },
      { label: '收件箱', path: '/email/inbox', icon: Postcard },
      { label: '邮箱绑定', path: '/email/accounts', icon: Connection },
    ],
  },
  {
    key: 'project',
    label: '项目',
    items: [{ label: '项目列表', path: '/projects', icon: Folder }],
  },
  {
    key: 'team',
    label: '团队',
    items: [{ label: '团队管理', path: '/teams', icon: UserFilled }],
  },
]

const adminNavGroup = {
  key: 'settings',
  label: '设置',
  items: [
    { label: '等级管理', path: '/admin/levels', icon: Medal, perm: 'level.manage' },
    { label: '角色权限', path: '/admin/roles', icon: Lock, perm: 'role.manage' },
    { label: '审批管理', path: '/admin/approvals', icon: Checked, perm: 'approval.manage' },
    { label: '操作审计', path: '/admin/audit-logs', icon: Document, perm: 'audit.log.view' },
    { label: 'Webhook 集成', path: '/admin/webhooks', icon: Setting, perm: 'settings.manage' },
    { label: '邮件发送日志', path: '/admin/email-send-logs', icon: Message, perm: 'settings.manage' },
  ],
}

/**
 * 菜单可见性用 **RBAC 能力** 判断，不再看 `users.role`。
 *
 * 改这一处的理由：后端 v2.18.2 已经把"是不是管理员"收敛到 AccessControlService（RBAC 优先），
 * 前端却还在用旧字段 `role === 'ROLE_ADMIN'` 决定要不要显示整个「设置」分组 ——
 * 结果是"只在 RBAC 里有角色、旧字段没同步"的管理员根本看不到这些页面（后端放行、前端藏起来）。
 * 旧字段只保留一个用途：权限码还没拉回来时兜底，避免刷新瞬间菜单闪一下。
 */
const permissionStore = usePermissionStore()
const isLegacyAdmin = computed(() => userStore.userInfo?.role === 'ROLE_ADMIN')

/**
 * 顶栏身份标记（v2.19.8）。口径与后端一致，但只用来决定**显示什么**：
 * - 系统管理员：旧 role 字段或 RBAC `role.manage`
 * - 团队管理员：RBAC `team.manage`（能看自己团队的全员任务流）
 * 前端不做安全判断，隐藏徽标不等于没权限。
 */
const isAdminAccount = computed(
  () => isLegacyAdmin.value || permissionStore.has('role.manage') || permissionStore.has('team.manage'),
)
const adminLabel = computed(() =>
  isLegacyAdmin.value || permissionStore.has('role.manage') ? '管理员' : '团队管理员',
)

const adminNavItems = computed(() =>
  adminNavGroup.items.filter(
    (item) => permissionStore.has(item.perm) || isLegacyAdmin.value
  )
)

const visibleNavGroups = computed(() => {
  const groups = [...navGroups]
  if (adminNavItems.value.length) {
    groups.push({ ...adminNavGroup, items: adminNavItems.value })
  }
  return groups
})

// 项目内导航：进入某个项目后，左侧只显示这个项目的功能
const projectNavItems = computed(() => {
  const id = currentProjectId.value
  if (!id) return []
  const base = `/projects/${id}`
  return [
    { label: '概览', path: base, icon: Grid },
    { label: '看板', path: `${base}/board`, icon: Tickets },
    { label: '迭代', path: `${base}/sprints`, icon: TrendCharts },
    { label: '时间线', path: `${base}/gantt`, icon: Calendar },
    { label: '报表', path: `${base}/reports`, icon: Document },
    { label: '知识库', path: `${base}/wiki`, icon: Notebook },
    { label: '交付分析', path: `${base}/analytics`, icon: DataAnalysis },
    { label: '工作流', path: `${base}/workflow`, icon: Share },
    { label: '故事地图', path: `${base}/storymap`, icon: Collection },
  ]
})

const currentProjectName = computed(() => {
  const id = currentProjectId.value
  const hit = myProjects.value.find((p) => p.id === id)
  return hit?.name || '项目空间'
})

const activeMenu = computed(() => {
  const p = route.path
  if (currentProjectId.value) {
    // 命中最长的项目内路径，例如 /projects/1/sprints/1/burndown → /projects/1/sprints
    const hit = [...projectNavItems.value]
      .sort((a, b) => b.path.length - a.path.length)
      .find((item) => p === item.path || p.startsWith(item.path + '/'))
    return hit?.path || p
  }
  if (p.startsWith('/projects')) return '/projects'
  if (p.startsWith('/teams')) return '/teams'
  return p
})

const showNotifications = ref(false)

async function toggleNotifications() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) await fetchNotifications()
}

async function handleNotifClick(n: any) {
  if (!n.isRead) await handleMarkRead(n.id)
  showNotifications.value = false
  const relatedId = n.relatedId
  if (!relatedId) {
    router.push('/')
    return
  }
  // 工作项类通知统一走 /issues/:id 落地页 —— 和邮件里的链接是同一个入口，
  // 免得同一件事在两个地方有两种行为（以前这里只认 ISSUE，审批类通知点了直接回工作台）。
  if (n.relatedType === 'ISSUE') {
    router.push(`/issues/${relatedId}`)
    return
  }
  if (n.relatedType === 'APPROVAL_REQUEST') {
    router.push(`/admin/approvals?id=${relatedId}`)
    return
  }
  router.push('/')
}
const myProjects = ref<ProjectVO[]>([])

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
  else if (command.startsWith('tour:')) {
    showNotifications.value = false
    const topic = command.slice('tour:'.length) as TourTopic
    // 启动失败的原因由 startTour 自己提示（页面不匹配 / 没有可引导内容），这里不再补一条
    startTour(topic, { force: true })
  }
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
  // 交给 useTour 决定：按路由选主题、等锚点渲染、同一会话只自动弹一次
  maybeAutoTour(route.path)
}

onMounted(() => {
  fetchMyProjects()
  document.addEventListener('click', handleClickOutside)
  checkTourTrigger()
  // Connect WebSocket
  wsConnect()
  // 检查审批结果通知
  checkApprovalNotifications()
  // 拉一次当前用户的权限码：左侧「设置」分组按能力显示，不再依赖 users.role
  if (!permissionStore.loaded) permissionStore.fetch()
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

watch(() => route.path, () => {
  abortTourOnNavigate()
  checkTourTrigger()
})
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

    /* 管理员头像加一圈金色描边：一眼能看出"这个账号权限不一样" */
    .el-avatar.is-admin {
      box-shadow: 0 0 0 2px var(--bg-card), 0 0 0 3.5px #f59e0b;
    }
  }

  &__username {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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

  /* 三层导航：分组标题与项目内导航头部 */
  .mimo-nav-label {
    padding: 14px 20px 6px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: rgba(255, 255, 255, 0.32);
    text-transform: uppercase;
  }

  .mimo-nav-back {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 20px 4px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
    cursor: pointer;
    transition: color 0.2s ease;

    &:hover { color: #fff; }
  }

  .mimo-nav-project {
    padding: 2px 20px 10px;
    font-size: 14px;
    font-weight: 700;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
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
