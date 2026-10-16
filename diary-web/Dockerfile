# =============================================================================
#  diary-web · 前端镜像
#
#  这个容器同时干两件事:托管 dist 静态文件,并把 /api 与 /uploads
#  反代到后端容器。前端代码里 API 一律写相对路径(见 src/api/http.ts),
#  同域部署下因此不需要任何 CORS 配置,也不用塞构建期的 API 地址变量。
# =============================================================================

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 同上:先锁依赖再拷源码,改代码不会导致重装 node_modules。
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# npm run build = vue-tsc --noEmit && vite build
# 类型错误会让镜像构建直接失败,这正是想要的:宁可构建红,不要线上白屏。
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
