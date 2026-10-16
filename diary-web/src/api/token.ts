/**
 * 登录凭证的本地存储。
 *
 * 单独抽出来是为了打断循环依赖:http.ts 要读 token,而 auth store 要用 http.ts。
 * 两边都只依赖这个模块,依赖就成了一条直线。
 */

import type { AuthVO, UserVO } from './types'

const TOKEN_KEY = 'diary.token'
const USER_KEY = 'diary.user'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    // 隐私模式下 localStorage 可能不可用,退化成"未登录"而不是白屏
    return null
  }
}

export function getStoredUser(): UserVO | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserVO) : null
  } catch {
    return null
  }
}

export function saveAuth(auth: AuthVO): void {
  try {
    localStorage.setItem(TOKEN_KEY, auth.token)
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
  } catch {
    // 存不进去也不影响本次会话,只是刷新后要重新登录
  }
}

export function saveUser(user: UserVO): void {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } catch {
    // 同上
  }
}

export function clearAuth(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  } catch {
    // 同上
  }
}
