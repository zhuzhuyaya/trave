<script setup lang="ts">
/**
 * 地图选点:在一张小地图上点一下,拾取那个位置的坐标和地名。
 *
 * 为什么基本不依赖地理编码服务:地名不是反查出来的,
 * 是用足迹地图已板的板块数据(litmap)做"点在多边形内"推出的 ——
 * 点落在哪个区县/国家板块,就抄板块自带的父子链拼文案。
 * 这意味着海外与港澳台之外的中国区域精确到区县,海外到国家。
 * 点在海面等无板块处给不出名字,坐标仍然有效,地名留空交给编辑器文案兜底。
 *
 * 搜索定位:同一份板块数据建了个名字索引,搜「龙岩」「京都」能直接飞过去;
 * 再叠内置地点表(PLACES,知名景点级)与在线检索(/api/geo/search,后端代理
 * OSM Nominatim,内有磁盘缓存与 1 req/s 限速,搜「武功山」这类表外景区用)
 * 三路结果合并去重。选搜索结果与手点一样:打点 + 识别地名,之后可再微调。
 *
 * 与 LocationPicker(内置地点表)并列作为第二个入口:
 * 表里搜不到、坐标又懒得手填的地点,在地图上点一下就行。
 *
 * Leaflet 同样按需动态 import(静态引入会把它打进首屏包;
 * 组件在 Sheet 里,Sheet 打开才挂地图 —— open 的 watch 里做)。
 */
import { computed, nextTick, ref, watch } from 'vue'
import type { Map as LeafletMap, Marker } from 'leaflet'
import Sheet from '@/components/ios/Sheet.vue'
import { get } from '@/api/http'
import { gcjToWgs, wgsToGcj } from '@/utils/coord'
import { loadLitRegions, placeAt, searchRegions } from '@/utils/litmap'
import { PLACES } from '@/utils/places'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 已选坐标(编辑已有日记时带进来),打开地图会先飞到这里 */
    latitude?: number | null
    longitude?: number | null
  }>(),
  { latitude: null, longitude: null },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  pick: [payload: { name: string; region: string; latitude: number; longitude: number }]
}>()

const mapEl = ref<HTMLElement | null>(null)
const picking = ref(false) // 板块数据没到位前点击不生效,给个提示
const pickedName = ref<string | null>(null)
const pickedRegion = ref<string | null>(null)
const pickedLat = ref<number | null>(null)
const pickedLng = ref<number | null>(null)

/** 搜索:keyword 非空才出结果面板,选中后清空输入收回面板 */
const keyword = ref('')
const searching = ref(false) // 板块索引要等数据到位;首次搜索可能转一下
interface SearchHit {
  key: string
  title: string
  subtitle: string
  /** 点它之后镜头怎么飞 */
  fly: 'point' | 'bounds'
  centroid?: [number, number]
  bounds?: [[number, number], [number, number]]
  /** 选中后直接认领的姓名(地点表条目用,板块条目让 placeAt 再推一遍) */
  name?: string
}

/** Nominatim 的 boundingbox:[南, 北, 西, 东](字符串),转成 Leaflet bounds 顺序 */
function geoBoundsOf(box: string[] | undefined): [[number, number], [number, number]] | undefined {
  if (!box || box.length !== 4) return undefined
  const s = Number(box[0])
  const n = Number(box[1])
  const w = Number(box[2])
  const e = Number(box[3])
  if ([s, n, w, e].some(Number.isNaN)) return undefined
  return [
    [s, w],
    [n, e],
  ]
}
const searchHits = ref<SearchHit[]>([])

const hasQuery = computed(() => keyword.value.trim().length > 0)

let map: LeafletMap | null = null
let marker: Marker | null = null

function close(): void {
  emit('update:modelValue', false)
}

/** 放一个点位标记并登记为当前选中;地图选点与搜索结果共用 */
function dropPin(L: typeof import('leaflet'), lat: number, lng: number): void {
  pickedLat.value = lat
  pickedLng.value = lng
  if (!marker) {
    marker = L.marker([lat, lng], {
      icon: L.divIcon({
        className: 'map-picker__pin',
        html: '<div class="map-picker__pin-dot"></div>',
        iconSize: [18, 18],
        iconAnchor: [9, 9],
      }),
    }).addTo(map!)
  } else {
    marker.setLatLng([lat, lng])
  }
}

