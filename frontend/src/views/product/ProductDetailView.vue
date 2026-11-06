<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Sku, Spu, SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import ProductArt from '@/components/common/ProductArt.vue'
import Price from '@/components/common/Price.vue'
import QtyStepper from '@/components/common/QtyStepper.vue'
import Icon from '@/components/common/Icon.vue'
import ProductCard from '@/components/common/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const user = useUserStore()
const spu = ref<(Spu & { favorited: boolean }) | null>(null)
const error = ref('')
const picked = reactive<Record<string, string>>({})
const qty = ref(1)
const tab = ref<'detail' | 'params' | 'reviews'>('detail')
const related = ref<SpuCard[]>([])
const thumb = ref(0)
const adding = ref(false)

onMounted(async () => {
  try {
    spu.value = await productApi.detail(route.params.id as string)
    // 默认选中第一个有库存的 SKU
    const first = spu.value.skus.find((k) => k.stock > 0) ?? spu.value.skus[0]
    Object.assign(picked, first.specs)
    const r = await productApi.search({ categoryId: spu.value.categoryId, size: 6 })
    related.value = r.records.filter((x) => x.id !== spu.value!.id).slice(0, 5)
    if (related.value.length < 5) {
      const more = await productApi.recommend(2, 8)
      related.value.push(...more.records.filter((x) => x.id !== spu.value!.id && !related.value.some((y) => y.id === x.id)).slice(0, 5 - related.value.length))
    }
  } catch (e) {
    error.value = (e as Error).message
  }
})

const sku = computed<Sku | undefined>(() => spu.value?.skus.find((k) => Object.entries(picked).every(([n, v]) => k.specs[n] === v)))

/** 某规格值在当前其他已选规格下是否有库存（SKU 联动置灰） */
function available(group: string, value: string) {
  if (!spu.value) return false
  return spu.value.skus.some((k) => k.stock > 0 && k.specs[group] === value && Object.entries(picked).every(([n, v]) => n === group || k.specs[n] === v))
}

function pick(group: string, value: string) {
  picked[group] = value
  if (sku.value && qty.value > sku.value.stock) qty.value = Math.max(1, sku.value.stock)
}

const thumbs = computed(() => {
  if (!spu.value) return []
  const tints = [spu.value.tint, '#d9d9de', '#c7b8a3', '#9fb6cc']
  return tints.map((t, i) => ({ tint: i === 0 ? spu.value!.tint : t, i }))
})

async function addCart() {
  if (!sku.value || sku.value.stock <= 0) return toast.warning('该规格暂时无货')
  adding.value = true
  try {
    await cart.add(sku.value.id, qty.value)
    toast.success(user.isLogin ? '已加入购物车' : '已加入购物车（登录后自动同步）')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    adding.value = false
  }
}

function buyNow() {
  if (!sku.value || sku.value.stock <= 0) return toast.warning('该规格暂时无货')
  const target = { path: '/checkout', query: { items: `${sku.value.id}:${qty.value}` } }
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: router.resolve(target).fullPath } })
  router.push(target)
}

async function toggleFav() {
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  const on = await productApi.toggleFavorite(spu.value!.id)
  spu.value!.favorited = on
  toast.success(on ? '已收藏' : '已取消收藏')
}

const REVIEWS = [
  { user: '橙***子', star: 5, text: '物流很快，第二天就到了，包装完好，和描述一致，好评！', spec: '' },
  { user: 'S***n', star: 5, text: '双 11 价格很划算，叠加了满减和优惠券，比平时便宜不少。', spec: '' },
  { user: '小***鱼', star: 4, text: '整体不错，客服回复也及时，就是赠品少了点。', spec: '' },
]
</script>

