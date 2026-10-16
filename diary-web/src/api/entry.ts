import { del, get, post, put } from './http'
import type {
  EntryDetailVO,
  EntryPayload,
  EntryQuery,
  EntrySummaryVO,
  PageResult,
  ToggleVO,
} from './types'

export const entryApi = {
  list: (query: EntryQuery = {}) => get<PageResult<EntrySummaryVO>>('/entries', query),
  detail: (id: number) => get<EntryDetailVO>(`/entries/${id}`),
  create: (payload: EntryPayload) => post<EntryDetailVO>('/entries', payload),
  update: (id: number, payload: EntryPayload) => put<EntryDetailVO>(`/entries/${id}`, payload),
  remove: (id: number) => del<void>(`/entries/${id}`),
  /** 阅读数 +1。单独一个接口而不是读详情时顺便加,避免 GET 产生副作用。 */
  recordView: (id: number) => post<void>(`/entries/${id}/view`),
  toggleBookmark: (id: number) => post<ToggleVO>(`/entries/${id}/bookmark`),
}
