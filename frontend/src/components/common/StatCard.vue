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
            {{ displayValue }}
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
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
  }

  &__accent {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 3px;
    border-radius: var(--border-radius-lg) var(--border-radius-lg) 0 0;
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
  }

  &__icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
  }

  &__value {
    font-size: 34px;
    font-weight: 800;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
    letter-spacing: -1px;
  }

  &__label {
    font-size: 13px;
    color: var(--text-secondary);
    margin-top: 4px;
    font-weight: 500;
  }

  &__skeleton {
    padding: 8px;
  }
}
</style>
