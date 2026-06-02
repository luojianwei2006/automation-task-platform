import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 将所有 /api 请求代理到 Gateway (:8085)，由 Gateway 统一路由
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
      // 文件上传等直连 admin-api，绕过 Gateway（Gateway 基于 WebFlux，无法正确转发 multipart 请求体）
      '/admin': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
})