<template>
  <div class="container page">
    <div v-if="error" class="card err">
      <Icon name="alert" :size="40" />
      <p>{{ error }}</p>
      <RouterLink to="/" class="btn btn-primary">返回首页</RouterLink>
    </div>
    <template v-else-if="spu">
      <div class="breadcrumb">
        <RouterLink to="/">首页</RouterLink>
        <template v-for="c in spu.categoryPath" :key="c">
          <Icon name="chevron-right" :size="12" />
          <span>{{ c }}</span>
        </template>
        <Icon name="chevron-right" :size="12" />
        <span class="ellipsis" style="max-width: 300px">{{ spu.name }}</span>
      </div>

      <section class="main card">
        <div class="gallery">
          <div class="big">
            <ProductArt :kind="spu.art" :tint="thumbs[thumb]?.tint ?? spu.tint" :src="spu.imageUrl" />
            <span v-if="spu.tags.includes('双11爆款')" class="flag">双11 爆款</span>
          </div>
          <div class="thumbs">
            <button v-for="t in thumbs" :key="t.i" :class="{ on: thumb === t.i }" @mouseenter="thumb = t.i">
              <ProductArt :kind="spu.art" :tint="t.tint" />
            </button>
          </div>
          <div class="g-actions">
            <a :class="{ on: spu.favorited }" @click="toggleFav"><Icon :name="spu.favorited ? 'heart-fill' : 'heart'" :size="16" /> {{ spu.favorited ? '已收藏' : '收藏商品' }}</a>
            <span class="muted">商品编号：{{ spu.id }}</span>
          </div>
        </div>

        <div class="info">
          <h1>{{ spu.name }}</h1>
          <p class="sub">{{ spu.subtitle }}</p>

          <div class="price-box">
            <div class="activity">
              <b>双11 全球好物节</b>
              <span>跨店每满 300 减 50 · 可叠加平台券</span>
            </div>
            <div class="row">
              <span class="lbl">到手价</span>
              <Price :value="sku?.price ?? spu.minPrice" :size="32" :del="sku?.originalPrice ?? spu.maxOriginalPrice" />
              <span class="sales muted">累计销量 <b class="num">{{ spu.sales.toLocaleString() }}</b> · 评分 <b class="num">{{ spu.rating.toFixed(1) }}</b></span>
            </div>
            <div class="row promo">
              <span class="lbl">优惠</span>
              <span class="tag tag-red">满减</span><span>每满 300 减 50，上不封顶 5 次</span>
              <RouterLink to="/coupons" class="get-coupon">领券 <Icon name="chevron-right" :size="12" /></RouterLink>
            </div>
            <div class="row promo">
              <span class="lbl">积分</span>
              <span class="tag">积分</span><span>购买可得 {{ Math.floor((sku?.price ?? spu.minPrice) / 100) }} 积分，100 积分抵 1 元</span>
            </div>
          </div>

          <div class="rows">
            <div class="row">
              <span class="lbl">配送</span>
              <span>陕西汉中 至 <b>汉台区</b>　<span class="tag tag-green">现货</span> 23:00 前下单，预计明天送达 · 免运费</span>
            </div>
            <div v-for="g in spu.specGroups" :key="g.name" class="row spec">
              <span class="lbl">{{ g.name }}</span>
              <div class="vals">
                <button
                  v-for="v in g.values"
                  :key="v"
                  :class="{ on: picked[g.name] === v, off: !available(g.name, v) }"
                  @click="pick(g.name, v)"
                >
                  {{ v }}
                </button>
              </div>
            </div>
            <div class="row">
              <span class="lbl">数量</span>
              <QtyStepper v-model="qty" :max="Math.max(1, Math.min(99, sku?.stock ?? 1))" :disabled="!sku || sku.stock <= 0" />
              <span class="stock" :class="{ low: sku && sku.stock > 0 && sku.stock <= 5 }">
                {{ !sku ? '请选择规格' : sku.stock <= 0 ? '该规格已售罄' : sku.stock <= 5 ? `仅剩 ${sku.stock} 件` : `库存 ${sku.stock} 件` }}
              </span>
            </div>
          </div>

          <div class="buy">
            <button class="btn btn-lg btn-cart" :disabled="adding || !sku || sku.stock <= 0" @click="addCart"><Icon name="cart" :size="20" /> 加入购物车</button>
            <button class="btn btn-lg btn-primary" :disabled="!sku || sku.stock <= 0" @click="buyNow">立即购买</button>
          </div>
          <div class="promise">
            <span><Icon name="shield" :size="14" /> 正品保障</span>
            <span><Icon name="truck" :size="14" /> 极速发货</span>
            <span><Icon name="refresh" :size="14" /> 7 天无理由</span>
            <span><Icon name="card" :size="14" /> 分期免息</span>
          </div>
        </div>
      </section>

      <div class="lower">
        <aside class="side">
          <div class="card shop">
            <div class="shop-head">
              <span class="logo"><Icon name="store" :size="22" /></span>
              <div>
                <b>{{ spu.shopName }}</b>
                <p class="muted">{{ spu.brandName }} · 官方授权</p>
              </div>
            </div>
            <div class="score">
              <span>描述 <b>4.9</b></span><span>服务 <b>4.9</b></span><span>物流 <b>4.8</b></span>
            </div>
            <RouterLink :to="{ path: '/search', query: { brandId: spu.brandId } }" class="btn btn-ghost btn-block btn-sm">进店逛逛</RouterLink>
          </div>
          <div class="card rel">
            <h4>看了又看</h4>
            <RouterLink v-for="r in related.slice(0, 3)" :key="r.id" :to="`/product/${r.id}`" class="rel-item">
              <ProductArt :kind="r.art" :tint="r.tint" bg />
              <p class="ellipsis">{{ r.name }}</p>
              <span class="price">¥{{ yuan(r.minPrice) }}</span>
            </RouterLink>
          </div>
        </aside>
        <div class="tabs-wrap card">
          <nav class="tabs">
            <a :class="{ on: tab === 'detail' }" @click="tab = 'detail'">商品详情</a>
            <a :class="{ on: tab === 'params' }" @click="tab = 'params'">规格参数</a>
            <a :class="{ on: tab === 'reviews' }" @click="tab = 'reviews'">商品评价 <small>(2.1万+)</small></a>
          </nav>
          <div v-if="tab === 'detail'" class="detail">
            <p v-for="(d, i) in spu.detail" :key="i">{{ d }}</p>
            <div class="poster" :style="{ background: `linear-gradient(135deg, ${spu.tint}33, #fff)` }">
              <ProductArt :kind="spu.art" :tint="spu.tint" :size="260" />
              <div>
                <h3>{{ spu.name }}</h3>
                <p>{{ spu.subtitle }}</p>
              </div>
            </div>
          </div>
          <table v-else-if="tab === 'params'" class="params">
            <tr v-for="[k, v] in spu.params" :key="k"><th>{{ k }}</th><td>{{ v }}</td></tr>
            <tr><th>商品编号（SPU）</th><td>{{ spu.id }}</td></tr>
            <tr><th>当前 SKU</th><td>{{ sku?.id }} · {{ sku ? Object.values(sku.specs).join(' / ') : '-' }}</td></tr>
          </table>
          <ul v-else class="reviews">
            <li v-for="r in REVIEWS" :key="r.user">
              <b>{{ r.user }}</b>
              <span class="stars">{{ '★'.repeat(r.star) }}{{ '☆'.repeat(5 - r.star) }}</span>
              <p>{{ r.text }}</p>
            </li>
          </ul>
        </div>
      </div>

      <section class="more">
        <h3 class="section-title">相关推荐</h3>
        <div class="grid">
          <ProductCard v-for="r in related" :key="r.id" :item="r" />
        </div>
      </section>
    </template>
    <div v-else class="main card"><div class="skeleton" style="height: 460px; flex: 1" /></div>
  </div>
