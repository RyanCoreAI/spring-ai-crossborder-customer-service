<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">Trajectory Replay</h2>
        <p class="page-subtitle">按 trace 回放意图、检索、工具调用、最终回答和失败归因。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <div class="filters">
        <el-input v-model="filters.conversationUuid" clearable placeholder="conversationUuid" style="width:260px" />
        <el-select v-model="filters.status" clearable placeholder="状态" style="width:160px">
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILED" value="FAILED" />
          <el-option label="RUNNING" value="RUNNING" />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-table :data="traces" v-loading="loading" stripe @row-click="openTrace">
        <el-table-column prop="traceId" label="Trace" min-width="180" show-overflow-tooltip />
        <el-table-column prop="conversationUuid" label="会话" min-width="180" show-overflow-tooltip />
        <el-table-column prop="intent" label="意图" width="150" />
        <el-table-column prop="modelName" label="模型" width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="failureCategory" label="失败分类" width="150" />
        <el-table-column prop="toolCallCount" label="工具" width="80" />
        <el-table-column prop="totalLatencyMs" label="总延迟" width="100" />
      </el-table>
    </el-card>

    <el-drawer v-model="drawer" size="60%" title="Trace Replay">
      <el-descriptions v-if="detail.run" :column="2" border class="detail">
        <el-descriptions-item label="Trace">{{ detail.run.traceId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.run.status }}</el-descriptions-item>
        <el-descriptions-item label="意图">{{ detail.run.intent }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detail.run.modelName }}</el-descriptions-item>
        <el-descriptions-item label="失败分类">{{ detail.run.failureCategory || '—' }}</el-descriptions-item>
        <el-descriptions-item label="延迟">{{ detail.run.totalLatencyMs || 0 }}ms</el-descriptions-item>
      </el-descriptions>
      <el-timeline class="timeline">
        <el-timeline-item v-for="s in detail.steps || []" :key="s.stepIndex" :timestamp="s.stepType">
          <div class="step-title">{{ s.stepIndex }}. {{ s.name }} <el-tag size="small">{{ s.status }}</el-tag></div>
          <div class="step-text" v-if="s.inputSummary">输入：{{ s.inputSummary }}</div>
          <div class="step-text" v-if="s.outputSummary">输出：{{ s.outputSummary }}</div>
          <div class="step-meta">{{ s.latencyMs || 0 }}ms {{ s.failureCategory || '' }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const route = useRoute()
const loading = ref(false)
const drawer = ref(false)
const traces = ref<any[]>([])
const detail = ref<any>({})
const filters = reactive({ conversationUuid: '', status: '' })

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
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.page-subtitle { margin: 6px 0 0; color: #606266; font-size: 13px; }
.filters { display: flex; gap: 8px; margin-bottom: 12px; }
.detail { margin-bottom: 16px; }
.timeline { padding: 8px 4px; }
.step-title { font-weight: 600; color: #303133; margin-bottom: 6px; }
.step-text { color: #606266; line-height: 1.5; word-break: break-word; margin-bottom: 4px; }
.step-meta { color: #909399; font-size: 12px; }
</style>
