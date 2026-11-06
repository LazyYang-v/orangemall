/**
 * Mock 路由：严格按 docs/api-contract.md 的接口契约实现，用来在没有后端时驱动整条交易链路。
 * 每个 handler 都注明了它在真实后端中对应的关键技术点。
 */
import type {
  Address, CartCheckIssue, CartItem, Category, CouponTemplate, DashboardStats, HomeData, Order, OrderItem,
  OrderPreview, OrderPriceDetail, OrderStatus, PageResult, PayChannel, PayOrderVO, SearchQuery, SeckillResult,
  Spu, SpuCard, SubmitOrderReq, UserCoupon, UserProfile, CacheCheckRow, LogisticsTrace,
} from '@/types/domain'
import {
  findSeckillItem, getDb, opsLog, persist, resetDb, seckillItems, seckillSessions, warmUp, type MockUser,
} from './db'
import { couponScopeRoot, HOT_KEYWORDS, categories as seedCategories } from './seed'
import { snowflakeId } from '@/utils/snowflake'
import { mockBus } from './bus'

export class MockError extends Error {
  constructor(public code: number, message: string) {
    super(message)
  }
}

export interface MockRequest {
  method: string
  path: string
  query: Record<string, string>
  body: any
  params: Record<string, string>
  user?: MockUser
}

type Handler = (req: MockRequest) => unknown
interface Route {
  method: string
  pattern: RegExp
  keys: string[]
  auth: 'none' | 'user' | 'admin' | 'optional'
  handler: Handler
}

const routes: Route[] = []
function route(method: string, path: string, auth: Route['auth'], handler: Handler) {
  const keys: string[] = []
  const pattern = new RegExp(
    '^' + path.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)' }) + '$',
  )
  routes.push({ method, pattern, keys, auth, handler })
}

export function dispatch(method: string, path: string, query: Record<string, string>, body: any, token?: string) {
  const db = getDb()
  tick()
  for (const r of routes) {
    if (r.method !== method) continue
    const m = path.match(r.pattern)
    if (!m) continue
    const params: Record<string, string> = {}
    r.keys.forEach((k, i) => (params[k] = decodeURIComponent(m[i + 1])))
    const uid = token ? db.tokens[token] : undefined
    const user = uid ? db.users.find((u) => u.id === uid) : undefined
    if ((r.auth === 'user' || r.auth === 'admin') && !user) throw new MockError(401, '登录已过期，请重新登录')
    if (r.auth === 'admin' && user?.role !== 'ADMIN') throw new MockError(403, '无后台权限')
    const result = r.handler({ method, path, query, body, params, user })
    persist()
    return result
  }
  throw new MockError(404, `接口不存在：${method} ${path}`)
}

/* ============================== 工具 ============================== */
const db = () => getDb()
const nowIso = () => new Date().toISOString()
const ORDER_TTL = 30 * 60000
const SECKILL_ORDER_TTL = 10 * 60000

function rootCategoryOf(categoryId: number): number {
  let c = seedCategories.find((x) => x.id === categoryId)
  while (c && c.parentId !== 0) {
    const pid: number = c.parentId
    c = seedCategories.find((x) => x.id === pid)
  }
  return c?.id ?? 0
}

function findSku(skuId: string) {
  for (const spu of db().spus) {
    const sku = spu.skus.find((k) => k.id === skuId)
    if (sku) return { spu, sku }
  }
  return undefined
}

function toCard(s: Spu): SpuCard {
  const { specGroups: _a, skus: _b, detail: _c, params: _d, ...card } = s
  return card
}

function specText(specs: Record<string, string>) {
  return Object.values(specs).join(' / ')
}

function safeUser(u: MockUser): UserProfile {
  const { password: _p, ...rest } = u
  return rest
}

function addPoints(userId: string, change: number, reason: string) {
  const u = db().users.find((x) => x.id === userId)
  if (!u) return
  u.points += change
  ;(db().points[userId] ||= []).unshift({ id: snowflakeId(), change, reason, createdAt: nowIso() })
}

/* ===================== 后台「定时任务」：关单 / 物流推进 ===================== */
let lastTick = 0
function tick() {
  const now = Date.now()
  if (now - lastTick < 800) return
  lastTick = now
  for (const o of db().orders) {
    // RabbitMQ 延迟队列：TTL 到期进入死信队列 → 消费者检查状态仍为待支付则关单 + 回补库存 + 退券
    if (o.status === 'PENDING_PAYMENT' && new Date(o.payDeadline).getTime() <= now) closeOrder(o, '超时未支付，系统自动关闭（延迟队列）')
    if (o.status === 'SHIPPED' && o.logistics && o.logistics.state !== 'SIGNED') advanceLogistics(o, now)
  }
}

function closeOrder(o: Order, reason: string, status: OrderStatus = 'CLOSED') {
  o.status = status
  o.closedAt = nowIso()
  o.closeReason = reason
  // 回补库存
  for (const it of o.items) {
    const f = findSku(it.skuId)
    if (f && o.source === 'NORMAL') f.sku.stock += it.count
  }
  // 释放优惠券
  if (o.couponId) {
    const c = db().userCoupons[o.userId]?.find((x) => x.id === o.couponId)
    if (c && c.status === 'LOCKED') c.status = 'UNUSED'
  }
  if (o.pointsUsed) addPoints(o.userId, o.pointsUsed, `订单 ${o.id} 关闭，积分退回`)
  opsLog('MQ', `[DLX 消费] 订单 ${o.id} ${reason}，已回补库存/释放优惠券`)
  mockBus.publish(o.userId, { type: 'ORDER_STATUS', orderId: o.id, status: o.status, text: reason })
}

const LOGISTICS_STEPS: Array<Omit<LogisticsTrace, 'time'>> = [
  { state: 'IN_TRANSIT', desc: '快件已从【西安集散中心】发出，下一站【汉中转运中心】', location: '西安市' },
  { state: 'IN_TRANSIT', desc: '快件已到达【汉中转运中心】', location: '汉中市' },
  { state: 'DELIVERING', desc: '快递员【李师傅 139****8866】正在为您派件', location: '汉中市' },
  { state: 'SIGNED', desc: '已签收，签收人：本人。如有疑问请联系快递员', location: '汉中市' },
]

function advanceLogistics(o: Order, now: number) {
  const lg = o.logistics!
  const last = new Date(lg.traces[0].time).getTime()
  if (now - last < 12000) return // 每 12 秒模拟一次快递鸟推送
  const step = LOGISTICS_STEPS[lg.traces.length - 1]
  if (!step) return
  const trace = { ...step, time: nowIso() }
  lg.traces.unshift(trace)
  lg.state = trace.state
  opsLog('MQ', `[物流回调] 快递鸟推送 ${lg.trackingNo} ${trace.desc} → MQ → WebSocket`)
  mockBus.publish(o.userId, { type: 'LOGISTICS_UPDATE', orderId: o.id, trace })
}

