<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="auth-page__hero">
      <div class="auth-page__hero-bg" />
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
                  <stop stop-color="#6366f1" />
                  <stop offset="1" stop-color="#8b5cf6" />
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
    color: '#6366f1',
    cards: [
      { type: 'STORY', title: '用户登录优化', color: '#10b981' },
      { type: 'BUG', title: '修复导出异常', color: '#ef4444' },
    ],
  },
  {
    name: '进行中',
    color: '#f59e0b',
    cards: [
      { type: 'TASK', title: 'API 接口联调', color: '#6366f1' },
    ],
  },
  {
    name: '已完成',
    color: '#10b981',
    cards: [
      { type: 'TASK', title: '数据库迁移', color: '#6366f1' },
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
    padding: 56px;
    overflow: hidden;
    background: #0a0c14;
  }

  &__hero-bg {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(ellipse 700px 500px at 18% 35%, rgba(91, 107, 246, 0.28) 0%, transparent 65%),
      radial-gradient(ellipse 550px 420px at 82% 68%, rgba(124, 108, 246, 0.22) 0%, transparent 58%),
      radial-gradient(ellipse 380px 320px at 52% 5%, rgba(34, 197, 94, 0.06) 0%, transparent 50%),
      linear-gradient(180deg, rgba(10,12,20,1) 0%, rgba(15,17,28,1) 100%);
  }

  // Animated mesh glow
  &__hero-bg::after {
    content: '';
    position: absolute;
    top: 20%;
    left: 30%;
    width: 400px;
    height: 400px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(91,107,246,0.08) 0%, transparent 70%);
    animation: hero-glow 8s ease-in-out infinite alternate;
    pointer-events: none;
  }
  @keyframes hero-glow {
    0% { transform: translate(0, 0); }
    100% { transform: translate(40px, -30px); }
  }

  &__hero-content {
    position: relative;
    max-width: 540px;
    z-index: 1;
  }

  &__brand {
    margin-bottom: 40px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 14px;

    svg { width: 44px; height: 44px; filter: drop-shadow(0 4px 12px rgba(91,107,246,0.3)); }

    span {
      font-size: 30px;
      font-weight: 800;
      color: #fff;
      letter-spacing: -0.8px;
    }
  }

  &__tagline {
    color: rgba(255, 255, 255, 0.6);
    font-size: 15.5px;
    line-height: 1.7;
    font-weight: 400;
  }

  &__preview {
    margin-bottom: 36px;
  }

  &__features {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 12px;

    li {
      display: flex;
      align-items: center;
      gap: 11px;
      color: rgba(255, 255, 255, 0.7);
      font-size: 14px;
      font-weight: 400;

      .el-icon {
        color: #22c55e;
        font-size: 17px;
        filter: drop-shadow(0 1px 3px rgba(34,197,94,0.4));
      }
    }
  }

  &__form-side {
    width: 500px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 48px;
    background: #fff;
  }

  &__form-wrapper {
    width: 100%;
    max-width: 380px;
  }
}

.preview-board {
  display: flex;
  gap: 14px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 18px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);

  &__col {
    flex: 1;
    min-width: 0;
  }

  &__col-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.55);
    margin-bottom: 12px;
    text-transform: uppercase;
    letter-spacing: 0.8px;
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__count {
    margin-left: auto;
    background: rgba(255, 255, 255, 0.1);
    padding: 1px 6px;
    border-radius: 8px;
    font-size: 10px;
  }

  &__card {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-left: 3px solid;
    border-radius: 10px;
    padding: 12px;
    margin-bottom: 10px;
    transition: transform 0.25s cubic-bezier(0.23, 1, 0.32, 1);

    &:hover { transform: translateY(-3px); }
  }

  &__card-type {
    font-size: 9px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.4);
    letter-spacing: 0.8px;
    margin-bottom: 5px;
  }

  &__card-title {
    font-size: 12.5px;
    color: rgba(255, 255, 255, 0.85);
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
