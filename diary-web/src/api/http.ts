import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { getToken } from './token'
import type { ApiEnvelope } from './types'

/** 业务错误。带上 code 让调用方能按类型分别处理,而不只是看字符串。 */
export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 后端 ResultCode.UNAUTHORIZED */
const CODE_UNAUTHORIZED = 40100

/**
 * 401 的处理回调。
 *
 * 用注册回调而不是在这里直接 import router / store:http.ts 是最底层模块,
 * 让它反向依赖路由会造成循环依赖,模块初始化顺序会变得不可预测。
 * 接线放在 main.ts 里显式完成。
 *
 * 这里刻意<b>不</b>再做一层"全局错误提示":所有调用方在自己的 catch 里
 * 都会调 ui.error(toMessage(e)),底层再弹一次就是同一条错误报两遍。
 * 错误提示的归属地是调用方 —— 它才知道这个错误该用 toast 还是渲染成错误页。
 */
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  onUnauthorized = handler
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 20000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 统一请求入口。
 *
 * 后端 HTTP 状态码恒为 200,业务结果在 body.code 里,所以这里只有一个判断分支:
 * code === 0 取 data,否则抛。不会出现"有时走 then 有时走 catch"的两套逻辑。
 */
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  let response
  try {
    response = await http.request<ApiEnvelope<T>>(config)
  } catch (error) {
    // 走到这里说明是网络层问题(断网、超时、跨域、5xx),不是业务错误
    throw new ApiError(-1, describeNetworkError(error as AxiosError))
  }

  const body = response.data

  // 理论上后端总会返回信封结构;万一不是(比如被代理拦截),原样透出,别静默吞掉
  if (!body || typeof body.code !== 'number') {
    return body as unknown as T
  }

  if (body.code === 0) {
    return body.data
  }

  if (body.code === CODE_UNAUTHORIZED) {
    onUnauthorized?.()
  }

  throw new ApiError(body.code, body.message || '请求失败')
}

function describeNetworkError(error: AxiosError): string {
  if (error.code === 'ECONNABORTED') {
    return '请求超时了,网络可能不太稳定'
  }
  if (!error.response) {
    return '连不上服务器,确认一下后端是否已启动'
  }
  const status = error.response.status
  if (status === 404) {
    return '接口不存在,检查一下代理配置'
  }
  if (status === 413) {
    return '图片太大了,换一张小一点的试试'
  }
  if (status >= 500) {
    return '服务器出错了,请稍后重试'
  }
  return `请求失败(HTTP ${status})`
}

/**
 * GET。params 取 object 而不是 Record<string, unknown>:
 * 后者会要求调用方传的类型带字符串索引签名,而 interface 声明出来的查询条件
 * 恰好没有,传进去会报错。查询参数的字段类型由各自的 query 类型把关,
 * 这里不需要再收窄一次。
 */
export function get<T>(url: string, params?: object): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PUT', url, data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ method: 'DELETE', url })
}

/**
 * 文件上传。
 *
 * 不设 Content-Type —— 交给浏览器自己带 boundary。手动写
 * `multipart/form-data` 会漏掉那串 boundary,后端直接解析失败。
 *
 * 超时给得更宽:手机在弱网下传一张几 MB 的图,20 秒并不够。
 */
export function postForm<T>(url: string, form: FormData, timeout = 60000): Promise<T> {
  return request<T>({ method: 'POST', url, data: form, timeout })
}

/** 供 toast 提示用:把任意异常转成一句能给用户看的话。 */
export function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return '出了点问题,请稍后重试'
}
