import { computed } from "vue";
import { usePermissionStore } from "@/store/permission";

/**
 * usePermission — 组合式函数
 * 返回响应式的 has/hasAny 方法
 */
export function usePermission() {
  const store = usePermissionStore();

  const has = computed(() => (code: string) => store.has(code));
  const hasAny = computed(() => (codes: string[]) => store.hasAny(codes));
  const isSuperAdmin = computed(() => store.isSuperAdmin);

  return {
    has,
    hasAny,
    isSuperAdmin,
    codes: computed(() => store.codes),
    refresh: store.fetch,
  };
}
