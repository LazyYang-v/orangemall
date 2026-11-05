<script setup lang="ts">
withDefaults(defineProps<{ small?: boolean }>(), { small: false })
</script>

<template>
  <RouterLink to="/" class="logo" :class="{ small }">
    <svg viewBox="0 0 64 64" class="mark" aria-hidden="true">
      <defs>
        <linearGradient id="logo-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#FF9A2E" />
          <stop offset="1" stop-color="#FF4D00" />
        </linearGradient>
      </defs>
      <path d="M21 22a11 11 0 0 1 22 0" fill="none" stroke="#FF6A00" stroke-width="5" stroke-linecap="round" />
      <rect x="7" y="19" width="50" height="40" rx="12" fill="url(#logo-g)" />
      <path d="M22 34a10 10 0 0 0 20 0" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" />
    </svg>
    <span class="txt">
      <b>橙选商城</b>
      <em>OrangeMall</em>
    </span>
  </RouterLink>
</template>

<style scoped>
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mark {
  width: 58px;
  height: 58px;
}
.txt {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}
b {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 1px;
  background: linear-gradient(90deg, #ff7a1a, #ff4d00);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
em {
  font-style: normal;
  font-size: 17px;
  color: var(--c-primary);
  font-weight: 500;
  letter-spacing: 0.5px;
}
.small .mark {
  width: 40px;
  height: 40px;
}
.small b {
  font-size: 22px;
}
.small em {
  font-size: 12px;
}
</style>
