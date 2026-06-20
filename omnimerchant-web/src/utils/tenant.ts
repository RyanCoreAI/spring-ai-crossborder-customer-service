export const SELECTED_TENANT_KEY = 'selectedTenantId'

const PREFERRED_DEMO_TENANT_CODES = ['OM-FASHION', 'OM-ELECTRO']
const LEGACY_EMPTY_TENANT_CODES = ['OM-DEMO001']

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
  const preferredTenant = tenants.find((t) => PREFERRED_DEMO_TENANT_CODES.includes(t.tenantCode || ''))

  if (savedTenant && !LEGACY_EMPTY_TENANT_CODES.includes(savedTenant.tenantCode || '')) {
    return savedTenant.id
  }
  if (preferredTenant) {
    return preferredTenant.id
  }
  return savedTenant?.id || tenants[0]?.id || null
}
