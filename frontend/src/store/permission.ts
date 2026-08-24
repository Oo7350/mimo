import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getMyPermissions } from "@/api/role";

/**
 * 权限 Store — 管理当前登录用户的权限码列表
 * 登录后调用 fetch() 拉取，路由守卫也可触发
 */
export const usePermissionStore = defineStore("permission", () => {
  const codes = ref<string[]>([]);
  const loaded = ref(false);
  const loading = ref(false);

  const isSuperAdmin = computed(
    () => codes.value.length >= 45 /* 全部权限点数量 */
  );

  async function fetch() {
    if (loading.value) return;
    loading.value = true;
    try {
      const res = await getMyPermissions();
      codes.value = res.data || [];
      loaded.value = true;
    } catch (e) {
      // 静默失败 — 未登录或网络异常
      codes.value = [];
    } finally {
      loading.value = false;
    }
  }

  function has(code: string): boolean {
    if (!loaded.value) return false;
    if (codes.value.length >= 45) return true; // SUPER_ADMIN
    return codes.value.includes(code);
  }

  function hasAny(permCodes: string[]): boolean {
    if (!loaded.value) return false;
    if (codes.value.length >= 45) return true;
    return permCodes.some((c) => codes.value.includes(c));
  }

  function reset() {
    codes.value = [];
    loaded.value = false;
  }

  return {
    codes,
    loaded,
    loading,
    isSuperAdmin,
    fetch,
    has,
    hasAny,
    reset,
  };
});
