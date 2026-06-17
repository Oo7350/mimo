<template>
  <div :class="['stat-card', { 'stat-card--loading': loading }]">
    <div v-if="loading" class="stat-card__skeleton">
      <SkeletonLoader variant="card" height="80px" />
    </div>
    <div v-else class="stat-card__inner">
      <div class="stat-card__value">{{ displayValue }}{{ suffix || '' }}</div>
      <div class="stat-card__label">{{ title }}</div>
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
    const eased = 1 - Math.pow(1 - progress, 3)
    displayValue.value = Math.round(start + diff * eased)
    if (progress < 1) animFrame = requestAnimationFrame(step)
  }
  animFrame = requestAnimationFrame(step)
}

onMounted(() => { if (!props.loading) displayValue.value = props.value })
watch(() => props.value, (v) => { if (!props.loading) animateValue(v) })
watch(() => props.loading, (ld) => { if (!ld) animateValue(props.value) })
</script>

<style scoped lang="scss">
.stat-card {
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  transition: border-color 0.15s;

  &:hover { border-color: rgba(0,0,0,0.12); }

  &__inner {
    padding: 20px 22px;
  }

  &__value {
    font-size: 32px;
    font-weight: 800;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    letter-spacing: -1px;
    color: var(--text-primary);
  }

  &__label {
    font-size: 12.5px;
    color: var(--text-muted);
    margin-top: 4px;
    font-weight: 550;
  }

  &__skeleton { padding: 8px; }
}
</style>
