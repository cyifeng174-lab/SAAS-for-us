/**
 * 统一API请求封装
 * 
 * 功能说明：
 * 1. 支持本地SpringBoot服务器模式（HTTP请求）
 * 2. 支持uniCloud云端模式（callFunction）
 * 3. 通过 USE_LOCAL_API 配置一键切换
 * 4. 自动携带JWT Token认证
 * 5. 统一错误处理
 * 
 * 注意：小程序真机调试时本机地址需在"详情-本地设置"中勾选"不校验合法域名"
 */

import { USE_LOCAL_API, API_BASE_URL } from '@/config/api.config.js'

// 云函数名映射表（用于uniCloud模式回退）
const CLOUD_FUNC_MAP = {
    '/auth/login': 'wechat_login',
    '/auth/check': 'wechat_login',
    '/customers': 'get_customer_list',
    '/purchases': 'purchase_create',
    '/sales': 'sales_order_create',
    '/inventory': 'get_inventory_list',
    '/processing': 'process_and_stock_in',
    '/finance/receive': 'finance_receive',
    '/finance/ledgers': 'finance_receive',
    '/dashboard': 'data_dashboard',
    '/specs': 'spec_manage',
    '/products': 'spec_manage',
    '/suppliers': 'supplier_manage',
    '/export': 'data_export'
}

/**
 * 获取存储的Token
 */
const getToken = () => {
    try {
        return uni.getStorageSync('token') || ''
    } catch (e) {
        return ''
    }
}

/**
 * 获取租户ID
 */
const getTenantId = () => {
    try {
        return uni.getStorageSync('tenant_id') || 'default'
    } catch (e) {
        return 'default'
    }
}

/**
 * 统一请求方法
 * @param {String} url - API路径（如 /customers）
 * @param {String} method - 请求方法：GET/POST/PUT/DELETE
 * @param {Object} data - 请求参数（GET时自动拼接到URL）
 * @returns {Promise} 返回 { code, message, data } 格式
 */
async function request(url, method = 'GET', data = null) {
    if (USE_LOCAL_API) {
        return httpRequest(url, method, data)
    } else {
        return cloudRequest(url, method, data)
    }
}

/**
 * 本地SpringBoot服务器 HTTP请求
 */
function httpRequest(url, method, data) {
    return new Promise((resolve, reject) => {
        // 构建完整请求URL
        let fullUrl = API_BASE_URL + url

        // GET请求将参数拼接到URL
        if (method === 'GET' && data) {
            const params = Object.keys(data)
                .filter(k => data[k] !== undefined && data[k] !== null && data[k] !== '')
                .map(k => `${k}=${encodeURIComponent(data[k])}`)
                .join('&')
            if (params) {
                fullUrl += '?' + params
            }
        }

        const header = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getToken(),
            'X-Tenant-Id': getTenantId()
        }

        uni.request({
            url: fullUrl,
            method: method,
            data: method === 'GET' ? undefined : data,
            header: header,
            success: (res) => {
                if (res.statusCode === 200) {
                    // 后端返回标准 ApiResponse 格式 {code, message, data}
                    const body = res.data
                    if (body.code === 0) {
                        resolve(body)
                    } else {
                        // 业务错误：显示提示但仍返回结果
                        uni.showToast({
                            title: body.message || '操作失败',
                            icon: 'none',
                            duration: 2000
                        })
                        resolve(body)
                    }
                } else if (res.statusCode === 401) {
                    // Token过期或无效，跳转登录页
                    uni.removeStorageSync('token')
                    uni.reLaunch({ url: '/pages/login/index' })
                    reject(res.data)
                } else {
                    uni.showToast({
                        title: res.data?.message || '网络请求失败',
                        icon: 'none',
                        duration: 2000
                    })
                    reject(res.data)
                }
            },
            fail: (err) => {
                console.error('HTTP请求失败：', url, err)
                uni.showToast({
                    title: '网络连接失败，请检查网络',
                    icon: 'none',
                    duration: 3000
                })
                reject(err)
            }
        })
    })
}

/**
 * uniCloud云端请求（保留原有逻辑兼容）
 */
