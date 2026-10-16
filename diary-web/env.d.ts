/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基地址。留空则用相对路径 /api,由 Vite 代理转发。 */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
