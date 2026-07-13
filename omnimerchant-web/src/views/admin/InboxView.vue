<template>
  <div class="inbox-view">
    <PageToolbar>
      <a-radio-group
        v-model:value="queue"
        button-style="solid"
        size="small"
        @change="loadItems"
      >
        <a-radio-button
          v-for="item in queues"
          :key="item.queueKey"
          :value="item.queueKey"
        >
          {{ item.queueLabel }}
          <span class="queue-count">{{ item.count }}</span>
        </a-radio-button>
      </a-radio-group>
      <template #actions>
        <a-button :loading="loading" @click="refresh"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        >
      </template>
    </PageToolbar>

    <div class="inbox-shell">
      <section class="conversation-list">
        <div class="panel-title">
          <strong>会话</strong><span>{{ total }} 条</span>
        </div>
        <BackendEmptyState
          v-if="loading || error || !items.length"
          :loading="loading"
          :error="error"
          description="当前队列没有会话"
          @retry="loadItems"
        />
        <button
          v-for="item in items"
          v-else
          :key="item.conversationUuid || item.ticketId"
          class="conversation-item"
          :class="{
            active: selected?.conversationUuid === item.conversationUuid,
          }"
          type="button"
          @click="selectItem(item)"
        >
          <span class="avatar">{{
            initial(item.customerName || item.customerEmail)
          }}</span>
          <span class="conversation-copy">
            <span class="conversation-line"
              ><strong>{{
                item.customerName || item.customerEmail || "未知客户"
              }}</strong
              ><small>{{ relative(item.lastMessageAt) }}</small></span
            >
            <span class="conversation-line"
              ><span>{{ item.intent || item.channelLabel || "普通咨询" }}</span
              ><StatusTag
                :status="item.slaState || item.statusLabel"
                :label="
                  item.slaState ? slaLabel(item.slaState) : item.statusLabel
                "
            /></span>
            <small class="conversation-summary">{{
              item.latestTicketNo
                ? `${item.latestTicketNo} · ${item.latestTicketStatus}`
                : `${item.messageCount || 0} 条消息`
            }}</small>
          </span>
        </button>
      </section>

      <section class="message-workspace">
        <BackendEmptyState
          v-if="!selected"
          description="从左侧选择一条会话开始处理"
        />
        <template v-else>
          <div class="conversation-header">
            <div>
              <strong>{{
                selected.customerName || selected.customerEmail
              }}</strong
              ><span
                >{{ selected.channelLabel }} ·
                {{ selected.intent || "未分类" }}</span
              >
            </div>
            <div class="header-actions">
              <a-button v-if="compact" @click="contextDrawerOpen = true"
                ><template #icon><ProfileOutlined /></template>上下文</a-button
              >
              <a-button v-if="selected.status !== 4" @click="takeover"
                ><template #icon><UserSwitchOutlined /></template>接管</a-button
              >
              <StatusTag
                :status="selected.statusLabel"
                :label="selected.statusLabel"
              />
            </div>
          </div>
          <div class="message-scroll">
            <BackendEmptyState
              v-if="contextLoading || contextError"
              :loading="contextLoading"
              :error="contextError"
              @retry="loadContext"
            />
            <div
              v-for="message in context?.messages || []"
              v-else
              :key="message.messageUuid"
              class="message-row"
              :class="message.role"
            >
              <div class="message-meta">
                <strong>{{ roleLabel(message.role) }}</strong
                ><span>{{ formatTime(message.createdAt) }}</span>
              </div>
              <div class="message-bubble">{{ message.content }}</div>
              <div v-if="message.translatedContent" class="translation-note">
                <TranslationOutlined /> Agent 输入：{{
                  message.translatedContent
                }}
                <span
                  >{{ message.translationProvider }} ·
                  {{ message.translationLatencyMs }} ms</span
                >
              </div>
            </div>
          </div>
          <div class="composer">
            <div class="composer-tools">
              <a-select
                v-model:value="selectedMacro"
                allow-clear
                placeholder="插入宏回复"
                style="width: 220px"
                @change="applyMacro"
              >
                <a-select-option
                  v-for="macro in macros"
                  :key="macro.macroCode"
                  :value="macro.macroCode"
                  >{{ macro.title }}</a-select-option
                >
              </a-select>
              <span>人工回复将写入完整会话记录</span>
            </div>
            <a-textarea
              v-model:value="reply"
              :rows="3"
              :maxlength="2000"
              show-count
              placeholder="输入给客户的回复"
            />
            <div class="composer-actions">
              <a-checkbox v-model:checked="closeAfterReply"
                >回复后标记已解决</a-checkbox
              ><a-button
                type="primary"
                :disabled="!reply.trim()"
                :loading="sending"
                @click="sendReply"
                ><template #icon><SendOutlined /></template>发送回复</a-button
              >
            </div>
          </div>
        </template>
      </section>

      <aside v-if="!compact" class="context-panel">
        <InboxContextPanel :context="context" />
      </aside>
    </div>

    <a-drawer
      v-model:open="contextDrawerOpen"
      title="客户与业务上下文"
      placement="right"
      :width="360"
      ><InboxContextPanel :context="context"
    /></a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import {
  ProfileOutlined,
  ReloadOutlined,
  SendOutlined,
  TranslationOutlined,
  UserSwitchOutlined,
} from "@ant-design/icons-vue";
import api from "@/api";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import BackendEmptyState from "@/components/admin/BackendEmptyState.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import InboxContextPanel from "@/components/admin/inbox/InboxContextPanel.vue";
import type {
  InboxContext,
  InboxWorkItem,
  QueueBucket,
  SupportMacro,
} from "@/types/inbox";

