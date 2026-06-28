<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">Trust Console</h2>
        <p class="page-subtitle">Agent eval、RAG 安全、工具调用、成本、延迟和失败归因。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16" class="metrics">
      <el-col v-for="m in metrics" :key="m.label" :span="6">
        <el-card shadow="never">
          <div class="metric-value">{{ m.value }}</div>
          <div class="metric-label">{{ m.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="失败归因">
          <el-table :data="failures" size="small" stripe>
            <el-table-column prop="category" label="分类" />
            <el-table-column prop="count" label="次数" width="100" />
            <el-table-column prop="rate" label="占比" width="100">
              <template #default="{ row }">{{ row.rate }}%</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="最新 Trace Replay">
          <el-table :data="traces" size="small" stripe @row-click="goTrace">
            <el-table-column prop="traceId" label="Trace" min-width="150" show-overflow-tooltip />
            <el-table-column prop="intent" label="意图" width="130" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="totalLatencyMs" label="延迟" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :span="12">
        <el-card shadow="never" header="工具调用健康度">
          <el-table :data="tools" size="small" stripe>
            <el-table-column prop="toolName" label="工具" min-width="150" show-overflow-tooltip />
            <el-table-column prop="calls" label="调用" width="80" />
            <el-table-column prop="failures" label="失败" width="80" />
            <el-table-column prop="successRate" label="成功率" width="90">
              <template #default="{ row }">{{ row.successRate }}%</template>
            </el-table-column>
            <el-table-column prop="p95LatencyMs" label="P95" width="90">
              <template #default="{ row }">{{ row.p95LatencyMs || 0 }}ms</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="Eval 趋势">
          <el-table :data="evalTrend" size="small" stripe>
            <el-table-column prop="runUuid" label="Run" min-width="150" show-overflow-tooltip />
            <el-table-column prop="totalCases" label="Cases" width="80" />
            <el-table-column prop="passRate" label="Pass" width="90">
              <template #default="{ row }">{{ row.passRate }}%</template>
            </el-table-column>
            <el-table-column prop="toolPrecision" label="Precision" width="100">
              <template #default="{ row }">{{ row.toolPrecision }}%</template>
            </el-table-column>
            <el-table-column prop="poisoningBlockRate" label="Poison" width="90">
              <template #default="{ row }">{{ row.poisoningBlockRate }}%</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="section-row" header="RAG Safety Metrics">
      <div class="rag-grid">
        <div>
          <div class="metric-value">{{ rag.evalRuns || 0 }}</div>
          <div class="metric-label">Eval runs</div>
        </div>
        <div>
          <div class="metric-value">{{ rag.citationCoverage || 0 }}%</div>
          <div class="metric-label">Citation coverage</div>
        </div>
        <div>
          <div class="metric-value">{{ rag.retrievalPrecisionAtK || 0 }}%</div>
          <div class="metric-label">Retrieval precision@k</div>
        </div>
        <div>
          <div class="metric-value">{{ rag.unsupportedClaimRate || 0 }}%</div>
          <div class="metric-label">Unsupported claim rate</div>
        </div>
        <div>
          <div class="metric-value">{{ rag.poisoningBlockRate || 0 }}%</div>
          <div class="metric-label">Poisoning block rate</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const router = useRouter()
const loading = ref(false)
const summary = ref<any>({})
const failures = ref<any[]>([])
const traces = ref<any[]>([])
const tools = ref<any[]>([])
const evalTrend = ref<any[]>([])
const rag = ref<any>({})

const metrics = computed(() => [
  { label: 'AI 解决率', value: `${summary.value.aiResolutionRate || 0}%` },
  { label: '升级率', value: `${summary.value.escalationRate || 0}%` },
  { label: '工具成功率', value: `${summary.value.toolSuccessRate || 0}%` },
  { label: 'Eval Pass', value: `${summary.value.latestEvalPassRate || 0}%` },
  { label: '安全拦截率', value: `${summary.value.safetyBlockRate || 0}%` },
  { label: 'RAG 引用覆盖', value: `${summary.value.ragCitationCoverage || 0}%` },
  { label: 'Retrieval P@K', value: `${summary.value.retrievalPrecisionAtK || 0}%` },
  { label: 'Unsupported claims', value: `${summary.value.unsupportedClaimRate || 0}%` },
  { label: 'Poisoning block', value: `${summary.value.poisoningBlockRate || 0}%` },
  { label: 'P95 首 token', value: `${summary.value.p95FirstTokenLatencyMs || 0}ms` },
  { label: 'P95 工具延迟', value: `${summary.value.p95ToolLatencyMs || 0}ms` },
  { label: 'Resolved 成本', value: `$${summary.value.costPerResolvedConversation || 0}` },
  { label: 'Webhook backlog', value: String(summary.value.shopifyWebhookBacklog || 0) },
])

async function load() {
  loading.value = true
  try {
    const [s, f, t, toolStats, trend, ragStats] = await Promise.all([
      api.get('/observability/summary'),
      api.get('/observability/failures'),
      api.get('/observability/traces', { params: { page: 1, size: 10 } }),
      api.get('/observability/tools'),
      api.get('/observability/eval-trend', { params: { limit: 10 } }),
      api.get('/observability/rag'),
    ])
    summary.value = s.data || {}
    failures.value = f.data || []
    traces.value = t.data?.records || []
    tools.value = toolStats.data || []
    evalTrend.value = trend.data || []
    rag.value = ragStats.data || {}
  } finally {
    loading.value = false
  }
}

function goTrace(row: any) {
  router.push({ path: '/admin/traces', query: { traceId: row.traceId } })
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.page-subtitle { margin: 6px 0 0; color: #606266; font-size: 13px; }
.metrics { margin-bottom: 16px; }
.section-row { margin-top: 16px; }
.metric-value { font-size: 24px; font-weight: 700; color: #303133; }
.metric-label { margin-top: 4px; color: #606266; font-size: 13px; }
.rag-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
@media (max-width: 900px) {
  .rag-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
