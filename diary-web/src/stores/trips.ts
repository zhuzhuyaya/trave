import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { tripApi } from '@/api/trip'
import { toMessage } from '@/api/http'
import type { TripVO } from '@/api/types'

/**
 * 行程的全局缓存。
 *
 * 行程列表在多个页面都要用(时间线的筛选、行程页、写日记时的归属选择),
 * 但它变化很少。做成"取一次全局复用",避免每次切 Tab 都重新请求。
 *
 * 错误存在 state 里而不是抛出去:页面自己决定是显示空态、错误提示还是重试按钮。
 */
export const useTripStore = defineStore('trips', () => {
  const items = ref<TripVO[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)

  const options = computed(() =>
    items.value.map((trip) => ({ value: trip.id, label: trip.title })),
  )

  async function ensureLoaded(): Promise<void> {
    if (loaded.value || loading.value) return
    await reload()
  }

  async function reload(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      // size 取上限:行程数量远小于 50,一次拉完省掉分页 UI
      const result = await tripApi.list({ page: 1, size: 50 })
      items.value = result.list
      loaded.value = true
    } catch (e) {
      error.value = toMessage(e)
      items.value = []
    } finally {
      loading.value = false
    }
  }

  function byId(id: number | null | undefined): TripVO | undefined {
    if (id == null) return undefined
    return items.value.find((trip) => trip.id === id)
  }

  /** 新建/改名/删除后调用,让缓存跟服务端对齐。 */
  function invalidate(): void {
    loaded.value = false
  }

  return { items, loading, loaded, error, options, ensureLoaded, reload, byId, invalidate }
})
