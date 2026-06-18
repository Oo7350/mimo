<template>
  <div class="auth-page">
    <!-- 左侧品牌区 - 粒子动画 + MagnetLines 装饰层 -->
    <div class="auth-page__hero">
      <MagnetLines :rows="9" :cols="14" :radius="180" :max-angle="55" />
      <div class="glass-bg" />
      <canvas ref="particleCanvasRef" class="particle-canvas" />
      <div class="auth-page__hero-content">
        <div class="auth-page__brand">
          <div class="auth-page__logo">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="url(#logo-grad)" />
              <rect x="7" y="8" width="5" height="16" rx="2" fill="white" opacity="0.9" />
              <rect x="14" y="8" width="5" height="11" rx="2" fill="white" opacity="0.7" />
              <rect x="21" y="8" width="5" height="14" rx="2" fill="white" opacity="0.5" />
              <defs>
                <linearGradient id="logo-grad" x1="0" y1="0" x2="32" y2="32">
                  <stop stop-color="#4f46e5" />
                  <stop offset="1" stop-color="#7c3aed" />
                </linearGradient>
              </defs>
            </svg>
            <span class="auth-page__brand-name">Mimo</span>
          </div>
          <p class="auth-page__tagline">轻量级敏捷项目管理，让小团队协作更高效</p>
        </div>
        <ul class="auth-page__features">
          <li v-for="f in features" :key="f">
            <el-icon><CircleCheck /></el-icon>
            {{ f }}
          </li>
        </ul>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="auth-page__form-side">
      <div class="auth-page__form-wrapper">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import MagnetLines from '@/components/common/MagnetLines.vue'

const particleCanvasRef = ref<HTMLCanvasElement | null>(null)

const features = [
  'Story / Task / Bug 差异化管理',
  '看板拖拽 · 实时协作同步',
  'Sprint 燃尽图 · 日报周报',
]

// ---- Particle Animation (MIMO 字形粒子) ----
let animFrameId = 0

onMounted(() => {
  const canvas = particleCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  const container = canvas.parentElement!

  let width = canvas.width = container.clientWidth
  let height = canvas.height = container.clientHeight

  const mouse = { x: null as number | null, y: null as number | null, radius: 120 }

  // 高饱和度实色
  const colors = [
    '#FF69B4', '#00BFFF', '#FFD700',
  ]

  function getTextCoordinates() {
    const text = 'MIMO'
    const fontSize = Math.min(120, width * 0.22)
    ctx.font = `bold ${fontSize}px Arial`
    ctx.fillStyle = '#000'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, width / 2, height / 2)
    const data = ctx.getImageData(0, 0, width, height).data
    ctx.clearRect(0, 0, width, height)
    const coords: { x: number; y: number }[] = []
    const gap = 5
    for (let y = 0; y < height; y += gap) {
      for (let x = 0; x < width; x += gap) {
        const index = (y * width + x) * 4
        if (data[index + 3] > 128) coords.push({ x, y })
      }
    }
    return coords
  }

  class Particle {
    baseX: number; baseY: number
    x: number; y: number
    size: number; color: string
    vx = 0; vy = 0
    offsetX: number; offsetY: number
    waveOffset: number
    scatterX: number; scatterY: number

    constructor(targetX: number, targetY: number) {
      this.x = Math.random() * width
      this.y = Math.random() * height
      this.size = Math.random() * 2.5 + 1.5
      this.color = colors[Math.floor(Math.random() * colors.length)]
      this.baseX = targetX
      this.baseY = targetY
      this.offsetX = (Math.random() - 0.5) * 4
      this.offsetY = (Math.random() - 0.5) * 4
      this.waveOffset = Math.random() * Math.PI * 2
      this.scatterX = Math.random() * width
      this.scatterY = Math.random() * height
    }

    update(state: string, time: number) {
      if (mouse.x != null && mouse.y != null) {
        const dx = this.x - mouse.x
        const dy = this.y - mouse.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < mouse.radius) {
          const force = (mouse.radius - dist) / mouse.radius
          const angle = Math.atan2(dy, dx)
          this.vx += Math.cos(angle) * force * 2
          this.vy += Math.sin(angle) * force * 2
        }
      }

      if (state === 'gathering') {
        const waveAmplitude = 3
        const waveFrequency = 0.02
        const wave = Math.sin((this.baseX * waveFrequency) + time * 0.003 + this.waveOffset) * waveAmplitude
        const tx = this.baseX + this.offsetX
        const ty = this.baseY + this.offsetY + wave
        this.vx += (tx - this.x) * 0.04
        this.vy += (ty - this.y) * 0.04
        this.vx *= 0.90
        this.vy *= 0.90
      } else {
        const dx = this.scatterX - this.x
        const dy = this.scatterY - this.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist > 50) {
          this.vx += dx * 0.01
          this.vy += dy * 0.01
        } else {
          this.vx += (Math.random() - 0.5) * 1.0
          this.vy += (Math.random() - 0.5) * 1.0
        }
        this.vx *= 0.95
        this.vy *= 0.95
      }
      this.x += this.vx
      this.y += this.vy
      if (this.x < 0 || this.x > width) this.vx *= -1
      if (this.y < 0 || this.y > height) this.vy *= -1
    }

    draw() {
      ctx.fillStyle = this.color
      ctx.beginPath()
      ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  let particles: Particle[] = []
  let state = 'chaos'
  let lastTime = 0
  const interval = 5000

  function init() {
    particles = []
    const coords = getTextCoordinates()
    coords.forEach(c => particles.push(new Particle(c.x, c.y)))
  }

  function animate(timestamp: number) {
    ctx.clearRect(0, 0, width, height)
    if (timestamp - lastTime > interval) {
      lastTime = timestamp
      state = state === 'chaos' ? 'gathering' : 'chaos'
      if (state === 'chaos') {
        particles.forEach(p => {
          p.scatterX = Math.random() * width
          p.scatterY = Math.random() * height
          const angle = Math.random() * Math.PI * 2
          p.vx = Math.cos(angle) * 10
          p.vy = Math.sin(angle) * 10
        })
      }
    }
    particles.forEach(p => { p.update(state, timestamp); p.draw() })
    animFrameId = requestAnimationFrame(animate)
  }

  function onResize() {
    if (!canvas) return
    width = canvas.width = container.clientWidth
    height = canvas.height = container.clientHeight
    init()
  }

  init()
  animate(0)

  window.addEventListener('resize', onResize)
  ;(container as HTMLElement).addEventListener('mousemove', (e: MouseEvent) => {
    mouse.x = e.offsetX
    mouse.y = e.offsetY
  })
  ;(container as HTMLElement).addEventListener('mouseout', () => {
    mouse.x = null
    mouse.y = null
  })

  onBeforeUnmount(() => {
    cancelAnimationFrame(animFrameId)
    window.removeEventListener('resize', onResize)
  })
})
</script>

