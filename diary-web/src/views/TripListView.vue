<script setup lang="ts">
/**
 * 行程列表。
 *
 * 行程是日记的"册子":一次旅行底下挂着若干篇日记。
 * 这一页把册子摊开成一格一格的封面卡,外加一个新建入口。
 *
 * 用全局 trip store 而不是本地 ref:时间线的筛选、编辑器的归属选择
 * 都要用同一份行程列表,集中缓存能避免切页面就重新请求一遍。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import Sheet from '@/components/ios/Sheet.vue'
import SegmentedControl from '@/components/ios/SegmentedControl.vue'
import TripCard from '@/components/trip/TripCard.vue'
import CoverPicker from '@/components/trip/CoverPicker.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { tripApi } from '@/api/trip'
import { toMessage } from '@/api/http'
import { useTripStore } from '@/stores/trips'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const trips = useTripStore()
const ui = useUiStore()

/** all = 全部行程;active = 进行中(不含已归档) */
const scope = ref<'all' | 'active'>('all')

const creating = ref(false)
const newTitle = ref('')
const newVisibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
/** 自定义封面:图片 id 与预览地址。两个都由 CoverPicker 通过 v-model 回填 */
const newCoverId = ref<number | null>(null)
const newCoverUrl = ref<string | null>(null)
const submitting = ref(false)

/** 待删除的行程 id。与对话框的开关分开:对话框只认 boolean,id 是业务状态 */
const pendingDelete = ref<number | null>(null)
const confirmOpen = ref(false)

const visible = computed(() =>
  scope.value === 'all' ? trips.items : trips.items.filter((trip) => !trip.archived),
)

const archivedCount = computed(() => trips.items.filter((trip) => trip.archived).length)

const stats = computed(() => {
  const entryCount = trips.items.reduce((sum, trip) => sum + trip.entryCount, 0)
  return { trips: trips.items.length, entries: entryCount }
})

const canCreate = computed(() => newTitle.value.trim().length > 0 && !submitting.value)

function openCreate(): void {
  newTitle.value = ''
  newVisibility.value = 'PRIVATE'
  newCoverId.value = null
  newCoverUrl.value = null
  creating.value = true
}