/** 打开一次就搭一次地图:Sheet 关闭时视图销毁,实例不留 */
async function setupMap(): Promise<void> {
  if (!mapEl.value || map) return
  const L = await import('leaflet')
  await import('leaflet/dist/leaflet.css')

  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: false,
    worldCopyJump: true,
  })

  const tileLayer = L.tileLayer('/tiles/{z}/{x}/{y}.png', {
    maxZoom: 19,
    keepBuffer: 4,
    crossOrigin: true,
  }).addTo(map)

  // 上游偶发超时留下的灰块:延迟几秒重置 src 重试一次(此刻回源大多已结束,
  // TileService 磁盘缓存里已落盘,重试即毫秒级);只重试一跳,不无限打转
  tileLayer.on('tileerror', (event) => {
    const img = event.tile as HTMLImageElement
    const coords = event.coords as { z: number; x: number; y: number }
    if (img.dataset.retry) return
    img.dataset.retry = '1'
    setTimeout(() => {
      img.src = `/tiles/${coords.z}/${coords.x}/${coords.y}.png`
    }, 3000)
  })

  L.control.zoom({ position: 'bottomright' }).addTo(map)

  map.on('click', (event) => {
    const { lat, lng } = event.latlng
    dropPin(L, lat, lng)
    void resolveName(lat, lng)
  })

  // 已有坐标(编辑场景):先飞过去并预标记,不是空白起步
  if (props.latitude != null && props.longitude != null) {
    map.setView([props.latitude, props.longitude], 9)
    L.marker([props.latitude, props.longitude], {
      icon: L.divIcon({
        className: 'map-picker__pin',
        html: '<div class="map-picker__pin-dot"></div>',
        iconSize: [18, 18],
        iconAnchor: [9, 9],
      }),
    }).addTo(map)
    pickedLat.value = props.latitude
    pickedLng.value = props.longitude
    void resolveName(props.latitude, props.longitude)
    marker = null // 这个标记不在 marker 引用里,首个点击会另建,不冲突
  } else {
    map.setView([35.0, 105.0], 3)
  }
}

/**
 * 用板块数据把坐标翻译成地名。数据拉不到时点位仍有效,只是没名字。
 *
 * 板块多边形是 GCJ02(DataV/高德系),进来的点是 WGS84(瓦片点选与
 * 在线检索都是 WGS84):先纠偏成 GCJ 再做多边形包含,否则贴边点会判错区。
 */
async function resolveName(lat: number, lng: number): Promise<void> {
  picking.value = true
  try {
    const bundle = await loadLitRegions()
    const [gLng, gLat] = wgsToGcj(lng, lat)
    const hit = placeAt(gLng, gLat, bundle)
    pickedName.value = hit?.name ?? null
    pickedRegion.value = hit?.region ?? null
  } catch {
    pickedName.value = null
    pickedRegion.value = null
  } finally {
    picking.value = false
  }
}

/**
 * 三路搜索合一:内置地点表(景区级)+ 板块名(区县/国家级)+ 在线检索(兜底表外的)。
 * 本地两路同步出,秒回;在线一路 300ms 去抖后追加,失败静默 ——
 * 本地结果永远可用,在线只是增量,不能拖垮搜索框。
 * (在线这路走后端代理 /api/geo/search:限速与自报 UA 只有服务端能保证,
 * 顺带一层磁盘缓存,同一地名整个部署只回源一次。)
 */
let searchSeq = 0 // 只认最后一次输入的结果:防止慢响应回来盖住新查询的答案
let debounceTimer: ReturnType<typeof setTimeout> | null = null
/** 当前关键词批次的已用名:本地两路在 runSearch 里清空重建,在线批次续着去重 */
const seen = new Set<string>()

interface GeoHitVO {
  name: string
  region: string
  latitude: number
  longitude: number
  /** Nominatim boundingbox 原序 [南, 北, 西, 东](字符串);后端 bbox 字段透传 */
  bbox?: string[]
}

async function runSearch(): Promise<void> {
  const seq = ++searchSeq
  const query = keyword.value.trim()
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
  if (!query) {
    searchHits.value = []
    return
  }
  searching.value = true
  seen.clear()
  try {
    const hits: SearchHit[] = []
    for (const place of PLACES) {
      if (place.name.includes(query) || place.region.includes(query)) {
        seen.add(place.name)
        // gcjToWgs 回 [经度, 纬度](GeoJSON 习惯),SearchHit.centroid 是 [纬度, 经度]
        const [wLng, wLat] = gcjToWgs(place.longitude, place.latitude)
        hits.push({
          key: `place:${place.name}`,
          title: place.name,
          subtitle: place.region,
          fly: 'point',
          centroid: [wLat, wLng],
          name: place.name,
        })
      }
    }
    const bundle = await loadLitRegions()
    for (const hit of searchRegions(query, bundle)) {
      if (seen.has(hit.name)) continue
      seen.add(hit.name)
      // 板块质心是 GCJ02(DataV),转 WGS84 后蓝圈才落在真位置;
      // bounds 由几何 bbox 来,同为 GCJ —— 转两个对角点即可
      // (gcjToWgs 回 [经度, 纬度],所以两处都先拿经度)
      const [cLng, cLat] = gcjToWgs(hit.centroid[1], hit.centroid[0])
      const [sLng, sLat] = gcjToWgs(hit.bounds[0][1], hit.bounds[0][0])
      const [nLng, nLat] = gcjToWgs(hit.bounds[1][1], hit.bounds[1][0])
      hits.push({
        key: `region:${hit.name}:${hit.region}`,
        title: hit.name,
        subtitle: hit.region,
        fly: 'bounds',
        centroid: [cLat, cLng],
        bounds: [
          [sLat, sLng],
          [nLat, nLng],
        ],
      })
    }
    // seq 不一致说明用户又输入了,这批过期的结果不能上屏
    if (seq !== searchSeq) return
    searchHits.value = hits.slice(0, 8)

    // 在线路:本地已有命中也查 —— 传入当前视野让上游把重名往视野里排;
    // 去抖 300ms,打字连击时不烧代理的 1 req/s 配额
    debounceTimer = setTimeout(() => void searchOnline(query, seq), 300)
  } catch {
    if (seq === searchSeq) searchHits.value = []
  } finally {
    if (seq === searchSeq) searching.value = false
  }
}

