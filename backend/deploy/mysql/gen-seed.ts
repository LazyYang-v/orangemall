/**
 * 由前端 Mock 种子（frontend/src/mock/seed.ts + db.ts 中的演示数据）生成 MySQL 初始化数据，
 * 保证真实后端与 Mock 的商品、券、积分商品、演示账号完全一致。
 *
 * Phase 4 起种子是各服务的 Flyway 迁移 db/migration/V2__seed.sql（已冻结：盐值/时间每次生成都不同，
 * 改动已发布的 V2 会让 Flyway 校验和失败）。只在全新搭建时重新生成；存量环境要改种子请新增 V<n> 迁移。
 *   node deploy/mysql/gen-seed.ts            # 输出到 stdout（按 USE mall_xxx; 分段）
 *   node deploy/mysql/gen-seed.ts --write    # 直接写入 backend/mall-<svc>/src/main/resources/db/migration/V2__seed.sql
 */
import { createHash, randomBytes } from 'node:crypto'
import {
  brands, buildCouponTemplates, buildNotices, buildPointsGoods, buildSpus, categories, couponScopeRoot,
} from '../../../frontend/src/mock/seed.ts'

const q = (v: unknown): string => {
  if (v === null || v === undefined) return 'NULL'
  if (typeof v === 'number') return String(v)
  if (typeof v === 'boolean') return v ? '1' : '0'
  return `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`
}
const ago = (sec: number) => `NOW(3) - INTERVAL ${Math.round(sec)} SECOND`
const later = (sec: number) => `NOW(3) + INTERVAL ${Math.round(sec)} SECOND`
const DAY = 86400
const out: string[] = ['-- 由 gen-seed.ts 生成，请勿手改', 'SET NAMES utf8mb4;']
const insert = (table: string, cols: string[], rows: string[][]) => {
  if (!rows.length) return
  out.push(`INSERT INTO ${table} (${cols.join(', ')}) VALUES\n  ${rows.map((r) => `(${r.join(', ')})`).join(',\n  ')};`)
}

/* ---------- 用户 ---------- */
const pwd = (p: string) => {
  const salt = randomBytes(8).toString('hex')
  return [q(createHash('sha256').update(`${salt}:${p}`).digest('hex')), q(salt)]
}
const U1 = '1880000000000000001'
const U2 = '1880000000000000002'
out.push('USE mall_user;')
insert('user', ['id', 'phone', 'password_hash', 'salt', 'nickname', 'level', 'points', 'credit_score', 'blocked', 'risk_hits', 'last_action', 'role', 'created_at'], [
  [U1, q('13800000000'), ...pwd('123456'), q('橙子用户'), '3', '2680', '720', '0', '0', q('正常'), q('USER'), ago(30 * DAY)],
  [U2, q('18800000000'), ...pwd('admin123'), q('运营管理员'), '9', '0', '800', '0', '0', q('正常'), q('ADMIN'), ago(60 * DAY)],
  // 风控演示账号（随机密码，无法登录）
  ['1990000000000000101', q('19900000101'), ...pwd(randomBytes(8).toString('hex')), q('疑似脚本账号A'), '1', '0', '120', '1', '386', q('1 秒内 40 次秒杀请求'), q('USER'), ago(3 * DAY)],
  ['1990000000000000102', q('19900000102'), ...pwd(randomBytes(8).toString('hex')), q('疑似黄牛账号B'), '1', '0', '260', '0', '92', q('同设备 6 个账号下单'), q('USER'), ago(2 * DAY)],
  ['1990000000000000103', q('19900000103'), ...pwd(randomBytes(8).toString('hex')), q('新注册账号C'), '1', '0', '410', '0', '17', q('注册 2 分钟即参与秒杀'), q('USER'), ago(DAY)],
])
const A1 = { id: '1880000000000100001', receiver: '张三', phone: '13800000000', province: '陕西省', city: '汉中市', district: '汉台区', detail: '天汉大道 88 号 橙选小区 3 栋 1201', isDefault: true }
const A2 = { id: '1880000000000100002', receiver: '张三', phone: '13800000000', province: '陕西省', city: '西安市', district: '雁塔区', detail: '科技路 10 号 软件园 B 座 6 层', isDefault: false }
insert('address', ['id', 'user_id', 'receiver', 'phone', 'province', 'city', 'district', 'detail', 'is_default'],
  [A1, A2].map((a) => [a.id, U1, q(a.receiver), q(a.phone), q(a.province), q(a.city), q(a.district), q(a.detail), q(a.isDefault)]))
