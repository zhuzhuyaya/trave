<script setup lang="ts">
/**
 * 日记详情。
 *
 * 版式按阅读顺序展开:大标题 + 元信息(日期/地点/天气/心情) → 图片
 * → 正文 → 标签 → 底部操作条。
 *
 * 三件值得说明的事:
 *   1. 阅读数用单独的 POST 接口上报,且在 onMounted 里只发一次 ——
 *      放在 GET 里会让"后退重放"也计数。
 *   2. 标题也用大标题样式,和详情页的层级感一致;滚动后收到导航栏。
 *   3. 地图坐标存在时给一个「在地图上查看」入口,跳回足迹页并定位。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import MarkdownView from '@/components/ui/MarkdownView.vue'
import PhotoViewer from '@/components/entry/PhotoViewer.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { entryApi } from '@/api/entry'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { EntryDetailVO } from '@/api/types'
import { formatDateCN, weekdayCN, formatRelative } from '@/utils/time'

const props = defineProps<{ id: string }>()

const router = useRouter()
const ui = useUiStore()

const entry = ref<EntryDetailVO | null>(null)
const loading = ref(true)
const failed = ref(false)

const viewerOpen = ref(false)
const viewerIndex = ref(0)
const confirmOpen = ref(false)

const photoUrls = computed(() => entry.value?.photos.map((photo) => photo.url) ?? [])

const dateText = computed(() => {
  if (!entry.value) return ''
  return `${formatDateCN(entry.value.entryDate)} ${weekdayCN(entry.value.entryDate)}`
})

const hasCoordinate = computed(
  () => entry.value?.latitude != null && entry.value?.longitude != null,
)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    entry.value = await entryApi.detail(Number(props.id))
  } catch (error) {
    failed.value = true
    entry.value = null
    ui.error(toMessage(error))
  } finally {
    loading.value = false
  }
}

async function toggleBookmark(): Promise<void> {
  if (!entry.value) return
  try {
    const result = await entryApi.toggleBookmark(entry.value.id)
    entry.value.bookmarked = result.active
    ui.success(result.active ? '已收藏' : '已取消收藏')
  } catch (error) {
    ui.error(toMessage(error))
  }
}

async function remove(): Promise<void> {
  if (!entry.value) return
  try {
    await entryApi.remove(entry.value.id)
    ui.success('日记已删除')
    await router.replace({ name: 'timeline' })
  } catch (error) {
    ui.error(toMessage(error))
  }
}

function openViewer(index: number): void {
  viewerIndex.value = index
  viewerOpen.value = true
}

function openOnMap(): void {
  if (!entry.value) return
  void router.push({ name: 'map', query: { focus: String(entry.value.id) } })
}

onMounted(async () => {
  await load()
  // 只在成功读到内容后才上报,不存在的 id 不该计数。
  // 也不 await:计数失败不影响阅读,不该拖慢首屏。
  if (entry.value) {
    void entryApi.recordView(entry.value.id).catch(() => undefined)
  }
})
</script>

<template>
  <div class="detail">
    <!--
      标题区不给 title:正文自己带一个 h1。
      移动版导航栏里那个小标题是"滚动后缩小"的替代品,桌面版标题区不再吸顶,
      两个标题就会一上一下重复出现。这里只留「返回」和右侧操作。
    -->
    <NavBar back>
      <!--
        操作全部收到页面标题区。
        移动版是底部一条固定操作条(拇指够得着),桌面端把操作放在标题右侧
        更顺手,也省掉了"正文底部要留出操作条高度"那笔账。
      -->
      <template #right>
        <template v-if="entry">
          <button
            class="detail__act"
            :class="{ 'detail__act--on': entry.bookmarked }"
            type="button"
            @click="toggleBookmark"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M6 3.75h12a1 1 0 0 1 1 1v15.5l-7-4.2-7 4.2V4.75a1 1 0 0 1 1-1Z"
                :fill="entry.bookmarked ? 'currentColor' : 'none'"
                :stroke="entry.bookmarked ? 'none' : 'currentColor'"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
            </svg>
            {{ entry.bookmarked ? '已收藏' : '收藏' }}
          </button>

          <router-link
            v-if="entry.canEdit"
            class="btn btn--primary detail__act-btn"
            :to="{ name: 'entry-edit', params: { id: entry.id } }"
          >
            编辑
          </router-link>

          <button v-if="entry.canEdit" class="detail__act detail__act--danger" type="button" @click="confirmOpen = true">
            删除
          </button>
        </template>
      </template>
    </NavBar>

    <div class="page__scroll">
      <!-- 加载骨架:形状按真实版式排,内容到位时几乎无跳动 -->
      <template v-if="loading">
        <div class="detail__body">
          <SkeletonBlock width="80%" height="28px" />
          <SkeletonBlock width="50%" height="14px" />
          <SkeletonBlock height="180px" radius="12px" />
          <SkeletonBlock width="95%" height="15px" />
          <SkeletonBlock width="88%" height="15px" />
          <SkeletonBlock width="70%" height="15px" />
        </div>
      </template>

      <EmptyState
        v-else-if="failed || !entry"
        icon="M12 8v5M12 16.5v.01M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z"
        title="这篇日记打不开了"
        description="它可能已被删除,或者不属于当前账号"
      >
        <template #action>
          <button class="btn btn--secondary" type="button" @click="router.replace({ name: 'timeline' })">
            回到时间线
          </button>
        </template>
      </EmptyState>

      <template v-else>
        <div class="detail__body">
          <h1 class="detail__title user-selectable">{{ entry.title }}</h1>

          <!-- 元信息:日期 · 地点 · 天气 · 心情 -->
          <div class="detail__meta">
            <span class="detail__meta-item">{{ dateText }}</span>
            <span v-if="entry.tripTitle" class="detail__meta-item detail__meta-item--trip">
              {{ entry.tripTitle }}
            </span>
          </div>

          <div v-if="entry.locationName || entry.weather || entry.mood" class="detail__chips">
            <button
              v-if="entry.locationName"
              class="chip"
              :class="{ 'chip--action': hasCoordinate }"
              :type="hasCoordinate ? 'button' : undefined"
              @click="hasCoordinate && openOnMap()"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
                  stroke="currentColor"
                  stroke-width="1.9"
                />
                <circle cx="12" cy="10.5" r="2.2" stroke="currentColor" stroke-width="1.9" />
              </svg>
              {{ entry.locationName }}
              <span v-if="hasCoordinate" class="chip__hint">地图</span>
            </button>

            <span v-if="entry.weather" class="chip">{{ entry.weather }}</span>
            <span v-if="entry.mood" class="chip chip--mood">{{ entry.mood }}</span>
          </div>
        </div>

        <!-- 图片:单张时整宽展示(长图/风光照更好看),多张时用 3 列网格 -->
        <div v-if="entry.photos.length" class="detail__photos" :class="{ 'detail__photos--single': entry.photos.length === 1 }">
          <template v-if="entry.photos.length === 1">
            <img
              class="detail__single"
              :src="entry.photos[0].url"
              :alt="entry.title"
              loading="lazy"
              @click="openViewer(0)"
            />
          </template>
          <div v-else class="detail__grid">
            <button
              v-for="(photo, index) in entry.photos"
              :key="photo.id"
              class="detail__grid-cell"
              type="button"
              @click="openViewer(index)"
            >
              <img class="detail__grid-img" :src="photo.url" alt="" loading="lazy" />
            </button>
          </div>
        </div>

        <div class="detail__body">
          <MarkdownView class="detail__content" :content="entry.content" />

          <div v-if="entry.tags.length" class="detail__tags">
            <button
              v-for="tag in entry.tags"
              :key="tag"
              class="tag"
              type="button"
              @click="router.push({ name: 'timeline', query: { tag } })"
            >
              #{{ tag }}
            </button>
          </div>

          <!-- 页脚:阅读数与更新时间。用次要色,不抢正文 -->
          <footer class="detail__footer">
            <span>{{ entry.viewCount }} 次阅读</span>
            <span v-if="formatRelative(entry.updatedAt) !== formatRelative(entry.createdAt)">
              · 编辑于 {{ formatRelative(entry.updatedAt) }}
            </span>
            <span v-if="!entry.canEdit">· 别人的日记,只能阅读</span>
          </footer>
        </div>
      </template>
    </div>

    <PhotoViewer v-model="viewerOpen" :urls="photoUrls" :index="viewerIndex" />

    <ConfirmDialog
      v-model="confirmOpen"
      title="删除这篇日记?"
      message="日记与它的图片都会消失,无法恢复。"
      confirm-text="删除"
      danger
      @confirm="remove"
    />
  </div>
</template>

<style scoped>
/*
  详情页是阅读页,用 --reading-max:正文一行超过约 40 个中文字,
  眼睛从行尾回到行首时就会串行 —— 这个页面越宽越难读。
*/
.detail {
  --page-max: var(--reading-max);
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
}

