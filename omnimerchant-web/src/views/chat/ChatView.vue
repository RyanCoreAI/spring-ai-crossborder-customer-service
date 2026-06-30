<template>
  <div class="chat-layout">
    <aside class="chat-sidebar">
      <div class="brand" @click="router.push('/admin')">
        <h2>OmniMerchant</h2>
        <span class="version">智能客服测试台</span>
      </div>
      <div class="new-chat-btn">
        <a-button block type="primary" @click="startNewChat">
          <template #icon><PlusOutlined /></template>
          新对话
        </a-button>
      </div>
      <div class="conversation-list">
        <button
          v-for="conversation in conversations"
          :key="conversation.uuid"
          class="conv-item"
          :class="{ active: conversation.uuid === currentConvId }"
          type="button"
          @click="switchConversation(conversation.uuid)"
        >
          <span class="conv-title">{{ conversation.title || '新对话' }}</span>
          <span class="conv-time">{{ conversation.time }}</span>
        </button>
        <a-empty v-if="!conversations.length" description="暂无对话" />
      </div>
      <div class="sidebar-footer">
        <a-button type="link" @click="router.push('/admin')">
          <template #icon><SettingOutlined /></template>
          管理后台
        </a-button>
        <a-button danger type="link" @click="handleLogout">
          <template #icon><LogoutOutlined /></template>
          退出
        </a-button>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <a-select
          v-model:value="selectedTenantId"
          show-search
          placeholder="选择租户"
          style="width: 280px"
          option-filter-prop="label"
          @change="onTenantChange"
        >
          <a-select-option
            v-for="tenant in tenants"
            :key="tenant.id"
            :value="tenant.id"
            :label="tenantOptionLabel(tenant)"
          >
            {{ tenantOptionLabel(tenant) }}
          </a-select-option>
        </a-select>
        <a-tag :color="streaming ? 'gold' : 'green'">{{ streaming ? '回复中' : '就绪' }}</a-tag>
      </header>

      <div ref="msgContainer" class="messages-container">
        <div v-if="messages.length === 0" class="welcome">
          <h3>知识库对话测试</h3>
          <p>这里会调用后台智能客服链路，可验证 RAG 政策问答、订单、物流、商品推荐和人工升级。</p>
          <div class="examples">
            <button type="button" @click="sendMessage('这件外套可以退货吗？')">这件外套可以退货吗？</button>
            <button type="button" @click="sendMessage('订单 #1001 现在到哪里了？')">订单 #1001 现在到哪里了？</button>
            <button type="button" @click="sendMessage('推荐一款 80 美元以内的防水旅行背包')">推荐防水旅行背包</button>
          </div>
        </div>

        <MessageBubble
          v-for="(msg, index) in messages"
          :key="index"
          :role="msg.role"
          :text="msg.text"
          :tool-calls="msg.toolCalls"
        />
        <MessageBubble v-if="streaming" role="assistant" :text="streamText" />
        <div ref="scrollAnchor"></div>
      </div>

      <footer class="chat-input">
        <a-input-search
          v-model:value="inputText"
          enter-button="发送"
          placeholder="输入消息测试智能客服，支持多语言"
          size="large"
          :disabled="!selectedTenantId || streaming"
          :loading="streaming"
          @search="sendMessage()"
        />
      </footer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LogoutOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import MessageBubble from '@/components/MessageBubble.vue'
import { useAuthStore } from '@/stores/auth'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'
import { tenantOptionLabel } from '@/utils/display'

const router = useRouter()
const authStore = useAuthStore()

