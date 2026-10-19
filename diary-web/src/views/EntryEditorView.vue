<script setup lang="ts">
/**
 * 写 / 编辑日记。
 *
 * 版式上刻意做成"一张纸":标题、正文、图片全部平铺在同一页,
 * 元信息(日期 / 地点 / 天气 / 心情 / 行程 / 标签)收进底部的一张
 * 分组卡片里。理由是这样写起来不需要在多个步骤间跳转 ——
 * 写字这件事最忌讳打断。
 *
 * 三件值得说明的事:
 *
 * 1. 图片先传后存。选图时立刻并发上传,拿到 url 才放进 photoUrls;
 *    保存日记时后端只认 url。这样"存草稿"和"发布"是同一条路径,
 *    也不存在保存时卡在等 9 张图上传的情况。
 *
 * 2. 并发上传有限流(同时 3 个)。移动端并发十几个请求会让后几个
 *    排队超时,3 个是吞吐与稳定性的折中。
 *
 * 3. 元信息用 Sheet 选择而不是原生 <select>:iOS 原生的 picker 是
 *    底部弹出的,<select> 在移动端会跳出一个系统级列表,很割裂。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import Sheet from '@/components/ios/Sheet.vue'
import PhotoGrid from '@/components/ui/PhotoGrid.vue'
import PhotoViewer from '@/components/entry/PhotoViewer.vue'
import LocationPicker from '@/components/entry/LocationPicker.vue'
import { entryApi } from '@/api/entry'
import { photoApi } from '@/api/photo'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import { useTripStore } from '@/stores/trips'
import { concurrent } from '@/utils/misc'
import { formatDateISO, formatDateCN, formatYearMonth } from '@/utils/time'

const props = defineProps<{ id?: string }>()

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const trips = useTripStore()

const isEdit = computed(() => Boolean(props.id))

/* ---- 正文 ---- */
const title = ref('')
const content = ref('')

/* ---- 元信息 ---- */
const entryDate = ref(formatDateISO(new Date()))
const locationName = ref('')
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const weather = ref('')
const mood = ref('')
const tripId = ref<number | null>(null)
const tags = ref<string[]>([])
const tagDraft = ref('')

/* ---- 图片 ---- */
const photoUrls = ref<string[]>([])
const uploading = ref(0)
const viewerOpen = ref(false)
const viewerIndex = ref(0)

/* ---- 面板可见性 ---- */
const dateOpen = ref(false)
const weatherOpen = ref(false)
const moodOpen = ref(false)
const tripOpen = ref(false)
const locationOpen = ref(false)

const WEATHERS = ['晴', '多云', '阴', '小雨', '大雨', '雷阵雨', '雪', '雾', '大风']
const MOODS = ['开心', '平静', '兴奋', '疲惫', '感慨', '满足', '想念', '有点低落']

const loading = ref(false)
const submitting = ref(false)

const canSubmit = computed(
  () => title.value.trim().length > 0 && content.value.trim().length > 0 && !submitting.value,
)

const dateLabel = computed(() => formatDateCN(entryDate.value))
const tripLabel = computed(() => {
  if (tripId.value == null) return '不归类'
  return trips.byId(tripId.value)?.title ?? '已删除的行程'
})
const hasCoordinate = computed(() => latitude.value != null && longitude.value != null)

/* ---------------------------------------------------------------------------
 *  载入(编辑态)
 * ------------------------------------------------------------------------- */

async function load(): Promise<void> {
  if (!props.id) return
  loading.value = true
  try {
    const entry = await entryApi.detail(Number(props.id))
    title.value = entry.title
    content.value = entry.content
    entryDate.value = entry.entryDate.slice(0, 10)
    locationName.value = entry.locationName ?? ''
    latitude.value = entry.latitude
    longitude.value = entry.longitude
    weather.value = entry.weather ?? ''
    mood.value = entry.mood ?? ''
    tripId.value = entry.tripId
    tags.value = [...entry.tags]
    photoUrls.value = entry.photos.map((photo) => photo.url)
  } catch (error) {
    ui.error(toMessage(error))
    await router.replace({ name: 'timeline' })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  trips.ensureLoaded()
  // 从行程详情页点"写日记"过来时会带上 ?trip=id,预选好归属 ——
  // 用户既然是从那段行程里进来的,意图已经很明确了。
  const fromTrip = Number(route.query.trip)
  if (!props.id && Number.isFinite(fromTrip) && fromTrip > 0) {
    tripId.value = fromTrip
  }
  void load()
})

/* ---------------------------------------------------------------------------
 *  图片:先传后存
 * ------------------------------------------------------------------------- */

