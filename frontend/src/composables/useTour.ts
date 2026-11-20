import { driver } from 'driver.js'
import type { Driver, DriveStep } from 'driver.js'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * 新手引导（driver.js）
 *
 * 设计要点（都是踩坑换来的）：
 * 1. 分主题：welcome / board / collab。引导只能在内容对得上的页面跑，
 *    以前只有一个写死看板的 3 步引导，从任何页面触发都在讲"看板列"，锚点还写着 @创建任务。
 * 2. 锚点先解析再运行：每一步都用选择器（或按文案定位）找真实元素，
 *    找不到就跳过这一步，而不是让 driver.js 挂到屏幕中央的 dummy 元素上讲错位置。
 *    整场一个锚点都找不到时直接提示"当前页面没有可引导的内容"，不启动。
 * 3. 退出必须干净：正常结束、跳过、Esc、路由切走，统一走 safeDestroy()，
 *    清掉 body 上的 driver-active/driver-fade 与残留遮罩。
 *    以前这些类不清会让全站 pointer-events: none —— 表现就是"页面点不动"。
 * 4. 状态按主题分开记，带版本号：跳过 ≠ 完成，跳过的主题下次还能看；
 *    入口不再因为看过一次就永久消失（以前 v-if="!tourDone" 一挂载就定死）。
 */

export type TourTopic = 'welcome' | 'board' | 'collab'

const STATE_KEY = 'mimo_tour_state'
const SESSION_KEY = 'mimo_tour_session'
const TOUR_VERSION = 2

interface TopicState {
  status: 'done' | 'skipped'
  v: number
  at: string
}
type StateMap = Partial<Record<TourTopic, TopicState>>

/** 步骤定义：锚点用函数解析，解析不到就跳过这一步 */
interface StepDef {
  /** 找不到时是否退化为居中气泡（只有在"讲整页"的步骤上才开） */
  center?: boolean
  title: string
  description: string
  side?: 'top' | 'bottom' | 'left' | 'right'
  target?: () => HTMLElement | null
}

function bySelector(...selectors: string[]): () => HTMLElement | null {
  return () => {
    for (const s of selectors) {
      const el = document.querySelector(s) as HTMLElement | null
      if (el) return el
    }
    return null
  }
}

/** 侧边栏菜单项是按文案渲染的，没有稳定 class，只能按文本找 */
function menuItem(text: string): () => HTMLElement | null {
  return () => {
    const items = Array.from(document.querySelectorAll('.el-menu-item, .mimo-sidebar__menu [role="menuitem"]'))
    for (const item of items) {
      if ((item.textContent || '').includes(text)) return item as HTMLElement
    }
    return null
  }
}

const TOPICS: Record<TourTopic, { label: string; steps: StepDef[] }> = {
  welcome: {
    label: '快速上手',
    steps: [
      {
        title: '顶栏：搜索、通知、主题',
        description: '中间是全局搜索，右侧依次是明暗主题切换、通知铃铛和你的账号菜单。',
        side: 'bottom',
        target: bySelector('.mimo-header'),
        center: true,
      },
      {
        title: 'Ctrl+K 全局搜索',
        description: '在任何页面按 Ctrl+K（Mac 上 ⌘K）都能搜任务、跳页面、执行快捷动作，不用一层层点菜单。',
        side: 'bottom',
        target: bySelector('.cmd-trigger'),
      },
      {
        title: '左侧导航',
        description: '导航分四组：我的工作、项目、团队、设置。管理员会多出审批、审计、Webhook 这些入口。',
        side: 'right',
        target: bySelector('.mimo-sidebar__menu'),
      },
      {
        title: '切换项目',
        description: '进入项目后，顶栏这里可以直接切到别的项目，不用先退回项目列表。',
        side: 'bottom',
        target: bySelector('.project-switcher'),
      },
      {
        title: 'AI 助手',
        description: '右下角这个圆钮是 AI 助手，可以问用法、让它把草稿整理成任务描述、按项目知识库回答问题。',
        side: 'left',
        target: bySelector('.ai-chat-panel__trigger'),
      },
    ],
  },
  board: {
    label: '看板实操',
    steps: [
      {
        title: '新建工作项',
        description: '在这里创建故事、任务或缺陷。也可以直接说一句话让 AI 帮你拆成结构化任务。',
        side: 'bottom',
        target: bySelector('#create-task-btn'),
      },
      {
        title: '三种状态的列',
        description: '待办 / 进行中 / 已完成。任务拖到哪一列，状态就变成哪一列，不用再单独改状态。',
        side: 'bottom',
        target: bySelector('.board__columns'),
      },
      {
        title: '拖过去就开始计时',
        description: '任务进入「进行中」列会自动开始计时，离开就停下并累计。卡片右下角的胶囊就是它，不用手动记工时。',
        side: 'left',
        target: bySelector('.issue-card-wrapper'),
      },
      {
        title: '卡片上的快捷操作',
        description: '鼠标移到卡片上会出现两个按钮：快速标记完成、直接打开编辑。不用点进详情页。',
        side: 'left',
        target: bySelector('.issue-card-wrapper'),
      },
      {
        title: '筛选和搜索',
        description: '按指派人、优先级、类型过滤，还能只按下我的任务看。过滤条件可以存成预设，下次一键切换。',
        side: 'bottom',
        target: bySelector('.board__filters'),
      },
      {
        title: '进入项目的其它视图',
        description: '左侧可以在概览、迭代、时间线、报表、知识库、交付分析之间切换，看板只是其中一种视角。',
        side: 'right',
        target: bySelector('.mimo-sidebar__menu'),
      },
    ],
  },
  collab: {
    label: '协作与治理',
    steps: [
      {
        title: '项目内导航',
        description: '概览看进度和风险，迭代管 Sprint 和燃尽图，交付分析看 Velocity 和累积流图。',
        side: 'right',
        target: bySelector('.mimo-sidebar__menu'),
      },
      {
        title: '迭代（Sprint）',
        description: 'Sprint 有健康度卡片：时间进度 vs 工作进度、逾期、阻塞、待验收，AI 还会给一句解读。',
        side: 'right',
        target: menuItem('迭代'),
      },
      {
        title: '交付分析',
        description: '承诺点与完成点、平均速度、累积流图都在这儿，用来判断下个迭代能接多少活。',
        side: 'right',
        target: menuItem('交付分析'),
      },
      {
        title: '知识库',
        description: '项目 Wiki：树形目录、Markdown 编辑、版本对比、附件，还能直接对它提问（带引用）。',
        side: 'right',
        target: menuItem('知识库'),
      },
      {
        title: '验收与变更都要走流程',
        description: '任务进 Sprint 后，需求字段的修改要提交变更申请、经审批才生效；完成前要先过验收，不能自己验自己。',
        side: 'bottom',
        target: bySelector('.cmd-trigger'),
        center: true,
      },
    ],
  },
}