/** 在线路:带当前视野请求在线检索,结果去重后追加在本地的后面。失败静默。 */
async function searchOnline(query: string, seq: number): Promise<void> {
  try {
    const params: Record<string, number | string> = { q: query }
    const b = map?.getBounds()
    if (b) {
      params.west = b.getWest()
      params.north = b.getNorth()
      params.east = b.getEast()
      params.south = b.getSouth()
    }
    const online = (await get<GeoHitVO[]>('/geo/search', params)) ?? []
    if (seq !== searchSeq || !online.length) return
    const extra: SearchHit[] = []
    for (const hit of online) {
      if (seen.has(hit.name)) continue
      seen.add(hit.name)
      const bounds = geoBoundsOf(hit.bbox)
      extra.push({
        key: `geo:${hit.name}:${hit.latitude},${hit.longitude}`,
        title: hit.name,
        subtitle: hit.region,
        // 有 bbox 的(景区面/行政区)取景飞行:缩放级别由 bbox 决定,像导航那样套住目标;
        // 纯点位(山顶/灯杆)没 bbox(或退化成一条线)才退回 setView
        fly: bounds ? 'bounds' : 'point',
        centroid: [hit.latitude, hit.longitude],
        bounds,
        // 名字与父链副标题都是现成的,不用再 placeAt 反推一遍
        name: hit.name,
      })
    }
    if (extra.length) {
      searchHits.value = [...searchHits.value, ...extra].slice(0, 10)
    }
  } catch {
    // 在线路失败:静默。本地两路的结果照常可用
  }
}

/** 选一条搜索结果:飞过去、打点、识别地名,和手点地图一致 */
async function pickHit(hit: SearchHit): Promise<void> {
  keyword.value = ''
  searchHits.value = []
  if (!map) return
  const L = await import('leaflet')
  if (hit.fly === 'bounds' && hit.bounds) {
    // maxZoom 13:景区级 bbox 很小,10 只够区县取景,景区会"看不清在哪";
    // 13 让小面也能铺满视窗,同时不至于深到看不见周边
    map.flyToBounds(hit.bounds, { padding: [24, 24], maxZoom: 13, duration: 0.6 })
  } else if (hit.centroid) {
    map.flyTo(hit.centroid, 10, { duration: 0.6 })
  }
  const lat = hit.centroid?.[0]
  const lng = hit.centroid?.[1]
  if (lat == null || lng == null) return
  dropPin(L, lat, lng)
  if (hit.name) {
    pickedName.value = hit.name
    pickedRegion.value = hit.subtitle
    picking.value = false
  } else {
    void resolveName(lat, lng)
  }
}

function confirm(): void {
  if (pickedLat.value == null || pickedLng.value == null) return
  emit('pick', {
    name: pickedName.value ?? '',
    region: pickedRegion.value ?? '',
    latitude: pickedLat.value,
    longitude: pickedLng.value,
  })
  close()
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      // 关闭即拆:地图实例与 marker 都随 DOM 一起走了,但 Leaflet 自己的
      // resize/scroll 监听挂在 document 上,不 remove 会泄漏。
      map?.remove()
      map = null
      marker = null
      pickedName.value = null
      pickedRegion.value = null
      pickedLat.value = props.latitude
      pickedLng.value = props.longitude
      if (debounceTimer) {
        clearTimeout(debounceTimer)
        debounceTimer = null
      }
      searchSeq += 1 // 作废还在路上的在线响应,别关了面板还往里塞结果
      keyword.value = ''
      searchHits.value = []
      return
    }
    await nextTick()
    await setupMap()
    // 容器从 display:none 变可见后 Leaflet 尺寸缓存不对,强制重算
    map?.invalidateSize()
  },
)

