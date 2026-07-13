<template>
  <div>
    <MetricStrip :items="metrics" /><PageToolbar class="section-row"
      ><a-segmented
        v-model:value="tab"
        :options="[
          { label: '风险工单', value: 'risk' },
          { label: 'SLA 策略', value: 'policy' },
        ]"
      /><template #actions
        ><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      ></PageToolbar
    ><DataTableShell
      v-if="tab === 'risk'"
      :columns="riskColumns"
      :rows="summary?.riskTickets || []"
      :loading="loading"
      :error="error"
      row-key="id"
      @retry="load"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'ticket'"
          ><strong>{{ record.ticketNo }}</strong
          ><small>{{ record.summary }}</small></template
        ><template v-else-if="column.key === 'priority'">{{
          priority(record.priority)
        }}</template
        ><template v-else-if="column.key === 'sla'"
          ><StatusTag
            :status="record.slaState"
            :label="sla(record.slaState)" /></template
        ><template v-else-if="column.key === 'due'">{{
          date(record.resolveDueAt)
        }}</template></template
      ></DataTableShell
    ><DataTableShell
      v-else
      :columns="policyColumns"
      :rows="summary?.policies || []"
      :loading="loading"
      :error="error"
      row-key="id"
      @retry="load"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'response'"
          >{{ record.firstResponseMinutes }} 分钟</template
        ><template v-else-if="column.key === 'resolve'"
          >{{ record.resolutionMinutes }} 分钟</template
        ><template v-else-if="column.key === 'active'"
          ><StatusTag
            :status="record.active ? 'ACTIVE' : 'DISABLED'"
            :label="record.active ? '已启用' : '已停用'" /></template></template
    ></DataTableShell>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import type { SlaSummary } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const summary = ref<SlaSummary | null>(null),
  loading = ref(false),
  error = ref(""),
  tab = ref("risk");
const metrics = computed(() => [
  {
    key: "open",
    label: "开放工单",
    value: summary.value?.openTickets,
    note: "当前租户",
  },
  {
    key: "due",
    label: "即将超时",
    value: summary.value?.dueSoon,
    note: "进入风险窗口",
  },
  {
    key: "response",
    label: "首响超时率",
    value: summary.value?.responseBreachRate,
    suffix: "%",
    note: "已超过首响截止",
  },
  {
    key: "resolve",
    label: "解决超时率",
    value: summary.value?.resolveBreachRate,
    suffix: "%",
    note: "已超过解决截止",
  },
]);
const riskColumns = [
  { title: "工单", key: "ticket" },
  { title: "优先级", key: "priority", width: 90 },
  { title: "SLA", key: "sla", width: 110 },
  { title: "解决截止", key: "due", width: 180 },
  { title: "负责人 ID", dataIndex: "assignedAgentId", width: 120 },
];
const policyColumns = [
  { title: "策略", dataIndex: "policyName" },
  { title: "渠道", dataIndex: "channel", width: 120 },
  { title: "首响目标", key: "response", width: 120 },
  { title: "解决目标", key: "resolve", width: 120 },
  { title: "工作时间", dataIndex: "businessHours", width: 190 },
  { title: "状态", key: "active", width: 100 },
];
async function load() {
  loading.value = true;
  error.value = "";
  try {
    summary.value = (await api.get("/sla/summary")).data;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function priority(v: number) {
  return ["", "低", "中", "高", "紧急"][v] || "—";
}
function sla(v: string) {
  const labels: Record<string, string> = { BREACHED: "已超时", DUE_SOON: "即将超时", NORMAL: "正常" };
  return labels[v] || v || "—";
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