async function create(): Promise<void> {
  if (!canCreate.value) return
  submitting.value = true
  try {
    const trip = await tripApi.create({
      title: newTitle.value.trim(),
      visibility: newVisibility.value,
      // 没选封面就不传这个字段(null 是"别动这一项",这里本来就没得动)
      coverPhotoId: newCoverId.value ?? undefined,
    })
    creating.value = false
    await trips.reload()
    ui.success('行程建好了')
    await router.push({ name: 'trip-detail', params: { id: trip.id } })
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(): Promise<void> {
  const id = pendingDelete.value
  pendingDelete.value = null
  if (id == null) return
  try {
    await tripApi.remove(id)
    await trips.reload()
    ui.success('行程已删除')
  } catch (error) {
    ui.error(toMessage(error))
  }
}

/**
 * 露出删除按钮并记下要删哪个。
 *
 * 移动版靠长按卡片弹删除;桌面端没有"长按"这个约定,
 * 改成卡片悬停时右上角浮出一个删除按钮 —— 这是鼠标上通用的做法,
 * 而且光标停在卡上时才出现,不会干扰浏览。
 */
function askDelete(id: number): void {
  pendingDelete.value = id
  confirmOpen.value = true
}
</script>

<template>
  <div class="page">
    <NavBar title="行程">
      <template #right>
        <button class="page__action" type="button" @click="openCreate">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
          </svg>
          新建行程
        </button>
      </template>

      <template #subtitle>
        <p class="page__summary">
          <template v-if="trips.loading">正在读取...</template>
          <template v-else-if="!stats.trips">还没有行程</template>
          <template v-else>{{ stats.trips }} 段行程 · {{ stats.entries }} 篇日记</template>
        </p>
      </template>
    </NavBar>

    <div class="page__scroll">
      <!-- 归档筛选。只有一个存档时有意义,没归档过就不占版面 -->
      <div v-if="archivedCount" class="scope">
        <SegmentedControl
          v-model="scope"
          :options="[
            { value: 'all', label: `全部 ${stats.trips}` },
            { value: 'active', label: `进行中 ${stats.trips - archivedCount}` },
          ]"
        />
      </div>

      <template v-if="trips.loading && !trips.loaded">
        <div class="list">
          <div v-for="n in 3" :key="n" class="skeleton">
            <SkeletonBlock height="200px" radius="12px" />
            <SkeletonBlock width="55%" height="17px" />
            <SkeletonBlock width="35%" height="13px" />
          </div>
        </div>
      </template>

      <template v-else-if="visible.length">
        <div class="list">
          <div v-for="trip in visible" :key="trip.id" class="list__item">
            <TripCard :trip="trip" />

            <button
              class="list__remove"
              type="button"
              :aria-label="`删除行程 ${trip.title}`"
              title="删除行程"
              @click.stop="askDelete(trip.id)"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M4.5 7h15M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7M6.5 7l.9 12a1.5 1.5 0 0 0 1.5 1.4h6.2a1.5 1.5 0 0 0 1.5-1.4l.9-12"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </div>
        </div>
      </template>

      <EmptyState
        v-else
        icon="M4 7.5h16M4 12h16M4 16.5h10"
        :title="archivedCount ? '没有进行中的行程' : '还没有行程'"
        :description="
          archivedCount
            ? '归档的行程都在「全部」里,切回去就能看到'
            : '把一次旅行建成一个行程,日记就能按册归档,地图上也会连成一条路线'
        "
      >
        <template #action>
          <button v-if="archivedCount" class="btn btn--secondary" type="button" @click="scope = 'all'">
            看全部行程
          </button>
          <button v-else class="btn btn--primary" type="button" @click="openCreate">新建行程</button>
        </template>
      </EmptyState>
    </div>

    <!-- 新建行程 -->
    <Sheet v-model="creating" title="新建行程">
      <div class="create">
        <!-- 先定封面再起名:这张图是这本册子的"脸",先挑它更顺 -->
        <CoverPicker v-model="newCoverId" v-model:preview-url="newCoverUrl" />

        <label class="create__field">
          <span class="create__label">名称</span>
          <input
            v-model="newTitle"
            class="field"
            type="text"
            placeholder="比如:京都的秋"
            maxlength="60"
            @keydown.enter.prevent="create"
          />
        </label>

        <div class="create__private">
          <div class="create__private-text">
            <span class="create__private-title">公开这段行程</span>
            <span class="create__private-desc">公开后别人能看到行程下的日记</span>
          </div>
          <button
            class="switch"
            type="button"
            role="switch"
            :aria-checked="newVisibility === 'PUBLIC'"
            :class="{ 'switch--on': newVisibility === 'PUBLIC' }"
            @click="newVisibility = newVisibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'"
          >
            <span class="switch__knob" />
          </button>
        </div>

        <button class="btn btn--primary btn--block" type="button" :disabled="!canCreate" @click="create">
          {{ submitting ? '创建中...' : '创建行程' }}
        </button>
      </div>
    </Sheet>

    <ConfirmDialog
      v-model="confirmOpen"
      title="删除这段行程?"
      message="行程下的日记也会一起删除,无法恢复。"
      confirm-text="删除"
      danger
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-grouped);
}

/* 页面标题区的操作按钮:图标 + 文字,桌面端有位置放得下文字 */
.page__action {
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

.page__action:hover {
  background: var(--bg-hover);
}

.page__summary {
  margin-top: 2px;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/*
  分组控件不能跟着内容栏一起变宽 ——
  1120px 宽的两段式控件既难扫也难点。限到 280px。
*/
.scope {
  max-width: 280px;
  padding: var(--space-1) var(--gutter) var(--space-4);
}

/*
  行程卡片网格。
  auto-fill + minmax 而不是写死列数:窄一点的窗口自动退成两列,
  宽屏上也不会因为列宽固定而两边空一大片。
*/
.list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-5);
  padding: 0 var(--gutter) var(--space-6);
}

.list__item {
  position: relative;
}

/* 悬停时才浮出的删除按钮。压在封面右上角,那儿没有别的角标 */
.list__remove {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.list__item:hover .list__remove,
.list__remove:focus-visible {
  opacity: 1;
}

.list__remove:hover {
  background: var(--ios-red);
}

.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

/* ---- 新建面板 ---- */
.create {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.create__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.create__label {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.create__private {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.create__private-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.create__private-title {
  font-size: var(--text-body);
}

.create__private-desc {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

/* iOS 开关:轨道 51x31,滑块 27 带一点阴影 */
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
