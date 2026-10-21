<script setup lang="ts">
/**
 * 足迹地图(Leaflet + OpenStreetMap),两种看法:
 *
 * - **足迹**:照片标记按时间连成虚线路线,一地一卡的相册观感;
 * - **点亮**:区县/国家级板块拼成的底图,去过的地方才点亮,拼图观感。
 *
 * Leaflet 是命令式的 DOM 库,和 Vue 的声明式模型天然隔一层。
 * 这一页的处理方式是:Vue 只管"数据"和"面板 UI",
 * 地图实例、标记、连线、板块全部在 Leaflet 自己的世界里手动维护,
 * 两者只在 watch 到数据变化的那一个点交汇。
 * 硬要把 marker 做成 Vue 组件会同时丢掉两边的优势。
 *
 * 几个刻意的选择:
 *
 * 1. (足迹模式)标记用圆形照片而不是默认的蓝色水滴图钉。
 *    默认图钉一眼就是"网页地图",圆形照片标记才像相册在回看足迹。
 *    全部走 L.divIcon,也顺带避开了 Vite 打包时 marker-icon.png 路径丢失的经典坑。
 *
 * 2. (足迹模式)按时间把点连成虚线路线。散落的点看不出"一趟旅行",
 *    连起来才有轨迹感;用虚线而不是实线,是因为两点之间并没有真的走过一条直线。
 *
 * 3. (点亮模式)板块用 GeoJSON 面叠加,点亮判定在前端做
 *    (见 utils/litmap.ts 的说明):点亮是几何推导,不是状态存储,
 *    改地点、删日记后板块自动熄灭,不存在失同步。
 *    未点亮板块是地图上淡淡的影子,点亮板块填系统蓝 ——
 *    "点亮"的语义靠色差表达,不靠弹出气泡。
 *
 * 4. 地图与板块数据都按需异步加载:Leaflet 底图 CSS 一百多 KB、
 *    板块数据 1.4MB(gzip 不到一半),它们只在地图页用到,不该拖慢其他页面的首屏。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Map as LeafletMap, LayerGroup } from 'leaflet'
import NavBar from '@/components/ios/NavBar.vue'
import SegmentedControl from '@/components/ios/SegmentedControl.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { insightApi } from '@/api/insight'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { FootprintVO, MapPointVO } from '@/api/types'
import { formatDateCN } from '@/utils/time'
import { gradientFor } from '@/utils/misc'
import { wgsToGcj } from '@/utils/coord'
import {
  geometryBounds,
  loadLitRegions,
  regionOf,
  type LitRegion,
} from '@/utils/litmap'

/** 地图的两种视图。 */
type MapMode = 'footprint' | 'lit'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const mode = ref<MapMode>('footprint')
const points = ref<MapPointVO[]>([])
const footprints = ref<FootprintVO[]>([])
const loading = ref(true)
const failed = ref(false)

/** 点亮模式下被足迹覆盖的板块,按板块 id 索引;足迹点所属板块与覆盖计数一起存 */
const litById = ref<Map<string, { region: LitRegion; count: number }>>(new Map())
const litLoading = ref(false)

/** 当前选中的足迹点,对应底部那张浮动卡片(足迹模式) */
const selected = ref<MapPointVO | null>(null)
/** 点亮模式下选中的板块,对应底部那张浮动卡片 */
const selectedRegion = ref<{ region: LitRegion; count: number } | null>(null)

const mapEl = ref<HTMLElement | null>(null)
let map: LeafletMap | null = null
/**
 * 三层各自独立,切模式时整层换血:
 * footprintLayer 照片标记 + 虚线路线 / shadowLayer 未点亮板块的拼图底板 / litLayer 点亮板块
 */
let footprintLayer: LayerGroup | null = null
let litLayer: LayerGroup | null = null
let shadowLayer: LayerGroup | null = null

const hasPoints = computed(() => points.value.length > 0)

const cityCount = computed(() => {
  const names = new Set<string>()
  for (const point of points.value) {
    if (point.locationName) names.add(point.locationName)
    else if (point.tripTitle) names.add(point.tripTitle)
  }
  return names.size
})

