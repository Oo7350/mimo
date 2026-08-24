import { driver } from 'driver.js'
import type { DriveStep } from 'driver.js'
import { ref } from 'vue'
import router from '@/router'

const TOUR_KEY = 'mimo_tour_completed'

export function useTour() {
  const isTourActive = ref(false)

  const tourSteps: DriveStep[] = [
    {
      element: '#create-task-btn',
      popover: {
        title: '创建任务',
        description: '点击这里创建新的工作任务，支持故事、任务和缺陷三种类型',
        side: 'bottom',
      },
    },
    {
      popover: {
        title: '看板列',
        description: '每个列代表一种任务状态。你可以通过拖拽卡片在不同列之间移动任务',
        side: 'bottom',
      },
      onHighlightStarted: () => {
        // highlight the first column
        const col = document.querySelector('.board__column') as HTMLElement
        if (col) {
          col.style.outline = '2px solid var(--color-primary, #409EFF)'
          col.style.outlineOffset = '2px'
        }
      },
      onDeselected: () => {
        const col = document.querySelector('.board__column') as HTMLElement
        if (col) {
          col.style.outline = ''
          col.style.outlineOffset = ''
        }
      },
    },
    {
      element: '#create-task-btn',
      popover: {
        title: 'Sprint 迭代',
        description: '创建 Sprint 来规划迭代周期，使用燃尽图跟踪团队进度',
        side: 'bottom',
        onNextClick: (_el, _step, opts) => {
          // driver.js 1.x：定义了 onNextClick 必须显式销毁，否则"完成"按钮看似无反应
          opts.driver.destroy()
          // 引导结束后跳转到当前项目的 Sprint 页面
          const projectId = router.currentRoute.value.params.id
          if (projectId) {
            router.push(`/projects/${projectId}/sprints`)
          }
        },
      },
    },
  ]

  function startTour() {
    const d = driver({
      showProgress: true,
      steps: tourSteps,
      animate: true,
      overlayColor: 'rgba(0, 0, 0, 0.5)',
      doneBtnText: '完成',
      nextBtnText: '下一步',
      prevBtnText: '上一步',
      onDestroyed: () => {
        markTourCompleted()
        isTourActive.value = false
      },
    })
    d.drive()
    isTourActive.value = true
  }

  function markTourCompleted() {
    localStorage.setItem(TOUR_KEY, 'true')
  }

  function isTourCompleted(): boolean {
    return localStorage.getItem(TOUR_KEY) === 'true'
  }

  return { startTour, markTourCompleted, isTourCompleted, isTourActive }
}
