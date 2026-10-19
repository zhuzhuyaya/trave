<script setup lang="ts">
/**
 * 注册页。
 *
 * 与登录页同一套视觉,但字段更多。校验策略是"提交后逐个浮现":
 * 不在输入时就标红 —— 用户还没写完一个邮箱就被判为格式错误,
 * 是很让人恼火的体验。
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
const email = ref('')
const nickname = ref('')
const password = ref('')

const canSubmit = computed(
  () =>
    username.value.trim().length >= 3 &&
    email.value.includes('@') &&
    nickname.value.trim().length >= 1 &&
    password.value.length >= 6,
)

async function submit(): Promise<void> {
  if (!canSubmit.value || auth.loading) return
  try {
    await auth.register({
      username: username.value.trim(),
      email: email.value.trim(),
      nickname: nickname.value.trim(),
      password: password.value,
    })
    ui.success('注册成功,开始记录吧')
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' ? redirect : '/timeline')
  } catch (error) {
    ui.error(toMessage(error))
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth__inner">
      <header class="auth__head">
        <h1 class="auth__title">创建账号</h1>
        <p class="auth__subtitle">你的日记只有你自己能看见</p>
      </header>

      <form class="auth__form" @submit.prevent="submit">
        <div class="group auth__group">
          <label class="auth__field">
            <span class="auth__field-label">用户名</span>
            <input
              v-model="username"
              class="auth__input"
              type="text"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              placeholder="字母、数字、下划线"
            />
          </label>

          <label class="auth__field">
            <span class="auth__field-label">昵称</span>
            <input
              v-model="nickname"
              class="auth__input"
              type="text"
              autocomplete="nickname"
              placeholder="显示在页面上的名字"
            />
          </label>

          <label class="auth__field">
            <span class="auth__field-label">邮箱</span>
            <input
              v-model="email"
              class="auth__input"
              type="email"
              autocomplete="email"
              autocapitalize="none"
              spellcheck="false"
              placeholder="you@example.com"
            />
          </label>

          <label class="auth__field">
            <span class="auth__field-label">密码</span>
            <input
              v-model="password"
              class="auth__input"
              type="password"
              autocomplete="new-password"
              placeholder="至少 6 位"
            />
          </label>
        </div>

        <button class="btn btn--primary btn--block" type="submit" :disabled="!canSubmit || auth.loading">
          {{ auth.loading ? '注册中…' : '注册' }}
        </button>
      </form>

      <p class="auth__foot">
        已经有账号了?
        <router-link class="auth__link" :to="{ name: 'login', query: route.query }">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
/* 与登录页共用一套类名,样式也保持一致 —— 两个页面在视觉上是同一个流程的两步 */
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

.auth__head {
  text-align: center;
  margin-bottom: var(--space-6);
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
  font-size: var(--text-subhead);
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
