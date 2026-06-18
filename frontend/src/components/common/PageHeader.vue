<template>
  <div class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title">
        <slot name="title">{{ title }}</slot>
      </h1>
      <p v-if="$slots.subtitle || subtitle" class="page-header__subtitle">
        <slot name="subtitle">{{ subtitle }}</slot>
      </p>
    </div>
    <div v-if="$slots.action" class="page-header__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
}>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 20px;

  &__main { flex: 1; }

  &__title {
    font-size: 24px;
    font-weight: 750;
    color: var(--text-primary);
    letter-spacing: -0.5px;
    line-height: 1.2;
  }

  &__subtitle {
    margin-top: 6px;
    font-size: 13.5px;
    color: var(--text-secondary);
    line-height: 1.5;
  }

  &__action {
    flex-shrink: 0;

    .el-button--primary {
      border-radius: 8px;
      font-weight: 600;
      transition: opacity 0.15s;
      &:hover { opacity: 0.85; }
    }
  }
}
</style>
