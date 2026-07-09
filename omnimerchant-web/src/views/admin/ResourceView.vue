<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ config.title }}</h2>
        <p class="page-subtitle">{{ config.subtitle }}</p>
      </div>
      <a-button @click="loadData">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-card class="toolbar-card" :bordered="false">
      <div class="toolbar">
        <a-select
          v-model:value="tenantId"
          show-search
          placeholder="选择租户"
          style="width: 260px"
          @change="onTenantChange"
        >
          <a-select-option v-for="t in tenants" :key="t.id" :value="t.id">
            {{ tenantOptionLabel(t) }}
          </a-select-option>
        </a-select>
        <a-input
          v-if="config.keyword"
          v-model:value="keyword"
          allow-clear
          :placeholder="config.keyword"
          style="width: 280px"
          @press-enter="loadData"
        />
        <a-select
          v-if="config.statusOptions"
          v-model:value="status"
          allow-clear
          placeholder="筛选状态"
          style="width: 160px"
          @change="loadData"
        >
          <a-select-option v-for="s in config.statusOptions" :key="String(s.value)" :value="s.value">
            {{ s.label }}
          </a-select-option>
        </a-select>
        <a-button type="primary" @click="loadData">查询</a-button>
        <a-button v-if="resource === 'products'" @click="reindexProducts">重建商品索引</a-button>
      </div>
    </a-card>

    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :custom-row="resourceRow"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1000 }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="assignTicket(record)">接管</a-button>
              <a-button size="small" type="primary" @click="resolveTicket(record)">解决</a-button>
            </a-space>
          </template>
          <template v-else-if="column.customTag">
            <a-tag :color="tagColor(cellValue(record, column), column.dataIndex)">
              {{ cellDisplay(record, column) }}
            </a-tag>
          </template>
          <template v-else>
            {{ cellDisplay(record, column) }}
          </template>
        </template>
      </a-table>
      <a-pagination
        v-model:current="page"
        class="pager"
        :page-size="size"
        :total="total"
        :show-size-changer="false"
        @change="loadData"
      />
    </a-card>

    <a-drawer v-model:open="detailOpen" :title="detailTitle" width="720px">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions :column="1" bordered size="small">
            <a-descriptions-item
              v-for="item in detailFields"
              :key="item.prop"
              :label="item.label"
            >
              <a-tag v-if="item.tag" :color="tagColor(detail[item.prop], item.prop)">
                {{ displayDetailValue(item.prop) }}
              </a-tag>
              <span v-else>{{ displayDetailValue(item.prop) }}</span>
            </a-descriptions-item>
          </a-descriptions>

          <template v-if="props.resource === 'orders'">
            <a-divider orientation="left">订单商品</a-divider>
            <a-table
              :columns="orderItemColumns"
              :data-source="orderItems"
              :pagination="false"
              row-key="sku"
              size="small"
            />
            <a-divider orientation="left">物流轨迹</a-divider>
            <a-timeline v-if="trackingItems.length">
              <a-timeline-item v-for="(item, index) in trackingItems" :key="index">
                <strong>{{ displayBusinessValue(item.status, 'trackingStatus') }}</strong>
                <div class="detail-muted">{{ item.time || '—' }} · {{ item.location || '—' }}</div>
                <div>{{ item.desc || item.description || '—' }}</div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无物流轨迹" />
          </template>

          <template v-if="props.resource === 'products'">
            <a-divider orientation="left">商品图片与索引</a-divider>
            <a-image v-if="detail.featuredImageUrl" :src="detail.featuredImageUrl" :width="220" />
            <a-alert
              class="detail-alert"
              :type="detail.vectorSynced ? 'success' : 'warning'"
              show-icon
              :message="detail.vectorSynced ? '商品已完成向量索引' : '商品尚未完成向量索引'"
            />
          </template>
        </template>
        <a-empty v-else description="暂无详情" />
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'
import { displayBusinessValue, tenantOptionLabel } from '@/utils/display'

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
      { label: 'AI 处理中', value: 1 },
      { label: '已完成', value: 2 },
      { label: '已升级人工', value: 3 },
      { label: '人工处理中', value: 4 },
      { label: '已关闭', value: 5 },
    ],
    columns: [
      { prop: 'customerName', label: '客户', width: 150 },
      { prop: 'customerEmail', label: '邮箱', width: 190 },
      { prop: 'intentPrimary', label: '意图', width: 140, tag: true },
      { prop: 'statusLabel', label: '状态', width: 120, tag: true },
      { prop: 'priority', label: '优先级', width: 90 },
      { prop: 'messageCount', label: '消息', width: 90 },
      { prop: 'totalCostUsd', label: '成本 USD', width: 110 },
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
      { label: '已支付', value: 'paid' },
      { label: '处理中', value: 'processing' },
      { label: '已发货', value: 'shipped' },
      { label: '已送达', value: 'delivered' },
      { label: '已退款', value: 'refunded' },
      { label: '已退货', value: 'returned' },
      { label: '已取消', value: 'cancelled' },
    ],
    columns: [
      { prop: 'externalOrderNumber', label: '订单号', width: 120 },
      { prop: 'customerEmail', label: '客户邮箱', width: 190 },
      { prop: 'orderStatus', label: '订单状态', width: 120, tag: true },
      { prop: 'fulfillmentStatus', label: '履约状态', width: 120, tag: true },
      { prop: 'totalAmount', label: '金额', width: 100 },
      { prop: 'trackingNumber', label: '物流号', width: 150 },
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
      { prop: 'title', label: '商品', width: 260 },
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
    subtitle: '独立 Helpdesk 工单，承接 AI 升级、人工接管、SLA、CSAT 和关闭原因。',
    endpoint: '/tickets',
    statusOptions: [
      { label: '待分配', value: 'OPEN' },
      { label: '处理中', value: 'ASSIGNED' },
      { label: '待客户回复', value: 'WAITING_CUSTOMER' },
      { label: '待审批', value: 'PENDING_APPROVAL' },
      { label: '已解决', value: 'RESOLVED' },
      { label: '已关闭', value: 'CLOSED' },
    ],
    columns: [
      { prop: 'ticketNo', label: '工单号', width: 210 },
      { prop: 'conversationUuid', label: '会话', width: 220 },
      { prop: 'subject', label: '主题', width: 220 },
      { prop: 'intent', label: '意图', width: 130, tag: true },
      { prop: 'priority', label: '优先级', width: 90 },
      { prop: 'statusLabel', label: '状态', width: 110, tag: true },
      { prop: 'slaState', label: 'SLA', width: 110, tag: true },
      { prop: 'assignedAgentId', label: '客服', width: 110 },
      { prop: 'createdAt', label: '创建时间', width: 190 },
    ],
  },
}

