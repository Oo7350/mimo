<template>
  <div :class="['skeleton', `skeleton--${variant}`]" :style="skeletonStyle">
    <template v-if="variant === 'card'">
      <div class="skeleton__block" />
    </template>
    <template v-else-if="variant === 'list-item'">
      <div v-for="i in count" :key="i" class="skeleton__row">
        <div class="skeleton__line skeleton__line--short" />
        <div class="skeleton__line" />
      </div>
    </template>
    <template v-else-if="variant === 'board-column'">
      <div v-for="i in count" :key="i" class="skeleton__card">
        <div class="skeleton__line skeleton__line--short" />
        <div class="skeleton__line" />
        <div class="skeleton__line skeleton__line--mid" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  variant: 'card' | 'list-item' | 'board-column'
  count?: number
  height?: string
  width?: string
}>(), {
  count: 3,
})

const skeletonStyle = computed(() => ({
  height: props.height,
  width: props.width,
}))
</script>

<style scoped lang="scss">
.skeleton {
  &__block,
  &__line,
  &__card {
    background: linear-gradient(
      90deg,
      var(--bg-column) 25%,
      var(--bg-page) 50%,
      var(--bg-column) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.5s ease infinite;
    border-radius: var(--border-radius-sm);
  }

  &--card &__block {
    height: 80px;
    border-radius: var(--border-radius-md);
  }

  &--list-item &__row {
    padding: 8px 0;
  }

  &__line {
    height: 14px;
    margin-bottom: 8px;
    border-radius: 4px;

    &--short {
      width: 40%;
    }

    &--mid {
      width: 70%;
    }
  }

  &--board-column &__card {
    height: 100px;
    margin-bottom: 8px;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
}

@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}
</style>
