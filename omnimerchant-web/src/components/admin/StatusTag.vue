<template>
  <a-tag :color="color">{{ text }}</a-tag>
</template>

<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{
  status?: string | number | null;
  label?: string;
}>();
const normalized = computed(() => String(props.status ?? "").toUpperCase());
const color = computed(() => {
  if (
    [
      "LIVE",
      "CONNECTED",
      "ACTIVE",
      "SUCCESS",
      "APPROVED",
      "INDEXED",
      "RESOLVED",
      "CLOSED",
      "OK",
      "DELIVERED",
    ].includes(normalized.value)
  )
    return "green";
  if (
    ["FIXTURE", "DEMO", "PROCESSING", "ASSIGNED", "AI_WORKING"].includes(
      normalized.value,
    )
  )
    return "blue";
  if (
    [
      "WAITING_CREDENTIALS",
      "PENDING",
      "PENDING_APPROVAL",
      "WAITING_CUSTOMER",
      "DUE_SOON",
      "DEGRADED",
      "ACKNOWLEDGED",
    ].includes(normalized.value)
  )
    return "orange";
  if (
    [
      "ERROR",
      "FAILED",
      "DEAD",
      "REJECTED",
      "QUARANTINED",
      "BREACHED",
      "CRITICAL",
    ].includes(normalized.value)
  )
    return "red";
  if (["DISABLED", "NOT_CONFIGURED"].includes(normalized.value))
    return "default";
  return "default";
});
const text = computed(() => props.label || props.status || "未知");
</script>
