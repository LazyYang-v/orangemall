import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import MallLayout from '@/layouts/MallLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 需要登录 */
    auth?: boolean
    /** 需要 ADMIN 角色 */
    admin?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: MallLayout,
    children: [
      { path: '', name: 'home', component: () => import('@/views/home/HomeView.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/search/SearchView.vue'), meta: { title: '商品搜索' } },
      { path: 'product/:id', name: 'product', component: () => import('@/views/product/ProductDetailView.vue'), meta: { title: '商品详情' } },
      { path: 'cart', name: 'cart', component: () => import('@/views/cart/CartView.vue'), meta: { title: '购物车' } },
      { path: 'checkout', name: 'checkout', component: () => import('@/views/checkout/CheckoutView.vue'), meta: { title: '确认订单', auth: true } },
      { path: 'pay/result/:orderId', name: 'pay-result', component: () => import('@/views/pay/PayResultView.vue'), meta: { title: '支付结果', auth: true } },
      { path: 'pay/:orderId', name: 'pay', component: () => import('@/views/pay/PayView.vue'), meta: { title: '收银台', auth: true } },
      { path: 'orders', name: 'orders', component: () => import('@/views/order/OrderListView.vue'), meta: { title: '我的订单', auth: true } },
      { path: 'orders/:id', name: 'order-detail', component: () => import('@/views/order/OrderDetailView.vue'), meta: { title: '订单详情', auth: true } },
      { path: 'seckill', name: 'seckill', component: () => import('@/views/seckill/SeckillListView.vue'), meta: { title: '限时秒杀' } },
      { path: 'seckill/:activityId', name: 'seckill-detail', component: () => import('@/views/seckill/SeckillDetailView.vue'), meta: { title: '秒杀详情' } },
      { path: 'coupons', name: 'coupons', component: () => import('@/views/coupon/CouponCenterView.vue'), meta: { title: '领券中心' } },
      { path: 'points', name: 'points', component: () => import('@/views/points/PointsView.vue'), meta: { title: '积分商城' } },
      { path: 'brands', name: 'brands', component: () => import('@/views/search/BrandsView.vue'), meta: { title: '品牌馆' } },
      { path: 'rank', name: 'rank', component: () => import('@/views/search/RankView.vue'), meta: { title: '排行榜' } },
      {
        path: 'user',
        component: () => import('@/views/user/UserLayout.vue'),
        meta: { title: '个人中心', auth: true },
        children: [
          { path: '', name: 'user', component: () => import('@/views/user/UserOverview.vue') },
          { path: 'coupons', name: 'user-coupons', component: () => import('@/views/user/MyCouponsView.vue'), meta: { title: '我的优惠券' } },
          { path: 'refunds', name: 'user-refunds', component: () => import('@/views/user/RefundsView.vue'), meta: { title: '我的售后' } },
          { path: 'favorites', name: 'user-favorites', component: () => import('@/views/user/FavoritesView.vue'), meta: { title: '我的收藏' } },
          { path: 'addresses', name: 'user-addresses', component: () => import('@/views/user/AddressView.vue'), meta: { title: '收货地址' } },
        ],
      },
      { path: '404', name: 'not-found', component: () => import('@/views/error/NotFoundView.vue'), meta: { title: '页面不存在' } },
    ],
  },
  {
    path: '/',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [
      { path: 'login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { title: '登录' } },
      { path: 'register', name: 'register', component: () => import('@/views/auth/RegisterView.vue'), meta: { title: '注册' } },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { admin: true },
    children: [
      { path: '', name: 'admin', component: () => import('@/views/admin/DashboardView.vue'), meta: { title: '数据看板' } },
      { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/OrdersView.vue'), meta: { title: '订单管理' } },
      { path: 'refunds', name: 'admin-refunds', component: () => import('@/views/admin/RefundsView.vue'), meta: { title: '售后管理' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'products', name: 'admin-products', component: () => import('@/views/admin/ProductsView.vue'), meta: { title: '商品 / SKU' } },
      { path: 'categories', name: 'admin-categories', component: () => import('@/views/admin/CategoriesView.vue'), meta: { title: '类目树' } },
      { path: 'coupons', name: 'admin-coupons', component: () => import('@/views/admin/CouponsView.vue'), meta: { title: '优惠券模板' } },
      { path: 'seckill', name: 'admin-seckill', component: () => import('@/views/admin/SeckillView.vue'), meta: { title: '秒杀活动' } },
      { path: 'cache', name: 'admin-cache', component: () => import('@/views/admin/CacheView.vue'), meta: { title: '缓存一致性' } },
      { path: 'risk', name: 'admin-risk', component: () => import('@/views/admin/RiskView.vue'), meta: { title: '风控黑名单' } },
      { path: 'logs', name: 'admin-logs', component: () => import('@/views/admin/LogsView.vue'), meta: { title: '链路日志' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'not-found' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.matched.some((r) => r.meta.auth || r.meta.admin) && !user.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.matched.some((r) => r.meta.admin) && user.profile && !user.isAdmin) return '/'
  return true
})

router.afterEach((to) => {
  const t = [...to.matched].reverse().find((r) => r.meta.title)?.meta.title
  document.title = t ? `${t} - 橙选商城` : '橙选商城 OrangeMall · 好物精选 低价放心'
})

export default router