const config = computed(() => configs[props.resource] || configs.inbox)
const columns = computed(() => {
  const base = config.value.columns.map((col) => ({
    title: col.label,
    dataIndex: col.prop,
    key: col.prop,
    width: col.width,
    ellipsis: true,
    customTag: col.tag,
  }))
  if (props.resource === 'tickets') {
    base.push({ title: '操作', dataIndex: 'actions', key: 'actions', width: 150, ellipsis: false, customTag: false })
  }
  return base
})

const tenants = ref<any[]>([])
const tenantId = ref<number | null>(null)
const keyword = ref('')
const status = ref<any>(null)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any | null>(null)

const detailEndpoints: Record<string, string> = {
  customers: '/customers',
  orders: '/orders',
  products: '/products',
}

const detailTitle = computed(() => {
  if (!detail.value) return '详情'
  if (props.resource === 'orders') return `订单 ${detail.value.externalOrderNumber || detail.value.id}`
  if (props.resource === 'products') return detail.value.title || '商品详情'
  if (props.resource === 'customers') return detail.value.displayName || detail.value.email || '客户详情'
  return '详情'
})

const detailFields = computed(() => {
  const map: Record<string, Column[]> = {
    customers: [
      { prop: 'displayName', label: '客户' },
      { prop: 'email', label: '邮箱' },
      { prop: 'phone', label: '手机号' },
      { prop: 'countryCode', label: '国家' },
      { prop: 'languagePref', label: '语言' },
      { prop: 'customerTier', label: '等级', tag: true },
      { prop: 'totalOrders', label: '订单数' },
      { prop: 'totalSpent', label: '累计消费' },
      { prop: 'lastOrderAt', label: '最近下单' },
      { prop: 'isBlacklisted', label: '黑名单', tag: true },
    ],
    orders: [
      { prop: 'externalOrderNumber', label: '订单号' },
      { prop: 'customerEmail', label: '客户邮箱' },
      { prop: 'customerName', label: '客户姓名' },
      { prop: 'orderStatus', label: '订单状态', tag: true },
      { prop: 'paymentStatus', label: '支付状态', tag: true },
      { prop: 'fulfillmentStatus', label: '履约状态', tag: true },
      { prop: 'totalAmount', label: '订单金额' },
      { prop: 'refundedAmount', label: '已退款金额' },
      { prop: 'trackingNumber', label: '物流号' },
      { prop: 'trackingCarrier', label: '承运商' },
      { prop: 'trackingStatus', label: '物流状态', tag: true },
      { prop: 'estimatedDeliveryAt', label: '预计送达' },
      { prop: 'placedAt', label: '下单时间' },
    ],
    products: [
      { prop: 'title', label: '商品' },
      { prop: 'defaultSku', label: 'SKU' },
      { prop: 'brand', label: '品牌' },
      { prop: 'categoryL1', label: '一级类目' },
      { prop: 'categoryL2', label: '二级类目' },
      { prop: 'productType', label: '类型' },
      { prop: 'price', label: '价格' },
      { prop: 'totalStock', label: '库存' },
      { prop: 'stockStatus', label: '库存状态', tag: true },
      { prop: 'ratingAvg', label: '评分' },
      { prop: 'ratingCount', label: '评价数' },
      { prop: 'vectorSynced', label: '向量索引', tag: true },
      { prop: 'updatedAt', label: '更新时间' },
    ],
  }
  return map[props.resource] || []
})

