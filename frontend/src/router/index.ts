import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/store/user";

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
        redirect: (to: any) => `/projects/${to.params.id}/board`,
      },
      {
        path: "projects/:id/sprints/:sprintId/burndown",
        redirect: (to: any) => `/projects/${to.params.id}/board`,
      },
      {
        path: "projects/:id/reports",
        name: "ReportList",
        component: () => import("@/views/ReportList.vue"),
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
        path: "admin/levels",
        name: "LevelManage",
        component: () => import("@/views/LevelManage.vue"),
      },
      {
        path: "admin/approvals",
        name: "ApprovalList",
        component: () => import("@/views/ApprovalList.vue"),
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

// 路由守卫 - 认证检查
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore();
  const requiresAuth = to.meta.requiresAuth !== false;

  if (requiresAuth && !userStore.token) {
    next({ name: "Login", query: { redirect: to.fullPath } });
  } else if (!requiresAuth && userStore.token) {
    next({ name: "Dashboard" });
  } else {
    next();
  }
});

export default router;