const tripCount = computed(() => footprints.value.filter((item) => item.tripId != null).length)

/** 点亮了多少块:市块与国家可能重叠(点在港澳台这类整域块 + 国家面上曾有的双份已去重),合并计数 */
const litCount = computed(() => litById.value.size)

/* ---------------------------------------------------------------------------
 *  数据
 * ------------------------------------------------------------------------- */

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const [pointList, footprintList] = await Promise.all([
      insightApi.points(),
      insightApi.footprints(),
    ])
    points.value = pointList
    footprints.value = footprintList
  } catch (error) {
    failed.value = true
    ui.error(toMessage(error))
  } finally {
    loading.value = false
  }
}

/** 等板块数据到位,给足迹点做归属判定。板块文件较大,只在第一次进点亮模式时拉。 */
async function computeLit(): Promise<void> {
  if (litById.value.size > 0 || points.value.length === 0) return
  litLoading.value = true
  try {
    const bundle = await loadLitRegions()
    bundleCache = bundle
    const lit = new Map<string, { region: LitRegion; count: number }>()
    for (const point of points.value) {
      // 板块多边形是 GCJ02(DataV),足迹点存的是 WGS84(点选/在线检索落图前已纠偏):
      // 判定前把点转进 GCJ,贴县界的点才不会判错区
      const [gLng, gLat] = wgsToGcj(point.longitude, point.latitude)
      const region = regionOf(gLng, gLat, bundle)
      if (!region) continue
      const entry = lit.get(region.id)
      if (entry) entry.count += 1
      else lit.set(region.id, { region, count: 1 })
    }
    litById.value = lit
  } catch (error) {
    // 板块数据拉不到不算页面失败:足迹模式还在,只提示点亮暂不可用
    ui.error(toMessage(error))
  } finally {
    litLoading.value = false
  }
}

/* ---------------------------------------------------------------------------
 *  Leaflet · 足迹模式
 * ------------------------------------------------------------------------- */

/**
 * setupMap 动态 import('leaflet') 的模块缓存。
 * 后续图层装配(标记/板块)都是同步函数,直接吃这份缓存,
 * 避免每个装配函数都 async 化只为等一次 import。
 */
let leafletRef: typeof import('leaflet') | null = null

function leafletModule(): typeof import('leaflet') {
  if (!leafletRef) throw new Error('Leaflet 尚未加载,装配函数只能在 setupMap 之后调用')
  return leafletRef
}

/** loadLitRegions 拉回来的板块数据,影子层绘制直接用,避免再 await 一次 */
let bundleCache: Awaited<ReturnType<typeof loadLitRegions>> | null = null

/** 圆形照片标记的 HTML。图片加载失败时退回一个渐变圆点,不留破图。 */
function markerHtml(point: MapPointVO): string {
  const fallback = gradientFor(point.id)
  const inner = point.coverUrl
    ? `<img src="${point.coverUrl}" alt="" loading="lazy" onerror="this.style.display='none'" />`
    : ''
  return `
    <div class="fp-marker" style="background:${fallback}">
      ${inner}
    </div>
  `
}

function buildMarkers(): void {
  if (!map) return
  const L = leafletModule()
  if (!footprintLayer) footprintLayer = L.layerGroup()
  // 整层重画:buildMarkers 会被足迹数据与模式切换多次触发,不清空会叠上双份标记
  footprintLayer.clearLayers()

  // 先画路线,再画点 —— 后加的图层在上面,点不该被线压住
  for (const footprint of footprints.value) {
    if (footprint.points.length < 2) continue
    const path: [number, number][] = footprint.points.map((point) => [
      point.latitude,
      point.longitude,
    ])
    L.polyline(path, {
      color: '#007aff',
      weight: 3,
      opacity: 0.55,
      dashArray: '1 8',
      lineCap: 'round',
    })
      .addTo(footprintLayer)
  }

  for (const point of points.value) {
    const icon = L.divIcon({
      className: 'fp-marker-wrap',
      html: markerHtml(point),
      iconSize: [46, 46],
      iconAnchor: [23, 23],
    })

    L.marker([point.latitude, point.longitude], { icon, riseOnHover: true })
      .addTo(footprintLayer)
      .on('click', () => {
        selected.value = point
      })
  }
}

