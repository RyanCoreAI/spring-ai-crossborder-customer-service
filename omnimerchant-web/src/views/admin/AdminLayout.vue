<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <h2>OmniMerchant</h2>
        <p>管理后台</p>
      </div>
      <el-menu :default-active="currentRoute" router :default-openeds="['/']" background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/inbox">
          <el-icon><MessageBox /></el-icon>
          <span>客服工作台</span>
        </el-menu-item>
        <el-menu-item index="/admin/tenants">
          <el-icon><Shop /></el-icon>
          <span>租户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/customers">
          <el-icon><User /></el-icon>
          <span>客户</span>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单</span>
        </el-menu-item>
        <el-menu-item index="/admin/products">
          <el-icon><Goods /></el-icon>
          <span>商品</span>
        </el-menu-item>
        <el-menu-item index="/admin/knowledge">
          <el-icon><Document /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/admin/conversations">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话回放</span>
        </el-menu-item>
        <el-menu-item index="/admin/tickets">
          <el-icon><Warning /></el-icon>
          <span>人工工单</span>
        </el-menu-item>
        <el-menu-item index="/admin/integrations">
          <el-icon><Connection /></el-icon>
          <span>渠道集成</span>
        </el-menu-item>
        <el-menu-item index="/admin/usage">
          <el-icon><Coin /></el-icon>
          <span>用量计费</span>
        </el-menu-item>
        <el-menu-item index="/admin/observability">
          <el-icon><Monitor /></el-icon>
          <span>观测</span>
        </el-menu-item>
        <el-menu-item index="/admin/traces">
          <el-icon><Share /></el-icon>
          <span>轨迹</span>
        </el-menu-item>
        <el-menu-item index="/admin/rag-safety">
          <el-icon><Lock /></el-icon>
          <span>RAG 安全</span>
        </el-menu-item>
        <el-menu-item index="/admin/evals">
          <el-icon><Checked /></el-icon>
          <span>评测</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-bottom">
        <el-button text style="color:#bfcbd9" @click="$router.push('/chat')" :icon="Promotion">前往对话页</el-button>
        <el-button text style="color:#bfcbd9" @click="handleLogout" :icon="SwitchButton">退出登录</el-button>
      </div>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  Shop,
  Document,
  ChatDotRound,
  Promotion,
  SwitchButton,
  MessageBox,
  User,
  Tickets,
  Goods,
  Warning,
  Connection,
  Coin,
  Checked,
  Monitor,
  Share,
  Lock,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const currentRoute = computed(() => route.path)

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
}
.admin-sidebar {
  width: 220px;
  background: #304156;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.admin-brand {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.admin-brand h2 {
  color: #fff;
  font-size: 18px;
}
.admin-brand p {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.admin-sidebar .el-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}
.sidebar-bottom {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px solid rgba(255,255,255,0.1);
}
.admin-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: #f5f7fa;
  min-width: 0;
}
</style>
