/**
 * 后端 DTO / VO 的 TypeScript 映射。
 *
 * 命名与后端一一对应(*VO 对应后端 VO),这样看到一个字段名就知道
 * 该去哪个 Java 文件里找它的来源。
 */

/** 后端统一响应信封。HTTP 状态码恒为 200,业务结果看 code。 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------------------------------------------------------------------
 *  用户
 * ------------------------------------------------------------------------- */

export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  bio: string | null
  role: 'USER' | 'ADMIN'
  createdAt: string
}

export interface AuthVO {
  token: string
  user: UserVO
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  nickname: string
}

export interface UpdateProfilePayload {
  nickname?: string
  bio?: string
  avatar?: string
}

/* ---------------------------------------------------------------------------
 *  图片
 * ------------------------------------------------------------------------- */

export interface PhotoVO {
  id: number
  url: string
  thumbUrl: string | null
  width: number | null
  height: number | null
  sortOrder: number | null
}

/* ---------------------------------------------------------------------------
 *  日记
 * ------------------------------------------------------------------------- */

export interface EntrySummaryVO {
  id: number
  title: string
  excerpt: string | null
  entryDate: string
  locationName: string | null
  latitude: number | null
  longitude: number | null
  weather: string | null
  mood: string | null
  photoCount: number
  coverUrl: string | null
  tags: string[]
  tripId: number | null
  tripTitle: string | null
  canEdit: boolean
}

export interface EntryDetailVO {
  id: number
  title: string
  content: string
  entryDate: string
  locationName: string | null
  latitude: number | null
  longitude: number | null
  weather: string | null
  mood: string | null
  photos: PhotoVO[]
  tags: string[]
  tripId: number | null
  tripTitle: string | null
  viewCount: number
  canEdit: boolean
  bookmarked: boolean
  createdAt: string
  updatedAt: string
}

export interface EntryPayload {
  title: string
  content: string
  entryDate?: string
  tripId?: number | null
  locationName?: string | null
  latitude?: number | null
  longitude?: number | null
  weather?: string | null
  mood?: string | null
  tags?: string[]
  photoUrls?: string[]
}

/**
 * 日记列表的查询条件。
 *
 * 这里用 type 而不是 interface:axios 的 params 需要一个带隐式索引签名的对象,
 * 而 interface 声明出来的类型恰好没有,直接传进去 TS 会报错。
 */
export type EntryQuery = {
  tripId?: number
  tag?: string
  keyword?: string
  year?: number
  month?: number
  sort?: 'latest' | 'oldest'
  page?: number
  size?: number
}

/* ---------------------------------------------------------------------------
 *  行程
 * ------------------------------------------------------------------------- */

export interface TripVO {
  id: number
  title: string
  description: string | null
  /** 实际该显示的那张图,后端已解析好:自定义封面 → 日记首图兜底 → null(前端渲染渐变) */
  coverUrl: string | null
  /** 作者自己指定的封面图;为 null 说明当前 coverUrl 是系统兜底出来的 */
  coverPhotoId: number | null
  visibility: 'PUBLIC' | 'PRIVATE'
  archived: boolean
  entryCount: number
  startDate: string | null
  endDate: string | null
  locationSummary: string | null
  createdAt: string
  /** 作者本人或管理员才能改;公开行程别人可读但不可改 */
  canEdit: boolean
}

export interface TripDetailVO {
  trip: TripVO
  entries: EntrySummaryVO[]
}

export interface TripPayload {
  title?: string
  description?: string
  visibility?: 'PUBLIC' | 'PRIVATE'
  archived?: boolean
  /**
   * 封面图 id(来自上传接口)。不传 = 不动这一项。
   * 传 0 是刻意的特殊值,表示"清掉自定义封面"退回兜底 ——
   * 因为 null 已经被"没传,别动"占用了。
   */
  coverPhotoId?: number | null
}

/* ---------------------------------------------------------------------------
 *  地图 / 统计 / 回忆
 * ------------------------------------------------------------------------- */

export interface MapPointVO {
  id: number
  title: string
  latitude: number
  longitude: number
  entryDate: string
  locationName: string | null
  coverUrl: string | null
  tripId: number | null
  tripTitle: string | null
}

export interface FootprintVO {
  tripId: number | null
  tripTitle: string | null
  pointCount: number
  startDate: string | null
  endDate: string | null
  points: MapPointVO[]
}

export interface YearStat {
  year: number
  entryCount: number
  dayCount: number
  placeCount: number
}

export interface MonthStat {
  year: number
  month: number
  entryCount: number
}

export interface StatsVO {
  entryCount: number
  tripCount: number
  placeCount: number
  dayCount: number
  photoCount: number
  yearCount: number
  firstDate: string | null
  yearly: YearStat[]
  monthly: MonthStat[]
}

export interface MemoryVO {
  entryId: number
  title: string
  excerpt: string | null
  locationName: string | null
  coverUrl: string | null
  entryDate: string
  yearsAgo: number
  caption: string
}

export interface ToggleVO {
  active: boolean
  count: number
}
