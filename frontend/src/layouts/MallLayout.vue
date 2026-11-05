<script setup lang="ts">
import { useRoute } from 'vue-router'
import TopBar from '@/components/layout/TopBar.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import MainNav from '@/components/layout/MainNav.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
const route = useRoute()
</script>

<template>
  <div class="mall">
    <TopBar />
    <SiteHeader />
    <MainNav :home="route.name === 'home'" />
    <main>
      <RouterView v-slot="{ Component }">
        <component :is="Component" :key="route.path" />
      </RouterView>
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
main {
  min-height: 60vh;
  background: linear-gradient(180deg, #fff3ec 0, var(--c-bg) 420px);
}
</style>
