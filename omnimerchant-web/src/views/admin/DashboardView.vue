<template>
  <div class="dashboard-view">
    <div class="dashboard-actions">
      <a-button :loading="loading" @click="load"
        ><template #icon><ReloadOutlined /></template>刷新</a-button
      >
    </div>
    <MetricStrip :items="metrics" />

    <a-row :gutter="[14, 14]" class="analytics-row">
      <a-col :xs="24" :xl="12">
        <a-card title="咨询意图分布" class="analytics-card">
          <template #extra>
            <span class="chart-source">{{ operations?.conversations ?? "—" }} 条会话</span>
          </template>
          <DonutChart
            :data="intentChartData"
            ariaLabel="当前租户咨询意图分布"
            empty-text="暂无已分类会话"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="12">
        <a-card title="接入渠道分布" class="analytics-card">
          <template #extra>
            <span class="chart-source">数据来自会话表</span>
          </template>
          <DonutChart
            :data="channelChartData"
            ariaLabel="当前租户接入渠道分布"
            empty-text="暂无渠道会话"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[14, 14]" class="dashboard-body">
      <a-col :xs="24" :xl="15">
        <a-card title="当前待处理">
          <a-empty v-if="!queueItems.length" description="当前没有待处理事项" />
          <button
            v-for="item in queueItems"
            :key="item.key"
            class="work-row"
            type="button"
            @click="router.push(item.path)"
          >
            <span class="work-indicator" :class="item.tone"></span>
            <span class="work-copy"
              ><strong>{{ item.label }}</strong
              ><small>{{ item.description }}</small></span
            >
            <span class="work-count">{{ item.count }}</span>
            <RightOutlined />
          </button>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="9">
        <a-card title="系统健康">
          <a-empty v-if="!sloItems.length" description="尚无 SLO 采样" />
          <div v-for="item in sloItems" :key="item.key" class="health-row">
            <div>
              <strong>{{ item.label }}</strong
              ><small>目标 {{ formatSlo(item.target, item.unit) }}</small>
            </div>
            <div class="health-value">
              <span>{{ formatSlo(item.actual, item.unit) }}</span
              ><a-tag :color="statusColor(item.status)">{{
                statusText(item.status)
              }}</a-tag>
            </div>
          </div>
          <template #extra
            ><a-button
              type="link"
              size="small"
              @click="router.push('/admin/sre')"
              >查看详情</a-button
            ></template
          >
        </a-card>
        <a-card v-if="alerts.length" title="活动告警" class="alert-panel">
          <div
            v-for="(alert, index) in alerts.slice(0, 3)"
            :key="`${alert.category}-${index}`"
            class="alert-row"
          >
            <a-tag :color="alert.severity === 'WARN' ? 'orange' : 'blue'">{{
              alert.severity
            }}</a-tag>
            <span>{{ alert.message }}</span>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ReloadOutlined, RightOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import DonutChart from "@/components/admin/DonutChart.vue";
import { dimensionLabel } from "@/utils/display";
import type {
  DashboardSummary,
  OperationsSummary,
  QueueBucket,
  SlaSummary,
  SreSummary,
} from "@/types/operations";

const router = useRouter();
const loading = ref(false);
const dashboard = ref<DashboardSummary | null>(null);
const queues = ref<QueueBucket[]>([]);
const sre = ref<SreSummary | null>(null);
const sla = ref<SlaSummary | null>(null);
const operations = ref<OperationsSummary | null>(null);

const metrics = computed(() => [
  {
    key: "open",
    label: "待处理工单",
    value: sla.value?.openTickets,
    note: "当前租户",
  },
  {
    key: "approval",
    label: "待审批动作",
    value: dashboard.value?.pendingReturns,
    note: "不会自动执行外部写操作",
  },
  {
    key: "resolution",
    label: "AI 解决率",
    value: dashboard.value?.aiResolutionRate,
    suffix: "%",
    note: "已解决且未升级人工",
  },
  {
    key: "tool",
    label: "工具成功率",
    value: dashboard.value?.toolSuccessRate,
    suffix: "%",
    note: "基于真实工具调用日志",
  },
]);