const orderItemColumns = [
  { title: 'SKU', dataIndex: 'sku', width: 160 },
  { title: '商品', dataIndex: 'title', ellipsis: true },
  { title: '数量', dataIndex: 'quantity', width: 90 },
  { title: '价格', dataIndex: 'price', width: 90 },
]

const orderItems = computed(() => parseJsonList(detail.value?.orderItems))
const trackingItems = computed(() => parseJsonList(detail.value?.trackingHistory))

function cellValue(record: any, column: any) {
  return record[column.dataIndex]
}

function tagColor(value: any, prop: string) {
  const text = String(value)
  if (prop === 'isBlacklisted') return value ? 'error' : 'success'
  if (prop === 'stockStatus') return value === 'low_stock' ? 'warning' : value === 'out_of_stock' ? 'error' : 'success'
  if (prop === 'vectorSynced') return value ? 'success' : 'warning'
  if (text.includes('exception') || text.includes('cancelled')) return 'error'
  if (text.includes('delivered') || text.includes('resolved') || text.includes('完成')) return 'success'
  if (text.includes('processing') || text.includes('处理中')) return 'processing'
  return 'blue'
}

function cellDisplay(record: any, column: any) {
  return displayBusinessValue(cellValue(record, column), column.dataIndex)
}

function displayDetailValue(prop: string) {
  if (!detail.value) return '—'
  return displayBusinessValue(detail.value[prop], prop)
}

function parseJsonList(value: any) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(String(value))
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function resourceRow(record: any) {
  if (!detailEndpoints[props.resource]) return {}
  return {
    style: { cursor: 'pointer' },
    onClick: () => openDetail(record),
  }
}

async function openDetail(record: any) {
  const endpoint = detailEndpoints[props.resource]
  if (!endpoint || !record?.id) return
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await api.get(`${endpoint}/${record.id}`)
    detail.value = res.data || null
  } finally {
    detailLoading.value = false
  }
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
  const queued = res.data?.queued
  message.success(queued === null || queued === undefined ? '重建商品索引请求已提交' : `已标记 ${queued} 个商品待重建索引`)
  loadData()
}

async function assignTicket(row: any) {
  await api.post(`/tickets/${row.id}/assign`, { agentId: 1, note: '后台人工接管' })
  message.success('已接管工单')
  loadData()
}

async function resolveTicket(row: any) {
  const note = window.prompt('填写解决备注')
  if (note === null) return
  await api.post(`/tickets/${row.id}/resolve`, { actorId: 1, note })
  message.success('工单已解决')
  loadData()
}

onMounted(async () => {
  await loadTenants()
  await loadData()
})
</script>

<style scoped>
.toolbar-card {
  margin-bottom: 14px;
}

.detail-muted {
  color: #667085;
  font-size: 12px;
  margin: 3px 0;
}

.detail-alert {
  margin-top: 12px;
}
</style>
