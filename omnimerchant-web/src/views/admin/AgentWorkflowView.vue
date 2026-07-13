<template>
  <div>
    <a-alert
      type="info"
      show-icon
      message="确定性 Supervisor-Worker 编排"
      description="后端按意图选择 specialist，并在请求级限定工具白名单；不使用开放式多 Agent 群聊。"
    />
    <PageToolbar class="section-row"
      ><a-select v-model:value="intent" style="width: 190px"
        ><a-select-option
          v-for="item in intents"
          :key="item.value"
          :value="item.value"
          >{{ item.label }}</a-select-option
        ></a-select
      ><a-input
        v-model:value="buyerMessage"
        placeholder="输入买家问题，查看后端路由结果"
        style="width: min(460px, 100%)"
      /><template #actions
        ><a-button type="primary" :loading="planning" @click="plan"
          >路由试算</a-button
        ><a-button :loading="loading" @click="load">刷新</a-button></template
      ></PageToolbar
    >
    <a-descriptions
      v-if="result"
      class="section-row"
      :column="2"
      bordered
      size="small"
      ><a-descriptions-item label="分派智能体">{{
        result.specialistLabel
      }}</a-descriptions-item
      ><a-descriptions-item label="风险等级">{{
        result.riskLevel
      }}</a-descriptions-item
      ><a-descriptions-item label="工具白名单">{{
        (result.toolAllowlist || []).join("、") || "无工具"
      }}</a-descriptions-item
      ><a-descriptions-item label="身份验证">{{
        result.requiresIdentityVerification ? "需要" : "不需要"
      }}</a-descriptions-item
      ><a-descriptions-item label="人工审批">{{
        result.requiresApproval ? "需要" : "不需要"
      }}</a-descriptions-item
      ><a-descriptions-item label="建议转人工">{{
        result.recommendHumanHandoff ? "是" : "否"
      }}</a-descriptions-item></a-descriptions
    >
    <section class="workflow">
      <div class="section-heading">
        <h3>{{ workflow?.workflowName || "工作流节点" }}</h3>
        <StatusTag
          :status="workflow?.currentMode"
          :label="workflow?.currentMode || '未载入'"
        />
      </div>
      <BackendEmptyState
        v-if="loading || !workflow?.nodes?.length"
        :loading="loading"
        description="暂无工作节点"
      />
      <div v-else class="node-line">
        <div
          v-for="(node, index) in workflow.nodes"
          :key="node.nodeKey"
          class="node"
        >
          <span>{{ index + 1 }}</span
          ><strong>{{ node.nodeLabel || node.nodeKey }}</strong
          ><small>{{ node.status }}</small>
        </div>
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from "vue";
import api from "@/api";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import BackendEmptyState from "@/components/admin/BackendEmptyState.vue";
import type { AgentPlan, AgentWorkflow } from "@/types/contracts";
const workflow = ref<AgentWorkflow | null>(null),
  result = ref<AgentPlan | null>(null),
  loading = ref(false),
  planning = ref(false),
  intent = ref("ORDER_STATUS"),
  buyerMessage = ref("");
const intents = [
  { value: "ORDER_STATUS", label: "订单查询" },
  { value: "LOGISTICS", label: "物流追踪" },
  { value: "RETURN_REFUND", label: "退货退款" },
  { value: "PRODUCT_ADVICE", label: "商品推荐" },
  { value: "POLICY_QA", label: "政策问答" },
  { value: "COMPLAINT", label: "投诉" },
  { value: "UNKNOWN", label: "未知意图" },
];
async function load() {
  loading.value = true;
  try {
    workflow.value = (await api.get("/agent/workflow")).data;
  } finally {
    loading.value = false;
  }
}
async function plan() {
  planning.value = true;
  try {
    result.value = (
      await api.post("/agent/plan", {
        intent: intent.value,
        message: buyerMessage.value,
      })
    ).data;
  } finally {
    planning.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.workflow {
  margin-top: 14px;
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
}
.node-line {
  display: flex;
  overflow: auto;
  padding: 22px 16px;
}
.node {
  position: relative;
  min-width: 145px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.node:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 15px;
  left: 66%;
  width: 68%;
  height: 1px;
  background: #ccd8e8;
}
.node > span {
  z-index: 1;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
}
.node strong {
  margin-top: 8px;
  font-size: 12px;
}
.node small {
  margin-top: 3px;
  color: #98a2b3;
}
</style>
