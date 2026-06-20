<template>
  <div>
    <h2 class="page-title">用量计费</h2>
    <el-row :gutter="16" class="metrics">
      <el-col :span="6" v-for="m in metrics" :key="m.label">
        <el-card shadow="never">
          <div class="metric-value">{{ m.value }}</div>
          <div class="metric-label">{{ m.label }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never" header="当前租户月度用量">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-descriptions v-if="usage" :column="2" border class="usage">
        <el-descriptions-item v-for="(v, k) in usage" :key="String(k)" :label="String(k)">{{ v }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无 billing 数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const usage = ref<any>(null)
const metrics = ref([
  { label: 'AI 解决率', value: '0%' },
  { label: '升级率', value: '0%' },
  { label: '工具成功率', value: '0%' },
  { label: '待处理工单', value: '0' },
])

async function load() {
  try {
    const dash = await api.get('/dashboard/commerce')
    const d = dash.data || {}
    metrics.value = [
      { label: 'AI 解决率', value: `${d.aiResolutionRate || 0}%` },
      { label: '升级率', value: `${d.escalationRate || 0}%` },
      { label: '工具成功率', value: `${d.toolSuccessRate || 0}%` },
      { label: '待处理工单', value: String(d.openTickets || 0) },
    ]
  } catch { /* dashboard optional */ }
  try {
    const res = await api.get('/billing/usage')
    usage.value = res.data
  } catch {
    usage.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page-title { margin: 0 0 16px; font-size: 22px; color: #303133; }
.metrics { margin-bottom: 16px; }
.metric-value { font-size: 26px; font-weight: 600; color: #303133; }
.metric-label { margin-top: 4px; color: #606266; font-size: 13px; }
.usage { margin-top: 16px; }
</style>