/* ============================== 认证 ============================== */
route('POST', '/auth/login', 'none', ({ body }) => {
  const { phone, password, smsCode } = body as { phone: string; password?: string; smsCode?: string }
  const u = db().users.find((x) => x.phone === phone)
  if (!u) throw new MockError(1001, '账号不存在，请先注册')
  if (smsCode ? smsCode !== '123456' : u.password !== password) throw new MockError(1002, smsCode ? '验证码错误' : '账号或密码错误')
  const token = `mock.${u.id}.${Math.random().toString(36).slice(2)}`
  db().tokens[token] = u.id
  return { token, user: safeUser(u) }
})

route('POST', '/auth/register', 'none', ({ body }) => {
  const { phone, password, nickname } = body as { phone: string; password: string; nickname?: string }
  if (!/^1\d{10}$/.test(phone)) throw new MockError(1003, '手机号格式不正确')
  if (db().users.some((u) => u.phone === phone)) throw new MockError(1004, '该手机号已注册')
  const u: MockUser = { id: snowflakeId(), phone, password, nickname: nickname || `橙友${phone.slice(-4)}`, level: 1, points: 0, creditScore: 600, role: 'USER' }
  db().users.push(u)
  addPoints(u.id, 200, '新用户注册奖励')
  const tpl = db().couponTemplates.find((t) => t.id === 'CT04')!
  grantCoupon(u.id, tpl)
  const token = `mock.${u.id}.${Math.random().toString(36).slice(2)}`
  db().tokens[token] = u.id
  return { token, user: safeUser(u) }
})

route('POST', '/auth/sms', 'none', () => ({ sent: true, hint: 'Mock 环境验证码固定为 123456' }))
route('POST', '/auth/logout', 'optional', () => true)
route('GET', '/users/me', 'user', ({ user }) => safeUser(user!))

/* ============================== 首页 ============================== */
route('GET', '/home', 'optional', ({ user }) => {
  const sessions = seckillSessions()
  const cur = sessions.find((s) => s.status === 'ONGOING')!
  const data: HomeData = {
    hotKeywords: HOT_KEYWORDS,
    notices: db().notices,
    seckillPreview: { endAt: cur.endAt, items: seckillItems(cur).slice(0, 4) },
    couponPreview: db().couponTemplates.slice(0, 2).map((t) => decorateTemplate(t, user)),
    newArrival: toCard(db().spus.find((s) => s.id === '1006')!),
    banners: [
      { id: 'B1', tag: '双11 全球好物节', title: '限时狂欢 低至5折', subtitle: '品牌好物 · 跨店满减 · 领券再省', theme: 'red' },
      { id: 'B2', tag: '数码焕新季', title: '旗舰新品 12期免息', subtitle: '以旧换新 · 至高补贴 2000 元', theme: 'violet' },
      { id: 'B3', tag: '整点秒杀', title: '每 2 小时一场 限量抢', subtitle: '一人一单 · 手慢无', theme: 'orange' },
      { id: 'B4', tag: '新人专享', title: '注册即领 10 元无门槛券', subtitle: '再送 200 积分 · 积分可抵现', theme: 'teal' },
    ],
  }
  return data
})

route('GET', '/home/recommend', 'optional', ({ query }) => {
  const page = Number(query.page || 1)
  const size = Number(query.size || 10)
  // 推荐流：按销量 + 轮换，模拟分页
  const sorted = [...db().spus].sort((a, b) => b.sales - a.sales)
  const rotated = [...sorted.slice(page - 1), ...sorted.slice(0, page - 1)]
  return { records: rotated.slice(0, size).map(toCard), total: 200, page, size } satisfies PageResult<SpuCard>
})

/* ============================== 商品 ============================== */
route('GET', '/categories/tree', 'none', () => buildTree(seedCategoriesLive()))

function seedCategoriesLive(): Category[] {
  return adminCategories
}
let adminCategories: Category[] = seedCategories.map((c) => ({ ...c }))

function buildTree(list: Category[]): Category[] {
  // 邻接表 → 树（后端可用 MySQL 8 WITH RECURSIVE 一次查出整棵子树）
  const map = new Map<number, Category>()
  list.forEach((c) => map.set(c.id, { ...c, children: [] }))
  const roots: Category[] = []
  map.forEach((c) => {
    if (c.parentId === 0) roots.push(c)
    else map.get(c.parentId)?.children!.push(c)
  })
  const sort = (arr: Category[]) => {
    arr.sort((a, b) => a.sort - b.sort)
    arr.forEach((c) => sort(c.children!))
  }
  sort(roots)
  return roots
}

function descendantIds(id: number): number[] {
  const out = [id]
  for (const c of adminCategories) if (c.parentId === id) out.push(...descendantIds(c.id))
  return out
}

route('GET', '/products', 'none', ({ query }) => {
  const q = query as unknown as SearchQuery & Record<string, string>
  let list = db().spus.filter((s) => s.status === 'ON_SALE')
  if (q.keyword) {
    const kw = String(q.keyword).toLowerCase().replace(/\s+/g, '')
    const alias: Record<string, string> = { iphone17: 'phone 17', 双11: '' }
    const k2 = alias[kw] ?? kw
    list = list.filter((s) =>
      [s.name, s.subtitle, s.brandName, ...s.categoryPath, ...s.tags].join(' ').toLowerCase().replace(/\s+/g, '').includes(k2.replace(/\s+/g, '')),
    )
  }
  if (q.categoryId) {
    const ids = descendantIds(Number(q.categoryId))
    list = list.filter((s) => ids.includes(s.categoryId))
  }
  if (q.brandId) list = list.filter((s) => s.brandId === Number(q.brandId))
  if (q.minPrice) list = list.filter((s) => s.minPrice >= Number(q.minPrice) * 100)
  if (q.maxPrice) list = list.filter((s) => s.minPrice <= Number(q.maxPrice) * 100)
  const sorters: Record<string, (a: Spu, b: Spu) => number> = {
    sales: (a, b) => b.sales - a.sales,
    price_asc: (a, b) => a.minPrice - b.minPrice,
    price_desc: (a, b) => b.minPrice - a.minPrice,
    new: (a, b) => Number(!!b.isNew) - Number(!!a.isNew),
  }
  if (q.sort && sorters[q.sort]) list = [...list].sort(sorters[q.sort])
  const page = Number(q.page || 1)
  const size = Number(q.size || 20)
  const brandIds = [...new Set(list.map((s) => s.brandId))]
  return {
    records: list.slice((page - 1) * size, page * size).map(toCard),
    total: list.length,
    page,
    size,
    brands: brandIds.map((id) => ({ id, name: db().spus.find((s) => s.brandId === id)!.brandName })),
  }
})

route('GET', '/products/:id', 'optional', ({ params, user }) => {
  const spu = db().spus.find((s) => s.id === params.id)
  if (!spu) throw new MockError(2001, '商品不存在或已下架')
  // Cache Aside：先读缓存，未命中再读库并回填
  if (db().productCache[spu.id]) db().cacheStats.hit++
  else {
    db().cacheStats.miss++
    db().productCache[spu.id] = Date.now()
    opsLog('CACHE', `[Cache Aside] product:detail:${spu.id} 未命中 → 查库回填 Redis（TTL 30min + 随机抖动）`)
  }
  return { ...spu, favorited: !!user && (db().favorites[user.id] || []).includes(spu.id) }
})

