import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { login as loginApi, register as registerApi, getUserProfile } from "@/api/user";
import router from "@/router";
import { ElMessage } from "element-plus";
import { usePermissionStore } from "./permission";

interface UserInfo {
  id: number;
  username: string;
  email: string;
  avatar?: string;
  role: string;
}

export const useUserStore = defineStore("user", () => {
  const token = ref<string>(localStorage.getItem("token") || "");
  const userInfo = ref<UserInfo | null>(null);

  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => userInfo.value?.role === "ROLE_ADMIN");
  const username = computed(() => userInfo.value?.username || "");

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
    login,
    register,
    logout,
    fetchUserInfo,
  };
});
