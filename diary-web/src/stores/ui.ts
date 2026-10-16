import { ref } from 'vue'
import { defineStore } from 'pinia'

export type ToastKind = 'info' | 'success' | 'error'

export interface Toast {
  id: number
  text: string
  kind: ToastKind
}

/**
 * 轻提示队列。
 *
 * 用自己写的而不是引一个 toast 库:iOS 系统里根本没有传统意义的 toast,
 * 有的是顶部横幅与 Haptic 反馈。这里的样式完全按苹果的设计走,
 * 引第三方库反而要跟它的默认样式打架。
 */
export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  const timers = new Map<number, ReturnType<typeof setTimeout>>()
  let nextId = 1

  /** 同时最多显示 3 条 —— 再多会盖住半个屏幕 */
  const MAX_VISIBLE = 3
  const DURATION = 2600

  function push(text: string, kind: ToastKind = 'info'): number {
    const id = nextId++
    toasts.value.push({ id, text, kind })

    // 超出上限时把最早的挤掉,连同它的定时器一起清
    while (toasts.value.length > MAX_VISIBLE) {
      const dropped = toasts.value.shift()
      if (dropped) {
        const timer = timers.get(dropped.id)
        if (timer) {
          clearTimeout(timer)
          timers.delete(dropped.id)
        }
      }
    }

    timers.set(
      id,
      setTimeout(() => dismiss(id), DURATION),
    )
    return id
  }

  function success(text: string): number {
    return push(text, 'success')
  }

  function error(text: string): number {
    // 错误停留久一点,用户需要时间读完
    const id = push(text, 'error')
    const existing = timers.get(id)
    if (existing) {
      clearTimeout(existing)
      timers.set(
        id,
        setTimeout(() => dismiss(id), 4000),
      )
    }
    return id
  }

  function dismiss(id: number): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
    const timer = timers.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.delete(id)
    }
  }

  return { toasts, push, success, error, dismiss }
})
