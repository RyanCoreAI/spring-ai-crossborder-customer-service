<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="brand-block">
        <div class="brand-mark">OM</div>
        <div>
          <h1>OmniMerchant</h1>
          <p>跨境电商智能客服平台</p>
        </div>
      </div>

      <a-card class="login-card" :bordered="false">
        <h2>登录管理后台</h2>
        <p class="hint">使用管理员分配的账号进入客服、评测与可信控制台。</p>
        <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" @finish="handleLogin">
          <a-form-item label="邮箱" name="email">
            <a-input v-model:value="form.email" size="large" placeholder="admin@example.com">
              <template #prefix><MailOutlined /></template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" name="password">
            <a-input-password v-model:value="form.password" size="large" placeholder="请输入密码">
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>
          <a-button type="primary" size="large" block html-type="submit" :loading="loading">
            {{ loading ? '登录中...' : '登录' }}
          </a-button>
        </a-form>
      </a-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ email: '', password: '' })
const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true
  try {
    await authStore.login(form.email, form.password)
    message.success('登录成功')
    router.push('/admin')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(22, 119, 255, 0.12), transparent 34%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.login-panel {
  width: min(960px, 100%);
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 40px;
  align-items: center;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  display: grid;
  width: 56px;
  height: 56px;
  color: #fff;
  font-weight: 700;
  background: #1677ff;
  border-radius: 12px;
  place-items: center;
}

.brand-block h1 {
  margin: 0;
  font-size: 34px;
  color: #111827;
}

.brand-block p {
  margin: 8px 0 0;
  color: #667085;
}

.login-card {
  border: 1px solid #e5e7eb;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.login-card h2 {
  margin: 0;
  font-size: 22px;
}

.hint {
  margin: 8px 0 24px;
  color: #667085;
  font-size: 13px;
}

@media (max-width: 820px) {
  .login-panel {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .brand-block h1 {
    font-size: 28px;
  }
}
</style>
