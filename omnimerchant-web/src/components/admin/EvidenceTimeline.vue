<template>
  <a-timeline>
    <a-timeline-item
      v-for="(item, index) in items"
      :key="item.id || index"
      :color="item.color || 'blue'"
    >
      <strong class="title">{{ item.title }}</strong
      ><span v-if="item.time" class="time">{{ item.time }}</span>
      <p v-if="item.description">{{ item.description }}</p>
    </a-timeline-item>
  </a-timeline>
</template>
<script setup lang="ts">
defineProps<{
  items: Array<{
    id?: string | number;
    title: string;
    time?: string;
    description?: string;
    color?: string;
  }>;
}>();
</script>
<style scoped>
.title {
  font-size: 13px;
  color: #182230;
}
.time {
  margin-left: 8px;
  color: #98a2b3;
  font-size: 11px;
}
p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}
</style>