/** 足迹模式的取景:一个点定一级,多个点包边;没有点回中国视角 */
function fitFootprints(): void {
  if (!map) return
  if (points.value.length === 1) {
    map.setView([points.value[0].latitude, points.value[0].longitude], 11)
  } else if (points.value.length > 1) {
    map.fitBounds(
      points.value.map((point) => [point.latitude, point.longitude] as [number, number]),
      { padding: [48, 48], maxZoom: 12 },
    )
  } else {
    map.setView([35.0, 105.0], 3)
  }
}

/* ---------------------------------------------------------------------------
 *  Leaflet · 点亮模式
 * ------------------------------------------------------------------------- */

/**
 * 点亮层整层重画。数据或模式变了就重新拼一面(blocks by id),
 * 板块是纯静态多边形,这里的 map 操作按**点亮状态**上样式,不看 Leaflet 的对象复用。
 */
function buildLitLayer(): void {
  if (!map || !litLayer) return
  const L = leafletModule()
  litLayer.clearLayers()

  for (const { region } of litById.value.values()) {
    L.geoJSON(region.geometry, {
      // 面有细描边:点亮块的轮廓直接咬合在影子层的县界线里,像拼图块嵌进去
      style: {
        color: '#007aff',
        weight: 1,
        opacity: 0.55,
        fillColor: '#007aff',
        fillOpacity: 0.42,
      },
    })
      .bindTooltip(region.name, { direction: 'top', offset: [0, -4] })
      .on('click', () => {
        selectedRegion.value = litById.value.get(region.id) ?? { region, count: 1 }
      })
      .addTo(litLayer)
  }
}

/** 点亮模式的取景:包住全部点亮板块,聚焦用户走过的范围 */
function fitLit(): void {
  if (!map) return
  const boundsList = [...litById.value.values()].map((entry) => geometryBounds(entry.region.geometry))
  const valid = boundsList.filter((b): b is [[number, number], [number, number]] => b != null)
  if (valid.length === 0) {
    map.setView([35.0, 105.0], 3)
    return
  }
  if (valid.length === 1) {
    map.fitBounds(valid[0], { padding: [40, 40], maxZoom: 8 })
    return
  }
  const merged: [[number, number], [number, number]] = [
    [Infinity, Infinity],
    [-Infinity, -Infinity],
  ]
  for (const [[minLat, minLng], [maxLat, maxLng]] of valid) {
    if (minLat < merged[0][0]) merged[0][0] = minLat
    if (minLng < merged[0][1]) merged[0][1] = minLng
    if (maxLat > merged[1][0]) merged[1][0] = maxLat
    if (maxLng > merged[1][1]) merged[1][1] = maxLng
  }
  map.fitBounds(merged, { padding: [48, 48], maxZoom: 7 })
}

/** 未点亮板块的影子层:把整幅地图罩一层淡淡的省/国轮廓,"拼图"的空格 */
function buildShadowLayer(): void {
  if (!map || !shadowLayer) return
  const L = leafletModule()
  shadowLayer.clearLayers()
  const bundle = bundleCache ?? { cities: [], countries: [] }
  const litNow = litById.value
  // 未点亮的面:一层几乎透明的灰,勾出拼图底板,让"没来的地方"与"海"区分开。
  // 已点亮的面**跳过**,点亮层会给同一几何上色 —— 两层各画一遍既浪费又叠深。
  const addShadow = (region: LitRegion) => {
    if (litNow.has(region.id)) return
    L.geoJSON(region.geometry, {
      interactive: false,
      style: {
        color: '#8e8e93',
        weight: 0.8,
        opacity: 0.35,
        fillColor: '#8e8e93',
        fillOpacity: 0.1,
      },
    }).addTo(shadowLayer!)
  }
  for (const region of bundle.cities) addShadow(region)
  // 中国的面(内地市县块 + 港澳台整域块)已由 cities 提供,国家面里跳过
  for (const region of bundle.countries) {
    if (region.id === 'CHN') continue
    addShadow(region)
  }
}

