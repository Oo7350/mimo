import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/store/user";
import { ElMessage } from "element-plus";

const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/Login.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("@/views/Register.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/",
    component: () => import("@/components/layout/MainLayout.vue"),
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        name: "Dashboard",
        component: () => import("@/views/Dashboard.vue"),
      },
      {
        path: "teams",
        name: "TeamList",
        component: () => import("@/views/TeamList.vue"),
      },
      {
        path: "teams/:id",
        name: "TeamDetail",
        component: () => import("@/views/TeamDetail.vue"),
      },
      {
        path: "projects/:id",
        name: "ProjectOverview",
        component: () => import("@/views/ProjectOverview.vue"),
      },
      {
        path: "projects/:id/board",
        name: "ProjectBoard",
        component: () => import("@/views/ProjectBoard.vue"),
      },
      {
        path: "projects/:id/sprints",
        name: "SprintList",
        component: () => import("@/views/SprintView.vue"),
      },
      {
        path: "projects/:id/sprints/:sprintId/burndown",
        name: "SprintBurndown",
        component: () => import("@/views/SprintView.vue"),
      },
      {
        path: "projects/:id/gantt",
        name: "ProjectGantt",
        component: () => import("@/views/GanttView.vue"),
      },
      {
        path: "projects/:id/reports",
        name: "ReportList",
        component: () => import("@/views/ReportList.vue"),
      },
      {
        path: "projects/:id/workflow",
        name: "WorkflowDesigner",
        component: () => import("@/views/WorkflowDesigner.vue"),
      },
      {
        path: "projects/:id/storymap",
        name: "StoryMap",
        component: () => import("@/components/issue/story/StoryMap.vue"),
        meta: { hideSidebar: true },
      },
      {
        path: "projects",
        name: "ProjectList",
        component: () => import("@/views/ProjectList.vue"),
      },
      {
        path: "profile",
        name: "Profile",
        component: () => import("@/views/Profile.vue"),
      },
      {
        path: "calendar",
        name: "Calendar",
        component: () => import("@/views/CalendarView.vue"),
      },
      {
        path: "announcements",
        name: "Announcements",
        component: () => import("@/views/Announcements.vue"),
      },
      {
        path: "email/inbox",
        name: "EmailInbox",
        component: () => import("@/views/EmailInbox.vue"),
      },
      {
        path: "email/accounts",
        name: "EmailAccounts",
        component: () => import("@/views/EmailAccounts.vue"),
      },
      {
        path: "admin/levels",
        name: "LevelManage",
        component: () => import("@/views/LevelManage.vue"),
      },
      {
        path: "admin/approvals",
        name: "ApprovalList",
        component: () => import("@/views/ApprovalList.vue"),
      },
      {
        path: "admin/roles",
        name: "RoleManage",
        component: () => import("@/views/RoleManage.vue"),
        meta: { requiresPermission: "role.manage" },
      },
      {
        path: "admin/audit-logs",
        name: "AuditLogs",
        component: () => import("@/views/AuditLogs.vue"),
        meta: { requiresPermission: "role.manage" },
      },
      {
        path: "admin/webhooks",
        name: "Webhooks",
        component: () => import("@/views/Webhooks.vue"),
        meta: { requiresPermission: "role.manage" },
      },
      {
        path: "admin/email-send-logs",
        name: "EmailSendLogs",
        component: () => import("@/views/EmailSendLogs.vue"),
        meta: { requiresPermission: "role.manage" },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫 - 认证 + 权限检查
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore();
  const requiresAuth = to.meta.requiresAuth !== false;

  if (requiresAuth && !userStore.token) {
    next({ name: "Login", query: { redirect: to.fullPath } });
    return;
  }
  if (!requiresAuth && userStore.token) {
    next({ name: "Dashboard" });
    return;
  }

  // 权限码检查
  if (to.meta.requiresPermission) {
    const { usePermissionStore } = await import("@/store/permission");
    const permStore = usePermissionStore();
    if (!permStore.loaded) await permStore.fetch();
    const code = to.meta.requiresPermission as string;
    if (!permStore.has(code)) {
      ElMessage.error("无访问权限：" + code);
      next({ name: "Dashboard" });
      return;
    }
  }
  next();
});

export default router;
