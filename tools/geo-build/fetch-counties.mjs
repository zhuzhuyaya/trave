/**
 * 把 DataV 各地级市的县级(区县)边界拼成一份缓存 china_counties.json。
 *
 * build-litmap.mjs 的点亮单元在县级:对每个地级市再下一层,拉 {市adcode}_full.json。
 * 只拉 childrenNum > 0 的市(东莞/中山这类直筒子市没有下层,
 * 由省级缓存里的市面直接当板块);直辖市到此为止 —— 它们的市辖区已经是
 * 点亮单元,不再往下拆街道。
 *
 * 用法:cd tools/geo-build && node fetch-counties.mjs
 * 失败的市不写进缓存,重跑可续;DataV 没给限流说明,这里串行 + 间隔 120ms + 失败重试一次。
 */
import fs from 'node:fs'
import path from 'node:path'

const base = process.env.GEO_BUILD_DIR || path.resolve(process.cwd())
const dataDir = path.join(base, 'geo-data')
const outPath = path.join(dataDir, 'china_counties.json')

const DIRECT_MUNICIPALITIES = new Set(['110000', '120000', '310000', '500000'])
const cities = JSON.parse(fs.readFileSync(path.join(dataDir, 'china_cities.json'), 'utf8'))
const cache = fs.existsSync(outPath) ? JSON.parse(fs.readFileSync(outPath, 'utf8')) : {}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let fetched = 0
const failed = []
for (const file of Object.values(cities)) {
  for (const feat of file.features) {
    const p = feat.properties
    const ad = String(p.adcode)
    if (!p.name || p.childrenNum === 0) continue
    if (DIRECT_MUNICIPALITIES.has(String(p.parent?.adcode ?? ''))) continue
    const key = `/${ad}_full.json`
    if (cache[key]) continue
    let data
    try {
      const res = await fetch(`https://geo.datav.aliyun.com/areas_v3/bound/${ad}_full.json`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      data = await res.json()
    } catch (first) {
      try {
        await sleep(1000)
        const res = await fetch(`https://geo.datav.aliyun.com/areas_v3/bound/${ad}_full.json`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        data = await res.json()
      } catch (second) {
        failed.push(`${ad} ${p.name}(${second.message})`)
        continue
      }
    }
    cache[key] = data
    fetched += 1
    if (fetched % 20 === 0) console.log(`...已拉 ${fetched} 份`)
    await sleep(120)
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
