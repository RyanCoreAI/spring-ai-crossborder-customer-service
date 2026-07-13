<template>
  <a-drawer
    :open="open"
    :title="title"
    :width="width"
    destroy-on-close
    @close="$emit('close')"
  >
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </a-drawer>
</template>
<script setup lang="ts">
withDefaults(
  defineProps<{ open: boolean; title: string; width?: number | string }>(),
  { width: 520 },
);
defineEmits<{ close: [] }>();
</script>
