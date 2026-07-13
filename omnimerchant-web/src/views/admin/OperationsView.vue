<template>
  <div>
    <MetricStrip :items="metrics" />
    <div class="distribution-grid">
      <section v-for="group in groups" :key="group.key" class="distribution">
        <div class="section-heading">
          <h3>{{ group.label }}</h3>
          <span>{{ group.rows.length }} 项</span>
        </div>
        <BackendEmptyState
          v-if="!group.rows.length"
          description="暂无维度数据"
        />
        <div
          v-for="item in group.rows"
          v-else
          :key="item.name"
          class="dimension"
        >
          <span>{{ label(item.name) }}</span>
          <div>
            <strong>{{ item.count }}</strong
            ><a-progress
              :percent="Number(item.rate || 0)"
              :show-info="false"
              size="small"
            />
          </div>
        </div>
      </section>
    </div>
    <div class="refresh-row">
      <a-button :loading="loading" @click="load"
        ><template #icon><ReloadOutlined /></template>刷新</a-button
      >
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import BackendEmptyState from "@/components/admin/BackendEmptyState.vue";
import type { OperationsSummary } from "@/types/operations";
const data = ref<OperationsSummary | null>(null),
  loading = ref(false);
const metrics = computed(() => [
  {
    key: "resolution",
    label: "AI 解决率",
    value: data.value?.aiResolutionRate,
    suffix: "%",
    note: `${data.value?.aiResolved ?? "—"} 个会话`,
  },
  {
    key: "takeover",
    label: "人工接管率",
    value: data.value?.humanTakeoverRate,
    suffix: "%",
    note: `${data.value?.humanTakeovers ?? "—"} 个会话`,
  },
  {
    key: "csat",
    label: "平均 CSAT",
    value: data.value?.avgCsat,
    note: "仅已评分会话",
  },
  {
    key: "cost",
    label: "单解决会话成本",
    value:
      data.value?.costPerResolvedCase === null
        ? null
        : `$${data.value?.costPerResolvedCase}`,
    note: "来自 token 用量",
  },
]);
const groups = computed(() => [
  { key: "intent", label: "意图分布", rows: data.value?.intents || [] },
  { key: "channel", label: "渠道分布", rows: data.value?.channels || [] },
  {
    key: "failure",
    label: "失败原因",
    rows: data.value?.topFailureCategories || [],
  },
]);
async function load() {
  loading.value = true;
  try {
    data.value = (await api.get("/operations/summary")).data;
  } finally {
    loading.value = false;
  }
}
function label(v: string) {
  const labels: Record<string, string> = {
    LOGISTICS: "物流",
    RETURN_REFUND: "退货退款",
    PRODUCT_ADVICE: "商品建议",
    POLICY_QA: "政策问答",
    WEB_WIDGET: "买家咨询组件",
    WECHAT_KF: "企业微信客服",
    EMAIL: "邮件",
  };
  return labels[v] || v || "未分类";
}
onMounted(load);
</script>
<style scoped>
.distribution-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}
.distribution {
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
}
.section-heading span {
  color: #98a2b3;
  font-size: 11px;
}
.dimension {
  min-height: 58px;
  display: grid;
  grid-template-columns: minmax(90px, 1fr) 55%;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid #edf0f4;
}
.dimension:last-child {
  border-bottom: 0;
}
.dimension > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dimension > div {
  display: grid;
  grid-template-columns: 40px 1fr;
  align-items: center;
  gap: 8px;
}
.refresh-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
@media (max-width: 980px) {
  .distribution-grid {
    grid-template-columns: 1fr;
  }
}
</style>