/* 标题区的操作按钮:图标 + 文字,和列表页的操作按钮同一套样式 */
.detail__act {
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

.detail__act:hover {
  background: var(--bg-hover);
}

.detail__act--on {
  color: var(--ios-orange);
}

.detail__act--danger {
  background: transparent;
  color: var(--ios-red);
}

.detail__act--danger:hover {
  background: color-mix(in srgb, var(--ios-red) 12%, transparent);
}

.detail__act-btn {
  height: 36px;
  padding: 0 var(--space-5);
  font-size: var(--text-subhead);
}

.detail__body {
  padding: var(--space-4) var(--gutter);
}

.detail__title {
  font-size: var(--text-title1);
  line-height: 1.28;
  font-weight: 700;
  letter-spacing: -0.7px;
}

.detail__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.detail__meta-item--trip {
  color: var(--ios-blue);
}

.detail__meta-item--trip::before {
  content: '·';
  color: var(--label-3);
  margin-right: var(--space-2);
}

.detail__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-full);
  background: var(--bg-fill);
  color: var(--label-2);
  font-size: var(--text-footnote);
}

.chip--action {
  color: var(--ios-blue);
  transition: background var(--dur-fast) var(--ease-standard);
}

.chip--action:hover {
  background: var(--bg-hover);
}

