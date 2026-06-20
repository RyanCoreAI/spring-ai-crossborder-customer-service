<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ config.title }}</h2>
        <p class="page-subtitle">{{ config.subtitle }}</p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="tenantId" placeholder="租户" filterable style="width:260px" @change="onTenantChange">
          <el-option v-for="t in tenants" :key="t.id" :label="`${t.storeName} (${t.tenantCode})`" :value="t.id" />
        </el-select>
        <el-input v-if="config.keyword" v-model="keyword" clearable :prefix-icon="Search"
                  :placeholder="config.keyword" style="width:280px" @keyup.enter="loadData" />
        <el-select v-if="config.statusOptions" v-model="status" clearable placeholder="状态" style="width:160px" @change="loadData">
          <el-option v-for="s in config.statusOptions" :key="String(s.value)" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button v-if="resource === 'products'" :icon="RefreshRight" @click="reindexProducts">重建商品索引</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column v-for="col in config.columns" :key="col.prop" :prop="col.prop" :label="col.label"
                         :min-width="col.width || 120">
          <template #default="{ row }">
            <el-tag v-if="col.tag" :type="tagType(row[col.prop], col.prop)" size="small">{{ display(row[col.prop]) }}</el-tag>
            <span v-else>{{ display(row[col.prop]) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="resource === 'tickets'" label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="assignTicket(row)">接管</el-button>
            <el-button size="small" type="success" @click="resolveTicket(row)">解决</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" @current-change="loadData"
                     class="pager" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const props = defineProps<{ resource: string }>()

type Column = { prop: string; label: string; width?: number; tag?: boolean }
type Config = {
  title: string
  subtitle: string
  endpoint: string
  keyword?: string
  statusOptions?: { label: string; value: string | number }[]
  columns: Column[]
}

const configs: Record<string, Config> = {
  inbox: {
    title: '客服工作台',
    subtitle: '按会话状态、意图和客户信息扫描 AI 与人工接管队列。',
    endpoint: '/conversations',
    statusOptions: [
      { label: 'AI处理中', value: 1 },
      { label: '已完成', value: 2 },
      { label: '已升级人工', value: 3 },
      { label: '人工处理中', value: 4 },
      { label: '已关闭', value: 5 },
    ],
    columns: [
      { prop: 'customerName', label: '客户', width: 150 },
      { prop: 'customerEmail', label: '邮箱', width: 180 },
      { prop: 'intentPrimary', label: '意图', width: 140, tag: true },
      { prop: 'statusLabel', label: '状态', width: 120, tag: true },
      { prop: 'priority', label: '优先级', width: 90 },
      { prop: 'messageCount', label: '消息', width: 90 },
      { prop: 'totalCostUsd', label: '成本USD', width: 110 },
      { prop: 'startedAt', label: '开始时间', width: 190 },
    ],
  },
  customers: {
    title: '客户',
    subtitle: '跨店铺隔离的买家画像、价值分层和黑名单状态。',
    endpoint: '/customers',
    keyword: '搜索邮箱、姓名或手机号',
    columns: [
      { prop: 'displayName', label: '客户', width: 150 },
      { prop: 'email', label: '邮箱', width: 190 },
      { prop: 'countryCode', label: '国家', width: 90 },
      { prop: 'languagePref', label: '语言', width: 90 },
      { prop: 'customerTier', label: '等级', width: 100, tag: true },
      { prop: 'totalOrders', label: '订单数', width: 90 },
      { prop: 'totalSpent', label: '消费额', width: 120 },
      { prop: 'isBlacklisted', label: '黑名单', width: 100, tag: true },
    ],
  },
  orders: {
    title: '订单',
    subtitle: '订单缓存、物流轨迹、退款状态和会话关联查询。',
    endpoint: '/orders',
    keyword: '搜索订单号、邮箱或物流号',
    statusOptions: [
      { label: 'paid', value: 'paid' },
      { label: 'processing', value: 'processing' },
      { label: 'shipped', value: 'shipped' },
      { label: 'delivered', value: 'delivered' },
      { label: 'refunded', value: 'refunded' },
      { label: 'returned', value: 'returned' },
      { label: 'cancelled', value: 'cancelled' },
    ],
    columns: [
      { prop: 'externalOrderNumber', label: '订单号', width: 110 },
      { prop: 'customerEmail', label: '客户邮箱', width: 190 },
      { prop: 'orderStatus', label: '订单状态', width: 120, tag: true },
      { prop: 'fulfillmentStatus', label: '履约', width: 120, tag: true },
      { prop: 'totalAmount', label: '金额', width: 100 },
      { prop: 'trackingNumber', label: '物流号', width: 140 },
      { prop: 'trackingStatus', label: '物流状态', width: 130, tag: true },
      { prop: 'estimatedDeliveryAt', label: '预计送达', width: 190 },
    ],
  },
  products: {
    title: '商品',
    subtitle: '商品缓存、库存状态和向量索引准备状态。',
    endpoint: '/products',
    keyword: '搜索商品、SKU 或标签',
    columns: [
      { prop: 'title', label: '商品', width: 240 },
      { prop: 'defaultSku', label: 'SKU', width: 150 },
      { prop: 'categoryL1', label: '一级类目', width: 110 },
      { prop: 'productType', label: '类型', width: 120 },
      { prop: 'price', label: '价格', width: 90 },
      { prop: 'totalStock', label: '库存', width: 90 },
      { prop: 'stockStatus', label: '库存状态', width: 120, tag: true },
      { prop: 'vectorSynced', label: '向量索引', width: 110, tag: true },
    ],
  },
  tickets: {
    title: '人工工单',
    subtitle: 'AI 升级、退款/补发/地址变更审批和 SLA 队列。',
    endpoint: '/escalations',
    statusOptions: [
      { label: '待分配', value: 1 },
      { label: '待响应', value: 2 },
      { label: '处理中', value: 3 },
      { label: '已解决', value: 4 },
      { label: '已关闭', value: 5 },
    ],
    columns: [
      { prop: 'ticketNo', label: '工单号', width: 200 },
      { prop: 'conversationUuid', label: '会话', width: 220 },
      { prop: 'escalationReason', label: '原因', width: 180, tag: true },
      { prop: 'priority', label: '优先级', width: 90 },
      { prop: 'status', label: '状态', width: 100, tag: true },
      { prop: 'assignedAgentId', label: '客服', width: 110 },
      { prop: 'createdAt', label: '创建时间', width: 190 },
    ],
  },
}

const config = computed(() => configs[props.resource] || configs.inbox)
const tenants = ref<any[]>([])
const tenantId = ref<number | null>(null)
const keyword = ref('')
const status = ref<any>(null)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

function display(value: any) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string' && value.includes('T')) return new Date(value).toLocaleString('zh-CN')
  return value
}

function tagType(value: any, prop: string) {
  if (prop === 'isBlacklisted') return value ? 'danger' : 'success'
  if (prop === 'stockStatus') return value === 'low_stock' ? 'warning' : value === 'out_of_stock' ? 'danger' : 'success'
  if (prop === 'vectorSynced') return value ? 'success' : 'warning'
  if (String(value).includes('exception') || String(value).includes('cancelled')) return 'danger'
  if (String(value).includes('delivered') || String(value).includes('resolved')) return 'success'
  return 'info'
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  tenantId.value = selectDefaultTenantId(tenants.value)
  setStoredTenantId(tenantId.value)
}

function onTenantChange() {
  setStoredTenantId(tenantId.value)
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (keyword.value) params.keyword = keyword.value
    if (status.value !== null && status.value !== undefined && status.value !== '') params.status = status.value
    const res = await api.get(config.value.endpoint, { params })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function reindexProducts() {
  const res = await api.post('/products/reindex')
  ElMessage.success(`已标记 ${res.data?.queued || 0} 个商品待重建索引`)
  loadData()
}

async function assignTicket(row: any) {
  await api.put(`/escalations/${row.id}/assign`, { agentId: 1 })
  ElMessage.success('已接管工单')
  loadData()
}

async function resolveTicket(row: any) {
  const { value } = await ElMessageBox.prompt('填写解决备注', '解决工单', {
    confirmButtonText: '解决',
    cancelButtonText: '取消',
    inputType: 'textarea',
  })
  await api.put(`/escalations/${row.id}/resolve`, { resolution: 'RESOLVED', note: value })
  ElMessage.success('工单已解决')
  loadData()
}

onMounted(async () => {
  await loadTenants()
  await loadData()
})
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
.page-subtitle {
  margin: 6px 0 0;
  color: #606266;
  font-size: 13px;
}
.toolbar-card {
  margin-bottom: 14px;
}
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
