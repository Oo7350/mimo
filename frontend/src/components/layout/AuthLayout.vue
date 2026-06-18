<template>
  <div class="auth-page">
    <!-- 左侧品牌区 - MagnetLines 磁吸网格线背景 -->
    <div class="auth-page__hero">
      <MagnetLines :rows="9" :cols="14" :radius="180" :max-angle="55" />
      <div class="glass-bg" />
      <div class="auth-page__hero-content">
        <div class="auth-page__brand">
          <div class="auth-page__logo">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="url(#logo-grad)" />
              <rect x="7" y="8" width="5" height="16" rx="2" fill="white" opacity="0.9" />
              <rect x="14" y="8" width="5" height="11" rx="2" fill="white" opacity="0.7" />
              <rect x="21" y="8" width="5" height="14" rx="2" fill="white" opacity="0.5" />
              <defs>
                <linearGradient id="logo-grad" x1="0" y1="0" x2="32" y2="32">
                  <stop stop-color="#4f46e5" />
                  <stop offset="1" stop-color="#7c3aed" />
                </linearGradient>
              </defs>
            </svg>
            <ShinyText
              text="Mimo"
              color="#ffffff"
              shine-color="#a78bfa"
              :speed="3.5"
            />
          </div>
          <p class="auth-page__tagline">轻量级敏捷项目管理，让小团队协作更高效</p>
        </div>
        <ul class="auth-page__features">
          <li v-for="f in features" :key="f">
            <el-icon><CircleCheck /></el-icon>
            {{ f }}
          </li>
        </ul>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="auth-page__form-side">
      <div class="auth-page__form-wrapper">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import MagnetLines from '@/components/common/MagnetLines.vue'
import ShinyText from '@/components/common/ShinyText.vue'

const features = [
  'Story / Task / Bug 差异化管理',
  '看板拖拽 · 实时协作同步',
  'Sprint 燃尽图 · 日报周报',
]
</script>

<style scoped lang="scss">
.auth-page {
  display: flex;
  min-height: 100vh;

  &__hero {
    flex: 1;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 60px;
    overflow: hidden;
    background: #ffffff;
  }

  .glass-bg {
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.6);
    backdrop-filter: blur(15px);
    -webkit-backdrop-filter: blur(15px);
    z-index: 0;
  }

  &__hero-content {
    position: relative;
    max-width: 560px;
    z-index: 2;
  }

  &__brand {
    margin-bottom: 44px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 18px;

    svg {
      width: 52px;
      height: 52px;
      filter: drop-shadow(0 8px 24px rgba(79, 70, 229, 0.4));
    }

    span {
      font-size: 36px;
      font-weight: 800;
      color: #1a1a2e;
      letter-spacing: -1.2px;
    }
  }

  &__tagline {
    color: #555;
    font-size: 16px;
    line-height: 1.7;
    font-weight: 400;
    letter-spacing: -0.1px;
  }

  &__features {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 14px;

    li {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #666;
      font-size: 14.5px;
      font-weight: 400;

      .el-icon {
        color: #059669;
        font-size: 18px;
        filter: drop-shadow(0 2px 6px rgba(5, 150, 105, 0.5));
      }
    }
  }

  &__form-side {
    width: 520px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 52px;
    background: #fff;
    position: relative;
  }

  // Subtle gradient accent on form side
  &__form-side::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 4px;
    background: var(--gradient-primary);
  }

  &__form-wrapper {
    width: 100%;
    max-width: 380px;
  }
}

@media (max-width: 900px) {
  .auth-page {
    flex-direction: column;

    &__hero { padding: 36px 28px; min-height: auto; }
    &__form-side { width: 100%; padding: 36px 28px; }
  }
}
</style>
