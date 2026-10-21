# travel-diary · 旅行日记

一个自托管的旅行日记应用:记录沿途的见闻、按地图回顾走过的路、用语义搜索翻出"当时记了什么"。

- **后端** `diary-server` —— Spring Boot 3.4.5 + MyBatis-Plus 3.5.12 + Spring AI 1.0.9,Java 17,MySQL 8
- **前端** `diary-web` —— Vue 3 + Vite 6 + TypeScript + Pinia + Leaflet

## 功能

| 模块 | 说明 |
| --- | --- |
| 日记 | Markdown 编辑、图片上传、标签、收藏、行程归属 |
| 行程 | 按一次出行组织日记,封面图与时间范围 |
| 足迹地图 | Leaflet 渲染全部去过的地方,瓦片走后端磁盘缓存代理 |
| 搜索 | 关键词搜索 + **语义搜索**(按意思匹配,不是按字面) |
| 回顾 | 年度/月的统计图表、"历史上的今天" |
| AI | 语义向量(本地 Ollama)、自动标签、年度回顾、问答(需 DeepSeek key) |

## 快速开始

### 1. 准备数据库

需要 MySQL 8.0+,并准备好一个账号密码。建表脚本可重复执行:

```bash
mysql -u root -p < diary-server/src/main/resources/db/schema.sql
```

### 2. 起后端

```bash
cd diary-server
DB_PASSWORD=你的MySQL密码 mvn spring-boot:run
```

服务起在 `http://localhost:8082`。

环境变量(都有默认值,按需覆盖):

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `travel_diary` | 数据库位置 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `change-me` | **密码占位符,必须自己设** |
| `JWT_SECRET` | 开发用弱密钥 | 生产环境务必覆盖,HS256 要求 ≥32 字节 |
| `CORS_ALLOWED_ORIGINS` | 三个本地 loopback | 逗号分隔的前端来源 |
| `UPLOAD_DIR` / `TILE_CACHE_DIR` | `./uploads` / `./tile-cache` | 上传目录与瓦片缓存 |
| `AI_ENABLED` | `false` | AI 总开关 |
| `OLLAMA_BASE` | `http://localhost:11434` | 嵌入模型服务 |
| `DEEPSEEK_API_KEY` | 无 | 对话类 AI(自动标签/年度回顾/问答)需要 |
| `SEED_ENABLED` | `true` | 首次启动写入演示数据 |

首次启动会自动写入演示数据:

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `traveler` | `diary123` | 普通用户 |
| `admin` | `diary123` | 管理员 |

### 3. 起前端

```bash
cd diary-web
npm install
npm run dev
```

打开 `http://localhost:5174`。Vite 已配好 `/api`、`/uploads`、`/tiles` 三个代理到后端。

> Windows 上用 `http://[::1]:5174` 打开也能用,后端 CORS 白名单已包含该来源。

## 语义搜索(可选)

需要本地跑 Ollama,不需要 Python 环境:

```bash
ollama pull bge-m3
```

然后 `AI_ENABLED=true` 起后端。首次启动会自动回填已有日记的向量。
DeepSeek key 是可选的 —— 没有它只是对话类功能不可用,不影响语义搜索。

## 部署

两个 Dockerfile 都提供了两阶段构建版(容器内编译)与 `Dockerfile.runtime`(宿主机先打好包)。
前端镜像用 nginx 同时托管静态文件并反代 `/api`,同域部署因此不需要配 CORS。

## 开发

```bash
cd diary-server && mvn test          # 后端测试
cd diary-web && npm run typecheck   # 前端类型检查
cd diary-web && npm run build       # 类型检查 + 构建
```

## 仓库结构

```
diary-server/     Spring Boot 后端
diary-web/        Vue 3 前端
tools/html2video/ 独立工具:HTML 动画转 MP4(与业务无关,可单独拷走)
```

## 说明

- 密码、密钥一律走环境变量,代码库中零硬编码。
- AI 功能全部可降级:DeepSeek 没 key、Ollama 没跑,日记主功能完全不受影响。
- 上传目录与瓦片缓存目录不入库,`.gitignore` 已处理。
