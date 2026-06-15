<template>
  <div :class="['stat-card', { 'stat-card--loading': loading }]">
    <div v-if="loading" class="stat-card__skeleton">
      <SkeletonLoader variant="card" height="100px" />
    </div>
    <div v-else class="stat-card__content">
      <div class="stat-card__accent" :style="{ background: accentColor }" />
      <div class="stat-card__shine" />
      <div class="stat-card__inner">
        <div class="stat-card__icon-wrap" :style="{ '--glow': accentColor }">
          <div class="stat-card__icon" :style="{ background: iconBg }">
            <el-icon :size="22"><component :is="icon" /></el-icon>
          </div>
        </div>
        <div class="stat-card__info">
          <div class="stat-card__value" :style="{ color: accentColor }">
            {{ displayValue }}{{ suffix || '' }}
          </div>
          <div class="stat-card__label">{{ title }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import SkeletonLoader from './SkeletonLoader.vue'

const props = defineProps<{
  title: string
  value: number
  accentColor?: string
  iconBg?: string
  icon?: string
  loading?: boolean
  suffix?: string
}>()

const displayValue = ref(0)
let animFrame = 0

function animateValue(target: number) {
  cancelAnimationFrame(animFrame)
  const start = displayValue.value
  const diff = target - start
  const duration = 800
  const startTime = performance.now()

  function step(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    // Spring-like ease-out
    const eased = 1 - Math.pow(1 - progress, 4)
    displayValue.value = Math.round(start + diff * eased)
    if (progress < 1) {
      animFrame = requestAnimationFrame(step)
    }
  }
  animFrame = requestAnimationFrame(step)
}

onMounted(() => {
  if (!props.loading) displayValue.value = props.value
})

watch(() => props.value, (v) => {
  if (!props.loading) animateValue(v)
})

watch(() => props.loading, (ld) => {
  if (!ld) animateValue(props.value)
})
</script>

<style scoped lang="scss">
.stat-card {
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
  position: relative;
  overflow: hidden;
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);

  &:hover {
    transform: translateY(-6px) scale(1.02);
    box-shadow: var(--shadow-card-hover);
    border-color: rgba(79, 70, 229, 0.2);

    .stat-card__accent {
      height: 6px;
      opacity: 1;
    }

    .stat-card__icon-wrap {
      transform: scale(1.1) rotate(-5deg);
    }

    .stat-card__shine {
      opacity: 1;
      transform: translateX(100%);
    }
  }

  &__accent {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 3px;
    border-radius: var(--border-radius-lg) var(--border-radius-lg) 0 0;
    transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
    opacity: 0.8;
  }

  // Shine sweep on hover
  &__shine {
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.06), transparent);
    transition: transform 0.6s ease, opacity 0.6s ease;
    opacity: 0;
    pointer-events: none;
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 26px;
  }

  &__icon-wrap {
    transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
    filter: drop-shadow(0 4px 12px var(--glow, rgba(79, 70, 229, 0.3)));
  }

  &__icon {
    width: 52px;
    height: 52px;
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
  }

  &__info {
    flex: 1;
  }

  &__value {
    font-size: 40px;
    font-weight: 800;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    letter-spacing: -1.5px;
    transition: color 0.2s ease;
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 6px;
    font-weight: 600;
    letter-spacing: -0.1px;
  }

  &__skeleton {
    padding: 8px;
  }
}
</style>