/** 点亮/影子两层只建一次;几何挂进图层后由 fitLit 决定取景 */
function ensureLitLayers(): void {
  const L = leafletModule()
  litLayer ??= L.layerGroup()
  shadowLayer ??= L.layerGroup()
}

/* ---------------------------------------------------------------------------
 *  模式切换
 * ------------------------------------------------------------------------- */

/** 模式切换时攒下来的定向飞行:applyMode 装配完镜头后消费,见 applyMode 内注释 */
let pendingFly: { center: [number, number]; zoom: number } | null = null

/**
 * 按当前模式装配图层。
 *
 * 时序:先加层、后取景(fitLit 放在 addTo 之后)。
 * 反过来曾实测吃到 Leaflet 的一个坑:fitBounds 的镜头动画还在进行时
 * 才加进来的 SVG 矢量会被重投影到半路,部分 path 停在 d="M0 0",
 * 要等下一次重绘才恢复 —— 板块"点了不亮"正是这么来的。
 */
async function applyMode(): Promise<void> {
  if (!map) return

  selected.value = null
  selectedRegion.value = null

  if (mode.value === 'footprint') {
    // 整层退出:点亮的影子与亮块一起撤,照片标记回来
    shadowLayer?.removeFrom(map)
    litLayer?.removeFrom(map)
    buildMarkers()
    footprintLayer?.addTo(map)
    // 导演没指定镜头时才自己取景:「去看看」会先切模式再飞到板块中心,
    // 那次 fitFootprints 若照常执行,会把这次定向飞行整个顶掉,
    // 观感就是"没飞". 所以用 pendingFly 协调两者。
    if (pendingFly) {
      map.flyTo(pendingFly.center, pendingFly.zoom, { duration: 0.8 })
      pendingFly = null
    } else {
      fitFootprints()
    }
    return
  }

  footprintLayer?.removeFrom(map)
  await computeLit()
  if (litById.value.size === 0 && litLoading.value) {
    // 板块数据(约 1.4MB)还在拉:面板在转圈,层等 computeLit 完成后的重放再画
    return
  }
  ensureLitLayers()
  buildShadowLayer()
  buildLitLayer()
  shadowLayer?.addTo(map)
  litLayer?.addTo(map)
  fitLit()
}

/* ---------------------------------------------------------------------------
 *  Leaflet · 底图装配
 * ------------------------------------------------------------------------- */

async function setupMap(): Promise<void> {
  if (!mapEl.value || map) return

  const L = await import('leaflet')
  leafletRef = L
  // CSS 也走动态 import:静态 import 会让这份样式进首屏包
  await import('leaflet/dist/leaflet.css')

  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: true,
    // 世界地图的副本在低缩放级别会重复,限一下让 OSM 版权信息始终可见
    maxBoundsViscosity: 0.6,
    worldCopyJump: true,
  }).setView([35.0, 105.0], 3)

  // 瓦片源最初写死 OSM 主站,但该域名在很多网络环境连不通(实测连接被拒/超时),
  // 地图会变成一片空灰底。候选源逐一实测后的取舍:
  //   cartocdn  —— 现在要 API key,瓦片全是 "API KEY REQUIRED" 占位图,不能用;
  //   ArcGIS    —— 中国区域高缩放级别回 "Map data not yet available",不能用;
  //   高德       —— 内容可用但坐标系是 GCJ02,点位会整体偏移约 500 米,不能用;
  //   openstreetmap.de —— OSM 德国镜像(FOSSGIS 运营,OSM 官方认可的基础设施),
  //   数据与坐标同 OSM 主站,中国区域细节齐全。已改用它,但本环境实测单张瓦片
  //   仍要 1.0~1.8s(HTTP/1.1,四子域),放大一级 12~21 张新瓦片,灰块还是会浮现。
  //
  // 于是把瓦片收进后端代理(TileService):前端不再直连镜像,统一走 /tiles ——
  // 后端磁盘缓存命中即毫秒级返回,未命中才回源;同一张瓦片整个部署只回源一次,
  // 换设备、换浏览器都吃这份热缓存。上游与缓存目录的配置在 application.yml 的
  // panel.tiles 段;开发期由 Vite 把 /tiles 转给 8082,生产环境交给 nginx 反代到 diary-server。
  //
  // 瓦片层参数保留(上游慢的处境没变,只是慢的那一跳从浏览器挪到了服务器,
  // 被缓存摊平):keepBuffer 4 垫底、动画中就拉新瓦片、crossOrigin 允许读像素。
  const tileLayer = L.tileLayer('/tiles/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
    keepBuffer: 4,
    updateWhenIdle: false,
    updateWhenZooming: true,
    crossOrigin: true,
  }).addTo(map)

  // 上游(tile.openstreetmap.de)偶发超时,失败的瓦片会停留成灰块。
  // 重试一次:延迟几秒重置 img.src 触发重新请求 —— 那 tile 的回源早就结束,
  // TileService 磁盘缓存里大概率已落盘,重试即毫秒级命中。
  tileLayer.on('tileerror', (event) => {
    const img = event.tile as HTMLImageElement
    const coords = event.coords as { z: number; x: number; y: number }
    const original = `/tiles/${coords.z}/${coords.x}/${coords.y}.png`
    // 只重试一次:retryWeaks 记住重试过的,避免上游持续故障时无限打转
    if (img.dataset.retry || img.src.endsWith(original)) return
    img.dataset.retry = '1'
    setTimeout(() => {
      img.src = original
    }, 3000)
  })

  // 缩放控件挪到右下:左上角留给导航栏,右上角有定位按钮
  L.control.zoom({ position: 'bottomright' }).addTo(map)

  // ?view=lit 直达点亮模式(else 分支今天没有,仅留入口对称)
  if (route.query.view === 'lit') {
    mode.value = 'lit'
  }
  await applyMode()
}