route('POST', '/products/:id/favorite', 'user', ({ params, user }) => {
  const list = (db().favorites[user!.id] ||= [])
  const i = list.indexOf(params.id)
  if (i >= 0) list.splice(i, 1)
  else list.push(params.id)
  return i < 0
})

route('GET', '/favorites', 'user', ({ user }) =>
  (db().favorites[user!.id] || []).map((id) => db().spus.find((s) => s.id === id)).filter(Boolean).map((s) => toCard(s!)),
)

/* ============================== 购物车（Redis Hash） ============================== */
function cartOf(userId: string) {
  return (db().carts[userId] ||= {})
}

function toCartItems(entries: Array<[string, { count: number; checked: boolean }]>): CartItem[] {
  return entries
    .map(([skuId, f]): CartItem | undefined => {
      const found = findSku(skuId)
      if (!found) return undefined
      const { spu, sku } = found
      const status: CartItem['status'] = spu.status !== 'ON_SALE' ? 'OFF_SALE' : sku.stock <= 0 ? 'NO_STOCK' : 'NORMAL'
      return {
        skuId, spuId: spu.id, name: spu.name, specText: specText(sku.specs), art: spu.art, tint: spu.tint,
        price: sku.price, originalPrice: sku.originalPrice, count: f.count, checked: f.checked && status === 'NORMAL',
        stock: sku.stock, status, shopName: spu.shopName,
      }
    })
    .filter((x): x is CartItem => !!x)
}

route('GET', '/cart', 'user', ({ user }) => {
  const c = cartOf(user!.id)
  return toCartItems(Object.entries(c).sort((a, b) => b[1].addedAt - a[1].addedAt))
})

/** 未登录购物车：前端本地存储，仅调用此接口补全商品信息 */
route('POST', '/cart/preview', 'none', ({ body }) =>
  toCartItems((body.items as Array<{ skuId: string; count: number; checked: boolean }>).map((i) => [i.skuId, i])),
)

route('POST', '/cart/items', 'user', ({ body, user }) => {
  const { skuId, count } = body as { skuId: string; count: number }
  const found = findSku(skuId)
  if (!found) throw new MockError(2001, '商品不存在')
  const c = cartOf(user!.id)
  const next = (c[skuId]?.count ?? 0) + count // HINCRBY
  if (next > found.sku.stock) throw new MockError(3001, `库存不足，最多可购买 ${found.sku.stock} 件`)
  if (next > 99) throw new MockError(3002, '单件商品最多购买 99 件')
  c[skuId] = { count: next, checked: true, addedAt: Date.now() }
  return Object.keys(c).length
})

route('PUT', '/cart/items/:skuId', 'user', ({ params, body, user }) => {
  const c = cartOf(user!.id)
  const f = c[params.skuId]
  if (!f) throw new MockError(3003, '购物车中不存在该商品')
  if (body.count !== undefined) {
    const stock = findSku(params.skuId)?.sku.stock ?? 0
    if (body.count > stock) throw new MockError(3001, `库存不足，最多可购买 ${stock} 件`)
    f.count = Math.max(1, Math.min(99, body.count))
  }
  if (body.checked !== undefined) f.checked = body.checked
  return true
})

route('PUT', '/cart/check-all', 'user', ({ body, user }) => {
  Object.values(cartOf(user!.id)).forEach((f) => (f.checked = body.checked))
  return true
})

route('DELETE', '/cart/items', 'user', ({ body, user }) => {
  const c = cartOf(user!.id)
  ;(body.skuIds as string[]).forEach((id) => delete c[id]) // HDEL
  return true
})

route('POST', '/cart/merge', 'user', ({ body, user }) => {
  // 登录后合并：本地数量与 Redis 数量相加，不超过库存
  const c = cartOf(user!.id)
  for (const it of body.items as Array<{ skuId: string; count: number; checked: boolean }>) {
    const stock = findSku(it.skuId)?.sku.stock ?? 0
    const merged = Math.min(stock || 1, 99, (c[it.skuId]?.count ?? 0) + it.count)
    c[it.skuId] = { count: merged, checked: it.checked, addedAt: Date.now() }
  }
  return Object.keys(c).length
})

/** 结算前预校验：上下架 / 库存 */
route('POST', '/cart/check', 'none', ({ body }) => {
  const issues: CartCheckIssue[] = []
  for (const it of body.items as Array<{ skuId: string; count: number }>) {
    const f = findSku(it.skuId)
    if (!f || f.spu.status !== 'ON_SALE') issues.push({ skuId: it.skuId, reason: 'OFF_SALE', message: '商品已下架' })
    else if (f.sku.stock <= 0) issues.push({ skuId: it.skuId, reason: 'NO_STOCK', message: '商品已售罄' })
    else if (f.sku.stock < it.count) issues.push({ skuId: it.skuId, reason: 'STOCK_NOT_ENOUGH', message: `库存仅剩 ${f.sku.stock} 件` })
  }
  return issues
})

/* ============================== 地址 ============================== */
route('GET', '/addresses', 'user', ({ user }) => db().addresses[user!.id] || [])
route('POST', '/addresses', 'user', ({ body, user }) => {
  const list = (db().addresses[user!.id] ||= [])
  const addr: Address = { ...(body as Address), id: body.id || snowflakeId() }
  if (addr.isDefault || list.length === 0) {
    list.forEach((a) => (a.isDefault = false))
    addr.isDefault = true
  }
  const i = list.findIndex((a) => a.id === addr.id)
  if (i >= 0) list[i] = addr
  else list.push(addr)
  return addr
})
route('DELETE', '/addresses/:id', 'user', ({ params, user }) => {
  const list = db().addresses[user!.id] || []
  db().addresses[user!.id] = list.filter((a) => a.id !== params.id)
  return true
})

/* ============================== 优惠券 ============================== */
function decorateTemplate(t: CouponTemplate, user?: MockUser): CouponTemplate {
  const mine = user ? (db().userCoupons[user.id] || []).filter((c) => c.templateId === t.id).length : 0
  return { ...t, receivedByMe: mine >= t.perUserLimit }
}

function grantCoupon(userId: string, t: CouponTemplate): UserCoupon {
  const now = Date.now()
  const c: UserCoupon = {
    id: snowflakeId(), templateId: t.id, title: t.title, type: t.type, threshold: t.threshold, amount: t.amount, scope: t.scope,
    status: 'UNUSED', validFrom: new Date(now).toISOString(), validTo: new Date(now + t.validDays * 86400000).toISOString(),
  }
  ;(db().userCoupons[userId] ||= []).unshift(c)
  t.received++
  return c
}

route('GET', '/coupons/templates', 'optional', ({ user }) => db().couponTemplates.map((t) => decorateTemplate(t, user)))

