<template><StatusTag :status="status" :label="label" /></template>
<script setup lang="ts">
import { computed } from "vue";
import StatusTag from "./StatusTag.vue";
const props = defineProps<{ status?: string | null }>();
const labels: Record<string, string> = {
  LIVE: "真实连接",
  CONNECTED: "真实连接",
  FIXTURE: "Fixture 演示",
  WAITING_CREDENTIALS: "等待凭据",
  DEGRADED: "降级",
  ERROR: "错误",
  DISABLED: "已停用",
  ADAPTER_READY: "适配器就绪",
  NOT_CONFIGURED: "未配置",
};
const status = computed(() => String(props.status || "DISABLED").toUpperCase());
const label = computed(() => labels[status.value] || props.status || "未知");
</script>
