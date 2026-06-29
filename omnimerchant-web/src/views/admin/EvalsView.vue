<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">智能体评测</h2>
        <p class="page-subtitle">管理基准用例、工具选择准确率、RAG 引用一致性和投毒安全回归。</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" :loading="running" @click="runEvals">运行确定性评测</a-button>
      </a-space>
    </div>

    <a-alert
      v-if="runReport"
      class="run-alert"
      show-icon
      type="success"
      :message="`评测完成：${runReport.passed}/${runReport.total} 通过，通过率 ${runReport.passRate}%`"
    />

    <a-row :gutter="[16, 16]" class="summary">
      <a-col v-for="item in summaryCards" :key="item.label" :xs="12" :md="8" :xl="4">
        <a-card>
          <a-statistic :title="item.label" :value="item.value" :suffix="item.suffix" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="最近运行" class="block">
      <a-table :columns="runColumns" :data-source="runs" row-key="id" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'PASS' || record.status === 'SUCCESS' ? 'green' : 'red'">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="metricColumnKeys.includes(String(column.key))">
            {{ metricPercent(record, column) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button size="small" @click="openRun(record)">详情</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="基准用例" class="block">
      <a-table :columns="caseColumns" :data-source="cases" :loading="loading" row-key="caseCode" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="drawer" title="评测运行详情" width="72%">
      <a-table :columns="resultColumns" :data-source="runDetail.results || []" row-key="id" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'PASS' ? 'green' : 'red'">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'trace'">
            <a-button v-if="record.traceId" size="small" @click="goTrace(record.traceId)">打开</a-button>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const router = useRouter()
const loading = ref(false)
const running = ref(false)
const summary = ref<any>({})
const cases = ref<any[]>([])
const runs = ref<any[]>([])
const runReport = ref<any>(null)
const drawer = ref(false)
const runDetail = ref<any>({})
const latestRun = computed(() => runs.value[0] || {})

const summaryCards = computed(() => [
  { label: '总用例', value: displayValue(summary.value.totalCases) },
  { label: '启用用例', value: displayValue(summary.value.enabledCases) },
  { label: '最新通过率', value: displayValue(latestRun.value.passRate), suffix: hasValue(latestRun.value.passRate) ? '%' : undefined },
  { label: '投毒拦截率', value: displayValue(latestRun.value.poisoningBlockRate), suffix: hasValue(latestRun.value.poisoningBlockRate) ? '%' : undefined },
  { label: '检索准确率@K', value: displayValue(latestRun.value.retrievalPrecisionAtK), suffix: hasValue(latestRun.value.retrievalPrecisionAtK) ? '%' : undefined },
  { label: '无依据结论率', value: displayValue(latestRun.value.unsupportedClaimRate), suffix: hasValue(latestRun.value.unsupportedClaimRate) ? '%' : undefined },
])

const metricColumnKeys = ['passRate', 'toolPrecision', 'toolRecall', 'citationCoverage', 'retrievalPrecisionAtK', 'unsupportedClaimRate', 'poisoningBlockRate']

const runColumns = [
  { title: '运行编号', dataIndex: 'runUuid', ellipsis: true },
  { title: '模式', dataIndex: 'runMode', width: 130 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '通过率', dataIndex: 'passRate', key: 'passRate', width: 90 },
  { title: '工具精确率', dataIndex: 'toolPrecision', key: 'toolPrecision', width: 110 },
  { title: '工具召回率', dataIndex: 'toolRecall', key: 'toolRecall', width: 110 },
  { title: '引用覆盖率', dataIndex: 'citationCoverage', key: 'citationCoverage', width: 110 },
  { title: '检索@K', dataIndex: 'retrievalPrecisionAtK', key: 'retrievalPrecisionAtK', width: 100 },
  { title: '无依据率', dataIndex: 'unsupportedClaimRate', key: 'unsupportedClaimRate', width: 100 },
  { title: '投毒拦截', dataIndex: 'poisoningBlockRate', key: 'poisoningBlockRate', width: 110 },
  { title: '开始时间', dataIndex: 'startedAt', width: 180 },
  { title: '操作', key: 'actions', width: 90 },
]

const caseColumns = [
  { title: '用例编号', dataIndex: 'caseCode', width: 150 },
  { title: '意图', dataIndex: 'intent', width: 160 },
  { title: '用户消息', dataIndex: 'userMessage', ellipsis: true },
  { title: '期望工具', dataIndex: 'expectedTools', ellipsis: true },
  { title: '攻击类型', dataIndex: 'attackType', width: 150 },
  { title: '启用', dataIndex: 'enabled', key: 'enabled', width: 90 },
]

const resultColumns = [
  { title: '用例编号', dataIndex: 'caseCode', width: 150 },
  { title: '意图', dataIndex: 'intent', width: 140 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '工具精确率', dataIndex: 'toolPrecision', width: 100 },
  { title: '工具召回率', dataIndex: 'toolRecall', width: 100 },
  { title: '观测摘要', dataIndex: 'actualObservation', ellipsis: true },
  { title: '轨迹', key: 'trace', width: 90 },
]

function statusLabel(status: string) {
  const labels: Record<string, string> = { PASS: '通过', SUCCESS: '成功', FAIL: '失败', FAILED: '失败', RUNNING: '运行中' }
  return labels[status] || status || '-'
}

function metricValue(record: any, column: any) {
  return displayValue(record[column.dataIndex])
}

function metricPercent(record: any, column: any) {
  const value = metricValue(record, column)
  return value === '—' ? value : `${value}%`
}

function hasValue(value: any) {
  return value !== null && value !== undefined && value !== ''
}

function displayValue(value: any) {
  return hasValue(value) ? value : '—'
}

async function load() {
  loading.value = true
  try {
    const [evals, runList] = await Promise.all([
      api.get('/evals', { params: { page: 1, size: 100 } }),
      api.get('/evals/runs', { params: { page: 1, size: 20 } }),
    ])
    summary.value = evals.data || {}
    cases.value = evals.data?.cases?.records || []
    runs.value = runList.data?.records || []
  } finally {
    loading.value = false
  }
}

async function runEvals() {
  running.value = true
  try {
    const res = await api.post('/evals/run', { mode: 'DETERMINISTIC', failOnThreshold: false })
    runReport.value = res.data
    await load()
  } finally {
    running.value = false
  }
}

async function openRun(row: any) {
  const res = await api.get(`/evals/runs/${row.id}`)
  runDetail.value = res.data || {}
  drawer.value = true
}

function goTrace(traceId: string) {
  router.push({ path: '/admin/traces', query: { traceId } })
}

onMounted(load)
</script>

<style scoped>
.run-alert,
.summary,
.block {
  margin-bottom: 16px;
}
</style>
