<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">Agent 评测</h2>
        <p class="page-subtitle">Golden conversations、工具选择准确率、RAG 引用和 poisoning 安全回归。</p>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :loading="running" @click="runEvals">运行 deterministic eval</el-button>
      </div>
    </div>

    <el-alert v-if="runReport" type="success" show-icon :closable="false" class="run-alert"
              :title="`评测完成：${runReport.passed}/${runReport.total} 通过，Pass Rate ${runReport.passRate}%`" />

    <el-row :gutter="16" class="summary">
      <el-col :span="4"><el-card shadow="never"><b>{{ summary.totalCases || 0 }}</b><span>总用例</span></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><b>{{ summary.enabledCases || 0 }}</b><span>启用用例</span></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><b>{{ latestRun?.passRate || 0 }}%</b><span>最新 Pass</span></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><b>{{ latestRun?.poisoningBlockRate || 0 }}%</b><span>Poisoning 拦截</span></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><b>{{ latestRun?.retrievalPrecisionAtK || 0 }}%</b><span>Retrieval P@K</span></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><b>{{ latestRun?.unsupportedClaimRate || 0 }}%</b><span>Unsupported claims</span></el-card></el-col>
    </el-row>

    <el-card shadow="never" header="最近运行" class="block">
      <el-table :data="runs" stripe>
        <el-table-column prop="runUuid" label="Run" min-width="180" show-overflow-tooltip />
        <el-table-column prop="runMode" label="模式" width="130" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="passRate" label="Pass" width="90">
          <template #default="{ row }">{{ row.passRate }}%</template>
        </el-table-column>
        <el-table-column prop="toolPrecision" label="Precision" width="110" />
        <el-table-column prop="toolRecall" label="Recall" width="100" />
        <el-table-column prop="citationCoverage" label="Citation" width="100" />
        <el-table-column prop="retrievalPrecisionAtK" label="Retrieval" width="100" />
        <el-table-column prop="unsupportedClaimRate" label="Unsupported" width="120" />
        <el-table-column prop="poisoningBlockRate" label="Poison" width="100" />
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button size="small" @click="openRun(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" header="Golden Cases" class="block">
      <el-table :data="cases" v-loading="loading" stripe>
        <el-table-column prop="caseCode" label="用例" width="150" />
        <el-table-column prop="intent" label="意图" width="160" />
        <el-table-column prop="userMessage" label="用户消息" min-width="280" />
        <el-table-column prop="expectedTools" label="期望工具" min-width="180" />
        <el-table-column prop="attackType" label="攻击类型" width="150" />
        <el-table-column prop="enabled" label="启用" width="90">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawer" size="65%" title="Eval Run 详情">
      <el-table :data="runDetail.results || []" stripe>
        <el-table-column prop="caseCode" label="用例" width="150" />
        <el-table-column prop="intent" label="意图" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="toolPrecision" label="Precision" width="100" />
        <el-table-column prop="toolRecall" label="Recall" width="100" />
        <el-table-column prop="actualObservation" label="观测" min-width="260" show-overflow-tooltip />
        <el-table-column label="Trace" width="100">
          <template #default="{ row }"><el-button v-if="row.traceId" size="small" @click="goTrace(row.traceId)">打开</el-button></template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
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
const latestRun = computed(() => runs.value[0])

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
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.actions { display: flex; gap: 8px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.page-subtitle { margin: 6px 0 0; color: #606266; font-size: 13px; }
.run-alert { margin-bottom: 16px; }
.summary { margin-bottom: 16px; }
.summary b { display: block; font-size: 26px; color: #303133; }
.summary span { color: #606266; font-size: 13px; }
.block { margin-bottom: 16px; }
</style>
