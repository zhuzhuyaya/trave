import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { ApiError, toMessage } from '@/api/http'
import { clearAuth, getStoredUser, getToken, saveAuth, saveUser } from '@/api/token'
import type { AuthVO, LoginPayload, RegisterPayload, UserVO } from '@/api/types'

/** 后端 ResultCode.UNAUTHORIZED */
const CODE_UNAUTHORIZED = 40100

export const useAuthStore = defineStore('auth', () => {
  // 初始值直接取自本地存储:刷新页面时先按"已登录"渲染,避免顶栏闪一下"未登录"。
  // 真伪由 App.vue 启动时的 refresh() 向后端核对。
  const token = ref<string | null>(getToken())
  const user = ref<UserVO | null>(getStoredUser())
  const loading = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname ?? '')
  const username = computed(() => user.value?.username ?? '')

  function apply(auth: AuthVO): void {
    token.value = auth.token
    user.value = auth.user
    saveAuth(auth)
  }

  function clear(): void {
    token.value = null
    user.value = null
    clearAuth()
  }

  async function login(payload: LoginPayload): Promise<void> {
    loading.value = true
    try {
      apply(await authApi.login(payload))
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterPayload): Promise<void> {
    loading.value = true
    try {
      apply(await authApi.register(payload))
    } finally {
      loading.value = false
    }
  }

  /**
   * 向后端核对本地 token 是否还有效,并顺带刷新用户资料。
   *
   * 只在明确收到 401 时才清登录态:网络不通、后端没起来这些情况下
   * 把用户踢下线是很糟糕的体验 —— 他明明只是网不好。
   */
  async function refresh(): Promise<void> {
    if (!token.value) return
    try {
      const fresh = await authApi.me()
      user.value = fresh
      saveUser(fresh)
    } catch (error) {
      if (error instanceof ApiError && error.code === CODE_UNAUTHORIZED) {
        clear()
      }
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 服务端失败也要清掉本地状态,否则用户会卡在"登不出去"的状态里
    } finally {
      clear()
    }
  }

  function setUser(next: UserVO): void {
    user.value = next
    saveUser(next)
  }

  return {
    token,
    user,
    loading,
    isLoggedIn,
    isAdmin,
    displayName,
    username,
    login,
    register,
    logout,
    refresh,
    clear,
    setUser,
    toMessage,
  }
})
