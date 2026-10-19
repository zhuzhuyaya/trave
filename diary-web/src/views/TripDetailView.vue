<script setup lang="ts">
/**
 * 行程详情。
 *
 * 结构:封面大图(带渐变兜底)+ 标题简介 + 统计行 + 日记列表。
 *
 * 图片做成"从封面里长出来"的效果:封面高度固定,内容区用负外边距
 * 压上去并给上圆角 —— 这是 iOS 音乐/相册详情页的经典做法,
 * 比"图是图、文是文"的上下拼接有整体感。
 *
 * 编辑走同一个 Sheet:改标题、简介、可见性、归档,一次提交。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import Sheet from '@/components/ios/Sheet.vue'
import EntryCard from '@/components/entry/EntryCard.vue'
import CoverPicker from '@/components/trip/CoverPicker.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { tripApi } from '@/api/trip'
import { toMessage } from '@/api/http'
import { useTripStore } from '@/stores/trips'
import { useUiStore } from '@/stores/ui'
import type { TripDetailVO } from '@/api/types'
import { gradientFor } from '@/utils/misc'
import { formatRange, daysBetween } from '@/utils/time'

const props = defineProps<{ id: string }>()

const router = useRouter()
const trips = useTripStore()
const ui = useUiStore()

const detail = ref<TripDetailVO | null>(null)
const loading = ref(true)
const failed = ref(false)

const editing = ref(false)
const confirmOpen = ref(false)
const submitting = ref(false)

/* ---- 编辑表单 ---- */
const formTitle = ref('')
const formDescription = ref('')
const formVisibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const formArchived = ref(false)
const formCoverId = ref<number | null>(null)
const formCoverUrl = ref<string | null>(null)

const trip = computed(() => detail.value?.trip ?? null)
const entries = computed(() => detail.value?.entries ?? [])

const coverStyle = computed(() => ({
  background: gradientFor(`trip-${props.id}`),
}))

const rangeText = computed(() => formatRange(trip.value?.startDate, trip.value?.endDate))
const dayCount = computed(() => daysBetween(trip.value?.startDate, trip.value?.endDate))

const statsLine = computed(() => {
  const t = trip.value
  if (!t) return []
  const parts: string[] = [`${t.entryCount} 篇日记`]
  if (t.startDate) parts.push(`${dayCount.value} 天`)
  if (t.locationSummary) parts.push(t.locationSummary)
  return parts
})

