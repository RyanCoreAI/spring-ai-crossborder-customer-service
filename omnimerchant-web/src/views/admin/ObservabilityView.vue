<template>
  <div class="trust-console">
    <div class="page-head console-head">
      <div>
        <h2 class="page-title">可信控制台</h2>
        <p class="page-subtitle">面向商家运营的 AI 安全、质量和成本总览；所有数值均来自后端观测接口。</p>
      </div>
      <a-space class="head-actions" :size="10">
        <a-select
          v-model:value="selectedTenantId"
          :loading="tenantLoading"
          placeholder="选择租户"
          style="width: 180px"
          @change="onTenantChange"
        >
          <a-select-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
            {{ tenantOptionLabel(tenant) }}
          </a-select-option>
        </a-select>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button type="primary" :loading="runningEval" @click="runEval">运行评测</a-button>
      </a-space>
    </div>

    <div class="metric-grid">
      <a-card v-for="metric in topMetrics" :key="metric.label" class="metric-card">
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-number">{{ metric.value }}</div>
        <div class="metric-note">{{ metric.note }}</div>
      </a-card>
    </div>

    <div class="content-grid">
      <a-card class="health-card">
        <template #title>
          <div class="section-title">
            <span>核心健康信号</span>
            <a-tag :color="overallHealth.color">{{ overallHealth.label }}</a-tag>
          </div>
        </template>

        <div class="signal-list">
          <button
            v-for="signal in healthSignals"
            :key="signal.name"
            class="signal-row"
            type="button"
            @click="goSignal(signal)"
          >
            <div class="signal-copy">
              <strong>{{ signal.name }}</strong>
              <span>{{ signal.description }}</span>
            </div>
            <div class="signal-bar" :class="{ empty: signal.value === null }">
              <span v-if="signal.value !== null" :style="{ width: `${signal.value}%` }"></span>
            </div>
            <div class="signal-value" :class="{ warn: signal.value !== null && signal.value < 95 }">
              {{ signal.value === null ? '—' : `${signal.value}%` }}
            </div>
          </button>
        </div>
      </a-card>

      <div class="side-stack">
        <a-card class="focus-card" title="待关注事项">
          <a-empty v-if="!focusItems.length" description="暂无后端返回的待处理事项" />
          <div v-for="item in focusItems" v-else :key="item.title" class="focus-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </div>
        </a-card>

        <a-card class="trace-card" title="最近轨迹">
          <a-empty v-if="!recentTraces.length" description="暂无轨迹数据" />
          <button
            v-for="trace in recentTraces"
            v-else
            :key="trace.key"
            class="trace-item"
            type="button"
            @click="openTrace(trace)"
          >
            <div>
              <strong>{{ trace.intent }}</strong>
              <span>{{ trace.meta }}</span>
            </div>
            <em>{{ trace.status }}</em>
          </button>
        </a-card>
      </div>
    </div>

    <a-row :gutter="[18, 18]" class="evidence-row">
      <a-col :xs="24" :xl="12">
        <a-card title="工具调用质量">
          <a-empty v-if="!toolMetrics.length" description="暂无工具调用指标" />
          <a-table
            v-else
            :columns="toolColumns"
            :data-source="toolMetrics"
            :pagination="false"
            row-key="toolName"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'toolName'">
                {{ toolLabel(record.toolName) }}
              </template>
              <template v-else-if="column.key === 'successRate'">
                {{ formatPercent(record.successRate) }}
              </template>
              <template v-else-if="column.key === 'p95LatencyMs'">
                {{ formatLatency(record.p95LatencyMs) }}
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="12">
        <a-card title="评测趋势">
          <a-empty v-if="!evalTrend.length" description="暂无评测趋势" />
          <a-table
            v-else
            :columns="trendColumns"
            :data-source="evalTrend"
            :pagination="false"
            row-key="runId"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="record.status === 'PASS' ? 'green' : 'red'">{{ statusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'passRate' || column.key === 'toolPrecision' || column.key === 'citationCoverage'">
                {{ formatPercent(record[column.dataIndex]) }}
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'
import { tenantOptionLabel, toolLabel } from '@/utils/display'

const router = useRouter()
const loading = ref(false)
const tenantLoading = ref(false)
const runningEval = ref(false)
const selectedTenantId = ref<number | null>(null)
const tenants = ref<any[]>([])
const summary = ref<any | null>(null)
const ragMetrics = ref<any | null>(null)
const traces = ref<any[]>([])
const failures = ref<any[]>([])
const toolMetrics = ref<any[]>([])
const evalTrend = ref<any[]>([])

const toolColumns = [
  { title: '工具', dataIndex: 'toolName', key: 'toolName' },
  { title: '调用次数', dataIndex: 'calls', width: 90 },
  { title: '失败次数', dataIndex: 'failures', width: 90 },
  { title: '成功率', dataIndex: 'successRate', key: 'successRate', width: 90 },
  { title: 'P95 耗时', dataIndex: 'p95LatencyMs', key: 'p95LatencyMs', width: 100 },
]

const trendColumns = [
  { title: '运行', dataIndex: 'runUuid', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '通过率', dataIndex: 'passRate', key: 'passRate', width: 90 },
  { title: '工具精确率', dataIndex: 'toolPrecision', key: 'toolPrecision', width: 110 },
  { title: '引用覆盖率', dataIndex: 'citationCoverage', key: 'citationCoverage', width: 110 },
]

const topMetrics = computed(() => [
  {
    label: '评测通过率',
    value: hasPositiveNumber(summary.value?.evalRuns) ? formatPercent(summary.value?.latestEvalPassRate) : '—',
    note: formatCount(summary.value?.evalRuns, '次评测运行'),
  },
  {
    label: '工具成功率',
    value: hasPositiveNumber(summary.value?.toolCalls) ? formatPercent(summary.value?.toolSuccessRate) : '—',
    note: formatCount(summary.value?.toolCalls, '次工具调用'),
  },
  {
    label: 'RAG 引用覆盖率',
    value: hasPositiveNumber(ragMetrics.value?.evalRuns) ? formatPercent(ragMetrics.value?.citationCoverage) : '—',
    note: hasPositiveNumber(ragMetrics.value?.evalRuns)
      ? `无依据结论率 ${formatPercent(ragMetrics.value?.unsupportedClaimRate)}`
      : '暂无评测样本',
  },
  {
    label: 'RAG 检索质量',
    value: hasPositiveNumber(ragMetrics.value?.evalRuns) ? formatPercent(ragMetrics.value?.recallAtK) : '—',
    note: hasPositiveNumber(ragMetrics.value?.evalRuns)
      ? `MRR ${formatDecimal(ragMetrics.value?.mrr)} · nDCG ${formatDecimal(ragMetrics.value?.ndcgAtK)} · P95 ${formatLatency(ragMetrics.value?.p95RetrievalLatencyMs)}`
      : '暂无 RAG 评测',
  },
  {
    label: '单解决会话成本',
    value: hasPositiveNumber(summary.value?.aiResolved) ? formatUsd(summary.value?.costPerResolvedConversation) : '—',
    note: `累计成本 ${formatUsd(summary.value?.estimatedCost)}`,
  },
])

const healthSignals = computed(() => [
  {
    name: '评测稳定性',
    description: '来自最新 deterministic eval 运行结果',
    value: hasPositiveNumber(summary.value?.evalRuns) ? percentNumber(summary.value?.latestEvalPassRate) : null,
    path: '/admin/evals',
  },
  {
    name: 'RAG 投毒拦截',
    description: '来自后端 eval 的 poisoning block rate',
    value: hasPositiveNumber(ragMetrics.value?.evalRuns) ? percentNumber(ragMetrics.value?.poisoningBlockRate) : null,
    path: '/admin/rag-safety',
  },
  {
    name: 'RAG 无答案准确率',
    description: '未知政策、过期政策和无证据问题应拒答或升级',
    value: hasPositiveNumber(ragMetrics.value?.evalRuns) ? percentNumber(ragMetrics.value?.noAnswerAccuracy) : null,
    path: '/admin/rag-workbench',
  },
  {
    name: '轨迹执行稳定性',
    description: '按 traces 与 failedTraces 计算',
    value: traceSuccessRate.value,
    path: '/admin/traces',
  },
])

const traceSuccessRate = computed(() => {
  const total = Number(summary.value?.traces)
  const failed = Number(summary.value?.failedTraces)
  if (!Number.isFinite(total) || total <= 0 || !Number.isFinite(failed)) return null
  return clampPercent(((total - failed) / total) * 100)
})

const overallHealth = computed(() => {
  const values = healthSignals.value.map((item) => item.value).filter((value) => value !== null) as number[]
  if (!values.length) return { label: '暂无数据', color: 'default' }
  const min = Math.min(...values)
  if (min >= 95) return { label: '整体健康', color: 'success' }
  if (min >= 85) return { label: '需要关注', color: 'warning' }
  return { label: '存在风险', color: 'error' }
})

const focusItems = computed(() => {
  const items: { title: string; description: string }[] = []
  const failedToolCalls = Number(summary.value?.failedToolCalls)
  const failedTraces = Number(summary.value?.failedTraces)
  const safetyBlocks = Number(summary.value?.safetyBlocks)
  const webhookBacklog = Number(summary.value?.shopifyWebhookBacklog)
  const fallbackRate = Number(summary.value?.fallbackRate)

  if (Number.isFinite(failedToolCalls) && failedToolCalls > 0) {
    items.push({
      title: `${failedToolCalls} 次工具调用失败`,
      description: summary.value?.topFailedTool ? `最常失败工具：${summary.value.topFailedTool}` : '后端未返回最常失败工具。',
    })
  }
  if (Number.isFinite(failedTraces) && failedTraces > 0) {
    items.push({ title: `${failedTraces} 条失败轨迹`, description: '可进入轨迹回放查看失败分类。' })
  }
  if (Number.isFinite(safetyBlocks) && safetyBlocks > 0) {
    items.push({ title: `${safetyBlocks} 次安全拦截`, description: '来自后端 safetyBlocks 计数。' })
  }
  if (Number.isFinite(webhookBacklog) && webhookBacklog > 0) {
    items.push({ title: `${webhookBacklog} 个 Shopify Webhook 积压`, description: '可在渠道集成页处理重试或重放。' })
  }
  if (Number.isFinite(fallbackRate) && fallbackRate > 0) {
    items.push({ title: `Fallback 率 ${formatPercent(fallbackRate)}`, description: '后端观测到降级响应。' })
  }
  if (failures.value.length) {
    const topFailure = failures.value[0]
    if (Number(topFailure.count) > 0) {
      items.push({
        title: `最高频失败分类：${topFailure.category}`,
        description: `${topFailure.count} 次，占比 ${formatPercent(topFailure.rate)}。`,
      })
    }
  }
  return items
})

const recentTraces = computed(() =>
  traces.value.map((trace, index) => ({
    key: trace.traceId || String(index),
    traceId: trace.traceId,
    intent: intentLabel(trace.intent),
    meta: traceMeta(trace),
    status: statusLabel(trace.status),
  }))
)

function hasValue(value: any) {
  return value !== null && value !== undefined && value !== ''
}

function hasPositiveNumber(value: any) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0
}

function formatPercent(value: any) {
  if (!hasValue(value)) return '—'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '—'
  return `${Math.round(parsed * 10) / 10}%`
}

function percentNumber(value: any) {
  if (!hasValue(value)) return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return null
  return clampPercent(parsed)
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, Math.round(value * 10) / 10))
}

