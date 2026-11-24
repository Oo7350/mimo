import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { login as loginApi, register as registerApi, getUserProfile } from "@/api/user";
import router from "@/router";
import { ElMessage } from "element-plus";
import { usePermissionStore } from "./permission";

/** 管理范围（后端 `AccessControlService#resolveAdminScope` 的结论） */
export type AdminScope = "SYSTEM" | "TEAM" | "NONE";

interface UserInfo {
  id: number;
  username: string;
  email: string;
  avatar?: string;
  role: string;
  adminScope?: AdminScope;
}

export const useUserStore = defineStore("user", () => {
  const token = ref<string>(localStorage.getItem("token") || "");
  const userInfo = ref<UserInfo | null>(null);

  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => userInfo.value?.role === "ROLE_ADMIN");
  const username = computed(() => userInfo.value?.username || "");

  /**
   * 管理范围：**只读后端结论**，前端不再自己拼权限码。
   *
   * 为什么要这样：v2.19.8 第一版用 `role === 'ROLE_ADMIN' || has('role.manage') || has('team.manage')`
   * 判断，结果开发库里 zhangsan（`users.role = ROLE_MEMBER`、RBAC 没角色、但
   * `team_members.role = ROLE_ADMIN` 是团队管理员）后端放行、前端把入口整个藏了 ——
   * 用户视角就是"这个功能没做"。前端看不到旧字段兜底，这类判定必须由后端给结论。
   *
   * 后端还没升级（响应里没有 adminScope）时退回旧口径，避免刷新瞬间入口闪一下。
   */
  const adminScope = computed<AdminScope>(() => {
    const fromServer = userInfo.value?.adminScope;
    if (fromServer) return fromServer;
    const permission = usePermissionStore();
    if (isAdmin.value || permission.has("role.manage")) return "SYSTEM";
    if (permission.has("team.manage")) return "TEAM";
    return "NONE";
  });

  /** 能不能看全员任务流 / 显示管理员身份（徽标、视角切换、看板全家泳道都看它） */
  const isManager = computed(() => adminScope.value !== "NONE");

  /** 徽标文案跟着真实范围走，不统一写"管理员"糊过去 */
  const managerLabel = computed(() => (adminScope.value === "SYSTEM" ? "管理员" : "团队管理员"));

  async function login(username: string, password: string) {
    const res = await loginApi({ username, password });
    token.value = res.data.token;
    userInfo.value = res.data.userInfo;
    localStorage.setItem("token", res.data.token);
    ElMessage.success("登录成功");
    // 拉取权限码
    usePermissionStore().fetch();
    router.push("/");
  }

  async function register(data: { username: string; password: string; email: string }) {
    await registerApi(data);
    ElMessage.success("注册成功，请登录");
    router.push("/login");
  }

  async function fetchUserInfo() {
    if (!token.value) return;
    try {
      const res = await getUserProfile();
      userInfo.value = res.data;
      // 已登录但权限未加载时，顺便拉权限
      const permStore = usePermissionStore();
      if (!permStore.loaded) permStore.fetch();
    } catch { /* token may be expired */ }
  }

  function logout() {
    token.value = "";
    userInfo.value = null;
    localStorage.removeItem("token");
    usePermissionStore().reset();
    router.push("/login");
  }

  // 应用初始化时自动获取用户信息
  if (token.value) {
    fetchUserInfo();
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    username,
    adminScope,
    isManager,
    managerLabel,
    login,
    register,
    logout,
    fetchUserInfo,
  };
});