/** 一次最多 18 张,已传的部分也算在内 */
const MAX_PHOTOS = 18

/** 真正的上传逻辑。点"添加"选文件、把文件拖进来,两条路都走这里。 */
async function uploadFiles(files: File[]): Promise<void> {
  if (!files.length) return

  const room = MAX_PHOTOS - photoUrls.value.length
  if (room <= 0) {
    ui.error(`一篇日记最多 ${MAX_PHOTOS} 张图片`)
    return
  }
  const picked = files.slice(0, room)
  if (picked.length < files.length) {
    ui.error(`一次最多还能加 ${room} 张,多余的已忽略`)
  }

  uploading.value += picked.length
  // 限流 3:同时发十几个请求,后面的会排队到超时
  const results = await concurrent(picked, 3, async (file) => {
    try {
      const photo = await photoApi.upload(file)
      return photo.url
    } catch (error) {
      ui.error(`${file.name}:${toMessage(error)}`)
      return null
    } finally {
      uploading.value -= 1
    }
  })

  for (const url of results) {
    if (url) photoUrls.value.push(url)
  }
}

/* ---------------------------------------------------------------------------
 *  拖拽入内上传
 *
 *  这是桌面端才有的能力:从文件管理器里把照片直接拖进页面。
 *  移动端没有"同时看着两个窗口"这回事,所以这套交互是纯增益的。
 *  只在真的拖着文件时才高亮 —— 拖着文字或链接时不该有反应。
 * ------------------------------------------------------------------------- */

const dragging = ref(false)

function onDragOver(event: DragEvent): void {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragging.value = true
}

/**
 * 指针移到子元素上也会触发 dragleave,
 * 用 relatedTarget 判断"是不是还在这块区域内",否则高亮会一直闪。
 */
function onDragLeave(event: DragEvent): void {
  const next = event.relatedTarget as Node | null
  if (next && (event.currentTarget as Node).contains(next)) return
  dragging.value = false
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  void uploadFiles(Array.from(event.dataTransfer?.files ?? []))
}

function removePhoto(index: number): void {
  photoUrls.value.splice(index, 1)
}

function previewPhoto(index: number): void {
  viewerIndex.value = index
  viewerOpen.value = true
}

/* ---------------------------------------------------------------------------
 *  标签
 * ------------------------------------------------------------------------- */

const MAX_TAGS = 5

function addTag(): void {
  const value = tagDraft.value.trim().replace(/^#/, '')
  if (!value) return
  if (value.length > 12) {
    ui.error('单个标签最长 12 个字')
    return
  }
  if (tags.value.includes(value)) {
    tagDraft.value = ''
    return
  }
  if (tags.value.length >= MAX_TAGS) {
    ui.error(`最多只能挂 ${MAX_TAGS} 个标签`)
    return
  }
  tags.value.push(value)
  tagDraft.value = ''
}

function removeTag(tag: string): void {
  tags.value = tags.value.filter((item) => item !== tag)
}

/* ---------------------------------------------------------------------------
 *  地点
 * ------------------------------------------------------------------------- */

function onLocationPicked(payload: {
  name: string
  latitude: number | null
  longitude: number | null
}): void {
  locationName.value = payload.name
  latitude.value = payload.latitude
  longitude.value = payload.longitude
}

/* ---------------------------------------------------------------------------
 *  保存
 * ------------------------------------------------------------------------- */

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  if (uploading.value > 0) {
    ui.error('还有图片在上传,稍等一下')
    return
  }

  submitting.value = true
  try {
    const payload = {
      title: title.value.trim(),
      content: content.value,
      entryDate: entryDate.value,
      tripId: tripId.value,
      locationName: locationName.value.trim() || null,
      latitude: latitude.value,
      longitude: longitude.value,
      weather: weather.value || null,
      mood: mood.value || null,
      tags: tags.value,
      photoUrls: photoUrls.value,
    }

    const saved = props.id
      ? await entryApi.update(Number(props.id), payload)
      : await entryApi.create(payload)

    ui.success(isEdit.value ? '已保存' : '日记写好了')
    await router.replace({ name: 'entry-detail', params: { id: saved.id } })
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    submitting.value = false
  }
}

/**
 * 有内容未保存时拦下返回,问一句再走 —— 手机上误触返回很常见,
 * 而"写了三百字被一次误触清空"是记日记最劝退的体验。
 */
function goBack(event: { preventDefault: () => void }): void {
  const dirty = title.value.trim() || content.value.trim() || photoUrls.value.length
  if (!dirty || submitting.value) return

  if (!window.confirm('这篇日记还没保存,确定要离开吗?')) {
    event.preventDefault()
  }
}
</script>