const canSubmit = computed(() => formTitle.value.trim().length > 0 && !submitting.value)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    detail.value = await tripApi.detail(Number(props.id))
  } catch (error) {
    failed.value = true
    detail.value = null
    ui.error(toMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openEdit(): void {
  const t = trip.value
  if (!t) return
  formTitle.value = t.title
  formDescription.value = t.description ?? ''
  formVisibility.value = t.visibility
  formArchived.value = t.archived
  formCoverId.value = t.coverPhotoId
  /*
    只在真的有自定义封面时才把 coverUrl 当预览。
    否则会把"兜底出来的那张日记首图"显示成当前封面 ——
    用户点「移除」之后画面纹丝不动,看起来就像坏了。
    没有自定义封面时是空框,那才是实话。
  */
  formCoverUrl.value = t.coverPhotoId ? t.coverUrl : null
  editing.value = true
}

async function save(): Promise<void> {
  if (!canSubmit.value || !trip.value) return
  submitting.value = true
  try {
    await tripApi.update(trip.value.id, {
      title: formTitle.value.trim(),
      description: formDescription.value.trim(),
      visibility: formVisibility.value,
      archived: formArchived.value,
      // 编辑态永远显式提交封面。null 在这里的意思是"清掉"(后端约定 0 = 清),
      // 而不是"别动" —— 面板里显示的封面状态就是用户按下保存时的意图。
      coverPhotoId: formCoverId.value ?? 0,
    })
    editing.value = false
    await load()
    trips.invalidate()
    await trips.reload()
    ui.success('行程已更新')
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    submitting.value = false
  }
}

async function remove(): Promise<void> {
  if (!trip.value) return
  try {
    await tripApi.remove(trip.value.id)
    trips.invalidate()
    await trips.reload()
    ui.success('行程已删除')
    await router.replace({ name: 'trips' })
  } catch (error) {
    ui.error(toMessage(error))
  }
}

/** 给这篇行程写一篇新日记:带上 tripId,编辑器会预选好归属 */
function writeEntry(): void {
  void router.push({ name: 'entry-new', query: { trip: String(props.id) } })
}
</script>

<template>
  <div class="trip">
    <!--
      同样不给 title:封面下方那张"纸面"里的 h1 才是这一页的标题,
      标题区只负责「返回」和右侧的写日记 / 编辑行程。

      操作全部挂在 trip.canEdit 上:公开行程别人读得到,但改不了。
      后端本来就会拦(403),可"点了才知道不行"是很差的反馈 ——
      看不见的按钮才是对的答案。
    -->
    <NavBar back>
      <template #right>
        <template v-if="trip && trip.canEdit">
          <button class="trip__nav-btn" type="button" @click="writeEntry">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
            </svg>
            写日记
          </button>

          <button class="trip__nav-btn trip__nav-btn--ghost" type="button" @click="openEdit">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M4 20h4l10-10a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20Z"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
            </svg>
            编辑行程
          </button>
        </template>
      </template>
    </NavBar>

    <div class="page__scroll">
      <template v-if="loading">
        <SkeletonBlock height="240px" radius="0" />
        <div class="trip__body">
          <SkeletonBlock width="70%" height="24px" />
          <SkeletonBlock width="45%" height="14px" />
        </div>
        <div class="trip__body">
          <SkeletonBlock height="86px" radius="12px" />
          <SkeletonBlock height="86px" radius="12px" />
        </div>
      </template>

      <EmptyState
        v-else-if="failed || !trip"
        icon="M12 8v5M12 16.5v.01M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z"
        title="这段行程打不开了"
        description="它可能已被删除,或者不属于当前账号"
      >
        <template #action>
          <button class="btn btn--secondary" type="button" @click="router.replace({ name: 'trips' })">
            回到行程列表
          </button>
        </template>
      </EmptyState>

      <template v-else>
        <!-- 封面。没有图片时用按 id 生成的渐变撑住,不留一块灰 -->
        <div class="trip__cover-wrap">
          <img v-if="trip.coverUrl" class="trip__cover" :src="trip.coverUrl" :alt="trip.title" />
          <div v-else class="trip__cover" :style="coverStyle" />
          <div class="trip__cover-scrim" />
        </div>

        <div class="trip__sheet">
          <div class="trip__head">
            <h1 class="trip__title user-selectable">{{ trip.title }}</h1>

            <div class="trip__badges">
              <span v-if="trip.visibility === 'PRIVATE'" class="trip__badge">仅自己可见</span>
              <span v-if="trip.archived" class="trip__badge trip__badge--muted">已归档</span>
              <span v-if="rangeText" class="trip__range">{{ rangeText }}</span>
            </div>

            <p v-if="trip.description" class="trip__desc user-selectable">{{ trip.description }}</p>

            <p class="trip__stats">
              <span v-for="(part, index) in statsLine" :key="part">
                <template v-if="index > 0"> · </template>{{ part }}
              </span>
            </p>
          </div>

          <div class="trip__list">
            <h2 class="trip__list-title">这段行程里的日记</h2>

            <template v-if="entries.length">
              <EntryCard v-for="entry in entries" :key="entry.id" :entry="entry" />
            </template>

            <p v-else class="trip__empty">
              还没有日记。这一路走过的地方,值得写下来。
            </p>
          </div>

          <!-- 删除是不可逆的,只给作者本人;别人看到的是纯阅读页 -->
          <div v-if="trip.canEdit" class="trip__danger">
            <button class="btn btn--danger btn--block" type="button" @click="confirmOpen = true">
              删除这段行程
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- ---- 编辑行程 ---- -->
    <Sheet v-model="editing" title="编辑行程">
      <div class="form">
        <CoverPicker v-model="formCoverId" v-model:preview-url="formCoverUrl" />

        <label class="form__field">
          <span class="form__label">名称</span>
          <input v-model="formTitle" class="field" type="text" maxlength="60" placeholder="行程名称" />
        </label>

        <label class="form__field">
          <span class="form__label">简介</span>
          <textarea
            v-model="formDescription"
            class="form__area"
            rows="3"
            maxlength="200"
            placeholder="这一趟的主题、同行的人、临行前的期待..."
          />
        </label>

        <div class="form__row">
          <div class="form__row-text">
            <span class="form__row-title">公开这段行程</span>
            <span class="form__row-desc">关闭时只有自己能看到</span>
          </div>
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="formVisibility === 'PUBLIC'"
            :class="{ 'switch--on': formVisibility === 'PUBLIC' }"
            @click="formVisibility = formVisibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'"
          >
            <span class="switch__knob" />
          </button>
        </div>

        <div class="form__row">
          <div class="form__row-text">
            <span class="form__row-title">归档</span>
            <span class="form__row-desc">归档后收进「全部」,不再出现在进行中</span>
          </div>
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="formArchived"
            :class="{ 'switch--on': formArchived }"
            @click="formArchived = !formArchived"
          >
            <span class="switch__knob" />
          </button>
        </div>

        <button class="btn btn--primary btn--block" type="button" :disabled="!canSubmit" @click="save">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </div>
    </Sheet>

    <ConfirmDialog
      v-model="confirmOpen"
      title="删除这段行程?"
      :message="`行程下的 ${trip?.entryCount ?? 0} 篇日记也会一起删除,无法恢复。`"
      confirm-text="删除"
      danger
      @confirm="remove"
    />
  </div>
</template>

<style scoped>
/* 行程详情是一条卡片列表 + 一张封面,和阅读页同一个宽度 */
.trip {
  --page-max: var(--list-max);
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-grouped);
}

.trip__nav-btn {
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

.trip__nav-btn:hover {
  background: var(--bg-hover);
}

.trip__nav-btn--ghost {
  background: transparent;
  color: var(--label-2);
}

.trip__nav-btn--ghost:hover {
  background: var(--bg-fill);
  color: var(--label);
}

/* ---- 封面 ---- */
.trip__cover-wrap {
  position: relative;
  height: 320px;
}

.trip__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 底部压一层黑渐变,让下方压上来的纸面有过渡,不是硬切 */
.trip__cover-scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0.1) 0%,
    rgba(0, 0, 0, 0) 40%,
    rgba(0, 0, 0, 0.3) 100%
  );
  pointer-events: none;
}

