<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 56 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 64 64" aria-hidden="true">
    <defs>
      <radialGradient id="mascot-g" cx=".38" cy=".35" r=".7">
        <stop offset="0" stop-color="#ffc06b" />
        <stop offset="1" stop-color="#ff7a12" />
      </radialGradient>
    </defs>
    <circle cx="32" cy="32" r="31" fill="#fff1e4" />
    <path d="M32 14c2-6 8-8 12-6-2 5-7 7-12 6Z" fill="#41b95c" />
    <path d="M32 15v-4" stroke="#7a4b1d" stroke-width="2" stroke-linecap="round" />
    <circle cx="32" cy="36" r="20" fill="url(#mascot-g)" />
    <ellipse cx="25" cy="34" rx="2.6" ry="3.4" fill="#3a2410" />
    <ellipse cx="39" cy="34" rx="2.6" ry="3.4" fill="#3a2410" />
    <circle cx="26" cy="33" r=".9" fill="#fff" />
    <circle cx="40" cy="33" r=".9" fill="#fff" />
    <ellipse cx="20.5" cy="40" rx="3" ry="1.8" fill="#ff5a5a" opacity=".45" />
    <ellipse cx="43.5" cy="40" rx="3" ry="1.8" fill="#ff5a5a" opacity=".45" />
    <path d="M28 41c2.4 2.4 5.6 2.4 8 0" fill="none" stroke="#3a2410" stroke-width="2" stroke-linecap="round" />
  </svg>
</template>
