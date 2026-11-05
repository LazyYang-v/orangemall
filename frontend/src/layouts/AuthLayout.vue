<script setup lang="ts">
import Logo from '@/components/layout/Logo.vue'
import ProductArt from '@/components/common/ProductArt.vue'
</script>

<template>
  <div class="auth">
    <header class="container head">
      <Logo />
      <span class="welcome">欢迎登录</span>
    </header>
    <div class="stage">
      <div class="container inner">
        <div class="promo">
          <h2>双11 全球好物节</h2>
          <p>新人注册即送 10 元无门槛券 + 200 积分</p>
          <div class="arts">
            <ProductArt kind="phone" tint="#c9a9e8" :size="150" />
            <ProductArt kind="headphones" tint="#e9e1d8" :size="130" />
            <ProductArt kind="watch" tint="#44464f" :size="110" />
          </div>
        </div>
        <div class="box"><RouterView /></div>
      </div>
    </div>
    <footer class="copy">© 2026 橙选商城 OrangeMall · 学习演示项目</footer>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  gap: 18px;
  height: 96px;
}
.welcome {
  font-size: 22px;
  color: var(--c-text-2);
  padding-left: 18px;
  border-left: 1px solid var(--c-border-2);
}
.stage {
  flex: 1;
  background: var(--g-hero);
  display: flex;
  align-items: center;
  padding: 50px 0;
}
.inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.promo {
  color: #fff;
  padding-left: 40px;
}
.promo h2 {
  font-size: 48px;
  font-weight: 900;
  font-style: italic;
}
.promo p {
  font-size: 20px;
  margin-top: 10px;
}
.arts {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 30px;
  filter: drop-shadow(0 10px 16px rgba(80, 0, 0, 0.3));
}
.box {
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 28px 32px;
  box-shadow: 0 20px 50px rgba(100, 0, 0, 0.25);
}
.copy {
  text-align: center;
  color: var(--c-text-3);
  font-size: 12px;
  padding: 20px;
}
</style>
