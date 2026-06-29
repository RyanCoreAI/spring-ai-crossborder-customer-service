<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">RAG 安全审核</h2>
        <p class="page-subtitle">审核命中提示注入、投毒、PII 或危险工具诱导规则的知识文档。</p>
      </div>
      <a-button :loading="loading" @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-card>
      <div class="toolbar">
        <a-select v-model:value="status" allow-clear placeholder="文档状态" style="width: 180px" @change="load">
          <a-select-option value="APPROVED">已通过</a-select-option>
          <a-select-option value="QUARANTINED">已隔离</a-select-option>
          <a-select-option value="REJECTED">已拒绝</a-select-option>
        </a-select>
        <a-select v-model:value="riskLevel" allow-clear placeholder="风险等级" style="width: 160px" @change="load">
          <a-select-option value="LOW">低风险</a-select-option>
          <a-select-option value="MEDIUM">中风险</a-select-option>
          <a-select-option value="HIGH">高风险</a-select-option>
        </a-select>
      </div>

      <a-table :columns="columns" :data-source="reviews" :loading="loading" row-key="docUuid" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'riskLevel'">
            <a-tag :color="riskColor(record.riskLevel)">{{ riskLabel(record.riskLevel) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'indexAllowed'">
            <a-tag :color="record.indexAllowed ? 'green' : 'red'">{{ record.indexAllowed ? '允许' : '禁止' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'sourceTrustLevel'">
            <a-tag :color="trustColor(record.sourceTrustLevel)">{{ trustLabel(record.sourceTrustLevel) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="approve(record)">通过</a-button>
              <a-button size="small" danger @click="reject(record)">拒绝</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const loading = ref(false)
const reviews = ref<any[]>([])
const status = ref<string | undefined>()
const riskLevel = ref<string | undefined>()

const columns = [
  { title: '文档 UUID', dataIndex: 'docUuid', ellipsis: true },
  { title: '来源可信度', dataIndex: 'sourceTrustLevel', key: 'sourceTrustLevel', width: 120 },
  { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
  { title: '索引权限', dataIndex: 'indexAllowed', key: 'indexAllowed', width: 100 },
  { title: '索引版本', dataIndex: 'indexVersion', width: 100 },
  { title: '风险规则', dataIndex: 'riskRules', ellipsis: true },
  { title: '脱敏片段', dataIndex: 'redactedExcerpt', ellipsis: true },
  { title: '操作', key: 'actions', width: 150, fixed: 'right' },
]

function riskColor(level: string) {
  if (level === 'HIGH') return 'red'
  if (level === 'MEDIUM') return 'gold'
  return 'green'
}

function riskLabel(level: string) {
  const labels: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return labels[level] || level || '-'
}

function statusColor(value: string) {
  if (value === 'APPROVED') return 'green'
  if (value === 'QUARANTINED') return 'orange'
  if (value === 'REJECTED') return 'red'
  return 'default'
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPROVED: '已通过',
    QUARANTINED: '已隔离',
    INDEXED: '已索引',
    REJECTED: '已拒绝',
  }
  return labels[value] || value || '-'
}

function trustColor(value: string) {
  if (value === 'HIGH' || value === 'TRUSTED') return 'green'
  if (value === 'LOW') return 'orange'
  if (value === 'UNTRUSTED') return 'red'
  return 'blue'
}

function trustLabel(value: string) {
  const labels: Record<string, string> = {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    TRUSTED: '可信',
    UNTRUSTED: '不可信',
  }
  return labels[value] || value || '—'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/rag/safety/docs', {
      params: { status: status.value, riskLevel: riskLevel.value, page: 1, size: 100 },
    })
    reviews.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

async function approve(row: any) {
  await api.post(`/rag/safety/docs/${row.docUuid}/approve`, { note: '从管理后台审核通过' })
  message.success('已允许索引')
  load()
}

async function reject(row: any) {
  await api.post(`/rag/safety/docs/${row.docUuid}/reject`, { note: '从管理后台拒绝索引' })
  message.success('已拒绝索引')
  load()
}

onMounted(load)
</script>
