<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import Icon from '../common/Icon.vue'

withDefaults(defineProps<{ floating?: boolean }>(), { floating: false })
const app = useAppStore()
const active = ref<number | null>(null)
onMounted(() => app.loadCategories())
</script>

<template>
  <div class="cat-menu" :class="{ floating }" @mouseleave="active = null">
    <ul class="roots">
      <li v-for="c in app.categories" :key="c.id" :class="{ on: active === c.id }" @mouseenter="active = c.id">
        <RouterLink :to="{ path: '/search', query: { categoryId: c.id } }" class="root">
          <Icon :name="c.icon ?? 'grid'" :size="18" />
          <b>{{ c.name }}</b>
          <span class="subs ellipsis">{{ (c.children ?? []).slice(0, 3).map((x) => x.name).join(' / ') }}</span>
          <Icon name="chevron-right" :size="12" class="arrow" />
        </RouterLink>
      </li>
      <li v-if="!app.categories.length" class="loading">
        <div v-for="i in 10" :key="i" class="skeleton" style="height: 18px; margin: 10px 16px" />
      </li>
    </ul>
    <Transition name="fade">
      <div v-if="active !== null" class="flyout">
        <template v-for="c in app.categories" :key="c.id">
          <template v-if="c.id === active">
            <div class="fly-head">
              <RouterLink :to="{ path: '/search', query: { categoryId: c.id } }">{{ c.name }} <Icon name="chevron-right" :size="12" /></RouterLink>
            </div>
            <dl v-for="s in c.children" :key="s.id">
              <dt><RouterLink :to="{ path: '/search', query: { categoryId: s.id } }">{{ s.name }}<Icon name="chevron-right" :size="11" /></RouterLink></dt>
              <dd>
                <RouterLink v-for="t in s.children" :key="t.id" :to="{ path: '/search', query: { categoryId: t.id } }">{{ t.name }}</RouterLink>
                <RouterLink v-if="!s.children?.length" :to="{ path: '/search', query: { categoryId: s.id } }" class="muted">全部{{ s.name }}</RouterLink>
              </dd>
            </dl>
          </template>
        </template>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.cat-menu {
  position: relative;
  width: 214px;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  z-index: 20;
}
.floating {
  position: absolute;
  top: 100%;
  left: 0;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  box-shadow: var(--shadow);
}
.roots {
  padding: 8px 0;
}
.root {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 31px;
  padding: 0 8px 0 14px;
  color: var(--c-text);
}
.root b {
  font-size: 14px;
  font-weight: 600;
  flex: none;
}
.subs {
  flex: 1;
  font-size: 11.5px;
  color: var(--c-text-3);
}
.arrow {
  color: var(--c-text-4);
}
li.on .root {
  background: var(--c-primary-soft);
  color: var(--c-primary);
}
li.on .subs {
  color: var(--c-primary-2);
}
.flyout {
  position: absolute;
  left: 100%;
  top: 0;
  min-height: 100%;
  width: 640px;
  background: #fff;
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  box-shadow: 8px 6px 24px rgba(0, 0, 0, 0.08);
  border-left: 2px solid var(--c-primary);
  padding: 18px 24px;
}
.fly-head a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 700;
  font-size: 15px;
  color: var(--c-primary);
  margin-bottom: 12px;
}
dl {
  display: flex;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--c-border);
  margin: 0;
}
dt {
  width: 86px;
  flex: none;
  font-weight: 600;
}
dt a {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
dd {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  color: var(--c-text-2);
}
dd a:hover,
dt a:hover {
  color: var(--c-primary);
}
</style>
