<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">轨迹回放</h2>
        <p class="page-subtitle">按 Trace 回放意图识别、检索、工具调用、最终回答和失败归因。</p>
      </div>
      <a-button :loading="loading" @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-card>
      <div class="toolbar">
        <a-input
          v-model:value="filters.conversationUuid"
          allow-clear
          placeholder="会话 UUID"
          style="width: 260px"
        />
        <a-select v-model:value="filters.status" allow-clear placeholder="状态" style="width: 160px">
          <a-select-option value="SUCCESS">成功</a-select-option>
          <a-select-option value="FAILED">失败</a-select-option>
          <a-select-option value="RUNNING">运行中</a-select-option>
        </a-select>
        <a-button type="primary" @click="load">查询</a-button>
      </div>

      <a-table
        :columns="columns"
        :custom-row="traceRow"
        :data-source="traces"
        :loading="loading"
        row-key="traceId"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'totalLatencyMs'">
            {{ formatLatency(record.totalLatencyMs) }}
          </template>
          <template v-else-if="column.key === 'intent'">
            {{ intentLabel(record.intent) }}
          </template>
          <template v-else-if="column.key === 'failureCategory'">
            {{ failureCategoryLabel(record.failureCategory) }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="drawer" title="轨迹回放详情" width="64%">
      <a-descriptions v-if="detail.run" :column="2" bordered class="detail">
        <a-descriptions-item label="Trace">{{ detail.run.traceId }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusLabel(detail.run.status) }}</a-descriptions-item>
        <a-descriptions-item label="意图">{{ intentLabel(detail.run.intent) }}</a-descriptions-item>
        <a-descriptions-item label="模型">{{ detail.run.modelName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="失败分类">{{ failureCategoryLabel(detail.run.failureCategory) }}</a-descriptions-item>
        <a-descriptions-item label="总延迟">{{ formatLatency(detail.run.totalLatencyMs) }}</a-descriptions-item>
      </a-descriptions>

      <a-empty v-if="!detail.steps?.length" description="暂无轨迹步骤" />
      <a-timeline v-else class="timeline">
        <a-timeline-item v-for="step in detail.steps" :key="step.stepIndex">
          <div class="step-title">
            {{ step.stepIndex }}. {{ step.name || stepTypeLabel(step.stepType) }}
            <a-tag :color="statusColor(step.status)">{{ statusLabel(step.status) }}</a-tag>
          </div>
          <div v-if="step.inputSummary" class="step-text">输入摘要：{{ step.inputSummary }}</div>
          <div v-if="step.outputSummary" class="step-text">输出摘要：{{ step.outputSummary }}</div>
          <div class="step-meta">{{ formatLatency(step.latencyMs) }} {{ failureCategoryLabel(step.failureCategory) }}</div>
        </a-timeline-item>
      </a-timeline>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import { failureCategoryLabel, intentLabel, statusLabel as cnStatusLabel, stepTypeLabel } from '@/utils/display'

const route = useRoute()
const loading = ref(false)
const drawer = ref(false)
const traces = ref<any[]>([])
const detail = ref<any>({})
const filters = reactive({ conversationUuid: '', status: undefined as string | undefined })

const columns = [
  { title: 'Trace', dataIndex: 'traceId', ellipsis: true },
  { title: '会话', dataIndex: 'conversationUuid', ellipsis: true },
  { title: '意图', dataIndex: 'intent', key: 'intent', width: 150 },
  { title: '模型', dataIndex: 'modelName', width: 160 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '失败分类', dataIndex: 'failureCategory', key: 'failureCategory', width: 150 },
  { title: '工具次数', dataIndex: 'toolCallCount', width: 90 },
  { title: '总延迟', dataIndex: 'totalLatencyMs', key: 'totalLatencyMs', width: 110 },
]

function statusColor(status: string) {
  if (status === 'SUCCESS' || status === 'PASS') return 'green'
  if (status === 'FAILED' || status === 'FAIL') return 'red'
  if (status === 'RUNNING') return 'blue'
  return 'default'
}

function statusLabel(status: string) {
  return cnStatusLabel(status)
}

function formatLatency(value: any) {
  return value === null || value === undefined || value === '' ? '—' : `${value}ms`
}

function traceRow(record: any) {
  return {
    style: { cursor: 'pointer' },
    onClick: () => openTrace(record),
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/observability/traces', { params: { ...filters, page: 1, size: 50 } })
    traces.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

async function openTrace(row: any) {
  const res = await api.get(`/observability/traces/${row.traceId}`)
  detail.value = res.data || {}
  drawer.value = true
}

onMounted(async () => {
  await load()
  if (route.query.traceId) {
    await openTrace({ traceId: route.query.traceId })
  }
})
</script>

<style scoped>
.detail {
  margin-bottom: 16px;
}

.timeline {
  padding: 8px 4px;
}

.step-title {
  align-items: center;
  color: #1f2937;
  display: flex;
  font-weight: 600;
  gap: 8px;
  margin-bottom: 6px;
}

.step-text {
  color: #4b5563;
  line-height: 1.5;
  margin-bottom: 4px;
  word-break: break-word;
}

.step-meta {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
