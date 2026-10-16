/**
 * 小工具。
 */

/** 按 id 稳定生成一个渐变色,给没有头像/封面的条目用。 */
export function gradientFor(seed: string | number): string {
  const palettes = [
    'linear-gradient(135deg, #7db8f0, #a78bfa 55%, #f0a6c8)',
    'linear-gradient(135deg, #f6b89b, #e88fa5 55%, #b48ad6)',
    'linear-gradient(135deg, #8fd3c7, #6fb3e0 60%, #9a8ee0)',
    'linear-gradient(135deg, #f3c98b, #e9a06b 55%, #d98aa0)',
    'linear-gradient(135deg, #a3c9f0, #8fb8e8 50%, #c0a8e8)',
    'linear-gradient(135deg, #e8a4b8, #d18fc0 55%, #a98fd8)',
  ]
  const hash = String(seed)
    .split('')
    .reduce((acc, char) => (acc * 31 + char.charCodeAt(0)) >>> 0, 7)
  return palettes[hash % palettes.length]
}

/** 取名字的第一个字,作为无头像时的占位。 */
export function initialOf(name: string | null | undefined): string {
  if (!name) return '旅'
  return name.trim().charAt(0) || '旅'
}

/** 中文数字,给"三年前"这类文案用。 */
export function numberToCN(n: number): string {
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
  if (n <= 10) return digits[n] ?? String(n)
  if (n < 20) return `十${digits[n - 10]}`
  return String(n)
}

/** 防抖。搜索输入用它可以避免每敲一个字打一次接口。 */
export function debounce<T extends (...args: never[]) => void>(fn: T, wait = 300): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  return ((...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), wait)
  }) as T
}

/** 简单限流:同一时刻最多同时跑 limit 个任务,用于并发上传图片。 */
export async function concurrent<T, R>(
  items: T[],
  limit: number,
  worker: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const results: R[] = new Array(items.length)
  let cursor = 0

  async function run(): Promise<void> {
    while (cursor < items.length) {
      const index = cursor++
      results[index] = await worker(items[index], index)
    }
  }

  const runners = Array.from({ length: Math.min(limit, items.length) }, run)
  await Promise.all(runners)
  return results
}