/* ---------------------------------------------------------------------------
 *  聚焦:从日记详情"在地图上查看"过来时带 ?focus=id
 * ------------------------------------------------------------------------- */

async function focusEntry(id: number): Promise<void> {
  // 点亮模式下不响应 focus:那个入口表达的是"单条日记的位置",
  // 切回足迹模式才有意义
  if (mode.value === 'lit') return
  const point = points.value.find((item) => item.id === id)
  if (!point || !map) return

  selected.value = point
  map.flyTo([point.latitude, point.longitude], 13, { duration: 0.9 })
  // 不弹气泡:Leaflet 的气泡样式与这套 iOS 观感冲突,选中态交给底部卡片表达
}

/* ---------------------------------------------------------------------------
 *  生命周期
 * ------------------------------------------------------------------------- */

onMounted(async () => {
  await load()
  await setupMap()

  // 从日记详情「在地图上查看」过来时带 ?focus=id:
  // 必须等地图建好再飞,否则 flyTo 没有可作用的目标。
  const id = Number(route.query.focus)
  if (Number.isFinite(id) && id > 0) {
    await focusEntry(id)
  }
})

onBeforeUnmount(() => {
  footprintLayer = null
  litLayer = null
  shadowLayer = null
  // Leaflet 会在 document 上挂 resize/scroll 监听并维护自己的尺寸缓存,
  // 不 remove 就会随每次进出地图页泄漏一份。
  map?.remove()
  map = null
})

/** 足迹数据到位后补画标记。首次加载时 setupMap 与 load 是并行的,顺序不定。 */
watch(points, () => {
  if (mode.value === 'footprint') void buildMarkers()
})

/** 点亮判定的输入是足迹点:点位一变,点亮集合重算、层重画 */
watch(points, async () => {
  if (mode.value !== 'lit') return
  litById.value = new Map()
  await computeLit()
  await applyMode()
})

/** 在地图页内换一个 ?focus= 时(比如从详情返回又点进另一篇)重新定位 */
watch(
  () => route.query.focus,
  (value) => {
    const id = Number(value)
    if (!Number.isFinite(id) || id <= 0 || !map) return
    void focusEntry(id)
  },
)

/* ---------------------------------------------------------------------------
 *  面板交互
 * ------------------------------------------------------------------------- */

/**
 * 切换「足迹 / 点亮」:整层换血,两套图层互不残留。
 * 显式驱动装配,不走 watch(mode):flyToRegion 切模式时
 * 需要自己控制装配时机(先攒 pendingFly 再 await applyMode),
 * watch 与它并发双跑会把定向飞行顶掉。
 */
