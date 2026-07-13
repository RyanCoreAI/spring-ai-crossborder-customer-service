<template>
  <div
    class="metric-strip"
    :style="{ '--metric-columns': String(Math.min(items.length || 1, 4)) }"
  >
    <div v-for="item in items" :key="item.key" class="metric-strip__item">
      <span>{{ item.label }}</span>
      <strong>{{ format(item.value, item.suffix) }}</strong>
      <small v-if="item.note">{{ item.note }}</small>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{
    key: string;
    label: string;
    value: string | number | null | undefined;
    suffix?: string;
    note?: string;
  }>;
}>();
function format(value: string | number | null | undefined, suffix?: string) {
  return value === null || value === undefined || value === ""
    ? "—"
    : `${value}${suffix || ""}`;
}
</script>

<style scoped>
.metric-strip {
  display: grid;
  grid-template-columns: repeat(var(--metric-columns), minmax(0, 1fr));
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.metric-strip__item {
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--omni-border);
}
.metric-strip__item:last-child {
  border-right: 0;
}
.metric-strip span,
.metric-strip small {
  display: block;
  color: var(--omni-muted);
  font-size: 12px;
}
.metric-strip strong {
  display: block;
  margin: 6px 0 3px;
  color: #101828;
  font-size: 24px;
  line-height: 30px;
  font-weight: 720;
}
@media (max-width: 767px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric-strip__item:nth-child(2n) {
    border-right: 0;
  }
  .metric-strip__item:nth-child(n + 3) {
    border-top: 1px solid var(--omni-border);
  }
}
</style>
