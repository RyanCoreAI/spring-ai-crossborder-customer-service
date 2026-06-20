<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">渠道集成</h2>
      <el-tag type="info">Shopify-first</el-tag>
    </div>
    <el-card shadow="never">
      <el-form label-width="150px" class="form">
        <el-form-item label="租户">
          <el-select v-model="tenantId" filterable style="width:320px" @change="saveTenant">
            <el-option v-for="t in tenants" :key="t.id" :label="`${t.storeName} (${t.tenantCode})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Shopify Shop Domain">
          <el-input v-model="form.shopDomain" placeholder="your-store.myshopify.com" />
        </el-form-item>
        <el-form-item label="Admin API Token">
          <el-input v-model="form.adminApiToken" type="password" show-password placeholder="shpat_..." />
        </el-form-item>
        <el-form-item label="Webhook Secret">
          <el-input v-model="form.webhookSecret" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="connecting" @click="connect">保存加密凭证</el-button>
          <el-button :loading="syncing" @click="sync">同步商品/客户/订单</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="warning" show-icon :closable="false"
                title="v1 不让 AI 直接执行退款、取消订单或改地址；这些动作只创建内部审批请求。" />
      <el-descriptions v-if="lastResult" :column="1" border class="result">
        <el-descriptions-item label="状态">{{ lastResult.status }}</el-descriptions-item>
        <el-descriptions-item label="消息">{{ lastResult.message }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ lastResult.customers }}</el-descriptions-item>
        <el-descriptions-item label="订单">{{ lastResult.orders }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ lastResult.products }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const tenants = ref<any[]>([])
const tenantId = ref<number | null>(null)
const connecting = ref(false)
const syncing = ref(false)
const lastResult = ref<any>(null)
const form = reactive({ shopDomain: '', adminApiToken: '', webhookSecret: '' })

function saveTenant() {
  setStoredTenantId(tenantId.value)
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  tenantId.value = selectDefaultTenantId(tenants.value)
  saveTenant()
}

async function connect() {
  connecting.value = true
  try {
    const res = await api.post('/integrations/shopify/connect', form)
    lastResult.value = res.data
    ElMessage.success('Shopify 凭证已保存')
  } finally {
    connecting.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const res = await api.post('/integrations/shopify/sync')
    lastResult.value = res.data
    ElMessage.success('同步完成')
  } finally {
    syncing.value = false
  }
}

onMounted(loadTenants)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.form { max-width: 760px; }
.result { margin-top: 18px; }
</style>
