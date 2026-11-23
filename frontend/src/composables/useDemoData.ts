import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateDemoData } from '@/api/demo-data'

/**
 * 「一键生成示例数据」的共用逻辑（KNOWN-020）。
 *
 * 空库只有 3 个用户 1 个团队，工作台 / 迭代健康度 / 交付分析 / 审计日志全是 0 ——
 * 试用和演示都很难受。工作台和项目列表都放了这个入口，逻辑放这里避免抄两遍。
 *
 * 调用方自己决定按钮显示条件和生成成功后要刷新什么。
 */
export function useDemoData() {
  const generating = ref(false)

  async function generate(onDone?: () => void | Promise<void>) {
    try {
      await ElMessageBox.confirm(
        '会新建一个「示例项目」：3 个迭代、15 个工作项，以及评论、工时、验收记录和报告。\n\n' +
          '这些都是独立的示例数据，不会动你已有的项目和任务；不想要了直接删掉那个项目即可。',
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
      if (onDone) await onDone()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || e?.message || '生成失败')
    } finally {
      generating.value = false
    }
  }

  return { generating, generate }
}
