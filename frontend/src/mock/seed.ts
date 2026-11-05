/**
 * Mock 种子数据：结构与后端表设计一致（category 邻接表、SPU/SKU、coupon_template ...）。
 */
import type {
  ArtKind, Brand, Category, CouponTemplate, Notice, PointsGoods, Sku, SpecGroup, Spu,
} from '@/types/domain'

export const categories: Category[] = [
  { id: 1, parentId: 0, name: '数码家电', icon: 'phone', sort: 1 },
  { id: 2, parentId: 0, name: '服饰鞋包', icon: 'shirt', sort: 2 },
  { id: 3, parentId: 0, name: '家居生活', icon: 'sofa', sort: 3 },
  { id: 4, parentId: 0, name: '美妆个护', icon: 'lipstick', sort: 4 },
  { id: 5, parentId: 0, name: '食品生鲜', icon: 'apple', sort: 5 },
  { id: 6, parentId: 0, name: '母婴玩具', icon: 'baby', sort: 6 },
  { id: 7, parentId: 0, name: '运动户外', icon: 'bike', sort: 7 },
  { id: 8, parentId: 0, name: '汽车用品', icon: 'car', sort: 8 },
  { id: 9, parentId: 0, name: '图书文娱', icon: 'book', sort: 9 },
  { id: 10, parentId: 0, name: '医药保健', icon: 'pill', sort: 10 },
  // 二级
  { id: 101, parentId: 1, name: '手机', sort: 1 },
  { id: 102, parentId: 1, name: '电脑', sort: 2 },
  { id: 103, parentId: 1, name: '耳机', sort: 3 },
  { id: 104, parentId: 1, name: '智能穿戴', sort: 4 },
  { id: 105, parentId: 1, name: '摄影摄像', sort: 5 },
  { id: 106, parentId: 1, name: '生活电器', sort: 6 },
  { id: 201, parentId: 2, name: '男装', sort: 1 },
  { id: 202, parentId: 2, name: '女装', sort: 2 },
  { id: 203, parentId: 2, name: '运动鞋', sort: 3 },
  { id: 204, parentId: 2, name: '箱包', sort: 4 },
  { id: 301, parentId: 3, name: '家具', sort: 1 },
  { id: 302, parentId: 3, name: '家纺', sort: 2 },
  { id: 303, parentId: 3, name: '收纳', sort: 3 },
  { id: 401, parentId: 4, name: '护肤', sort: 1 },
  { id: 402, parentId: 4, name: '彩妆', sort: 2 },
  { id: 403, parentId: 4, name: '香水', sort: 3 },
  { id: 501, parentId: 5, name: '零食', sort: 1 },
  { id: 502, parentId: 5, name: '水果', sort: 2 },
  { id: 503, parentId: 5, name: '酒饮', sort: 3 },
  { id: 601, parentId: 6, name: '奶粉', sort: 1 },
  { id: 602, parentId: 6, name: '纸尿裤', sort: 2 },
  { id: 603, parentId: 6, name: '玩具', sort: 3 },
  { id: 701, parentId: 7, name: '健身', sort: 1 },
  { id: 702, parentId: 7, name: '户外', sort: 2 },
  { id: 703, parentId: 7, name: '骑行', sort: 3 },
  { id: 801, parentId: 8, name: '车载', sort: 1 },
  { id: 802, parentId: 8, name: '养护', sort: 2 },
  { id: 803, parentId: 8, name: '配件', sort: 3 },
  { id: 901, parentId: 9, name: '图书', sort: 1 },
  { id: 902, parentId: 9, name: '影音', sort: 2 },
  { id: 903, parentId: 9, name: '文创', sort: 3 },
  { id: 1001, parentId: 10, name: '保健', sort: 1 },
  { id: 1002, parentId: 10, name: '器械', sort: 2 },
  { id: 1003, parentId: 10, name: '防护', sort: 3 },
  // 三级（演示多级递归）
  { id: 10101, parentId: 101, name: '5G 手机', sort: 1 },
  { id: 10102, parentId: 101, name: '游戏手机', sort: 2 },
  { id: 10201, parentId: 102, name: '轻薄本', sort: 1 },
  { id: 10202, parentId: 102, name: '游戏本', sort: 2 },
]

