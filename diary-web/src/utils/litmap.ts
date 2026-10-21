/**
 * 点亮地图:板块数据 + 点亮判定。
 *
 * 板块多边形来自仓库静态资源 /geo/litmap-geo.json
 * (生成脚本见 tools/geo-build,数据来源与坐标系的取舍在那里说明)。
 *
 * 点亮判定在**前端**做:后端 /map/points 只回足迹点,
 * 拿点位对板块做多边形包含测试(point-in-polygon)即可,
 * 不需要动表结构 —— 点亮是几何推导,不是状态存储:
 * 改地点、删日记之后"熄灭"是自动的,不存在失同步。
 *
 * 算法量级:射线法每环 O(顶点数)。全包 34 省 + 240 国约 5 万顶点,
 * 几百个足迹点全测一遍是几十毫秒,而且只在数据到位后算一次。
 *
 * 坐标系说明(为什么结果有几百米毛刺):中国省界来自 DataV(高德 GCJ02),
 * 海外国界来自 Natural Earth(WGS84),Leaflet 的 OSM 瓦片是 WGS84。
 * GCJ 与 WGS 的偏差在数百米量级,对"省级/国家级"的点亮示意无感,
 * 因此省去一次纠偏依赖;详见 tools/geo-build/README.md。
 */
import type { Geometry } from 'geojson'

/** 一个板块:省级或国家级。 */
export interface LitRegion {
  /** 稳定 id:省用 adcode,国家用 ISO-3 */
  id: string
  /** 展示名(省级直接中文,国名已映射中文,未收录的保留英文) */
  name: string
  /** 地理质心;生成时没算出来的国家为 null */
  centroid: [number, number] | null
  geometry: Geometry
}

interface LitMapBundle {
  provinces: LitRegion[]
  countries: LitRegion[]
}

/** 板块数据的会话级缓存:整个应用只拉一次 */
let bundlePromise: Promise<LitMapBundle> | null = null

export function loadLitRegions(): Promise<LitMapBundle> {
  bundlePromise ??= fetch('/geo/litmap-geo.json').then((res) => {
    if (!res.ok) throw new Error(`板块数据加载失败(${res.status})`)
    return res.json() as Promise<LitMapBundle>
  })
  return bundlePromise
}

/* -------------------------------------------------------------------------
 *  多边形包含测试
 * ---------------------------------------------------------------------- */

/** 射线法:点是否在一个环内(不处理跨经线 180°,板块数据不含该地区) */
function inRing(x: number, y: number, ring: number[][]): boolean {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0]
    const yi = ring[i][1]
    const xj = ring[j][0]
    const yj = ring[j][1]
    const crosses = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi
    if (crosses) inside = !inside
  }
  return inside
}

/** 点是否在一个 Polygon 内:在外环内,且不在任何内环(洞,如湖泊飞地)里 */
function inPolygon(x: number, y: number, rings: number[][][]): boolean {
  if (!inRing(x, y, rings[0])) return false
  for (let k = 1; k < rings.length; k++) {
    if (inRing(x, y, rings[k])) return false
  }
  return true
}

/** 点是否在任意 Polygon/MultiPolygon 内 */
export function inGeometry(x: number, y: number, geometry: Geometry): boolean {
  if (geometry.type === 'Polygon') return inPolygon(x, y, geometry.coordinates)
  if (geometry.type === 'MultiPolygon') {
    for (const poly of geometry.coordinates) {
      if (inPolygon(x, y, poly)) return true
    }
  }
  return false
}

/** 足迹点所属板块:省优先(更细),未命中再看国家。 */
export function regionOf(x: number, y: number, bundle: LitMapBundle): LitRegion | null {
  for (const province of bundle.provinces) {
    if (inGeometry(x, y, province.geometry)) return province
  }
  for (const country of bundle.countries) {
    if (inGeometry(x, y, country.geometry)) return country
  }
  return null
}

/**
 * 板块几何的 bbox([[minLat, minLng], [maxLat, maxLng]]),给 fitBounds 用。
 * 点亮模式切换定位镜头用,取值不必精确 —— 是取景,不是测量。
 */
export function geometryBounds(geometry: Geometry): [[number, number], [number, number]] | null {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  const rings =
    geometry.type === 'Polygon'
      ? geometry.coordinates
      : geometry.type === 'MultiPolygon'
        ? geometry.coordinates.flat()
        : null
  if (!rings) return null
  for (const ring of rings) {
    for (const [x, y] of ring) {
      if (x < minX) minX = x
      if (y < minY) minY = y
      if (x > maxX) maxX = x
      if (y > maxY) maxY = y
    }
  }
  if (!Number.isFinite(minX)) return null
  return [
    [minY, minX],
    [maxY, maxX],
  ]
}