function formatUsd(value: any) {
  if (!hasValue(value)) return '—'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '—'
  return `$${Math.round(parsed * 100) / 100}`
}

function formatDecimal(value: any) {
  if (!hasValue(value)) return '—'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '—'
  return `${Math.round(parsed * 1000) / 1000}`
}

function formatLatency(value: any) {
  if (!hasValue(value)) return '—'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '—'
  return `${Math.round(parsed)}ms`
}

function formatCount(value: any, suffix: string) {
  if (!hasValue(value)) return '暂无数据'
  return `${value} ${suffix}`
}

function intentLabel(intent: string) {
  const labels: Record<string, string> = {
    ORDER_STATUS: '订单查询',
    LOGISTICS: '物流追踪',
    RETURN_REFUND: '退货退款',
    PRODUCT_ADVICE: '商品推荐',
    POLICY_QA: '政策问答',
    COMPLAINT: '投诉处理',
    HUMAN_REQUEST: '人工升级',
  }
  return labels[intent] || intent || '未分类'
}

function statusLabel(status: string) {
  const labels: Record<string, string> = { PASS: '通过', FAIL: '失败', SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中' }
  return labels[status] || status || '-'
}

function traceMeta(trace: any) {
  const parts = []
  if (trace.modelName) parts.push(trace.modelName)
  if (hasValue(trace.totalLatencyMs)) parts.push(`${trace.totalLatencyMs}ms`)
  if (hasValue(trace.toolCallCount)) parts.push(`${trace.toolCallCount} 次工具`)
  return parts.length ? parts.join(' · ') : '后端未返回摘要'
}

function goSignal(signal: any) {
  router.push(signal.path)
}

function openTrace(trace: any) {
  if (trace.traceId) {
    router.push({ path: '/admin/traces', query: { traceId: trace.traceId } })
    return
  }
  router.push('/admin/traces')
}

function onTenantChange() {
  setStoredTenantId(selectedTenantId.value)
  load()
}

async function runEval() {
  runningEval.value = true
  try {
    await api.post('/evals/run', { mode: 'DETERMINISTIC', failOnThreshold: false })
    message.success('评测已完成')
    await load()
  } finally {
    runningEval.value = false
  }
}

async function loadTenants() {
  tenantLoading.value = true
  try {
    const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
    tenants.value = res.data?.records || []
    selectedTenantId.value = selectDefaultTenantId(tenants.value)
    setStoredTenantId(selectedTenantId.value)
  } finally {
    tenantLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [summaryRes, failureRes, traceRes, ragRes, toolRes, trendRes] = await Promise.all([
      api.get('/observability/summary'),
      api.get('/observability/failures'),
      api.get('/observability/traces', { params: { page: 1, size: 2 } }),
      api.get('/observability/rag'),
      api.get('/observability/tools'),
      api.get('/observability/eval-trend', { params: { limit: 8 } }),
    ])
    summary.value = summaryRes.data || null
    failures.value = failureRes.data || []
    traces.value = traceRes.data?.records || []
    ragMetrics.value = ragRes.data || null
    toolMetrics.value = toolRes.data || []
    evalTrend.value = trendRes.data || []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadTenants()
  await load()
})
</script>

<style scoped>
.trust-console {
  max-width: 1280px;
}

.console-head {
  align-items: flex-start;
  margin-bottom: 24px;
}

.head-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.metric-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 18px;
}

