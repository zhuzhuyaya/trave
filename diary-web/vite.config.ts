import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    // 开发时把 /api 与 /uploads 转给 Spring Boot,前端代码里一律写相对路径,
    // 这样生产环境换成同域部署时不用改任何代码。
    // /uploads 单独代理是因为图片不走 axios,由 <img src> 直接请求,
    // 不代理的话开发环境下图片会 404(请求打到了 Vite 而不是后端)。
    // /tiles 是地图瓦片代理(后端磁盘缓存 + 回源 OSM 镜像),同样由 <img> 请求。
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/tiles': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
