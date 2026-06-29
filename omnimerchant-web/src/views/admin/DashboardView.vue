<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">数据概览</h2>
        <p class="page-subtitle">查看租户内客户、订单、会话、商品和客服质量状态。</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col v-for="stat in stats" :key="stat.label" :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" class="section-row">
      <a-col :xs="24" :lg="12">
        <a-card title="客服质量指标" :bordered="false">
          <a-table
            size="small"
            :columns="qualityColumns"
            :data-source="qualityData"
            :pagination="false"
            row-key="label"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="系统状态" :bordered="false">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="API 状态">
              <a-tag :color="healthStatus === 'UP' ? 'success' : 'error'">{{ healthStatus }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="租户数量">{{ tenantCount }}</a-descriptions-item>
            <a-descriptions-item label="知识文档">{{ knowledgeCount }}</a-descriptions-item>
            <a-descriptions-item label="今日会话">{{ todayConversations }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const healthStatus = ref('...')
const tenantCount = ref<string | number>('—')
const knowledgeCount = ref<string | number>('—')
const todayConversations = ref<string | number>('—')
const qualityData = ref<any[]>([])
const stats = ref<{ label: string; value: string | number }[]>([
  { label: '客户数', value: '—' },
  { label: '订单数', value: '—' },
  { label: '会话数', value: '—' },
  { label: '商品数', value: '—' },
])

const qualityColumns = [
  { title: '指标', dataIndex: 'label' },
  { title: '当前值', dataIndex: 'value' },
]

async function load() {
  try {
    const h = await api.get('/health')
    healthStatus.value = h.data?.status || 'UP'
  } catch {
    healthStatus.value = 'DOWN'
  }

  try {
    const t = await api.get('/tenants', { params: { page: 1, size: 100 } })
    tenantCount.value = formatBackendValue(t.data?.total)
    const defaultTenantId = selectDefaultTenantId(t.data?.records || [])
    if (defaultTenantId) setStoredTenantId(defaultTenantId)
  } catch { /* optional */ }

  try {
    const k = await api.get('/knowledge/docs', { params: { page: 1, size: 1 } })
    knowledgeCount.value = formatBackendValue(k.data?.total)
  } catch { /* optional */ }

  try {
    const c = await api.get('/conversations', { params: { page: 1, size: 1 } })
    todayConversations.value = formatBackendValue(c.data?.total)
  } catch { /* optional */ }

  try {
    const d = await api.get('/dashboard/commerce')
    const data = d.data || {}
    stats.value = [
      { label: '客户数', value: formatBackendValue(data.customers) },
      { label: '订单数', value: formatBackendValue(data.orders) },
      { label: '会话数', value: formatBackendValue(data.conversations) },
      { label: '商品数', value: formatBackendValue(data.products) },
    ]
    qualityData.value = [
      { label: 'AI 解决率', value: formatPercent(data.aiResolutionRate) },
      { label: '升级率', value: formatPercent(data.escalationRate) },
      { label: '工具成功率', value: formatPercent(data.toolSuccessRate) },
      { label: '待处理工单', value: formatBackendValue(data.openTickets) },
      { label: '待审批退货/动作', value: formatBackendValue(data.pendingReturns) },
    ]
  } catch {
    stats.value = stats.value.map((item) => ({ ...item, value: '—' }))
    qualityData.value = []
  }
}

function hasValue(value: any) {
  return value !== null && value !== undefined && value !== ''
}

function formatBackendValue(value: any) {
  return hasValue(value) ? value : '—'
}

function formatPercent(value: any) {
  return hasValue(value) ? `${value}%` : '—'
}

onMounted(load)
</script>

<style scoped>
.section-row {
  margin-top: 16px;
}
</style>
