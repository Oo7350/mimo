<!--
  GradientText — 仿 react-bits GradientText
  流动渐变彩色文字 (纯 CSS background-clip + 动画 background-position)
-->
<template>
  <span class="gradient-text" :class="className" :style="style">
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /** 渐变颜色数组 (首尾相同避免锯齿) */
  colors?: string[]
  /** 动画速度（秒） */
  speed?: number
  /** 是否显示带渐变描边的容器 */
  showBorder?: boolean
  /** 自定义 class */
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  colors: () => ['#40ffaa', '#4079ff', '#40ffaa', '#4079ff', '#40ffaa'],
  speed: 6,
  showBorder: false,
  className: '',
})

const style = computed(() => ({
  backgroundImage: `linear-gradient(to right, ${props.colors.join(', ')})`,
  animationDuration: `${props.speed}s`,
}))

void props.showBorder
</script>

<style scoped lang="scss">
.gradient-text {
  display: inline-block;
  background-size: 300% 100%;
  background-position: 0% 50%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  animation: gt-flow linear infinite;
  font-weight: 700;
  letter-spacing: -0.5px;
}

@keyframes gt-flow {
  0%, 100% { background-position: 0% 50%; }
  50%      { background-position: 100% 50%; }
}

@media (prefers-reduced-motion: reduce) {
  .gradient-text {
    animation: none !important;
  }
}
</style>