export const brands: Brand[] = [
  { id: 1, name: '果仁科技' },
  { id: 2, name: '联想' },
  { id: 3, name: '索尼' },
  { id: 4, name: '声悦' },
  { id: 5, name: '跃动' },
  { id: 6, name: '慢咖' },
  { id: 7, name: '栖木家居' },
  { id: 8, name: '北岭' },
  { id: 9, name: '花研' },
  { id: 10, name: '行者' },
  { id: 11, name: '小橙优选' },
]

interface SpuSeed {
  id: string
  name: string
  subtitle: string
  categoryId: number
  brandId: number
  art: ArtKind
  tint: string
  basePrice: number // 元
  originalPrice: number // 元
  sales: number
  tags: string[]
  specGroups: SpecGroup[]
  priceStep?: Record<string, number> // 规格值加价（元）
  isNew?: boolean
  params: Array<[string, string]>
}

const spuSeeds: SpuSeed[] = [
  {
    id: '1001', name: '果仁 Phone 17 Pro 5G 全网通', subtitle: 'A19 Pro 芯片 · 钛金属机身 · 4800 万三摄', categoryId: 10101, brandId: 1,
    art: 'phone', tint: '#8f7cf0', basePrice: 7999, originalPrice: 8999, sales: 52310, tags: ['双11爆款', '24期免息', '官方正品'],
    specGroups: [{ name: '颜色', values: ['星光紫', '曜石黑', '冰川银'] }, { name: '容量', values: ['256G', '512G', '1TB'] }],
    priceStep: { '512G': 1500, '1TB': 3000 }, params: [['屏幕', '6.3 英寸 OLED'], ['处理器', 'A19 Pro'], ['电池', '4300mAh'], ['重量', '199g']],
  },
  {
    id: '1002', name: '声悦 Buds 3 主动降噪真无线耳机', subtitle: '48dB 深度降噪 · 空间音频 · 36h 续航', categoryId: 103, brandId: 4,
    art: 'earbuds', tint: '#e9edf3', basePrice: 199, originalPrice: 299, sales: 128800, tags: ['秒杀', '降噪'],
    specGroups: [{ name: '颜色', values: ['云朵白', '石墨黑'] }], params: [['降噪', '主动降噪 48dB'], ['续航', '36 小时'], ['蓝牙', '5.4']],
  },
  {
    id: '1003', name: '果仁 Watch S10 智能手表', subtitle: '血氧心率 · 双频 GPS · 14 天续航', categoryId: 104, brandId: 1,
    art: 'watch', tint: '#2d2f36', basePrice: 599, originalPrice: 799, sales: 33100, tags: ['秒杀', '运动健康'],
    specGroups: [{ name: '颜色', values: ['午夜黑', '星光金'] }, { name: '尺寸', values: ['42mm', '46mm'] }],
    priceStep: { '46mm': 100 }, params: [['屏幕', '1.96 英寸 AMOLED'], ['防水', '5ATM'], ['续航', '14 天']],
  },
  {
    id: '1004', name: '花研 晨露淡香水 50ml', subtitle: '白茶 · 小苍兰 · 清新木质调', categoryId: 403, brandId: 9,
    art: 'perfume', tint: '#e8b86a', basePrice: 269, originalPrice: 399, sales: 18700, tags: ['秒杀', '礼盒装'],
    specGroups: [{ name: '规格', values: ['50ml', '100ml'] }], priceStep: { '100ml': 180 }, params: [['香调', '木质花香调'], ['净含量', '50ml']],
  },
  {
    id: '1005', name: '跃动 M3 无线静音鼠标', subtitle: '三模连接 · 静音微动 · 人体工学', categoryId: 102, brandId: 5,
    art: 'mouse', tint: '#25262b', basePrice: 79, originalPrice: 129, sales: 90210, tags: ['秒杀', '办公'],
    specGroups: [{ name: '颜色', values: ['曜石黑', '雾霾蓝'] }], params: [['连接', '蓝牙/2.4G/有线'], ['DPI', '800-4000']],
  },
  {
    id: '1006', name: '索尼 微单 A7C II 全画幅相机', subtitle: '轻巧全画幅 · 影像新升级 · 3300 万像素', categoryId: 105, brandId: 3,
    art: 'camera', tint: '#1d1f24', basePrice: 13999, originalPrice: 14999, sales: 2310, tags: ['新品首发', '12期免息'], isNew: true,
    specGroups: [{ name: '套装', values: ['单机身', '28-60mm 套机'] }, { name: '颜色', values: ['黑色', '银色'] }],
    priceStep: { '28-60mm 套机': 1500 }, params: [['传感器', '全画幅 3300 万'], ['防抖', '7 档机身防抖'], ['视频', '4K 60p']],
  },
  {
    id: '1007', name: '声悦 Studio Max 头戴式降噪耳机', subtitle: 'Hi-Res 认证 · 60h 超长续航', categoryId: 103, brandId: 4,
    art: 'headphones', tint: '#d9d4cc', basePrice: 1299, originalPrice: 1699, sales: 15400, tags: ['降噪', '跨店满减'],
    specGroups: [{ name: '颜色', values: ['燕麦白', '深空灰'] }], params: [['驱动单元', '40mm'], ['续航', '60 小时']],
  },
  {
    id: '1008', name: '跃动 Cloud 轻量缓震跑鞋', subtitle: '超临界发泡中底 · 透气网面', categoryId: 203, brandId: 5,
    art: 'sneaker', tint: '#f2f2f2', basePrice: 459, originalPrice: 699, sales: 41200, tags: ['运动', '跨店满减'],
    specGroups: [{ name: '颜色', values: ['云白', '黑武士'] }, { name: '尺码', values: ['39', '40', '41', '42', '43', '44'] }],
    params: [['鞋面', '工程网布'], ['中底', '超临界 EVA']],
  },
  {
    id: '1009', name: '慢咖 Pro 半自动意式咖啡机', subtitle: '20Bar 意式萃取 · 蒸汽打奶', categoryId: 106, brandId: 6,
    art: 'coffee', tint: '#3a3b40', basePrice: 1899, originalPrice: 2499, sales: 6120, tags: ['家电', '分期免息'],
    specGroups: [{ name: '颜色', values: ['钛金灰', '奶油白'] }], params: [['压力', '20Bar'], ['水箱', '1.8L']],
  },
  {
    id: '1010', name: '栖木 云朵模块沙发 三人位', subtitle: '科技布 · 高回弹海绵 · 可拆洗', categoryId: 301, brandId: 7,
    art: 'sofa', tint: '#d8cfc2', basePrice: 3299, originalPrice: 4599, sales: 3820, tags: ['大件包邮', '送货安装'],
    specGroups: [{ name: '颜色', values: ['奶咖色', '燕麦色'] }, { name: '尺寸', values: ['2.4m', '2.8m'] }],
    priceStep: { '2.8m': 600 }, params: [['面料', '科技布'], ['填充', '高回弹海绵']],
  },
  {
    id: '1011', name: '北岭 三合一冲锋衣 男女同款', subtitle: '防水防风 · 可拆卸抓绒内胆', categoryId: 201, brandId: 8,
    art: 'jacket', tint: '#c9b79c', basePrice: 699, originalPrice: 1299, sales: 22800, tags: ['户外', '双11爆款'],
    specGroups: [{ name: '颜色', values: ['卡其', '墨绿', '黑色'] }, { name: '尺码', values: ['S', 'M', 'L', 'XL'] }],
    params: [['防水指数', '10000mm'], ['内胆', '摇粒绒']],
  },
  {
    id: '1012', name: '花研 修护精华水乳套装', subtitle: '烟酰胺 + 积雪草 · 舒缓修护', categoryId: 401, brandId: 9,
    art: 'skincare', tint: '#e9d7b5', basePrice: 329, originalPrice: 489, sales: 27800, tags: ['美妆', '礼盒装'],
    specGroups: [{ name: '规格', values: ['标准装', '加量装'] }], priceStep: { 加量装: 120 }, params: [['适用肤质', '所有肤质'], ['保质期', '3 年']],
  },
  {
    id: '1013', name: '行者 铝框拉杆箱 20 寸', subtitle: 'PC 箱体 · 静音万向轮 · TSA 海关锁', categoryId: 204, brandId: 10,
    art: 'luggage', tint: '#9aa0a8', basePrice: 499, originalPrice: 899, sales: 11300, tags: ['出行', '跨店满减'],
    specGroups: [{ name: '颜色', values: ['钛灰', '雅黑'] }, { name: '尺寸', values: ['20 寸', '24 寸', '28 寸'] }],
    priceStep: { '24 寸': 100, '28 寸': 200 }, params: [['材质', 'PC + 铝框'], ['锁具', 'TSA 海关锁']],
  },
  {
    id: '1014', name: '联想 ThinkBook 14+ 轻薄笔记本', subtitle: '酷睿 Ultra 7 · 2.8K 120Hz · 32G', categoryId: 10201, brandId: 2,
    art: 'laptop', tint: '#7b8796', basePrice: 5999, originalPrice: 6999, sales: 9870, tags: ['办公', '12期免息'],
    specGroups: [{ name: '配置', values: ['Ultra5/16G/1T', 'Ultra7/32G/1T'] }], priceStep: { 'Ultra7/32G/1T': 1200 },
    params: [['屏幕', '14.5 英寸 2.8K'], ['重量', '1.38kg']],
  },
  {
    id: '1015', name: '小橙优选 每日坚果 30 袋装', subtitle: '7 种坚果果干 · 独立小包装', categoryId: 501, brandId: 11,
    art: 'snack', tint: '#d99a4a', basePrice: 69, originalPrice: 99, sales: 210300, tags: ['食品', '回购榜'],
    specGroups: [{ name: '规格', values: ['30 袋', '60 袋'] }], priceStep: { '60 袋': 60 }, params: [['保质期', '180 天'], ['净含量', '750g']],
  },
  {
    id: '1016', name: '小橙优选 益智积木城堡 1200 粒', subtitle: '兼容大颗粒 · 6 岁以上', categoryId: 603, brandId: 11,
    art: 'toy', tint: '#f2b134', basePrice: 239, originalPrice: 399, sales: 8230, tags: ['母婴', '礼物'],
    specGroups: [{ name: '款式', values: ['城堡款', '太空款'] }], params: [['颗粒数', '1200'], ['适用年龄', '6+']],
  },
  {
    id: '1017', name: '《高并发系统设计实战》', subtitle: '从秒杀到分布式事务 · 附源码', categoryId: 901, brandId: 11,
    art: 'book', tint: '#ff6a00', basePrice: 79, originalPrice: 119, sales: 15600, tags: ['图书', '程序员必读'],
    specGroups: [{ name: '版本', values: ['平装', '精装'] }], priceStep: { 精装: 30 }, params: [['页数', '512'], ['出版社', '橙选出版']],
  },
  {
    id: '1018', name: '行者 碳纤维公路自行车', subtitle: '22 速 · 7.8kg 轻量车架', categoryId: 703, brandId: 10,
    art: 'bike', tint: '#2e7df6', basePrice: 4599, originalPrice: 5999, sales: 1530, tags: ['骑行', '送货上门'],
    specGroups: [{ name: '车架尺寸', values: ['S', 'M', 'L'] }], params: [['车架', 'T800 碳纤维'], ['变速', '22 速']],
  },
  {
    id: '1019', name: '小橙优选 车载无线快充支架', subtitle: '15W 快充 · 自动夹紧', categoryId: 801, brandId: 11,
    art: 'car', tint: '#3f4652', basePrice: 129, originalPrice: 199, sales: 19800, tags: ['车载'],
    specGroups: [{ name: '安装方式', values: ['出风口', '吸盘'] }], params: [['功率', '15W'], ['接口', 'Type-C']],
  },
  {
    id: '1020', name: '北岭 复合维生素片 120 粒', subtitle: '23 种营养素 · 全家适用', categoryId: 1001, brandId: 8,
    art: 'pill', tint: '#3cbf8a', basePrice: 159, originalPrice: 229, sales: 31200, tags: ['保健'],
    specGroups: [{ name: '规格', values: ['120 粒', '240 粒'] }], priceStep: { '240 粒': 120 }, params: [['规格', '1.2g × 120 粒']],
  },
  {
    id: '1021', name: '果仁 Phone 17 标准版', subtitle: 'A19 芯片 · 6.1 英寸 · 超瓷晶面板', categoryId: 10101, brandId: 1,
    art: 'phone', tint: '#5cb8f2', basePrice: 5999, originalPrice: 6499, sales: 40800, tags: ['24期免息'],
    specGroups: [{ name: '颜色', values: ['天蓝', '黑色', '白色'] }, { name: '容量', values: ['128G', '256G'] }],
    priceStep: { '256G': 800 }, params: [['屏幕', '6.1 英寸 OLED'], ['处理器', 'A19']],
  },
  {
    id: '1022', name: '联想 拯救者 Y9000P 游戏本', subtitle: 'RTX 5070 · 2.5K 240Hz 电竞屏', categoryId: 10202, brandId: 2,
    art: 'laptop', tint: '#2b2f38', basePrice: 9999, originalPrice: 11499, sales: 7120, tags: ['游戏', '12期免息'], isNew: true,
    specGroups: [{ name: '配置', values: ['i9/16G/1T', 'i9/32G/2T'] }], priceStep: { 'i9/32G/2T': 1500 },
    params: [['显卡', 'RTX 5070'], ['屏幕', '16 英寸 2.5K 240Hz']],
  },
  {
    id: '1023', name: '栖木 60 支长绒棉四件套', subtitle: 'A 类母婴级 · 裸睡级亲肤', categoryId: 302, brandId: 7,
    art: 'sofa', tint: '#c7d6e6', basePrice: 399, originalPrice: 699, sales: 26500, tags: ['家纺', '跨店满减'],
    specGroups: [{ name: '尺寸', values: ['1.5m 床', '1.8m 床'] }, { name: '颜色', values: ['雾蓝', '奶白'] }],
    params: [['面料', '60 支长绒棉'], ['安全等级', 'A 类']],
  },
  {
    id: '1024', name: '慢咖 挂耳咖啡 精品阿拉比卡 50 包', subtitle: '中深烘焙 · 坚果可可风味', categoryId: 503, brandId: 6,
    art: 'coffee', tint: '#8b5a3c', basePrice: 89, originalPrice: 139, sales: 66300, tags: ['食品', '回购榜'],
    specGroups: [{ name: '烘焙度', values: ['中深烘', '浅烘'] }], params: [['净含量', '10g × 50'], ['产地', '云南保山']],
  },
]

