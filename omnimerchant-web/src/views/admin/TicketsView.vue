<template>
  <div>
    <MetricStrip :items="metrics" />
    <PageToolbar class="section-row">
      <a-select
        v-model:value="status"
        allow-clear
        placeholder="全部状态"
        style="width: 170px"
        @change="load"
      >
        <a-select-option value="OPEN">待分配</a-select-option
        ><a-select-option value="ASSIGNED">处理中</a-select-option
        ><a-select-option value="WAITING_CUSTOMER">待客户回复</a-select-option
        ><a-select-option value="PENDING_APPROVAL">待审批</a-select-option
        ><a-select-option value="RESOLVED">已解决</a-select-option>
      </a-select>
      <template #actions
        ><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      >
    </PageToolbar>
    <DataTableShell
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      :scroll="{ x: 1050 }"
      @retry="load"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'ticket'"
          ><a-button
            type="link"
            class="link-cell"
            @click="openDetail(record)"
            >{{ record.ticketNo }}</a-button
          ><small class="cell-note">{{ record.subject }}</small></template
        >
        <template v-else-if="column.key === 'priority'"
          ><a-tag :color="priorityColor(record.priority)">{{
            priorityLabel(record.priority)
          }}</a-tag></template
        >
        <template v-else-if="column.key === 'status'"
          ><StatusTag :status="record.status" :label="record.statusLabel"
        /></template>
        <template v-else-if="column.key === 'sla'"
          ><StatusTag
            :status="record.slaState"
            :label="slaLabel(record.slaState)"
        /></template>
        <template v-else-if="column.key === 'assignee'">{{
          record.assignedAgentName || "待分配"
        }}</template>
        <template v-else-if="column.key === 'updatedAt'">{{
          formatDate(record.updatedAt)
        }}</template>
        <template v-else-if="column.key === 'actions'"
          ><a-space
            ><a-button
              size="small"
              @click="assign(record)"
              :disabled="closed(record)"
              >接管</a-button
            ><a-popconfirm
              title="确认将工单标记为已解决？"
              @confirm="resolve(record)"
              ><a-button size="small" type="primary" :disabled="closed(record)"
                >解决</a-button
              ></a-popconfirm
            ></a-space
          ></template
        >
      </template>
    </DataTableShell>
    <DetailDrawer :open="drawer" title="工单详情" @close="drawer = false">
      <a-descriptions v-if="current" :column="1" bordered size="small"
        ><a-descriptions-item label="工单号">{{
          current.ticketNo
        }}</a-descriptions-item
        ><a-descriptions-item label="客户">{{
          current.customerEmail
        }}</a-descriptions-item
        ><a-descriptions-item label="负责人">{{
          current.assignedAgentName || "待分配"
        }}</a-descriptions-item
        ><a-descriptions-item label="SLA">{{
          slaLabel(current.slaState)
        }}</a-descriptions-item
        ><a-descriptions-item label="摘要">{{
          current.summary || "—"
        }}</a-descriptions-item
        ><a-descriptions-item label="关闭原因">{{
          current.closeReason || "—"
        }}</a-descriptions-item></a-descriptions
      >
    </DetailDrawer>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import DetailDrawer from "@/components/admin/DetailDrawer.vue";
import type { SlaSummary, TablePageChange, TicketSummary } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const rows = ref<TicketSummary[]>([]),
  loading = ref(false),
  error = ref(""),
  status = ref<string>(),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  drawer = ref(false),
  current = ref<TicketSummary | null>(null),
  sla = ref<SlaSummary | null>(null);
const columns = [
  { title: "工单", key: "ticket", width: 230 },
  { title: "优先级", key: "priority", width: 90 },
  { title: "状态", key: "status", width: 110 },
  { title: "SLA", key: "sla", width: 110 },
  { title: "负责人", key: "assignee", width: 120 },
  { title: "渠道", dataIndex: "channel", width: 110 },
  { title: "最近更新", key: "updatedAt", width: 150 },
  { title: "操作", key: "actions", fixed: "right", width: 145 },
];
const metrics = computed(() => [
  {
    key: "open",
    label: "待处理工单",
    value: sla.value?.openTickets,
    note: "当前租户",
  },
  {
    key: "due",
    label: "即将超时",
    value: sla.value?.dueSoon,
    note: "请优先处理",
  },
  {
    key: "response",
    label: "首响超时",
    value: sla.value?.responseBreached,
    note: "基于 SLA 截止时间",
  },
  {
    key: "resolve",
    label: "解决超时",
    value: sla.value?.resolveBreached,
    note: "基于 SLA 截止时间",
  },
]);
const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
}));
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [a, b] = await Promise.all([
      api.get("/tickets", {
        params: { status: status.value, page: page.value, size: size.value },
      }),
      api.get("/sla/summary"),
    ]);
    rows.value = a.data?.records || [];
    total.value = a.data?.total || 0;
    sla.value = b.data;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function onTableChange(p: TablePageChange) {
  page.value = p.current || 1;
  size.value = p.pageSize || 20;
  void load();
}
function openDetail(v: TicketSummary) {
  current.value = v;
  drawer.value = true;
}
async function assign(v: TicketSummary) {
  await api.post(`/tickets/${v.id}/assign`, { note: "从工单列表接管" });
  message.success("已接管");
  await load();
}
async function resolve(v: TicketSummary) {
  await api.post(`/tickets/${v.id}/resolve`, { note: "客服确认问题已解决" });
  message.success("已解决");
  await load();
}
function closed(v: TicketSummary) {
  return ["RESOLVED", "CLOSED"].includes(v.status);
}
function priorityLabel(v: number) {
  return ["", "低", "中", "高", "紧急"][v] || "未知";
}
function priorityColor(v: number) {
  return v >= 4 ? "red" : v === 3 ? "orange" : "blue";
}
function slaLabel(v: string) {
  const labels: Record<string, string> = { BREACHED: "已超时", DUE_SOON: "即将超时", NORMAL: "正常" };
  return labels[v] || v || "—";
}
function formatDate(v: string) {
  return v ? new Date(v).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>
<style scoped>
.link-cell {
  height: auto;
  padding: 0;
  font-weight: 650;
}
.cell-note {
  display: block;
  max-width: 210px;
  color: #98a2b3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
