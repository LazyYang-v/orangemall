import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      // 关闭 Mock 后，所有 /api 与 /ws 请求转发到 Spring Cloud Gateway
      proxy: {
        '/api': { target: env.VITE_GATEWAY_URL || 'http://localhost:8080', changeOrigin: true },
        '/ws': { target: env.VITE_GATEWAY_URL || 'http://localhost:8080', ws: true, changeOrigin: true },
      },
    },
  }
})
