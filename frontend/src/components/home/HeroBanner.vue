<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import type { ArtKind, HomeData } from '@/types/domain'
import ProductArt from '../common/ProductArt.vue'
import Icon from '../common/Icon.vue'

const props = defineProps<{ banners: HomeData['banners'] }>()

interface Prop {
  kind: ArtKind
  tint: string
  x: number // 百分比定位（相对舞台）
  y: number
  w: number
  podium?: number // 台座宽度
  z?: number
}

const THEMES: Record<string, { bg: string; glow: string; cta: string; to: string; props: Prop[] }> = {
  red: {
    bg: 'linear-gradient(115deg, #ff7a4d 0%, #f43a22 45%, #e01f2b 100%)', glow: '#ffd0a8', cta: '立即抢购', to: '/search?keyword=双11',
    props: [
      { kind: 'headphones', tint: '#e9e1d8', x: 4, y: 30, w: 30, podium: 34 },
      { kind: 'laptop', tint: '#cfd5de', x: 26, y: 12, w: 42, podium: 46, z: 2 },
      { kind: 'phone', tint: '#c9a9e8', x: 60, y: 0, w: 32, podium: 30 },
      { kind: 'watch', tint: '#44464f', x: 66, y: 44, w: 22, podium: 26, z: 3 },
    ],
  },
  violet: {
    bg: 'linear-gradient(115deg, #8f6bff 0%, #6a3df0 50%, #4b2bd1 100%)', glow: '#d9ccff', cta: '去看看', to: '/search?categoryId=1',
    props: [
      { kind: 'camera', tint: '#2b2d33', x: 2, y: 30, w: 34, podium: 36 },
      { kind: 'laptop', tint: '#7b8796', x: 30, y: 10, w: 42, podium: 46, z: 2 },
      { kind: 'earbuds', tint: '#e9edf3', x: 68, y: 34, w: 28, podium: 30 },
    ],
  },
  orange: {
    bg: 'linear-gradient(115deg, #ffb340 0%, #ff7a1a 50%, #ff4d00 100%)', glow: '#fff1c2', cta: '马上抢', to: '/seckill',
    props: [
      { kind: 'perfume', tint: '#e8b86a', x: 4, y: 28, w: 30, podium: 32 },
      { kind: 'watch', tint: '#2d2f36', x: 34, y: 8, w: 34, podium: 40, z: 2 },
      { kind: 'mouse', tint: '#25262b', x: 68, y: 34, w: 26, podium: 30 },
    ],
  },
  teal: {
    bg: 'linear-gradient(115deg, #2ed3b7 0%, #12a594 50%, #0b7d86 100%)', glow: '#c9fff3', cta: '注册领券', to: '/register',
    props: [
      { kind: 'snack', tint: '#d99a4a', x: 6, y: 26, w: 30, podium: 32 },
      { kind: 'coffee', tint: '#8b5a3c', x: 34, y: 8, w: 34, podium: 40, z: 2 },
      { kind: 'skincare', tint: '#e9d7b5', x: 68, y: 30, w: 28, podium: 30 },
    ],
  },
}

const idx = ref(0)
const cur = computed(() => props.banners[idx.value])
const theme = computed(() => THEMES[cur.value?.theme ?? 'red'] ?? THEMES.red)
let timer: ReturnType<typeof setInterval> | undefined
function play() {
  stop()
  timer = setInterval(() => (idx.value = (idx.value + 1) % Math.max(1, props.banners.length)), 5000)
}
function stop() {
  clearInterval(timer)
}
function go(i: number) {
  idx.value = (i + props.banners.length) % props.banners.length
}
play()
onUnmounted(stop)

const titleParts = computed(() => (cur.value?.title ?? '').split(' '))
</script>

<template>
  <div v-if="cur" class="hero" :style="{ background: theme.bg }" @mouseenter="stop" @mouseleave="play">
    <Transition name="slide" mode="out-in">
      <div :key="cur.id" class="slide">
        <div class="copy">
          <div class="pill">
            <b>{{ cur.tag.split(' ')[0] }}</b>
            <i v-if="cur.tag.split(' ')[1]" />
            <span v-if="cur.tag.split(' ')[1]">{{ cur.tag.split(' ').slice(1).join(' ') }}</span>
          </div>
          <h2>
            <span v-for="(t, i) in titleParts" :key="i">{{ t }}</span>
          </h2>
          <p>{{ cur.subtitle }}</p>
          <RouterLink :to="theme.to" class="cta">{{ theme.cta }} <Icon name="chevron-right" :size="18" :stroke="3" /></RouterLink>
        </div>
        <div class="stage">
          <div class="arch" :style="{ '--glow': theme.glow }" />
          <div
            v-for="(p, i) in theme.props"
            :key="i"
            class="prop"
            :style="{ left: p.x + '%', top: p.y + '%', width: p.w + '%', zIndex: p.z ?? 1 }"
          >
            <ProductArt :kind="p.kind" :tint="p.tint" />
            <div v-if="p.podium" class="podium" :style="{ width: (p.podium / p.w) * 100 + '%' }" />
          </div>
          <span class="gift g1" />
          <span class="gift g2" />
          <span class="ribbon" />
        </div>
      </div>
    </Transition>
    <button class="nav prev" aria-label="上一张" @click="go(idx - 1)"><Icon name="chevron-left" :size="20" :stroke="2.4" /></button>
    <button class="nav next" aria-label="下一张" @click="go(idx + 1)"><Icon name="chevron-right" :size="20" :stroke="2.4" /></button>
    <div class="dots">
      <span v-for="(b, i) in banners" :key="b.id" :class="{ on: i === idx }" @click="go(i)" />
    </div>
  </div>
  <div v-else class="hero skeleton" />
