<script setup lang="ts">
/**
 * 地点选择器。
 *
 * 地理编码服务不接:那需要一个 API Key,还涉及配额与限流。
 * 三个零依赖的入口并列:
 * 1. 内置常用地点表(带真实经纬度),输入即筛选;
 * 2. 地图选点(MapLocationPicker):在图上点一下,坐标与地名都拿到 ——
 *    地名由足迹地图的板块数据推出,不是反查服务;
 * 3. 手动填写坐标,留给表与地图都覆盖不到的精确打点。
 *
 * 选中的结果通过 v-model 传回 { name, latitude, longitude }。
 * 统一口径:PLACES 表当初取自高德,是 GCJ02;出这扇门前一律 gcjToWgs
 * 转成 WGS84(与地图选点、OSM 瓦片、在线检索同系),存库坐标才不混系。
 */
import { computed, ref, watch } from 'vue'
import Sheet from '@/components/ios/Sheet.vue'
import MapLocationPicker from '@/components/entry/MapLocationPicker.vue'
import { gcjToWgs } from '@/utils/coord'
import { PLACES } from '@/utils/places'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    name?: string | null
    latitude?: number | null
    longitude?: number | null
  }>(),
  { name: null, latitude: null, longitude: null },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  pick: [payload: { name: string; latitude: number | null; longitude: number | null }]
}>()

const keyword = ref('')
const manualOpen = ref(false)
const manualLat = ref('')
const manualLng = ref('')
/** 二级弹窗:地图选点开着时本弹窗保持挂载,确认后一起收 */
const mapOpen = ref(false)

const filtered = computed(() => {
  const query = keyword.value.trim()
  if (!query) return PLACES
  return PLACES.filter(
    (place) => place.name.includes(query) || place.region.includes(query),
  )
})

/** 内置表条目转成 WGS84 再交出去(表是 GCJ02, gcjToWgs 回 [经度, 纬度]) */
function pickPlace(place: (typeof PLACES)[number]): void {
  const [lng, lat] = gcjToWgs(place.longitude, place.latitude)
  pick(place.name, lat, lng)
}

/** 打开时把搜索框清空并带上当前值,避免上次输入残留 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    keyword.value = ''
    manualOpen.value = false
    manualLat.value = props.latitude != null ? String(props.latitude) : ''
    manualLng.value = props.longitude != null ? String(props.longitude) : ''
  },
)

function close(): void {
  emit('update:modelValue', false)
}

function pick(name: string, latitude: number | null, longitude: number | null): void {
  emit('pick', { name, latitude, longitude })
  close()
}

/** 地图选点回执:region 文案("福建 · 龙岩 · 新罗区")拆成"地名 + 上级",
 *  name 取点位的板块名,region 放上级链,与地点表的展示口径一致 */
function onMapPicked(payload: { name: string; region: string; latitude: number; longitude: number }): void {
  pick(payload.name || payload.region, payload.latitude, payload.longitude)
}

function clear(): void {
  emit('pick', { name: '', latitude: null, longitude: null })
  close()
}

/** 手动坐标:两个都能解析成数字才算有效,否则退化成"只有地名没有坐标" */
function submitManual(): void {
  const rawName = keyword.value.trim()
  const lat = Number(manualLat.value)
  const lng = Number(manualLng.value)
  const validCoords =
    manualLat.value.trim() !== '' &&
    manualLng.value.trim() !== '' &&
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    Math.abs(lat) <= 90 &&
    Math.abs(lng) <= 180

  if (!rawName && !validCoords) return
  pick(
    rawName || `${lat.toFixed(4)}, ${lng.toFixed(4)}`,
    validCoords ? lat : null,
    validCoords ? lng : null,
  )
}
</script>

