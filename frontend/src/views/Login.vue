<template>
  <AuthLayout>
    <div class="auth-form">
      <h2 class="auth-form__title">欢迎回来</h2>
      <p class="auth-form__desc">登录你的 Mimo 账号，继续团队协作</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form__body"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="auth-form__submit"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-form__footer">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue"
import { useUserStore } from "@/store/user"
import type { FormInstance, FormRules } from "element-plus"
import AuthLayout from "@/components/layout/AuthLayout.vue"

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: "",
  password: "",
})

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 50, message: "用户名长度 3-50 字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码至少 6 位", trigger: "blur" },
  ],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.auth-form {
  &__title {
    font-size: 32px;
    font-weight: 800;
    color: var(--text-primary);
    letter-spacing: -0.8px;
    margin-bottom: 10px;
    line-height: 1.2;
  }

  &__desc {
    color: var(--text-secondary);
    font-size: 15px;
    margin-bottom: 36px;
    line-height: 1.6;
  }

  &__body {
    :deep(.el-form-item__label) {
      font-weight: 650;
      font-size: 13.5px;
      color: var(--text-primary);
      letter-spacing: -0.1px;
      padding-bottom: 8px;
    }
    :deep(.el-input) {
      .el-input__wrapper {
        border-radius: var(--border-radius-md);
        padding: 4px 16px;
        box-shadow: 0 0 0 1px var(--border-color);
        transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

        &:hover { box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.3); }
        &.is-focus {
          box-shadow:
            0 0 0 2px rgba(79, 70, 229, 0.25),
            0 0 20px rgba(79, 70, 229, 0.12);
          border-color: transparent;
        }
      }
      .el-input__inner { height: 44px; font-size: 14.5px; }
    }
  }

  &__submit {
    width: 100%;
    height: 48px;
    font-size: 16px;
    font-weight: 700;
    border-radius: var(--border-radius-md);
    background: linear-gradient(135deg, #4f46e5, #7c3aed);
    border: none;
    letter-spacing: 2px;
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
    position: relative;
    overflow: hidden;

    &:hover {
      transform: translateY(-2px) scale(1.01);
      box-shadow: 0 8px 28px rgba(79, 70, 229, 0.4), inset 0 1px 0 rgba(255,255,255,0.15);
    }
    &:active { transform: translateY(0) scale(0.98); }
  }

  &__footer {
    text-align: center;
    margin-top: 28px;
    font-size: 14px;
    color: var(--text-secondary);

    a {
      font-weight: 700;
      margin-left: 4px;
      color: var(--color-primary);
      text-decoration: none;
      transition: all 0.2s ease;

      &:hover {
        color: var(--color-primary-dark);
        text-decoration: underline;
        text-underline-offset: 3px;
      }
    }
  }
}
</style>
