<template>
  <div class="filter-bar">
    <div class="filter-bar__fields"><slot /></div>
    <div v-if="$slots.actions" class="filter-bar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  min-height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
}
.filter-bar__fields,
.filter-bar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.filter-bar__fields {
  flex: 1;
  flex-wrap: wrap;
}
.filter-bar__actions {
  flex: 0 0 auto;
}
@media (max-width: 767px) {
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }
  .filter-bar__fields {
    width: 100%;
  }
  .filter-bar__fields :deep(.ant-input-affix-wrapper),
  .filter-bar__fields :deep(.ant-select) {
    width: 100% !important;
  }
  .filter-bar__actions {
    justify-content: flex-end;
  }
}
</style>
