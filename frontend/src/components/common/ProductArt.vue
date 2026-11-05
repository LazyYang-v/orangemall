<script setup lang="ts">
/**
 * 商品矢量插画：替代真实商品图，保证离线可用、无版权风险。
 * 后端返回 imageUrl 后，传入 src 即可显示真实图片。
 */
import { computed } from 'vue'
import type { ArtKind } from '@/types/domain'

const props = withDefaults(
  defineProps<{ kind: ArtKind; tint?: string; src?: string; size?: number | string; bg?: boolean }>(),
  { tint: '#888', size: '100%', bg: false },
)

let seed = 0
const uid = `pa${++seed}${Math.random().toString(36).slice(2, 7)}`

function shade(hex: string, amt: number): string {
  const h = hex.replace('#', '')
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16)
  const clamp = (v: number) => Math.max(0, Math.min(255, v))
  const r = clamp((n >> 16) + amt)
  const g = clamp(((n >> 8) & 255) + amt)
  const b = clamp((n & 255) + amt)
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
}

const svg = computed(() => {
  const t = props.tint
  const lt = shade(t, 40)
  const dk = shade(t, -45)
  const g = `${uid}g`
  const gs = `${uid}s`
  const defs = `<defs>
    <linearGradient id="${g}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${lt}"/><stop offset="1" stop-color="${dk}"/></linearGradient>
    <radialGradient id="${gs}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#000" stop-opacity=".18"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>
  </defs>`
  const shadow = `<ellipse cx="100" cy="182" rx="62" ry="8" fill="url(#${gs})"/>`
  const F = `url(#${g})`
  const art: Record<ArtKind, string> = {
    phone: `${shadow}<rect x="58" y="18" width="84" height="160" rx="18" fill="${F}"/><rect x="62" y="22" width="76" height="152" rx="15" fill="${shade(t, 20)}" opacity=".35"/>
      <rect x="68" y="28" width="40" height="44" rx="12" fill="${dk}" opacity=".9"/><circle cx="80" cy="40" r="8" fill="#1b1b22"/><circle cx="80" cy="40" r="4" fill="#3a4a6a"/>
      <circle cx="96" cy="40" r="8" fill="#1b1b22"/><circle cx="96" cy="40" r="4" fill="#3a4a6a"/><circle cx="88" cy="60" r="8" fill="#1b1b22"/><circle cx="88" cy="60" r="4" fill="#3a4a6a"/>
      <circle cx="102" cy="62" r="3" fill="#ffe9b0"/><path d="M90 118c0-6 8-10 10-4 2-6 10-2 10 4s-10 12-10 12-10-6-10-12Z" fill="#fff" opacity=".5"/>`,
    earbuds: `${shadow}<rect x="46" y="86" width="108" height="78" rx="34" fill="#f7f8fa" stroke="#dfe3ea" stroke-width="2"/><path d="M46 112h108" stroke="#dfe3ea" stroke-width="2"/>
      <rect x="92" y="122" width="16" height="4" rx="2" fill="#c9ced6"/>
      <g><ellipse cx="78" cy="62" rx="20" ry="22" fill="#fff" stroke="#dfe3ea" stroke-width="2"/><rect x="72" y="70" width="12" height="40" rx="6" fill="#fff" stroke="#dfe3ea" stroke-width="2"/></g>
      <g><ellipse cx="124" cy="58" rx="20" ry="22" fill="#fff" stroke="#dfe3ea" stroke-width="2"/><rect x="118" y="66" width="12" height="40" rx="6" fill="#fff" stroke="#dfe3ea" stroke-width="2"/></g>`,
    watch: `${shadow}<rect x="74" y="10" width="52" height="56" rx="10" fill="${dk}"/><rect x="74" y="134" width="52" height="50" rx="10" fill="${dk}"/>
      <rect x="56" y="52" width="88" height="96" rx="24" fill="${F}"/><rect x="62" y="58" width="76" height="84" rx="19" fill="#0d0d10"/>
      <circle cx="100" cy="100" r="26" fill="none" stroke="#ff3b5c" stroke-width="6" stroke-dasharray="120 60" transform="rotate(-90 100 100)"/>
      <circle cx="100" cy="100" r="17" fill="none" stroke="#7ce35a" stroke-width="6" stroke-dasharray="80 40" transform="rotate(-90 100 100)"/>
      <circle cx="100" cy="100" r="8" fill="none" stroke="#3bd3ff" stroke-width="5" stroke-dasharray="35 20" transform="rotate(-90 100 100)"/><rect x="144" y="84" width="6" height="20" rx="3" fill="${dk}"/>`,
    perfume: `${shadow}<rect x="84" y="22" width="32" height="30" rx="4" fill="#c99a4d"/><rect x="84" y="22" width="32" height="8" rx="3" fill="#e7c27c"/>
      <rect x="92" y="50" width="16" height="14" fill="#d8b46a"/><rect x="52" y="62" width="96" height="112" rx="16" fill="${t}" opacity=".35" stroke="${t}" stroke-width="3"/>
      <rect x="58" y="96" width="84" height="72" rx="10" fill="${t}" opacity=".55"/><rect x="74" y="100" width="52" height="30" rx="3" fill="#fff" opacity=".85"/>
      <path d="M84 112h32M88 120h24" stroke="${dk}" stroke-width="2"/><rect x="62" y="68" width="10" height="90" rx="5" fill="#fff" opacity=".45"/>`,
    mouse: `${shadow}<path d="M100 24c-34 0-50 30-50 72s16 80 50 80 50-38 50-80-16-72-50-72Z" fill="${F}"/>
      <path d="M100 24v52M50 80h100" stroke="#000" stroke-opacity=".35" stroke-width="2"/><rect x="95" y="44" width="10" height="22" rx="5" fill="#666"/>
      <path d="M66 46c6-10 16-16 26-18" stroke="#fff" stroke-opacity=".25" stroke-width="5" stroke-linecap="round"/>`,
    camera: `${shadow}<rect x="22" y="58" width="156" height="102" rx="12" fill="${F}"/><rect x="40" y="46" width="44" height="18" rx="4" fill="${dk}"/>
      <rect x="138" y="48" width="22" height="12" rx="3" fill="#555"/><rect x="22" y="80" width="156" height="10" fill="#000" opacity=".25"/>
      <circle cx="112" cy="112" r="44" fill="#111"/><circle cx="112" cy="112" r="36" fill="#26282e" stroke="#555" stroke-width="2"/>
      <circle cx="112" cy="112" r="24" fill="#0b1630"/><circle cx="112" cy="112" r="14" fill="#1d3f7a"/><circle cx="104" cy="104" r="6" fill="#fff" opacity=".45"/>
      <rect x="32" y="70" width="40" height="6" rx="3" fill="#888"/><text x="36" y="148" font-size="10" fill="#bbb" font-family="Arial" font-weight="700">α7C</text>`,
    headphones: `${shadow}<path d="M44 112V96a56 56 0 0 1 112 0v16" fill="none" stroke="${dk}" stroke-width="12" stroke-linecap="round"/>
      <path d="M44 112V96a56 56 0 0 1 112 0v16" fill="none" stroke="${lt}" stroke-width="6" stroke-linecap="round"/>
      <rect x="28" y="102" width="40" height="66" rx="18" fill="${F}"/><rect x="132" y="102" width="40" height="66" rx="18" fill="${F}"/>
      <rect x="56" y="110" width="12" height="50" rx="6" fill="${dk}"/><rect x="132" y="110" width="12" height="50" rx="6" fill="${dk}"/>`,
    sneaker: `${shadow}<path d="M20 140c0-14 6-30 18-42 10 6 26 8 40 2l24 26c18 4 46 6 64 14 10 4 14 12 14 20H26c-4 0-6-6-6-20Z" fill="#fafafa" stroke="#ddd" stroke-width="2"/>
      <path d="M20 152h160v10c0 4-2 6-6 6H26c-4 0-6-2-6-6v-10Z" fill="${t === '#f2f2f2' ? '#e8e8e8' : t}"/>
      <path d="M62 104l18 22M74 100l18 22M86 98l16 20" stroke="#bbb" stroke-width="3" stroke-linecap="round"/>
      <path d="M104 132c22 6 44 8 64 14" stroke="#ff6a00" stroke-width="5" stroke-linecap="round"/>`,
    coffee: `${shadow}<rect x="46" y="20" width="108" height="140" rx="12" fill="${F}"/><rect x="46" y="20" width="108" height="26" rx="12" fill="${dk}"/>
      <circle cx="128" cy="62" r="11" fill="#ddd"/><circle cx="128" cy="62" r="6" fill="#999"/><rect x="62" y="56" width="40" height="14" rx="3" fill="#2b2b2b"/>
      <rect x="66" y="84" width="60" height="12" rx="4" fill="#888"/><rect x="88" y="96" width="16" height="10" fill="#666"/>
      <path d="M80 128h32v18c0 6-4 10-10 10h-12c-6 0-10-4-10-10v-18Z" fill="#fff"/><path d="M112 134h6a6 6 0 0 1 0 12h-6" fill="none" stroke="#fff" stroke-width="3"/>
      <rect x="46" y="158" width="108" height="12" rx="4" fill="${dk}"/>`,
    sofa: `${shadow}<rect x="28" y="62" width="144" height="64" rx="18" fill="${shade(t, -10)}"/><rect x="16" y="98" width="168" height="54" rx="20" fill="${F}"/>
      <rect x="40" y="104" width="58" height="30" rx="12" fill="${lt}"/><rect x="102" y="104" width="58" height="30" rx="12" fill="${lt}"/>
      <rect x="10" y="94" width="30" height="62" rx="14" fill="${t}"/><rect x="160" y="94" width="30" height="62" rx="14" fill="${t}"/>
      <rect x="30" y="152" width="8" height="18" rx="3" fill="#6b5a46"/><rect x="162" y="152" width="8" height="18" rx="3" fill="#6b5a46"/>`,
    jacket: `${shadow}<path d="M70 26l30 14 30-14 38 22 14 62-24 6-6-34v92H48V82l-6 34-24-6 14-62 38-22Z" fill="${F}"/>
      <path d="M100 40v138" stroke="#333" stroke-width="3"/><path d="M70 26c4 18 16 26 30 26s26-8 30-26" fill="none" stroke="${dk}" stroke-width="5"/>
      <rect x="58" y="112" width="26" height="22" rx="3" fill="${dk}" opacity=".5"/><rect x="116" y="112" width="26" height="22" rx="3" fill="${dk}" opacity=".5"/>`,
    skincare: `${shadow}<rect x="54" y="62" width="44" height="112" rx="10" fill="${F}"/><rect x="62" y="34" width="28" height="30" rx="4" fill="#d9b24c"/>
      <rect x="60" y="100" width="32" height="40" rx="3" fill="#fff" opacity=".8"/><path d="M66 112h20M68 120h16" stroke="${dk}" stroke-width="2"/>
      <rect x="108" y="88" width="44" height="86" rx="10" fill="#fdfaf3" stroke="${t}" stroke-width="3"/><rect x="118" y="68" width="24" height="22" rx="4" fill="#d9b24c"/>
      <rect x="114" y="116" width="32" height="30" rx="3" fill="${t}" opacity=".6"/>`,
    luggage: `${shadow}<path d="M84 18h32v26h-8V26H92v18h-8V18Z" fill="#666"/><rect x="52" y="42" width="96" height="128" rx="14" fill="${F}"/>
      <path d="M72 50v112M100 50v112M128 50v112" stroke="#000" stroke-opacity=".15" stroke-width="4"/>
      <rect x="52" y="96" width="96" height="10" fill="#000" opacity=".15"/><circle cx="66" cy="176" r="6" fill="#333"/><circle cx="134" cy="176" r="6" fill="#333"/>`,
    laptop: `${shadow}<rect x="40" y="30" width="120" height="86" rx="8" fill="${dk}"/><rect x="46" y="36" width="108" height="74" rx="4" fill="#1e2a44"/>
      <path d="M46 110l38-44 26 26 16-14 28 32Z" fill="#3a5a8a"/><circle cx="130" cy="56" r="8" fill="#ffd27a"/>
      <path d="M20 120h160l-12 22H32Z" fill="${F}"/><rect x="88" y="124" width="24" height="4" rx="2" fill="${dk}"/>`,
    snack: `${shadow}<path d="M54 24h92l-6 14 8 128c0 6-4 10-10 10H62c-6 0-10-4-10-10l8-128-6-14Z" fill="${F}"/>
      <path d="M54 24h92" stroke="${dk}" stroke-width="6" stroke-dasharray="6 4"/><circle cx="100" cy="104" r="34" fill="#fff" opacity=".9"/>
      <ellipse cx="88" cy="98" rx="10" ry="7" fill="#c47a3a"/><ellipse cx="110" cy="96" rx="9" ry="6" fill="#e0a85c"/><ellipse cx="100" cy="114" rx="10" ry="6" fill="#8a4a2a"/>
      <text x="100" y="158" font-size="14" text-anchor="middle" fill="#fff" font-weight="700" font-family="sans-serif">每日坚果</text>`,
    toy: `${shadow}<rect x="40" y="120" width="56" height="50" rx="4" fill="#ff5a3c"/><rect x="104" y="120" width="56" height="50" rx="4" fill="#2f8cf6"/>
      <rect x="64" y="70" width="72" height="50" rx="4" fill="${t}"/><rect x="82" y="30" width="36" height="40" rx="4" fill="#3cbf8a"/>
      ${[52, 72, 116, 136].map((x) => `<rect x="${x}" y="112" width="12" height="8" rx="2" fill="#000" opacity=".15"/>`).join('')}
      <rect x="76" y="62" width="12" height="8" rx="2" fill="#000" opacity=".15"/><rect x="112" y="62" width="12" height="8" rx="2" fill="#000" opacity=".15"/>`,
    book: `${shadow}<path d="M48 22h96c6 0 10 4 10 10v134c0 6-4 10-10 10H48Z" fill="${F}"/><rect x="48" y="22" width="14" height="154" fill="${dk}"/>
      <rect x="74" y="50" width="66" height="8" rx="4" fill="#fff"/><rect x="74" y="66" width="48" height="6" rx="3" fill="#fff" opacity=".7"/>
      <path d="M82 110l16-22 16 22 14-14 14 26H82Z" fill="#fff" opacity=".85"/><rect x="74" y="148" width="40" height="5" rx="2" fill="#fff" opacity=".6"/>`,
    bike: `${shadow}<circle cx="52" cy="128" r="34" fill="none" stroke="#333" stroke-width="8"/><circle cx="148" cy="128" r="34" fill="none" stroke="#333" stroke-width="8"/>
      <path d="M52 128l34-56h48l14 56M86 72l20 56h42M106 128l28-56" fill="none" stroke="${t}" stroke-width="8" stroke-linejoin="round"/>
      <path d="M74 60h26M128 54l6 18" stroke="#333" stroke-width="7" stroke-linecap="round"/>`,
    car: `${shadow}<path d="M24 126l14-40c4-10 12-16 22-16h80c10 0 18 6 22 16l14 40v26H24Z" fill="${F}"/>
      <path d="M50 86l8-12h84l8 12Z" fill="#9fc3e8"/><rect x="24" y="118" width="152" height="10" fill="#000" opacity=".12"/>
      <circle cx="58" cy="152" r="16" fill="#222"/><circle cx="58" cy="152" r="7" fill="#999"/><circle cx="142" cy="152" r="16" fill="#222"/><circle cx="142" cy="152" r="7" fill="#999"/>
      <rect x="30" y="104" width="18" height="8" rx="3" fill="#ffd27a"/><rect x="152" y="104" width="18" height="8" rx="3" fill="#ffd27a"/>`,
    pill: `${shadow}<rect x="60" y="30" width="80" height="26" rx="6" fill="#fff" stroke="#ddd" stroke-width="2"/><rect x="54" y="54" width="92" height="120" rx="16" fill="${F}"/>
      <rect x="64" y="86" width="72" height="56" rx="6" fill="#fff"/><path d="M100 98v32M84 114h32" stroke="${t}" stroke-width="8" stroke-linecap="round"/>`,
  }
  return `${defs}${art[props.kind] ?? art.phone}`
})
</script>

<template>
  <!-- 相对尺寸（如 100%）只设宽度，高度由 aspect-ratio 推导，避免在 grid / flex 中被撑高 -->
  <div class="art" :class="{ 'art-bg': bg }" :style="typeof size === 'number' ? { width: size + 'px', height: size + 'px' } : { width: size }">
    <img v-if="src" :src="src" alt="" />
    <svg v-else viewBox="0 0 200 200" v-html="svg" />
  </div>
</template>

<style scoped>
.art {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
}
.art svg,
.art img {
  width: 82%;
  height: 82%;
  object-fit: contain;
}
.art-bg {
  background: linear-gradient(160deg, #fafafa, #f1f1f3);
  border-radius: 10px;
}
</style>
