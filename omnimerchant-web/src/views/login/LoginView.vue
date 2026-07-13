<template>
  <main class="login-page">
    <section class="brand-side">
      <div class="brand-lockup">
        <span class="brand-mark">OM</span>
        <div><strong>OmniMerchant</strong><small>可信电商客服平台</small></div>
      </div>
      <div class="brand-message">
        <h1>商家客服运营工作台</h1>
        <p>面向平台运营人员和商家客服团队；账号角色与租户权限均由服务端分配。</p>
      </div>
      <p class="version">Spring Boot 4 · Spring AI 2</p>
    </section>

    <section class="form-side">
      <div class="mobile-brand">
        <span class="brand-mark">OM</span>
        <strong>OmniMerchant</strong>
      </div>
      <div class="login-form-wrap">
        <div class="form-heading">
          <h2>商家与平台人员登录</h2>
          <p>登录后将按账号权限进入对应租户，不支持在前端自行选择角色。</p>
        </div>
        <div class="role-note">
          <SafetyCertificateOutlined />
          <span>支持平台管理员、租户管理员、客服主管、客服和只读审计员。</span>
        </div>
        <a-alert v-if="loginError" type="error" show-icon :message="loginError" closable @close="loginError = ''" />
        <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" @finish="handleLogin">
          <a-form-item label="邮箱" name="email">
            <a-input v-model:value="form.email" size="large" autocomplete="username" placeholder="name@company.com">
              <template #prefix><MailOutlined /></template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" name="password">
            <a-input-password v-model:value="form.password" size="large" autocomplete="current-password" placeholder="请输入密码">
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>
          <a-button type="primary" size="large" block html-type="submit" aria-label="登录" :loading="loading">登录</a-button>
        </a-form>
        <a-button type="link" block class="buyer-entry" @click="router.push('/widget')">
          我是买家，进入咨询组件
        </a-button>
      </div>
      <p class="security-note">登录状态仅保存在当前浏览器会话中。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LockOutlined, MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { httpErrorMessage } from '@/utils/httpError'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const loginError = ref('')
const form = reactive({ email: '', password: '' })
const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true
  loginError.value = ''
  try {
    await authStore.login(form.email, form.password)
    await router.push('/admin')
  } catch (error: unknown) {
    loginError.value = httpErrorMessage(error, '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: minmax(320px, 42%) 1fr; background: #fff; }
.brand-side { position: relative; min-height: 100vh; display: flex; flex-direction: column; justify-content: space-between; padding: 42px 48px; background: #0d1728; color: #fff; }
.brand-lockup, .mobile-brand { display: flex; align-items: center; gap: 12px; }
.brand-mark { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 7px; background: #1677ff; color: #fff; font-size: 13px; font-weight: 800; }
.brand-lockup div { display: flex; flex-direction: column; }
.brand-lockup strong { font-size: 16px; }
.brand-lockup small { margin-top: 2px; color: #91a0b5; font-size: 11px; }
.brand-message { max-width: 440px; }
.brand-message h1 { margin: 0; max-width: 420px; font-size: 36px; line-height: 1.25; font-weight: 720; letter-spacing: 0; }
.brand-message p { margin: 16px 0 0; color: #a7b2c3; font-size: 14px; line-height: 1.7; }
.version { margin: 0; color: #64748b; font-size: 11px; }
.form-side { min-height: 100vh; display: grid; grid-template-rows: 1fr auto; place-items: center; padding: 40px; }
.login-form-wrap { width: min(380px, 100%); }
.form-heading { margin-bottom: 26px; }
.form-heading h2 { margin: 0; color: #111827; font-size: 24px; line-height: 32px; }
.form-heading p { margin: 7px 0 0; color: #667085; font-size: 13px; }
.role-note { display: flex; align-items: flex-start; gap: 8px; margin: -10px 0 20px; padding: 10px 12px; border: 1px solid #d9e6f7; border-radius: 6px; background: #f7faff; color: #475467; font-size: 12px; line-height: 1.6; }
.role-note :deep(.anticon) { margin-top: 3px; color: #1677ff; }
.login-form-wrap .ant-alert { margin-bottom: 18px; }
.login-form-wrap :deep(.ant-form-item-label > label) { color: #344054; font-size: 12px; font-weight: 600; }
.login-form-wrap :deep(.ant-input-affix-wrapper) { height: 44px; }
.login-form-wrap :deep(.ant-btn) { height: 44px; margin-top: 4px; font-weight: 650; }
.buyer-entry { height: 36px !important; margin-top: 10px !important; font-weight: 500 !important; }
.security-note { margin: 0 0 4px; color: #98a2b3; font-size: 11px; }
.mobile-brand { display: none; }
@media (max-width: 760px) {
  .login-page { grid-template-columns: 1fr; }
  .brand-side { display: none; }
  .form-side { min-height: 100vh; grid-template-rows: auto 1fr auto; align-items: center; padding: 24px; }
  .mobile-brand { width: 100%; display: flex; justify-self: start; color: #111827; }
  .login-form-wrap { align-self: center; }
}
</style>
