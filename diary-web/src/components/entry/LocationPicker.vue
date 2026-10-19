<script setup lang="ts">
/**
 * 地点选择器。
 *
 * 为什么不调地图选点 / 逆地理编码服务:那需要一个 API Key,
 * 而且移动端在 H5 里拖地图选点远不如原生顺畅。
 * 这里给一个内置的常用地点表(带真实经纬度),输入即筛选,
 * 再留一个"手动填写坐标"的出口 —— 够用,且零外部依赖。
 *
 * 选中的结果通过 v-model 传回 { name, latitude, longitude }。
 */
import { computed, ref, watch } from 'vue'
import Sheet from '@/components/ios/Sheet.vue'
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

const filtered = computed(() => {
  const query = keyword.value.trim()
  if (!query) return PLACES
  return PLACES.filter(
    (place) => place.name.includes(query) || place.region.includes(query),
  )
})

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

      <!-- 手动坐标:折叠起来,只有需要精确打点的人才展开 -->
      <button class="picker__manual-toggle" type="button" @click="manualOpen = !manualOpen">
        {{ manualOpen ? '收起手动坐标' : '手动填写经纬度' }}
      </button>

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
          <button class="picker__row" type="button" @click="pick(place.name, place.latitude, place.longitude)">
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

.picker__manual-toggle {
  align-self: flex-start;
  font-size: var(--text-footnote);
  color: var(--ios-blue);
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