.chip--mood {
  color: var(--ios-orange);
}

.chip__hint {
  font-size: var(--text-caption2);
  padding-left: 4px;
  border-left: 1px solid currentColor;
  opacity: 0.7;
}

/* ---- 图片 ---- */
.detail__photos {
  padding: 0 var(--gutter) var(--space-4);
}

/*
  单图:宽度和高度同时设上限,比例交给浏览器自己算 ——
  max-width / max-height 作用在替换元素上时会保持原始比例,
  取"两种限制里更严的那个"。
  比 width:100% + object-fit:cover 好在竖构图:
  那种写法会把 900×1600 的竖图裁成 712×520,三分之二的内容直接没了。
  现在竖图缩到 520px 高、水平居中,一张都不裁。
*/
.detail__single {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 520px;
  margin: 0 auto;
  border-radius: var(--radius-card);
  cursor: zoom-in;
}

/*
  多张图用自适应网格而不是固定 3 列:列宽写死在 760px 的阅读栏里
  会剩下一格半空着。auto-fill 能让它按可用宽度排满。
*/
.detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-2);
}

.detail__grid-cell {
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--bg-fill);
  border-radius: var(--radius-cell);
  cursor: zoom-in;
}

.detail__grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--dur) var(--ease-ios);
}

.detail__grid-cell:hover .detail__grid-img {
  transform: scale(1.04);
}

/* ---- 正文 ---- */
.detail__content {
  margin-top: var(--space-2);
}

.detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.tag {
  padding: 4px var(--space-3);
  border-radius: var(--radius-full);
  background: color-mix(in srgb, var(--ios-blue) 12%, transparent);
  color: var(--ios-blue);
  font-size: var(--text-footnote);
  transition: background var(--dur-fast) var(--ease-standard);
}

.tag:hover {
  background: color-mix(in srgb, var(--ios-blue) 20%, transparent);
}

.detail__footer {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 0.5px solid var(--separator);
  font-size: var(--text-caption1);
  color: var(--label-3);
}

</style>