const shopNames: Record<number, string> = {
  1: '果仁科技官方旗舰店', 2: '联想官方旗舰店', 3: '索尼影像旗舰店', 4: '声悦音频旗舰店', 5: '跃动运动旗舰店',
  6: '慢咖咖啡旗舰店', 7: '栖木家居旗舰店', 8: '北岭户外旗舰店', 9: '花研美妆旗舰店', 10: '行者出行旗舰店', 11: '橙选自营',
}

function cartesian(groups: SpecGroup[]): Array<Record<string, string>> {
  return groups.reduce<Array<Record<string, string>>>(
    (acc, g) => acc.flatMap((combo) => g.values.map((v) => ({ ...combo, [g.name]: v }))),
    [{}],
  )
}

function categoryPath(id: number): string[] {
  const path: string[] = []
  let cur = categories.find((c) => c.id === id)
  while (cur) {
    path.unshift(cur.name)
    const pid = cur.parentId
    cur = pid ? categories.find((c) => c.id === pid) : undefined
  }
  return path
}

export function buildSpus(): Spu[] {
  return spuSeeds.map((s) => {
    const combos = cartesian(s.specGroups)
    const skus: Sku[] = combos.map((specs, i) => {
      const extra = Object.values(specs).reduce((sum, v) => sum + (s.priceStep?.[v] ?? 0), 0)
      // 让少量 SKU 处于低库存 / 无库存，便于演示库存校验
      const stock = i === combos.length - 1 && combos.length > 2 ? 0 : i % 4 === 3 ? 3 : 50 + ((i * 37) % 200)
      return {
        id: `${s.id}${String(i + 1).padStart(2, '0')}`,
        spuId: s.id,
        specs,
        price: (s.basePrice + extra) * 100,
        originalPrice: (s.originalPrice + extra) * 100,
        stock,
        sales: Math.floor(s.sales / combos.length),
      }
    })
    return {
      id: s.id,
      name: s.name,
      subtitle: s.subtitle,
      categoryId: s.categoryId,
      categoryPath: categoryPath(s.categoryId),
      brandId: s.brandId,
      brandName: brands.find((b) => b.id === s.brandId)?.name ?? '',
      art: s.art,
      tint: s.tint,
      minPrice: Math.min(...skus.map((k) => k.price)),
      maxOriginalPrice: Math.max(...skus.map((k) => k.originalPrice)),
      sales: s.sales,
      rating: 4.6 + ((Number(s.id) * 7) % 4) / 10,
      tags: s.tags,
      status: 'ON_SALE',
      specGroups: s.specGroups,
      skus,
      detail: [
        `${s.name}，${s.subtitle}。`,
        '官方正品保障，假一赔十；全国多仓发货，现货次日达。',
        '支持 7 天无理由退换，购买后可在「我的订单」中实时查看物流轨迹。',
      ],
      params: [['品牌', brands.find((b) => b.id === s.brandId)?.name ?? ''], ...s.params],
      isNew: s.isNew,
      shopName: shopNames[s.brandId],
    }
  })
}