route('POST', '/coupons/templates/:id/receive', 'user', ({ params, user }) => {
  const t = db().couponTemplates.find((x) => x.id === params.id)
  if (!t) throw new MockError(4001, '优惠券不存在')
  if (Date.now() > new Date(t.endAt).getTime()) throw new MockError(4002, '活动已结束')
  // 真实实现：Redis Lua 原子判断「库存 > 0 且用户领取次数 < 限领」再扣减，异步落库
  if (t.received >= t.total) throw new MockError(4003, '手慢了，优惠券已抢光')
  const mine = (db().userCoupons[user!.id] || []).filter((c) => c.templateId === t.id).length
  if (mine >= t.perUserLimit) throw new MockError(4004, '已达领取上限')
  return grantCoupon(user!.id, t)
})

route('GET', '/coupons/mine', 'user', ({ user, query }) => {
  const list = db().userCoupons[user!.id] || []
  const now = Date.now()
  list.forEach((c) => {
    if (c.status === 'UNUSED' && new Date(c.validTo).getTime() < now) c.status = 'EXPIRED'
  })
  return query.status ? list.filter((c) => c.status === query.status) : list
})

/* ============================== 订单 ============================== */
const FULL_RULES = [{ threshold: 30000, reduce: 5000 }] // 跨店每满 300 减 50
const POINTS_RATE = 100 // 100 积分 = 1 元
const MAX_POINTS_RATIO = 0.2

function buildItems(reqItems: Array<{ skuId: string; count: number }>): Array<OrderItem & { root: number }> {
  return reqItems.map((it) => {
    const f = findSku(it.skuId)
    if (!f) throw new MockError(2001, '商品不存在')
    return {
      skuId: it.skuId, spuId: f.spu.id, name: f.spu.name, specText: specText(f.sku.specs), art: f.spu.art, tint: f.spu.tint,
      price: f.sku.price, count: it.count, root: rootCategoryOf(f.spu.categoryId),
    }
  })
}

function couponEligibleAmount(items: Array<OrderItem & { root: number }>, templateId: string) {
  const root = couponScopeRoot[templateId]
  return items.filter((i) => root === undefined || i.root === root).reduce((s, i) => s + i.price * i.count, 0)
}

function couponDeductOf(c: UserCoupon, eligible: number) {
  if (eligible < c.threshold || eligible <= 0) return 0
  if (c.type === 'DISCOUNT') return Math.round((eligible * (100 - c.amount)) / 100)
  return Math.min(c.amount, eligible)
}

function calcPrice(items: Array<OrderItem & { root: number }>, coupon: UserCoupon | undefined, pointsUsed: number, pointsBalance: number) {
  const goodsAmount = items.reduce((s, i) => s + i.price * i.count, 0)
  const fullReduction = Math.min(5, Math.floor(goodsAmount / FULL_RULES[0].threshold)) * FULL_RULES[0].reduce
  const couponDeduct = coupon ? couponDeductOf(coupon, couponEligibleAmount(items, coupon.templateId)) : 0
  const freight = goodsAmount >= 9900 ? 0 : 1000
  const afterDiscount = Math.max(0, goodsAmount - fullReduction - couponDeduct)
  const maxPoints = Math.floor((afterDiscount * MAX_POINTS_RATIO) / 100) * POINTS_RATE // 积分上限（按整元）
  const usable = Math.min(pointsUsed, maxPoints, Math.floor(pointsBalance / POINTS_RATE) * POINTS_RATE)
  const pointsDeduct = Math.floor(usable / POINTS_RATE) * 100
  const price: OrderPriceDetail = {
    goodsAmount, freight, fullReduction, couponDeduct, pointsDeduct, seckillDeduct: 0,
    payAmount: Math.max(1, afterDiscount - pointsDeduct + freight),
  }
  return { price, pointsUsed: (pointsDeduct / 100) * POINTS_RATE, maxPoints }
}

route('POST', '/orders/preview', 'user', ({ body, user }) => {
  const items = buildItems(body.items)
  const coupons = (db().userCoupons[user!.id] || []).filter((c) => c.status === 'UNUSED' && new Date(c.validTo).getTime() > Date.now())
  const available: UserCoupon[] = []
  const unavailable: Array<UserCoupon & { reason: string }> = []
  for (const c of coupons) {
    const eligible = couponEligibleAmount(items, c.templateId)
    if (eligible <= 0) unavailable.push({ ...c, reason: '所结算商品不在适用范围内' })
    else if (eligible < c.threshold) unavailable.push({ ...c, reason: `还差 ¥${((c.threshold - eligible) / 100).toFixed(2)} 可用` })
    else available.push(c)
  }
  const best = [...available].sort((a, b) => couponDeductOf(b, couponEligibleAmount(items, b.templateId)) - couponDeductOf(a, couponEligibleAmount(items, a.templateId)))[0]
  const couponId = body.couponId === undefined ? best?.id : body.couponId || undefined
  const coupon = available.find((c) => c.id === couponId)
  const { price, maxPoints } = calcPrice(items, coupon, body.pointsUsed ?? 0, user!.points)
  const token = snowflakeId()
  db().submitTokens.push(token)
  const preview: OrderPreview & { maxPoints: number; selectedCouponId?: string } = {
    items: items.map(({ root: _r, ...i }) => i),
    address: (db().addresses[user!.id] || []).find((a) => a.isDefault),
    availableCoupons: available,
    unavailableCoupons: unavailable,
    bestCouponId: best?.id,
    selectedCouponId: coupon?.id,
    fullReductionRules: FULL_RULES,
    pointsBalance: user!.points,
    pointsRate: POINTS_RATE,
    maxPointsRatio: MAX_POINTS_RATIO,
    maxPoints,
    price,
    submitToken: token,
  }
  return preview
})

route('POST', '/orders', 'user', ({ body, user }) => {
  const req = body as SubmitOrderReq
  // 幂等：submitToken 一次性（真实实现：Redis DEL 返回 1 才放行）
  const ti = db().submitTokens.indexOf(req.submitToken)
  if (ti < 0) throw new MockError(5001, '请勿重复提交订单')
  db().submitTokens.splice(ti, 1)
  if (user!.creditScore < 300) throw new MockError(5009, '账户存在异常行为，已被风控拦截')
  const address = (db().addresses[user!.id] || []).find((a) => a.id === req.addressId)
  if (!address) throw new MockError(5002, '请选择收货地址')
  const items = buildItems(req.items)
  // 库存预扣减（真实实现：Redis Lua 判断+扣减原子执行；Seata AT 保证订单与库存服务一致）
  for (const it of items) {
    const f = findSku(it.skuId)!
    if (f.sku.stock < it.count) throw new MockError(3001, `「${f.spu.name}」库存不足`)
  }
  for (const it of items) {
    const f = findSku(it.skuId)!
    f.sku.stock -= it.count
    f.sku.sales += it.count
  }
  const coupon = req.couponId ? (db().userCoupons[user!.id] || []).find((c) => c.id === req.couponId && c.status === 'UNUSED') : undefined
  if (req.couponId && !coupon) throw new MockError(4005, '优惠券不可用')
  const { price, pointsUsed } = calcPrice(items, coupon, req.pointsUsed, user!.points)
  if (coupon) coupon.status = 'LOCKED'
  if (pointsUsed) addPoints(user!.id, -pointsUsed, '订单积分抵扣')
  const now = Date.now()
  const order: Order = {
    id: snowflakeId(), userId: user!.id, status: 'PENDING_PAYMENT', source: 'NORMAL',
    items: items.map(({ root: _r, ...i }) => i), price, address, couponId: coupon?.id, pointsUsed, remark: req.remark,
    createdAt: new Date(now).toISOString(), payDeadline: new Date(now + ORDER_TTL).toISOString(),
  }
  db().orders.unshift(order)
  if (req.fromCart) {
    const c = cartOf(user!.id)
    req.items.forEach((i) => delete c[i.skuId])
  }
  opsLog('ORDER', `[下单] 订单 ${order.id} 创建成功 → 投递延迟消息 order.close.delay（TTL 30min）`)
  return { orderId: order.id, payAmount: price.payAmount, payDeadline: order.payDeadline }
})

