<script setup lang="ts">
import Mascot from '@/components/common/Mascot.vue'
</script>

<template>
  <div class="container page nf">
    <div class="card box">
      <Mascot :size="120" />
      <h2 class="num">404</h2>
      <p class="muted">页面走丢了，可能已下架或链接有误</p>
      <div class="ops">
        <RouterLink to="/" class="btn btn-primary">回到首页</RouterLink>
        <RouterLink to="/search" class="btn">逛逛全部商品</RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 0 70px;
}
h2 {
  font-size: 56px;
  color: var(--c-primary);
  line-height: 1;
}
.ops {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}
</style>
