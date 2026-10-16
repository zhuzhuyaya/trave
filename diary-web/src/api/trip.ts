import { del, get, post, put } from './http'
import type { PageResult, TripDetailVO, TripPayload, TripVO } from './types'

export type TripQuery = {
  page?: number
  size?: number
  includeArchived?: boolean
}

export const tripApi = {
  list: (query: TripQuery = {}) => get<PageResult<TripVO>>('/trips', query),
  detail: (id: number) => get<TripDetailVO>(`/trips/${id}`),
  create: (payload: TripPayload) => post<TripVO>('/trips', payload),
  update: (id: number, payload: TripPayload) => put<TripVO>(`/trips/${id}`, payload),
  remove: (id: number) => del<void>(`/trips/${id}`),
}