/* ---- 内容纸面:压住封面下沿,上圆角 ---- */
.trip__sheet {
  position: relative;
  margin-top: -22px;
  border-radius: 22px 22px 0 0;
  background: var(--bg-grouped);
}

.trip__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5) var(--gutter);
}

.trip__head {
  padding: var(--space-5) var(--gutter) var(--space-2);
}

.trip__title {
  font-size: var(--text-title1);
  line-height: 1.26;
  font-weight: 700;
  letter-spacing: -0.6px;
}

.trip__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.trip__badge {
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--ios-blue) 12%, transparent);
  color: var(--ios-blue);
  font-size: var(--text-caption2);
}

.trip__badge--muted {
  background: var(--bg-fill);
  color: var(--label-2);
}

.trip__range {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.trip__desc {
  margin-top: var(--space-3);
  font-size: var(--text-subhead);
  line-height: 1.6;
  color: var(--label-2);
}

.trip__stats {
  margin-top: var(--space-3);
  font-size: var(--text-footnote);
  color: var(--label-3);
}

.trip__list {
  padding: var(--space-2) 0 0;
}

.trip__list-title {
  padding: 0 var(--gutter) var(--space-2);
  font-size: var(--text-footnote);
  color: var(--label-2);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.trip__empty {
  padding: var(--space-6) var(--gutter);
  text-align: center;
  font-size: var(--text-subhead);
  line-height: 1.7;
  color: var(--label-2);
}

.trip__danger {
  padding: var(--space-6) var(--gutter) var(--space-4);
}

/* ---- 编辑表单 ---- */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form__label {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.form__area {
  width: 100%;
  padding: var(--space-3);
  background: var(--bg-fill);
  border: none;
  border-radius: var(--radius-field);
  font-size: var(--text-body);
  line-height: 1.6;
  font-family: inherit;
  resize: vertical;
}

.form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.form__row-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.form__row-title {
  font-size: var(--text-body);
}

.form__row-desc {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.switch {
  position: relative;
  flex-shrink: 0;
  width: 51px;
  height: 31px;
  border-radius: var(--radius-full);
  background: var(--ios-gray5);
  transition: background var(--dur) var(--ease-standard);
}

.switch--on {
  background: var(--ios-green);
}

.switch__knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 27px;
  height: 27px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
  transition: transform var(--dur) var(--ease-ios);
}

.switch--on .switch__knob {
  transform: translateX(20px);
}
</style>
