<template>
  <div class="widget-shell">
    <section class="widget">
      <header class="widget-header">
        <div>
          <h1>OmniMerchant Support</h1>
          <p>{{ session?.welcomeMessage || 'Ask about products, orders, shipping, returns, or warranty.' }}</p>
        </div>
        <el-tag :type="connected ? 'success' : 'warning'">{{ connected ? '已连接' : '未连接' }}</el-tag>
      </header>

      <div v-if="!connected" class="session-form">
        <el-input v-model="tenantCode" placeholder="Tenant code, e.g. OM-FASHION" />
        <el-input v-model="customerEmail" placeholder="Order email (optional but required for order details)" />
        <el-input v-model="customerName" placeholder="Name (optional)" />
        <el-button type="primary" :loading="starting" @click="startSession">开始咨询</el-button>
      </div>

      <div v-else class="chat">
        <div class="messages" ref="messagesEl">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="msg-body" v-html="render(m.text)"></div>
          </div>
          <div v-if="streaming" class="msg assistant">
            <div class="msg-body" v-html="render(streamText)"></div>
          </div>
        </div>
        <div class="quick">
          <el-button v-for="q in quickTests" :key="q" size="small" @click="send(q)">{{ q }}</el-button>
        </div>
        <footer class="composer">
          <el-input v-model="input" placeholder="Type your question..." :disabled="streaming"
                    @keydown.enter.exact="send()" />
          <el-button type="primary" :disabled="!input.trim() || streaming" @click="send()">发送</el-button>
        </footer>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { renderSafeMarkdown } from '@/utils/markdown'

const tenantCode = ref('OM-FASHION')
const customerEmail = ref('ava@example.com')
const customerName = ref('Ava Miller')
const session = ref<any>(null)
const customerSessionToken = ref('')
const connected = ref(false)
const starting = ref(false)
const streaming = ref(false)
const input = ref('')
const streamText = ref('')
const messages = ref<{ role: string; text: string }[]>([])
const messagesEl = ref<HTMLElement>()

const quickTests = [
  'Where is my order #1001? My email is ava@example.com.',
  'Recommend a waterproof travel backpack under $80.',
  'Can I return my rain jacket from #1002? lucia@example.es',
  'I am angry because my package is late.',
]

function render(text: string) {
  return renderSafeMarkdown(text || '')
}

async function startSession() {
  starting.value = true
  customerSessionToken.value = ''
  try {
    const resp = await fetch('/api/widget/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tenantCode: tenantCode.value,
        customerEmail: customerEmail.value,
        customerName: customerName.value,
        language: 'en',
      }),
    })
    const body = await readJson(resp)
    if (!resp.ok || body.code !== '200') throw new Error(formatWidgetError(resp.status, body.message))
    if (!body.data?.customerSessionToken) throw new Error('服务未返回客户会话令牌，请刷新后重试')
    session.value = body.data
    customerSessionToken.value = body.data.customerSessionToken
    connected.value = true
    messages.value = [{ role: 'assistant', text: body.data.welcomeMessage }]
  } catch (e: any) {
    ElMessage.error(formatFetchError(e, '无法创建会话'))
  } finally {
    starting.value = false
  }
}

async function send(text?: string) {
  const msg = (text || input.value).trim()
  if (!msg || streaming.value || !session.value) return
  if (!customerSessionToken.value) {
    ElMessage.error('会话已失效，请重新开始咨询')
    connected.value = false
    session.value = null
    return
  }
  messages.value.push({ role: 'user', text: msg })
  input.value = ''
  streamText.value = ''
  streaming.value = true
  await nextTick()
  messagesEl.value?.scrollTo({ top: messagesEl.value.scrollHeight })

  try {
    const resp = await fetch('/api/widget/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${customerSessionToken.value}`,
      },
      body: JSON.stringify({
        tenantCode: tenantCode.value,
        conversationUuid: session.value.conversationUuid,
        message: msg,
        intent: 'UNKNOWN',
      }),
    })
    if (!resp.ok) throw new Error(formatWidgetError(resp.status, (await readJson(resp)).message))
    const reader = resp.body?.getReader()
    if (!reader) throw new Error('No response body')
    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line.startsWith('data:')) {
          const data = line.slice(5).replace(/^ /, '')
          if (eventName === 'done' || data === '[DONE]') continue
          if (eventName === 'error') throw new Error(data)
          streamText.value += data
        }
      }
    }
    if (streamText.value) messages.value.push({ role: 'assistant', text: streamText.value })
  } catch (e: any) {
    ElMessage.error(formatFetchError(e, '回复失败'))
    if (e.message?.includes('会话已过期') || e.message?.includes('重新开始咨询')) {
      connected.value = false
      session.value = null
      customerSessionToken.value = ''
    }
  } finally {
    streamText.value = ''
    streaming.value = false
  }
}

async function readJson(resp: Response) {
  try {
    return await resp.json()
  } catch {
    return {}
  }
}

function formatWidgetError(status: number, message?: string) {
  if (status === 400) return message || '请求内容格式不正确，请刷新页面后重试'
  if (status === 401) return message || '会话已过期，请重新开始咨询'
  if (status === 403) return message || '当前会话无权限访问该店铺或订单'
  if (status === 405) return '请求方法错误，请刷新页面使用最新版本'
  if (status >= 500) return '服务异常，请稍后重试或联系人工客服'
  return message || `请求失败（HTTP ${status}）`
}

function formatFetchError(error: any, fallback: string) {
  const message = error?.message || ''
  if (message === 'Failed to fetch' || message === 'NetworkError when attempting to fetch resource.') {
    return '无法连接服务或地址不一致，请统一使用 http://127.0.0.1:5188 或 http://localhost:5188'
  }
  return message || fallback
}
</script>

<style scoped>
.widget-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #eef2f7;
  padding: 24px;
}
.widget {
  width: min(760px, 100%);
  height: min(760px, calc(100vh - 48px));
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.widget-header {
  padding: 18px 20px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.widget-header h1 { margin: 0; font-size: 20px; color: #303133; }
.widget-header p { margin: 6px 0 0; color: #606266; font-size: 13px; }
.session-form {
  padding: 20px;
  display: grid;
  gap: 12px;
}
.chat { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg {
  max-width: 82%;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f4f6f8;
  color: #303133;
  line-height: 1.6;
}
.msg.user {
  align-self: flex-end;
  background: #246bfe;
  color: #fff;
}
.msg.assistant { align-self: flex-start; }
.quick {
  padding: 10px 14px;
  border-top: 1px solid #ebeef5;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.composer {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid #ebeef5;
}
</style>
