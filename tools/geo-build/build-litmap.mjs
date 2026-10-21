/**
 * 生成前端「点亮地图」用的板块数据 litmap-geo.json。
 *
 * 输入(数据文件在 ../geo-data/,与脚本分离,来历见 README 注释):
 *   - china_full.json       DataV省界(内含高德坐标系,GCJ02)
 *   - china_cities.json     DataV市级面(31份 {省adcode}_full.json 拼成的缓存,见 fetch-cities.mjs)
 *   - china_counties.json   DataV区县面(329份 {市adcode}_full.json 拼成的缓存,见 fetch-counties.mjs)
 *   - ne50_admin0.json      Natural Earth 50m 国界(WGS84)
 *
 * 输出:
 *   - litmap-geo.json     { cities: [...], countries: [...] }
 *
 * 板块颗粒度:中国的点亮单元是**区县**(约 2700 块);
 * 去过龙岩的新罗区就只亮新罗区,不会亮整个龙岩市。海外国家维持国家级。
 *
 * 坐标说明:DataV 是 GCJ02、NE 是 WGS84,两套混用会有约几百米的缝隙。
 * 点亮地图是"区县/国家级"的示意视图,几百米的边界缝在缩放级别 4~7 不可见,
 * 为了省一次纠偏依赖(WGS↔GCJ 转换表),接受这份误差。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const base = process.env.GEO_BUILD_DIR || path.resolve(process.cwd())
const dataDir = path.join(base, 'geo-data')
const outPath = path.join(base, 'out', 'litmap-geo.json')
fs.mkdirSync(path.dirname(outPath), { recursive: true })

/* ---------------- Douglas-Peucker 简化(迭代实现,无递归栈风险) ---------------- */

function sqSegDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax
  const dy = by - ay
  const l2 = dx * dx + dy * dy
  if (l2 === 0) {
    const ex = px - ax
    const ey = py - ay
    return ex * ex + ey * ey
  }
  let t = ((px - ax) * dx + (py - ay) * dy) / l2
  t = Math.max(0, Math.min(1, t))
  return (ax + t * dx - px) ** 2 + (ay + t * dy - py) ** 2
}

function simplifyRing(ring, tol) {
  const n = ring.length
  // 3 位小数 ≈ 110 米:省界/国界在点亮视图的缩放级别下分辨不出这个差别,
  // 但能把每个坐标从 ~19 字节压到 ~14 字节,整包省下四分之一的体积。
  const round = (p) => [+p[0].toFixed(3), +p[1].toFixed(3)]
  if (n < 5) return ring.map(round)
  const keep = new Uint8Array(n)
  keep[0] = 1
  keep[n - 1] = 1
  const stack = [[0, n - 1]]
  while (stack.length) {
    const [s, e] = stack.pop()
    if (e <= s + 1) continue
    let maxD = -1
    let idx = -1
    for (let i = s + 1; i < e; i++) {
      const d = sqSegDist(ring[i][0], ring[i][1], ring[s][0], ring[s][1], ring[e][0], ring[e][1])
      if (d > maxD) {
        maxD = d
        idx = i
      }
    }
    if (maxD > tol * tol) {
      keep[idx] = 1
      stack.push([s, idx], [idx, e])
    }
  }
  const out = []
  for (let i = 0; i < n; i++) if (keep[i]) out.push(round(ring[i]))
  return out
}

function simplifyGeometry(geom, tol) {
  if (geom.type === 'Polygon') {
    return { type: 'Polygon', coordinates: geom.coordinates.map((r) => simplifyRing(r, tol)) }
  }
  return {
    type: 'MultiPolygon',
    coordinates: geom.coordinates.map((p) => p.map((r) => simplifyRing(r, tol))),
  }
}

/* ---------------- 1. 中国区县级板块(DataV) ---------------- */

/**
 * 收集区县级面,点亮单元是区县(约 2700 块)。
 *
 * 走两级缓存,路径各省各市:
 * - 有下层且有缓存 → 拆成区县(如龙岩市 → 新罗区/永定区/…);
 * - 直筒子市(东莞/中山/嘉峪关等无下层)与直辖市辖区 → 市面本身就是叶子(保留市面),
 *   直辖市的"区"不是区县,是自成一体的点亮单元,与普通市同级;
 * - 台湾/香港/澳门、九段线段、缓存缺失兜底 → 整域一块。
 */
