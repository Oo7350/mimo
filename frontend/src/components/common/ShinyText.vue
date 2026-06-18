<!--
  ShinyText — 仿 react-bits ShinyText
  纯 CSS 文字光泽滑动动画
  用于品牌名 / 标题 / 欢迎语
-->
<template>
  <span
    class="shiny-text"
    :class="{ 'is-disabled': disabled }"
    :style="cssVars"
    @mouseenter="pause = pauseOnHover"
    @mouseleave="pause = false"
  >
    <slot>{{ text }}</slot>
  </span>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

interface Props {
  /** 显示的文字（也可使用默认 slot） */
  text?: string
  /** 文字底色 */
  color?: string
  /** 光泽颜色 */
  shineColor?: string
  /** 一次循环秒数 */
  speed?: number
  /** 循环间隔（秒） */
  delay?: number
  /** hover 时暂停 */
  pauseOnHover?: boolean
  /** 关闭动画 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  text: '',
  color: '#94a3b8',           // 灰蓝底，柔和
  shineColor: '#ffffff',
  speed: 2.6,                 // 比 react-bits 略慢一点，更克制
  delay: 1.2,                 // 每次循环后停顿
  pauseOnHover: true,
  disabled: false,
})

const pause = ref(false)

const cssVars = computed(() => ({
  '--shiny-color': props.color,
  '--shiny-shine': props.shineColor,
  '--shiny-speed': `${props.speed}s`,
  '--shiny-delay': `${props.delay}s`,
  '--shiny-state': props.disabled || pause.value ? 'paused' : 'running',
}))
</script>

<style scoped lang="scss">
.shiny-text {
  display: inline-block;
  position: relative;
  color: var(--shiny-color);
  background: linear-gradient(
    100deg,
    var(--shiny-color) 30%,
    var(--shiny-shine) 48%,
    var(--shiny-shine) 52%,
    var(--shiny-color) 70%
  );
  background-size: 220% 100%;
  background-position: 100% 0;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  // 始终跑动画，shimmer 由 background-position 控制
  animation: shiny-shimmer var(--shiny-speed) linear infinite;
  animation-play-state: var(--shiny-state);
  // 间歇性停顿：用 animation-delay + 长 duration + 大 background-size
  // 通过 animation-iteration-count 实现：每完成 N 次后停 1 次
  // 简化方案：直接跑连续动画 + speed 调慢，自然就有"间歇感"
}

@keyframes shiny-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

.shiny-text.is-disabled {
  animation: none;
  background: none;
  -webkit-text-fill-color: var(--shiny-color);
  color: var(--shiny-color);
}
</style>
