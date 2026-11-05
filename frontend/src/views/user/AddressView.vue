<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Address } from '@/types/domain'
import { userApi } from '@/api/user'
import { toast } from '@/utils/toast'
import { maskPhone } from '@/utils/format'
import AddressEditor from '@/components/order/AddressEditor.vue'
import Icon from '@/components/common/Icon.vue'

const list = ref<Address[]>([])
const editing = ref<Address | null>(null)
const open = ref(false)
async function load() {
  list.value = await userApi.addresses()
}
onMounted(load)
function edit(a: Address | null) {
  editing.value = a
  open.value = true
}
async function del(a: Address) {
  if (!confirm('确定删除该地址？')) return
  await userApi.deleteAddress(a.id)
  toast.success('已删除')
  load()
}
async function setDefault(a: Address) {
  await userApi.saveAddress({ ...a, isDefault: true })
  load()
}
</script>

<template>
  <div class="card wrap">
    <h3>收货地址 <button class="btn btn-primary btn-sm" @click="edit(null)"><Icon name="plus" :size="14" /> 新增地址</button></h3>
    <div v-for="a in list" :key="a.id" class="addr">
      <div class="info">
        <b>{{ a.receiver }}</b><span class="num">{{ maskPhone(a.phone) }}</span><span v-if="a.isDefault" class="tag tag-solid">默认</span>
        <p>{{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}</p>
      </div>
      <div class="ops">
        <a v-if="!a.isDefault" @click="setDefault(a)">设为默认</a>
        <a @click="edit(a)">编辑</a>
        <a @click="del(a)">删除</a>
      </div>
    </div>
    <p v-if="!list.length" class="muted">还没有收货地址</p>
    <AddressEditor :open="open" :value="editing" @close="open = false" @saved="open = false; load()" />
  </div>
</template>

<style scoped>
.wrap {
  padding: 18px 22px;
}
h3 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  margin-bottom: 10px;
}
.addr {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--c-border);
}
.info {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.info p {
  width: 100%;
  color: var(--c-text-2);
  font-size: 13px;
}
.ops {
  display: flex;
  gap: 16px;
  font-size: 13px;
}
.ops a {
  cursor: pointer;
  color: var(--c-text-2);
}
.ops a:hover {
  color: var(--c-primary);
}
</style>
