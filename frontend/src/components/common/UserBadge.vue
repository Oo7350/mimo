<template>
  <div
    class="user-badge"
    :class="'user-badge--' + (level?.levelName?.toLowerCase() || 'l1')"
    :style="badgeStyle"
  >
    <span v-if="level?.badgeIcon === 'crown'" class="user-badge__icon">👑</span>
    <span v-else-if="level?.badgeIcon === 'medal'" class="user-badge__icon">🏅</span>
    <span v-else-if="level?.badgeIcon === 'star'" class="user-badge__icon">⭐</span>
    <span class="user-badge__text">{{ level?.levelName || 'L1' }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  level?: {
    level: number
    levelName: string
    badgeColor: string
    badgeIcon?: string
  }
  size?: 'small' | 'medium' | 'large'
}>()

const size = computed(() => props.size || 'medium')

const badgeStyle = computed(() => {
  const base: Record<string, string> = {
    background: props.level?.badgeColor || '#909399',
    color: '#fff',
  }

  // L4 特殊样式：渐变 + 光效
  if (props.level?.level === 4) {
    base.background = 'linear-gradient(135deg, #E6A23C, #F56C6C)'
  }

  return base
})
</script>

<style lang="scss" scoped>
.user-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  white-space: nowrap;

  &--small {
    padding: 2px 8px;
    font-size: 10px;
  }

  &--large {
    padding: 5px 14px;
    font-size: 13px;
  }

  &__icon {
    font-size: inherit;
  }

  &--l2 {
    box-shadow: 0 0 6px rgba(103, 194, 58, 0.35);
  }

  &--l3 {
    box-shadow: 0 0 8px rgba(64, 158, 255, 0.4);
    position: relative;

    &::after {
      content: '';
      position: absolute;
      inset: -1px;
      border-radius: 11px;
      background: linear-gradient(135deg, transparent 40%, rgba(255,255,255,0.15));
      pointer-events: none;
    }
  }

  &--l4 {
    animation: badge-glow 2s ease-in-out infinite;
    border: 1px solid rgba(255, 215, 0, 0.45);
    position: relative;
    overflow: visible;

    &::before {
      content: '';
      position: absolute;
      inset: -2px;
      border-radius: 12px;
      background: linear-gradient(45deg, transparent 30%, rgba(255,215,0,0.25), transparent 70%);
      animation: rotate-border 3s linear infinite;
      z-index: -1;
    }
  }
}

@keyframes badge-glow {
  0%, 100% {
    filter: brightness(1);
    transform: scale(1);
  }
  50% {
    filter: brightness(1.18);
    transform: scale(1.03);
  }
}

@keyframes rotate-border {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
