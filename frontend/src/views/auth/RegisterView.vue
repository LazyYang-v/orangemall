<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const form = reactive({ phone: '', smsCode: '', password: '', confirm: '', nickname: '', agree: false })
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
  if (form.password.length < 6) return toast.warning('密码至少 6 位')
  if (form.password !== form.confirm) return toast.warning('两次密码不一致')
  if (!form.agree) return toast.warning('请先阅读并同意用户协议')
  loading.value = true
  try {
    await user.register({ phone: form.phone, password: form.password, nickname: form.nickname, smsCode: form.smsCode })
    toast.success('注册成功，已发放 10 元无门槛券 + 200 积分')
    router.replace((route.query.redirect as string) || '/')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <h3 class="title">注册橙选账号</h3>
    <form @submit.prevent="submit">
      <div class="field"><input v-model.trim="form.phone" class="input" placeholder="手机号" maxlength="11" /></div>
      <div class="field sms">
        <input v-model.trim="form.smsCode" class="input" placeholder="短信验证码" maxlength="6" />
        <button type="button" class="btn btn-ghost btn-square" :disabled="cooldown > 0" @click="sendSms">{{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}</button>
      </div>
      <div class="field"><input v-model.trim="form.nickname" class="input" placeholder="昵称（选填）" maxlength="16" /></div>
      <div class="field"><input v-model="form.password" class="input" type="password" placeholder="设置密码（至少 6 位）" autocomplete="new-password" /></div>
      <div class="field"><input v-model="form.confirm" class="input" type="password" placeholder="确认密码" autocomplete="new-password" /></div>
      <label class="agree"><input v-model="form.agree" type="checkbox" class="checkbox" /> 我已阅读并同意《用户协议》《隐私政策》</label>
      <button class="btn btn-primary btn-lg btn-block" :disabled="loading">{{ loading ? '注册中…' : '同意并注册' }}</button>
    </form>
    <div class="links">
      <span class="muted">已有账号？</span>
      <RouterLink :to="{ path: '/login', query: route.query }" class="hl">立即登录</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.title {
  font-size: 20px;
  margin-bottom: 18px;
}
.field {
  margin-bottom: 12px;
}
.input {
  height: 42px;
}
.sms {
  flex-direction: row;
}
.sms .btn {
  height: 42px;
  flex: none;
  width: 110px;
}
.agree {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--c-text-2);
  margin: 4px 0 16px;
  cursor: pointer;
}
.links {
  margin-top: 14px;
  font-size: 13px;
  text-align: right;
}
.hl {
  color: var(--c-primary);
}
</style>