const now = Date.now()
const day = 86400000
const iso = (t: number) => new Date(t).toISOString()

export function buildCouponTemplates(): CouponTemplate[] {
  return [
    { id: 'CT01', title: '双11 全场通用券', type: 'FULL_REDUCTION', threshold: 49900, amount: 5000, scope: '全场通用（秒杀商品除外）', total: 10000, received: 6820, validDays: 7, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 10 * day) },
    { id: 'CT02', title: '双11 大额神券', type: 'FULL_REDUCTION', threshold: 99900, amount: 10000, scope: '全场通用（秒杀商品除外）', total: 5000, received: 4410, validDays: 7, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 10 * day) },
    { id: 'CT03', title: '数码家电品类券', type: 'FULL_REDUCTION', threshold: 300000, amount: 30000, scope: '限数码家电品类', total: 2000, received: 1200, validDays: 5, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 6 * day) },
    { id: 'CT04', title: '新人无门槛券', type: 'NO_THRESHOLD', threshold: 0, amount: 1000, scope: '新用户专享', total: 20000, received: 9300, validDays: 3, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 20 * day) },
    { id: 'CT05', title: '美妆个护 9 折券', type: 'DISCOUNT', threshold: 20000, amount: 90, scope: '限美妆个护品类', total: 3000, received: 2990, validDays: 7, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 8 * day) },
    { id: 'CT06', title: '食品生鲜满减券', type: 'FULL_REDUCTION', threshold: 9900, amount: 1500, scope: '限食品生鲜品类', total: 8000, received: 3100, validDays: 7, perUserLimit: 2, startAt: iso(now - day), endAt: iso(now + 9 * day) },
    { id: 'CT07', title: '服饰鞋包满减券', type: 'FULL_REDUCTION', threshold: 39900, amount: 6000, scope: '限服饰鞋包品类', total: 6000, received: 6000, validDays: 7, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 9 * day) },
    { id: 'CT08', title: '家居生活满减券', type: 'FULL_REDUCTION', threshold: 199900, amount: 20000, scope: '限家居生活品类', total: 1500, received: 400, validDays: 7, perUserLimit: 1, startAt: iso(now - day), endAt: iso(now + 9 * day) },
  ]
}

