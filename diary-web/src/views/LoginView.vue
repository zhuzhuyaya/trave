<script setup lang="ts">
/**
 * 登录页。
 *
 * 版式对齐 iOS 的登录表单:居中一个圆形 Logo、一个圆角分组表单、
 * 一个通栏蓝色主按钮,下面一行"还没有账号?注册"。
 *
 * 键盘弹起时按钮不该被顶飞 —— 这里让整个表单随内容滚动,
 * 而不是把它固定在底部。
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const username = ref('')
const password = ref('')
const showPassword = ref(false)

const canSubmit = computed(() => username.value.trim().length > 0 && password.value.length > 0)

async function submit(): Promise<void> {
  if (!canSubmit.value || auth.loading) return
  try {
    await auth.login({ username: username.value.trim(), password: password.value })
    ui.success(`欢迎回来,${auth.displayName}`)
    // 登录后跳回原本想去的页面;没有来路就去时间线
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/timeline')
  } catch (error) {
    ui.error(toMessage(error))
  }
}

/** 演示账号一键填充,省去每次手动输入 */
function fillDemo(): void {
  username.value = 'traveler'
  password.value = 'diary123'
}
</script>

<template>
  <div class="auth">
    <div class="auth__inner">
      <div class="auth__brand">
        <div class="auth__logo">
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="10.5" r="2.2" stroke="currentColor" stroke-width="1.7" />
          </svg>
        </div>
        <h1 class="auth__title">旅行日记</h1>
        <p class="auth__subtitle">把走过的路,写成能翻回去的文字</p>
      </div>

      <form class="auth__form" @submit.prevent="submit">
        <div class="group auth__group">
          <label class="auth__field">
            <span class="auth__field-label">用户名</span>
            <input
              v-model="username"
              class="auth__input"
              type="text"
              name="username"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              placeholder="traveler"
            />
          </label>

          <label class="auth__field">
            <span class="auth__field-label">密码</span>
            <span class="auth__password">
              <input
                v-model="password"
                class="auth__input"
                :type="showPassword ? 'text' : 'password'"
                name="password"
                autocomplete="current-password"
                placeholder="diary123"
              />
              <button
                class="auth__toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <template v-if="showPassword">
                    <path
                      d="M3 3l18 18M10.6 10.7a2 2 0 0 0 2.8 2.8"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                    <path
                      d="M6.6 6.7C4.6 8 3.2 10 2.5 12c1.4 3.6 5.2 6 9.5 6 1.5 0 3-.3 4.3-.9M9.9 6.3A9.6 9.6 0 0 1 12 6c4.3 0 8.1 2.4 9.5 6-.4 1-1 1.9-1.7 2.7"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                  </template>
                  <template v-else>
                    <path
                      d="M2.5 12C3.9 8.4 7.7 6 12 6s8.1 2.4 9.5 6c-1.4 3.6-5.2 6-9.5 6s-8.1-2.4-9.5-6Z"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linejoin="round"
                    />
                    <circle cx="12" cy="12" r="2.6" stroke="currentColor" stroke-width="1.8" />
                  </template>
                </svg>
              </button>
            </span>
          </label>
        </div>

        <button class="btn btn--primary btn--block" type="submit" :disabled="!canSubmit || auth.loading">
          {{ auth.loading ? '登录中…' : '登录' }}
        </button>

        <button class="auth__demo" type="button" @click="fillDemo">
          使用演示账号 traveler / diary123
        </button>
      </form>

      <p class="auth__foot">
        还没有账号?
        <router-link class="auth__link" :to="{ name: 'register', query: route.query }">
          注册一个
        </router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
/* 登录页没有导航栏,整屏是一张居中的卡片 */
.auth {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--gutter);
  background: var(--bg-grouped);
}

.auth__inner {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  border-radius: 20px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
  border: 0.5px solid var(--separator);
}

.auth__brand {
  text-align: center;
  margin-bottom: var(--space-8);
}

.auth__logo {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--space-4);
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(150deg, var(--ios-teal), var(--ios-blue) 60%, var(--ios-indigo));
  box-shadow: 0 8px 24px rgba(0, 122, 255, 0.28);
}

.auth__title {
  font-size: var(--text-title1);
  line-height: var(--lh-title1);
  font-weight: 700;
  letter-spacing: -0.6px;
}

.auth__subtitle {
  margin-top: var(--space-1);
  font-size: var(--text-subhead);
  line-height: var(--lh-subhead);
  color: var(--label-2);
}

.auth__group {
  margin: 0 0 var(--space-5);
}

.auth__field {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  min-height: 48px;
  position: relative;
}

.auth__field:not(:last-child)::after {
  content: '';
  position: absolute;
  left: var(--space-4);
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--separator);
  transform: scaleY(0.5);
}

.auth__field-label {
  width: 56px;
  flex-shrink: 0;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/* 输入框本身无边框无底色 —— 整行才是输入区,这是 iOS 表单的做法 */
.auth__input {
  flex: 1;
  min-width: 0;
  height: 48px;
  border: none;
  background: transparent;
  font-size: var(--text-body);
  outline: none;
}

.auth__input::placeholder {
  color: var(--label-3);
}

.auth__password {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.auth__toggle {
  color: var(--label-3);
  padding: var(--space-2);
  margin-right: calc(var(--space-2) * -1);
  transition: color var(--dur-fast) var(--ease-standard);
}

.auth__toggle:hover {
  color: var(--label);
}

.auth__demo {
  width: 100%;
  margin-top: var(--space-3);
  padding: var(--space-2);
  font-size: var(--text-footnote);
  color: var(--label-2);
  transition: color var(--dur-fast) var(--ease-standard);
}

.auth__demo:hover {
  color: var(--ios-blue);
}

.auth__foot {
  margin-top: var(--space-6);
  text-align: center;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

.auth__link {
  color: var(--ios-blue);
  font-weight: 500;
}
</style>
