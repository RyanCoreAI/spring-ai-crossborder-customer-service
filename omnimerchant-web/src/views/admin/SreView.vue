<template>
  <div>
    <MetricStrip :items="metrics" />
    <a-tabs v-model:active-key="tab" class="section-surface">
      <a-tab-pane key="current" tab="当前状态">
        <DataTableShell
          :columns="sloColumns"
          :rows="summary?.slos || []"
          :loading="loading"
          :error="error"
          row-key="key"
          @retry="load"
        >
          <template #bodyCell="{ column, record }"
            ><template v-if="column.key === 'value'"
              >{{ record.actual ?? "—" }} / {{ record.target ?? "—" }}
              {{ record.unit }}</template
            ><template v-else-if="column.key === 'status'"
              ><StatusTag
                :status="record.status"
                :label="statusLabel(record.status)" /></template
          ></template>
        </DataTableShell>
      </a-tab-pane>
      <a-tab-pane key="history" tab="SLO 历史">
        <DataTableShell
          :columns="historyColumns"
          :rows="snapshots"
          :loading="loading"
          :error="error"
          row-key="id"
          :pagination="false"
          @retry="load"
        >
          <template #bodyCell="{ column, record }"
            ><template v-if="column.key === 'value'"
              >{{ record.actualValue ?? "—" }} / {{ record.targetValue }}
              {{ record.unit }}</template
            ><template v-else-if="column.key === 'status'"
              ><StatusTag
                :status="record.status"
                :label="statusLabel(record.status)" /></template
            ><template v-else-if="column.key === 'time'">{{
              date(record.capturedAt)
            }}</template></template
          >
        </DataTableShell>
      </a-tab-pane>
      <a-tab-pane key="alerts" tab="活动告警">
        <DataTableShell
          :columns="alertColumns"
          :rows="alerts"
          :loading="loading"
          :error="error"
          row-key="id"
          :pagination="false"
          @retry="load"
        >
          <template #bodyCell="{ column, record }"
            ><template v-if="column.key === 'status'"
              ><StatusTag
                :status="record.status"
                :label="alertLabel(record.status)" /></template
            ><template v-else-if="column.key === 'time'">{{
              date(record.lastObservedAt)
            }}</template
            ><template v-else-if="column.key === 'actions'"
              ><a-space
                ><a-button
                  v-if="record.status === 'OPEN'"
                  size="small"
                  @click="ack(record)"
                  >确认</a-button
                ><a-popconfirm
                  v-if="record.status !== 'CLOSED'"
                  title="确认关闭告警？"
                  @confirm="close(record)"
                  ><a-button size="small">关闭</a-button></a-popconfirm
                ></a-space
              ></template
            ></template
          >
        </DataTableShell>
      </a-tab-pane>
      <template #rightExtra
        ><a-button :loading="loading" @click="evaluate"
          ><template #icon><ReloadOutlined /></template>采样并刷新</a-button
        ></template
      >
    </a-tabs>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import type { AlertEvent, SloSnapshot, SreSummary } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const summary = ref<SreSummary | null>(null),
  snapshots = ref<SloSnapshot[]>([]),
  alerts = ref<AlertEvent[]>([]),
  loading = ref(false),
  error = ref(""),
  tab = ref("current");
const metrics = computed(() => [
  {
    key: "webhook",
    label: "Webhook 积压",
    value: summary.value?.webhookBacklog,
    note: "待处理与失败事件",
  },
  {
    key: "trace",
    label: "失败轨迹",
    value: summary.value?.failedTraces,
    note: "当前租户",
  },
  {
    key: "tool",
    label: "失败工具",
    value: summary.value?.failedTools,
    note: "来自工具调用日志",
  },
  {
    key: "eval",
    label: "最新评测通过率",
    value: summary.value?.latestEvalPassRate,
    suffix: "%",
    note: "最近一次运行",
  },
]);
const sloColumns = [
  { title: "SLO", dataIndex: "label" },
  { title: "实际 / 目标", key: "value", width: 210 },
  { title: "状态", key: "status", width: 110 },
];
const historyColumns = [
  { title: "SLO", dataIndex: "sloLabel" },
  { title: "实际 / 目标", key: "value", width: 210 },
  { title: "状态", key: "status", width: 110 },
  { title: "采样时间", key: "time", width: 180 },
];
const alertColumns = [
  { title: "级别", dataIndex: "severity", width: 90 },
  { title: "类别", dataIndex: "category", width: 140 },
  { title: "告警", dataIndex: "message" },
  { title: "状态", key: "status", width: 110 },
  { title: "最近发生", key: "time", width: 180 },
  { title: "操作", key: "actions", width: 120 },
];
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [a, b, c] = await Promise.all([
      api.get("/sre/summary"),
      api.get("/sre/snapshots", { params: { limit: 100 } }),
      api.get("/sre/alerts", { params: { limit: 100 } }),
    ]);
    summary.value = a.data;
    snapshots.value = b.data || [];
    alerts.value = c.data || [];
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
async function evaluate() {
  await api.post("/sre/evaluate");
  await load();
}
async function ack(value: AlertEvent) {
  await api.post(`/sre/alerts/${value.id}/acknowledge`, {
    note: "已在生产健康页确认",
  });
  await load();
}
async function close(value: AlertEvent) {
  await api.post(`/sre/alerts/${value.id}/close`, { note: "问题已处理" });
  await load();
}
function statusLabel(value: string) {
  const labels: Record<string, string> = { OK: "正常", WARN: "需关注", BREACH: "超出目标" };
  return labels[value] || value || "—";
}
function alertLabel(value: string) {
  const labels: Record<string, string> = { OPEN: "未处理", ACKNOWLEDGED: "已确认", CLOSED: "已关闭" };
  return labels[value] || value || "—";
}
function date(value: string) {
  return value ? new Date(value).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>
<style scoped>
.section-surface {
  padding: 0 14px 14px;
}
</style>