/** 优惠券适用品类（根类目 ID，空 = 全场） */
export const couponScopeRoot: Record<string, number | undefined> = {
  CT03: 1, CT05: 4, CT06: 5, CT07: 2, CT08: 3,
}

export function buildNotices(): Notice[] {
  return [
    { id: 'N1', title: '双11大促活动正式开启！', date: '2026-10-28', content: '跨店满 300 减 50，叠加平台券最高省 1000 元。' },
    { id: 'N2', title: '关于部分地区物流时效调整的通知', date: '2026-10-26', content: '大促期间部分偏远地区配送时效延长 1-2 天。' },
    { id: 'N3', title: '新用户专享礼包上线，注册即领！', date: '2026-10-25', content: '注册即送 10 元无门槛券 + 200 积分。' },
    { id: 'N4', title: '秒杀规则说明：每人每场限购 1 件', date: '2026-10-22', content: '同一账号、设备、收货地址视为同一用户。' },
  ]
}

export function buildPointsGoods(): PointsGoods[] {
  return [
    { id: 'PG1', name: '5 元无门槛券', art: 'book', tint: '#ff6a00', points: 500, stock: 999 },
    { id: 'PG2', name: '小橙定制帆布袋', art: 'luggage', tint: '#f3a25b', points: 1200, stock: 120 },
    { id: 'PG3', name: '每日坚果体验装', art: 'snack', tint: '#d99a4a', points: 800, stock: 300 },
    { id: 'PG4', name: '声悦 Buds 3 耳机', art: 'earbuds', tint: '#e9edf3', points: 19900, stock: 8 },
    { id: 'PG5', name: '挂耳咖啡 10 包', art: 'coffee', tint: '#8b5a3c', points: 990, stock: 260 },
    { id: 'PG6', name: '积木钥匙扣', art: 'toy', tint: '#f2b134', points: 600, stock: 0 },
  ]
}

export const HOT_KEYWORDS = ['iPhone17', '联想笔记本', '运动鞋', '羽绒服', '咖啡机', '双11']
