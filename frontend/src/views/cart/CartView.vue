<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { CartCheckIssue, CartItem } from '@/types/domain'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { cartApi } from '@/api/cart'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import ProductArt from '@/components/common/ProductArt.vue'
import QtyStepper from '@/components/common/QtyStepper.vue'
import Empty from '@/components/common/Empty.vue'
import Icon from '@/components/common/Icon.vue'
import Modal from '@/components/common/Modal.vue'

const cart = useCartStore()
const user = useUserStore()
const router = useRouter()
const issues = ref<CartCheckIssue[]>([])
const checking = ref(false)

onMounted(() => cart.load())

const shops = computed(() => {
  const map = new Map<string, CartItem[]>()
  cart.valid.forEach((i) => map.set(i.shopName, [...(map.get(i.shopName) ?? []), i]))
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})
const invalid = computed(() => cart.items.filter((i) => i.status !== 'NORMAL'))
const fullReduction = computed(() => Math.min(5, Math.floor(cart.checkedAmount / 30000)) * 5000)
const gap = computed(() => 30000 - (cart.checkedAmount % 30000))

function shopChecked(items: CartItem[]) {
  return items.every((i) => i.checked)
}
async function toggleShop(items: CartItem[], v: boolean) {
  await Promise.all(items.map((i) => cart.update(i.skuId, { checked: v })))
}
async function setCount(i: CartItem, n: number) {
  try {
    await cart.update(i.skuId, { count: n })
  } catch (e) {
    toast.error((e as Error).message)
  }
}
async function remove(ids: string[]) {
  if (!ids.length) return toast.warning('请选择要删除的商品')
  await cart.remove(ids)
  toast.success('已删除')
}

/** 结算前预校验：商品状态 + 库存（防止进入结算页才发现失效） */
async function checkout() {
  if (!cart.checked.length) return toast.warning('请选择要结算的商品')
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: '/cart' } })
  checking.value = true
  try {
    issues.value = await cartApi.check(cart.checked.map((i) => ({ skuId: i.skuId, count: i.count })))
    if (issues.value.length) return
    router.push({ path: '/checkout', query: { items: cart.checked.map((i) => `${i.skuId}:${i.count}`).join(','), from: 'cart' } })
  } finally {
    checking.value = false
  }
}
const issueItem = (skuId: string) => cart.items.find((i) => i.skuId === skuId)
</script>