route('GET', '/orders', 'user', ({ user, query }) => {
  let list = db().orders.filter((o) => o.userId === user!.id)
  if (query.status) list = list.filter((o) => (query.status === 'CLOSED' ? ['CLOSED', 'CANCELLED'].includes(o.status) : o.status === query.status))
  if (query.keyword) list = list.filter((o) => o.id.includes(query.keyword) || o.items.some((i) => i.name.includes(query.keyword)))
  const page = Number(query.page || 1)
  const size = Number(query.size || 10)
  return { records: list.slice((page - 1) * size, page * size), total: list.length, page, size } satisfies PageResult<Order>
})

route('GET', '/orders/counts', 'user', ({ user }) => {
  const list = db().orders.filter((o) => o.userId === user!.id)
  const count = (s: OrderStatus) => list.filter((o) => o.status === s).length
  return { PENDING_PAYMENT: count('PENDING_PAYMENT'), PAID: count('PAID'), SHIPPED: count('SHIPPED'), COMPLETED: count('COMPLETED') }
})

function ownOrder(id: string, user: MockUser) {
  const o = db().orders.find((x) => x.id === id)
  if (!o || (o.userId !== user.id && user.role !== 'ADMIN')) throw new MockError(5004, '订单不存在')
  return o
}

route('GET', '/orders/:id', 'user', ({ params, user }) => ownOrder(params.id, user!))

route('POST', '/orders/:id/cancel', 'user', ({ params, user }) => {
  const o = ownOrder(params.id, user!)
  if (o.status !== 'PENDING_PAYMENT') throw new MockError(5005, '当前状态不可取消')
  closeOrder(o, '用户主动取消', 'CANCELLED')
  return true
})

route('POST', '/orders/:id/confirm', 'user', ({ params, user }) => {
  const o = ownOrder(params.id, user!)
  if (o.status !== 'SHIPPED') throw new MockError(5006, '当前状态不可确认收货')
  o.status = 'COMPLETED'
  o.completedAt = nowIso()
  if (o.logistics && o.logistics.state !== 'SIGNED') {
    o.logistics.state = 'SIGNED'
    o.logistics.traces.unshift({ time: nowIso(), state: 'SIGNED', desc: '用户已确认收货', location: o.address.city })
  }
  if (o.couponId) {
    const c = db().userCoupons[o.userId]?.find((x) => x.id === o.couponId)
    if (c) c.status = 'USED'
  }
  const earned = Math.floor(o.price.payAmount / 100)
  addPoints(o.userId, earned, `订单 ${o.id} 确认收货奖励`)
  return { earnedPoints: earned }
})

/* ============================== 支付 ============================== */
function signOf(paymentNo: string, amount: number, status: string) {
  // 沙箱验签模拟：真实环境为支付宝 RSA2 / 微信 V3 平台证书验签
  let h = 0
  for (const ch of `${paymentNo}|${amount}|${status}|mock-secret`) h = (h * 31 + ch.charCodeAt(0)) | 0
  return (h >>> 0).toString(16)
}

route('POST', '/payments', 'user', ({ body, user }) => {
  const { orderId, channel } = body as { orderId: string; channel: PayChannel }
  const o = ownOrder(orderId, user!)
  if (o.status !== 'PENDING_PAYMENT') throw new MockError(6001, o.status === 'CLOSED' ? '订单已超时关闭' : '订单状态不可支付')
  // 同一订单同一渠道复用未完成的流水
  let p = db().payments.find((x) => x.orderId === orderId && x.channel === channel && x.status === 'PENDING')
  if (!p) {
    const paymentNo = `P${snowflakeId()}`
    p = {
      paymentNo, orderId, channel, amount: o.price.payAmount, status: 'PENDING',
      qrCode: `${channel === 'ALIPAY' ? 'https://qr.alipay-sandbox.com/' : 'weixin://wxpay/bizpayurl?pr='}${paymentNo}`,
      expireAt: o.payDeadline, callbackCount: 0,
    }
    db().payments.unshift(p)
  }
  const { callbackCount: _c, ...vo } = p
  return vo satisfies PayOrderVO
})

route('GET', '/payments/:paymentNo', 'user', ({ params }) => {
  const p = db().payments.find((x) => x.paymentNo === params.paymentNo)
  if (!p) throw new MockError(6002, '支付流水不存在')
  return { status: p.status, orderId: p.orderId, callbackCount: p.callbackCount }
})

/**
 * 沙箱「扫码支付」：生成带签名的回调报文（真实环境由支付宝/微信服务器发起）
 */
route('POST', '/payments/:paymentNo/sandbox-pay', 'user', ({ params, body }) => {
  const p = db().payments.find((x) => x.paymentNo === params.paymentNo)
  if (!p) throw new MockError(6002, '支付流水不存在')
  const status = body.success ? 'TRADE_SUCCESS' : 'TRADE_CLOSED'
  return { paymentNo: p.paymentNo, amount: p.amount, tradeStatus: status, sign: signOf(p.paymentNo, p.amount, status) }
})

/** 支付回调：验签 → 幂等（支付流水号唯一索引）→ 状态机流转 */
route('POST', '/payments/notify', 'none', ({ body }) => {
  const { paymentNo, amount, tradeStatus, sign } = body as { paymentNo: string; amount: number; tradeStatus: string; sign: string }
  if (signOf(paymentNo, amount, tradeStatus) !== sign) {
    opsLog('PAY', `[回调] ${paymentNo} 验签失败，拒绝处理`)
    throw new MockError(6003, '验签失败')
  }
  const p = db().payments.find((x) => x.paymentNo === paymentNo)
  if (!p) throw new MockError(6002, '支付流水不存在')
  p.callbackCount++
  if (p.status !== 'PENDING') {
    opsLog('PAY', `[回调] ${paymentNo} 第 ${p.callbackCount} 次回调，状态已为 ${p.status}，幂等忽略`)
    return { result: 'success', idempotent: true, callbackCount: p.callbackCount }
  }
  if (amount !== p.amount) throw new MockError(6004, '金额不一致')
  const o = db().orders.find((x) => x.id === p.orderId)!
  if (tradeStatus === 'TRADE_SUCCESS') {
    if (o.status !== 'PENDING_PAYMENT') {
      // 已关单后才到账：标记异常单，走自动退款
      p.status = 'CLOSED'
      opsLog('PAY', `[回调] ${paymentNo} 订单已关闭仍收到支付，进入自动退款流程`)
      return { result: 'success', refund: true }
    }
    p.status = 'SUCCESS'
    p.paidAt = nowIso()
    o.status = 'PAID'
    o.paidAt = p.paidAt
    o.payChannel = p.channel
    o.paymentNo = p.paymentNo
    opsLog('PAY', `[回调] ${paymentNo} 验签通过，UPDATE ... WHERE status='PENDING' 成功，订单 ${o.id} → 已支付`)
    mockBus.publish(o.userId, { type: 'ORDER_STATUS', orderId: o.id, status: 'PAID', text: '支付成功，商家正在备货' })
  } else {
    p.status = 'FAILED'
    opsLog('PAY', `[回调] ${paymentNo} 支付失败，订单保持待支付可重新发起`)
  }
  return { result: 'success', idempotent: false, callbackCount: p.callbackCount }
})

