<template>
  <div class="page-toolbar">
    <div class="page-toolbar__filters"><slot /></div>
    <div class="page-toolbar__actions"><slot name="actions" /></div>
  </div>
</template>

<style scoped>
.page-toolbar {
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
.page-toolbar__filters,
.page-toolbar__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 767px) {
  .page-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .page-toolbar__actions {
    justify-content: flex-end;
  }
  .page-toolbar__filters {
    width: 100%;
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: thin;
  }
}
</style>
