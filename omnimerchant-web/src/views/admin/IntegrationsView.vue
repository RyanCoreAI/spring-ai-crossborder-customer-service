<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">渠道集成</h2>
        <p class="page-subtitle">管理 Shopify 连接、同步任务和 Webhook 死信重放，真实写操作仍走审批流。</p>
      </div>
      <a-tag color="blue">Shopify 优先</a-tag>
    </div>

    <a-card class="block">
      <a-form layout="vertical" class="form">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="租户">
              <a-select
                v-model:value="tenantId"
                show-search
                style="width: 100%"
                option-filter-prop="label"
                @change="saveTenant"
              >
                <a-select-option
                  v-for="tenant in tenants"
                  :key="tenant.id"
                  :value="tenant.id"
                  :label="`${tenant.storeName} (${tenant.tenantCode})`"
                >
                  {{ tenant.storeName }}（{{ tenant.tenantCode }}）
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="Shopify 店铺域名">
              <a-input v-model:value="form.shopDomain" placeholder="your-store.myshopify.com" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="Admin API Token（开发兜底）">
              <a-input-password v-model:value="form.adminApiToken" placeholder="shpat_..." />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="Webhook Secret">
              <a-input-password v-model:value="form.webhookSecret" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-space wrap>
          <a-button type="primary" :loading="connecting" @click="connect">保存 Custom App 凭证</a-button>
          <a-button :loading="installing" @click="installOauth">生成 OAuth 安装链接</a-button>
          <a-button :loading="syncing" @click="sync">同步商品、客户和订单</a-button>
        </a-space>
      </a-form>

      <a-alert
        class="notice"
        type="warning"
        show-icon
        message="退款、取消订单和改地址仍必须进入内部审批流；智能体不会直接写外部 Shopify。"
      />

      <a-descriptions v-if="lastResult" :column="1" bordered class="result">
        <a-descriptions-item label="状态">{{ lastResult.status || '-' }}</a-descriptions-item>
        <a-descriptions-item label="消息">{{ lastResult.message || '-' }}</a-descriptions-item>
        <a-descriptions-item v-if="lastResult.installUrl" label="OAuth 安装链接">
          <a :href="lastResult.installUrl" target="_blank" rel="noreferrer">打开安装链接</a>
        </a-descriptions-item>
        <a-descriptions-item v-if="lastResult.customers !== undefined" label="客户">
          {{ lastResult.customers }}
        </a-descriptions-item>
        <a-descriptions-item v-if="lastResult.orders !== undefined" label="订单">
          {{ lastResult.orders }}
        </a-descriptions-item>
        <a-descriptions-item v-if="lastResult.products !== undefined" label="商品">
          {{ lastResult.products }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <a-card title="同步任务">
          <a-table :columns="jobColumns" :data-source="jobs" :pagination="false" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-button size="small" @click="retryJob(record)">重试</a-button>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="12">
        <a-card title="Webhook 死信与重放">
          <a-table :columns="webhookColumns" :data-source="webhooks" :pagination="false" row-key="id" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'signatureValid'">
                <a-tag :color="record.signatureValid ? 'green' : 'red'">
                  {{ record.signatureValid ? '有效' : '无效' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-button size="small" @click="replay(record)">重放</a-button>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
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

const jobColumns = [
  { title: '资源', dataIndex: 'resource', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '尝试次数', dataIndex: 'attempts', width: 90 },
  { title: '导入数量', dataIndex: 'importedCount', width: 90 },
  { title: '最近错误', dataIndex: 'lastError', ellipsis: true },
  { title: '操作', key: 'actions', width: 90 },
]

const webhookColumns = [
  { title: '主题', dataIndex: 'topic', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '签名', dataIndex: 'signatureValid', key: 'signatureValid', width: 90 },
  { title: '处理次数', dataIndex: 'processAttempts', width: 90 },
  { title: '最近错误', dataIndex: 'lastError', ellipsis: true },
  { title: '操作', key: 'actions', width: 90 },
]

function statusColor(status: string) {
  if (['SUCCESS', 'COMPLETED'].includes(status)) return 'green'
  if (['FAILED', 'DEAD'].includes(status)) return 'red'
  if (['PROCESSING', 'RUNNING'].includes(status)) return 'blue'
  return 'default'
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    RECEIVED: '已接收',
    PROCESSING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
    DEAD: '死信',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    PENDING: '待处理',
  }
  return labels[status] || status || '-'
}

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
    const [jobRes, webhookRes] = await Promise.all([
      api.get('/integrations/shopify/jobs', { params: { page: 1, size: 50 } }),
      api.get('/integrations/shopify/webhooks', { params: { page: 1, size: 50 } }),
    ])
    jobs.value = jobRes.data?.records || []
    webhooks.value = webhookRes.data?.records || []
  } catch {
    jobs.value = []
    webhooks.value = []
  }
}

async function connect() {
  connecting.value = true
  try {
    const res = await api.post('/integrations/shopify/connect', form)
    lastResult.value = res.data
    message.success('Shopify 凭证已保存')
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
    message.success('同步任务已触发')
    loadOps()
  } finally {
    syncing.value = false
  }
}

async function retryJob(row: any) {
  await api.post(`/integrations/shopify/jobs/${row.id}/retry`)
  message.success('已排队重试')
  loadOps()
}

async function replay(row: any) {
  await api.post(`/integrations/shopify/webhooks/${row.id}/replay`)
  message.success('Webhook 已重放')
  loadOps()
}

onMounted(loadTenants)
</script>

<style scoped>
.form {
  max-width: 920px;
}

.notice,
.result {
  margin-top: 18px;
}

.block {
  margin-bottom: 16px;
}
</style>
