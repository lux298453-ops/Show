import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 后端地址：默认 8090；dev profile 副本启动在 8091，可用
    //   $env:VITE_API_TARGET="http://localhost:8091"; npm run dev
    // 覆盖，无需改代码
    proxy: {
      '/api': process.env.VITE_API_TARGET || 'http://localhost:8091',
      '/files': process.env.VITE_API_TARGET || 'http://localhost:8091',
    },
  },
})