export const SELECTED_TENANT_KEY = 'selectedTenantId'

export type TenantOption = {
  id: number
  tenantCode?: string
}

export function getStoredTenantId() {
  const raw = localStorage.getItem(SELECTED_TENANT_KEY)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

export function setStoredTenantId(tenantId: number | string | null | undefined) {
  if (tenantId === null || tenantId === undefined || tenantId === '') {
    localStorage.removeItem(SELECTED_TENANT_KEY)
    return
  }
  localStorage.setItem(SELECTED_TENANT_KEY, String(tenantId))
}

export function selectDefaultTenantId(tenants: TenantOption[]) {
  const saved = getStoredTenantId()
  const savedTenant = saved ? tenants.find((t) => t.id === saved) : null
  if (savedTenant) {
    return savedTenant.id
  }
  return tenants[0]?.id || null
}
