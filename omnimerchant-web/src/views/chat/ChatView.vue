<template>
  <div class="chat-layout">
    <!-- Sidebar -->
    <aside class="chat-sidebar">
      <div class="brand" @click="$router.push('/admin')">
        <h2>OmniMerchant</h2>
        <span class="version">v0.0.1</span>
      </div>
      <div class="new-chat-btn">
        <el-button type="primary" @click="startNewChat" :icon="Plus">新对话</el-button>
      </div>
      <div class="conversation-list">
        <div v-for="c in conversations" :key="c.uuid" class="conv-item"
             :class="{ active: c.uuid === currentConvId }"
             @click="switchConversation(c.uuid)">
          <span class="conv-title">{{ c.title || '新对话' }}</span>
          <span class="conv-time">{{ c.time }}</span>
        </div>
        <el-empty v-if="!conversations.length" description="暂无对话" :image-size="60" />
      </div>
      <div class="sidebar-footer">
        <el-button text @click="$router.push('/admin')" :icon="Setting">管理后台</el-button>
        <el-button text type="danger" @click="handleLogout" :icon="SwitchButton">退出</el-button>
      </div>
    </aside>

    <!-- Main chat area -->
    <main class="chat-main">
      <header class="chat-header">
        <div class="header-left">
          <el-select v-model="selectedTenantId" placeholder="选择租户" size="default" style="width:260px" filterable
                     @change="onTenantChange">
            <el-option v-for="t in tenants" :key="t.id" :label="`${t.storeName} (${t.tenantCode})`" :value="t.id" />
          </el-select>
        </div>
        <div class="header-right">
          <el-tag v-if="streaming" type="warning">AI 回复中...</el-tag>
          <el-tag v-else type="success">就绪</el-tag>
        </div>
      </header>

      <!-- Messages -->
      <div class="messages-container" ref="msgContainer">
        <div v-if="messages.length === 0" class="welcome">
          <h3>OmniMerchant AI 客服</h3>
          <p>选择租户后发送消息，测试多语言智能客服</p>
          <div class="quick-tests">
            <el-button v-for="qt in quickTests" :key="qt" size="small" @click="sendMessage(qt)">{{ qt }}</el-button>
          </div>
        </div>
        <MessageBubble v-for="(msg, i) in messages" :key="i" :role="msg.role" :text="msg.text" :tool-calls="msg.toolCalls" />
        <div v-if="streaming" class="streaming-indicator">
          <MessageBubble role="assistant" :text="streamText" />
        </div>
        <div ref="scrollAnchor"></div>
      </div>

      <!-- Input -->
      <footer class="chat-input">
        <el-input v-model="inputText" placeholder="输入消息测试 AI 客服 (支持多语言)..." size="large"
                  :disabled="!selectedTenantId || streaming" clearable
                  @keydown.enter.exact="sendMessage()">
          <template #append>
            <el-button :icon="Promotion" :disabled="!selectedTenantId || streaming || !inputText.trim()"
                       @click="sendMessage()" type="primary">发送</el-button>
          </template>
        </el-input>
      </footer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Setting, SwitchButton, Promotion } from '@element-plus/icons-vue'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'
import MessageBubble from '@/components/MessageBubble.vue'
import { selectDefaultTenantId, setStoredTenantId } from '@/utils/tenant'

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

const quickTests = [
  'Where is my order #1001? My email is ava@example.com.',
  'Recommend a waterproof travel backpack under $80.',
  'Can I return the rain jacket from order #1002? lucia@example.es',
  'I am angry because tracking VL2004US is late.',
]

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function startNewChat() {
  currentConvId.value = generateUUID()
  messages.value = []
  streamText.value = ''
  conversations.value.unshift({
    uuid: currentConvId.value,
    title: '新对话',
    time: new Date().toLocaleTimeString(),
  })
}

function switchConversation(uuid: string) {
  currentConvId.value = uuid
  messages.value = []
  streamText.value = ''
}

async function onTenantChange() {
  setStoredTenantId(selectedTenantId.value)
  if (!currentConvId.value) startNewChat()
}

async function loadTenants() {
  try {
    const res = await api.get('/tenants', { params: { page: 1, size: 100 } })
    tenants.value = res.data?.records || []
    selectedTenantId.value = selectDefaultTenantId(tenants.value)
    setStoredTenantId(selectedTenantId.value)
  } catch { /* ignore */ }
}

async function sendMessage(text?: string) {
  const msg = (text || inputText.value).trim()
  if (!msg || streaming.value) return
  if (!currentConvId.value) startNewChat()

  messages.value.push({ role: 'user', text: msg })
  inputText.value = ''
  streaming.value = true
  streamText.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const token = authStore.token
    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'X-Tenant-Id': String(selectedTenantId.value),
      },
      body: JSON.stringify({
        conversationUuid: currentConvId.value,
        message: msg,
        intent: 'UNCLEAR',
      }),
    })

    if (!resp.ok) {
      if (resp.status === 401 || resp.status === 403) {
        authStore.logout()
        ElMessage.error('当前登录权限已失效，请重新登录')
        router.push('/login')
        return
      }
      throw new Error(`HTTP ${resp.status}`)
    }

    const reader = resp.body?.getReader()
    if (!reader) throw new Error('No response body')

    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // Keep the last partial line in buffer
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          // SSE data field: remove "data:" prefix, trim leading space if any
          const data = line.slice(5).replace(/^ /, '')
          if (currentEvent === 'done' || data === '[DONE]') {
            // Stream complete — finalize message on next blank line or here
            if (streamText.value) {
              messages.value.push({ role: 'assistant', text: streamText.value })
              streamText.value = ''
            }
          } else if (currentEvent === 'error') {
            ElMessage.error('AI 回复出错: ' + data)
          } else {
            // message event — append text chunk as-is
            streamText.value += data
          }
        }
        // Blank lines (event boundary) reset currentEvent
      }
    }

    // Handle any remaining streamed text
    if (streamText.value) {
      messages.value.push({ role: 'assistant', text: streamText.value })
      streamText.value = ''
    }
    await nextTick()
    scrollToBottom()

    // Update conversation title from first user message
    const conv = conversations.value.find((c) => c.uuid === currentConvId.value)
    if (conv && messages.value.length >= 2) {
      conv.title = msg.slice(0, 30) + (msg.length > 30 ? '...' : '')
    }
  } catch (e: any) {
    ElMessage.error('AI 请求失败: ' + (e.message || '网络错误'))
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
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}
.chat-sidebar {
  width: 260px;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.brand {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
}
.brand h2 {
  font-size: 18px;
  color: #409eff;
}
.version {
  font-size: 11px;
  color: #c0c4cc;
}
.new-chat-btn {
  padding: 12px 16px;
}
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}
.conv-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.conv-item:hover { background: #f5f7fa; }
.conv-item.active { background: #ecf5ff; }
.conv-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-time {
  font-size: 11px;
  color: #c0c4cc;
}
.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.welcome {
  text-align: center;
  margin: auto;
  color: #909399;
}
.welcome h3 { font-size: 24px; color: #303133; margin-bottom: 8px; }
.welcome p { margin-bottom: 20px; }
.quick-tests {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.chat-input {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.streaming-indicator {
  opacity: 0.85;
}
</style>