<template>
  <Sheet :model-value="modelValue" title="地点" @update:model-value="close">
    <div class="picker">
      <div class="picker__search">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="2" />
          <path d="M16 16l4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input
          v-model="keyword"
          class="picker__input"
          type="search"
          autocomplete="off"
          placeholder="搜索城市或地区"
        />
      </div>

      <div class="picker__entries">
        <button class="picker__entry" type="button" @click="mapOpen = true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z" stroke="currentColor" stroke-width="1.8" />
            <circle cx="12" cy="10.5" r="2.3" stroke="currentColor" stroke-width="1.8" />
          </svg>
          在地图上选点
        </button>
        <button class="picker__entry" type="button" @click="manualOpen = !manualOpen">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 17l6-6-4-4 8-3m3 14l3-7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          {{ manualOpen ? '收起手动坐标' : '手动填写经纬度' }}
        </button>
      </div>

      <MapLocationPicker
        v-model="mapOpen"
        :latitude="latitude"
        :longitude="longitude"
        @pick="onMapPicked"
      />

      <div v-if="manualOpen" class="picker__manual">
        <label class="picker__manual-field">
          <span>纬度</span>
          <input v-model="manualLat" type="text" inputmode="decimal" placeholder="25.6065" />
        </label>
        <label class="picker__manual-field">
          <span>经度</span>
          <input v-model="manualLng" type="text" inputmode="decimal" placeholder="100.2676" />
        </label>
        <button class="btn btn--secondary btn--block" type="button" @click="submitManual">
          用这个地点和坐标
        </button>
      </div>

      <div v-if="manualOpen" class="picker__manual">
        <label class="picker__manual-field">
          <span>纬度</span>
          <input v-model="manualLat" type="text" inputmode="decimal" placeholder="25.6065" />
        </label>
        <label class="picker__manual-field">
          <span>经度</span>
          <input v-model="manualLng" type="text" inputmode="decimal" placeholder="100.2676" />
        </label>
        <button class="btn btn--secondary btn--block" type="button" @click="submitManual">
          用这个地点和坐标
        </button>
      </div>

      <ul v-if="filtered.length" class="picker__list">
        <li v-for="place in filtered" :key="place.name">
          <button class="picker__row" type="button" @click="pickPlace(place)">
            <span class="picker__row-main">
              <span class="picker__row-name">{{ place.name }}</span>
              <span class="picker__row-region">{{ place.region }}</span>
            </span>
            <svg v-if="place.name === name" class="picker__check" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M4.5 12.5l5 5 10-11" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </li>
      </ul>

      <p v-else class="picker__empty">
        没有这个地点。可以
        <button class="picker__empty-link" type="button" @click="manualOpen = true">手动填坐标</button>,
        或者关掉后直接在输入框里写地名。
      </p>

      <button v-if="name" class="picker__clear" type="button" @click="clear">清除地点</button>
    </div>
  </Sheet>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.picker__search {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-cell);
  background: var(--bg-fill);
  color: var(--label-3);
}

.picker__input {
  flex: 1;
  min-width: 0;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--label);
  font-size: var(--text-body);
}

.picker__input::-webkit-search-cancel-button {
  display: none;
}

.picker__entries {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.picker__entry {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-1);
  border-radius: 8px;
  font-size: var(--text-footnote);
  color: var(--ios-blue);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-standard);
}

.picker__entry:hover {
  background: var(--bg-hover);
}

.picker__entry svg {
  flex-shrink: 0;
}

.picker__manual {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-cell);
  background: var(--bg-grouped);
}

.picker__manual-field {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: 8px;
  background: var(--bg-elevated);
  font-size: var(--text-subhead);
  color: var(--label-2);
}

.picker__manual-field input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--label);
  font-size: var(--text-body);
  text-align: right;
}

/* 列表用负外边距顶到弹窗两侧,行自身的 padding 负责留白,
   这样分隔线能像 iOS 列表一样通到边缘。
   负值必须和 Sheet 弹窗正文的左右内边距一致,否则会露出缝。 */
.picker__list {
  margin: 0 calc(var(--space-5) * -1);
}

.picker__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-5);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-standard);
}

.picker__row:hover {
  background: var(--bg-hover);
}

.picker__row-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}

.picker__row-name {
  font-size: var(--text-body);
}

.picker__row-region {
  font-size: var(--text-footnote);
  color: var(--label-3);
}

.picker__check {
  color: var(--ios-blue);
  flex-shrink: 0;
}

.picker__empty {
  padding: var(--space-4) 0;
  text-align: center;
  font-size: var(--text-footnote);
  line-height: 1.6;
  color: var(--label-2);
}

.picker__empty-link {
  color: var(--ios-blue);
  font-size: var(--text-footnote);
}

.picker__clear {
  padding: var(--space-3);
  font-size: var(--text-body);
  color: var(--ios-red);
  border-top: 0.5px solid var(--separator);
}
</style>
