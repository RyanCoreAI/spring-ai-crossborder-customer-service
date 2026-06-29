<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">用量计费</h2>
        <p class="page-subtitle">查看当前租户的会话成效、工具健康度和月度 token 用量。</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-row :gutter="[16, 16]" class="metrics">
      <a-col v-for="metric in metrics" :key="metric.label" :xs="12" :md="6">
        <a-card>
          <a-statistic :title="metric.label" :value="metric.value" :suffix="metric.suffix" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="当前租户月度用量">
      <a-descriptions v-if="usage" :column="2" bordered class="usage">
        <a-descriptions-item v-for="(value, key) in usage" :key="String(key)" :label="String(key)">
          {{ value }}
        </a-descriptions-item>
      </a-descriptions>
      <a-empty v-else description="暂无计费数据" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import api from '@/api'

const usage = ref<any>(null)
const metrics = ref<{ label: string; value: string | number; suffix?: string }[]>([
  { label: 'AI 解决率', value: '—' },
  { label: '人工升级率', value: '—' },
  { label: '工具成功率', value: '—' },
  { label: '待处理工单', value: '—' },
])

async function load() {
  try {
    const dash = await api.get('/dashboard/commerce')
    const data = dash.data || {}
    metrics.value = [
      { label: 'AI 解决率', value: formatValue(data.aiResolutionRate), suffix: hasValue(data.aiResolutionRate) ? '%' : undefined },
      { label: '人工升级率', value: formatValue(data.escalationRate), suffix: hasValue(data.escalationRate) ? '%' : undefined },
      { label: '工具成功率', value: formatValue(data.toolSuccessRate), suffix: hasValue(data.toolSuccessRate) ? '%' : undefined },
      { label: '待处理工单', value: formatValue(data.openTickets) },
    ]
  } catch {
    metrics.value = metrics.value.map((item) => ({ label: item.label, value: '—' }))
  }
  try {
    const res = await api.get('/billing/usage')
    usage.value = res.data
  } catch {
    usage.value = null
  }
}

function hasValue(value: any) {
  return value !== null && value !== undefined && value !== ''
}

function formatValue(value: any) {
  return hasValue(value) ? value : '—'
}

onMounted(load)
</script>

<style scoped>
.metrics {
  margin-bottom: 16px;
}

.usage {
  margin-top: 4px;
}
</style>
