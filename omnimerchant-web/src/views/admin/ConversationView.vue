<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">对话回放</h2>
    </div>

    <div class="conversation-layout">
      <!-- Conversation list -->
      <div class="conv-list-panel">
        <el-card header="会话列表">
          <div class="filter-bar">
            <el-select v-model="filterTenantId" placeholder="租户" size="small" style="width:140px" @change="onTenantChange">
              <el-option v-for="t in tenants" :key="t.id" :label="t.storeName" :value="t.id" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width:110px;margin-left:8px" @change="loadData">
              <el-option label="AI处理中" :value="1" />
              <el-option label="已完成" :value="2" />
              <el-option label="已升级" :value="3" />
              <el-option label="人工处理中" :value="4" />
              <el-option label="已关闭" :value="5" />
            </el-select>
          </div>
          <div class="conv-items" v-loading="loading">
            <div v-for="c in conversations" :key="c.conversationUuid" class="conv-card"
                 :class="{ active: selectedUuid === c.conversationUuid }"
                 @click="selectConversation(c)">
              <div class="conv-card-header">
                <span class="conv-customer">{{ c.customerName || c.customerEmail || '匿名客户' }}</span>
                <el-tag size="small" :type="c.status === 2 ? 'success' : c.status === 1 ? '' : 'warning'">
                  {{ c.statusLabel }}
                </el-tag>
              </div>
              <div class="conv-card-meta">
                <span>{{ c.language || '—' }}</span>
                <span>{{ c.intentPrimary || '未分类' }}</span>
                <span>{{ c.messageCount || 0 }} 条消息</span>
              </div>
              <div class="conv-card-time">{{ formatTime(c.startedAt) }}</div>
            </div>
            <el-empty v-if="!conversations.length && !loading" description="暂无会话" :image-size="60" />
          </div>
          <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                         layout="prev, pager, next" size="small" @current-change="loadData"
                         style="margin-top:8px;justify-content:center" />
        </el-card>
      </div>

      <!-- Message replay -->
      <div class="message-panel">
        <el-card v-if="!selectedUuid" style="height:100%;display:flex;align-items:center;justify-content:center">
          <el-empty description="选择左侧会话查看消息" :image-size="80" />
        </el-card>
        <el-card v-else header="消息详情" class="message-card">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>消息回放</span>
              <div>
                <el-tag size="small" style="margin-right:8px">意图: {{ selectedConv?.intentPrimary || '—' }}</el-tag>
                <el-tag size="small" type="warning">情绪: {{ selectedConv?.sentiment || '—' }}</el-tag>
              </div>
            </div>
          </template>
          <div class="message-list" v-loading="msgLoading">
            <div v-for="msg in messages" :key="msg.id" class="msg-item" :class="msg.role">
              <div class="msg-role">{{ msg.role === 'user' ? '客户' : msg.role === 'assistant' ? 'AI' : msg.role }}</div>
              <div class="msg-text" v-html="renderMarkdown(msg.content || '')"></div>
              <div class="msg-meta">
                <span v-if="msg.modelName">模型: {{ msg.modelName }}</span>
                <span v-if="msg.totalTokens">Token: {{ msg.totalTokens }}</span>
                <span v-if="msg.latencyMs">延迟: {{ msg.latencyMs }}ms</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'
import { renderSafeMarkdown } from '@/utils/markdown'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

const loading = ref(false)
const msgLoading = ref(false)
const tenants = ref<any[]>([])
const conversations = ref<any[]>([])
const messages = ref<any[]>([])
const selectedUuid = ref('')
const selectedConv = ref<any>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterTenantId = ref<number | null>(null)
const filterStatus = ref<number | null>(null)

function renderMarkdown(text: string) {
  return renderSafeMarkdown(text)
}

function formatTime(t: string) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  filterTenantId.value = selectDefaultTenantId(tenants.value)
  setStoredTenantId(filterTenantId.value)
}

function onTenantChange() {
  setStoredTenantId(filterTenantId.value)
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (filterTenantId.value) params.tenantId = filterTenantId.value
    if (filterStatus.value) params.status = filterStatus.value
    const res = await api.get('/conversations', { params })
    conversations.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function selectConversation(c: any) {
  selectedUuid.value = c.conversationUuid
  selectedConv.value = c
  msgLoading.value = true
  try {
    const res = await api.get(`/conversations/${c.conversationUuid}/messages`)
    messages.value = res.data || []
  } finally {
    msgLoading.value = false
  }
}

onMounted(async () => {
  await loadTenants()
  loadData()
})
</script>

<style scoped>
.page-header { margin-bottom: 20px; }
.page-title { font-size: 22px; color: #303133; }
.conversation-layout {
  display: flex;
  gap: 20px;
  height: calc(100vh - 140px);
}
.conv-list-panel {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.conv-list-panel :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
}
.filter-bar { margin-bottom: 12px; }
.conv-items {
  flex: 1;
  overflow-y: auto;
}
.conv-card {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.conv-card:hover { border-color: #409eff; }
.conv-card.active { border-color: #409eff; background: #ecf5ff; }
.conv-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.conv-customer { font-size: 14px; font-weight: 500; color: #303133; }
.conv-card-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}
.conv-card-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; }
.message-panel { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.message-card { flex: 1; display: flex; flex-direction: column; }
.message-card :deep(.el-card__body) { flex: 1; overflow-y: auto; }
.message-list { padding: 8px 0; }
.msg-item { margin-bottom: 16px; padding: 12px; border-radius: 8px; background: #f5f7fa; }
.msg-item.user { background: #ecf5ff; }
.msg-item.assistant { background: #f0f9eb; }
.msg-role { font-size: 12px; font-weight: 600; color: #909399; margin-bottom: 6px; text-transform: uppercase; }
.msg-text { font-size: 14px; line-height: 1.7; color: #303133; }
.msg-text :deep(p) { margin: 0 0 8px; }
.msg-text :deep(p:last-child) { margin-bottom: 0; }
.msg-meta { display: flex; gap: 12px; margin-top: 8px; font-size: 11px; color: #c0c4cc; }
</style>
