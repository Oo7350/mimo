<template>
  <div :class="['stat-card', { 'stat-card--loading': loading }]">
    <div v-if="loading" class="stat-card__skeleton">
      <SkeletonLoader variant="card" height="88px" />
    </div>
    <div v-else class="stat-card__content">
      <div class="stat-card__accent" :style="{ background: accentColor }" />
      <div class="stat-card__inner">
        <div class="stat-card__icon" :style="{ background: iconBg }">
          <el-icon :size="20"><component :is="icon" /></el-icon>
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
  const duration = 600
  const startTime = performance.now()

  function step(now: number) {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
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
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);

  &:hover {
    transform: translateY(-4px) scale(1.01);
    box-shadow: var(--shadow-lg);
    border-color: rgba(91,107,246,0.15);

    .stat-card__accent {
      height: 5px;
      opacity: 1;
    }
  }

  &__accent {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 3px;
    border-radius: var(--border-radius-lg) var(--border-radius-lg) 0 0;
    transition: all var(--transition-normal);
    opacity: 0.85;
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 18px;
    padding: 24px;
  }

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
    box-shadow: 0 3px 10px rgba(0,0,0,0.12);
    transition: transform var(--transition-normal);
  }

  &:hover &__icon {
    transform: scale(1.08) rotate(-2deg);
  }

  &__info {
    flex: 1;
  }

  &__value {
    font-size: 36px;
    font-weight: 800;
    line-height: 1.05;
    font-variant-numeric: tabular-nums;
    letter-spacing: -1.2px;
    transition: color var(--transition-fast);
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 5px;
    font-weight: 550;
    letter-spacing: -0.1px;
  }

  &__skeleton {
    padding: 8px;
  }
}
</style>
