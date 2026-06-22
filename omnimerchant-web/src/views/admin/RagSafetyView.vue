<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">RAG 安全审核</h2>
        <p class="page-subtitle">审核被 prompt injection、poisoning、PII 或危险工具指令命中的知识文档。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never">
      <div class="filters">
        <el-select v-model="status" clearable placeholder="状态" style="width:180px" @change="load">
          <el-option label="APPROVED" value="APPROVED" />
          <el-option label="QUARANTINED" value="QUARANTINED" />
          <el-option label="REJECTED" value="REJECTED" />
        </el-select>
        <el-select v-model="riskLevel" clearable placeholder="风险" style="width:160px" @change="load">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
        </el-select>
      </div>
      <el-table :data="reviews" v-loading="loading" stripe>
        <el-table-column prop="docUuid" label="文档" min-width="180" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="风险" width="100">
          <template #default="{ row }"><el-tag :type="riskType(row.riskLevel)">{{ row.riskLevel }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130" />
        <el-table-column prop="indexAllowed" label="可索引" width="90">
          <template #default="{ row }">{{ row.indexAllowed ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="matchedRules" label="命中规则" min-width="220" show-overflow-tooltip />
        <el-table-column prop="redactedExcerpt" label="脱敏片段" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const loading = ref(false)
const reviews = ref<any[]>([])
const status = ref('')
const riskLevel = ref('')

function riskType(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/rag/safety/docs', { params: { status: status.value, riskLevel: riskLevel.value, page: 1, size: 100 } })
    reviews.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

async function approve(row: any) {
  await api.post(`/rag/safety/docs/${row.docUuid}/approve`, { note: 'Approved from admin console' })
  ElMessage.success('已允许索引')
  load()
}

async function reject(row: any) {
  await api.post(`/rag/safety/docs/${row.docUuid}/reject`, { note: 'Rejected from admin console' })
  ElMessage.success('已拒绝索引')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 22px; color: #303133; }
.page-subtitle { margin: 6px 0 0; color: #606266; font-size: 13px; }
.filters { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
