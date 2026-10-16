/**
 * 按年份分组的工具。
 *
 * 时间线是按年分段的列表:2026 年一段、2025 年一段……
 * 分组逻辑放在这里而不是组件里,是为了让"年份降序、年内日期降序"
 * 这个排序约定只有一个来源。
 */

export interface Grouped<T> {
  year: number
  items: T[]
}

/**
 * 把带 entryDate 的列表按年分组。
 *
 * @param items 任意带 entryDate 的对象数组
 * @param descending 年份是否降序(时间线默认新→旧)
 */
export function groupByYear<T extends { entryDate: string }>(
  items: T[],
  descending = true,
): Grouped<T>[] {
  const buckets = new Map<number, T[]>()

  for (const item of items) {
    const year = Number(item.entryDate.slice(0, 4))
    if (!Number.isFinite(year)) continue
    const bucket = buckets.get(year)
    if (bucket) {
      bucket.push(item)
    } else {
      buckets.set(year, [item])
    }
  }

  const years = [...buckets.keys()].sort((a, b) => (descending ? b - a : a - b))
  return years.map((year) => ({ year, items: buckets.get(year)! }))
}

/**
 * 按年月分组。用于"按月看"的筛选结果。
 */
export function groupByMonth<T extends { entryDate: string }>(items: T[]): { key: string; label: string; items: T[] }[] {
  const buckets = new Map<string, T[]>()

  for (const item of items) {
    const key = item.entryDate.slice(0, 7) // yyyy-MM
    const bucket = buckets.get(key)
    if (bucket) {
      bucket.push(item)
    } else {
      buckets.set(key, [item])
    }
  }

  return [...buckets.keys()]
    .sort((a, b) => b.localeCompare(a))
    .map((key) => {
      const [year, month] = key.split('-')
      return { key, label: `${year} 年 ${Number(month)} 月`, items: buckets.get(key)! }
    })
}