function switchMode(next: string): void {
  if (next !== mode.value) {
    mode.value = next as MapMode
    void applyMode()
  }
}

function openEntry(id: number): void {
  void router.push({ name: 'entry-detail', params: { id } })
}

/** 点亮面板里"去这里看看":跳回足迹模式并飞到该板块的中心 */
async function flyToRegion(region: LitRegion): Promise<void> {
  const center = region.centroid
  if (center) {
    pendingFly = { center: [center[1], center[0]], zoom: 6 }
    mode.value = 'footprint'
    await applyMode()
  }
}

function resetView(): void {
  selected.value = null
  selectedRegion.value = null
  if (mode.value === 'footprint') {
    fitFootprints()
  } else {
    fitLit()
  }
}
</script>

<template>
  <div class="map-page">
    <NavBar title="足迹">
      <template #subtitle>
        <SegmentedControl
          class="map-page__mode"
          :model-value="mode"
          :options="[
            { value: 'footprint', label: '足迹' },
            { value: 'lit', label: '点亮' },
          ]"
          @update:model-value="switchMode"
        />
      </template>
      <template #right>
        <button
          class="map-page__action"
          type="button"
          title="回到合适的取景"
          @click="resetView"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="8" stroke="currentColor" stroke-width="1.9" />
            <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
            <circle cx="12" cy="12" r="2.6" fill="currentColor" />
          </svg>
          看全部
        </button>
      </template>
    </NavBar>

    <div class="map-page__canvas">
      <div ref="mapEl" class="map-page__map" />

      <!-- 加载/失败提示浮在地图上,而不是替换它 —— 地图本身没数据也能看 -->
      <div v-if="loading" class="map-page__hint">正在载入足迹...</div>
      <div v-else-if="mode === 'lit' && litLoading" class="map-page__hint">正在点亮...</div>
      <p v-else-if="failed" class="map-page__hint map-page__hint--error">
        足迹没读出来,刷新重试
      </p>

      <!-- 一个点都没有时给引导,而不是给一张空地图 -->
      <div v-else-if="!hasPoints" class="map-page__empty">
        <EmptyState
          icon="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
          title="地图上还没有你的足迹"
          description="写日记时填上地点(带经纬度),走过的地方就会在这里连成一条路线"
        />
      </div>
    </div>

    <!-- 底部浮动毛玻璃卡片:统计,或选中后的详情 -->
    <div class="floating">
      <transition name="rise" mode="out-in">
        <!-- 足迹模式:选中某个点 -->
        <button
          v-if="mode === 'footprint' && selected"
          :key="`point-${selected.id}`"
          class="floating__point"
          type="button"
          @click="openEntry(selected.id)"
        >
          <div class="floating__thumb" :style="selected.coverUrl ? undefined : { background: gradientFor(selected.id) }">
            <img v-if="selected.coverUrl" :src="selected.coverUrl" alt="" />
          </div>
          <div class="floating__point-text">
            <span class="floating__point-title ellipsis">{{ selected.title }}</span>
            <span class="floating__point-meta ellipsis">
              {{ formatDateCN(selected.entryDate) }}
              <template v-if="selected.locationName"> · {{ selected.locationName }}</template>
            </span>
          </div>
          <span class="floating__go">查看</span>
        </button>

        <!-- 足迹模式:统计条 -->
        <div
          v-else-if="mode === 'footprint'"
          key="footprint-stats"
          class="floating__stats"
        >
          <div class="floating__stat">
            <strong>{{ points.length }}</strong>
            <span>个足迹</span>
          </div>
          <span class="floating__divider" />
          <div class="floating__stat">
            <strong>{{ cityCount }}</strong>
            <span>个地方</span>
          </div>
          <span class="floating__divider" />
          <div class="floating__stat">
            <strong>{{ tripCount }}</strong>
            <span>段行程</span>
          </div>
        </div>

        <!-- 点亮模式:没点亮任何板块时提示 -->
        <div v-else-if="litCount === 0" key="lit-empty" class="floating__stats">
          <p class="floating__lit-empty">还没有可以去点亮的足迹。写日记时填上地点,去过的地方就会亮起来。</p>
        </div>

        <!-- 点亮模式:选中某个板块 -->
        <div
          v-else-if="selectedRegion"
          :key="`region-${selectedRegion.region.id}`"
          class="floating__region"
        >
          <div class="floating__region-main">
            <span class="floating__point-title">{{ selectedRegion.region.name }}</span>
            <span class="floating__point-meta">{{ selectedRegion.count }} 篇日记写过这里</span>
          </div>
          <button class="floating__link" type="button" @click="flyToRegion(selectedRegion.region)">
            去看看
          </button>
        </div>

        <!-- 点亮模式:点亮统计条 -->
        <div v-else key="lit-stats" class="floating__stats">
          <div class="floating__stat">
            <strong>{{ litCount }}</strong>
            <span>块已点亮</span>
          </div>
          <span class="floating__divider" />
          <div class="floating__stat">
            <strong>{{ points.length }}</strong>
            <span>个足迹</span>
          </div>
          <span class="floating__divider" />
          <div class="floating__stat">
            <strong>{{ tripCount }}</strong>
            <span>段行程</span>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
