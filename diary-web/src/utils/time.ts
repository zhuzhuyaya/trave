/**
 * 时间格式化。
 *
 * 刻意不引 dayjs / date-fns:这里只需要四五种固定格式,
 * 为它们多装一个依赖、多一份体积与升级风险并不划算。
 *
 * 所有函数都接收后端返回的 ISO 字符串或 'yyyy-MM-dd'(LocalDate)。
 */

/** 把 'yyyy-MM-dd' 或 ISO 时间串解析成 Date。避免 new Date('2026-10-15') 被当成 UTC 而差一天。 */
function parse(value: string): Date {
  // 纯日期串手动拆,不走 Date 解析 —— 'YYYY-MM-DD' 在部分实现里按 UTC 解释,
  // 东八区会变成前一天。这是移动端日期显示错一天的经典原因。
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (dateOnly) {
    return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
  }
  return new Date(value)
}

const pad = (n: number): string => String(n).padStart(2, '0')

/** 2026年10月15日 */
export function formatDateCN(value: string | null | undefined): string {
  if (!value) return ''
  const d = parse(value)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

/** 10月15日 */
export function formatMonthDay(value: string | null | undefined): string {
  if (!value) return ''
  const d = parse(value)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/** 2026.10.15 */
export function formatDateDots(value: string | null | undefined): string {
  if (!value) return ''
  const d = parse(value)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
}

/** 2026-10-15,给 <input type="date"> 用 */
export function formatDateISO(value: string | Date | null | undefined): string {
  if (!value) return ''
  const d = typeof value === 'string' ? parse(value) : value
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 2026年10月 */
export function formatYearMonth(value: string | null | undefined): string {
  if (!value) return ''
  const d = parse(value)
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
}

/** 星期几 */
export function weekdayCN(value: string | null | undefined): string {
  if (!value) return ''
  const labels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return labels[parse(value).getDay()]
}

/**
 * 相对时间:刚刚 / 12 分钟前 / 3 小时前 / 昨天 / 3 天前 / 2026.10.15
 *
 * 超过一周就退回绝对日期 —— "23 天前"这种说法人脑换算不过来,
 * 不如直接给日期。
 */
export function formatRelative(value: string | null | undefined): string {
  if (!value) return ''
  const d = parse(value)
  const diff = Date.now() - d.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < 0) return formatDateDots(value)
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 2 * day) return '昨天'
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDateDots(value)
}

/**
 * 日期区间:'2026.10.15 - 2026.10.20'。
 * 同一年时省略前一个年份后面的部分,看起来更清爽:'2026.10.15 - 10.20'。
 */
export function formatRange(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  if (!start) return ''
  if (!end || start === end) return formatDateDots(start)
  const a = parse(start)
  const b = parse(end)
  if (a.getFullYear() === b.getFullYear()) {
    return `${formatDateDots(start)} - ${pad(b.getMonth() + 1)}.${pad(b.getDate())}`
  }
  return `${formatDateDots(start)} - ${formatDateDots(end)}`
}

/** 天数(含首尾)。用于"5 天"这类行程时长展示。 */
export function daysBetween(start: string | null | undefined, end: string | null | undefined): number {
  if (!start || !end) return 1
  const a = parse(start).getTime()
  const b = parse(end).getTime()
  return Math.max(1, Math.round((b - a) / 86_400_000) + 1)
}

/** 距今多少天,给"今天/昨天"这类文案用 */
export function daysAgo(value: string | null | undefined): number {
  if (!value) return 0
  const d = parse(value)
  const today = new Date()
  d.setHours(0, 0, 0, 0)
  today.setHours(0, 0, 0, 0)
  return Math.round((today.getTime() - d.getTime()) / 86_400_000)
}
