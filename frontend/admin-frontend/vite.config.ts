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
      // 将 /api/admin 开头的请求代理到后端 8084 端口
      '/api/admin': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
})