/*
  地图页是唯一一个"内容该铺满"的页面:地图铺得越宽越好用。
  所以 .map-page 保持默认的 --page-max(页面标题跟着内容栏居中),
  而下面的地图画布自己撑满整宽 —— 这是"标题在栏内、地图通栏"的常见排法。
*/
.map-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-grouped);
}

/* 分段控件(足迹/点亮)放进标题行:比放到地图上多一层毛玻璃更轻 */
.map-page__mode {
  width: 220px;
  max-width: 100%;
}

/* 页面标题区的操作:图标 + 文字,桌面端有位置放得下文字 */
.map-page__action {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-field);
  background: var(--bg-fill);
  color: var(--ios-blue);
  font-size: var(--text-subhead);
  font-weight: 600;
  transition: background var(--dur-fast) var(--ease-standard);
}

.map-page__action:hover {
  background: var(--bg-hover);
}

/*
  地图画布。
  高度 = 视口剩下的部分,但给一个下限:窗口矮的时候地图不能缩成一条缝。
  正文页靠文档流自然变高,地图不一样 —— Leaflet 需要一个明确的高度才能算出
  自己的视口尺寸,写 auto 它会渲染成 0 高。
*/
.map-page__canvas {
  position: relative;
  flex: 1;
  min-height: 520px;
  overflow: hidden;
}

/*
  地图容器用绝对定位铺满画布,**不能**写 height: 100%。
  画布的高度来自 flex(它是个 flex:1 的弹性项),而不是一个写死的高度;
  父级的 min-height 不构成"确定高度",所以子元素的百分比高度会算成 0 ——
  表现就是地图一片空白、标记全在但一个都看不见。
  absolute + inset:0 直接取父级的 padding box,不受百分比解析规则影响。

  z-index: 0 是为了给 Leaflet 建一个层叠上下文,把它的 z-index 关在里面。
  Leaflet 内部是一整套 400~800 的层级(.leaflet-map-pane 400、标记 600、
  弹层 700、控件 800),而这一层如果 z-index 是 auto,这些数字就会跑到
  根层叠上下文里参与比较 —— 结果是导航栏(z-index 50)的下拉菜单、
  右上角的 toast,只要落进地图范围就会被地图盖住,露出半截。
  在容器上建层之后,Leaflet 无论内部多高,对外都只是"一层 0",
  导航栏、浮层、弹窗照常压在它上面。
*/
.map-page__map {
  position: absolute;
  inset: 0;
  z-index: 0;
  /* Leaflet 自己的背景是灰白格,先把底色铺上,加载中也不刺眼 */
  background: var(--bg-fill);
}

/*
  浮在地图上的提示与空态。
  必须显式给 z-index:上面那个容器已经建了层,它内部的 Leaflet 面板
  仍然带 400 的层级;这里靠 DOM 顺序或 auto 都可能被压在下面。
*/
.map-page__hint {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 1;
  transform: translate(-50%, -50%);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-full);
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  font-size: var(--text-footnote);
  color: var(--label-2);
  pointer-events: none;
}

