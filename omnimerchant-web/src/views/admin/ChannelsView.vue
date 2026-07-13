<template>
  <div>
    <MetricStrip :items="metrics" /><PageToolbar class="section-row"
      ><a-select
        v-model:value="filter"
        allow-clear
        placeholder="全部连接状态"
        style="width: 180px"
        ><a-select-option value="LIVE">真实连接</a-select-option
        ><a-select-option value="FIXTURE">Fixture 演示</a-select-option
        ><a-select-option value="WAITING_CREDENTIALS"
          >等待凭据</a-select-option
        ></a-select
      ><template #actions
        ><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      ></PageToolbar
    ><DataTableShell
      :columns="columns"
      :rows="filtered"
      :loading="loading"
      :error="error"
      row-key="id"
      :scroll="{ x: 980 }"
      @retry="load"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'channel'"
          ><strong>{{ record.channelLabel }}</strong
          ><small>{{ record.accountName || "未配置账号" }}</small></template
        ><template v-else-if="column.key === 'status'"
          ><ConnectionBadge
            :status="normalize(record.adapterStatus)" /></template
        ><template v-else-if="column.key === 'direction'"
          >{{ record.inboundEnabled ? "入站" : ""
          }}{{ record.inboundEnabled && record.outboundEnabled ? " / " : ""
          }}{{ record.outboundEnabled ? "出站" : "—" }}</template
        ><template v-else-if="column.key === 'event'">{{
          date(record.lastEventAt)
        }}</template></template
      ></DataTableShell
    ><a-alert
      class="section-row"
      type="info"
      show-icon
      message="连接状态由后端账号配置和最近事件决定"
      description="没有真实凭据时只显示 Fixture 或等待凭据，不会伪装成已接入。"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import ConnectionBadge from "@/components/admin/ConnectionBadge.vue";
import type { ChannelAccount, ChannelSummary } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const rows = ref<ChannelAccount[]>([]),
  summary = ref<ChannelSummary[]>([]),
  loading = ref(false),
  error = ref(""),
  filter = ref<string>();
const columns = [
  { title: "渠道", key: "channel", width: 220 },
  { title: "连接状态", key: "status", width: 150 },
  { title: "收发能力", key: "direction", width: 120 },
  { title: "鉴权方式", dataIndex: "authMode", width: 160 },
  { title: "Webhook", dataIndex: "webhookStatus", width: 140 },
  { title: "最近事件", key: "event", width: 180 },
  { title: "最近错误", dataIndex: "lastError", ellipsis: true },
];
const filtered = computed(() =>
  filter.value
    ? rows.value.filter((x) => normalize(x.adapterStatus) === filter.value)
    : rows.value,
);
const metrics = computed(() => [
  {
    key: "accounts",
    label: "渠道账号",
    value: rows.value.length,
    note: "当前租户",
  },
  {
    key: "live",
    label: "真实连接",
    value: rows.value.filter((x) => normalize(x.adapterStatus) === "LIVE")
      .length,
    note: "后端认定 LIVE",
  },
  {
    key: "fixture",
    label: "Fixture 演示",
    value: rows.value.filter((x) => normalize(x.adapterStatus) === "FIXTURE")
      .length,
    note: "非真实授权",
  },
  {
    key: "messages",
    label: "已记录渠道会话",
    value: summary.value.reduce((n, x) => n + Number(x.conversations || 0), 0),
    note: "来自会话表",
  },
]);
function normalize(v: string) {
  const s = String(v || "").toUpperCase();
  if (["CONNECTED", "LIVE"].includes(s)) return "LIVE";
  if (s === "FIXTURE") return "FIXTURE";
  if (["ADAPTER_READY", "NOT_CONFIGURED", "WAITING_CREDENTIALS"].includes(s))
    return "WAITING_CREDENTIALS";
  return s || "DISABLED";
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [a, b] = await Promise.all([
      api.get("/channels/accounts"),
      api.get("/channels/summary"),
    ]);
    rows.value = a.data || [];
    summary.value = b.data || [];
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function date(v: string) {
  return v ? new Date(v).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>
<style scoped>
small {
  display: block;
  margin-top: 3px;
  color: #98a2b3;
}
</style>