function cloudRequest(url, method, data) {
    return new Promise((resolve, reject) => {
        // 根据路径映射云函数名
        let funcName = ''
        for (const [key, val] of Object.entries(CLOUD_FUNC_MAP)) {
            if (url.startsWith(key)) {
                funcName = val
                break
            }
        }

        if (!funcName) {
            console.error('未找到对应的云函数：', url)
            reject(new Error('API路径未注册云函数映射'))
            return
        }

        // 构建uniCloud调用参数（兼容原有event参数格式）
        const cloudData = { ...data }
        // 传递action字段（spec_manage、supplier_manage等需要）
        if (url === '/specs' && method === 'POST') {
            cloudData.action = 'category_add'
        }

        uniCloud.callFunction({
            name: funcName,
            data: cloudData
        }).then(res => {
            resolve(res.result)
        }).catch(err => {
            console.error('云函数调用失败：', funcName, err)
            reject(err)
        })
    })
}

// ==================== 便捷请求方法 ====================

function get(url, params = {}) {
    return request(url, 'GET', params)
}

function post(url, data = {}) {
    return request(url, 'POST', data)
}

function put(url, data = {}) {
    return request(url, 'PUT', data)
}

function del(url) {
    return request(url, 'DELETE')
}

// ==================== API方法导出（按模块分组） ====================

/** 认证模块 */
export const authAPI = {
    /** 微信登录 */
    login: (code) => post('/auth/login', { code }),
    /** 校验登录状态 */
    check: () => get('/auth/check')
}

/** 客户模块 */
export const customerAPI = {
    /** 客户列表（分页+筛选） */
    list: (params = {}) => get('/customers', params),
    /** 客户详情 */
    detail: (id) => get(`/customers/${id}`),
    /** 新增客户 */
    create: (data) => post('/customers', data),
    /** 更新客户 */
    update: (id, data) => put(`/customers/${id}`, data),
    /** 删除客户 */
    remove: (id) => del(`/customers/${id}`)
}

/** 供应商模块 */
export const supplierAPI = {
    list: (params = {}) => get('/suppliers', params),
    detail: (id) => get(`/suppliers/${id}`),
    create: (data) => post('/suppliers', data),
    update: (id, data) => put(`/suppliers/${id}`, data),
    remove: (id) => del(`/suppliers/${id}`),
    toggle: (id) => post(`/suppliers/${id}/toggle`)
}

/** 采购模块 */
export const purchaseAPI = {
    /** 采购单列表 */
    list: (params = {}) => get('/purchases', params),
    /** 创建采购单 */
    create: (data) => post('/purchases', data),
    /** 作废采购单 */
    cancel: (id) => post(`/purchases/${id}/cancel`)
}

/** 销售模块 */
export const salesAPI = {
    /** 销售开单 */
    create: (data) => post('/sales', data),
    /** 销售历史 */
    history: (params = {}) => get('/sales/history', params),
    /** 作废销售单 */
    cancel: (id) => post(`/sales/${id}/cancel`)
}

/** 库存模块 */
export const inventoryAPI = {
    /** 库存列表 */
    list: (params = {}) => get('/inventory', params),
    /** 库存详情 */
    detail: (id) => get(`/inventory/${id}`)
}

/** 加工模块 */
export const processingAPI = {
    /** 加工入库 */
    create: (data) => post('/processing', data),
    /** 作废加工单 */
    cancel: (id) => post(`/processing/${id}/cancel`),
    /** 加工历史 */
    history: (params = {}) => get('/processing/history', params)
}

/** 财务模块 */
export const financeAPI = {
    /** 收款核销 */
    receive: (data) => post('/finance/receive', data),
    /** 财务流水 */
    ledgers: (params = {}) => get('/finance/ledgers', params)
}

/** 规格管理模块 */
export const specAPI = {
    /** 规格类型列表（含规格值） */
    list: (params = {}) => get('/specs', params),
    /** 统一操作入口（根据action路由到不同逻辑） */
    manage: (data) => post('/specs', data)
}

/** 商品种类模块 */
export const productAPI = {
    /** 商品种类列表 */
    list: (params = {}) => get('/products', params),
    /** 新增商品种类 */
    create: (data) => post('/products', data)
}

/** 看板模块 */
export const dashboardAPI = {
    /** 获取看板数据 */
    get: () => get('/dashboard')
}

/** 导出模块 */
export const exportAPI = {
    /** 提交导出任务 */
    submit: (data) => post('/export', data)
}

export default {
    authAPI,
    customerAPI,
    supplierAPI,
    purchaseAPI,
    salesAPI,
    inventoryAPI,
    processingAPI,
    financeAPI,
    specAPI,
    productAPI,
    dashboardAPI,
    exportAPI
}
