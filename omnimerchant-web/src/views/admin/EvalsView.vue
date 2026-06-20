<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">Agent 评测</h2>
        <p class="page-subtitle">覆盖工具调用、越权拒绝、RAG 引用和 prompt injection 场景。</p>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :loading="running" @click="runEvals">运行当前租户评测</el-button>
      </div>
    </div>
    <el-alert v-if="runReport" type="success" show-icon :closable="false" class="run-alert"
              :title="`评测完成：${runReport.passed}/${runReport.total} 通过，Pass Rate ${runReport.passRate}%`" />
    <el-row :gutter="16" class="summary">
      <el-col :span="8">
        <el-card shadow="never"><b>{{ summary.totalCases || 0 }}</b><span>总用例</span></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><b>{{ summary.enabledCases || 0 }}</b><span>启用用例</span></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><b>{{ Object.keys(summary.casesByIntent || {}).length }}</b><span>意图覆盖</span></el-card>
      </el-col>
    </el-row>
    <el-card shadow="never">
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
    <el-card v-if="runReport" shadow="never" header="最近一次运行结果" class="result-card">
      <el-table :data="runReport.results" stripe>
        <el-table-column prop="caseCode" label="用例" width="150" />
        <el-table-column prop="intent" label="意图" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.passed ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="actualObservation" label="观测" min-width="320" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const loading = ref(false)
const running = ref(false)
const summary = ref<any>({})
const cases = ref<any[]>([])
const runReport = ref<any>(null)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/evals', { params: { page: 1, size: 100 } })
    summary.value = res.data || {}
    cases.value = res.data?.cases?.records || []
  } finally {
    loading.value = false
  }
}

async function runEvals() {
  running.value = true
  try {
    const res = await api.post('/evals/run')
    runReport.value = res.data
  } finally {
    running.value = false
  }
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
.summary b { display: block; font-size: 28px; color: #303133; }
.summary span { color: #606266; font-size: 13px; }
.result-card { margin-top: 16px; }
</style>
