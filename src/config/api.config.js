/**
 * API环境配置
 * 用于切换uniCloud云端模式和本地/自建服务器模式
 * 
 * 使用方式：
 *   import { USE_LOCAL_API, API_BASE_URL } from '@/config/api.config.js'
 */
export const USE_LOCAL_API = true          // true=本地SpringBoot服务器, false=uniCloud云端

// 开发环境（本机调试）
export const API_BASE_URL = 'http://localhost:8091/api'

// 生产环境（服务器部署时替换）
// export const API_BASE_URL = 'https://your-domain.com/api'
