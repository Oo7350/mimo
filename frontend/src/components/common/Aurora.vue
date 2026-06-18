<!--
  Aurora — 仿 react-bits Aurora
  纯 CSS / SVG 极光渐变背景
  极轻量（无 Canvas），自适应容器
  支持亮/暗主题
-->
<template>
  <div class="aurora" :class="`aurora--${speed}`">
    <div class="aurora__layer aurora__layer--1" />
    <div class="aurora__layer aurora__layer--2" />
    <div class="aurora__layer aurora__layer--3" />
    <div v-if="showNoise" class="aurora__noise" />
    <div class="aurora__overlay" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 主色 1 */
  color1?: string
  /** 主色 2 */
  color2?: string
  /** 主色 3 */
  color3?: string
  /** 动画速度档：slow / normal / fast */
  speed?: 'slow' | 'normal' | 'fast'
  /** 顶层加噪点（克制使用） */
  showNoise?: boolean
  /** 整体不透明度 */
  opacity?: number
}

withDefaults(defineProps<Props>(), {
  color1: '#4f46e5',
  color2: '#7c3aed',
  color3: '#06b6d4',
  speed: 'slow',
  showNoise: false,
  opacity: 0.55,
})
</script>

<style scoped lang="scss">
.aurora {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
  // 用 CSS 变量驱动
  --aurora-c1: v-bind(color1);
  --aurora-c2: v-bind(color2);
  --aurora-c3: v-bind(color3);
  --aurora-opacity: v-bind(opacity);
}

.aurora__layer {
  position: absolute;
  inset: -20%;
  filter: blur(80px);
  opacity: var(--aurora-opacity);
  will-change: transform;
  mix-blend-mode: screen;
}

.aurora--dark .aurora__layer {
  mix-blend-mode: screen;
}
.aurora--light .aurora__layer {
  mix-blend-mode: multiply;
}

.aurora__layer--1 {
  background: radial-gradient(
    ellipse 50% 60% at 30% 40%,
    var(--aurora-c1) 0%,
    transparent 70%
  );
  animation: aurora-drift-1 24s ease-in-out infinite;
}

.aurora__layer--2 {
  background: radial-gradient(
    ellipse 60% 50% at 70% 60%,
    var(--aurora-c2) 0%,
    transparent 70%
  );
  animation: aurora-drift-2 30s ease-in-out infinite;
}

.aurora__layer--3 {
  background: radial-gradient(
    ellipse 40% 50% at 50% 80%,
    var(--aurora-c3) 0%,
    transparent 70%
  );
  animation: aurora-drift-3 36s ease-in-out infinite;
}

.aurora--slow .aurora__layer--1 { animation-duration: 30s; }
.aurora--slow .aurora__layer--2 { animation-duration: 38s; }
.aurora--slow .aurora__layer--3 { animation-duration: 46s; }

.aurora--normal .aurora__layer--1 { animation-duration: 20s; }
.aurora--normal .aurora__layer--2 { animation-duration: 26s; }
.aurora--normal .aurora__layer--3 { animation-duration: 32s; }

.aurora--fast .aurora__layer--1 { animation-duration: 12s; }
.aurora--fast .aurora__layer--2 { animation-duration: 16s; }
.aurora--fast .aurora__layer--3 { animation-duration: 20s; }

.aurora__overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at top, transparent 0%, var(--bg-page, #f8f9fc) 100%);
  opacity: 0.35;
}

.aurora__noise {
  position: absolute;
  inset: 0;
  opacity: 0.04;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  mix-blend-mode: overlay;
}

@keyframes aurora-drift-1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%      { transform: translate(8%, -5%) scale(1.05); }
  66%      { transform: translate(-5%, 4%) scale(0.97); }
}

@keyframes aurora-drift-2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%      { transform: translate(-6%, 4%) scale(1.08); }
  66%      { transform: translate(5%, -6%) scale(0.95); }
}

@keyframes aurora-drift-3 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50%      { transform: translate(4%, 6%) scale(1.1); }
}

// 暗色模式：overlay 颜色用深色
:global([data-theme="dark"]) .aurora__overlay {
  background:
    radial-gradient(ellipse at top, transparent 0%, #030304 100%);
}

// 减少动画：尊重系统偏好
@media (prefers-reduced-motion: reduce) {
  .aurora__layer {
    animation: none !important;
  }
}
</style>
