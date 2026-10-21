/**
 * 把 DataV 34 份省级下层(市级)边界拼成一份缓存 china_cities.json。
 *
 * build-litmap.mjs 不想为每个省发一次请求,所以由这个脚本集中拉一次、
 * 缓存在 geo-data/(下游只需要一份文件)。key 是请求路径(如 "/350000_full.json"),
 * value 是解析好的 FeatureCollection,和 DataV 原始返回一一对应。
 *
 * 用法:cd tools/geo-build && node fetch-cities.mjs
 * 失败的省**不会**写进缓存,重跑可续;build-litmap 对拿不到的省整域一块兜底。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const base = process.env.GEO_BUILD_DIR || path.resolve(process.cwd())
const dataDir = path.join(base, 'geo-data')
const outPath = path.join(dataDir, 'china_cities.json')

const full = JSON.parse(fs.readFileSync(path.join(dataDir, 'china_full.json'), 'utf8'))
const cache = fs.existsSync(outPath) ? JSON.parse(fs.readFileSync(outPath, 'utf8')) : {}

let fetched = 0
let failed = []
for (const feat of full.features) {
  const { adcode, name, childrenNum } = feat.properties
  const ad = String(adcode)
  // 九段线段没有边界下层;台/港/澳走整域一块,不拉市级数据
  if (!name || childrenNum === 0 || ['710000', '810000', '820000'].includes(ad)) continue
  const key = `/${ad}_full.json`
  if (cache[key]) continue
  const url = `https://geo.datav.aliyun.com/areas_v3/bound/${ad}_full.json`
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    cache[key] = data
    fetched += 1
    const count = data.features.filter((f) => f.properties.name).length
    console.log(`${ad} ${name}: ${count} 块`)
  } catch (error) {
    failed.push(`${ad} ${name}(${error.message})`)
  }
}

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, JSON.stringify(cache))
const kb = Math.round(fs.statSync(outPath).size / 1024)
console.log(`\n新增 ${fetched} 份,失败 ${failed.length} 份,缓存 ${Object.keys(cache).length} 份,${kb} KB -> ${outPath}`)
if (failed.length) {
  console.log('失败(重跑可续):')
  for (const item of failed) console.log(`  ${item}`)
  process.exitCode = 1
}
