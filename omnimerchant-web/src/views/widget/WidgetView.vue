<template>
  <div class="widget-shell">
    <section class="widget">
      <header class="widget-header">
        <div>
          <h1>OmniMerchant 买家咨询</h1>
          <p>{{ session?.welcomeMessage || '可咨询商品、订单、物流、退货、保修和人工客服。' }}</p>
        </div>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </header>

      <div v-if="!connected" class="session-form">
        <a-alert
          type="info"
          show-icon
          message="订单相关问题需要邮箱或手机号校验；公开咨询不会要求管理员登录。"
        />
        <a-input v-model:value="tenantCode" placeholder="请输入后端返回的店铺编码" />
        <a-input v-model:value="customerEmail" placeholder="下单邮箱，可选但查询订单时需要" />
        <a-input v-model:value="customerName" placeholder="称呼，可选" />
        <a-button block type="primary" :loading="starting" @click="startSession">开始咨询</a-button>
      </div>

      <div v-else class="chat">
        <div ref="messagesEl" class="messages">
          <div v-for="(msg, index) in messages" :key="index" class="msg" :class="msg.role">
            <div class="msg-body" v-html="render(msg.text)"></div>
          </div>
          <div v-if="streaming" class="msg assistant">
            <div class="msg-body" v-html="render(streamText || '正在回复...')"></div>
          </div>
        </div>
        <footer class="composer">
          <a-input-search
            v-model:value="input"
            enter-button="发送"
            placeholder="请输入你的问题"
            :disabled="streaming"
            :loading="streaming"
            @search="send()"
          />
        </footer>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { renderSafeMarkdown } from '@/utils/markdown'

const tenantCode = ref('')
const customerEmail = ref('')
const customerName = ref('')
const session = ref<any>(null)
const customerSessionToken = ref('')
const connected = ref(false)
const starting = ref(false)
const streaming = ref(false)
const input = ref('')
const streamText = ref('')
const messages = ref<{ role: string; text: string }[]>([])
const messagesEl = ref<HTMLElement>()

const statusText = computed(() => {
  if (streaming.value) return '回复中'
  if (connected.value) return '已连接'
  return '未连接'
})

const statusColor = computed(() => {
  if (streaming.value) return 'gold'
  return connected.value ? 'green' : 'default'
})

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
        language: 'zh',
      }),
    })
    const body = await readJson(resp)
    if (!resp.ok || body.code !== '200') throw new Error(formatWidgetError(resp.status, body.message))
    if (!body.data?.customerSessionToken) throw new Error('服务未返回客户会话令牌，请刷新后重试')
    session.value = body.data
    customerSessionToken.value = body.data.customerSessionToken
    connected.value = true
    messages.value = [{ role: 'assistant', text: body.data.welcomeMessage || '您好，请问有什么可以帮您？' }]
  } catch (error: any) {
    message.error(formatFetchError(error, '无法创建会话'))
  } finally {
    starting.value = false
  }
}

async function send(text?: string) {
  const userText = (text || input.value).trim()
  if (!userText || streaming.value || !session.value) return
  if (!customerSessionToken.value) {
    message.error('会话已失效，请重新开始咨询')
    resetSession()
    return
  }

  messages.value.push({ role: 'user', text: userText })
  input.value = ''
  streamText.value = ''
  streaming.value = true
  await nextTick()
  scrollToBottom()

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
        message: userText,
        intent: 'UNKNOWN',
      }),
    })
    if (!resp.ok) throw new Error(formatWidgetError(resp.status, (await readJson(resp)).message))
    const reader = resp.body?.getReader()
    if (!reader) throw new Error('响应流为空')

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
      await nextTick()
      scrollToBottom()
    }
    if (streamText.value) messages.value.push({ role: 'assistant', text: streamText.value })
  } catch (error: any) {
    message.error(formatFetchError(error, '回复失败'))
    if (error.message?.includes('会话已过期') || error.message?.includes('重新开始咨询')) {
      resetSession()
    }
  } finally {
    streamText.value = ''
    streaming.value = false
  }
}

function resetSession() {
  connected.value = false
  session.value = null
  customerSessionToken.value = ''
}

function scrollToBottom() {
  messagesEl.value?.scrollTo({ top: messagesEl.value.scrollHeight, behavior: 'smooth' })
}

async function readJson(resp: Response) {
  try {
    return await resp.json()
  } catch {
    return {}
  }
}

function formatWidgetError(status: number, errorMessage?: string) {
  if (status === 400) return errorMessage || '请求内容格式不正确，请刷新页面后重试'
  if (status === 401) return errorMessage || '会话已过期，请重新开始咨询'
  if (status === 403) return errorMessage || '当前会话无权限访问该店铺或订单'
  if (status === 405) return '请求方法错误，请刷新页面使用最新版本'
  if (status >= 500) return '服务异常，请稍后重试或联系人工客服'
  return errorMessage || `请求失败（HTTP ${status}）`
}

function formatFetchError(error: any, fallback: string) {
  const errorMessage = error?.message || ''
  if (errorMessage === 'Failed to fetch' || errorMessage === 'NetworkError when attempting to fetch resource.') {
    return '无法连接服务或地址不一致，请统一使用 http://127.0.0.1:5188 或 http://localhost:5188'
  }
  return errorMessage || fallback
}
</script>

<style scoped>
.widget-shell {
  background: #eef2f7;
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.widget {
  background: #fff;
  border: 1px solid #d9dee8;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: min(760px, calc(100vh - 48px));
  overflow: hidden;
  width: min(760px, 100%);
}

.widget-header {
  align-items: flex-start;
  border-bottom: 1px solid #eef0f3;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 18px 20px;
}

.widget-header h1 {
  color: #1f2937;
  font-size: 20px;
  margin: 0;
}

.widget-header p {
  color: #667085;
  font-size: 13px;
  margin: 6px 0 0;
}

.session-form {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.chat {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.messages {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding: 18px;
}

.msg {
  background: #f4f6f8;
  border-radius: 8px;
  color: #1f2937;
  line-height: 1.6;
  max-width: 82%;
  padding: 10px 12px;
}

.msg.user {
  align-self: flex-end;
  background: #1677ff;
  color: #fff;
}

.msg.assistant {
  align-self: flex-start;
}

.composer {
  border-top: 1px solid #eef0f3;
  padding: 14px;
}

@media (max-width: 640px) {
  .widget-shell {
    padding: 0;
  }

  .widget {
    border-radius: 0;
    height: 100vh;
  }

  .widget-header {
    flex-direction: column;
  }
}
</style>
