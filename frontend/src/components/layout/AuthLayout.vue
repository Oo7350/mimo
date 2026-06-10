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
    padding: 48px;
    overflow: hidden;
    background: #0f172a;
  }

  &__hero-bg {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(ellipse 80% 60% at 20% 40%, rgba(99, 102, 241, 0.25) 0%, transparent 60%),
      radial-gradient(ellipse 60% 50% at 80% 70%, rgba(139, 92, 246, 0.2) 0%, transparent 55%),
      radial-gradient(ellipse 40% 40% at 50% 0%, rgba(16, 185, 129, 0.08) 0%, transparent 50%);
  }

  &__hero-content {
    position: relative;
    max-width: 520px;
    z-index: 1;
  }

  &__brand {
    margin-bottom: 36px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    svg { width: 40px; height: 40px; }

    span {
      font-size: 28px;
      font-weight: 800;
      color: #fff;
      letter-spacing: -0.5px;
    }
  }

  &__tagline {
    color: rgba(255, 255, 255, 0.65);
    font-size: 15px;
    line-height: 1.6;
  }

  &__preview {
    margin-bottom: 32px;
  }

  &__features {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 10px;

    li {
      display: flex;
      align-items: center;
      gap: 10px;
      color: rgba(255, 255, 255, 0.75);
      font-size: 14px;

      .el-icon {
        color: #10b981;
        font-size: 16px;
      }
    }
  }

  &__form-side {
    width: 480px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background: #fff;
  }

  &__form-wrapper {
    width: 100%;
    max-width: 380px;
  }
}

.preview-board {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  backdrop-filter: blur(12px);

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
    color: rgba(255, 255, 255, 0.6);
    margin-bottom: 10px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
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
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-left: 3px solid;
    border-radius: 8px;
    padding: 10px;
    margin-bottom: 8px;
    transition: transform 0.2s;

    &:hover { transform: translateY(-2px); }
  }

  &__card-type {
    font-size: 9px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.45);
    letter-spacing: 0.5px;
    margin-bottom: 4px;
  }

  &__card-title {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.85);
    font-weight: 500;
    line-height: 1.4;
  }
}

@media (max-width: 900px) {
  .auth-page {
    flex-direction: column;

    &__hero { padding: 32px 24px; min-height: auto; }
    &__preview { display: none; }
    &__form-side { width: 100%; padding: 32px 24px; }
  }
}
</style>