const CITIES_CACHE = path.join(dataDir, 'china_cities.json')
const COUNTIES_CACHE = path.join(dataDir, 'china_counties.json')
const DIRECT_MUNICIPALITIES = new Set(['110000', '120000', '310000', '500000'])
const WHOLE_REGION = new Set(['710000', '810000', '820000']) // 台湾/香港/澳门

const dataV = JSON.parse(fs.readFileSync(path.join(dataDir, 'china_full.json'), 'utf8'))
const citiesCache = JSON.parse(fs.readFileSync(CITIES_CACHE, 'utf8'))
const countiesCache = JSON.parse(fs.readFileSync(COUNTIES_CACHE, 'utf8'))
const cities = []
for (const feat of dataV.features) {
  const p = feat.properties
  const adcode = String(p.adcode)
  if (!feat.geometry || !p.name) continue // 含 100000_JD 九段线段(features[0].name 为空)
  if (WHOLE_REGION.has(adcode)) {
    // 整域一块:台港澳,以及数据缺失时的兜底
    cities.push({
      id: adcode,
      name: p.name,
      centroid: p.centroid || p.center,
      geometry: simplifyGeometry(feat.geometry, 0.01),
    })
    continue
  }
  const childFile = citiesCache[`/${adcode}_full.json`]
  const direct = DIRECT_MUNICIPALITIES.has(adcode)
  for (const city of childFile?.features ?? []) {
    if (!city.geometry) continue
    const cp = city.properties
    if (!cp.name) continue
    // 直辖市的区:市面已经是叶子;普通市:再看一层拆区县
    if (direct || cp.childrenNum === 0) {
      cities.push({
        id: String(cp.adcode),
        name: cp.name,
        parent: p.name,
        centroid: cp.centroid || cp.center,
        geometry: simplifyGeometry(city.geometry, 0.01),
      })
      continue
    }
    const counties = countiesCache[`/${cp.adcode}_full.json`]
    let count = 0
    for (const county of counties?.features ?? []) {
      if (!county.geometry || !county.properties.name) continue
      count += 1
      cities.push({
        id: String(county.properties.adcode),
        name: county.properties.name,
        parent: cp.name,
        province: p.name,
        centroid: county.properties.centroid || county.properties.center,
        geometry: simplifyGeometry(county.geometry, 0.01),
      })
    }
    if (count === 0) {
      // 缓存缺失时少见但会真题:市级兜底不省,亮点还是要能亮
      cities.push({
        id: String(cp.adcode),
        name: cp.name,
        parent: p.name,
        centroid: cp.centroid || cp.center,
        geometry: simplifyGeometry(city.geometry, 0.01),
      })
    }
  }
}

/* ---------------- 2. 海外国家板块(Natural Earth 50m) ---------------- */

const CN = JSON.parse(fs.readFileSync(path.join(base, 'out', 'country-name-cn.json'), 'utf8'))
const ne = JSON.parse(fs.readFileSync(path.join(dataDir, 'ne50_admin0.json'), 'utf8'))
const countries = []
for (const feat of ne.features) {
  const admin = feat.properties.ADMIN
  if (!feat.geometry || !admin) continue
  // 内地省份已由 DataV 提供;港澳台在 cities 里整域成块,三个都跳过,
  // 否则同几何画两遍(点亮判断也会双份计数)。
  if (admin === 'Antarctica' || admin === 'China' || ['Taiwan', 'Hong Kong S.A.R.', 'Macao S.A.R'].includes(admin)) continue
  countries.push({
    id: feat.properties.ADM0_A3 || admin,
    name: CN[admin] || admin,
    centroid: feat.properties.LABEL_X != null && feat.properties.LABEL_Y != null
      ? [feat.properties.LABEL_X, feat.properties.LABEL_Y]
      : null,
    geometry: simplifyGeometry(feat.geometry, 0.08), // ≈8 公里:洲际视图本就粗略
  })
}

const bundle = { cities, countries }
fs.writeFileSync(outPath, JSON.stringify(bundle))
const kb = Math.round(fs.statSync(outPath).size / 1024)
console.log(`cities: ${cities.length}, countries: ${countries.length}, bundle: ${kb} KB -> ${outPath}`)
