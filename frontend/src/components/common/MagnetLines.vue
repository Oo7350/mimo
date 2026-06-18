<!--
  MagnetLines — 仿 react-bits MagnetLines
  Canvas 绘制网格短线，鼠标附近短线受引力旋转
  极简、克制，适合做品牌区背景
-->
<template>
  <div ref="containerRef" class="magnet-lines">
    <canvas ref="canvasRef" class="magnet-lines__canvas" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'

interface Props {
  rows?: number
  cols?: number
  /** 主线颜色（CSS color） */
  lineColor?: string
  /** 鼠标附近高亮颜色 */
  activeColor?: string
  /** 影响半径（px） */
  radius?: number
  /** 最大旋转角度（度） */
  maxAngle?: number
}

const props = withDefaults(defineProps<Props>(), {
  rows: 9,
  cols: 12,
  lineColor: 'rgba(79, 70, 229, 0.18)',
  activeColor: 'rgba(124, 58, 237, 0.85)',
  radius: 180,
  maxAngle: 50,
})

const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let raf = 0
let width = 0
let height = 0
let dpr = 1
const mouse = { x: -9999, y: -9999 }

interface Line {
  cx: number
  cy: number
  baseAngle: number
  length: number
  currentAngle: number
  alpha: number
}

const lines: Line[] = []

function readColor(css: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const probe = document.createElement('span')
  probe.style.color = css
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).color
  document.body.removeChild(probe)
  return computed || fallback
}

let baseColorRGB = '124, 58, 237'
let activeColorRGB = '255, 255, 255'

function setupColors() {
  // 解析当前主题下的真实颜色（支持 var() 透传）
  const root = document.documentElement
  const themeBase = root.getAttribute('data-theme') === 'dark' ? '#a78bfa' : '#4f46e5'
  const baseColor = props.lineColor || themeBase
  const activeColor = props.activeColor || '#a78bfa'

  // 简单解析 rgb/rgba
  const baseResolved = readColor(baseColor, 'rgb(79, 70, 229)')
  const activeResolved = readColor(activeColor, 'rgb(167, 139, 250)')
  baseColorRGB = baseResolved.match(/\d+,\s*\d+,\s*\d+/)?.[0] || '79, 70, 229'
  activeColorRGB = activeResolved.match(/\d+,\s*\d+,\s*\d+/)?.[0] || '167, 139, 250'
}

function resize() {
  const container = containerRef.value
  const canvas = canvasRef.value
  if (!container || !canvas) return
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  width = container.clientWidth
  height = container.clientHeight
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  rebuildLines()
}

function rebuildLines() {
  lines.length = 0
  const stepX = width / (props.cols + 1)
  const stepY = height / (props.rows + 1)
  for (let r = 1; r <= props.rows; r++) {
    for (let c = 1; c <= props.cols; c++) {
      lines.push({
        cx: stepX * c,
        cy: stepY * r,
        baseAngle: -10,
        length: Math.min(stepX, stepY) * 0.55,
        currentAngle: -10,
        alpha: 0,
      })
    }
  }
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  ctx.lineCap = 'round'
  ctx.lineWidth = 1.4

  for (const line of lines) {
    const dx = line.cx - mouse.x
    const dy = line.cy - mouse.y
    const dist = Math.sqrt(dx * dx + dy * dy)

    let targetAngle = line.baseAngle
    let targetAlpha = 0.18

    if (dist < props.radius) {
      const ratio = 1 - dist / props.radius
      // 鼠标右下方短线向 -45 度（左上）旋转
      const sign = (dx + dy) > 0 ? -1 : 1
      targetAngle = line.baseAngle + sign * props.maxAngle * ratio
      targetAlpha = 0.18 + 0.82 * ratio
    }

    // 平滑插值
    line.currentAngle += (targetAngle - line.currentAngle) * 0.18
    line.alpha += (targetAlpha - line.alpha) * 0.18

    const rad = (line.currentAngle * Math.PI) / 180
    const dx2 = Math.cos(rad) * (line.length / 2)
    const dy2 = Math.sin(rad) * (line.length / 2)

    const r = dist < props.radius
      ? activeColorRGB
      : baseColorRGB

    ctx.strokeStyle = `rgba(${r}, ${line.alpha})`
    ctx.beginPath()
    ctx.moveTo(line.cx - dx2, line.cy - dy2)
    ctx.lineTo(line.cx + dx2, line.cy + dy2)
    ctx.stroke()
  }

  raf = requestAnimationFrame(draw)
}

function onMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
}

function onMouseLeave() {
  mouse.x = -9999
  mouse.y = -9999
}

const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null

onMounted(() => {
  setupColors()
  resize()
  raf = requestAnimationFrame(draw)
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseleave', onMouseLeave)
  if (observer && containerRef.value) observer.observe(containerRef.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseleave', onMouseLeave)
  observer?.disconnect()
})
</script>

<style scoped lang="scss">
.magnet-lines {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none; // 背景层不抢事件
  z-index: 0;
}

.magnet-lines__canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