/* ============================== 秒杀（四层防护） ============================== */
const pathTokens = new Map<string, { path: string; expire: number }>()
const buckets = new Map<string, { tokens: number; ts: number }>()
const BUCKET_CAP = 5
const BUCKET_RATE = 2 // 每秒补充令牌数（单用户维度演示；网关为全局 1000 QPS）

function takeToken(key: string) {
  const now = Date.now()
  const b = buckets.get(key) ?? { tokens: BUCKET_CAP, ts: now }
  b.tokens = Math.min(BUCKET_CAP, b.tokens + ((now - b.ts) / 1000) * BUCKET_RATE)
  b.ts = now
  const ok = b.tokens >= 1
  if (ok) b.tokens -= 1
  buckets.set(key, b)
  return ok
}

route('GET', '/seckill/sessions', 'optional', () => {
  const sessions = seckillSessions()
  return sessions.map((s) => ({ ...s, items: seckillItems(s) }))
})

route('GET', '/seckill/items/:activityId', 'optional', ({ params, user }) => {
  const item = findSeckillItem(params.activityId)
  if (!item) throw new MockError(7001, '秒杀活动不存在')
  const bought = user ? (db().seckillBought[item.activityId] || []).includes(user.id) : false
  return { ...item, bought, serverTime: Date.now() }
})

/** 获取动态秒杀地址（活动开始后才下发，60s 有效） */
route('GET', '/seckill/:activityId/path', 'user', ({ params, user }) => {
  const item = findSeckillItem(params.activityId)
  if (!item) throw new MockError(7001, '秒杀活动不存在')
  if (item.status !== 'ONGOING') throw new MockError(7002, item.status === 'UPCOMING' ? '活动未开始' : '活动已结束')
  const path = Array.from({ length: 4 }, () => Math.random().toString(16).slice(2, 10)).join('')
  pathTokens.set(`${user!.id}:${item.activityId}`, { path, expire: Date.now() + 60000 })
  return { path }
})

route('POST', '/seckill/:activityId/:path/orders', 'user', ({ params, user, body }) => {
  const m = db().seckillMetrics
  m.totalRequests++
  const key = `${user!.id}:${params.activityId}`
  // 第一层：网关令牌桶限流
  if (!takeToken(user!.id)) {
    m.rateLimited++
    throw new MockError(4290, '当前抢购人数过多，请稍后再试')
  }
  // 动态 URL 校验
  const tk = pathTokens.get(key)
  if (!tk || tk.path !== params.path || tk.expire < Date.now()) {
    m.illegalPath++
    throw new MockError(7003, '秒杀地址无效或已过期')
  }
  // 风控：信用分前置拦截
  const risk = db().risk.find((r) => r.userId === user!.id)
  if (user!.creditScore < 300 || risk?.blocked) {
    throw new MockError(7009, '账户存在异常行为，暂不能参与秒杀')
  }
  const item = findSeckillItem(params.activityId)
  if (!item || item.status !== 'ONGOING') throw new MockError(7002, '活动未开始或已结束')
  // 第二层：Lua 原子脚本 —— SISMEMBER 一人一单 → DECR 库存 → SADD
  const bought = (db().seckillBought[item.activityId] ||= [])
  if (bought.includes(user!.id)) {
    m.repeatBlocked++
    throw new MockError(7004, '每人限购 1 件，您已抢到过啦')
  }
  const left = (db().seckillStock[item.activityId] ?? item.totalStock) - 1
  if (left < 0) {
    m.soldOut++
    throw new MockError(7005, '已抢完，下次早点来哦')
  }
  db().seckillStock[item.activityId] = left
  bought.push(user!.id)
  pathTokens.delete(key)
  // 第三层：投递 Kafka，立即返回排队中
  m.enqueued++
  const orderId = snowflakeId()
  db().seckillResults[key] = { state: 'QUEUING', orderId }
  opsLog('SECKILL', `[Lua] ${item.activityId} 扣减成功 剩余 ${left} → Kafka topic seckill-order 投递 orderId=${orderId}`)
  const addressId = body?.addressId as string | undefined
  setTimeout(() => consumeSeckill(user!.id, item.activityId, orderId, addressId), 900 + Math.random() * 1200)
  return { state: 'QUEUING', orderId } satisfies SeckillResult
})

/** 第四层：消费者异步落库（Redisson 锁 + 乐观锁 UPDATE ... WHERE num > 0） */
function consumeSeckill(userId: string, activityId: string, orderId: string, addressId?: string) {
  const d = getDb()
  const key = `${userId}:${activityId}`
  const item = findSeckillItem(activityId)!
  const dbLeft = d.seckillDbStock[activityId] ?? item.totalStock
  if (dbLeft <= 0) {
    d.seckillMetrics.optimisticFail++
    d.seckillResults[key] = { state: 'FAILED', reason: '库存不足（DB 乐观锁兜底拦截）' }
    persist()
    return
  }
  d.seckillDbStock[activityId] = dbLeft - 1
  const user = d.users.find((u) => u.id === userId)!
  const addrs = d.addresses[userId] || []
  const address = addrs.find((a) => a.id === addressId) ?? addrs.find((a) => a.isDefault) ?? addrs[0] ?? {
    id: 'TEMP', receiver: user.nickname, phone: user.phone, province: '', city: '', district: '', detail: '请在订单中补充收货地址', isDefault: false,
  }
  const found = findSku(item.skuId)!
  const now = Date.now()
  const price: OrderPriceDetail = {
    goodsAmount: found.sku.price, freight: 0, fullReduction: 0, couponDeduct: 0, pointsDeduct: 0,
    seckillDeduct: found.sku.price - item.seckillPrice, payAmount: item.seckillPrice,
  }
  d.orders.unshift({
    id: orderId, userId, status: 'PENDING_PAYMENT', source: 'SECKILL',
    items: [{ skuId: item.skuId, spuId: item.spuId, name: item.name, specText: specText(found.sku.specs), art: item.art, tint: item.tint, price: found.sku.price, count: 1 }],
    price, address, pointsUsed: 0, createdAt: new Date(now).toISOString(), payDeadline: new Date(now + SECKILL_ORDER_TTL).toISOString(),
  })
  d.seckillMetrics.ordersCreated++
  d.seckillResults[key] = { state: 'SUCCESS', orderId }
  opsLog('SECKILL', `[消费] 订单 ${orderId} 落库成功，ACK 消息；DB 库存剩余 ${dbLeft - 1}`)
  mockBus.publish(userId, { type: 'SECKILL_RESULT', activityId, orderId, success: true })
  persist()
}

