/**
 * GCJ02 ↔ WGS84 坐标互转(火星坐标纠偏,零依赖纯数学)。
 *
 * 为什么需要:本应用的坐标数据有三个来源、两种坐标系 ——
 * - OSM 瓦片(经 /tiles 代理)是 WGS84:点哪蓝圈就在哪,点出来的坐标是准的;
 * - 内置地点表(places.ts,坐标当初取自高德)与 DataV 板块数据(区县边界/质心)
 *   都是 GCJ02("火星坐标",国测局加偏):它们直接画在 WGS84 瓦片上
 *   会整体偏 300~600 米 —— 这就是"搜索落点蓝圈总偏一点"的病根;
 * - 在线检索(OSM Nominatim)是 WGS84,与瓦片同系,不用转。
 *
 * 口径:调用方各自知道手里坐标是哪个系 —— **一律转成 WGS84 再落图、再入库**,
 * 让 OSM 瓦片、在线检索、新存的日记坐标处在同一坐标系;板块多边形是 GCJ02,
 * 反查地名(点在多边形内)前把点 wgsToGcj 转过去即可,多边形本身不动。
 * 注意两个函数都按 GeoJSON 习惯回「[经度, 纬度]」,Leaflet 的 [lat, lng] 要倒一次。
 *
 * 算法是公开的 eviltransform(对国测局偏移公式的社区逆向实现),中国境外原样返回。
 * gcjToWgs 用"正向函数迭代逼近"的近似逆:两三轮残差修正即可到米级,足够落图。
 */

/** 克拉索夫斯基椭球;GCJ02 偏移公式固定用它 */
const A = 6378245
const EE = 0.00669342162296594326

/** 粗判是否在中国大陆偏移范围内:境外没有 GCJ 偏移,原样返回 */
export function outOfChina(lng: number, lat: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

/** 偏移量多项式(国测局公式),x/y 是相对中心的经纬度 */
function transformDelta(x: number, y: number): [number, number] {
  let dLat =
    -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  dLat += (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
  dLat += (20 * Math.sin(y * Math.PI) + 40 * Math.sin(y / 3 * Math.PI)) * 2 / 3
  dLat += (160 * Math.sin(y / 12 * Math.PI) + 320 * Math.sin(y * Math.PI / 30)) * 2 / 3

  let dLng =
    300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  dLng += (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
  dLng += (20 * Math.sin(x * Math.PI) + 40 * Math.sin(x / 3 * Math.PI)) * 2 / 3
  dLng += (150 * Math.sin(x / 12 * Math.PI) + 300 * Math.sin(x / 30 * Math.PI)) * 2 / 3

  return [dLng, dLat]
}

/** WGS84 → GCJ02。正向是精确公式,一次到位。 */
export function wgsToGcj(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  const [dLng0, dLat0] = transformDelta(lng - 105, lat - 35)
  const radLat = (lat / 180) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  const dLat = (dLat0 * 180) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI)
  const dLng = (dLng0 * 180) / ((A / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

/**
 * GCJ02 → WGS84(迭代逼近的逆变换)。
 * 反向没有解析式:反复用正向公式修正残差,三轮即收敛到米级内(偏移本身才几百米)。
 */
export function gcjToWgs(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  let wLng = lng
  let wLat = lat
  for (let i = 0; i < 3; i++) {
    const [gLng, gLat] = wgsToGcj(wLng, wLat)
    wLng += lng - gLng
    wLat += lat - gLat
  }
  return [wLng, wLat]
}
