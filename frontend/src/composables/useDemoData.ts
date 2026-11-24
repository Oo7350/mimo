import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearDemoData, generateDemoData, getDemoDataStatus } from '@/api/demo-data'

/**
 * 「示例数据」的共用逻辑（KNOWN-020 / v2.19.5）。
 *
 * 空库只有 3 个用户 1 个团队，工作台 / 迭代健康度 / 交付分析 / 审计日志全是 0 ——
 * 试用和演示都很难受。工作台和项目列表都放了这个入口，逻辑放这里避免抄两遍。
 *
 * v2.19.5 补上"收回去"：生成过一次之后必须能一键清掉，否则演示完要么手删一堆，
 * 要么假数据混在真数据里。清掉的只是生成器自己造的东西（示例项目 + 它的子记录 + 自动生成的工作流）。
 *
 * 调用方自己决定按钮显示条件和之后要刷新什么。
 */
export function useDemoData() {
  const generating = ref(false)
  const clearing = ref(false)
  const existed = ref<boolean | null>(null)

  /** 现在有没有示例数据（用于决定显示"生成"还是"清除"） */
  async function refreshStatus() {
    try {
      const res = await getDemoDataStatus()
      existed.value = Boolean(res?.data?.exists)
    } catch {
      existed.value = null // 查不到就别乱显示按钮
    }
    return existed.value
  }

  async function generate(onDone?: () => void | Promise<void>) {
    try {
      await ElMessageBox.confirm(
        '会新建一个「示例项目」：3 个迭代、15 个工作项，以及评论、工时、验收记录和报告。\n\n' +
          '这些都是独立的示例数据，不会动你已有的项目和任务；不想要了可以在同一个入口一键清除。',
        '生成示例数据',
        { confirmButtonText: '生成', cancelButtonText: '先不用' },
      )
    } catch {
      return // 用户取消
    }
    generating.value = true
    try {
      const res = await generateDemoData()
      ElMessage.success(res?.data?.message || '示例数据已生成')
      await refreshStatus()
      if (onDone) await onDone()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '生成失败')
    } finally {
      generating.value = false
    }
  }

  async function clear(onDone?: () => void | Promise<void>) {
    try {
      await ElMessageBox.confirm(
        '会删除「示例项目：移动端改版」以及它的工作项、评论、工时、验收记录、报告、自动生成的工作流。\n\n' +
          '其它项目和团队不受影响；如果那个团队只装着示例项目，团队也会一起删掉。',
        '清除示例数据',
        { confirmButtonText: '清除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' },
      )
    } catch {
      return
    }
    clearing.value = true
    try {
      const res = await clearDemoData()
      ElMessage.success(res?.data?.message || '示例数据已清除')
      await refreshStatus()
      if (onDone) await onDone()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '清除失败')
    } finally {
      clearing.value = false
    }
  }

  return { generating, clearing, existed, refreshStatus, generate, clear }
}