route('GET', '/seckill/:activityId/result', 'user', ({ params, user }) => {
  return db().seckillResults[`${user!.id}:${params.activityId}`] ?? { state: 'FAILED', reason: '未查询到抢购记录' }
})

/* ============================== 积分 ============================== */
route('GET', '/points', 'user', ({ user }) => ({ balance: user!.points, records: db().points[user!.id] || [] }))
route('GET', '/points/goods', 'none', () => db().pointsGoods)
route('POST', '/points/goods/:id/exchange', 'user', ({ params, user }) => {
  const g = db().pointsGoods.find((x) => x.id === params.id)
  if (!g) throw new MockError(8001, '商品不存在')
  if (g.stock <= 0) throw new MockError(8002, '已兑完')
  if (user!.points < g.points) throw new MockError(8003, '积分不足')
  g.stock--
  addPoints(user!.id, -g.points, `兑换「${g.name}」`)
  if (g.id === 'PG1') grantCoupon(user!.id, { ...db().couponTemplates.find((t) => t.id === 'CT04')!, title: '积分兑换 5 元券', amount: 500 })
  return { balance: user!.points }
})

/* ============================== 后台 ============================== */
route('GET', '/admin/dashboard', 'admin', () => {
  const orders = db().orders
  const today = new Date().toDateString()
  const paidStatuses: OrderStatus[] = ['PAID', 'SHIPPED', 'COMPLETED']
  const statusCount = { PENDING_PAYMENT: 0, PAID: 0, SHIPPED: 0, COMPLETED: 0, CANCELLED: 0, CLOSED: 0 } as Record<OrderStatus, number>
  orders.forEach((o) => statusCount[o.status]++)
  const todays = orders.filter((o) => new Date(o.createdAt).toDateString() === today)
  const last7Days = Array.from({ length: 7 }, (_, k) => {
    const d = new Date(Date.now() - (6 - k) * 86400000)
    const list = orders.filter((o) => new Date(o.createdAt).toDateString() === d.toDateString() && paidStatuses.includes(o.status))
    return { date: `${d.getMonth() + 1}/${d.getDate()}`, gmv: list.reduce((s, o) => s + o.price.payAmount, 0), orders: list.length }
  })
  const stats: DashboardStats = {
    todayGmv: todays.filter((o) => paidStatuses.includes(o.status)).reduce((s, o) => s + o.price.payAmount, 0),
    todayOrders: todays.length,
    pendingShip: statusCount.PAID,
    conversionRate: todays.length ? todays.filter((o) => paidStatuses.includes(o.status)).length / todays.length : 0,
    statusCount,
    last7Days,
  }
  return stats
})

route('GET', '/admin/orders', 'admin', ({ query }) => {
  let list = db().orders
  if (query.status) list = list.filter((o) => o.status === query.status)
  if (query.source) list = list.filter((o) => o.source === query.source)
  if (query.keyword) list = list.filter((o) => o.id.includes(query.keyword) || o.address.receiver.includes(query.keyword) || o.address.phone.includes(query.keyword))
  const page = Number(query.page || 1)
  const size = Number(query.size || 10)
  return { records: list.slice((page - 1) * size, page * size), total: list.length, page, size } satisfies PageResult<Order>
})

route('POST', '/admin/orders/:id/ship', 'admin', ({ params, body }) => {
  const o = db().orders.find((x) => x.id === params.id)
  if (!o || o.status !== 'PAID') throw new MockError(9001, '仅待发货订单可发货')
  o.status = 'SHIPPED'
  o.shippedAt = nowIso()
  const company = (body.company as string) || '顺丰速运'
  o.logistics = {
    company, trackingNo: (body.trackingNo as string) || `SF${Date.now().toString().slice(-12)}`, state: 'COLLECTED',
    traces: [{ time: nowIso(), state: 'COLLECTED', desc: `${company}已揽收，快件正在发往【西安集散中心】`, location: '西安市' }],
  }
  opsLog('ORDER', `[发货] 订单 ${o.id} 已发货，订阅快递鸟轨迹推送 ${o.logistics.trackingNo}`)
  mockBus.publish(o.userId, { type: 'ORDER_STATUS', orderId: o.id, status: 'SHIPPED', text: '您的订单已发货' })
  mockBus.publish(o.userId, { type: 'LOGISTICS_UPDATE', orderId: o.id, trace: o.logistics.traces[0] })
  return true
})

/** 仅 Mock：把 TTL 立即置为到期，演示延迟队列关单 */
route('POST', '/admin/orders/:id/expire', 'admin', ({ params }) => {
  const o = db().orders.find((x) => x.id === params.id)
  if (!o || o.status !== 'PENDING_PAYMENT') throw new MockError(9002, '仅待支付订单可模拟超时')
  o.payDeadline = new Date(Date.now() - 1000).toISOString()
  lastTick = 0
  tick()
  return true
})

route('GET', '/admin/products', 'admin', ({ query }) => {
  let list = db().spus
  if (query.keyword) list = list.filter((s) => s.name.includes(query.keyword) || s.id.includes(query.keyword))
  if (query.status) list = list.filter((s) => s.status === query.status)
  return list
})

route('PUT', '/admin/products/:id/status', 'admin', ({ params, body }) => {
  const s = db().spus.find((x) => x.id === params.id)
  if (!s) throw new MockError(2001, '商品不存在')
  s.status = body.status
  delayDoubleDelete(s.id)
  return true
})

route('PUT', '/admin/skus/:id', 'admin', ({ params, body }) => {
  const f = findSku(params.id)
  if (!f) throw new MockError(2001, 'SKU 不存在')
  if (body.price !== undefined) f.sku.price = body.price
  if (body.stock !== undefined) f.sku.stock = body.stock
  f.spu.minPrice = Math.min(...f.spu.skus.map((k) => k.price))
  delayDoubleDelete(f.spu.id)
  return true
})

function delayDoubleDelete(spuId: string) {
  delete db().productCache[spuId]
  opsLog('CACHE', `[延迟双删] UPDATE DB → DEL product:detail:${spuId}（第 1 次）`)
  setTimeout(() => {
    delete getDb().productCache[spuId]
    opsLog('CACHE', `[延迟双删] 500ms 后 DEL product:detail:${spuId}（第 2 次），防并发读回填旧值`)
    persist()
  }, 500)
}

