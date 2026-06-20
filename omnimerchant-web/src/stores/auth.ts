import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'
import { setStoredTenantId } from '@/utils/tenant'

function parseJwtClaims(rawToken: string) {
  try {
    const payload = rawToken.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    return JSON.parse(decodeURIComponent(escape(atob(padded))))
  } catch {
    return null
  }
}

function isCurrentAuthToken(rawToken: string) {
  const claims = parseJwtClaims(rawToken)
  if (!claims) return false
  if (typeof claims.exp === 'number' && claims.exp * 1000 <= Date.now()) return false
  return claims.platformAdmin === true
}

export const useAuthStore = defineStore('auth', () => {
  const storedToken = localStorage.getItem('token') || ''
  if (storedToken && !isCurrentAuthToken(storedToken)) {
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    setStoredTenantId(null)
  }

  const token = ref(isCurrentAuthToken(storedToken) ? storedToken : '')
  const email = ref(token.value ? localStorage.getItem('email') || '' : '')

  const isLoggedIn = computed(() => !!token.value)

  async function login(loginEmail: string, password: string) {
    const res = await api.post('/admin/login', { email: loginEmail, password })
    token.value = res.data.token
    email.value = res.data.email
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('email', res.data.email)
    setStoredTenantId(null)
    return res.data
  }

  function logout() {
    token.value = ''
    email.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    setStoredTenantId(null)
  }

  return { token, email, isLoggedIn, login, logout }
})