<template>
  <div class="container page">
    <div class="head">
      <h2>购物车 <small class="muted">（全部 {{ cart.count }}）</small></h2>
      <span v-if="!user.isLogin" class="guest">
        <Icon name="alert" :size="14" /> 当前未登录，购物车暂存在本机。<RouterLink :to="{ path: '/login', query: { redirect: '/cart' } }">登录</RouterLink>后自动合并到账号
      </span>
    </div>

    <Empty v-if="!cart.loading && !cart.items.length" icon="cart" text="购物车空空如也，去挑点好物吧">
      <RouterLink to="/" class="btn btn-primary">去逛逛</RouterLink>
    </Empty>

    <template v-else>
      <div class="thead card">
        <label class="c-check"><input type="checkbox" class="checkbox" :checked="cart.allChecked" @change="cart.checkAll(($event.target as HTMLInputElement).checked)" /> 全选</label>
        <span class="c-info">商品信息</span>
        <span class="c-price">单价</span>
        <span class="c-qty">数量</span>
        <span class="c-sum">小计</span>
        <span class="c-op">操作</span>
      </div>

      <div v-for="s in shops" :key="s.name" class="shop card">
        <div class="shop-head">
          <input type="checkbox" class="checkbox" :checked="shopChecked(s.items)" @change="toggleShop(s.items, ($event.target as HTMLInputElement).checked)" />
          <Icon name="store" :size="16" />
          <b>{{ s.name }}</b>
          <span class="tag tag-red">跨店满减</span>
        </div>
        <div v-for="i in s.items" :key="i.skuId" class="row" :class="{ on: i.checked }">
          <span class="c-check"><input type="checkbox" class="checkbox" :checked="i.checked" @change="cart.update(i.skuId, { checked: ($event.target as HTMLInputElement).checked })" /></span>
          <RouterLink :to="`/product/${i.spuId}`" class="c-info">
            <ProductArt :kind="i.art" :tint="i.tint" :size="84" bg />
            <div>
              <p class="name ellipsis-2">{{ i.name }}</p>
              <p class="spec">{{ i.specText }}</p>
              <span v-if="i.stock <= 5" class="tag tag-red">仅剩 {{ i.stock }} 件</span>
            </div>
          </RouterLink>
          <span class="c-price">
            <b class="num">¥{{ yuan(i.price, true) }}</b>
            <s v-if="i.originalPrice > i.price" class="price-del">¥{{ yuan(i.originalPrice, true) }}</s>
          </span>
          <span class="c-qty"><QtyStepper :model-value="i.count" :max="Math.min(99, i.stock)" small @update:model-value="(n) => setCount(i, n)" /></span>
          <span class="c-sum price">¥{{ yuan(i.price * i.count, true) }}</span>
          <span class="c-op"><a @click="remove([i.skuId])">删除</a></span>
        </div>
      </div>

      <div v-if="invalid.length" class="shop card invalid">
        <div class="shop-head">
          <b>失效商品 {{ invalid.length }} 件</b>
          <a class="clear" @click="remove(invalid.map((i) => i.skuId))">清空失效商品</a>
        </div>
        <div v-for="i in invalid" :key="i.skuId" class="row">
          <span class="c-check"><span class="tag tag-gray">失效</span></span>
          <div class="c-info">
            <ProductArt :kind="i.art" :tint="i.tint" :size="84" bg style="filter: grayscale(1); opacity: 0.6" />
            <div>
              <p class="name ellipsis-2">{{ i.name }}</p>
              <p class="spec">{{ i.specText }}</p>
            </div>
          </div>
          <span class="c-price muted">{{ i.status === 'NO_STOCK' ? '该规格已售罄' : '商品已下架' }}</span>
          <span class="c-qty" />
          <span class="c-sum" />
          <span class="c-op"><a @click="remove([i.skuId])">删除</a></span>
        </div>
      </div>

      <div class="bar card">
        <label class="c-check"><input type="checkbox" class="checkbox" :checked="cart.allChecked" @change="cart.checkAll(($event.target as HTMLInputElement).checked)" /> 全选</label>
        <a class="op" @click="remove(cart.checked.map((i) => i.skuId))">删除选中</a>
        <span class="tip" v-if="cart.checkedAmount > 0">
          <template v-if="fullReduction < 25000">再买 <b>¥{{ yuan(gap) }}</b> 可再减 ¥50，</template>
          <RouterLink to="/search">去凑单 <Icon name="chevron-right" :size="12" /></RouterLink>
        </span>
        <div class="total">
          <span>已选 <b class="hl num">{{ cart.checkedCount }}</b> 件</span>
          <span>合计（不含运费）：<span class="price big">¥{{ yuan(Math.max(0, cart.checkedAmount - fullReduction), true) }}</span></span>
          <p v-if="cart.checkedAmount" class="saved">
            已省 ¥{{ yuan(cart.checkedSaved + fullReduction, true) }}<template v-if="fullReduction">（含满减 ¥{{ yuan(fullReduction) }}）</template>，结算页可继续用券/积分
          </p>
        </div>
        <button class="btn btn-primary btn-lg go" :disabled="checking || !cart.checked.length" @click="checkout">{{ checking ? '校验中…' : '去结算' }}</button>
      </div>
    </template>

    <Modal :open="issues.length > 0" title="以下商品暂时无法结算" @close="issues = []">
      <ul class="issues">
        <li v-for="it in issues" :key="it.skuId">
          <ProductArt v-if="issueItem(it.skuId)" :kind="issueItem(it.skuId)!.art" :tint="issueItem(it.skuId)!.tint" :size="48" bg />
          <span class="ellipsis">{{ issueItem(it.skuId)?.name }}</span>
          <b>{{ it.message }}</b>
        </li>
      </ul>
      <template #footer>
        <button class="btn" @click="issues = []">返回修改</button>
        <button class="btn btn-primary" @click="cart.load(); issues = []">刷新购物车</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  gap: 20px;
  margin: 8px 0 14px;
}
.head h2 {
  font-size: 22px;
}
.guest {
  font-size: 13px;
  color: var(--c-warning);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.guest a {
  color: var(--c-primary);
  text-decoration: underline;
}
.thead,
.row,
.bar {
  display: flex;
  align-items: center;
}
.thead {
  padding: 12px 20px;
  color: var(--c-text-2);
  font-size: 13px;
  margin-bottom: 12px;
}
.c-check {
  width: 70px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.c-info {
  flex: 1;
  display: flex;
  gap: 14px;
  min-width: 0;
}
.c-price {
  width: 140px;
  display: flex;
  flex-direction: column;
}
.c-qty {
  width: 150px;
}
.c-sum {
  width: 130px;
  font-size: 16px;
}
.c-op {
  width: 70px;
  text-align: center;
}
.c-op a {
  cursor: pointer;
  color: var(--c-text-2);
}
.c-op a:hover {
  color: var(--c-red);
}
.shop {
  margin-bottom: 12px;
  overflow: hidden;
}
.shop-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--c-border);
}
.row {
  padding: 16px 20px;
  border-bottom: 1px solid var(--c-border);
}
.row:last-child {
  border-bottom: none;
}
.row.on {
  background: #fffaf6;
}
.name {
  font-size: 13px;
  max-width: 340px;
}
.spec {
  font-size: 12px;
  color: var(--c-text-3);
  margin: 4px 0;
}
.invalid .shop-head {
  justify-content: space-between;
  color: var(--c-text-3);
}
.clear {
  cursor: pointer;
  color: var(--c-primary);
  font-size: 13px;
}
.bar {
  position: sticky;
  bottom: 0;
  padding: 0 0 0 20px;
  height: 64px;
  gap: 20px;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
  z-index: 10;
}
.op {
  cursor: pointer;
  color: var(--c-text-2);
}
.tip {
  font-size: 12px;
  color: var(--c-text-2);
}
.tip b {
  color: var(--c-red);
}
.tip a {
  color: var(--c-primary);
}
.total {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: baseline;
  column-gap: 18px;
  font-size: 13px;
}
.hl {
  color: var(--c-primary);
  font-size: 16px;
}
.big {
  font-size: 24px;
}
.saved {
  width: 100%;
  text-align: right;
  font-size: 12px;
  color: var(--c-primary);
}
.go {
  height: 64px;
  border-radius: 0;
  width: 150px;
  font-size: 18px;
}
.issues li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--c-border);
}
.issues span {
  flex: 1;
}
.issues b {
  color: var(--c-red);
  font-size: 13px;
  flex: none;
}
@media (max-width: 900px) {
  .thead { display: none; }
  .row { flex-wrap: wrap; row-gap: 8px; padding: 12px; }
  .c-check { width: 32px; }
  .c-info { flex: 1 1 calc(100% - 32px); }
  .c-price { display: none; }
  .c-qty, .c-sum, .c-op { width: auto; }
  .c-qty { margin-left: 32px; }
  .c-sum { margin-left: auto; }
  .bar { padding-left: 12px; gap: 10px; height: auto; min-height: 56px; }
  .bar .op, .bar .clear { display: none; }
  .total { column-gap: 8px; }
  .big { font-size: 18px; }
  .go { width: 96px; height: 56px; font-size: 16px; padding: 0; }
}
@media (max-width: 900px) {
  .bar .c-check { width: auto; white-space: nowrap; }
  .bar .tip { display: none; }
  .saved { display: none; }
}
</style>
