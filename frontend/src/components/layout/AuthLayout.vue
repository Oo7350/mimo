<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="auth-page__hero">
      <div class="auth-page__hero-bg" />
      <div class="auth-page__hero-orb hero-orb-1" />
      <div class="auth-page__hero-orb hero-orb-2" />
      <div class="auth-page__hero-orb hero-orb-3" />
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
            <span>Mimo</span>
          </div>
          <p class="auth-page__tagline">轻量级敏捷项目管理，让小团队协作更高效</p>
        </div>

        <!-- 模拟看板预览 -->
        <div class="auth-page__preview">
          <div class="preview-board">
            <div v-for="col in previewColumns" :key="col.name" class="preview-board__col">
              <div class="preview-board__col-header">
                <span class="preview-board__dot" :style="{ background: col.color }" />
                {{ col.name }}
                <span class="preview-board__count">{{ col.cards.length }}</span>
              </div>
              <div
                v-for="(card, i) in col.cards"
                :key="i"
                class="preview-board__card"
                :style="{ borderLeftColor: card.color }"
              >
                <div class="preview-board__card-type">{{ card.type }}</div>
                <div class="preview-board__card-title">{{ card.title }}</div>
              </div>
            </div>
          </div>
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
const previewColumns = [
  {
    name: '待办',
    color: '#4f46e5',
    cards: [
      { type: 'STORY', title: '用户登录优化', color: '#059669' },
      { type: 'BUG', title: '修复导出异常', color: '#dc2626' },
    ],
  },
  {
    name: '进行中',
    color: '#d97706',
    cards: [
      { type: 'TASK', title: 'API 接口联调', color: '#4f46e5' },
    ],
  },
  {
    name: '已完成',
    color: '#059669',
    cards: [
      { type: 'TASK', title: '数据库迁移', color: '#4f46e5' },
    ],
  },
]

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
    background: #050507;
  }

  &__hero-bg {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(ellipse 900px 600px at 15% 30%, rgba(79, 70, 229, 0.35) 0%, transparent 60%),
      radial-gradient(ellipse 700px 500px at 85% 70%, rgba(124, 58, 237, 0.25) 0%, transparent 55%),
      radial-gradient(ellipse 400px 300px at 50% 5%, rgba(5, 150, 105, 0.08) 0%, transparent 50%);
  }

  // Floating orbs — animated
  &__hero-orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    pointer-events: none;
  }

  .hero-orb-1 {
    width: 500px;
    height: 500px;
    top: -15%;
    left: -10%;
    background: rgba(79, 70, 229, 0.18);
    animation: orb-float-1 12s ease-in-out infinite alternate;
  }
  .hero-orb-2 {
    width: 350px;
    height: 350px;
    bottom: -10%;
    right: -5%;
    background: rgba(124, 58, 237, 0.15);
    animation: orb-float-2 10s ease-in-out infinite alternate;
  }
  .hero-orb-3 {
    width: 200px;
    height: 200px;
    top: 50%;
    left: 50%;
    background: rgba(5, 150, 105, 0.08);
    animation: orb-float-3 8s ease-in-out infinite alternate;
  }

  @keyframes orb-float-1 {
    0% { transform: translate(0, 0) scale(1); }
    100% { transform: translate(60px, 40px) scale(1.15); }
  }
  @keyframes orb-float-2 {
    0% { transform: translate(0, 0) scale(1); }
    100% { transform: translate(-40px, -30px) scale(1.1); }
  }
  @keyframes orb-float-3 {
    0% { transform: translate(-50%, -50%) scale(1); }
    100% { transform: translate(calc(-50% + 30px), calc(-50% - 20px)) scale(1.2); }
  }

  &__hero-content {
    position: relative;
    max-width: 560px;
    z-index: 1;
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
      color: #fff;
      letter-spacing: -1.2px;
    }
  }

  &__tagline {
    color: rgba(255, 255, 255, 0.55);
    font-size: 16px;
    line-height: 1.7;
    font-weight: 400;
    letter-spacing: -0.1px;
  }

  &__preview {
    margin-bottom: 40px;
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
      color: rgba(255, 255, 255, 0.65);
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

.preview-board {
  display: flex;
  gap: 14px;
  padding: 22px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.04),
    0 20px 60px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);

  &__col {
    flex: 1;
    min-width: 0;
  }

  &__col-header {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 11px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.45);
    margin-bottom: 14px;
    text-transform: uppercase;
    letter-spacing: 1px;
  }

  &__dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
    box-shadow: 0 0 8px currentColor;
  }

  &__count {
    margin-left: auto;
    background: rgba(255, 255, 255, 0.08);
    padding: 2px 7px;
    border-radius: 8px;
    font-size: 10px;
    font-weight: 600;
  }

  &__card {
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-left: 3px solid;
    border-radius: 12px;
    padding: 14px;
    margin-bottom: 10px;
    transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease;

    &:hover {
      transform: translateY(-4px) scale(1.02);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
    }
  }

  &__card-type {
    font-size: 9px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.3);
    letter-spacing: 1px;
    margin-bottom: 6px;
  }

  &__card-title {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.8);
    font-weight: 500;
    line-height: 1.45;
  }
}

@media (max-width: 900px) {
  .auth-page {
    flex-direction: column;

    &__hero { padding: 36px 28px; min-height: auto; }
    &__preview { display: none; }
    &__form-side { width: 100%; padding: 36px 28px; }
  }
}
</style>