<template>
  <div class="editor">
    <NavBar :title="isEdit ? '编辑日记' : '写日记'" back @back="goBack">
      <template #right>
        <button
          class="editor__save"
          type="button"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{ submitting ? '保存中' : '保存' }}
        </button>
      </template>
    </NavBar>

    <div class="page__scroll">
      <div v-if="loading" class="editor__loading">正在读取...</div>

      <template v-else>
        <!--
          两栏:左边是"一张纸"(标题 + 正文 + 图片),右边是元信息。
          宽屏上写正文时元信息不该挤在同一列里跟着滚 ——
          它们是次要信息,收进右栏既不挡字,又始终看得见。
        -->
        <div class="editor__cols">
          <div class="editor__col-main">
            <!-- 一张纸:标题 + 正文 -->
            <div class="editor__paper">
              <input
                v-model="title"
                class="editor__title"
                type="text"
                placeholder="标题"
                maxlength="120"
              />

              <textarea
                v-model="content"
                class="editor__content"
                placeholder="今天走了哪些路,看见了什么?支持 Markdown。"
                spellcheck="false"
              />
            </div>

            <!-- 图片:相册式网格 + 添加格。整块区域都能接收拖进来的文件 -->
            <section
              class="editor__photos"
              :class="{ 'editor__photos--drop': dragging }"
              @dragover.prevent="onDragOver"
              @dragleave="onDragLeave"
              @drop.prevent="onDrop"
            >
              <div class="editor__photos-head">
                <span class="editor__photos-label">图片</span>
                <span class="editor__photos-tip">可以把图片直接拖到这里</span>
              </div>

              <PhotoGrid
                :urls="photoUrls"
                editable
                :max="MAX_PHOTOS"
                @files="uploadFiles"
                @remove="removePhoto"
                @preview="previewPhoto"
              />
              <p v-if="uploading" class="editor__uploading">
                正在上传 {{ uploading }} 张图片...
              </p>
            </section>
          </div>

          <aside class="editor__col-side">
            <!-- 元信息:日期 / 地点 / 天气 / 心情 / 行程 -->
            <div class="group">
              <button class="meta" type="button" @click="dateOpen = true">
                <span class="meta__label">日期</span>
                <span class="meta__value">{{ dateLabel }}</span>
              </button>

              <button class="meta" type="button" @click="locationOpen = true">
                <span class="meta__label">地点</span>
                <span class="meta__value" :class="{ 'meta__value--placeholder': !locationName }">
                  {{ locationName || '未填写' }}
                </span>
                <span v-if="hasCoordinate" class="meta__badge">有坐标</span>
              </button>

              <button class="meta" type="button" @click="weatherOpen = true">
                <span class="meta__label">天气</span>
                <span class="meta__value" :class="{ 'meta__value--placeholder': !weather }">
                  {{ weather || '未填写' }}
                </span>
              </button>

              <button class="meta" type="button" @click="moodOpen = true">
                <span class="meta__label">心情</span>
                <span class="meta__value" :class="{ 'meta__value--placeholder': !mood }">
                  {{ mood || '未填写' }}
                </span>
              </button>

              <button class="meta" type="button" @click="tripOpen = true">
                <span class="meta__label">归入行程</span>
                <span class="meta__value" :class="{ 'meta__value--placeholder': tripId == null }">
                  {{ tripLabel }}
                </span>
              </button>
            </div>

            <!-- 标签 -->
            <div class="group">
              <div class="tags">
                <span v-for="tag in tags" :key="tag" class="tags__item">
                  #{{ tag }}
                  <button class="tags__remove" type="button" :aria-label="`移除 ${tag}`" @click="removeTag(tag)">
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M7 7l10 10M17 7L7 17" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
                    </svg>
                  </button>
                </span>

                <input
                  v-model="tagDraft"
                  class="tags__input"
                  type="text"
                  :placeholder="tags.length >= MAX_TAGS ? '标签已满' : '加标签'"
                  :disabled="tags.length >= MAX_TAGS"
                  @keydown.enter.prevent="addTag"
                  @blur="addTag"
                />
              </div>
            </div>

            <p class="editor__hint">
              图文都支持 Markdown 语法,例如 **加粗**、- 列表、[链接](url)。
            </p>

            <!-- 保存也放一份在右栏:页面标题会随内容滚走,而右栏是贴顶的 -->
            <button
              class="btn btn--primary btn--block"
              type="button"
              :disabled="!canSubmit"
              @click="submit"
            >
              {{ submitting ? '保存中...' : '保存日记' }}
            </button>
          </aside>
        </div>
      </template>
    </div>

    <!-- ---- 日期 ---- -->
    <Sheet v-model="dateOpen" title="选择日期">
      <div class="picker">
        <input v-model="entryDate" class="field" type="date" :max="formatDateISO(new Date())" />
        <p class="picker__hint">不能选未来的日期 —— 日记记的是已经发生的事。</p>
      </div>
    </Sheet>

    <!-- ---- 天气 ---- -->
    <Sheet v-model="weatherOpen" title="天气">
      <div class="picker">
        <div class="picker__options">
          <button
            v-for="item in WEATHERS"
            :key="item"
            class="picker__option"
            :class="{ 'picker__option--active': weather === item }"
            type="button"
            @click="weather = weather === item ? '' : item; weatherOpen = false"
          >
            {{ item }}
          </button>
        </div>
        <button v-if="weather" class="picker__clear" type="button" @click="weather = ''; weatherOpen = false">
          清除天气
        </button>
      </div>
    </Sheet>

    <!-- ---- 心情 ---- -->
    <Sheet v-model="moodOpen" title="心情">
      <div class="picker">
        <div class="picker__options">
          <button
            v-for="item in MOODS"
            :key="item"
            class="picker__option"
            :class="{ 'picker__option--active': mood === item }"
            type="button"
            @click="mood = mood === item ? '' : item; moodOpen = false"
          >
            {{ item }}
          </button>
        </div>
        <button v-if="mood" class="picker__clear" type="button" @click="mood = ''; moodOpen = false">
          清除心情
        </button>
      </div>
    </Sheet>

    <!-- ---- 行程 ---- -->
    <Sheet v-model="tripOpen" title="归入行程">
      <div class="picker">
        <button
          class="picker__trip"
          :class="{ 'picker__trip--active': tripId == null }"
          type="button"
          @click="tripId = null; tripOpen = false"
        >
          <span class="picker__trip-title">不归类</span>
          <span class="picker__trip-meta">留在这篇日记自己的时间点上</span>
        </button>

        <button
          v-for="trip in trips.items"
          :key="trip.id"
          class="picker__trip"
          :class="{ 'picker__trip--active': tripId === trip.id }"
          type="button"
          @click="tripId = trip.id; tripOpen = false"
        >
          <span class="picker__trip-title">{{ trip.title }}</span>
          <span class="picker__trip-meta">
            {{ trip.entryCount }} 篇
            <template v-if="trip.startDate"> · {{ formatYearMonth(trip.startDate) }}</template>
          </span>
        </button>

        <p v-if="!trips.items.length" class="picker__hint">
          还没有行程。可以先去「行程」页新建一个,再回来归类。
        </p>
      </div>
    </Sheet>

    <LocationPicker
      v-model="locationOpen"
      :name="locationName"
      :latitude="latitude"
      :longitude="longitude"
      @pick="onLocationPicked"
    />

    <PhotoViewer v-model="viewerOpen" :urls="photoUrls" :index="viewerIndex" />
  </div>
