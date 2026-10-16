import { get } from './http'
import type { FootprintVO, MapPointVO, MemoryVO, StatsVO } from './types'

export const insightApi = {
  /** 地图上的全部足迹点 */
  points: () => get<MapPointVO[]>('/map/points'),
  /** 按行程分组的足迹路线 */
  footprints: () => get<FootprintVO[]>('/map/footprints'),
  stats: () => get<StatsVO>('/stats'),
  /** 历史上的今天 */
  memories: () => get<MemoryVO[]>('/memories/today'),
}
