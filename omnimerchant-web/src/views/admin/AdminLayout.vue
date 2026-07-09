<template>
  <a-layout class="admin-layout">
    <a-layout-sider class="admin-sider" :width="248">
      <div class="admin-brand" @click="router.push('/admin')">
        <div class="brand-mark">OM</div>
        <div>
          <h1>OmniMerchant</h1>
          <p>AI 客服运营台</p>
        </div>
      </div>

      <a-menu
        class="admin-menu"
        :selected-keys="[activeMenuKey]"
        mode="inline"
        theme="dark"
        @click="onMenuClick"
      >
        <a-menu-item v-for="item in menuItems" :key="item.path">
          {{ item.label }}
        </a-menu-item>
      </a-menu>

      <div class="sider-actions">
        <a-button type="text" block @click="router.push('/chat')">知识库对话测试</a-button>
        <a-button type="text" danger block @click="handleLogout">退出登录</a-button>
      </div>
    </a-layout-sider>

    <a-layout-content class="admin-main">
      <router-view />
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const menuItems = [
  { path: '/admin', label: '数据概览', match: ['/admin'] },
  { path: '/admin/observability', label: '可信控制台', match: ['/admin/observability', '/admin/traces', '/admin/evals', '/admin/tool-calls'] },
  { path: '/admin/inbox', label: '客服工作台', match: ['/admin/inbox', '/admin/conversations'] },
  { path: '/admin/channels', label: '多渠道接入', match: ['/admin/channels'] },
  { path: '/admin/integrations', label: 'Shopify 集成', match: ['/admin/integrations'] },
  { path: '/admin/tickets', label: '工单与订单', match: ['/admin/tickets', '/admin/customers', '/admin/orders', '/admin/products'] },
  { path: '/admin/actions', label: '动作审批', match: ['/admin/actions'] },
  { path: '/admin/sla', label: 'SLA 管理', match: ['/admin/sla', '/admin/macros'] },
  { path: '/admin/qa', label: '客服质检', match: ['/admin/qa'] },
  { path: '/admin/operations', label: '运营指标', match: ['/admin/operations'] },
  { path: '/admin/knowledge', label: '知识库管理', match: ['/admin/knowledge'] },
  { path: '/admin/rag-workbench', label: 'RAG 证据工作台', match: ['/admin/rag-workbench'] },
  { path: '/admin/rag-safety', label: 'RAG 安全审核', match: ['/admin/rag-safety'] },
  { path: '/admin/agent-workflow', label: '多智能体工作流', match: ['/admin/agent-workflow'] },
  { path: '/admin/security', label: '生产边界', match: ['/admin/security'] },
  { path: '/admin/sre', label: '生产健康', match: ['/admin/sre'] },
  { path: '/admin/audit', label: '审计日志', match: ['/admin/audit'] },
  { path: '/admin/usage', label: '用量计费', match: ['/admin/usage'] },
]

const activeMenuKey = computed(() => {
  const matched = menuItems.find((item) => item.match.some((prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`)))
  return matched?.path || '/admin'
})

function onMenuClick({ key }: { key: string }) {
  router.push(key)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  background: #f4f6fa;
  min-height: 100vh;
}

.admin-sider {
  background: #101828;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-brand {
  align-items: center;
  cursor: pointer;
  display: flex;
  gap: 12px;
  padding: 24px 18px 30px;
}

.brand-mark {
  background: #1f7aff;
  border-radius: 8px;
  color: #fff;
  display: grid;
  font-size: 14px;
  font-weight: 800;
  height: 36px;
  place-items: center;
  width: 36px;
}

.admin-brand h1 {
  color: #fff;
  font-size: 16px;
  line-height: 1.2;
  margin: 0;
}

.admin-brand p {
  color: #98a2b3;
  font-size: 12px;
  margin: 3px 0 0;
}

.admin-menu {
  background: #101828;
  border-inline-end: none;
  flex: 1;
  overflow-y: auto;
  padding: 0 18px;
}

.admin-menu :deep(.ant-menu-item) {
  border-radius: 8px;
  color: #c7d0df;
  height: 38px;
  line-height: 38px;
  margin: 4px 0;
  padding-left: 12px !important;
}

.admin-menu :deep(.ant-menu-item-selected) {
  background: #17345f !important;
  color: #fff;
  font-weight: 650;
}

.admin-menu :deep(.ant-menu-item:not(.ant-menu-item-selected):hover) {
  background: rgba(255, 255, 255, 0.06) !important;
  color: #fff;
}

.sider-actions {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px 18px 18px;
}

.sider-actions :deep(.ant-btn) {
  color: #c7d0df;
  justify-content: flex-start;
  padding-left: 12px;
}

.admin-main {
  background: #f4f6fa;
  min-width: 0;
  overflow-y: auto;
  padding: 30px 32px;
}

@media (max-width: 900px) {
  .admin-sider {
    display: none;
  }

  .admin-main {
    padding: 20px 16px;
  }
}
</style>
