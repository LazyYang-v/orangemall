<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Address, ConfirmItemReq } from '@/types/domain'
import { orderApi, type PreviewResp } from '@/api/order'
import { userApi } from '@/api/user'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'
import { yuan, maskPhone } from '@/utils/format'
import { COUPON_TYPE } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import Icon from '@/components/common/Icon.vue'
import AddressEditor from '@/components/order/AddressEditor.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const user = useUserStore()

const items = computed<ConfirmItemReq[]>(() =>
  String(route.query.items ?? '')
    .split(',')
    .filter(Boolean)
    .map((s) => {
      const [skuId, count] = s.split(':')
      return { skuId, count: Number(count) || 1 }
    }),
)
const fromCart = route.query.from === 'cart'
const preview = ref<PreviewResp | null>(null)
const addresses = ref<Address[]>([])
const addressId = ref('')
const couponId = ref<string | null | undefined>(undefined) // undefined=系统推荐最优券, null=不使用
const usePoints = ref(false)
const remark = ref('')
const submitting = ref(false)
const editing = ref(false)
const showUnavailable = ref(false)
const error = ref('')

async function refresh() {
  try {
    preview.value = await orderApi.preview({
      items: items.value,
      couponId: couponId.value,
      pointsUsed: usePoints.value ? 999999 : 0,
    })
    if (couponId.value === undefined) couponId.value = preview.value.selectedCouponId ?? null
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function loadAddresses(selectId?: string) {
  addresses.value = await userApi.addresses()
  addressId.value = selectId ?? addresses.value.find((a) => a.isDefault)?.id ?? addresses.value[0]?.id ?? ''
}

onMounted(async () => {
  if (!items.value.length) return router.replace('/cart')
  await Promise.all([refresh(), loadAddresses()])
})

function pickCoupon(id: string | null) {
  couponId.value = couponId.value === id ? null : id
  refresh()
}
function togglePoints() {
  usePoints.value = !usePoints.value
  refresh()
}

const p = computed(() => preview.value?.price)
const pointsCanUse = computed(() => {
  if (!preview.value) return 0
  return Math.min(preview.value.maxPoints, Math.floor(preview.value.pointsBalance / preview.value.pointsRate) * preview.value.pointsRate)
})
const totalCount = computed(() => items.value.reduce((s, i) => s + i.count, 0))
const address = computed(() => addresses.value.find((a) => a.id === addressId.value))

async function submit() {
  if (!preview.value) return
  if (!addressId.value) return toast.warning('请先添加收货地址')
  submitting.value = true
  try {
    const r = await orderApi.submit({
      items: items.value,
      addressId: addressId.value,
      couponId: couponId.value ?? undefined,
      pointsUsed: preview.value.price.pointsDeduct ? (preview.value.price.pointsDeduct / 100) * preview.value.pointsRate : 0,
      remark: remark.value,
      submitToken: preview.value.submitToken,
      fromCart,
    })
    if (fromCart) cart.load()
    user.fetchProfile()
    router.replace(`/pay/${r.orderId}`)
  } catch (e) {
    toast.error((e as Error).message)
    if ((e as { code?: number }).code === 5001 || (e as { code?: number }).code === 3001) refresh() // 令牌失效 / 库存变化：重新试算
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="container page">
    <div class="steps">
      <span class="on">1 我的购物车</span><i /><span class="on cur">2 确认订单</span><i /><span>3 付款</span><i /><span>4 支付成功</span>
    </div>

    <div v-if="error" class="card err">
      <Icon name="alert" :size="36" />
      <p>{{ error }}</p>
      <RouterLink to="/cart" class="btn btn-primary">返回购物车</RouterLink>
    </div>

    <template v-else>
      <section class="card block">
        <h3>收货地址 <a class="add" @click="editing = true"><Icon name="plus" :size="14" /> 新增地址</a></h3>
        <div class="addrs">
          <div v-for="a in addresses" :key="a.id" class="addr" :class="{ on: a.id === addressId }" @click="addressId = a.id">
            <div class="who"><b>{{ a.receiver }}</b> <span class="num">{{ maskPhone(a.phone) }}</span><span v-if="a.isDefault" class="tag tag-solid">默认</span></div>
            <p>{{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}</p>
            <Icon v-if="a.id === addressId" name="check" :size="16" class="tick" :stroke="3" />
          </div>
          <div v-if="!addresses.length" class="addr empty" @click="editing = true"><Icon name="plus" :size="22" /> 添加收货地址</div>
        </div>
      </section>

      <section class="card block">
        <h3>商品清单 <small class="muted">共 {{ totalCount }} 件</small></h3>
        <div v-for="it in preview?.items ?? []" :key="it.skuId" class="item">
          <ProductArt :kind="it.art" :tint="it.tint" :size="72" bg />
          <div class="nm">
            <p class="ellipsis">{{ it.name }}</p>
            <span class="muted">{{ it.specText }}</span>
          </div>
          <span class="num">¥{{ yuan(it.price, true) }}</span>
          <span class="muted">× {{ it.count }}</span>
          <b class="price">¥{{ yuan(it.price * it.count, true) }}</b>
        </div>
        <div v-if="!preview" class="skeleton" style="height: 80px" />
        <div class="remark">
          <span>订单备注</span>
          <input v-model="remark" class="input" maxlength="100" placeholder="选填：对本次交易的说明（建议填写已和商家协商一致的内容）" />
        </div>
      </section>

      <section v-if="preview" class="card block">
        <h3>
          优惠券
          <small class="muted">可用 {{ preview.availableCoupons.length }} 张</small>
          <span v-if="preview.bestCouponId && couponId === preview.bestCouponId" class="tag tag-red">已为你选择最优券</span>
          <RouterLink to="/coupons" class="add">去领券 <Icon name="chevron-right" :size="12" /></RouterLink>
        </h3>
        <div class="coupons">
          <div v-for="c in preview.availableCoupons" :key="c.id" class="coupon" :class="{ on: c.id === couponId }" @click="pickCoupon(c.id)">
            <div class="amt">
              <template v-if="c.type === 'DISCOUNT'"><b>{{ c.amount / 10 }}</b>折</template>
              <template v-else><small>¥</small><b>{{ yuan(c.amount) }}</b></template>
            </div>
            <div class="meta">
              <p>{{ c.threshold ? `满 ${yuan(c.threshold)} 可用` : '无门槛' }} · {{ COUPON_TYPE[c.type] }}</p>
              <span>{{ c.scope }}</span>
            </div>
            <Icon v-if="c.id === couponId" name="check-circle" :size="20" class="ck" />
          </div>
          <div v-if="!preview.availableCoupons.length" class="muted none">暂无可用优惠券</div>
        </div>
        <a v-if="preview.unavailableCoupons.length" class="toggle" @click="showUnavailable = !showUnavailable">
          不可用优惠券（{{ preview.unavailableCoupons.length }}）<Icon name="chevron-down" :size="12" />
        </a>
        <div v-if="showUnavailable" class="coupons">
          <div v-for="c in preview.unavailableCoupons" :key="c.id" class="coupon disabled">
            <div class="amt"><small>¥</small><b>{{ c.type === 'DISCOUNT' ? c.amount / 10 + '折' : yuan(c.amount) }}</b></div>
            <div class="meta">
              <p>{{ c.title }}</p>
              <span class="why">{{ c.reason }}</span>
            </div>
          </div>
        </div>

        <div class="points">
          <label>
            <input type="checkbox" class="checkbox" :checked="usePoints" :disabled="pointsCanUse <= 0" @change="togglePoints" />
            积分抵扣
          </label>
          <span class="muted">
            共 {{ preview.pointsBalance }} 积分，本单最多可用 {{ pointsCanUse }} 积分（{{ preview.pointsRate }} 积分 = ¥1，不超过实付 {{ preview.maxPointsRatio * 100 }}%）
          </span>
          <b v-if="p && p.pointsDeduct" class="price">-¥{{ yuan(p.pointsDeduct, true) }}</b>
        </div>
      </section>

      <section v-if="p" class="card summary">
        <dl>
          <div><dt>商品总额</dt><dd>¥{{ yuan(p.goodsAmount, true) }}</dd></div>
          <div><dt>运费</dt><dd>{{ p.freight ? `+¥${yuan(p.freight, true)}` : '免运费' }}</dd></div>
          <div v-if="p.fullReduction"><dt>跨店满减</dt><dd class="red">-¥{{ yuan(p.fullReduction, true) }}</dd></div>
          <div v-if="p.couponDeduct"><dt>优惠券</dt><dd class="red">-¥{{ yuan(p.couponDeduct, true) }}</dd></div>
          <div v-if="p.pointsDeduct"><dt>积分抵扣</dt><dd class="red">-¥{{ yuan(p.pointsDeduct, true) }}</dd></div>
        </dl>
        <div class="final">
          <div class="pay">
            实付款：<span class="price">¥{{ yuan(p.payAmount, true) }}</span>
            <p v-if="address" class="to muted">寄送至：{{ address.province }}{{ address.city }}{{ address.district }} {{ address.detail }}　收货人：{{ address.receiver }} {{ maskPhone(address.phone) }}</p>
          </div>
          <button class="btn btn-primary btn-lg submit" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '提交订单' }}</button>
        </div>
        <p class="note muted">
          <Icon name="shield" :size="13" /> 提交后将锁定库存与优惠券，30 分钟内未支付订单将自动关闭并退回库存/优惠券/积分
        </p>
      </section>
    </template>

    <AddressEditor :open="editing" @close="editing = false" @saved="(a) => { editing = false; loadAddresses(a.id) }" />
  </div>
</template>

<style scoped>
.steps {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin: 6px 0 16px;
  font-size: 13px;
  color: var(--c-text-3);
}
.steps i {
  width: 40px;
  height: 1px;
  background: var(--c-border-2);
}
.steps .on {
  color: var(--c-primary);
}
.steps .cur {
  font-weight: 700;
}
.err {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 60px;
  color: var(--c-text-3);
}
.block {
  padding: 18px 22px;
  margin-bottom: 14px;
}
.block h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  margin-bottom: 14px;
}
.block h3 small {
  font-size: 12px;
  font-weight: 400;
}
.add {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
  color: var(--c-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.addrs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.addr {
  position: relative;
  border: 1.5px solid var(--c-border-2);
  border-radius: var(--radius);
  padding: 12px 14px;
  cursor: pointer;
  font-size: 13px;
  min-height: 86px;
  background: repeating-linear-gradient(-45deg, transparent 0 8px, rgba(255, 90, 0, 0) 8px 16px);
}
.addr:hover {
  border-color: var(--c-primary-border);
}
.addr.on {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}
.who {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
.addr p {
  color: var(--c-text-2);
  line-height: 1.5;
}
.tick {
  position: absolute;
  right: 0;
  bottom: 0;
  background: var(--c-primary);
  color: #fff;
  border-radius: 8px 0 8px 0;
  padding: 2px;
  width: 22px;
  height: 20px;
}
.addr.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--c-text-3);
  border-style: dashed;
}
.item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--c-border);
}
.nm {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
.nm span {
  font-size: 12px;
}
.item .price {
  width: 110px;
  text-align: right;
}
.remark {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  font-size: 13px;
}
.remark span {
  flex: none;
  color: var(--c-text-2);
}
.coupons {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 10px;
}
.coupon {
  position: relative;
  display: flex;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1.5px solid var(--c-primary-border);
  cursor: pointer;
  background: #fff;
}
.coupon.on {
  border-color: var(--c-primary);
  box-shadow: 0 4px 12px rgba(255, 90, 0, 0.15);
}
.amt {
  width: 86px;
  flex: none;
  background: var(--g-primary);
  color: #fff;
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 16px;
}
.amt b {
  font-size: 26px;
  font-family: var(--font-num);
}
.meta {
  padding: 10px 12px;
  font-size: 12px;
}
.meta p {
  font-weight: 600;
  color: var(--c-text);
}
.meta span {
  color: var(--c-text-3);
}
.ck {
  position: absolute;
  right: 6px;
  bottom: 6px;
  color: var(--c-primary);
}
.coupon.disabled {
  cursor: not-allowed;
  border-color: var(--c-border-2);
}
.coupon.disabled .amt {
  background: #ccc;
}
.why {
  color: var(--c-red) !important;
}
.none {
  font-size: 13px;
}
.toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--c-text-2);
  cursor: pointer;
  margin-bottom: 10px;
}
.points {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--c-border);
  font-size: 13px;
}
.points label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  cursor: pointer;
}
.points .price {
  margin-left: auto;
}
.summary {
  padding: 18px 22px;
}
dl {
  margin: 0 0 0 auto;
  width: 320px;
  font-size: 13px;
}
dl div {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
}
dt {
  color: var(--c-text-2);
}
dd {
  margin: 0;
  font-family: var(--font-num);
}
.red {
  color: var(--c-red);
}
.final {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--c-border);
}
.pay {
  text-align: right;
  font-size: 14px;
}
.pay .price {
  font-size: 30px;
}
.to {
  font-size: 12px;
}
.submit {
  width: 180px;
  height: 52px;
  font-size: 18px;
}
.note {
  text-align: right;
  font-size: 12px;
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
</style>
