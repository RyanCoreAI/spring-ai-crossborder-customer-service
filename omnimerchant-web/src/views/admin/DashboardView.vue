<template>
  <div class="dashboard">
    <h2 class="page-title">数据概览</h2>
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" :style="{ background: stat.color }">
              <el-icon :size="28"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card header="客服质量指标">
          <div class="chart-placeholder">
            <el-table :data="qualityData" size="small">
              <el-table-column prop="label" label="指标" />
              <el-table-column prop="value" label="当前值" />
            </el-table>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="系统状态">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="API 状态">
              <el-tag :type="healthStatus === 'UP' ? 'success' : 'danger'">{{ healthStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="租户数量">{{ tenantCount }}</el-descriptions-item>
            <el-descriptions-item label="知识文档">{{ knowledgeCount }}</el-descriptions-item>
            <el-descriptions-item label="今日会话">{{ todayConversations }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { User, Coin, ChatLineRound, Document } from '@element-plus/icons-vue'
import api from '@/api'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const healthStatus = ref('...')
const tenantCount = ref(0)
const knowledgeCount = ref(0)
const todayConversations = ref(0)
const qualityData = ref<any[]>([])

const stats = ref([
  { label: '客户数', value: '...', icon: markRaw(User), color: '#409eff' },
  { label: '订单数', value: '...', icon: markRaw(Coin), color: '#67c23a' },
  { label: '会话数', value: '...', icon: markRaw(ChatLineRound), color: '#e6a23c' },
  { label: '商品数', value: '...', icon: markRaw(Document), color: '#f56c6c' },
])

onMounted(async () => {
  try {
    const h = await api.get('/health')
    healthStatus.value = h.data?.status || 'UP'
  } catch { healthStatus.value = 'DOWN' }

  try {
    const t = await api.get('/tenants', { params: { page: 1, size: 100 } })
    tenantCount.value = t.data?.total || 0
    stats.value[0].value = String(tenantCount.value)
    const defaultTenantId = selectDefaultTenantId(t.data?.records || [])
    if (defaultTenantId) {
      setStoredTenantId(defaultTenantId)
    }
  } catch { /* empty */ }

  try {
    const k = await api.get('/knowledge/docs', { params: { page: 1, size: 1 } })
    knowledgeCount.value = k.data?.total || 0
    stats.value[3].value = String(knowledgeCount.value)
  } catch { /* empty */ }

  try {
    const c = await api.get('/conversations', { params: { page: 1, size: 1 } })
    todayConversations.value = c.data?.total || 0
    stats.value[2].value = String(todayConversations.value)
  } catch { /* empty */ }

  try {
    const d = await api.get('/dashboard/commerce')
    const data = d.data || {}
    stats.value[0].value = String(data.customers || 0)
    stats.value[1].value = String(data.orders || 0)
    stats.value[2].value = String(data.conversations || 0)
    stats.value[3].value = String(data.products || 0)
    qualityData.value = [
      { label: 'AI 解决率', value: `${data.aiResolutionRate || 0}%` },
      { label: '升级率', value: `${data.escalationRate || 0}%` },
      { label: '工具成功率', value: `${data.toolSuccessRate || 0}%` },
      { label: '待处理工单', value: data.openTickets || 0 },
      { label: '待审批退货/动作', value: data.pendingReturns || 0 },
    ]
  } catch { /* keep fallback counts */ }
})
</script>

<style scoped>
.page-title { font-size: 22px; margin-bottom: 20px; color: #303133; }
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.stat-value { font-size: 24px; font-weight: 600; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.chart-placeholder { min-height: 200px; }
</style>
