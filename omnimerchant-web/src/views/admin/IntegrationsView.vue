<template>
  <div>
    <div class="page-head">
      <a-tag color="blue">Shopify 优先</a-tag>
    </div>

    <a-tabs v-model:active-key="activeTab" class="integration-tabs">
      <a-tab-pane key="shopify" tab="Shopify">
        <a-card class="block">
          <a-form layout="vertical" class="form">
            <a-row :gutter="16">
              <a-col :span="24">
                <a-form-item label="Shopify 店铺域名">
                  <a-input
                    v-model:value="form.shopDomain"
                    placeholder="your-store.myshopify.com"
                  />
                </a-form-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="Admin API Token（开发兜底）">
                  <a-input-password
                    v-model:value="form.adminApiToken"
                    placeholder="shpat_..."
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="Webhook Secret">
                  <a-input-password v-model:value="form.webhookSecret" />
                </a-form-item>
              </a-col>
            </a-row>

            <a-space wrap>
              <a-button type="primary" :loading="connecting" @click="connect"
                >保存 Custom App 凭证</a-button
              >
              <a-button :loading="installing" @click="installOauth"
                >生成 OAuth 安装链接</a-button
              >
              <a-button :loading="syncing" @click="sync"
                >同步商品、客户和订单</a-button
              >
            </a-space>
          </a-form>

          <a-alert
            class="notice"
            type="warning"
            show-icon
            message="退款、取消订单和改地址仍必须进入内部审批流；智能体不会直接写外部 Shopify。"
          />

          <a-descriptions v-if="lastResult" :column="1" bordered class="result">
            <a-descriptions-item label="状态">{{
              lastResult.status || "-"
            }}</a-descriptions-item>
            <a-descriptions-item label="消息">{{
              lastResult.message || "-"
            }}</a-descriptions-item>
            <a-descriptions-item
              v-if="lastResult.installUrl"
              label="OAuth 安装链接"
            >
              <a :href="lastResult.installUrl" target="_blank" rel="noreferrer"
                >打开安装链接</a
              >
            </a-descriptions-item>
            <a-descriptions-item
              v-if="lastResult.customers !== undefined"
              label="客户"
            >
              {{ lastResult.customers }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="lastResult.orders !== undefined"
              label="订单"
            >
              {{ lastResult.orders }}
            </a-descriptions-item>
            <a-descriptions-item
              v-if="lastResult.products !== undefined"
              label="商品"
            >
              {{ lastResult.products }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :xl="12">
            <a-card title="同步任务">
              <a-table
                :columns="jobColumns"
                :data-source="jobs"
                :pagination="false"
                row-key="id"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'">
                    <a-tag :color="statusColor(record.status)">{{
                      statusLabel(record.status)
                    }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" @click="retryJob(record)"
                      >重试</a-button
                    >
                  </template>
                </template>
              </a-table>
            </a-card>
          </a-col>
          <a-col :xs="24" :xl="12">
            <a-card title="Webhook 死信与重放">
              <a-table
                :columns="webhookColumns"
                :data-source="webhooks"
                :pagination="false"
                row-key="id"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'signatureValid'">
                    <a-tag :color="record.signatureValid ? 'green' : 'red'">
                      {{ record.signatureValid ? "有效" : "无效" }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'status'">
                    <a-tag :color="statusColor(record.status)">{{
                      statusLabel(record.status)
                    }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" @click="replay(record)"
                      >重放</a-button
                    >
                  </template>
                </template>
              </a-table>
            </a-card>
          </a-col>
        </a-row>

        <a-row :gutter="[16, 16]" class="secondary-row">
          <a-col :xs="24" :xl="14">
            <a-card title="大店初始同步（Bulk Operation）">
              <a-space class="bulk-actions" wrap>
                <a-select v-model:value="bulkResource" style="width: 150px">
                  <a-select-option value="products">商品</a-select-option>
                  <a-select-option value="customers">客户</a-select-option>
                  <a-select-option value="orders">订单</a-select-option>
                </a-select>
                <a-button
                  type="primary"
                  :loading="bulkStarting"
                  @click="startBulk"
                  >启动异步同步</a-button
                >
              </a-space>
              <a-table
                :columns="bulkColumns"
                :data-source="bulkOperations"
                :pagination="false"
                row-key="id"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'">
                    <a-tag :color="statusColor(record.status)">{{
                      statusLabel(record.status)
                    }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'resultReady'">
                    <a-tag :color="record.resultReady ? 'green' : 'default'">{{
                      record.resultReady ? "可导入" : "未就绪"
                    }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-space size="small">
                      <a-button
                        type="link"
                        size="small"
                        @click="refreshBulk(record)"
                        >刷新</a-button
                      >
                      <a-button
                        v-if="
                          record.resultReady && record.status === 'COMPLETED'
                        "
                        type="link"
                        size="small"
                        @click="importBulk(record)"
                        >导入</a-button
                      >
                    </a-space>
                  </template>
                </template>
              </a-table>
            </a-card>
          </a-col>
          <a-col :xs="24" :xl="10">
            <a-card title="隐私合规 Webhook">
              <a-alert
                type="info"
                show-icon
                message="仅显示处理元数据和记录数；客户邮箱与原始 payload 不会返回前端。"
              />
              <a-table
                class="privacy-table"
                :columns="privacyColumns"
                :data-source="privacyRequests"
                :pagination="false"
                row-key="id"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'">
                    <a-tag :color="statusColor(record.status)">{{
                      statusLabel(record.status)
                    }}</a-tag>
                  </template>
                </template>
              </a-table>
            </a-card>
          </a-col>
        </a-row>
      </a-tab-pane>

      <a-tab-pane key="domestic" tab="国内平台">
        <a-alert
          class="block"
          type="info"
          show-icon
          message="国内平台只展示后端真实状态：没有开放平台授权时显示 Fixture 或等待授权，不伪装为已接通淘宝、京东、拼多多或抖店。"
        />
        <a-card title="国内平台连接状态">
          <a-table
            :columns="domesticColumns"
            :data-source="domesticPlatforms"
            :pagination="false"
            row-key="platform"
          >
            <template #bodyCell="{ column, record }">
              <a-tag
                v-if="column.key === 'mode'"
                :color="connectionColor(record.mode)"
              >
                {{ connectionLabel(record.mode) }}
              </a-tag>
              <a-tag
                v-else-if="column.key === 'connectionStatus'"
                :color="connectionColor(record.connectionStatus)"
              >
                {{ connectionLabel(record.connectionStatus) }}
              </a-tag>
            </template>
          </a-table>
          <a-space class="domestic-actions" wrap>
            <a-button
              type="primary"
              :loading="domesticSyncing"
              @click="syncDouyinFixture"
            >
              导入抖店 Fixture
            </a-button>
            <a-button
              :loading="domesticWebhooking"
              @click="sendDouyinFixtureWebhook"
            >
              模拟抖店 Webhook
            </a-button>
          </a-space>
          <a-descriptions
            v-if="domesticResult"
            class="result"
            :column="2"
            bordered
          >
            <a-descriptions-item label="平台">{{
              domesticResult.platform
            }}</a-descriptions-item>
            <a-descriptions-item label="模式">{{
              domesticResult.mode
            }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{
              domesticResult.status
            }}</a-descriptions-item>
            <a-descriptions-item label="客户">{{
              domesticResult.customers
            }}</a-descriptions-item>
            <a-descriptions-item label="订单">{{
              domesticResult.orders
            }}</a-descriptions-item>
            <a-descriptions-item label="商品">{{
              domesticResult.products
            }}</a-descriptions-item>
            <a-descriptions-item label="说明" :span="2">{{
              domesticResult.message
            }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import api from "@/api";
import type {
  DomesticPlatform,
  IntegrationResult,
  ShopifyBulkOperation,
  ShopifyJob,
  ShopifyPrivacyRequest,
  ShopifyWebhook,
} from "@/types/contracts";

const connecting = ref(false);
const installing = ref(false);
const syncing = ref(false);
const domesticSyncing = ref(false);
const domesticWebhooking = ref(false);
const bulkStarting = ref(false);
const activeTab = ref("shopify");
const lastResult = ref<IntegrationResult | null>(null);
const domesticResult = ref<IntegrationResult | null>(null);
const jobs = ref<ShopifyJob[]>([]);
const webhooks = ref<ShopifyWebhook[]>([]);
const bulkOperations = ref<ShopifyBulkOperation[]>([]);
const privacyRequests = ref<ShopifyPrivacyRequest[]>([]);
const bulkResource = ref("products");
const domesticPlatforms = ref<DomesticPlatform[]>([]);
const form = reactive({ shopDomain: "", adminApiToken: "", webhookSecret: "" });

const jobColumns = [
  { title: "资源", dataIndex: "resource", width: 120 },
  { title: "状态", dataIndex: "status", key: "status", width: 110 },
  { title: "尝试次数", dataIndex: "attempts", width: 90 },
  { title: "导入数量", dataIndex: "importedCount", width: 90 },
  { title: "最近错误", dataIndex: "lastError", ellipsis: true },
  { title: "操作", key: "actions", width: 90 },
];

const webhookColumns = [
  { title: "主题", dataIndex: "topic", ellipsis: true },
  { title: "状态", dataIndex: "status", key: "status", width: 100 },
  {
    title: "签名",
    dataIndex: "signatureValid",
    key: "signatureValid",
    width: 90,
  },
  { title: "处理次数", dataIndex: "processAttempts", width: 90 },
  { title: "最近错误", dataIndex: "lastError", ellipsis: true },
  { title: "操作", key: "actions", width: 90 },
];

const bulkColumns = [
  { title: "资源", dataIndex: "resource", width: 90 },
  { title: "状态", dataIndex: "status", key: "status", width: 100 },
  { title: "对象数", dataIndex: "objectCount", width: 85 },
  { title: "结果", dataIndex: "resultReady", key: "resultReady", width: 90 },
  { title: "错误", dataIndex: "errorCode", ellipsis: true },
  { title: "操作", key: "actions", width: 110 },
];

const privacyColumns = [
  { title: "主题", dataIndex: "topic", ellipsis: true },
  { title: "状态", dataIndex: "status", key: "status", width: 100 },
  { title: "处理记录", dataIndex: "affectedRecords", width: 90 },
  { title: "完成时间", dataIndex: "completedAt", width: 165 },
];

const domesticColumns = [
  { title: "平台", dataIndex: "platformLabel", width: 170 },
  { title: "模式", dataIndex: "mode", key: "mode", width: 120 },
  { title: "授权", dataIndex: "authStatus", width: 150 },
  {
    title: "连接状态",
    dataIndex: "connectionStatus",
    key: "connectionStatus",
    width: 130,
  },
  { title: "最近同步", dataIndex: "lastSyncAt", width: 190 },
  { title: "待审批动作", dataIndex: "pendingActionRequests", width: 110 },
  { title: "证据", dataIndex: "evidence", ellipsis: true },
];

function statusColor(status: string) {
  if (["SUCCESS", "COMPLETED", "IMPORTED"].includes(status)) return "green";
  if (["FAILED", "DEAD"].includes(status)) return "red";
  if (["PROCESSING", "RUNNING"].includes(status)) return "blue";
  return "default";
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    RECEIVED: "已接收",
    PROCESSING: "处理中",
    SUCCESS: "成功",
    FAILED: "失败",
    DEAD: "死信",
    RUNNING: "运行中",
    COMPLETED: "已完成",
    CREATED: "已创建",
    CANCELED: "已取消",
    EXPIRED: "已过期",
    IMPORTED: "已导入",
    PENDING: "待处理",
  };
  return labels[status] || status || "-";
}

function connectionColor(status: string) {
  if (status === "LIVE") return "green";
  if (status === "FIXTURE") return "blue";
  if (status === "ERROR") return "red";
  if (status === "DEGRADED") return "orange";
  return "default";
}

function connectionLabel(status: string) {
  const labels: Record<string, string> = {
    LIVE: "真实连接",
    FIXTURE: "Fixture 演示",
    WAITING_CREDENTIALS: "等待商家授权",
    DEGRADED: "降级",
    ERROR: "错误",
    DISABLED: "已停用",
  };
  return labels[status] || status || "—";
}

async function loadOps() {
  try {
    const [jobRes, webhookRes, bulkRes, privacyRes] = await Promise.all([
      api.get("/integrations/shopify/jobs", { params: { page: 1, size: 50 } }),
      api.get("/integrations/shopify/webhooks", {
        params: { page: 1, size: 50 },
      }),
      api.get("/integrations/shopify/bulk", { params: { page: 1, size: 50 } }),
      api.get("/integrations/shopify/privacy-requests", {
        params: { page: 1, size: 50 },
      }),
    ]);
    jobs.value = jobRes.data?.records || [];
    webhooks.value = webhookRes.data?.records || [];
    bulkOperations.value = bulkRes.data?.records || [];
    privacyRequests.value = privacyRes.data?.records || [];
    domesticPlatforms.value =
      (await api.get("/integrations/domestic/platforms")).data || [];
  } catch {
    jobs.value = [];
    webhooks.value = [];
    bulkOperations.value = [];
    privacyRequests.value = [];
    domesticPlatforms.value = [];
  }
}

async function connect() {
  connecting.value = true;
  try {
    const res = await api.post("/integrations/shopify/connect", form);
    lastResult.value = res.data;
    message.success("Shopify 凭证已保存");
    loadOps();
  } finally {
    connecting.value = false;
  }
}

async function installOauth() {
  installing.value = true;
  try {
    const res = await api.get("/integrations/shopify/install", {
      params: { shop: form.shopDomain },
    });
    lastResult.value = res.data;
    if (res.data?.installUrl) window.open(res.data.installUrl, "_blank");
  } finally {
    installing.value = false;
  }
}

async function sync() {
  syncing.value = true;
  try {
    const res = await api.post("/integrations/shopify/sync");
    lastResult.value = res.data;
    message.success("同步任务已触发");
    loadOps();
  } finally {
    syncing.value = false;
  }
}

async function retryJob(row: ShopifyJob) {
  await api.post(`/integrations/shopify/jobs/${row.id}/retry`);
  message.success("已排队重试");
  loadOps();
}

async function replay(row: ShopifyWebhook) {
  await api.post(`/integrations/shopify/webhooks/${row.id}/replay`);
  message.success("Webhook 已重放");
  loadOps();
}

async function startBulk() {
  bulkStarting.value = true;
  try {
    await api.post("/integrations/shopify/bulk", {
      resource: bulkResource.value,
    });
    message.success("Bulk Operation 已启动");
    await loadOps();
  } finally {
    bulkStarting.value = false;
  }
}

async function refreshBulk(row: ShopifyBulkOperation) {
  await api.post(`/integrations/shopify/bulk/${row.id}/refresh`);
  message.success("Bulk Operation 状态已刷新");
  await loadOps();
}

async function importBulk(row: ShopifyBulkOperation) {
  lastResult.value = (
    await api.post(`/integrations/shopify/bulk/${row.id}/import`)
  ).data;
  message.success("Bulk 结果已导入本地缓存");
  await loadOps();
}

async function syncDouyinFixture() {
  domesticSyncing.value = true;
  try {
    domesticResult.value = (
      await api.post("/integrations/domestic/douyin/fixture-sync")
    ).data;
    message.success("抖店 Fixture 已导入");
    loadOps();
  } finally {
    domesticSyncing.value = false;
  }
}

async function sendDouyinFixtureWebhook() {
  domesticWebhooking.value = true;
  try {
    domesticResult.value = (
      await api.post("/integrations/domestic/douyin/fixture-webhook", {
        topic: "order_status_changed",
        orderId: "douyin-order-9001",
      })
    ).data;
    message.success("抖店 Fixture Webhook 已处理");
    loadOps();
  } finally {
    domesticWebhooking.value = false;
  }
}

onMounted(loadOps);
</script>

<style scoped>
.form {
  max-width: 920px;
}

.notice,
.result {
  margin-top: 18px;
}

.block {
  margin-bottom: 16px;
}

.integration-tabs {
  margin-top: 8px;
}

.domestic-actions {
  margin-top: 16px;
}

.secondary-row {
  margin-top: 16px;
}

.bulk-actions {
  margin-bottom: 12px;
}

.privacy-table {
  margin-top: 12px;
}
</style>
