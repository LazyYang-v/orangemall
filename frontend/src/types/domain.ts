/**
 * 前后端共享的领域模型（与后端 DTO/VO 一一对应）。
 * 约定：所有雪花 ID（订单号、支付流水号、用户 ID 等 Long）一律以 string 传输，
 * 避免 JS Number 超过 2^53 精度丢失 —— 后端需配置 Jackson ToStringSerializer。
 * 金额统一以「分」为单位的整数传输，前端展示时再格式化。
 */

/** 统一响应体 Result<T> */
export interface Result<T> {
  code: number // 0 = 成功
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/* ---------------- 用户 ---------------- */
export interface UserProfile {
  id: string
  nickname: string
  phone: string
  avatar?: string
  level: number
  points: number
  creditScore: number // 信用分（防刷体系）
  role: 'USER' | 'ADMIN'
}

export interface Address {
  id: string
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
}

/* ---------------- 商品 ---------------- */
export interface Category {
  id: number
  parentId: number // 邻接表：0 为根
  name: string
  icon?: string
  sort: number
  children?: Category[]
}

export interface Brand {
  id: number
  name: string
}

/** 规格定义：颜色 / 容量 等 */
export interface SpecGroup {
  name: string
  values: string[]
}

export interface Sku {
  id: string
  spuId: string
  specs: Record<string, string> // { 颜色: '星光色', 容量: '256G' }
  price: number // 分
  originalPrice: number
  stock: number
  sales: number
}

/** 商品图形：前端用矢量插画代替真实图片，后端对接后改为 imageUrl */
export type ArtKind =
  | 'phone' | 'earbuds' | 'watch' | 'perfume' | 'mouse' | 'camera' | 'headphones'
  | 'sneaker' | 'coffee' | 'sofa' | 'jacket' | 'skincare' | 'luggage' | 'laptop'
  | 'snack' | 'toy' | 'book' | 'bike' | 'car' | 'pill'

export interface Spu {
  id: string
  name: string
  subtitle: string
  categoryId: number
  categoryPath: string[]
  brandId: number
  brandName: string
  art: ArtKind
  tint: string // 插画主色
  imageUrl?: string
  minPrice: number
  maxOriginalPrice: number
  sales: number
  rating: number
  tags: string[]
  status: 'ON_SALE' | 'OFF_SALE'
  specGroups: SpecGroup[]
  skus: Sku[]
  detail: string[] // 图文详情段落
  params: Array<[string, string]>
  isNew?: boolean
  shopName: string
}

export type SpuCard = Omit<Spu, 'specGroups' | 'skus' | 'detail' | 'params'>

export interface SearchQuery {
  keyword?: string
  categoryId?: number
  brandId?: number
  sort?: 'default' | 'sales' | 'price_asc' | 'price_desc' | 'new'
  minPrice?: number
  maxPrice?: number
  page?: number
  size?: number
}

/* ---------------- 购物车 ---------------- */
/** Redis Hash: cart:{userId}  field = skuId  value = { count, checked, addedAt } */
export interface CartItem {
  skuId: string
  spuId: string
  name: string
  specText: string
  art: ArtKind
  tint: string
  price: number
  originalPrice: number
  count: number
  checked: boolean
  stock: number
  status: 'NORMAL' | 'OFF_SALE' | 'NO_STOCK' | 'PRICE_CHANGED'
  shopName: string
}

export interface CartCheckIssue {
  skuId: string
  reason: 'OFF_SALE' | 'NO_STOCK' | 'STOCK_NOT_ENOUGH' | 'PRICE_CHANGED'
  message: string
}

/* ---------------- 营销 ---------------- */
export type CouponType = 'FULL_REDUCTION' | 'DISCOUNT' | 'NO_THRESHOLD'

export interface CouponTemplate {
  id: string
  title: string
  type: CouponType
  threshold: number // 满 X 分可用
  amount: number // 减 X 分；折扣券为 85 = 8.5 折
  scope: string // 适用范围描述
  total: number
  received: number
  validDays: number
  perUserLimit: number
  startAt: string
  endAt: string
  receivedByMe?: boolean
}

export type UserCouponStatus = 'UNUSED' | 'USED' | 'EXPIRED' | 'LOCKED'
export interface UserCoupon {
  id: string
  templateId: string
  title: string
  type: CouponType
  threshold: number
  amount: number
  scope: string
  status: UserCouponStatus
  validFrom: string
  validTo: string
}

export interface FullReductionRule {
  threshold: number
  reduce: number
}

/* ---------------- 订单 ---------------- */
/**
 * 订单状态机：
 *   PENDING_PAYMENT ──支付成功──▶ PAID ──发货──▶ SHIPPED ──确认收货──▶ COMPLETED
 *        │
 *        ├──用户取消──▶ CANCELLED
 *        └──30min 未支付（RabbitMQ TTL+DLX）──▶ CLOSED
 */
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED' | 'CLOSED'

export interface OrderItem {
  skuId: string
  spuId: string
  name: string
  specText: string
  art: ArtKind
  tint: string
  price: number
  count: number
}

export interface OrderPriceDetail {
  goodsAmount: number
  freight: number
  fullReduction: number
  couponDeduct: number
  pointsDeduct: number
  seckillDeduct: number
  payAmount: number
}

export interface Order {
  id: string // 雪花 ID
  userId: string
  status: OrderStatus
  source: 'NORMAL' | 'SECKILL'
  items: OrderItem[]
  price: OrderPriceDetail
  address: Address
  couponId?: string
  pointsUsed: number
  remark?: string
  createdAt: string
  payDeadline: string
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  closedAt?: string
  closeReason?: string
  payChannel?: PayChannel
  paymentNo?: string
  logistics?: LogisticsInfo
}

export interface ConfirmItemReq {
  skuId: string
  count: number
}

/** 结算页预览（试算） */
export interface OrderPreview {
  items: OrderItem[]
  address?: Address
  availableCoupons: UserCoupon[]
  unavailableCoupons: Array<UserCoupon & { reason: string }>
  bestCouponId?: string
  fullReductionRules: FullReductionRule[]
  pointsBalance: number
  pointsRate: number // 多少积分抵 1 元
  maxPointsRatio: number // 最多抵扣订单金额比例
  price: OrderPriceDetail
  submitToken: string // 防重复提交令牌（幂等）
}

export interface SubmitOrderReq {
  items: ConfirmItemReq[]
  addressId: string
  couponId?: string
  pointsUsed: number
  remark?: string
  submitToken: string
  fromCart: boolean
}

/* ---------------- 支付 ---------------- */
export type PayChannel = 'ALIPAY' | 'WECHAT'
export type PayStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CLOSED'

export interface PayOrderVO {
  paymentNo: string // 支付流水号（唯一索引）
  orderId: string
  channel: PayChannel
  amount: number
  status: PayStatus
  qrCode: string // 沙箱二维码内容
  expireAt: string
}

/* ---------------- 物流 ---------------- */
export type LogisticsState = 'COLLECTED' | 'IN_TRANSIT' | 'DELIVERING' | 'SIGNED'
export interface LogisticsTrace {
  time: string
  state: LogisticsState
  desc: string
  location: string
}
export interface LogisticsInfo {
  company: string
  trackingNo: string
  state: LogisticsState
  traces: LogisticsTrace[]
}

/* ---------------- 秒杀 ---------------- */
export type SeckillStatus = 'UPCOMING' | 'ONGOING' | 'ENDED'

export interface SeckillSession {
  id: string
  label: string // "10:00"
  startAt: string
  endAt: string
  status: SeckillStatus
}

export interface SeckillItem {
  activityId: string
  sessionId: string
  skuId: string
  spuId: string
  name: string
  art: ArtKind
  tint: string
  seckillPrice: number
  originalPrice: number
  totalStock: number
  remainStock: number // 来自 Redis 预热 key：seckill:stock:{activityId}
  limitPerUser: number
  startAt: string
  endAt: string
  status: SeckillStatus
}

/** 秒杀结果：Redis 扣减成功后进入 MQ，前端轮询结果 */
export type SeckillResultState = 'QUEUING' | 'SUCCESS' | 'FAILED'
export interface SeckillResult {
  state: SeckillResultState
  orderId?: string
  reason?: string
}

/* ---------------- 积分 ---------------- */
export interface PointsRecord {
  id: string
  change: number
  reason: string
  createdAt: string
}

export interface PointsGoods {
  id: string
  name: string
  art: ArtKind
  tint: string
  points: number
  stock: number
}

/* ---------------- 首页 ---------------- */
export interface Notice {
  id: string
  title: string
  date: string
  content: string
}

export interface HomeData {
  hotKeywords: string[]
  notices: Notice[]
  seckillPreview: { endAt: string; items: SeckillItem[] }
  couponPreview: CouponTemplate[]
  newArrival: SpuCard
  banners: Array<{ id: string; title: string; subtitle: string; tag: string; theme: string }>
}

/* ---------------- 后台 ---------------- */
export interface DashboardStats {
  todayGmv: number
  todayOrders: number
  pendingShip: number
  conversionRate: number
  statusCount: Record<OrderStatus, number>
  last7Days: Array<{ date: string; gmv: number; orders: number }>
}

export interface CacheCheckRow {
  key: string
  redisValue: number | null
  dbValue: number
  consistent: boolean
}

export interface RiskUser {
  userId: string
  nickname: string
  creditScore: number
  hits: number
  lastAction: string
  blocked: boolean
}