// ---- 状态读写 ----

function readState(): StateMap {
  try {
    return JSON.parse(localStorage.getItem(STATE_KEY) || '{}') as StateMap
  } catch {
    return {}
  }
}

function writeState(topic: TourTopic, status: 'done' | 'skipped') {
  const state = readState()
  state[topic] = { status, v: TOUR_VERSION, at: new Date().toISOString() }
  localStorage.setItem(STATE_KEY, JSON.stringify(state))
}

/** 老版本迁移：以前只有一个 mimo_tour_completed=true 的全局标记 */
function migrateLegacyFlag() {
  if (localStorage.getItem('mimo_tour_completed') === 'true' && !localStorage.getItem(STATE_KEY)) {
    writeState('board', 'done')
    writeState('welcome', 'done')
    localStorage.removeItem('mimo_tour_completed')
  }
}

/** 某个主题是否已经看过（看过的不再自动弹，但仍可从菜单重看） */
export function hasSeen(topic: TourTopic): boolean {
  migrateLegacyFlag()
  const s = readState()[topic]
  return !!s && s.v === TOUR_VERSION
}

// ---- 引导本体 ----

let current: Driver | null = null
let currentTopic: TourTopic | null = null
let reachedLastStep = false
const isTourActive = ref(false)

/** 退出时统一清理：driver 正常销毁 + 兜底扫掉残留类与遮罩 */
function cleanupDom() {
  document.querySelectorAll('.driver-overlay, .driver-popover, #driver-dummy-element').forEach((el) => el.remove())
  document
    .querySelectorAll('.driver-active-element')
    .forEach((el) => el.classList.remove('driver-active-element', 'driver-no-interaction'))
  document.body.classList.remove('driver-active', 'driver-fade', 'driver-simple')
  // 极端情况下样式被打到 html 上
  document.documentElement.classList.remove('driver-active')
  document.body.style.pointerEvents = ''
  isTourActive.value = false
}

function safeDestroy(markAs?: 'done' | 'skipped') {
  if (current) {
    try {
      current.destroy()
    } catch {
      /* driver 自己已经销毁过 */
    }
    current = null
  }
  cleanupDom()
  if (currentTopic && markAs) {
    // 走到最后一步才算"完成"，中途退出/跳过只记 skipped，下次还能看
    writeState(currentTopic, markAs)
  }
  currentTopic = null
  reachedLastStep = false
}

/** 等锚点出现（看板是异步渲染的，引导起太早会挂到 dummy 元素上） */
async function waitForAnchor(timeoutMs = 4000): Promise<boolean> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    if (document.querySelector('#create-task-btn') || document.querySelector('.board__columns')) return true
    await new Promise((r) => setTimeout(r, 120))
  }
  return false
}

function resolveSteps(topic: TourTopic): DriveStep[] {
  const steps: DriveStep[] = []
  for (const def of TOPICS[topic].steps) {
    const el = def.target ? def.target() : null
    if (!el && !def.center) continue // 锚点不在就跳过，避免讲到错的元素上
    steps.push({
      element: el || undefined,
      popover: {
        title: def.title,
        description: def.description,
        side: def.side || 'bottom',
      },
    })
  }
  return steps
}

export interface StartTourOptions {
  /** 强制启动：不看"是否看过"，用于菜单里重播 */
  force?: boolean
}

