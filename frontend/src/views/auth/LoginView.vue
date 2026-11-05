<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { toast } from '@/utils/toast'
import { USE_MOCK } from '@/api/http'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const mode = ref<'password' | 'sms'>('password')
const form = reactive({ phone: '', password: '', smsCode: '' })
const loading = ref(false)
const cooldown = ref(0)

async function sendSms() {
  if (!/^1\d{10}$/.test(form.phone)) return toast.warning('请输入正确的手机号')
  const r = await userApi.sendSms(form.phone)
  toast.success(r.hint ?? '验证码已发送')
  cooldown.value = 60
  const t = setInterval(() => --cooldown.value <= 0 && clearInterval(t), 1000)
}

async function submit() {
  if (!/^1\d{10}$/.test(form.phone)) return toast.warning('请输入正确的手机号')
  loading.value = true
  try {
    await user.login(mode.value === 'password' ? { phone: form.phone, password: form.password } : { phone: form.phone, smsCode: form.smsCode })
    toast.success(`欢迎回来，${user.profile?.nickname}`)
    router.replace((route.query.redirect as string) || (user.isAdmin ? '/admin' : '/'))
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function fill(kind: 'user' | 'admin') {
  mode.value = 'password'
  Object.assign(form, kind === 'user' ? { phone: '13800000000', password: '123456' } : { phone: '18800000000', password: 'admin123' })
}
</script>

<template>
  <div>
    <div class="tabs">
      <a :class="{ on: mode === 'password' }" @click="mode = 'password'">密码登录</a>
      <a :class="{ on: mode === 'sms' }" @click="mode = 'sms'">短信登录</a>
    </div>
    <form @submit.prevent="submit">
      <div class="field"><input v-model.trim="form.phone" class="input" placeholder="手机号" maxlength="11" autocomplete="username" /></div>
      <div v-if="mode === 'password'" class="field">
        <input v-model="form.password" class="input" type="password" placeholder="密码" autocomplete="current-password" />
      </div>
      <div v-else class="field sms">
        <input v-model.trim="form.smsCode" class="input" placeholder="短信验证码" maxlength="6" />
        <button type="button" class="btn btn-ghost btn-square" :disabled="cooldown > 0" @click="sendSms">{{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}</button>
      </div>
      <button class="btn btn-primary btn-lg btn-block" :disabled="loading">{{ loading ? '登录中…' : '登 录' }}</button>
    </form>
    <div class="links">
      <span class="muted">没有账号？</span>
      <RouterLink :to="{ path: '/register', query: route.query }" class="hl">免费注册</RouterLink>
    </div>
    <div v-if="USE_MOCK" class="demo">
      <p>演示账号（Mock 环境）</p>
      <div>
        <button class="btn btn-sm" @click="fill('user')">普通用户 13800000000 / 123456</button>
        <button class="btn btn-sm" @click="fill('admin')">运营后台 18800000000 / admin123</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 30px;
  margin-bottom: 22px;
  font-size: 18px;
}
.tabs a {
  cursor: pointer;
  color: var(--c-text-3);
  padding-bottom: 6px;
}
.tabs a.on {
  color: var(--c-text);
  font-weight: 700;
  border-bottom: 3px solid var(--c-primary);
}
.input {
  height: 44px;
}
.sms {
  flex-direction: row;
}
.sms .btn {
  height: 44px;
  flex: none;
}
.links {
  margin-top: 14px;
  font-size: 13px;
  text-align: right;
}
.hl {
  color: var(--c-primary);
}
.demo {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--c-border-2);
  font-size: 12px;
  color: var(--c-text-3);
}
.demo div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}
</style>
