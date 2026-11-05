/**
 * 浏览器内 Mock 数据库：模拟 MySQL 表 + Redis 结构，持久化到 localStorage。
 * 仅在 VITE_USE_MOCK=true 时加载；后端就绪后整个 mock 目录不会进入生产包。
 */
import type {
  Address, CouponTemplate, Notice, Order, PayOrderVO, PointsGoods, PointsRecord, RiskUser,
  SeckillItem, SeckillResult, SeckillSession, Spu, UserCoupon, UserProfile,
} from '@/types/domain'
import { buildCouponTemplates, buildNotices, buildPointsGoods, buildSpus } from './seed'
import { KEYS, storage } from '@/utils/storage'

export interface MockUser extends UserProfile {
  password: string
}

export interface CartField {
  count: number
  checked: boolean
  addedAt: number
}

export interface PaymentRecord extends PayOrderVO {
  callbackCount: number
  paidAt?: string
}

export interface SeckillMetrics {
  totalRequests: number
  rateLimited: number // 第一层：网关令牌桶拒绝
  illegalPath: number // 动态 URL 校验失败
  repeatBlocked: number // 第二层：一人一单拦截
  soldOut: number // 第二层：Lua DECR < 0
  enqueued: number // 第三层：投递 MQ
  ordersCreated: number // 第四层：消费落库成功
  optimisticFail: number // 第四层：乐观锁失败
}

export interface OpsLog {
  time: string
  type: 'CACHE' | 'MQ' | 'PAY' | 'SECKILL' | 'ORDER' | 'RISK'
  text: string
}

export interface MockDb {
  version: number
  users: MockUser[]
  tokens: Record<string, string> // token -> userId
  addresses: Record<string, Address[]>
  spus: Spu[]
  carts: Record<string, Record<string, CartField>> // Redis Hash cart:{userId}
  couponTemplates: CouponTemplate[]
  userCoupons: Record<string, UserCoupon[]>
  orders: Order[]
  payments: PaymentRecord[]
  submitTokens: string[]
  points: Record<string, PointsRecord[]>
  pointsGoods: PointsGoods[]
  notices: Notice[]
  favorites: Record<string, string[]>
  /** 秒杀：Redis 预热库存 seckill:stock:{activityId} */
  seckillStock: Record<string, number>
  /** 秒杀：DB 库存（乐观锁扣减） */
  seckillDbStock: Record<string, number>
  /** 秒杀：一人一单 Set seckill:bought:{activityId} */
  seckillBought: Record<string, string[]>
  seckillResults: Record<string, SeckillResult> // `${activityId}:${userId}`
  seckillMetrics: SeckillMetrics
  /** 商品详情缓存（Cache Aside）：spuId -> 缓存写入时间 */
  productCache: Record<string, number>
  cacheStats: { hit: number; miss: number }
  risk: RiskUser[]
  opsLogs: OpsLog[]
}

const DB_VERSION = 3
const day = 86400000

function seedDb(): MockDb {
  const spus = buildSpus()
  const demoAddress: Address = {
    id: 'A1', receiver: '张三', phone: '13800000000', province: '陕西省', city: '汉中市', district: '汉台区',
    detail: '天汉大道 88 号 橙选小区 3 栋 1201', isDefault: true,
  }
  const db: MockDb = {
    version: DB_VERSION,
    users: [
      { id: '1880000000000000001', nickname: '橙子用户', phone: '13800000000', password: '123456', level: 3, points: 2680, creditScore: 720, role: 'USER' },
      { id: '1880000000000000002', nickname: '运营管理员', phone: '18800000000', password: 'admin123', level: 9, points: 0, creditScore: 800, role: 'ADMIN' },
    ],
    tokens: {},
    addresses: {
      '1880000000000000001': [
        demoAddress,
        { id: 'A2', receiver: '张三', phone: '13800000000', province: '陕西省', city: '西安市', district: '雁塔区', detail: '科技路 10 号 软件园 B 座 6 层', isDefault: false },
      ],
    },
    spus,
    carts: {},
    couponTemplates: buildCouponTemplates(),
    userCoupons: {},
    orders: [],
    payments: [],
    submitTokens: [],
    points: {
      '1880000000000000001': [
        { id: 'P1', change: 200, reason: '新用户注册奖励', createdAt: new Date(Date.now() - 20 * day).toISOString() },
        { id: 'P2', change: 2480, reason: '历史订单确认收货', createdAt: new Date(Date.now() - 6 * day).toISOString() },
      ],
    },
    pointsGoods: buildPointsGoods(),
    notices: buildNotices(),
    favorites: {},
    seckillStock: {},
    seckillDbStock: {},
    seckillBought: {},
    seckillResults: {},
    seckillMetrics: { totalRequests: 0, rateLimited: 0, illegalPath: 0, repeatBlocked: 0, soldOut: 0, enqueued: 0, ordersCreated: 0, optimisticFail: 0 },
    productCache: {},
    cacheStats: { hit: 0, miss: 0 },
    risk: [
      { userId: '1990000000000000101', nickname: '疑似脚本账号A', creditScore: 120, hits: 386, lastAction: '1 秒内 40 次秒杀请求', blocked: true },
      { userId: '1990000000000000102', nickname: '疑似黄牛账号B', creditScore: 260, hits: 92, lastAction: '同设备 6 个账号下单', blocked: false },
      { userId: '1990000000000000103', nickname: '新注册账号C', creditScore: 410, hits: 17, lastAction: '注册 2 分钟即参与秒杀', blocked: false },
      { userId: '1880000000000000001', nickname: '橙子用户', creditScore: 720, hits: 0, lastAction: '正常', blocked: false },
    ],
    opsLogs: [],
  }
  seedHistoryOrders(db, demoAddress)
  return db
}

