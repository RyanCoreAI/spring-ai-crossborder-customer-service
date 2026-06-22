<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">渠道集成</h2>
      <el-tag type="info">Shopify-first</el-tag>
    </div>

    <el-card shadow="never" class="block">
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
          <el-button type="primary" :loading="connecting" @click="connect">保存 Custom App 凭证</el-button>
          <el-button :loading="installing" @click="installOauth">生成 OAuth 安装链接</el-button>
          <el-button :loading="syncing" @click="sync">同步商品/客户/订单</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="warning" show-icon :closable="false"
                title="退款、取消订单和改地址仍必须进入内部审批流；AI 不直接写外部 Shopify。" />
      <el-descriptions v-if="lastResult" :column="1" border class="result">
        <el-descriptions-item label="状态">{{ lastResult.status }}</el-descriptions-item>
        <el-descriptions-item label="消息">{{ lastResult.message }}</el-descriptions-item>
        <el-descriptions-item v-if="lastResult.installUrl" label="OAuth URL">
          <el-link :href="lastResult.installUrl" target="_blank">打开安装链接</el-link>
        </el-descriptions-item>
        <el-descriptions-item v-if="lastResult.customers !== undefined" label="客户">{{ lastResult.customers }}</el-descriptions-item>
        <el-descriptions-item v-if="lastResult.orders !== undefined" label="订单">{{ lastResult.orders }}</el-descriptions-item>
        <el-descriptions-item v-if="lastResult.products !== undefined" label="商品">{{ lastResult.products }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="Sync Jobs">
          <el-table :data="jobs" size="small" stripe>
            <el-table-column prop="resource" label="资源" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="attempts" label="次数" width="80" />
            <el-table-column prop="importedCount" label="导入" width="90" />
            <el-table-column prop="lastError" label="错误" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }"><el-button size="small" @click="retryJob(row)">重试</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="Webhook DLQ / Replay">
          <el-table :data="webhooks" size="small" stripe>
            <el-table-column prop="topic" label="Topic" min-width="160" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column prop="signatureValid" label="签名" width="80" />
            <el-table-column prop="processAttempts" label="次数" width="80" />
            <el-table-column prop="lastError" label="错误" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }"><el-button size="small" @click="replay(row)">Replay</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
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
const installing = ref(false)
const syncing = ref(false)
const lastResult = ref<any>(null)
const jobs = ref<any[]>([])
const webhooks = ref<any[]>([])
const form = reactive({ shopDomain: '', adminApiToken: '', webhookSecret: '' })

function saveTenant() {
  setStoredTenantId(tenantId.value)
  loadOps()
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  tenantId.value = selectDefaultTenantId(tenants.value)
  saveTenant()
}

async function loadOps() {
  try {
    const [j, w] = await Promise.all([
      api.get('/integrations/shopify/jobs', { params: { page: 1, size: 50 } }),
      api.get('/integrations/shopify/webhooks', { params: { page: 1, size: 50 } }),
    ])
    jobs.value = j.data?.records || []
    webhooks.value = w.data?.records || []
  } catch { /* optional until connector is configured */ }
}

async function connect() {
  connecting.value = true
  try {
    const res = await api.post('/integrations/shopify/connect', form)
    lastResult.value = res.data
    ElMessage.success('Shopify 凭证已保存')
    loadOps()
  } finally {
    connecting.value = false
  }
}

async function installOauth() {
  installing.value = true
  try {
    const res = await api.get('/integrations/shopify/install', { params: { shop: form.shopDomain } })
    lastResult.value = res.data
    if (res.data?.installUrl) window.open(res.data.installUrl, '_blank')
  } finally {
    installing.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const res = await api.post('/integrations/shopify/sync')
    lastResult.value = res.data
    ElMessage.success('同步完成')
    loadOps()
  } finally {
    syncing.value = false
  }
}

async function retryJob(row: any) {
  await api.post(`/integrations/shopify/jobs/${row.id}/retry`)
  ElMessage.success('已排队重试')
  loadOps()
}

async function replay(row: any) {
  await api.post(`/integrations/shopify/webhooks/${row.id}/replay`)
  ElMessage.success('已重放 webhook')
  loadOps()
}

onMounted(loadTenants)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.form { max-width: 820px; }
.result { margin-top: 18px; }
.block { margin-bottom: 16px; }
</style>