</template>

<style scoped>
.editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-grouped);
}

/* 标题区的保存:次要入口,右栏底部还有一个主按钮 */
.editor__save {
  height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-field);
  background: var(--bg-fill);
  font-size: var(--text-subhead);
  font-weight: 600;
  color: var(--ios-blue);
  transition: background var(--dur-fast) var(--ease-standard);
}

.editor__save:hover:not(:disabled) {
  background: var(--bg-hover);
}

.editor__save:disabled {
  color: var(--label-3);
  cursor: default;
}

.editor__loading {
  padding: var(--space-8);
  text-align: center;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/*
  两栏。左栏给正文一个固定的阅读宽度(写作时行长稳定,排版才是可预期的),
  右栏固定 320px 并贴顶 —— 往下写的时候元信息一直在。

  左右留白必须自己加:.page__scroll 这一层是不留白的(内容块都自带 --gutter),
  而这一页的第一层是栅格,没有内缩,不加就会比上面的页面标题左凸 24px。
*/
.editor__cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-6);
  align-items: start;
  padding: 0 var(--gutter);
}

.editor__col-main {
  min-width: 0;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
}

.editor__col-side {
  position: sticky;
  top: calc(var(--sticky-offset) + var(--space-4));
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* 右栏的分组自带左右 24px 外边距,在这里会把它顶出栏外 */
.editor__col-side .group {
  margin-left: 0;
  margin-right: 0;
}

/* ---- 一张纸 ---- */
.editor__paper {
  padding: var(--space-5) var(--space-6) var(--space-6);
}

.editor__title {
  width: 100%;
  border: none;
  background: transparent;
  font-size: var(--text-title1);
  line-height: var(--lh-title1);
  font-weight: 700;
  letter-spacing: -0.5px;
}

.editor__title::placeholder {
  color: var(--label-4);
  font-weight: 600;
}

/* 正文用等宽行高的自增 textarea —— 高度交给 rows 与 min-height,
   不写 JS 自动撑高,少一个 resize 监听就少一处滚动抖动 */
.editor__content {
  width: 100%;
  min-height: 360px;
  margin-top: var(--space-4);
  border: none;
  background: transparent;
  font-size: var(--text-body);
  line-height: 1.7;
  resize: vertical;
  font-family: inherit;
}

.editor__content::placeholder {
  color: var(--label-4);
}

/* ---- 图片 ---- */
.editor__photos {
  position: relative;
  padding: var(--space-4) var(--space-6) var(--space-6);
  border-top: 0.5px solid var(--separator);
  transition: background var(--dur-fast) var(--ease-standard);
}

/* 拖着文件进来时整块区域亮一下,明确"松手就落在这儿" */
.editor__photos--drop {
  background: color-mix(in srgb, var(--ios-blue) 8%, transparent);
  outline: 2px dashed var(--ios-blue);
  outline-offset: -8px;
  border-radius: var(--radius-cell);
}

.editor__photos-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.editor__photos-label {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

/* 这行提示是发现"拖拽上传"这个能力的唯一线索,虽然占地方但不能省 */
.editor__photos-tip {
  font-size: var(--text-caption1);
  color: var(--label-3);
}

.editor__uploading {
  margin-top: var(--space-2);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

/* ---- 元信息行 ---- */
.meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 46px;
  padding: 0 var(--space-4);
  text-align: left;
  position: relative;
  transition: background var(--dur-fast) var(--ease-standard);
}

.meta:not(:last-child)::after {
  content: '';
  position: absolute;
  left: var(--space-4);
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--separator);
  transform: scaleY(0.5);
}

.meta:hover {
  background: var(--bg-hover);
}

.meta__label {
  flex-shrink: 0;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

.meta__value {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: var(--text-subhead);
  color: var(--label);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta__value--placeholder {
  color: var(--label-3);
}

.meta__badge {
  flex-shrink: 0;
  padding: 1px var(--space-2);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--ios-blue) 14%, transparent);
  color: var(--ios-blue);
  font-size: var(--text-caption2);
}

/* ---- 标签 ---- */
.tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
}