<style scoped lang="scss">
.auth-page {
  display: flex;
  min-height: 100vh;

  &__hero {
    flex: 1;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 60px;
    overflow: hidden;
    background: #ffffff;
  }

  .glass-bg {
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.6);
    backdrop-filter: blur(15px);
    -webkit-backdrop-filter: blur(15px);
    z-index: 0;
  }

  .particle-canvas {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    z-index: 2;       // 粒子在最上（视觉主体）
    pointer-events: none;
  }

  // MagnetLines 已经在 z-index: 0 (组件内)，它作为最底层
  // 但因 glass-bg 在它之上，磁吸线基本被遮住——这是有意的
  // 真正要看磁吸效果需关闭粒子层；保留双层以兼容暗色 / 移动端

  &__hero-content {
    position: relative;
    max-width: 560px;
    z-index: 2;
  }

  &__brand {
    margin-bottom: 44px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 18px;

    svg {
      width: 52px;
      height: 52px;
      filter: drop-shadow(0 8px 24px rgba(79, 70, 229, 0.4));
    }

    .auth-page__brand-name {
      font-size: 36px;
      font-weight: 800;
      color: #1a1a2e;          // 白色 hero 底，深色字
      letter-spacing: -1.2px;
    }
  }

  &__tagline {
    color: #555;
    font-size: 16px;
    line-height: 1.7;
    font-weight: 400;
    letter-spacing: -0.1px;
  }

  &__features {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 14px;

    li {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #666;
      font-size: 14.5px;
      font-weight: 400;

      .el-icon {
        color: #059669;
        font-size: 18px;
        filter: drop-shadow(0 2px 6px rgba(5, 150, 105, 0.5));
      }
    }
  }

  &__form-side {
    width: 520px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 52px;
    background: #fff;
    position: relative;
  }

  // Subtle gradient accent on form side
  &__form-side::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 4px;
    background: var(--gradient-primary);
  }

  &__form-wrapper {
    width: 100%;
    max-width: 380px;
  }
}

@media (max-width: 900px) {
  .auth-page {
    flex-direction: column;

    &__hero { padding: 36px 28px; min-height: auto; }
    &__form-side { width: 100%; padding: 36px 28px; }
  }
}
</style>
