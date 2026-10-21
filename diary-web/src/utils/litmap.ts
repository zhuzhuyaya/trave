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
 * 板块颗粒度:中国的点亮单元是**区县**(直筒子市保留市面,台港澳整域一块),
 * 去过龙岩的新罗区就只亮新罗区,不会点亮整个龙岩市;海外国家维持国家级。
 *
 * 算法量级:射线法每环 O(顶点数)。全包约 2800 市/县 + 240 国约 17 万顶点,
 * 几百个足迹点全测一遍是几十毫秒,而且只在数据到位后算一次。
 *
 * 坐标系说明(为什么结果有几百米毛刺):中国县界来自 DataV(高德 GCJ02),
 * 海外国界来自 Natural Earth(WGS84),Leaflet 的 OSM 瓦片是 WGS84。
 * GCJ 与 WGS 的偏差在数百米量级,对"区县/国家级"的点亮示意无感,
 * 因此省去一次纠偏依赖;详见 tools/geo-build/README.md。
 */
import type { Geometry } from 'geojson'

/** 一个板块:区县级或国家级。 */
export interface LitRegion {
  /** 稳定 id:市/县用 adcode,国家用 ISO-3 */
  id: string
  /** 展示名(区县直接中文,国名已映射中文,未收录的保留英文) */
  name: string
  /** 父级名,给地图选点拼 region 文案:县 → 市,直筒子市 → 省;台港澳没有 */
  parent?: string
  /** 祖级名(仅区县有):区县 → 省 */
  province?: string
  /** 地理质心;生成时没算出来的国家为 null */
  centroid: [number, number] | null
  geometry: Geometry
}

interface LitMapBundle {
  cities: LitRegion[]
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

/** 足迹点所属板块:区县优先(更细),未命中再看国家。 */
export function regionOf(x: number, y: number, bundle: LitMapBundle): LitRegion | null {
  for (const city of bundle.cities) {
    if (inGeometry(x, y, city.geometry)) return city
  }
  for (const country of bundle.countries) {
    if (inGeometry(x, y, country.geometry)) return country
  }
  return null
}

/**
 * 地图选点的地名文案:点哪就抄哪一块的名字,加上它自带的父子链。
 *
 * 输出与内置地点表的 region 风格对齐:「新罗区」得到 "福建 · 龙岩 · 新罗区",
 * 「武侯区」得到 "四川 · 成都 · 武侯区",「东莞市」(直筒子)得到 "广东 · 东莞市",
 * 港澳台整域块只有自身名。海外国家只抄国名。
 * 找不到所属板块(点了海面)回 null,调用方可以保留手动输入。
 */
export function placeAt(
  x: number,
  y: number,
  bundle: LitMapBundle,
): { name: string; region: string } | null {
  const city = bundle.cities.find((tile) => inGeometry(x, y, tile.geometry))
  if (city) {
    const parts = [city.province, city.parent, city.name].filter(
      (part, index, all) => part != null && all.indexOf(part) === index,
    )
    return { name: city.name, region: parts.join(' · ') }
  }
  const country = bundle.countries.find((tile) => inGeometry(x, y, tile.geometry))
  return country ? { name: country.name, region: country.name } : null
}

/**
 * 板块名搜索:给地图选点的搜索定位用。
 *
 * 2851 块板块一次建好索引(名字 + 父子链拼出的全文),过滤是纯字符串匹配,
 * 命中后用 centroid 飞行、geometryBounds 取景。县名重名很多(全国几十个"城关镇"),
 * 所以结果里带上父子链当副标题,用户看得出来自己选的是哪个。
 */
/**
 * 板块名搜索结果。
 */
export interface RegionHit {
  /** 板块自身名,如「新罗区」 */
  name: string
  /** 父子链文案,如「福建 · 龙岩」,当搜索结果副标题 */
  region: string
  /** 质心 [lat, lng];没算出的板块不进结果 */
  centroid: [number, number]
  /** 取景用 bbox */
  bounds: [[number, number], [number, number]]
}

/** 会话级索引:第一次搜索时建,板块数据不变就不用重建 */
let regionIndex: { hit: RegionHit; haystack: string }[] | null = null

function buildRegionIndex(bundle: LitMapBundle) {
  const index: { hit: RegionHit; haystack: string }[] = []
  for (const tile of bundle.cities) {
    const bounds = geometryBounds(tile.geometry)
    if (!tile.centroid || !bounds) continue
    const parts = [tile.province, tile.parent, tile.name].filter(
      (part, index, all) => part != null && all.indexOf(part) === index,
    )
    index.push({
      hit: {
        name: tile.name,
        region: parts.join(' · '),
        centroid: [tile.centroid[1], tile.centroid[0]],
        bounds,
      },
      haystack: parts.join(''),
    })
  }
  for (const tile of bundle.countries) {
    const bounds = geometryBounds(tile.geometry)
    if (!tile.centroid || !bounds) continue
    index.push({
      hit: {
        name: tile.name,
        region: tile.name,
        centroid: [tile.centroid[1], tile.centroid[0]],
        bounds,
      },
      haystack: tile.name,
    })
  }
  return index
}

/** 按关键词搜板块,最多回 limit 条。空关键词回空数组。 */
export function searchRegions(
  keyword: string,
  bundle: LitMapBundle,
  limit = 8,
): RegionHit[] {
  const query = keyword.trim()
  if (!query) return []
  regionIndex ??= buildRegionIndex(bundle)
  return regionIndex
    .filter((entry) => entry.haystack.includes(query))
    .slice(0, limit)
    .map((entry) => entry.hit)
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