route('GET', '/admin/categories', 'admin', () => buildTree(adminCategories))
route('POST', '/admin/categories', 'admin', ({ body }) => {
  const id = Math.max(...adminCategories.map((c) => c.id)) + 1
  const c: Category = { id, parentId: body.parentId ?? 0, name: body.name, sort: 99 }
  adminCategories.push(c)
  return c
})
route('PUT', '/admin/categories/:id', 'admin', ({ params, body }) => {
  const c = adminCategories.find((x) => x.id === Number(params.id))
  if (!c) throw new MockError(9003, '类目不存在')
  c.name = body.name
  return true
})
route('DELETE', '/admin/categories/:id', 'admin', ({ params }) => {
  const id = Number(params.id)
  if (adminCategories.some((c) => c.parentId === id)) throw new MockError(9004, '存在子类目，不能删除')
  if (db().spus.some((s) => s.categoryId === id)) throw new MockError(9005, '类目下存在商品，不能删除')
  adminCategories = adminCategories.filter((c) => c.id !== id)
  return true
})

route('GET', '/admin/coupons', 'admin', () => db().couponTemplates)
route('POST', '/admin/coupons', 'admin', ({ body }) => {
  const t: CouponTemplate = { ...(body as CouponTemplate), id: `CT${snowflakeId().slice(-6)}`, received: 0 }
  db().couponTemplates.unshift(t)
  return t
})

route('GET', '/admin/seckill', 'admin', () => {
  const sessions = seckillSessions()
  return {
    sessions: sessions.map((s) => ({
      ...s,
      items: seckillItems(s).map((i) => ({
        ...i,
        warmed: db().seckillStock[i.activityId] !== undefined,
        redisStock: db().seckillStock[i.activityId] ?? null,
        dbStock: db().seckillDbStock[i.activityId] ?? null,
      })),
    })),
    metrics: db().seckillMetrics,
  }
})

route('POST', '/admin/seckill/:activityId/warmup', 'admin', ({ params }) => {
  const item = findSeckillItem(params.activityId)
  if (!item) throw new MockError(7001, '活动不存在')
  db().seckillStock[item.activityId] = item.totalStock
  db().seckillDbStock[item.activityId] = item.totalStock
  db().seckillBought[item.activityId] = []
  opsLog('SECKILL', `[预热] SET seckill:stock:${item.activityId} ${item.totalStock}；DEL seckill:bought:${item.activityId}`)
  return true
})

/** 仅 Mock：模拟 N 个并发用户抢购，验证无超卖 */
route('POST', '/admin/seckill/:activityId/simulate', 'admin', ({ params, body }) => {
  const item = findSeckillItem(params.activityId)
  if (!item || item.status !== 'ONGOING') throw new MockError(7002, '仅进行中的活动可模拟')
  const n = Math.min(Number(body.users) || 500, 5000)
  const d = db()
  if (d.seckillStock[item.activityId] === undefined) warmUp(item.activityId, item.totalStock)
  let success = 0
  let soldOut = 0
  let limited = 0
  const bought = (d.seckillBought[item.activityId] ||= [])
  for (let i = 0; i < n; i++) {
    d.seckillMetrics.totalRequests++
    if (Math.random() < 0.18) { limited++; d.seckillMetrics.rateLimited++; continue }
    const uid = `sim-${Date.now()}-${i % Math.ceil(n * 0.9)}` // 10% 重复用户
    if (bought.includes(uid)) { d.seckillMetrics.repeatBlocked++; continue }
    if ((d.seckillStock[item.activityId] ?? 0) - 1 < 0) { soldOut++; d.seckillMetrics.soldOut++; continue }
    d.seckillStock[item.activityId]--
    bought.push(uid)
    success++
    d.seckillMetrics.enqueued++
    d.seckillMetrics.ordersCreated++
    d.seckillDbStock[item.activityId] = (d.seckillDbStock[item.activityId] ?? item.totalStock) - 1
  }
  opsLog('SECKILL', `[模拟] ${n} 个请求：成功 ${success} / 售罄 ${soldOut} / 限流 ${limited}，Redis 剩余 ${d.seckillStock[item.activityId]}`)
  return { requests: n, success, soldOut, limited, redisLeft: d.seckillStock[item.activityId], dbLeft: d.seckillDbStock[item.activityId] }
})

/* OM-013 / OM-019：mock 模式下可靠性视图恒为健康、无死信（演练需真实后端） */
let mockConsumerPaused = false
route('GET', '/admin/seckill/health', 'admin', () => ({
  consumer: { running: true, paused: mockConsumerPaused },
  lag: 0,
  stuckSeconds: 180,
  autoReset: true,
  inflight: [],
  deadLetters: { new: 0, total: 0 },
}))
route('GET', '/admin/seckill/dead-letters', 'admin', () => [])
route('POST', '/admin/seckill/dead-letters/:id/resolve', 'admin', () => {
  throw new MockError(7011, '死信不存在或已处理')
})
route('POST', '/admin/seckill/:activityId/inflight/reset', 'admin', () => ({ reset: false, inflight: 0 }))
route('POST', '/admin/seckill/consumer/:action', 'admin', ({ params }) => {
  mockConsumerPaused = params.action === 'pause'
  opsLog('ALERT', `[演练] ${mockConsumerPaused ? '暂停' : '恢复'}秒杀主消费者（mock）`)
  return { paused: mockConsumerPaused }
})

route('GET', '/admin/cache/check', 'admin', () => {
  const rows: CacheCheckRow[] = Object.keys(db().seckillStock).slice(-12).map((k) => ({
    key: `seckill:stock:${k}`,
    redisValue: db().seckillStock[k] ?? null,
    dbValue: db().seckillDbStock[k] ?? 0,
    consistent: (db().seckillStock[k] ?? 0) === (db().seckillDbStock[k] ?? 0),
  }))
  const { hit, miss } = db().cacheStats
  return { rows, hitRate: hit + miss ? hit / (hit + miss) : 0, hit, miss }
})

route('POST', '/admin/cache/repair', 'admin', ({ body }) => {
  const key = String(body.key).replace('seckill:stock:', '')
  db().seckillStock[key] = db().seckillDbStock[key]
  opsLog('CACHE', `[一致性校验] ${body.key} 以 DB 为准修复 Redis = ${db().seckillDbStock[key]}`)
  return true
})

route('GET', '/admin/risk', 'admin', () => [...db().risk].sort((a, b) => a.creditScore - b.creditScore))
route('PUT', '/admin/risk/:userId', 'admin', ({ params, body }) => {
  const r = db().risk.find((x) => x.userId === params.userId)
  if (!r) throw new MockError(9006, '用户不存在')
  r.blocked = body.blocked
  opsLog('RISK', `[风控] 用户 ${r.nickname} ${body.blocked ? '加入' : '移出'}黑名单`)
  return true
})

route('GET', '/admin/logs', 'admin', ({ query }) => (query.type ? db().opsLogs.filter((l) => l.type === query.type) : db().opsLogs).slice(0, 100))

route('POST', '/admin/mock/reset', 'admin', () => {
  resetDb()
  adminCategories = seedCategories.map((c) => ({ ...c }))
  return true
})

/** 模拟后台定时任务线程（关单消费者 / 物流回调） */
setInterval(() => {
  tick()
  persist()
}, 2000)
