/**
 * 看板「进行中」计时器的显示逻辑（v2.16.0）
 *
 * 后端只存两样东西：
 * - inProgressStartedAt：本轮进入进行中的时间（非空 = 正在计时）
 * - inProgressSeconds：此前累计的秒数
 *
 * 前端用一个 1s 的 tick 把「已累计 + 本轮已跑」算出来，所以刷新页面、换设备、
 * 多人同时看同一个看板，显示的时间都是一致的，也不需要后端跑定时任务。
 */
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import type { IssueCard } from "@/types";

const now = ref(Date.now());
let ticker: ReturnType<typeof setInterval> | null = null;
let refCount = 0;

/** 全局单例 ticker：不管看板上有多少张卡片，只跑一个 1s 定时器 */
function acquireTicker() {
  refCount += 1;
  if (ticker) return;
  ticker = setInterval(() => {
    now.value = Date.now();
  }, 1000);
}

function releaseTicker() {
  refCount = Math.max(0, refCount - 1);
  if (refCount === 0 && ticker) {
    clearInterval(ticker);
    ticker = null;
  }
}

export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/**
 * @param getIssue 传入 ref/computed 取当前卡片，保证数据刷新（WebSocket/轮询）后自动跟随
 */
export function useIssueTimer(getIssue: () => IssueCard | undefined) {
  onMounted(acquireTicker);
  onBeforeUnmount(releaseTicker);

  // 双保险：既要求有开始时间，也要求当前状态确实是进行中，
  // 避免历史脏数据（例如状态已改成完成但 started_at 没清）导致卡片一直显示在走。
  const isRunning = computed(() => {
    const issue = getIssue();
    return Boolean(issue?.inProgressStartedAt) && issue?.status === "IN_PROGRESS";
  });

  const elapsedSeconds = computed(() => {
    const issue = getIssue();
    if (!issue) return 0;
    const base = issue.inProgressSeconds ?? 0;
    if (!issue.inProgressStartedAt) return base;
    const started = new Date(issue.inProgressStartedAt).getTime();
    if (Number.isNaN(started)) return base;
    return base + Math.max(0, Math.floor((now.value - started) / 1000));
  });

  const display = computed(() => formatDuration(elapsedSeconds.value));

  /** 例如「已进行 12:03」，用于 tooltip */
  const tooltip = computed(() => {
    const issue = getIssue();
    if (!issue) return "";
    if (isRunning.value) return `进行中，本轮已计时 ${display.value}`;
    if (elapsedSeconds.value > 0) return `累计进行中 ${display.value}`;
    return "尚未开始计时（拖到进行中列后自动开始）";
  });

  return { isRunning, elapsedSeconds, display, tooltip };
}
