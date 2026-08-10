// ============================================================
// axios 实例 + 拦截器（适配 Sa-Token）
// 关键约定：
// 1. 请求头 token 名严格用 "token"（后端 sa-token.token-name=token，非 Authorization）
// 2. 多租户上下文通过 X-Tenant-Id 头传递
// 3. 响应拦截器：R<T>.code === null 表示成功（仅 data 有值），code 非 null 表示业务失败
// 4. 401 自动清凭证跳登录；403/404/500 统一 ElMessage
// ============================================================
import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from './types'
import { getToken, clearAuthStorage } from '@/utils/auth'
import { getErrorMessage } from '@/utils/errorCodeMap'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000
  // 注意: 不在此处设置默认 Content-Type: application/json。
  // 若在实例级默认锁定 application/json, 会导致 FormData 上传请求的 Content-Type 残留为
  // application/json, 浏览器不会再覆盖为 multipart/form-data; boundary=..., 后端抛出
  // MultipartException: Current request is not a multipart request。
  // JSON 请求的 Content-Type 由 axios 内部 transformRequest 自动设置 (application/json)。
})

// ---------- 请求拦截器 ----------
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 关键修复: FormData 请求必须删除 Content-Type, 交由浏览器自动设置
    // multipart/form-data; boundary=...。否则若残留 application/json, 后端会判定非 multipart 请求。
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
      delete config.headers['content-type']
    }
    // 注入 Sa-Token（header 名 token）
    const token = getToken()
    if (token) {
      config.headers['token'] = token
    }
    // 注入多租户上下文（动态引入 store 避免循环依赖）
    try {
      const tenantId = localStorage.getItem('current_tenant_id')
      if (tenantId) {
        config.headers['X-Tenant-Id'] = tenantId
      }
    } catch {
      // localStorage 不可用时静默忽略
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---------- 响应拦截器 ----------
// 关键约定：成功时返回 R<T>.data（剥壳）；失败 reject
// 返回 any 类型断言绕过 axios 对 fulfilled handler 必须返回 AxiosResponse 的强类型约束
// 下游业务层通过 request.get<T>() 的泛型恢复具体类型，类型安全不受影响
service.interceptors.response.use(
  (response): any => {
    const r = response.data as R<unknown>
    // ★ 成功判断: code === 200 (与 Java R.ok() / ErrCodeEnum.SUCCESS 对齐)
    // 过渡期兼容: code === null/undefined (旧接口未返回 code)
    if (r.code === 200 || r.code === null || r.code === undefined) {
      return r.data
    }
    // 业务失败: 按错误码映射友好提示, msg 仅作 fallback
    ElMessage.error(getErrorMessage(r.code, r.msg))
    return Promise.reject(r)
  },
  (error) => {
    const status = error?.response?.status
    const respMsg = error?.response?.data?.msg
    const respCode = error?.response?.data?.code
    // 优先按业务码映射友好提示
    if (respCode != null && respCode !== 200) {
      ElMessage.error(getErrorMessage(respCode, respMsg))
      return Promise.reject(error)
    }
    if (status === 401) {
      // 未授权：清凭证并跳登录页（用 location 避免 router 循环依赖）
      clearAuthStorage()
      ElMessage.warning('登录已过期，请重新登录')
      setTimeout(() => {
        const redirect = window.location.pathname + window.location.search
        window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
      }, 300)
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else if (status === 404) {
      ElMessage.error('请求的资源不存在')
    } else if (status && status >= 500) {
      ElMessage.error('服务暂时不可用，请稍后重试')
    } else if (error?.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error?.request) {
      ElMessage.error('网络异常，请检查网络连接')
    } else {
      ElMessage.error('操作失败，请稍后重试')
    }
    return Promise.reject(error)
  }
)

// ---------- 类型化请求封装 ----------
/**
 * 业务层调用后直接拿到 R<T>.data（已被响应拦截器剥壳）
 * 例：request.get<ProductResp>('/products/1') → Promise<ProductResp>
 */
const request = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as unknown as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config) as unknown as Promise<T>
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as unknown as Promise<T>
  }
}

export default request
export { service as axiosInstance }