.metric-card :deep(.ant-card-body) {
  padding: 18px;
}

.metric-label {
  color: #4b5870;
  font-size: 13px;
  margin-bottom: 10px;
}

.metric-number {
  color: #07111f;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1.1;
}

.metric-note {
  color: #4b5870;
  font-size: 12px;
  margin-top: 10px;
}

.content-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.9fr);
  margin-bottom: 18px;
}

.evidence-row {
  margin-bottom: 18px;
}

.section-title {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.health-card :deep(.ant-card-body) {
  min-height: 330px;
  padding: 0 18px 18px;
}

.signal-list {
  display: grid;
  gap: 12px;
}

.signal-row {
  align-items: center;
  background: #fff;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(220px, 1fr) 110px 90px;
  padding: 13px 14px;
  text-align: left;
  transition: border-color 0.2s, box-shadow 0.2s;
  width: 100%;
}

.signal-row:hover {
  border-color: #b9d4ff;
  box-shadow: 0 8px 20px rgba(23, 105, 255, 0.08);
}

.signal-copy strong {
  color: #111827;
  display: block;
  font-size: 14px;
  font-weight: 750;
}

.signal-copy span {
  color: #667085;
  display: block;
  font-size: 12px;
  margin-top: 4px;
}

.signal-bar {
  background: #edf2f7;
  border-radius: 999px;
  height: 8px;
  overflow: hidden;
}

.signal-bar.empty {
  background: repeating-linear-gradient(90deg, #edf2f7, #edf2f7 8px, #f8fafc 8px, #f8fafc 16px);
}

.signal-bar span {
  background: #1769ff;
  display: block;
  height: 100%;
}

.signal-value {
  color: #067647;
  font-size: 13px;
  font-weight: 750;
  text-align: right;
}

.signal-value.warn {
  color: #b54708;
}

.side-stack {
  display: grid;
  gap: 18px;
}

.focus-card :deep(.ant-card-body),
.trace-card :deep(.ant-card-body) {
  padding: 18px;
}

.focus-item {
  border-left: 3px solid #1769ff;
  padding-left: 12px;
}

.focus-item + .focus-item {
  margin-top: 15px;
}

.focus-item strong {
  color: #111827;
  display: block;
  font-size: 14px;
  font-weight: 750;
  margin-bottom: 4px;
}

.focus-item span {
  color: #667085;
  font-size: 12px;
}

.trace-item {
  align-items: center;
  background: transparent;
  border: 0;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  text-align: left;
  width: 100%;
}

.trace-item + .trace-item {
  border-top: 1px solid #edf1f6;
}

.trace-item strong {
  color: #111827;
  display: block;
  font-size: 13px;
  font-weight: 750;
}

.trace-item span {
  color: #667085;
  display: block;
  font-size: 12px;
  margin-top: 3px;
}

.trace-item em {
  color: #667085;
  font-size: 12px;
  font-style: normal;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .signal-row {
    grid-template-columns: 1fr;
  }

  .signal-value {
    text-align: left;
  }
}
</style>
