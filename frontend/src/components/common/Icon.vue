<script setup lang="ts">
/** 线性图标集（24×24，stroke），避免引入图标字体 */
const props = withDefaults(defineProps<{ name: string; size?: number | string; stroke?: number }>(), { size: 18, stroke: 1.8 })

const ICONS: Record<string, string> = {
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  camera: '<path d="M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="3.5"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>',
  order: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 2.8h6v2.4H9z"/><path d="M8.5 10h7M8.5 14h7M8.5 18h4"/>',
  cart: '<path d="M3 4h2.2l2.3 11.2a1.5 1.5 0 0 0 1.5 1.2h8.6a1.5 1.5 0 0 0 1.4-1l2-6.4H6.3"/><circle cx="9.5" cy="20" r="1.3"/><circle cx="17.5" cy="20" r="1.3"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  bolt: '<path d="M13 2 4.5 13.5H11L10 22l8.5-11.5H12L13 2Z" fill="currentColor" stroke="none"/>',
  ticket: '<path d="M3 8a2 2 0 0 0 0 4v0a2 2 0 0 1 0 4v2h18v-2a2 2 0 0 1 0-4 2 2 0 0 0 0-4V6H3v2Z"/><path d="M9 9.5 15 15.5M15 9.5 9 15.5"/>',
  diamond: '<path d="M6 3h12l4 6-10 12L2 9l4-6Z"/><path d="M2 9h20M9 3 7.5 9 12 21l4.5-12L15 3"/>',
  coins: '<ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v4c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 10v4c0 1.7 3.1 3 7 3s7-1.3 7-3v-4M5 14v4c0 1.7 3.1 3 7 3s7-1.3 7-3v-4"/>',
  chart: '<path d="M5 20V12M10 20V6M15 20v-9M20 20V4" stroke-width="3" stroke-linecap="round"/>',
  'chevron-right': '<path d="m9 6 6 6-6 6"/>',
  'chevron-left': '<path d="m15 6-6 6 6 6"/>',
  'chevron-down': '<path d="m6 9 6 6 6-6"/>',
  phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="2.5"/><path d="M10.5 18.5h3"/>',
  shirt: '<path d="m8 3-5 3 2 4 2-1v12h10V9l2 1 2-4-5-3a4 4 0 0 1-8 0Z"/>',
  sofa: '<path d="M5 10V7a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3"/><path d="M3 12a2 2 0 0 1 4 0v2h10v-2a2 2 0 0 1 4 0v5H3v-5ZM5 17v2M19 17v2"/>',
  lipstick: '<path d="M9 21h6v-9H9v9ZM10 12V7l4-3v8"/>',
  apple: '<path d="M12 7c-2-1.5-6-1-6 4 0 4 2.5 9 4.5 9 1 0 1-.5 1.5-.5s.5.5 1.5.5c2 0 4.5-5 4.5-9 0-5-4-5.5-6-4Z"/><path d="M12 7c0-2 1-3.5 3-4"/>',
  baby: '<circle cx="12" cy="13" r="8"/><circle cx="9" cy="12" r=".8" fill="currentColor"/><circle cx="15" cy="12" r=".8" fill="currentColor"/><path d="M9.5 16c1.5 1.2 3.5 1.2 5 0M12 5c0-1.5 1.5-2 2.5-1.5"/>',
  bike: '<circle cx="6" cy="16" r="3.5"/><circle cx="18" cy="16" r="3.5"/><path d="m6 16 4-7h6l2 7M10 9 8.5 6H7M15 6h2l-1 3M10 9l2.5 7H18"/>',
  car: '<path d="M4 16v-4l2-5h12l2 5v4H4Z"/><path d="M4 12h16"/><circle cx="7.5" cy="16.5" r="1.5"/><circle cx="16.5" cy="16.5" r="1.5"/>',
  book: '<path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H11v17H5.5A1.5 1.5 0 0 0 4 21.5v-17ZM20 4.5A1.5 1.5 0 0 0 18.5 3H13v17h5.5a1.5 1.5 0 0 1 1.5 1.5v-17Z"/>',
  pill: '<rect x="7" y="3" width="10" height="18" rx="3"/><path d="M7 9h10M10 14h4M12 12v4"/>',
  shield: '<path d="M12 3 4.5 6v5.5c0 4.5 3.2 8.4 7.5 9.5 4.3-1.1 7.5-5 7.5-9.5V6L12 3Z"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
  refund: '<path d="M3.5 12a8.5 8.5 0 1 0 2.6-6.1"/><path d="M3 3v4.5h4.5"/><path d="M8.5 12h7M12 8.5l3.5 3.5L12 15.5"/>',
  truck: '<path d="M2 6h12v10H2zM14 9h4l3 3.5V16h-7"/><circle cx="6" cy="17.5" r="1.8"/><circle cx="17" cy="17.5" r="1.8"/>',
  refresh: '<path d="M4 12a8 8 0 0 1 14-5.3L20 9M20 4v5h-5M20 12a8 8 0 0 1-14 5.3L4 15M4 20v-5h5"/>',
  card: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/>',
  heart: '<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 3c0 5.4-7.5 10-7.5 10Z"/>',
  'heart-fill': '<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 3c0 5.4-7.5 10-7.5 10Z" fill="currentColor"/>',
  coupon: '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="M9.5 9.5l5 5M14.5 9.5l-5 5"/><circle cx="9" cy="9.5" r=".5"/><circle cx="15" cy="14.5" r=".5"/>',
  dollar: '<circle cx="12" cy="12" r="9"/><path d="M15 9.2c-.5-1-1.6-1.6-3-1.6-1.8 0-3 .9-3 2.2 0 3 6 1.6 6 4.4 0 1.3-1.3 2.2-3 2.2-1.5 0-2.7-.7-3.1-1.8M12 6v1.6M12 16.4V18"/>',
  service: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  headset: '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="13" width="4" height="6" rx="1.5"/><rect x="17" y="13" width="4" height="6" rx="1.5"/><path d="M19 19c0 1.5-2 2-5 2"/>',
  location: '<path d="M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12Z"/><circle cx="12" cy="9" r="2.5"/>',
  star: '<path d="m12 3 2.7 5.6 6.1.8-4.5 4.3 1.1 6.1L12 17l-5.4 2.8 1.1-6.1L3.2 9.4l6.1-.8L12 3Z"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  'check-circle': '<circle cx="12" cy="12" r="9"/><path d="m8 12.5 3 3 5-6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5.5l3.5 2"/>',
  bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16ZM10 20.5a2 2 0 0 0 4 0"/>',
  home: '<path d="m3 11 9-7 9 7M5 9.5V20h5v-6h4v6h5V9.5"/>',
  logout: '<path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M4.2 6.2l2.1 2.1M17.7 15.7l2.1 2.1M2.5 12h3M18.5 12h3M4.2 17.8l2.1-2.1M17.7 8.3l2.1-2.1"/>',
  grid: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  package: '<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9M8 5.3l8 4.5"/>',
  store: '<path d="M4 9.5V20h16V9.5M3 5l1.5-2h15L21 5v2.5a3 3 0 0 1-6 0 3 3 0 0 1-6 0 3 3 0 0 1-6 0V5Z"/><path d="M10 20v-5h4v5"/>',
  fire: '<path d="M12 21c4 0 7-2.7 7-6.5 0-4-3-6-4-9.5-1.5 2-2 3.5-2 5-1-1-2-2.5-2-4.5C8 8 5 11 5 14.5 5 18.3 8 21 12 21Z"/>',
  gift: '<rect x="3.5" y="8" width="17" height="4" rx="1"/><path d="M5 12v8.5h14V12M12 8v12.5M12 8C10 4 6.5 4.5 7 6.5 7.4 8 12 8 12 8Zm0 0c2-4 5.5-3.5 5-1.5C16.6 8 12 8 12 8Z"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16v4ZM14 6l4 4"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/>',
  database: '<ellipse cx="12" cy="5.5" rx="7.5" ry="2.8"/><path d="M4.5 5.5v13c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8v-13M4.5 12c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8"/>',
  activity: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
  upload: '<path d="M12 16V4M7 9l5-5 5 5M4 16v4h16v-4"/>',
  alert: '<path d="M12 3 2 20h20L12 3Z"/><path d="M12 10v4.5M12 17.2v.3"/>',
  qr: '<rect x="4" y="4" width="6" height="6"/><rect x="14" y="4" width="6" height="6"/><rect x="4" y="14" width="6" height="6"/><path d="M14 14h2v2h-2zM18 14h2M14 18h2M18 18h2v2"/>',
  wechat: '<path d="M9.5 4C5.4 4 2 6.8 2 10.2c0 2 1.1 3.7 2.8 4.8L4.2 17l2.4-1.2c.9.3 1.9.4 2.9.4h.4a5.4 5.4 0 0 1-.2-1.5c0-3.4 3.3-6 7.3-6h.4C16.8 5.9 13.5 4 9.5 4Z" fill="currentColor" stroke="none"/><path d="M22 14.6c0-2.8-2.8-5-6.2-5s-6.2 2.2-6.2 5 2.8 5 6.2 5c.7 0 1.4-.1 2-.3l2 1-.5-1.7c1.6-.9 2.7-2.3 2.7-4Z" fill="currentColor" stroke="none" opacity=".75"/>',
  alipay: '<rect x="3" y="3" width="18" height="18" rx="4" fill="currentColor" stroke="none"/><path d="M7 9h10M12 6.5V12M8.5 12h7c-.8 3.2-3.8 5.5-8.5 5.5M11 13.5c2 1 5 2.6 7 4" stroke="#fff"/>',
  zap: '<path d="M13 2 4.5 13.5H11L10 22l8.5-11.5H12L13 2Z"/>',
  filter: '<path d="M4 5h16l-6 8v5l-4 2v-7L4 5Z"/>',
}

const svg = () => ICONS[props.name] ?? ICONS.grid
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="icon"
    v-html="svg()"
  />
</template>

<style scoped>
.icon {
  flex: none;
  display: inline-block;
  vertical-align: middle;
}
</style>
