<script setup lang="ts">
import Icon from '../common/Icon.vue'
const SERVICES = [
  { icon: 'shield', title: '正品保障', desc: '官方正品 · 假一赔十' },
  { icon: 'truck', title: '极速发货', desc: '全国多仓 · 次日达' },
  { icon: 'refresh', title: '无忧售后', desc: '7 天无理由退换' },
  { icon: 'card', title: '分期免息', desc: '热门商品 · 0 息购' },
]
const LINKS = [
  { title: '购物指南', items: ['购物流程', '会员介绍', '常见问题', '联系客服'] },
  { title: '配送方式', items: ['配送范围', '配送时效', '运费说明', '物流查询'] },
  { title: '支付方式', items: ['支付宝', '微信支付', '分期付款', '积分抵扣'] },
  { title: '售后服务', items: ['退换货政策', '退款说明', '价格保护', '投诉建议'] },
  { title: '关于我们', items: ['商城简介', '商家入驻', '隐私政策', '用户协议'] },
]
</script>

<template>
  <footer class="footer">
    <div class="svc">
      <div class="container svc-inner">
        <div v-for="s in SERVICES" :key="s.title" class="svc-item">
          <Icon :name="s.icon" :size="34" :stroke="1.5" />
          <div>
            <b>{{ s.title }}</b>
            <p>{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </div>
    <div class="container links">
      <dl v-for="g in LINKS" :key="g.title">
        <dt>{{ g.title }}</dt>
        <dd v-for="i in g.items" :key="i">{{ i }}</dd>
      </dl>
      <div class="hotline">
        <Icon name="headset" :size="30" />
        <b class="num">400-888-6666</b>
        <p>周一至周日 8:00-22:00</p>
      </div>
    </div>
    <div class="copy">© 2026 橙选商城 OrangeMall · 学习演示项目（Spring Boot 3 + Vue 3 高并发电商） · 非真实交易</div>
  </footer>
</template>

<style scoped>
.footer {
  background: #fff;
  margin-top: 40px;
  color: var(--c-text-2);
}
.svc {
  border-bottom: 1px solid var(--c-border);
}
.svc-inner {
  display: flex;
  justify-content: space-between;
  padding: 26px 40px;
}
.svc-item {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--c-primary);
}
.svc-item b {
  color: var(--c-text);
  font-size: 16px;
}
.svc-item p {
  font-size: 12px;
  color: var(--c-text-3);
}
.links {
  display: flex;
  justify-content: space-between;
  padding: 30px 40px;
}
dl {
  margin: 0;
}
dt {
  font-weight: 600;
  color: var(--c-text);
  margin-bottom: 10px;
}
dd {
  margin: 0 0 6px;
  font-size: 12px;
  cursor: pointer;
}
dd:hover {
  color: var(--c-primary);
}
.hotline {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--c-primary);
  padding-left: 40px;
  border-left: 1px solid var(--c-border);
}
.hotline b {
  font-size: 22px;
}
.hotline p {
  font-size: 12px;
  color: var(--c-text-3);
}
.copy {
  text-align: center;
  font-size: 12px;
  color: var(--c-text-3);
  padding: 16px 0 24px;
  border-top: 1px solid var(--c-border);
}
</style>
