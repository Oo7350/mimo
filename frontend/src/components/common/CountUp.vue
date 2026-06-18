<!--
  CountUp — 仿 react-bits CountUp
  数字滚动动画（requestAnimationFrame + easeOutCubic）
  使用 IntersectionObserver，仅在进入视口时启动
-->
<template>
  <span ref="elRef" class="count-up" :class="className">
    {{ display }}
  </span>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'

interface Props {
  /** 目标值 */
  to: number
  /** 起始值 */
  from?: number
  /** 动画持续时间（秒） */
  duration?: number
  /** 延迟启动（秒） */
  delay?: number
  /** 方向：'up' 从 from 增到 to；'down' 反之 */
  direction?: 'up' | 'down'
  /** 千分位分隔符（默认无） */
  separator?: string
  /** 小数位数 */
  decimals?: number
  /** 自定义 class */
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  from: 0,
  duration: 1.6,
  delay: 0,
  direction: 'up',
  separator: '',
  decimals: 0,
  className: '',
})

const elRef = ref<HTMLSpanElement | null>(null)
const display = ref(formatNumber(props.from))

let raf = 0
let started = false
let observer: IntersectionObserver | null = null

function formatNumber(n: number): string {
  const fixed = n.toFixed(props.decimals)
  if (!props.separator) return fixed
  const [int, dec] = fixed.split('.')
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, props.separator)
  return dec ? `${grouped}.${dec}` : grouped
}

function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3)
}

function start() {
  if (started) return
  started = true

  const startVal = props.direction === 'down' ? props.to : props.from
  const endVal = props.direction === 'down' ? props.from : props.to
  const startTime = performance.now() + props.delay * 1000
  const durationMs = props.duration * 1000

  function tick(now: number) {
    if (now < startTime) {
      raf = requestAnimationFrame(tick)
      return
    }
    const elapsed = now - startTime
    const progress = Math.min(elapsed / durationMs, 1)
    const eased = easeOutCubic(progress)
    const current = startVal + (endVal - startVal) * eased
    display.value = formatNumber(current)
    if (progress < 1) {
      raf = requestAnimationFrame(tick)
    }
  }

  raf = requestAnimationFrame(tick)
}

onMounted(() => {
  // 进入视口才启动
  if (typeof IntersectionObserver === 'undefined') {
    start()
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          start()
          observer?.disconnect()
          break
        }
      }
    },
    { threshold: 0.3 },
  )
  if (elRef.value) observer.observe(elRef.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  observer?.disconnect()
})

// 监听 to 变化：值变化时重新滚动
watch(
  () => props.to,
  () => {
    started = false
    cancelAnimationFrame(raf)
    start()
  },
)
</script>

<style scoped lang="scss">
.count-up {
  display: inline-block;
  font-variant-numeric: tabular-nums;     // 数字等宽，不抖动
  letter-spacing: -0.02em;
}

@media (prefers-reduced-motion: reduce) {
  .count-up {
    transition: none !important;
  }
}
</style>