.tags__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 var(--space-2) 0 var(--space-3);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--ios-blue) 12%, transparent);
  color: var(--ios-blue);
  font-size: var(--text-footnote);
}

.tags__remove {
  display: flex;
  color: currentColor;
  opacity: 0.6;
}

.tags__remove:hover {
  opacity: 1;
}

.tags__input {
  flex: 1;
  min-width: 90px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: var(--text-body);
}

.tags__input::placeholder {
  color: var(--label-3);
  font-size: var(--text-subhead);
}

.editor__hint {
  font-size: var(--text-caption1);
  line-height: 1.6;
  color: var(--label-3);
}

/* ---- 选择面板内容 ---- */
.picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.picker__hint {
  font-size: var(--text-footnote);
  line-height: 1.6;
  color: var(--label-2);
}

.picker__options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.picker__option {
  height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-full);
  background: var(--bg-fill);
  color: var(--label);
  font-size: var(--text-subhead);
}

.picker__option--active {
  background: var(--ios-blue);
  color: #fff;
  font-weight: 600;
}

.picker__option:hover {
  background: var(--bg-hover);
}

.picker__option--active:hover {
  background: var(--ios-blue);
}

.picker__clear {
  padding: var(--space-3);
  font-size: var(--text-body);
  color: var(--ios-red);
  border-top: 0.5px solid var(--separator);
  transition: background var(--dur-fast) var(--ease-standard);
}

.picker__clear:hover {
  background: color-mix(in srgb, var(--ios-red) 10%, transparent);
}

.picker__trip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-cell);
  background: var(--bg-grouped);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-standard);
}

.picker__trip--active {
  background: color-mix(in srgb, var(--ios-blue) 12%, transparent);
}

.picker__trip:hover {
  background: var(--bg-hover);
}

.picker__trip--active:hover {
  background: color-mix(in srgb, var(--ios-blue) 20%, transparent);
}

.picker__trip-title {
  font-size: var(--text-body);
  font-weight: 500;
}

.picker__trip-meta {
  font-size: var(--text-footnote);
  color: var(--label-2);
}
</style>