</template>

<style scoped>
.err {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 80px 0;
  color: var(--c-text-3);
}
.main {
  display: flex;
  gap: 36px;
  padding: 24px;
}
.gallery {
  width: 440px;
  flex: none;
}
.big {
  position: relative;
  background: linear-gradient(160deg, #fbfbfc, #efeff2);
  border-radius: var(--radius-lg);
  padding: 30px;
}
.flag {
  position: absolute;
  left: 14px;
  top: 14px;
  background: var(--g-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
}
.thumbs {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.thumbs button {
  width: 70px;
  border: 2px solid transparent;
  border-radius: 8px;
  background: #f5f5f7;
  padding: 4px;
}
.thumbs button.on {
  border-color: var(--c-primary);
}
.g-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  font-size: 13px;
}
.g-actions a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--c-text-2);
}
.g-actions a.on,
.g-actions a:hover {
  color: var(--c-red);
}
.info {
  flex: 1;
  min-width: 0;
}
h1 {
  font-size: 22px;
  line-height: 1.4;
}
.sub {
  color: var(--c-primary);
  margin-top: 6px;
}
.price-box {
  margin-top: 16px;
  border-radius: var(--radius);
  overflow: hidden;
  background: linear-gradient(180deg, #fff4ee, #fff);
  border: 1px solid #ffe2d1;
}
.activity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--g-hero);
  color: #fff;
}
.activity b {
  font-style: italic;
  font-size: 16px;
}
.activity span {
  font-size: 12px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
}
.lbl {
  width: 48px;
  color: var(--c-text-3);
  flex: none;
  font-size: 13px;
}
.sales {
  margin-left: auto;
  font-size: 12px;
}
.sales b {
  color: var(--c-text);
}
.promo {
  font-size: 13px;
  padding-top: 2px;
}
.get-coupon {
  margin-left: auto;
  color: var(--c-primary);
  display: inline-flex;
  align-items: center;
}
.rows {
  margin-top: 8px;
}
.rows .row {
  padding: 10px 16px;
  font-size: 13px;
}
.spec {
  align-items: flex-start !important;
}
.spec .lbl {
  padding-top: 7px;
}
.vals {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.vals button {
  min-width: 64px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--c-border-2);
  border-radius: 6px;
  background: #fff;
}
.vals button:hover {
  border-color: var(--c-primary);
}
.vals button.on {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: var(--c-primary-soft);
  font-weight: 600;
}
.vals button.off {
  border-style: dashed;
  color: var(--c-text-4);
}
.stock {
  color: var(--c-text-3);
}
.stock.low {
  color: var(--c-red);
}
.buy {
  display: flex;
  gap: 14px;
  padding: 14px 16px 6px;
}
.buy .btn {
  width: 200px;
  height: 50px;
  font-size: 17px;
}
.btn-cart {
  background: linear-gradient(90deg, #ffc93a, #ffa01a);
  border: none;
  color: #fff;
}
.btn-cart:hover {
  color: #fff;
  filter: brightness(1.05);
}
.promise {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  font-size: 12px;
  color: var(--c-text-3);
}
.promise span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.lower {
  display: flex;
  gap: 16px;
  margin-top: 16px;
  align-items: flex-start;
}
.side {
  width: 240px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.shop {
  padding: 16px;
}
.shop-head {
  display: flex;
  gap: 10px;
  align-items: center;
}
.shop-head .logo {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--g-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.shop-head p {
  font-size: 12px;
}
.score {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--c-text-3);
  margin: 14px 0;
}
.score b {
  color: var(--c-primary);
}
.rel {
  padding: 14px;
}
.rel h4 {
  margin-bottom: 10px;
}
.rel-item {
  display: block;
  margin-bottom: 12px;
}
.rel-item p {
  font-size: 12px;
  margin-top: 4px;
}
.rel-item .price {
  font-size: 14px;
}
.tabs-wrap {
  flex: 1;
  min-width: 0;
}
.tabs {
  display: flex;
  border-bottom: 1px solid var(--c-border);
  padding: 0 10px;
}
.tabs a {
  padding: 14px 20px;
  cursor: pointer;
  font-size: 15px;
  position: relative;
}
.tabs a.on {
  color: var(--c-primary);
  font-weight: 700;
}
.tabs a.on::after {
  content: '';
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: -1px;
  height: 3px;
  background: var(--c-primary);
}
.detail {
  padding: 24px;
  line-height: 2;
  color: var(--c-text-2);
}
.poster {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 30px;
  border-radius: var(--radius-lg);
}
.poster h3 {
  font-size: 26px;
  color: var(--c-text);
}
.params {
  margin: 20px 24px;
  width: calc(100% - 48px);
  border-collapse: collapse;
}
.params th,
.params td {
  border: 1px solid var(--c-border);
  padding: 10px 14px;
  text-align: left;
  font-size: 13px;
}
.params th {
  width: 180px;
  background: #fafafa;
  font-weight: 500;
  color: var(--c-text-2);
}
.reviews {
  padding: 10px 24px;
}
.reviews li {
  padding: 16px 0;
  border-bottom: 1px solid var(--c-border);
}
.stars {
  color: #ffb400;
  margin-left: 10px;
}
.reviews p {
  margin-top: 6px;
  color: var(--c-text-2);
}
.more {
  margin-top: 24px;
}
.more .grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
@media (max-width: 900px) {
  .main { flex-direction: column; gap: 16px; padding: 12px; }
  .gallery { width: 100%; }
  .big { padding: 12px; }
  .info { width: 100%; }
  .buy { padding: 12px 0 4px; }
  .buy .btn { flex: 1; width: auto; min-width: 0; padding: 0 8px; font-size: 15px; }
  .lower { flex-direction: column; align-items: stretch; }
  .side { width: 100%; }
  .row, .rows, .vals { flex-wrap: wrap; }
}
@media (max-width: 900px) {
  .tabs-wrap { min-width: 0; width: 100%; }
  .detail { padding: 12px; }
  .poster { flex-direction: column; gap: 12px; padding: 16px; text-align: center; }
  .poster h3 { font-size: 20px; }
  .params { margin: 12px; width: calc(100% - 24px); }
  .params th { width: 88px; }
}
</style>
