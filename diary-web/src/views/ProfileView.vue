<script setup lang="ts">
/**
 * 我的。
 *
 * 桌面版排成两栏:左边一张贴顶的用户卡(头像 + 昵称 + 一行统计),
 * 右边是几个白色圆角分组。宽屏上把"我是谁"固定在视线里,
 * 滚动下面那串设置项时它还一直在,比跟着一起滚走有用。
 *
 * 三个分组分别是:
 *   内容 —— 行程、日记、足迹的入口与计数
 *   账号 —— 昵称与签名
 *   其他 —— 退出登录(红色,单独一组,避免误触)
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import ListRow from '@/components/ios/ListRow.vue'
import Sheet from '@/components/ios/Sheet.vue'
import Avatar from '@/components/ui/Avatar.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { profileApi } from '@/api/auth'
import { insightApi } from '@/api/insight'
import { toMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useTripStore } from '@/stores/trips'
import { useUiStore } from '@/stores/ui'
import type { StatsVO } from '@/api/types'
import { formatDateCN } from '@/utils/time'

const router = useRouter()
const auth = useAuthStore()
const trips = useTripStore()
const ui = useUiStore()

const stats = ref<StatsVO | null>(null)

const editing = ref(false)
const nickname = ref('')
const bio = ref('')
const saving = ref(false)

const logoutOpen = ref(false)

const statsLine = computed(() => {
  const s = stats.value
  if (!s) return '正在读取...'
  return `${s.entryCount} 篇日记 · ${s.placeCount} 个地方 · ${s.photoCount} 张照片`
})

const joinedAt = computed(() => {
  const created = auth.user?.createdAt
  return created ? `${formatDateCN(created)} 加入` : ''
})

const canSave = computed(() => nickname.value.trim().length > 0 && !saving.value)

onMounted(async () => {
  trips.ensureLoaded()
  try {
    stats.value = await insightApi.stats()
  } catch {
    // 统计失败不影响这一页的主要功能(看资料 / 退出),静默降级
  }
})

function openEdit(): void {
  nickname.value = auth.user?.nickname ?? ''
  bio.value = auth.user?.bio ?? ''
  editing.value = true
}

async function save(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  try {
    const updated = await profileApi.update({
      nickname: nickname.value.trim(),
      bio: bio.value.trim(),
    })
    auth.setUser(updated)
    editing.value = false
    ui.success('已保存')
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    saving.value = false
  }
}

async function doLogout(): Promise<void> {
  await auth.logout()
  trips.invalidate()
  ui.success('已退出登录')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <NavBar title="我的" />

    <div class="page__scroll">
      <div class="layout">
        <!-- 左栏:贴顶的用户卡 -->
        <aside class="layout__side">
          <section class="profile">
            <Avatar :name="auth.displayName" :src="auth.user?.avatar" :size="64" />

            <div class="profile__text">
              <h2 class="profile__name ellipsis">{{ auth.displayName || '旅行者' }}</h2>
              <p class="profile__username ellipsis">@{{ auth.username }}</p>
            </div>

            <button class="profile__edit" type="button" aria-label="编辑资料" title="编辑资料" @click="openEdit">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M4 20h4l10-10a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20Z"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </section>

          <p v-if="auth.user?.bio" class="profile__bio user-selectable">{{ auth.user.bio }}</p>

          <p class="profile__stats">{{ statsLine }}</p>
          <p v-if="joinedAt" class="profile__joined">{{ joinedAt }}</p>
        </aside>

        <!-- 右栏:设置分组 -->
        <div class="layout__main">
          <div class="group">
            <ListRow
              first
              clickable
              title="我的行程"
              :value="stats ? `${stats.tripCount} 段` : ''"
              @click="router.push({ name: 'trips' })"
            />
            <ListRow
              clickable
              title="写过的日记"
              :value="stats ? `${stats.entryCount} 篇` : ''"
              @click="router.push({ name: 'timeline' })"
            />
            <ListRow
              clickable
              title="我的收藏"
              @click="router.push({ name: 'bookmarks' })"
            />
            <ListRow
              last
              clickable
              title="足迹地图"
              :value="stats ? `${stats.placeCount} 个地方` : ''"
              @click="router.push({ name: 'map' })"
            />
          </div>
          <p class="group-footer">归类到行程的日记会在地图上连成一条路线。</p>

          <div class="group">
            <ListRow clickable title="昵称" :value="auth.displayName" @click="openEdit" />
            <ListRow
              last
              clickable
              title="个性签名"
              :subtitle="auth.user?.bio ?? '还没有签名'"
              @click="openEdit"
            />
          </div>

          <div class="group">
            <ListRow first title="版本" value="1.0.0" />
            <ListRow last title="账号角色" :value="auth.isAdmin ? '管理员' : '普通用户'" />
          </div>

          <!-- 退出:单独一组,红色。分组本身就是"这里要小心"的视觉信号 -->
          <div class="group">
            <ListRow last clickable danger title="退出登录" @click="logoutOpen = true" />
          </div>

          <p class="page__foot">旅行日记 · 把走过的路写成能翻回去的文字</p>
        </div>
      </div>
    </div>

    <!-- ---- 编辑资料 ---- -->
    <Sheet v-model="editing" title="编辑资料">
      <div class="form">
        <label class="form__field">
          <span class="form__label">昵称</span>
          <input v-model="nickname" class="field" type="text" maxlength="20" placeholder="显示在页面上的名字" />
        </label>

        <label class="form__field">
          <span class="form__label">个性签名</span>
          <textarea
            v-model="bio"
            class="form__area"
            rows="3"
            maxlength="80"
            placeholder="一句话介绍自己,或者一句常说的话"
          />
        </label>

        <button class="btn btn--primary btn--block" type="button" :disabled="!canSave" @click="save">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </Sheet>

    <ConfirmDialog
      v-model="logoutOpen"
      title="退出登录?"
      message="本地保存的登录状态会被清除,下次需要重新输入密码。"
      confirm-text="退出"
      danger
      @confirm="doLogout"
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

/* 左栏固定宽,右栏吃掉剩余宽度。minmax(0,1fr) 让右栏可以被压缩,
   否则里面的长文本会顶破网格。 */
.layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  /* 间距给得小一点:右栏的 .group 自己还带着 --gutter 的左边距,
     两者相加才是它在版面上看到的间隙 */
  gap: var(--space-3);
  align-items: start;
  padding: var(--space-2) 0 0;
}

/* 滚动右栏时用户卡一直贴在标题区下方 */
.layout__side {
  position: sticky;
  top: calc(var(--sticky-offset) + var(--space-4));
  padding: var(--space-5);
  margin-left: var(--gutter);
  border-radius: var(--radius-card);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
}

.layout__main {
  min-width: 0;
}

/* ---- 用户卡 ---- */
.profile {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.profile__text {
  flex: 1;
  min-width: 0;
}

.profile__name {
  font-size: var(--text-title3);
  line-height: var(--lh-title3);
  font-weight: 700;
  letter-spacing: -0.3px;
}

.profile__username {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.profile__edit {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--bg-fill);
  color: var(--ios-blue);
  transition: background var(--dur-fast) var(--ease-standard);
}

.profile__edit:hover {
  background: var(--bg-hover);
}

.profile__bio {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 0.5px solid var(--separator);
  font-size: var(--text-subhead);
  line-height: 1.6;
  color: var(--label-2);
}

.profile__stats {
  margin-top: var(--space-4);
  font-size: var(--text-footnote);
  line-height: 1.6;
  color: var(--label-2);
}

.profile__joined {
  margin-top: 2px;
  font-size: var(--text-caption1);
  color: var(--label-3);
}

.page__foot {
  margin: var(--space-8) 0 0;
  text-align: center;
  font-size: var(--text-caption1);
  color: var(--label-3);
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
</style>
