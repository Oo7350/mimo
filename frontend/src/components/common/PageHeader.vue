<template>
  <div class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.action" class="page-header__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  gap: 20px;

  &__main { flex: 1; }

  &__title {
    font-size: 30px;
    font-weight: 800;
    color: var(--text-primary);
    letter-spacing: -0.8px;
    line-height: 1.2;
  }

  &__subtitle {
    margin-top: 8px;
    font-size: 14.5px;
    color: var(--text-secondary);
    line-height: 1.55;
  }

  &__action {
    flex-shrink: 0;

    .el-button--primary {
      border-radius: 12px;
      padding: 12px 26px;
      font-weight: 700;
      font-size: 14px;
      letter-spacing: -0.1px;
      transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(79, 70, 229, 0.4);
      }
      &:active { transform: translateY(0) scale(0.97); }
    }
  }
}
</style>
