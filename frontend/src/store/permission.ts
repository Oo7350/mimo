import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getMyPermissions } from "@/api/role";

/** 全部权限点数量（V11 共 45 个）。拿到全部即视为超级管理员 */
const ALL_PERMISSION_COUNT = 45;

/**
 * 权限 Store — 管理当前登录用户的权限码列表
 *
 * 两个地方会触发拉取：MainLayout 挂载时（决定菜单显示什么）、路由守卫（校验 meta.requiresPermission）。
 * 它们几乎同时发生，所以 fetch() 必须**返回同一个在途请求**：
 * 以前这里写的是 `if (loading.value) return`，守卫那一侧拿到 undefined 立刻往下走，
 * `has()` 因为 loaded 还是 false 直接判"无权限"，于是**菜单里有、点进去被弹回工作台**（BUG-097）。
 */
export const usePermissionStore = defineStore("permission", () => {
  const codes = ref<string[]>([]);
  const loaded = ref(false);
  const loading = ref(false);
  /** 同一个会话里只拉一次；失败后允许重试，所以不缓存"已尝试"状态 */
  let inflight: Promise<void> | null = null;

  const isSuperAdmin = computed(() => codes.value.length >= ALL_PERMISSION_COUNT);

  async function fetch() {
    if (loaded.value) return;
    if (inflight) return inflight;
    loading.value = true;
    inflight = (async () => {
      try {
        const res = await getMyPermissions();
        codes.value = res.data || [];
        loaded.value = true;
      } catch (e) {
        // 静默失败 — 未登录或网络异常；下次调用还能重试
        codes.value = [];
      } finally {
        loading.value = false;
        inflight = null;
      }
    })();
    return inflight;
  }

  function has(code: string): boolean {
    if (!loaded.value) return false;
    if (codes.value.length >= ALL_PERMISSION_COUNT) return true; // SUPER_ADMIN
    return codes.value.includes(code);
  }

  function hasAny(permCodes: string[]): boolean {
    if (!loaded.value) return false;
    if (codes.value.length >= ALL_PERMISSION_COUNT) return true;
    return permCodes.some((c) => codes.value.includes(c));
  }

  function reset() {
    codes.value = [];
    loaded.value = false;
    inflight = null;
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