const selectedTenantId = ref<number | null>(null)
const tenants = ref<any[]>([])
const conversations = ref<{ uuid: string; title: string; time: string }[]>([])
const currentConvId = ref('')
const messages = ref<{ role: string; text: string; toolCalls?: any[] }[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamText = ref('')
const msgContainer = ref<HTMLElement>()
const scrollAnchor = ref<HTMLElement>()

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0
    return (char === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}

function startNewChat() {
  currentConvId.value = generateUUID()
  messages.value = []
  streamText.value = ''
  conversations.value.unshift({
    uuid: currentConvId.value,
    title: '新对话',
    time: new Date().toLocaleTimeString('zh-CN'),
  })
}

function switchConversation(uuid: string) {
  currentConvId.value = uuid
  messages.value = []
  streamText.value = ''
}

function onTenantChange() {
  setStoredTenantId(selectedTenantId.value)
  if (!currentConvId.value) startNewChat()
}

async function loadTenants() {
  try {
    const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
    tenants.value = res.data?.records || []
    selectedTenantId.value = selectDefaultTenantId(tenants.value)
    setStoredTenantId(selectedTenantId.value)
  } catch {
    tenants.value = []
  }
}

async function sendMessage(text?: string) {
  const userText = (text || inputText.value).trim()
  if (!userText || streaming.value) return
  if (!currentConvId.value) startNewChat()

  messages.value.push({ role: 'user', text: userText })
  inputText.value = ''
  streaming.value = true
  streamText.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authStore.token}`,
        'X-Tenant-Id': String(selectedTenantId.value),
      },
      body: JSON.stringify({
        conversationUuid: currentConvId.value,
        message: userText,
        intent: 'UNCLEAR',
      }),
    })

    if (!resp.ok) {
      if (resp.status === 401 || resp.status === 403) {
        authStore.logout()
        message.error('当前登录权限已失效，请重新登录')
        router.push('/login')
        return
      }
      throw new Error(`HTTP ${resp.status}`)
    }

    const reader = resp.body?.getReader()
    if (!reader) throw new Error('响应流为空')

    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).replace(/^ /, '')
          if (currentEvent === 'done' || data === '[DONE]') {
            if (streamText.value) {
              messages.value.push({ role: 'assistant', text: streamText.value })
              streamText.value = ''
            }
          } else if (currentEvent === 'error') {
            message.error(`智能客服回复出错：${data}`)
          } else {
            streamText.value += data
          }
        }
      }
    }

    if (streamText.value) {
      messages.value.push({ role: 'assistant', text: streamText.value })
      streamText.value = ''
    }
    await nextTick()
    scrollToBottom()

    const conversation = conversations.value.find((item) => item.uuid === currentConvId.value)
    if (conversation && messages.value.length >= 2) {
      conversation.title = userText.slice(0, 30) + (userText.length > 30 ? '...' : '')
    }
  } catch (error: any) {
    message.error(`智能客服请求失败：${error.message || '网络错误'}`)
    streamText.value = ''
  } finally {
    streaming.value = false
  }
}

function scrollToBottom() {
  scrollAnchor.value?.scrollIntoView({ behavior: 'smooth' })
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await loadTenants()
  startNewChat()
})
</script>

<style scoped>
.chat-layout {
  background: #f3f6fb;
  display: flex;
  height: 100vh;
}

.chat-sidebar {
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 280px;
}

.brand {
  border-bottom: 1px solid #eef0f3;
  cursor: pointer;
  padding: 20px;
}

.brand h2 {
  color: #1677ff;
  font-size: 18px;
  margin: 0;
}

.version {
  color: #8c8c8c;
  font-size: 12px;
}

.new-chat-btn {
  padding: 12px 16px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px;
}

.conv-item {
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 4px;
  padding: 10px 12px;
  text-align: left;
  width: 100%;
}

.conv-item:hover,
.conv-item.active {
  background: #f0f5ff;
}

.conv-title {
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  color: #9ca3af;
  font-size: 11px;
}

.sidebar-footer {
  border-top: 1px solid #eef0f3;
  display: flex;
  justify-content: space-between;
  padding: 12px;
}

.chat-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  flex-shrink: 0;
  height: 60px;
  justify-content: space-between;
  padding: 0 20px;
}

.messages-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  padding: 20px;
}

.welcome {
  color: #6b7280;
  margin: auto;
  max-width: 760px;
  text-align: center;
}

.welcome h3 {
  color: #1f2937;
  font-size: 24px;
  margin-bottom: 8px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 18px;
}

.examples button {
  background: #fff;
  border: 1px solid #d9e4f5;
  border-radius: 18px;
  color: #1f4d8f;
  cursor: pointer;
  font-size: 13px;
  padding: 8px 14px;
}

.examples button:hover {
  background: #f0f6ff;
  border-color: #9ec3ff;
}

.chat-input {
  background: #fff;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
  padding: 16px 20px;
}

@media (max-width: 860px) {
  .chat-sidebar {
    display: none;
  }

  .chat-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    height: auto;
    padding: 12px;
  }
}
</style>