/**
 * 启动某个主题的引导。返回是否真的启动了。
 */
export function startTour(topic: TourTopic = 'welcome', opts: StartTourOptions = {}): boolean {
  migrateLegacyFlag()
  // 重复点击菜单时先收掉上一场，否则 driver 的全局状态会让新实例直接失效（表现为"点了没反应"）
  if (current) safeDestroy()

  if (!opts.force && hasSeen(topic)) return false

  // 页面守卫：看板/协作主题只在对应页面跑，否则会退化成"只讲侧边栏的一步引导"，
  // 还会把该主题记成已完成，用户以后就再也看不到真正的看板引导了。
  const required = topic === 'board' ? ['/board'] : topic === 'collab' ? ['/projects/'] : []
  const path = router.currentRoute.value.path
  if (required.length && !required.some((r) => path.includes(r))) {
    ElMessage.info(
      topic === 'board' ? '看板引导需要在项目看板页启动，先进入项目的看板' : '这套引导需要在项目内启动'
    )
    return false
  }

  const steps = resolveSteps(topic)
  if (!steps.length) {
    ElMessage.info('当前页面没有可引导的内容，换个页面再试')
    return false
  }

  currentTopic = topic
  reachedLastStep = false
  document.body.dataset.tour = topic

  const d = driver({
    steps,
    animate: true,
    smoothScroll: true,
    allowClose: true,
    overlayColor: 'rgba(15, 23, 42, 0.6)',
    stagePadding: 6,
    stageRadius: 10,
    progressText: '{{current}} / {{total}}',
    nextBtnText: '下一步',
    prevBtnText: '上一步',
    doneBtnText: '开始使用',
    showButtons: ['next', 'previous', 'close'],
    onHighlighted: (_el, _step, { state }) => {
      reachedLastStep = state.activeIndex === steps.length - 1
    },
    // 每一步都挂一个显眼的「跳过」：遮罩会拦住页面点击，没有明显出路用户会以为卡死
    onPopoverRender: (popover) => {
      if (popover.wrapper.querySelector('.driver-skip-btn')) return
      const skip = document.createElement('button')
      skip.type = 'button'
      skip.className = 'driver-skip-btn'
      skip.textContent = '跳过引导'
      skip.setAttribute('aria-label', '跳过本次引导')
      skip.style.cssText =
        'margin-left:8px;padding:4px 10px;font-size:12px;color:var(--text-muted,#94a3b8);background:transparent;border:none;cursor:pointer;'
      skip.addEventListener('click', () => safeDestroy('skipped'))
      popover.wrapper.appendChild(skip)
    },
    onDestroyStarted: () => {
      // 走完最后一步点"开始使用"算完成；点 X / Esc 算跳过
      safeDestroy(reachedLastStep ? 'done' : 'skipped')
    },
  })

  current = d
  isTourActive.value = true
  try {
    d.drive()
  } catch (e) {
    console.warn('[tour] 启动失败', e)
    safeDestroy()
    ElMessage.warning('引导启动失败，请刷新页面重试')
    return false
  }
  return true
}

/** 按当前路由挑一个合适的主题 */
export function topicForRoute(path: string): TourTopic {
  if (path.includes('/board')) return 'board'
  if (path.includes('/projects/')) return 'collab'
  return 'welcome'
}

/**
 * 首次进入时自动触发。同一浏览器会话里只自动弹一次，避免在路由间来回切换时反复骚扰。
 */
export async function maybeAutoTour(path: string) {
  if (isTourActive.value) return
  const topic = topicForRoute(path)

  // 只在内容能对上时自动弹：欢迎引导需要顶栏+侧边栏，看板主题需要看板渲染出来
  if (topic === 'board') {
    const ready = await waitForAnchor()
    if (!ready) return
  }
  if (hasSeen(topic)) return

  const sessionKey = `${SESSION_KEY}_${topic}`
  if (sessionStorage.getItem(sessionKey)) return
  sessionStorage.setItem(sessionKey, '1')

  // 稍等一下，让页面把弹窗/骨架屏收掉
  await new Promise((r) => setTimeout(r, topic === 'board' ? 400 : 700))
  if (isTourActive.value) return
  startTour(topic)
}

/** 路由切走时收尾，避免遮罩留在新页面上 */
export function abortTourOnNavigate() {
  if (current) safeDestroy('skipped')
}

/** 供设置页/调试使用：清空所有引导记录 */
export function resetTourState() {
  localStorage.removeItem(STATE_KEY)
  localStorage.removeItem('mimo_tour_completed')
  Object.keys(sessionStorage)
    .filter((k) => k.startsWith(SESSION_KEY))
    .forEach((k) => sessionStorage.removeItem(k))
}

export function useTour() {
  return {
    startTour,
    maybeAutoTour,
    abortTourOnNavigate,
    hasSeen,
    resetTourState,
    topicForRoute,
    isTourActive,
    topics: Object.keys(TOPICS) as TourTopic[],
    labelOf: (t: TourTopic) => TOPICS[t].label,
  }
}

export const __tourInternals = { TOPICS, cleanupDom, router }
