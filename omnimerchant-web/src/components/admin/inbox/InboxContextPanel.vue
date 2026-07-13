<template>
  <div v-if="context" class="context-content">
    <section class="context-section">
      <h3>客户</h3>
      <ContextLine label="姓名" :value="context.customer?.displayName" />
      <ContextLine label="邮箱" :value="context.customer?.email" />
      <ContextLine label="客户层级" :value="context.customer?.tier" />
      <ContextLine label="语言" :value="context.customer?.language" />
    </section>

    <section class="context-section">
      <h3>最近订单</h3>
      <div v-if="context.recentOrders?.length">
        <div v-for="order in context.recentOrders" :key="order.id || order.orderNumber" class="context-record">
          <strong>{{ order.orderNumber || "—" }}</strong>
          <span>{{ order.orderStatus || "—" }} · {{ order.trackingStatus || "暂无物流" }}</span>
          <small>{{ order.currency || "" }} {{ order.totalAmount ?? "—" }}</small>
        </div>
      </div>
      <span v-else class="empty-inline">暂无关联订单</span>
    </section>

    <section class="context-section">
      <h3>SLA</h3>
      <template v-if="context.sla">
        <ContextLine label="状态" :value="slaLabel(context.sla.slaState)" />
        <ContextLine label="首响截止" :value="formatTime(context.sla.responseDueAt)" />
        <ContextLine label="解决截止" :value="formatTime(context.sla.resolveDueAt)" />
      </template>
      <span v-else class="empty-inline">无关联工单</span>
    </section>

    <section class="context-section">
      <h3>审批请求</h3>
      <div v-if="context.actions?.length">
        <div v-for="action in context.actions" :key="action.requestNo" class="context-record">
          <strong>{{ action.requestNo || "—" }}</strong>
          <span>{{ action.actionType || "—" }} · {{ action.statusLabel || "—" }}</span>
          <small>{{ action.riskReason || "仅内部审批" }}</small>
        </div>
      </div>
      <span v-else class="empty-inline">暂无审批请求</span>
    </section>

    <section class="context-section">
      <h3>工具调用</h3>
      <div v-if="context.toolCalls?.length">
        <div v-for="(tool, index) in context.toolCalls.slice(0, 5)" :key="`${tool.toolName}-${index}`" class="context-record">
          <strong>{{ tool.toolName || "—" }}</strong>
          <span>{{ tool.success ? "成功" : "失败" }} · {{ tool.latencyMs ?? "—" }} ms</span>
        </div>
      </div>
      <span v-else class="empty-inline">暂无工具调用</span>
    </section>
  </div>
  <div v-else class="empty-inline">请先选择会话</div>
</template>

<script setup lang="ts">
import { defineComponent, h, type PropType } from "vue";
import type { InboxContext } from "@/types/inbox";

defineProps<{ context: InboxContext | null }>();

const ContextLine = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number] as PropType<string | number>, default: undefined },
  },
  setup(props) {
    return () => h("div", { class: "context-line" }, [
      h("span", props.label),
      h("strong", props.value ?? "—"),
    ]);
  },
});

function slaLabel(value?: string) {
  const labels: Record<string, string> = { BREACHED: "已超时", DUE_SOON: "即将超时", NORMAL: "正常" };
  return labels[value || ""] || value || "—";
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }) : "—";
}
</script>

<style scoped>
.context-section { padding: 16px 0; border-bottom: 1px solid #eaecf0; }
.context-section:last-child { border-bottom: 0; }
.context-section h3 { margin: 0 0 10px; font-size: 14px; }
.context-line { display: flex; justify-content: space-between; gap: 16px; margin: 7px 0; }
.context-line span, .context-record span, .context-record small, .empty-inline { color: #667085; }
.context-line strong { text-align: right; overflow-wrap: anywhere; }
.context-record { display: grid; gap: 3px; padding: 9px 0; border-bottom: 1px solid #f2f4f7; }
.context-record:last-child { border-bottom: 0; }
</style>
