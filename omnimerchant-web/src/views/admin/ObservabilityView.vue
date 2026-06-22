<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">可观测性</h2>
        <p class="page-subtitle">Agent 质量、成本、延迟、失败归因和 Shopify webhook 积压。</p>
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
        <el-card shadow="never" header="最新 Trace">
          <el-table :data="traces" size="small" stripe @row-click="goTrace">
            <el-table-column prop="traceId" label="Trace" min-width="150" show-overflow-tooltip />
            <el-table-column prop="intent" label="意图" width="130" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="totalLatencyMs" label="延迟" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
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

const metrics = computed(() => [
  { label: 'AI 解决率', value: `${summary.value.aiResolutionRate || 0}%` },
  { label: '升级率', value: `${summary.value.escalationRate || 0}%` },
  { label: '工具成功率', value: `${summary.value.toolSuccessRate || 0}%` },
  { label: 'Eval Pass', value: `${summary.value.latestEvalPassRate || 0}%` },
  { label: '安全拦截率', value: `${summary.value.safetyBlockRate || 0}%` },
  { label: 'RAG 引用覆盖', value: `${summary.value.ragCitationCoverage || 0}%` },
  { label: 'P95 首 token', value: `${summary.value.p95FirstTokenLatencyMs || 0}ms` },
  { label: 'Webhook backlog', value: String(summary.value.shopifyWebhookBacklog || 0) },
])

async function load() {
  loading.value = true
  try {
    const [s, f, t] = await Promise.all([
      api.get('/observability/summary'),
      api.get('/observability/failures'),
      api.get('/observability/traces', { params: { page: 1, size: 10 } }),
    ])
    summary.value = s.data || {}
    failures.value = f.data || []
    traces.value = t.data?.records || []
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
.metric-value { font-size: 24px; font-weight: 700; color: #303133; }
.metric-label { margin-top: 4px; color: #606266; font-size: 13px; }
</style>
