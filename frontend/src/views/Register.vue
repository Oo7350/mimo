<template>
  <AuthLayout>
    <div class="auth-form">
      <h2 class="auth-form__title">
        <ShinyText
          text="创建账号"
          color="#0f172a"
          shine-color="#7c3aed"
          :speed="3"
        />
      </h2>
      <p class="auth-form__desc">
        <GradientText
          :colors="['#4f46e5', '#7c3aed', '#06b6d4', '#4f46e5']"
          :speed="5"
        >注册 Mimo，开启高效的项目协作之旅</GradientText>
      </p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form__body">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            prefix-icon="Message"
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
            @input="calcPasswordStrength"
          />
          <!-- 密码强度指示器 -->
          <div v-if="form.password" class="auth-form__strength">
            <div class="auth-form__strength-bar">
              <span
                v-for="i in 4"
                :key="i"
                class="auth-form__strength-seg"
                :class="{ 'is-filled': i <= strengthLevel, [`is-${strengthLabel}`]: i <= strengthLevel }"
              />
            </div>
            <span class="auth-form__strength-text" :class="`text-${strengthLabel}`">{{ strengthText }}</span>
          </div>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请确认密码"
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
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-form__footer">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue"
import { useUserStore } from "@/store/user"
import type { FormInstance, FormRules } from "element-plus"
import AuthLayout from "@/components/layout/AuthLayout.vue"
import ShinyText from "@/components/common/ShinyText.vue"
import GradientText from "@/components/common/GradientText.vue"

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: "",
  email: "",
  password: "",
  confirmPassword: "",
})

// 密码强度
const strengthLevel = ref(0)
const strengthLabel = ref<'weak' | 'fair' | 'good' | 'strong'>('weak')
const strengthText = ref('')

function calcPasswordStrength() {
  const pwd = form.password
  if (!pwd) { strengthLevel.value = 0; return }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  strengthLevel.value = Math.min(4, Math.ceil(score * 4 / 5))
  if (strengthLevel.value <= 1) { strengthLabel.value = 'weak'; strengthText.value = '弱 - 建议增加长度和复杂度' }
  else if (strengthLevel.value === 2) { strengthLabel.value = 'fair'; strengthText.value = '一般 - 可以更强' }
  else if (strengthLevel.value === 3) { strengthLabel.value = 'good'; strengthText.value = '不错 - 安全性良好' }
  else { strengthLabel.value = 'strong'; strengthText.value = '很强 - 非常安全' }
}

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error("两次密码输入不一致"))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 50, message: "用户名长度 3-50 字符", trigger: "blur" },
  ],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码至少 6 位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请确认密码", trigger: "blur" },
    { validator: validateConfirmPassword, trigger: "blur" },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      email: form.email,
    })
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
        transition: border-color 0.15s;

        &:hover { border-color: var(--text-muted); }
        &.is-focus {
          border-color: var(--text-primary);
          box-shadow: none;
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
    background: var(--text-primary);
    border: none;
    letter-spacing: 2px;
    color: #fff;
    transition: opacity 0.15s;

    &:hover { opacity: 0.85; }
    &:active { transform: scale(0.97); }
  }

  // ===== 密码强度 — Premium =====
  &__strength {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 10px;
  }

  &__strength-bar {
    display: flex;
    gap: 5px;
    flex: 1;
  }

  &__strength-seg {
    flex: 1;
    height: 5px;
    background: var(--border-color);
    border-radius: 3px;
    transition: all 0.25s ease;

    &.is-filled {
      &.is-weak { background: #ef4444; height: 6px; }
      &.is-fair { background: #f59e0b; height: 7px; }
      &.is-good { background: #10b981; height: 7px; }
      &.is-strong { background: #10b981; height: 7px; }
    }
  }

  &__strength-text {
    font-size: 11.5px;
    white-space: nowrap;
    font-weight: 600;

    &.text-weak { color: #ef4444; }
    &.text-fair { color: #f59e0b; }
    &.text-good { color: #10b981; }
    &.text-strong { color: #4f46e5; }
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
