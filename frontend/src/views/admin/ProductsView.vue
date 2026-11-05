<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { Sku, Spu } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import ProductArt from '@/components/common/ProductArt.vue'
import Modal from '@/components/common/Modal.vue'

const q = reactive({ keyword: '', status: '' })
const list = ref<Spu[]>([])
const expanded = ref<string | null>(null)
async function load() {
  list.value = await adminApi.products(q)
}
onMounted(load)

async function toggle(s: Spu) {
  const next = s.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  await adminApi.setProductStatus(s.id, next)
  toast.success(`${next === 'ON_SALE' ? '已上架' : '已下架'}，缓存 product:detail:${s.id} 已延迟双删`)
  load()
}

const editing = ref<{ spu: Spu; sku: Sku } | null>(null)
const form = reactive({ price: '', stock: '' })
function edit(spu: Spu, sku: Sku) {
  editing.value = { spu, sku }
  form.price = yuan(sku.price, true)
  form.stock = String(sku.stock)
}
async function save() {
  if (!editing.value) return
  const price = Math.round(Number(form.price) * 100)
  const stock = Number(form.stock)
  if (!(price > 0) || !Number.isInteger(stock) || stock < 0) return toast.warning('请输入合法的价格与库存')
  await adminApi.updateSku(editing.value.sku.id, { price, stock })
  toast.success('已更新：先写 DB → 删缓存 → 500ms 后再删一次')
  editing.value = null
  load()
}
const specText = (k: Sku) => Object.values(k.specs).join(' / ') || '默认'
const totalStock = (s: Spu) => s.skus.reduce((a, k) => a + k.stock, 0)
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>商品 / SKU</h2><span class="sub">SPU {{ list.length }} 个 · 修改价格/库存触发 Cache Aside + 延迟双删，可在「链路日志」查看</span></div>
    <div class="adm-card">
      <div class="adm-toolbar">
        <input v-model.trim="q.keyword" class="input" style="width: 260px" placeholder="商品名 / SPU ID" @keyup.enter="load" />
        <select v-model="q.status" @change="load">
          <option value="">全部状态</option>
          <option value="ON_SALE">在售</option>
          <option value="OFF_SALE">已下架</option>
        </select>
        <button class="btn btn-primary btn-sm btn-square" @click="load">查询</button>
      </div>
      <table class="table">
        <thead><tr><th style="width: 34%">商品</th><th>类目</th><th>品牌</th><th>最低价</th><th>总库存</th><th>销量</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <template v-for="s in list" :key="s.id">
            <tr>
              <td>
                <div class="goods">
                  <ProductArt :kind="s.art" :tint="s.tint" :size="44" bg />
                  <div class="nm"><p class="ellipsis">{{ s.name }}</p><span class="mono muted">{{ s.id }}</span></div>
                </div>
              </td>
              <td class="muted">{{ s.categoryPath.join(' / ') }}</td>
              <td>{{ s.brandName }}</td>
              <td class="price">¥{{ yuan(s.minPrice) }}</td>
              <td class="num" :class="{ low: totalStock(s) < 20 }">{{ totalStock(s) }}</td>
              <td class="num">{{ s.sales }}</td>
              <td><span class="tag" :class="s.status === 'ON_SALE' ? 'tag-green' : 'tag-gray'">{{ s.status === 'ON_SALE' ? '在售' : '已下架' }}</span></td>
              <td>
                <button class="op" @click="expanded = expanded === s.id ? null : s.id">SKU（{{ s.skus.length }}）</button>
                <button class="op" :class="{ danger: s.status === 'ON_SALE' }" @click="toggle(s)">{{ s.status === 'ON_SALE' ? '下架' : '上架' }}</button>
              </td>
            </tr>
            <tr v-if="expanded === s.id" class="sub-row">
              <td colspan="8">
                <table class="table inner">
                  <thead><tr><th>SKU ID</th><th>规格</th><th>售价</th><th>划线价</th><th>库存</th><th>销量</th><th></th></tr></thead>
                  <tbody>
                    <tr v-for="k in s.skus" :key="k.id">
                      <td class="mono">{{ k.id }}</td>
                      <td>{{ specText(k) }}</td>
                      <td class="price">¥{{ yuan(k.price, true) }}</td>
                      <td class="price-del">¥{{ yuan(k.originalPrice, true) }}</td>
                      <td class="num" :class="{ low: k.stock < 10 }">{{ k.stock }}</td>
                      <td class="num">{{ k.sales }}</td>
                      <td><button class="op" @click="edit(s, k)">改价 / 改库存</button></td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <Modal :open="!!editing" title="编辑 SKU" @close="editing = null">
      <p class="muted" style="margin-bottom: 12px">{{ editing?.spu.name }} · {{ editing && specText(editing.sku) }}</p>
      <div class="field"><label>售价（元）</label><input v-model="form.price" class="input" type="number" step="0.01" min="0" /></div>
      <div class="field"><label>库存</label><input v-model="form.stock" class="input" type="number" step="1" min="0" /></div>
      <p class="muted" style="font-size: 12px">提交后：UPDATE pms_sku → DEL product:detail:{spuId} → 500ms 后再次 DEL</p>
      <template #footer>
        <button class="btn" @click="editing = null">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.goods {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nm {
  min-width: 0;
}
.low {
  color: var(--c-red);
  font-weight: 700;
}
.sub-row > td {
  background: #fafafb !important;
  padding: 4px 16px 14px 64px;
}
.inner th {
  background: #f1f2f5;
}
.inner td {
  background: #fff;
}
</style>
