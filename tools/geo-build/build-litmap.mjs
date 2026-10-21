/**
 * 生成前端「点亮地图」用的板块数据 litmap-geo.json。
 *
 * 输入(数据文件在 ../geo-data/,与脚本分离,来历见 README 注释):
 *   - china_full.json     DataV省界(内含高德坐标系,GCJ02)
 *   - ne50_admin0.json    Natural Earth 50m 国界(WGS84)
 *
 * 输出:
 *   - litmap-geo.json     { provinces: [...], countries: [...] }
 *
 * 坐标说明:DataV 是 GCJ02、NE 是 WGS84,两套混用会有约几百米的缝隙。
 * 点亮地图是"省级/国家级"的示意视图,几百米的边界缝在缩放级别 4~7 不可见,
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

/* ---------------- 1. 中国省级板块(DataV) ---------------- */

const dataV = JSON.parse(fs.readFileSync(path.join(dataDir, 'china_full.json'), 'utf8'))
const provinces = []
for (const feat of dataV.features) {
  const p = feat.properties
  if (!feat.geometry || !p.name) continue
  provinces.push({
    id: String(p.adcode),
    name: p.name,
    centroid: p.centroid || p.center,
    geometry: simplifyGeometry(feat.geometry, 0.006), // ≈600 米:省级视图足够平滑
  })
}

/* ---------------- 2. 海外国家板块(Natural Earth 50m) ---------------- */

const CN = JSON.parse(fs.readFileSync(path.join(base, 'out', 'country-name-cn.json'), 'utf8'))
const ne = JSON.parse(fs.readFileSync(path.join(dataDir, 'ne50_admin0.json'), 'utf8'))
const countries = []
for (const feat of ne.features) {
  const admin = feat.properties.ADMIN
  if (!feat.geometry || !admin) continue
  if (admin === 'Antarctica' || admin === 'China') continue // 内地省份已由 DataV 提供
  countries.push({
    id: feat.properties.ADM0_A3 || admin,
    name: CN[admin] || admin,
    centroid: feat.properties.LABEL_X != null && feat.properties.LABEL_Y != null
      ? [feat.properties.LABEL_X, feat.properties.LABEL_Y]
      : null,
    geometry: simplifyGeometry(feat.geometry, 0.08), // ≈8 公里:洲际视图本就粗略
  })
}

const bundle = { provinces, countries }
fs.writeFileSync(outPath, JSON.stringify(bundle))
const kb = Math.round(fs.statSync(outPath).size / 1024)
console.log(`provinces: ${provinces.length}, countries: ${countries.length}, bundle: ${kb} KB -> ${outPath}`)
