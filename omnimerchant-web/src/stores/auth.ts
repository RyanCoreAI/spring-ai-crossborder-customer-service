import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import api from '@/api'
import { setStoredTenantId } from '@/utils/tenant'

const ACCESS_TOKEN_KEY = 'omni_access_token'
const REFRESH_TOKEN_KEY = 'omni_refresh_token'
const EMAIL_KEY = 'omni_email'

export type AuthClaims = {
  exp?: number
  sub?: string
  userId?: number
  role?: string
  roles?: string[]
  tenantIds?: number[]
  platformAdmin?: boolean
}

type AuthTokenPayload = {
  token?: string
  accessToken?: string
  refreshToken?: string
  email?: string
}

function parseJwtClaims(rawToken: string): AuthClaims | null {
  try {
    const payload = rawToken.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

function isCurrentAuthToken(rawToken: string) {
  const claims = parseJwtClaims(rawToken)
  if (!claims || claims.role === 'WIDGET_CUSTOMER') return false
  if (typeof claims.exp === 'number' && claims.exp * 1000 <= Date.now()) return false
  return claims.platformAdmin === true || Boolean(claims.tenantIds?.length)
}

function migrateLegacySession() {
  const legacyToken = localStorage.getItem('token') || ''
  if (!sessionStorage.getItem(ACCESS_TOKEN_KEY) && isCurrentAuthToken(legacyToken)) {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, legacyToken)
    sessionStorage.setItem(EMAIL_KEY, localStorage.getItem('email') || '')
  }
  localStorage.removeItem('token')
  localStorage.removeItem('email')
}

export const useAuthStore = defineStore('auth', () => {
  migrateLegacySession()
  const storedToken = sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
  const token = ref(isCurrentAuthToken(storedToken) ? storedToken : '')
  const refreshToken = ref(token.value ? sessionStorage.getItem(REFRESH_TOKEN_KEY) || '' : '')
  const email = ref(token.value ? sessionStorage.getItem(EMAIL_KEY) || '' : '')
  const claims = computed(() => parseJwtClaims(token.value))
  const userId = computed(() => claims.value?.userId || null)
  const roles = computed(() => claims.value?.roles || (claims.value?.role ? [claims.value.role] : []))
  const tenantIds = computed(() => claims.value?.tenantIds || [])
  const platformAdmin = computed(() => claims.value?.platformAdmin === true)
  const isLoggedIn = computed(() => isCurrentAuthToken(token.value))

  function persistAuth(data: AuthTokenPayload) {
    const accessToken = data?.accessToken || data?.token || ''
    if (!isCurrentAuthToken(accessToken)) throw new Error('服务返回的登录令牌无有效租户权限')
    token.value = accessToken
    refreshToken.value = data?.refreshToken || refreshToken.value
    email.value = data?.email || parseJwtClaims(accessToken)?.sub || email.value
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token.value)
    sessionStorage.setItem(EMAIL_KEY, email.value)
    if (refreshToken.value) sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken.value)
  }

  async function login(loginEmail: string, password: string) {
    const res = await api.post('/auth/login', { email: loginEmail, password })
    persistAuth(res.data)
    setStoredTenantId(null)
    return res.data
  }

  async function refreshAccessToken() {
    if (!refreshToken.value) return false
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refreshToken.value }),
      })
      const body = await response.json()
      if (!response.ok || body?.code !== '200') return false
      persistAuth(body.data)
      return true
    } catch {
      return false
    }
  }

  async function logout(requestServer = true) {
    const accessToken = token.value
    const currentRefreshToken = refreshToken.value
    token.value = ''
    refreshToken.value = ''
    email.value = ''
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
    sessionStorage.removeItem(REFRESH_TOKEN_KEY)
    sessionStorage.removeItem(EMAIL_KEY)
    setStoredTenantId(null)
    if (requestServer && accessToken) {
      try {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
          body: JSON.stringify({ refreshToken: currentRefreshToken || null }),
        })
      } catch {
        // Local logout must still complete when the backend is unavailable.
      }
    }
  }

  if (!isLoggedIn.value && storedToken) void logout(false)

  return {
    token,
    refreshToken,
    email,
    userId,
    roles,
    tenantIds,
    platformAdmin,
    isLoggedIn,
    login,
    refreshAccessToken,
    logout,
  }
})
