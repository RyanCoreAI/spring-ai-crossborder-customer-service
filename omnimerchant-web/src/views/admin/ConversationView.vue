<template>
  <div>
    <div class="page-head">
      <div>
        <h2 class="page-title">对话回放</h2>
        <p class="page-subtitle">查看买家消息、智能体回复、模型消耗和延迟信息。</p>
      </div>
    </div>

    <div class="conversation-layout">
      <a-card title="会话列表" class="conv-list-panel">
        <div class="toolbar compact">
          <a-select
            v-model:value="filterTenantId"
            placeholder="租户"
            size="small"
            style="width: 160px"
            @change="onTenantChange"
          >
            <a-select-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
              {{ tenantOptionLabel(tenant) }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="filterStatus"
            allow-clear
            placeholder="状态"
            size="small"
            style="width: 130px"
            @change="loadData"
          >
            <a-select-option :value="1">AI 处理中</a-select-option>
            <a-select-option :value="2">已完成</a-select-option>
            <a-select-option :value="3">已升级</a-select-option>
            <a-select-option :value="4">人工处理中</a-select-option>
            <a-select-option :value="5">已关闭</a-select-option>
          </a-select>
        </div>

        <a-spin :spinning="loading">
          <div class="conv-items">
            <button
              v-for="conversation in conversations"
              :key="conversation.conversationUuid"
              class="conv-card"
              :class="{ active: selectedUuid === conversation.conversationUuid }"
              type="button"
              @click="selectConversation(conversation)"
            >
              <div class="conv-card-header">
                <span class="conv-customer">
                  {{ conversation.customerName || conversation.customerEmail || '匿名客户' }}
                </span>
                <a-tag :color="statusColor(conversation.status)">
                  {{ conversation.statusLabel || statusLabel(conversation.status) }}
                </a-tag>
              </div>
              <div class="conv-card-meta">
                <span>{{ languageLabel(conversation.language) }}</span>
                <span>{{ intentLabel(conversation.intentPrimary) }}</span>
                <span>{{ formatCount(conversation.messageCount) }} 条消息</span>
              </div>
              <div class="conv-card-time">{{ formatTime(conversation.startedAt) }}</div>
            </button>
            <a-empty v-if="!conversations.length && !loading" description="暂无会话" />
          </div>
        </a-spin>

        <div class="pager center">
          <a-pagination v-model:current="page" :page-size="size" :total="total" size="small" @change="loadData" />
        </div>
      </a-card>

      <a-card v-if="!selectedUuid" class="message-panel empty-panel">
        <a-empty description="选择左侧会话查看消息" />
      </a-card>

      <a-card v-else class="message-panel">
        <template #title>
          <div class="message-title">
            <span>消息回放</span>
            <a-space>
              <a-tag>意图：{{ intentLabel(selectedConv?.intentPrimary) }}</a-tag>
              <a-tag color="gold">情绪：{{ selectedConv?.sentiment || '未知' }}</a-tag>
            </a-space>
          </div>
        </template>

        <a-spin :spinning="msgLoading">
          <div class="message-list">
            <div v-for="msg in messages" :key="msg.id" class="msg-item" :class="msg.role">
              <div class="msg-role">{{ roleLabel(msg.role) }}</div>
              <div class="msg-text" v-html="renderMarkdown(msg.content || '')"></div>
              <div class="msg-meta">
                <span v-if="msg.modelName">模型：{{ msg.modelName }}</span>
                <span v-if="msg.totalTokens">Token：{{ msg.totalTokens }}</span>
                <span v-if="msg.latencyMs">延迟：{{ msg.latencyMs }}ms</span>
              </div>
            </div>
            <a-empty v-if="!messages.length && !msgLoading" description="暂无消息" />
          </div>
        </a-spin>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api'
import { renderSafeMarkdown } from '@/utils/markdown'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'
import { intentLabel, tenantOptionLabel } from '@/utils/display'

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
const filterStatus = ref<number | undefined>()

function renderMarkdown(text: string) {
  return renderSafeMarkdown(text)
}

function formatTime(value: string) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN')
}

function statusColor(status: number) {
  if (status === 2) return 'green'
  if (status === 1) return 'blue'
  if (status === 3 || status === 4) return 'gold'
  if (status === 5) return 'default'
  return 'default'
}

function statusLabel(status: number) {
  const labels: Record<number, string> = { 1: 'AI 处理中', 2: '已完成', 3: '已升级', 4: '人工处理中', 5: '已关闭' }
  return labels[status] || '未知'
}

function roleLabel(role: string) {
  if (role === 'user') return '客户'
  if (role === 'assistant') return '智能客服'
  if (role === 'system') return '系统'
  return role
}

function languageLabel(value?: string) {
  if (value === 'zh') return '中文'
  if (value === 'en') return '英文'
  return value || '未知语言'
}

function formatCount(value: any) {
  return value === null || value === undefined || value === '' ? '—' : value
}

async function loadTenants() {
  const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
  tenants.value = res.data?.records || []
  filterTenantId.value = selectDefaultTenantId(tenants.value)
  setStoredTenantId(filterTenantId.value)
}

function onTenantChange() {
  setStoredTenantId(filterTenantId.value)
  selectedUuid.value = ''
  selectedConv.value = null
  messages.value = []
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

async function selectConversation(conversation: any) {
  selectedUuid.value = conversation.conversationUuid
  selectedConv.value = conversation
  msgLoading.value = true
  try {
    const res = await api.get(`/conversations/${conversation.conversationUuid}/messages`)
    messages.value = res.data || []
  } finally {
    msgLoading.value = false
  }
}

onMounted(async () => {
  await loadTenants()
  await loadData()
})
</script>

<style scoped>
.conversation-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 380px minmax(0, 1fr);
  min-height: calc(100vh - 170px);
}

.conv-list-panel,
.message-panel {
  min-height: 0;
}

.conv-list-panel :deep(.ant-card-body),
.message-panel :deep(.ant-card-body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.compact {
  margin-bottom: 12px;
}

.conv-items {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 295px);
  overflow-y: auto;
}

.conv-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  padding: 12px;
  text-align: left;
  transition: border-color 0.2s, background 0.2s;
}

.conv-card:hover,
.conv-card.active {
  background: #f0f5ff;
  border-color: #1677ff;
}

.conv-card-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.conv-customer {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-card-meta {
  color: #6b7280;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 10px;
}

.conv-card-time {
  color: #9ca3af;
  font-size: 11px;
  margin-top: 6px;
}

.center {
  justify-content: center;
}

.empty-panel {
  align-items: center;
  display: flex;
  justify-content: center;
}

.message-title {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 250px);
  overflow-y: auto;
  padding-right: 4px;
}

.msg-item {
  background: #f5f7fb;
  border-radius: 8px;
  padding: 12px;
}

.msg-item.user {
  background: #eef6ff;
}

.msg-item.assistant {
  background: #f1f8f4;
}

.msg-role {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
}

.msg-text {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.7;
}

.msg-text :deep(p) {
  margin: 0 0 8px;
}

.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-meta {
  color: #8c8c8c;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 12px;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .conversation-layout {
    grid-template-columns: 1fr;
  }

  .conv-items,
  .message-list {
    max-height: none;
  }
}
</style>
