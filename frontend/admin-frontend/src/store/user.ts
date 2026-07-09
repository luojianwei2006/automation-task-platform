import { defineStore } from 'pinia'
import { ref } from 'vue'

interface UserInfo {
  id?: number
  username?: string
  displayName?: string
  roleType?: number
  merchantId?: number | null
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo>(loadUserInfo())

  function loadUserInfo(): UserInfo {
    try {
      const raw = localStorage.getItem('userInfo')
      return raw ? JSON.parse(raw) : {}
    } catch {
      return {}
    }
  }

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})
