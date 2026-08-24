import { Directive, DirectiveBinding } from "vue";
import { usePermissionStore } from "@/store/permission";

/**
 * v-permission 指令 — 按权限码控制元素可见性
 *
 * 用法：
 *   <el-button v-permission="'issue.delete'">删除</el-button>
 *   <el-button v-permission="['issue.delete', 'issue.edit']">操作</el-button>
 *   多个权限为 OR 关系（任一满足即显示）
 *
 * 无权限时元素被移除（display:none 可能影响布局）
 */
export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    check(el, binding);
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    check(el, binding);
  },
};

function check(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const store = usePermissionStore();
  const value = binding.value;
  const codes = Array.isArray(value) ? value : [value];
  const ok = store.hasAny(codes);
  if (!ok) {
    el.parentNode?.removeChild(el);
  }
}

export default permission;