/** 生成少量历史订单，便于订单中心 / 后台演示 */
function seedHistoryOrders(db: MockDb, address: Address) {
  const uid = '1880000000000000001'
  const pick = (spuId: string, skuIdx = 0) => {
    const spu = db.spus.find((s) => s.id === spuId)!
    const sku = spu.skus[skuIdx]
    return { skuId: sku.id, spuId, name: spu.name, specText: Object.values(sku.specs).join(' / '), art: spu.art, tint: spu.tint, price: sku.price, count: 1 }
  }
  const mk = (id: string, ago: number, status: Order['status'], items: ReturnType<typeof pick>[]): Order => {
    const goods = items.reduce((s, i) => s + i.price * i.count, 0)
    const created = Date.now() - ago
    return {
      id, userId: uid, status, source: 'NORMAL', items, address, pointsUsed: 0,
      price: { goodsAmount: goods, freight: 0, fullReduction: 0, couponDeduct: 0, pointsDeduct: 0, seckillDeduct: 0, payAmount: goods },
      createdAt: new Date(created).toISOString(),
      payDeadline: new Date(created + 30 * 60000).toISOString(),
      paidAt: status === 'CLOSED' ? undefined : new Date(created + 60000).toISOString(),
      shippedAt: ['SHIPPED', 'COMPLETED'].includes(status) ? new Date(created + 3600000).toISOString() : undefined,
      completedAt: status === 'COMPLETED' ? new Date(created + 2 * day).toISOString() : undefined,
      closedAt: status === 'CLOSED' ? new Date(created + 30 * 60000).toISOString() : undefined,
      closeReason: status === 'CLOSED' ? '超时未支付，系统自动关闭（延迟队列）' : undefined,
      payChannel: status === 'CLOSED' ? undefined : 'ALIPAY',
      paymentNo: status === 'CLOSED' ? undefined : `P${id}`,
      logistics: ['SHIPPED', 'COMPLETED'].includes(status)
        ? {
            company: '顺丰速运', trackingNo: `SF${id.slice(-10)}`, state: status === 'COMPLETED' ? 'SIGNED' : 'IN_TRANSIT',
            traces: [
              ...(status === 'COMPLETED' ? [{ time: new Date(created + 2 * day).toISOString(), state: 'SIGNED' as const, desc: '已签收，签收人：本人。感谢使用顺丰速运', location: '汉中市' }] : []),
              { time: new Date(created + 1.2 * day).toISOString(), state: 'IN_TRANSIT', desc: '快件已到达【汉中转运中心】', location: '汉中市' },
              { time: new Date(created + 0.3 * day).toISOString(), state: 'IN_TRANSIT', desc: '快件已从【西安集散中心】发出', location: '西安市' },
              { time: new Date(created + 3600000).toISOString(), state: 'COLLECTED', desc: '顺丰速运已收取快件', location: '西安市' },
            ],
          }
        : undefined,
    }
  }
  db.orders.push(
    mk('2120394857100001281', 6 * day, 'COMPLETED', [pick('1015'), pick('1024')]),
    mk('2120394857100002562', 2 * day, 'SHIPPED', [pick('1008', 2)]),
    mk('2120394857100003843', 1 * day, 'PAID', [pick('1017')]),
    mk('2120394857100005124', 3 * day, 'CLOSED', [pick('1012')]),
  )
}

