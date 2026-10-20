<script setup lang="ts">
/**
 * 足迹地图(Leaflet + OpenStreetMap)。
 *
 * Leaflet 是命令式的 DOM 库,和 Vue 的声明式模型天然隔一层。
 * 这一页的处理方式是:Vue 只管"数据"和"面板 UI",
 * 地图实例、标记、连线全部在 Leaflet 自己的世界里手动维护,
 * 两者只在 watch 到数据变化的那一个点交汇。
 * 硬要把 marker 做成 Vue 组件会同时丢掉两边的优势。
 *
 * 三个刻意的选择:
 *
 * 1. 标记用圆形照片而不是默认的蓝色水滴图钉。默认图钉一眼就是"网页地图",
 *    而圆形照片标记才像相册在回看足迹。为此全部走 L.divIcon,
 *    也顺带避开了 Vite 打包时 marker-icon.png 路径丢失的经典坑。
 *
 * 2. 按时间把点连成虚线路线。散落的点看不出"一趟旅行",连起来才有轨迹感;
 *    用虚线而不是实线,是因为两点之间并没有真的走过一条直线。
 *
 * 3. 地图按需异步加载。Leaflet 加底图 CSS 有一百多 KB,
 *    它在首屏用不到,不该拖慢时间线的打开速度。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Map as LeafletMap, LayerGroup, Marker } from 'leaflet'
import NavBar from '@/components/ios/NavBar.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { insightApi } from '@/api/insight'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { FootprintVO, MapPointVO } from '@/api/types'
import { formatDateCN } from '@/utils/time'
import { gradientFor } from '@/utils/misc'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const points = ref<MapPointVO[]>([])
const footprints = ref<FootprintVO[]>([])
const loading = ref(true)
const failed = ref(false)

/** 当前选中的足迹点,对应底部那张浮动卡片 */
const selected = ref<MapPointVO | null>(null)

const mapEl = ref<HTMLElement | null>(null)
let map: LeafletMap | null = null
let layers: LayerGroup | null = null
/** divIcon 的实例按点 id 存,重绘时先清空再建,不做增量更新 —— 点数很少,不值得 */
const markers = new Map<number, Marker>()

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

/* ---------------------------------------------------------------------------
 *  Leaflet
 * ------------------------------------------------------------------------- */

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

async function buildMarkers(): Promise<void> {
  if (!map || !layers) return

  const L = await import('leaflet')
  const group: LayerGroup = L.layerGroup()

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
      .addTo(group)
  }

  markers.clear()
  for (const point of points.value) {
    const icon = L.divIcon({
      className: 'fp-marker-wrap',
      html: markerHtml(point),
      iconSize: [46, 46],
      iconAnchor: [23, 23],
    })

    const marker = L.marker([point.latitude, point.longitude], { icon, riseOnHover: true })
      .addTo(group)
      .on('click', () => {
        selected.value = point
      })

    markers.set(point.id, marker)
  }

  layers.clearLayers()
  group.addTo(layers)

  if (points.value.length === 1) {
    map.setView([points.value[0].latitude, points.value[0].longitude], 11)
  } else if (points.value.length > 1) {
    map.fitBounds(
      points.value.map((point) => [point.latitude, point.longitude] as [number, number]),
      { padding: [48, 48], maxZoom: 12 },
    )
  }
}

async function setupMap(): Promise<void> {
  if (!mapEl.value || map) return

  const L = await import('leaflet')
  // CSS 也走动态 import:静态 import 会让这份样式进首屏包
  await import('leaflet/dist/leaflet.css')

  map = L.map(mapEl.value, {
    zoomControl: false,
    attributionControl: true,
    // 世界地图的副本在低缩放级别会重复,限一下让 OSM 版权信息始终可见
    maxBoundsViscosity: 0.6,
    worldCopyJump: true,
  }).setView([35.0, 105.0], 3)

  // 瓦片源原写死 OSM 的 tile.openstreetmap.org,但该域名在很多网络环境
  // 连不通(此处实测全部连接被拒/超时),地图会变成一片空灰底。
  // 候选源逐一实测后的取舍:
  //   cartocdn  —— 现在要 API key,瓦片全是 "API KEY REQUIRED" 占位图,不能用;
  //   ArcGIS    —— 中国区域高缩放级别回 "Map data not yet available",不能用;
  //   高德       —— 内容可用但坐标系是 GCJ02,点位会整体偏移约 500 米,不能用;
  //   openstreetmap.de —— OSM 德国镜像(FOSSGIS 运营,OSM 官方认可的基础设施),
  //   数据与坐标同 OSM 主站,中国区域细节齐全,四子域轮换可达。改用它。
  L.tileLayer('https://{s}.tile.openstreetmap.de/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
  }).addTo(map)

  // 缩放控件挪到右下:左上角留给导航栏,右上角有定位按钮
  L.control.zoom({ position: 'bottomright' }).addTo(map)

  layers = L.layerGroup().addTo(map)
  await buildMarkers()
}

/* ---------------------------------------------------------------------------
 *  聚焦:从日记详情"在地图上查看"过来时带 ?focus=id
 * ------------------------------------------------------------------------- */

async function focusEntry(id: number): Promise<void> {
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
  markers.clear()
  layers = null
  // Leaflet 会在 document 上挂 resize/scroll 监听并维护自己的尺寸缓存,
  // 不 remove 就会随每次进出地图页泄漏一份。
  map?.remove()
  map = null
})

/** 数据到位后补画标记。首次加载时 setupMap 与 load 是并行的,顺序不定。 */
watch(points, () => {
  void buildMarkers()
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

function openEntry(id: number): void {
  void router.push({ name: 'entry-detail', params: { id } })
}

function resetView(): void {
  selected.value = null
  void buildMarkers()
}
</script>

<template>
  <div class="map-page">
    <NavBar title="足迹">
      <template #right>
        <button
          v-if="hasPoints"
          class="map-page__action"
          type="button"
          title="回到全部足迹"
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

    <!-- 底部浮动毛玻璃卡片:统计,或选中某个点后的详情 -->
    <div class="floating">
      <transition name="rise" mode="out-in">
        <button
          v-if="selected"
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

        <div v-else :key="'stats'" class="floating__stats">
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

.floating__divider {
  width: 0.5px;
  height: 26px;
  background: var(--separator);
}

.floating__point {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2);
  border-radius: var(--radius-card);
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  box-shadow: var(--shadow-card);
  text-align: left;
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

.floating__point-text {
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

.floating__go {
  flex-shrink: 0;
  font-size: var(--text-footnote);
  color: var(--ios-blue);
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