const queueRoutes: Record<string, string> = {
  unassigned: "/admin/inbox",
  approval: "/admin/actions",
  sla_risk: "/admin/sla",
  mine: "/admin/inbox",
};
const queueItems = computed(() =>
  queues.value
    .filter((item) => Number(item.count) > 0 && queueRoutes[item.queueKey])
    .slice(0, 6)
    .map((item) => ({
      ...item,
      key: item.queueKey,
      label: item.queueLabel,
      path: queueRoutes[item.queueKey],
      tone:
        item.queueKey === "sla_risk"
          ? "danger"
          : item.queueKey === "approval"
            ? "warning"
            : "primary",
    })),
);
const sloItems = computed(() => (sre.value?.slos || []).slice(0, 4));
const alerts = computed(() => sre.value?.alerts || []);
const intentChartData = computed(() =>
  (operations.value?.intents || []).map((item) => ({
    name: dimensionLabel(item.name),
    value: Number(item.count),
  })),
);
const channelChartData = computed(() =>
  (operations.value?.channels || []).map((item) => ({
    name: dimensionLabel(item.name),
    value: Number(item.count),
  })),
);

async function load() {
  loading.value = true;
  const [dashboardResult, queuesResult, sreResult, slaResult, operationsResult] =
    await Promise.allSettled([
      api.get("/dashboard/commerce"),
      api.get("/inbox/queues"),
      api.get("/sre/summary"),
      api.get("/sla/summary"),
      api.get("/operations/summary"),
    ]);
  dashboard.value =
    dashboardResult.status === "fulfilled" ? dashboardResult.value.data : null;
  queues.value =
    queuesResult.status === "fulfilled" ? queuesResult.value.data || [] : [];
  sre.value = sreResult.status === "fulfilled" ? sreResult.value.data : null;
  sla.value = slaResult.status === "fulfilled" ? slaResult.value.data : null;
  operations.value =
    operationsResult.status === "fulfilled" ? operationsResult.value.data : null;
  loading.value = false;
}

function formatSlo(value: number | null | undefined, unit: string) {
  return value === null || value === undefined
    ? "—"
    : `${value}${unit === "%" ? "%" : ` ${unit || ""}`}`;
}
function statusColor(status: string) {
  return status === "OK" ? "green" : status === "BREACH" ? "red" : "orange";
}
function statusText(status: string) {
  return status === "OK" ? "正常" : status === "BREACH" ? "超出目标" : "需关注";
}

onMounted(load);
</script>

<style scoped>
.dashboard-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.dashboard-body {
  margin-top: 14px;
}
.analytics-row {
  margin-top: 14px;
}
.analytics-card {
  height: 100%;
}
.chart-source {
  color: #98a2b3;
  font-size: 11px;
}
.work-row {
  width: 100%;
  min-height: 62px;
  display: grid;
  grid-template-columns: 3px 1fr auto 16px;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.work-row:last-child {
  border-bottom: 0;
}
.work-row:hover {
  background: #f8fafc;
}
.work-indicator {
  width: 3px;
  height: 34px;
  border-radius: 2px;
  background: #1677ff;
}
.work-indicator.warning {
  background: #f79009;
}
.work-indicator.danger {
  background: #d92d20;
}
.work-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.work-copy strong {
  color: #182230;
  font-size: 13px;
}
.work-copy small {
  margin-top: 3px;
  color: #667085;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.work-count {
  min-width: 32px;
  color: #111827;
  font-size: 17px;
  font-weight: 700;
  text-align: right;
}
.health-row {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-bottom: 1px solid #edf0f4;
}
.health-row:last-child {
  border-bottom: 0;
}
.health-row > div:first-child {
  display: flex;
  flex-direction: column;
}
.health-row strong {
  color: #344054;
  font-size: 12px;
}
.health-row small {
  margin-top: 3px;
  color: #98a2b3;
  font-size: 10px;
}
.health-value {
  display: flex;
  align-items: center;
  gap: 7px;
}
.health-value span {
  color: #111827;
  font-size: 12px;
  font-weight: 650;
}
.alert-panel {
  margin-top: 14px;
}
.alert-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 0;
  color: #475467;
  font-size: 12px;
  line-height: 20px;
}
@media (max-width: 560px) {
  .work-copy small {
    white-space: normal;
  }
}
</style>