.map-page__hint--error {
  color: var(--ios-red);
}

.map-page__empty {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--gutter);
  background: var(--bg-grouped);
}

/*
  浮动卡片停在左下角。
  左边界用 max() 让它在宽屏上跟内容栏对齐(地图通栏,卡片不必通栏),
  窄窗口退回普通的 --gutter 内缩。
*/
.floating {
  position: absolute;
  left: max(var(--gutter), calc((100% - var(--content-max)) / 2 + var(--gutter)));
  bottom: var(--space-6);
  width: min(380px, calc(100% - var(--gutter) * 2));
  /*
    必须盖过 Leaflet 自己的图层面板。
    Leaflet 把地图内容放在 .leaflet-map-pane 里,那个元素带 z-index: 400
    (控件更是到 800),所以这里小于 400 的话卡片会被地图整个盖住 ——
    数据在、DOM 也在,就是看不见。500 足够高过地图本体,
    又低于 Leaflet 的控件层,不会去跟缩放按钮抢层级。
  */
  z-index: 500;
}

.floating__stats {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-card);
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  box-shadow: var(--shadow-card);
}

.floating__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.floating__stat strong {
  font-family: var(--font-num);
  font-size: var(--text-title3);
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.4px;
}

.floating__stat span {
  font-size: var(--text-caption2);
  color: var(--label-2);
}

.floating__lit-empty {
  padding: var(--space-2) 0;
  font-size: var(--text-footnote);
  line-height: 1.6;
  color: var(--label-2);
  text-align: center;
}

.floating__divider {
  width: 0.5px;
  height: 26px;
  background: var(--separator);
}

.floating__point,
.floating__region {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-card);
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  box-shadow: var(--shadow-card);
  text-align: left;
}

.floating__point {
  padding: var(--space-2);
}

.floating__point:hover {
  opacity: 0.85;
}

.floating__thumb {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg-fill);
}

.floating__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.floating__point-text,
.floating__region-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.floating__point-title {
  font-size: var(--text-body);
  font-weight: 600;
}

.floating__point-meta {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.floating__go,
.floating__link {
  flex-shrink: 0;
  font-size: var(--text-footnote);
  color: var(--ios-blue);
}

.floating__link {
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-field);
  transition: background var(--dur-fast) var(--ease-standard);
}

.floating__link:hover {
  background: var(--bg-hover);
}
</style>

<!--
  标记的样式不能写在 scoped 里:divIcon 的 DOM 是 Leaflet 在运行时
  用 innerHTML 插进去的,拿不到组件的作用域属性,scoped 选择器选不中它。
-->
<style>
.fp-marker-wrap {
  /* Leaflet 默认给 divIcon 一个白方块,这里必须清掉 */
  background: transparent !important;
  border: none !important;
}

.fp-marker {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  overflow: hidden;
  /* 白边 + 阴影:这是"相册里的照片"而不是"地图图钉"的关键 */
  border: 2.5px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.32);
  transition: transform 0.22s cubic-bezier(0.32, 0.72, 0, 1);
}

.fp-marker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 按压反馈:拖动地图时不会触发,只有真的点在标记上才缩 */
.leaflet-marker-icon:active .fp-marker {
  transform: scale(1.14);
}

/* OSM 版权信息:白底黑字太扎眼,压成半透明小字 */
.map-page .leaflet-control-attribution,
.leaflet-control-attribution {
  background: rgba(255, 255, 255, 0.72) !important;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  font-size: 10px !important;
  padding: 1px 6px !important;
  border-radius: 6px 0 0 0;
}

@media (prefers-color-scheme: dark) {
  .leaflet-control-attribution {
    background: rgba(28, 28, 30, 0.72) !important;
    color: var(--label-2);
  }

  .leaflet-control-attribution a {
    color: var(--ios-blue);
  }
}

/* Leaflet 的默认交互控件在深色下也需要一点适配 */
.leaflet-bar a {
  background: var(--bg-elevated);
  color: var(--label);
  border-bottom-color: var(--separator);
}

.leaflet-bar a:hover {
  background: var(--bg-hover);
}
</style>