</template>

<style scoped>
.hero {
  position: relative;
  height: 252px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  color: #fff;
  transition: background 0.6s;
}
.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 78% 40%, rgba(255, 255, 255, 0.28), transparent 45%),
    radial-gradient(circle at 10% 110%, rgba(255, 255, 255, 0.18), transparent 40%);
  pointer-events: none;
}
.slide {
  position: absolute;
  inset: 0;
  display: flex;
}
.copy {
  position: relative;
  z-index: 3;
  padding: 24px 0 0 40px;
  width: 50%;
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 34px;
  padding: 0 18px;
  border-radius: 999px 999px 999px 4px;
  background: linear-gradient(90deg, #e3141f, #ff3a2a);
  box-shadow: 0 4px 14px rgba(150, 0, 0, 0.25);
  font-size: 17px;
}
.pill b {
  font-size: 20px;
  font-style: italic;
  font-weight: 800;
}
.pill i {
  width: 1.5px;
  height: 14px;
  background: rgba(255, 255, 255, 0.7);
}
.pill span {
  font-weight: 600;
}
h2 {
  margin-top: 16px;
  display: flex;
  gap: 22px;
  font-size: 46px;
  line-height: 1.1;
  font-weight: 900;
  font-style: italic;
  letter-spacing: 1px;
  text-shadow: 0 3px 8px rgba(120, 0, 0, 0.25);
  white-space: nowrap;
}
p {
  margin-top: 14px;
  font-size: 19px;
  letter-spacing: 1px;
  opacity: 0.95;
}
.cta {
  margin-top: 20px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 46px;
  padding: 0 26px 0 32px;
  border-radius: 999px;
  background: linear-gradient(180deg, #fff7d6, #ffd98a);
  color: #e3261a;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 6px 16px rgba(120, 0, 0, 0.25);
  transition: transform 0.15s;
}
.cta:hover {
  transform: translateY(-2px);
}
.stage {
  position: relative;
  flex: 1;
  margin: 6px 26px 0 0;
}
.arch {
  position: absolute;
  left: 22%;
  top: 6%;
  width: 52%;
  height: 86%;
  border: 3px solid var(--glow);
  border-bottom: none;
  border-radius: 40px 40px 0 0;
  box-shadow: 0 0 22px var(--glow), inset 0 0 22px var(--glow);
  opacity: 0.85;
}
.prop {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.prop :deep(.art) {
  width: 100%;
  height: auto;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 8px 10px rgba(90, 0, 0, 0.25));
}
.podium {
  position: relative;
  margin-top: -16%;
  aspect-ratio: 2.4;
  border-radius: 50% / 34%;
  background: linear-gradient(90deg, #f08a4c 0%, #ffd2ad 40%, #ffb27a 70%, #e86a2c 100%);
  box-shadow: 0 12px 20px rgba(120, 20, 0, 0.3);
}
.podium::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 68%;
  border-radius: 50%;
  background: radial-gradient(ellipse at 50% 40%, #fff6ec 0%, #ffd8b8 60%, #ffbf91 100%);
}
.gift {
  position: absolute;
  border-radius: 4px;
  background: linear-gradient(135deg, #ff4b4b, #c90f1f);
  box-shadow: 0 6px 12px rgba(100, 0, 0, 0.3);
  z-index: 4;
}
.gift::before,
.gift::after {
  content: '';
  position: absolute;
  background: #ffd36b;
}
.gift::before {
  left: 44%;
  top: 0;
  bottom: 0;
  width: 12%;
}
.gift::after {
  top: 30%;
  left: 0;
  right: 0;
  height: 12%;
}
.g1 {
  left: 30%;
  bottom: 4%;
  width: 48px;
  height: 42px;
}
.g2 {
  left: 22%;
  bottom: 3%;
  width: 30px;
  height: 26px;
  transform: rotate(-12deg);
}
.ribbon {
  position: absolute;
  right: 0;
  top: 22%;
  width: 34px;
  height: 22px;
  background: linear-gradient(135deg, #ff6b6b, #e3141f);
  transform: rotate(-28deg);
  border-radius: 3px;
  box-shadow: 0 4px 8px rgba(100, 0, 0, 0.3);
}
.nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 30px;
  height: 46px;
  border: none;
  background: rgba(0, 0, 0, 0.18);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 5;
}
.hero:hover .nav {
  opacity: 1;
}
.prev {
  left: 0;
  border-radius: 0 6px 6px 0;
}
.next {
  right: 0;
  border-radius: 6px 0 0 6px;
}
.dots {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 5;
}
.dots span {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: width 0.25s;
}
.dots span.on {
  width: 20px;
  background: #fff;
}
.slide-enter-active,
.slide-leave-active {
  transition: all 0.35s ease;
}
.slide-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.slide-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}
</style>