let db: MockDb = load()

function load(): MockDb {
  const saved = storage.get<MockDb | null>(KEYS.mockDb, null)
  if (saved && saved.version === DB_VERSION) return saved
  const fresh = seedDb()
  storage.set(KEYS.mockDb, fresh)
  return fresh
}

let saveTimer: ReturnType<typeof setTimeout> | undefined
export function persist() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => storage.set(KEYS.mockDb, db), 50)
}

export function getDb(): MockDb {
  return db
}

export function resetDb() {
  db = seedDb()
  storage.set(KEYS.mockDb, db)
}

export function opsLog(type: OpsLog['type'], text: string) {
  db.opsLogs.unshift({ time: new Date().toISOString(), type, text })
  if (db.opsLogs.length > 200) db.opsLogs.length = 200
}

/* ---------------- 秒杀场次：按 2 小时滚动，保证任意时刻都有「进行中」场次 ---------------- */
const SECKILL_TEMPLATE: Array<{ spuId: string; skuIdx: number; price: number; stock: number }> = [
  { spuId: '1002', skuIdx: 0, price: 19900, stock: 100 },
  { spuId: '1003', skuIdx: 0, price: 59900, stock: 50 },
  { spuId: '1004', skuIdx: 0, price: 26900, stock: 80 },
  { spuId: '1005', skuIdx: 0, price: 7900, stock: 200 },
  { spuId: '1001', skuIdx: 0, price: 699900, stock: 10 },
  { spuId: '1007', skuIdx: 0, price: 99900, stock: 30 },
  { spuId: '1015', skuIdx: 0, price: 3990, stock: 500 },
  { spuId: '1013', skuIdx: 0, price: 29900, stock: 60 },
]

const BLOCK = 2 * 3600000

export function seckillSessions(now = Date.now()): SeckillSession[] {
  const tzOffset = new Date().getTimezoneOffset() * 60000
  const cur = Math.floor((now - tzOffset) / BLOCK) * BLOCK + tzOffset
  return [-1, 0, 1, 2].map((k) => {
    const start = cur + k * BLOCK
    const end = start + BLOCK
    const d = new Date(start)
    return {
      id: `S${start}`,
      label: `${String(d.getHours()).padStart(2, '0')}:00`,
      startAt: new Date(start).toISOString(),
      endAt: new Date(end).toISOString(),
      status: now < start ? 'UPCOMING' : now >= end ? 'ENDED' : 'ONGOING',
    }
  })
}

export function seckillItems(session: SeckillSession): SeckillItem[] {
  const offset = Number(session.id.slice(1)) / BLOCK
  return SECKILL_TEMPLATE.map((t, i) => {
    // 不同场次轮换商品顺序
    const tpl = SECKILL_TEMPLATE[(i + offset) % SECKILL_TEMPLATE.length] ?? t
    const spu = db.spus.find((s) => s.id === tpl.spuId)!
    const sku = spu.skus[tpl.skuIdx]
    const activityId = `${session.id}-${sku.id}`
    if (db.seckillStock[activityId] === undefined && session.status !== 'UPCOMING') {
      // 已开始场次自动预热；未开始场次需后台手动「预热」
      warmUp(activityId, tpl.stock, session.status === 'ENDED')
    }
    const remain = db.seckillStock[activityId]
    return {
      activityId,
      sessionId: session.id,
      skuId: sku.id,
      spuId: spu.id,
      name: spu.name,
      art: spu.art,
      tint: spu.tint,
      seckillPrice: tpl.price,
      originalPrice: sku.originalPrice,
      totalStock: tpl.stock,
      remainStock: remain === undefined ? tpl.stock : Math.max(0, remain),
      limitPerUser: 1,
      startAt: session.startAt,
      endAt: session.endAt,
      status: session.status,
    }
  })
}

/** 预热：SET seckill:stock:{activityId} = stock */
export function warmUp(activityId: string, stock: number, ended = false) {
  // 用确定性伪随机模拟「已抢百分比」，让首页进度条更真实
  const seed = [...activityId].reduce((s, c) => s + c.charCodeAt(0), 0)
  const sold = ended ? stock : Math.floor(stock * (0.35 + (seed % 40) / 100))
  db.seckillStock[activityId] = stock - sold
  db.seckillDbStock[activityId] = stock - sold
  persist()
}

export function findSeckillItem(activityId: string): SeckillItem | undefined {
  const now = Date.now()
  for (const s of seckillSessions(now)) {
    const item = seckillItems(s).find((i) => i.activityId === activityId)
    if (item) return item
  }
  return undefined
}
