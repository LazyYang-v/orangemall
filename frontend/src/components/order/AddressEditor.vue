<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { Address } from '@/types/domain'
import { userApi } from '@/api/user'
import { toast } from '@/utils/toast'
import Modal from '../common/Modal.vue'

const props = defineProps<{ open: boolean; value?: Address | null }>()
const emit = defineEmits<{ close: []; saved: [Address] }>()
const empty = (): Partial<Address> => ({ receiver: '', phone: '', province: '陕西省', city: '', district: '', detail: '', isDefault: false })
const form = reactive<Partial<Address>>(empty())
watch(() => props.open, (o) => o && Object.assign(form, empty(), props.value ?? {}))

async function save() {
  if (!form.receiver) return toast.warning('请填写收货人')
  if (!/^1\d{10}$/.test(form.phone ?? '')) return toast.warning('请填写正确的手机号')
  if (!form.city || !form.detail) return toast.warning('请填写完整地址')
  const a = await userApi.saveAddress({ ...form })
  toast.success('地址已保存')
  emit('saved', a)
}
</script>

<template>
  <Modal :open="open" :title="value ? '编辑收货地址' : '新增收货地址'" :width="520" @close="emit('close')">
    <div class="grid">
      <div class="field"><label>收货人</label><input v-model.trim="form.receiver" class="input" maxlength="20" /></div>
      <div class="field"><label>手机号</label><input v-model.trim="form.phone" class="input" maxlength="11" /></div>
      <div class="field"><label>省份</label><input v-model.trim="form.province" class="input" /></div>
      <div class="field"><label>城市</label><input v-model.trim="form.city" class="input" placeholder="如：汉中市" /></div>
      <div class="field"><label>区县</label><input v-model.trim="form.district" class="input" /></div>
      <div class="field full"><label>详细地址</label><textarea v-model.trim="form.detail" class="input" rows="2" placeholder="街道、门牌号" /></div>
    </div>
    <label class="def"><input v-model="form.isDefault" type="checkbox" class="checkbox" /> 设为默认地址</label>
    <template #footer>
      <button class="btn" @click="emit('close')">取消</button>
      <button class="btn btn-primary" @click="save">保存</button>
    </template>
  </Modal>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 14px;
}
.full {
  grid-column: span 2;
}
.def {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
}
</style>
