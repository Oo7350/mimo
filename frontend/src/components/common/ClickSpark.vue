<!--
  ClickSpark — 仿 react-bits ClickSpark
  点击时在点击位置放射 8 条火花线
  使用 SVG 动画，性能好且支持 prefers-reduced-motion
-->
<template>
  <span class="click-spark" @click="onClick">
    <svg class="click-spark__svg" :width="svgSize" :height="svgSize" :style="svgStyle" :viewBox="`0 0 ${svgSize} ${svgSize}`">
      <g
        v-for="i in sparkCount"
        :key="i"
        class="click-spark__line"
        :class="{ 'is-active': active }"
        :style="lineStyle(i - 1)"
      >
        <line
          :x1="svgSize / 2"
          :y1="svgSize / 2"
          :x2="svgSize / 2 - Math.cos((i - 1) * (2 * Math.PI / sparkCount)) * sparkRadius"
          :y2="svgSize / 2 - Math.sin((i - 1) * (2 * Math.PI / sparkCount)) * sparkRadius"
          :stroke="sparkColor"
          :stroke-width="2"
          stroke-linecap="round"
        />
      </g>
    </svg>
    <slot />
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  /** 火花颜色 */
  sparkColor?: string
  /** 火花线长度（像素） */
  sparkSize?: number
  /** 火花线放射距离（像素） */
  sparkRadius?: number
  /** 火花线数量 */
  sparkCount?: number
  /** 动画持续时间（毫秒） */
  duration?: number
}

const props = withDefaults(defineProps<Props>(), {
  sparkColor: '#7c3aed',
  sparkSize: 10,
  sparkRadius: 18,
  sparkCount: 8,
  duration: 420,
})

const svgSize = 80
const svgStyle = {
  position: 'absolute' as const,
  left: '50%',
  top: '50%',
  width: `${svgSize}px`,
  height: `${svgSize}px`,
  transform: 'translate(-50%, -50%)',
  pointerEvents: 'none' as const,
  overflow: 'visible' as const,
}

const active = ref(false)
let timer: number | null = null

function lineStyle(i: number) {
  const angle = i * (2 * Math.PI / props.sparkCount)
  return {
    transform: `rotate(${(angle * 180) / Math.PI}deg)`,
    transformOrigin: `${svgSize / 2}px ${svgSize / 2}px`,
    '--spark-distance': `${props.sparkRadius}px`,
    '--spark-duration': `${props.duration}ms`,
  }
}

function onClick() {
  if (timer) {
    window.clearTimeout(timer)
  }
  active.value = false
  // 双 RAF 确保动画重新触发
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      active.value = true
      timer = window.setTimeout(() => {
        active.value = false
        timer = null
      }, props.duration)
    })
  })
}
</script>

<style scoped lang="scss">
.click-spark {
  position: relative;
  display: inline-block;
}

.click-spark__svg {
  z-index: 0;
}

.click-spark__line {
  opacity: 0;
  transform-origin: center;
  transition: none;
}

.click-spark__line.is-active {
  animation: spark-burst var(--spark-duration) ease-out forwards;
}

@keyframes spark-burst {
  0% {
    opacity: 1;
    transform: rotate(var(--rot, 0deg)) scale(0.4);
  }
  60% {
    opacity: 1;
    transform: rotate(var(--rot, 0deg)) scale(1.0) translateX(var(--spark-distance));
  }
  100% {
    opacity: 0;
    transform: rotate(var(--rot, 0deg)) scale(1.1) translateX(calc(var(--spark-distance) * 1.3));
  }
}

@media (prefers-reduced-motion: reduce) {
  .click-spark__line.is-active {
    animation: none !important;
  }
}
</style>