watch(keyword, () => void runSearch())
</script>

<template>
  <Sheet :model-value="modelValue" title="在地图上选点" @update:model-value="close">
    <div class="map-picker">
      <div class="map-picker__search-wrap">
        <div class="map-picker__search">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="2" />
            <path d="M16 16l4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          <input
            v-model="keyword"
            class="map-picker__input"
            type="search"
            autocomplete="off"
            placeholder="搜地名,如:龙岩 / 鼓浪屿 / 京都"
          />
        </div>

        <ul v-if="hasQuery" class="map-picker__results">
          <li v-if="searching" class="map-picker__result--muted">正在搜索...</li>
          <li v-else-if="!searchHits.length" class="map-picker__result--muted">
            没找到。可以直接在地图上点,或手动填坐标
          </li>
          <li v-for="hit in searchHits" v-else :key="hit.key">
            <button class="map-picker__result" type="button" @click="pickHit(hit)">
              <span class="map-picker__result-main">
                <span class="map-picker__result-title">{{ hit.title }}</span>
                <span class="map-picker__result-sub">{{ hit.subtitle }}</span>
              </span>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" class="map-picker__result-go">
                <path d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z" stroke="currentColor" stroke-width="1.8" />
                <circle cx="12" cy="10.5" r="2.2" fill="currentColor" />
              </svg>
            </button>
          </li>
        </ul>
      </div>

      <div ref="mapEl" class="map-picker__map" />

      <div class="map-picker__hint">
        <template v-if="picking">正在识别地名...</template>
        <template v-else-if="pickedLat == null">在地图上点一下,选那里的坐标和地名</template>
        <template v-else-if="pickedRegion">
          {{ `${pickedRegion} ${pickedLat!.toFixed(4)}, ${pickedLng!.toFixed(4)}` }}
        </template>
        <template v-else>
          <span class="map-picker__co">{{ `${pickedLat!.toFixed(4)}, ${pickedLng!.toFixed(4)}` }}</span>
          (这里识别不出区县名,只记坐标)
        </template>
      </div>

      <button
        class="btn btn--primary btn--block"
        type="button"
        :disabled="pickedLat == null"
        @click="confirm"
      >
        用这个地点
      </button>
    </div>
  </Sheet>
</template>

<style scoped>
.map-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* 搜索框 + 结果面板一起定位:面板要浮在地图上,不能把地图挤下去 */
.map-picker__search-wrap {
  position: relative;
  z-index: 20; /* 结果面板要浮到地图上面;能压住的前提是下面的地图自成层叠上下文(见 .map-picker__map) */
}

.map-picker__search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-cell);
  background: var(--bg-fill);
  color: var(--label-3);
}

.map-picker__input {
  flex: 1;
  min-width: 0;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--label);
  font-size: var(--text-body);
}

.map-picker__input::-webkit-search-cancel-button {
  display: none;
}

.map-picker__results {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 4px);
  z-index: 20;
  max-height: 240px;
  overflow-y: auto;
  border-radius: var(--radius-cell);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-float);
}

.map-picker__results li + li {
  border-top: 0.5px solid var(--separator);
}

.map-picker__result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-standard);
}

.map-picker__result:hover {
  background: var(--bg-hover);
}

.map-picker__result-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.map-picker__result-title {
  font-size: var(--text-body);
}

.map-picker__result-sub {
  font-size: var(--text-footnote);
  color: var(--label-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-picker__result-go {
  flex-shrink: 0;
  color: var(--ios-blue);
}

.map-picker__result--muted {
  padding: var(--space-3);
  font-size: var(--text-footnote);
  color: var(--label-3);
  text-align: center;
}

.map-picker__map {
  height: 320px;
  border-radius: var(--radius-cell);
  overflow: hidden;
  background: var(--bg-fill);
  /* 关键:自成层叠上下文,把 Leaflet 窗格的 z-index(瓦片 200 / 覆盖 400 /
     标记 600 / 控件 1000)关在地图内部 —— 否则它们逃逸到本组件的层级里,
     会盖过搜索结果面板的 z-index: 20。 */
  z-index: 0;
}

.map-picker__hint {
  font-size: var(--text-footnote);
  color: var(--label-2);
  text-align: center;
  min-height: 1.2em;
}

.map-picker__co {
  color: var(--label-3);
  font-variant-numeric: tabular-nums;
}
</style>

<style>
/* divIcon 走非 scoped:divIcon 的 html 不带 scoped 属性,样式只放全局 */
.map-picker__pin {
  background: none;
  border: none;
}

.map-picker__pin-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 122, 255, 0.25);
  border: 3px solid #007aff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}
</style>
