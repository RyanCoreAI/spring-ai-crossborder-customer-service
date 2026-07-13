<template>
  <div>
    <MetricStrip :items="metrics" /><PageToolbar class="section-row"
      ><a-select
        v-model:value="status"
        allow-clear
        placeholder="全部状态"
        style="width: 180px"
        @change="load"
        ><a-select-option value="1">待审批</a-select-option
        ><a-select-option value="2">审核中</a-select-option
        ><a-select-option value="3">已批准</a-select-option
        ><a-select-option value="4">已拒绝</a-select-option></a-select
      ><template #actions
        ><a-alert
          type="warning"
          show-icon
          message="外部写操作未启用"
        /><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      ></PageToolbar
    ><DataTableShell
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      :scroll="{ x: 1080 }"
      @retry="load"
      @change="onTableChange"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'request'"
          ><a-button type="link" class="link-cell" @click="open(record)">{{
            record.requestNo
          }}</a-button
          ><small
            >{{ record.actionType }} · {{ record.externalOrderNumber }}</small
          ></template
        ><template v-else-if="column.key === 'status'"
          ><StatusTag
            :status="record.status"
            :label="record.statusLabel" /></template
        ><template v-else-if="column.key === 'amount'">{{
          money(record.amount, record.currency)
        }}</template
        ><template v-else-if="column.key === 'createdAt'">{{
          date(record.createdAt)
        }}</template
        ><template v-else-if="column.key === 'actions'"
          ><a-space
            ><a-popconfirm
              title="仅批准内部请求，不会执行外部退款。继续？"
              @confirm="decide(record, 'approve')"
              ><a-button
                size="small"
                type="primary"
                :disabled="finished(record)"
                >批准</a-button
              ></a-popconfirm
            ><a-popconfirm
              title="确认拒绝该请求？"
              @confirm="decide(record, 'reject')"
              ><a-button size="small" danger :disabled="finished(record)"
                >拒绝</a-button
              ></a-popconfirm
            ></a-space
          ></template
        ></template
      ></DataTableShell
    ><DetailDrawer :open="drawer" title="审批详情" @close="drawer = false"
      ><a-alert
        type="warning"
        show-icon
        message="仅内部审批"
        description="默认不会向 Shopify、抖店或其他外部平台执行退款、取消或改地址。"
      /><a-descriptions
        v-if="current"
        :column="1"
        bordered
        size="small"
        class="drawer-block"
        ><a-descriptions-item label="请求号">{{
          current.requestNo
        }}</a-descriptions-item
        ><a-descriptions-item label="订单">{{
          current.externalOrderNumber
        }}</a-descriptions-item
        ><a-descriptions-item label="客户">{{
          current.customerEmail
        }}</a-descriptions-item
        ><a-descriptions-item label="风险原因">{{
          current.riskReason || "—"
        }}</a-descriptions-item
        ><a-descriptions-item label="申请内容">
          <pre>{{ current.requestedPayload || "—" }}</pre>
        </a-descriptions-item></a-descriptions
      ></DetailDrawer
    >
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
import type { ActionRequest, TablePageChange } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const rows = ref<ActionRequest[]>([]),
  loading = ref(false),
  error = ref(""),
  status = ref<string>(),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  drawer = ref(false),
  current = ref<ActionRequest | null>(null);
const columns = [
  { title: "请求", key: "request", width: 210 },
  { title: "客户", dataIndex: "customerEmail", width: 190 },
  { title: "金额", key: "amount", width: 100 },
  { title: "风险原因", dataIndex: "riskReason", ellipsis: true },
  { title: "状态", key: "status", width: 110 },
  { title: "创建时间", key: "createdAt", width: 160 },
  { title: "操作", key: "actions", fixed: "right", width: 135 },
];
const metrics = computed(() => [
  {
    key: "pending",
    label: "待审批",
    value: rows.value.filter((x) =>
      ["1", "2", "REQUESTED", "NEEDS_APPROVAL"].includes(String(x.status)),
    ).length,
    note: "当前页",
  },
  {
    key: "high",
    label: "高风险",
    value: rows.value.filter((x) => x.riskReason).length,
    note: "需人工复核",
  },
  {
    key: "approved",
    label: "已批准",
    value: rows.value.filter((x) =>
      ["3", "APPROVED_MANUAL"].includes(String(x.status)),
    ).length,
    note: "仅内部状态",
  },
  { key: "external", label: "外部写操作", value: "未启用", note: "安全边界" },
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
    const r = await api.get("/actions", {
      params: { status: status.value, page: page.value, size: size.value },
    });
    rows.value = r.data?.records || [];
    total.value = r.data?.total || 0;
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
function open(v: ActionRequest) {
  current.value = v;
  drawer.value = true;
}
function finished(v: ActionRequest) {
  return [
    "3",
    "4",
    "5",
    "APPROVED_MANUAL",
    "REJECTED",
    "EXECUTED",
    "FAILED",
  ].includes(String(v.status));
}
async function decide(v: ActionRequest, action: "approve" | "reject") {
  await api.post(`/actions/${v.source}/${v.id}/${action}`, {
    note: action === "approve" ? "人工批准，仅内部记录" : "人工拒绝",
  });
  message.success(action === "approve" ? "已批准内部请求" : "已拒绝");
  await load();
}
function money(v: number | string | null | undefined, c?: string) {
  return v === null || v === undefined ? "—" : `${c || ""} ${v}`;
}
function date(v: string) {
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
.link-cell + small {
  display: block;
  color: #98a2b3;
}
.drawer-block {
  margin-top: 16px;
}
pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