const queues = ref<QueueBucket[]>([]),
  items = ref<InboxWorkItem[]>([]),
  macros = ref<SupportMacro[]>([]);
const queue = ref("unassigned"),
  total = ref(0),
  selected = ref<InboxWorkItem | null>(null),
  context = ref<InboxContext | null>(null);
const loading = ref(false),
  contextLoading = ref(false),
  sending = ref(false);
const error = ref(""),
  contextError = ref(""),
  reply = ref(""),
  selectedMacro = ref<string>(),
  closeAfterReply = ref(false);
const contextDrawerOpen = ref(false),
  viewport = ref(window.innerWidth);
const compact = computed(() => viewport.value < 1280);

async function refresh() {
  await Promise.all([loadQueues(), loadItems(), loadMacros()]);
}
async function loadQueues() {
  const r = await api.get("/inbox/queues");
  queues.value = r.data || [];
  if (!queues.value.some((item) => item.queueKey === queue.value))
    queue.value = queues.value[0]?.queueKey || "all";
}
async function loadItems() {
  loading.value = true;
  error.value = "";
  try {
    const r = await api.get("/inbox/items", {
      params: { queue: queue.value, page: 1, size: 50 },
    });
    items.value = r.data?.records || [];
    total.value = r.data?.total || 0;
    if (
      selected.value &&
      !items.value.some(
        (item) => item.conversationUuid === selected.value?.conversationUuid,
      )
    ) {
      selected.value = null;
      context.value = null;
    }
    if (!selected.value && items.value.length && window.innerWidth >= 768) {
      await selectItem(items.value[0]);
    }
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : "加载失败";
  } finally {
    loading.value = false;
  }
}
async function loadMacros() {
  try {
    const r = await api.get("/macros");
    macros.value = r.data || [];
  } catch {
    macros.value = [];
  }
}
async function selectItem(item: InboxWorkItem) {
  if (!item.conversationUuid) {
    message.warning("该工单暂无关联会话");
    return;
  }
  selected.value = item;
  await loadContext();
}
async function loadContext() {
  if (!selected.value?.conversationUuid) return;
  contextLoading.value = true;
  contextError.value = "";
  try {
    const r = await api.get(
      `/inbox/${selected.value.conversationUuid}/context`,
    );
    context.value = r.data;
  } catch (cause: unknown) {
    contextError.value = cause instanceof Error ? cause.message : "上下文加载失败";
  } finally {
    contextLoading.value = false;
  }
}
async function takeover() {
  if (!selected.value) return;
  await api.post(`/inbox/${selected.value.conversationUuid}/takeover`, {
    note: "客服工作台人工接管",
  });
  message.success("已接管会话");
  await refresh();
  await loadContext();
}
async function sendReply() {
  if (!reply.value.trim() || !selected.value) return;
  sending.value = true;
  try {
    await api.post(`/inbox/${selected.value.conversationUuid}/reply`, {
      message: reply.value.trim(),
      closeAfterReply: closeAfterReply.value,
    });
    reply.value = "";
    closeAfterReply.value = false;
    selectedMacro.value = undefined;
    message.success("回复已写入会话");
    await refresh();
    await loadContext();
  } finally {
    sending.value = false;
  }
}
function applyMacro(code: string) {
  const macro = macros.value.find((item) => item.macroCode === code);
  if (macro) reply.value = macro.content;
}
function initial(v: string) {
  return (v || "?").trim().charAt(0).toUpperCase();
}
function roleLabel(role: string) {
  return role === "user"
    ? "客户"
    : role === "assistant"
      ? "AI 客服"
      : role === "tool"
        ? "工具"
        : "系统";
}
function slaLabel(v: string) {
  return (
    ({ BREACHED: "已超时", DUE_SOON: "即将超时", NORMAL: "正常" } as any)[v] ||
    v ||
    "—"
  );
}
function formatTime(v: string) {
  return v
    ? new Date(v).toLocaleString("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
}
function relative(v: string) {
  if (!v) return "";
  const m = Math.max(
    1,
    Math.round((Date.now() - new Date(v).getTime()) / 60000),
  );
  return m < 60
    ? `${m}分钟`
    : m < 1440
      ? `${Math.floor(m / 60)}小时`
      : `${Math.floor(m / 1440)}天`;
}
function resize() {
  viewport.value = window.innerWidth;
}
onMounted(() => {
  window.addEventListener("resize", resize);
  void refresh();
});
onBeforeUnmount(() => window.removeEventListener("resize", resize));
</script>

<style scoped>
.queue-count {
  margin-left: 4px;
  color: #667085;
}
.inbox-shell {
  height: calc(100vh - 154px);
  min-height: 620px;
  display: grid;
  grid-template-columns: 310px minmax(420px, 1fr) 310px;
  margin-top: 12px;
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.conversation-list,
.message-workspace,
.context-panel {
  min-width: 0;
}
.conversation-list {
  border-right: 1px solid var(--omni-border);
  overflow: auto;
}
.panel-title {
  height: 49px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--omni-border);
}
.panel-title span {
  color: #98a2b3;
  font-size: 12px;
}
.conversation-item {
  width: 100%;
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 10px;
  padding: 13px 12px;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.conversation-item:hover,
.conversation-item.active {
  background: #f4f8ff;
}
.conversation-item.active {
  box-shadow: inset 3px 0 #1677ff;
}
.avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eaf2ff;
  color: #175cd3;
  font-weight: 700;
}
.conversation-copy {
  min-width: 0;
}
.conversation-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.conversation-line strong,
.conversation-line > span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conversation-line strong {
  font-size: 13px;
}
.conversation-line small,
.conversation-summary {
  color: #98a2b3;
  font-size: 11px;
}
.conversation-summary {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.message-workspace {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}
.conversation-header {
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--omni-border);
  background: #fff;
}
.conversation-header > div:first-child {
  display: flex;
  flex-direction: column;
}
.conversation-header span {
  margin-top: 3px;
  color: #667085;
  font-size: 11px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.message-scroll {
  flex: 1;
  overflow: auto;
  padding: 18px;
}
.message-row {
  max-width: 78%;
  margin-bottom: 16px;
}
.message-row.user {
  margin-left: auto;
}
.message-meta {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 5px;
  color: #98a2b3;
  font-size: 11px;
}
.message-bubble {
  padding: 10px 12px;
  border: 1px solid #dde5ef;
  border-radius: 6px;
  background: #fff;
  color: #344054;
  line-height: 1.65;
  white-space: pre-wrap;
}
.message-row.user .message-bubble {
  border-color: #cfe0ff;
  background: #eef5ff;
}
.translation-note {
  margin-top: 5px;
  color: #667085;
  font-size: 11px;
}
.translation-note span {
  margin-left: 8px;
  color: #98a2b3;
}
.composer {
  padding: 12px;
  border-top: 1px solid var(--omni-border);
  background: #fff;
}
.composer-tools,
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.composer-tools {
  margin-bottom: 8px;
  color: #98a2b3;
  font-size: 11px;
}
.composer-actions {
  margin-top: 8px;
}
.context-panel {
  overflow: auto;
  border-left: 1px solid var(--omni-border);
  background: #fff;
}
.context-panel :deep(.context-content) {
  padding: 0 16px;
}
.context-panel :deep(.context-section) {
  padding: 15px 0;
  border-bottom: 1px solid #edf0f4;
}
.context-panel :deep(.context-section:last-child) {
  border-bottom: 0;
}
.context-panel :deep(.context-section h3) {
  margin: 0 0 10px;
  font-size: 13px;
}
.context-panel :deep(.context-line) {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 7px 0;
  font-size: 12px;
}
.context-panel :deep(.context-line span) {
  color: #667085;
}
.context-panel :deep(.context-line strong) {
  max-width: 62%;
  text-align: right;
  font-weight: 600;
  word-break: break-word;
}
.context-panel :deep(.context-record) {
  display: flex;
  flex-direction: column;
  padding: 8px 0;
  border-bottom: 1px dashed #e5eaf0;
}
.context-panel :deep(.context-record:last-child) {
  border-bottom: 0;
}
.context-panel :deep(.context-record strong) {
  font-size: 12px;
}
.context-panel :deep(.context-record span),
.context-panel :deep(.context-record small),
.context-panel :deep(.empty-inline) {
  margin-top: 3px;
  color: #667085;
  font-size: 11px;
}
.inbox-view :deep(.backend-state) {
  border: 0;
  border-radius: 0;
}
.inbox-view :deep(.ant-radio-button-wrapper) {
  padding-inline: 10px;
}
@media (max-width: 1279px) {
  .inbox-shell {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}
@media (max-width: 767px) {
  .inbox-shell {
    height: auto;
    min-height: calc(100vh - 190px);
    grid-template-columns: 1fr;
  }
  .conversation-list {
    display: v-bind("selected ? 'none' : 'block'");
    border-right: 0;
  }
  .message-workspace {
    display: v-bind("selected ? 'flex' : 'none'");
    min-height: 650px;
  }
  .message-row {
    max-width: 92%;
  }
  .composer-tools {
    align-items: flex-start;
    flex-direction: column;
  }
  .page-toolbar :deep(.ant-radio-group) {
    display: flex;
    width: max-content;
    max-width: none;
    flex: 0 0 auto;
  }
  .page-toolbar :deep(.ant-radio-button-wrapper) {
    flex: 0 0 auto;
    white-space: nowrap;
  }
  .conversation-header {
    padding: 0 10px;
  }
  .message-scroll {
    padding: 12px;
  }
}
</style>