insert('points_record', ['id', 'user_id', 'change_amount', 'reason', 'created_at'], [
  ['1880000000000200001', U1, '200', q('新用户注册奖励'), ago(20 * DAY)],
  ['1880000000000200002', U1, '2480', q('历史订单确认收货'), ago(6 * DAY)],
])
insert('points_goods', ['id', 'name', 'art', 'tint', 'points', 'stock', 'coupon_template_id', 'coupon_amount', 'coupon_title', 'sort'],
  buildPointsGoods().map((g, i) => [q(g.id), q(g.name), q(g.art), q(g.tint), q(g.points), q(g.stock),
    q(g.id === 'PG1' ? 'CT04' : null), q(g.id === 'PG1' ? 500 : null), q(g.id === 'PG1' ? '积分兑换 5 元券' : null), String(i + 1)]))

/* ---------- 商品 ---------- */
const spus = buildSpus()
out.push('USE mall_product;')
insert('category', ['id', 'parent_id', 'name', 'icon', 'sort'], categories.map((c) => [q(c.id), q(c.parentId), q(c.name), q(c.icon ?? null), q(c.sort)]))
insert('brand', ['id', 'name', 'shop_name'], brands.map((b) => [q(b.id), q(b.name), q(spus.find((s) => s.brandId === b.id)?.shopName ?? '橙选自营')]))
insert('spu', ['id', 'name', 'subtitle', 'category_id', 'brand_id', 'art', 'tint', 'min_price', 'max_original_price', 'sales', 'rating', 'tags', 'status', 'spec_groups', 'detail', 'params', 'is_new', 'shop_name', 'created_at'],
  spus.map((s, i) => [s.id, q(s.name), q(s.subtitle), q(s.categoryId), q(s.brandId), q(s.art), q(s.tint), q(s.minPrice), q(s.maxOriginalPrice), q(s.sales),
    q(Math.round(s.rating * 10) / 10), q(JSON.stringify(s.tags)), q(s.status), q(JSON.stringify(s.specGroups)), q(JSON.stringify(s.detail)),
    q(JSON.stringify(s.params)), q(!!s.isNew), q(s.shopName), ago((spus.length - i) * DAY)]))
insert('sku', ['id', 'spu_id', 'specs', 'price', 'original_price', 'stock', 'sales'],
  spus.flatMap((s) => s.skus.map((k) => [k.id, s.id, q(JSON.stringify(k.specs)), q(k.price), q(k.originalPrice), q(k.stock), q(k.sales)])))
insert('notice', ['id', 'title', 'date', 'content'], buildNotices().map((n) => [q(n.id), q(n.title), q(n.date), q(n.content)]))

/* ---------- 营销 ---------- */
out.push('USE mall_marketing;')
const startOffsets: Record<string, number> = { CT01: 10, CT02: 10, CT03: 6, CT04: 20, CT05: 8, CT06: 9, CT07: 9, CT08: 9 }
insert('coupon_template', ['id', 'title', 'type', 'threshold', 'amount', 'scope', 'scope_root_id', 'total', 'received', 'valid_days', 'per_user_limit', 'start_at', 'end_at'],
  buildCouponTemplates().map((t) => [q(t.id), q(t.title), q(t.type), q(t.threshold), q(t.amount), q(t.scope), q(couponScopeRoot[t.id] ?? null),
    q(t.total), q(t.received), q(t.validDays), q(t.perUserLimit), ago(DAY), later((startOffsets[t.id] ?? 10) * DAY)]))

/* ---------- 订单（历史订单，user1 的分表 = U1 & 3 = 1） ---------- */
out.push('USE mall_order;')
const shardOf = (id: string) => Number(BigInt(id) & 3n)
const userShard = shardOf(U1)
const addressJson = JSON.stringify({ ...A1 })
const pick = (spuId: string, count = 1) => {
  const spu = spus.find((s) => s.id === spuId)!
  const sku = spu.skus[0]
  return { skuId: sku.id, spuId, name: spu.name, specText: Object.values(sku.specs).join(' / '), art: spu.art, tint: spu.tint, price: sku.price, count }
}
type Hist = { id: string; agoSec: number; status: string; items: ReturnType<typeof pick>[] }
const hist: Hist[] = [
  { id: '2120394857100001281', agoSec: 6 * DAY, status: 'COMPLETED', items: [pick('1015'), pick('1024')] },
  { id: '2120394857100002561', agoSec: 2 * DAY, status: 'SHIPPED', items: [pick('1008', 2)] },
  { id: '2120394857100003841', agoSec: 1 * DAY, status: 'PAID', items: [pick('1017')] },
  { id: '2120394857100005121', agoSec: 3 * DAY, status: 'CLOSED', items: [pick('1012')] },
]
const orderRows: string[][] = []
const itemRows: string[][] = []
const isoAgo = (sec: number) => new Date(Date.now() - sec * 1000).toISOString()
for (const h of hist) {
  if (shardOf(h.id) !== userShard) throw new Error(`order ${h.id} gene mismatch`)
  const goods = h.items.reduce((s, i) => s + i.price * i.count, 0)
  const shipped = ['SHIPPED', 'COMPLETED'].includes(h.status)
  const created = h.agoSec
  const logistics = shipped ? {
    company: '顺丰速运', trackingNo: `SF${h.id.slice(-10)}`, state: h.status === 'COMPLETED' ? 'SIGNED' : 'IN_TRANSIT',
    traces: [
      ...(h.status === 'COMPLETED' ? [{ time: isoAgo(created - 2 * DAY), state: 'SIGNED', desc: '已签收，签收人：本人。感谢使用顺丰速运', location: '汉中市' }] : []),
      { time: isoAgo(created - 1.2 * DAY), state: 'IN_TRANSIT', desc: '快件已到达【汉中转运中心】', location: '汉中市' },
      { time: isoAgo(created - 0.3 * DAY), state: 'IN_TRANSIT', desc: '快件已从【西安集散中心】发出', location: '西安市' },
      { time: isoAgo(created - 3600), state: 'COLLECTED', desc: '顺丰速运已收取快件', location: '西安市' },
    ],
  } : null
  const closed = h.status === 'CLOSED'
  orderRows.push([h.id, U1, q(h.status), q('NORMAL'), q(goods), '0', '0', '0', '0', '0', q(goods), '0', q(addressJson),
    ago(created - 1800), closed ? 'NULL' : ago(created - 60), shipped ? ago(created - 3600) : 'NULL',
    h.status === 'COMPLETED' ? ago(created - 2 * DAY) : 'NULL', closed ? ago(created - 1800) : 'NULL',
    q(closed ? '超时未支付，系统自动关闭（延迟队列）' : null), q(closed ? null : 'ALIPAY'), q(closed ? null : `P${h.id}`),
    q(logistics ? JSON.stringify(logistics) : null), ago(created)])
  for (const it of h.items) itemRows.push([h.id, U1, it.skuId, it.spuId, q(it.name), q(it.specText), q(it.art), q(it.tint), q(it.price), q(it.count), ago(created)])
}
insert(`t_order_${userShard}`, ['id', 'user_id', 'status', 'source', 'goods_amount', 'freight', 'full_reduction', 'coupon_deduct', 'points_deduct', 'seckill_deduct', 'pay_amount', 'points_used', 'address_json',
  'pay_deadline', 'paid_at', 'shipped_at', 'completed_at', 'closed_at', 'close_reason', 'pay_channel', 'payment_no', 'logistics_json', 'created_at'], orderRows)
insert(`t_order_item_${userShard}`, ['order_id', 'user_id', 'sku_id', 'spu_id', 'name', 'spec_text', 'art', 'tint', 'price', 'count', 'created_at'], itemRows)

out.push('USE mall_pay;')
insert('payment', ['payment_no', 'order_id', 'user_id', 'channel', 'amount', 'status', 'qr_code', 'expire_at', 'callback_count', 'paid_at', 'created_at'],
  hist.filter((h) => h.status !== 'CLOSED').map((h) => {
    const goods = h.items.reduce((s, i) => s + i.price * i.count, 0)
    return [q(`P${h.id}`), h.id, U1, q('ALIPAY'), q(goods), q('SUCCESS'), q(`https://qr.alipay-sandbox.com/P${h.id}`), ago(h.agoSec - 1800), '1', ago(h.agoSec - 60), ago(h.agoSec)]
  }))

if (process.argv.includes('--write')) {
  const { writeFileSync } = await import('node:fs')
  const { fileURLToPath } = await import('node:url')
  const backend = fileURLToPath(new URL('../../', import.meta.url))
  // out 以 'USE mall_xxx;' 分段；每段写到对应服务的 V2__seed.sql（Flyway 连接时已选库，去掉 USE）
  let db = '', buf: string[] = []
  const flush = () => {
    if (!db) return
    const svc = db.replace('mall_', 'mall-')
    writeFileSync(`${backend}${svc}/src/main/resources/db/migration/V2__seed.sql`,
      `-- ${db} 演示种子（由 deploy/mysql/gen-seed.ts 生成；Flyway 冻结后勿重新生成覆盖）\nSET NAMES utf8mb4;\n\n${buf.join('\n\n')}\n`)
  }
  for (const s of out) {
    const m = /^USE (mall_\w+);$/.exec(s)
    if (m) { flush(); db = m[1]; buf = [] } else if (db) buf.push(s)
  }
  flush()
} else {
  console.log(out.join('\n\n'))
}
