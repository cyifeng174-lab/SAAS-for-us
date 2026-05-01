/**
 * 工具函数库
 * 包含金额转换、日期格式化等通用功能
 */

/**
 * 分转元
 * @param {Number} fen - 金额（分）
 * @returns {String} 金额（元），保留两位小数
 */
export const fenToYuan = (fen) => {
	if (fen === null || fen === undefined) return '0.00'
	return (fen / 100).toFixed(2)
}

/**
 * 元转分
 * @param {Number|String} yuan - 金额（元）
 * @returns {Number} 金额（分），整数
 */
export const yuanToFen = (yuan) => {
	if (yuan === null || yuan === undefined || yuan === '') return 0
	return Math.round(parseFloat(yuan) * 100)
}

/**
 * 格式化日期时间
 * @param {Number|Date} timestamp - 时间戳或Date对象
 * @param {String} format - 格式化模式，默认 'YYYY-MM-DD HH:mm:ss'
 * @returns {String} 格式化后的日期时间字符串
 */
export const formatDateTime = (timestamp, format = 'YYYY-MM-DD HH:mm:ss') => {
	if (!timestamp) return ''
	
	const date = new Date(timestamp)
	const year = date.getFullYear()
	const month = String(date.getMonth() + 1).padStart(2, '0')
	const day = String(date.getDate()).padStart(2, '0')
	const hours = String(date.getHours()).padStart(2, '0')
	const minutes = String(date.getMinutes()).padStart(2, '0')
	const seconds = String(date.getSeconds()).padStart(2, '0')
	
	return format
		.replace('YYYY', year)
		.replace('MM', month)
		.replace('DD', day)
		.replace('HH', hours)
		.replace('mm', minutes)
		.replace('ss', seconds)
}

/**
 * 格式化日期
 * @param {Number|Date} timestamp - 时间戳或Date对象
 * @returns {String} 格式化后的日期字符串
 */
export const formatDate = (timestamp) => {
	return formatDateTime(timestamp, 'YYYY-MM-DD')
}

/**
 * 格式化时间
 * @param {Number|Date} timestamp - 时间戳或Date对象
 * @returns {String} 格式化后的时间字符串
 */
export const formatTime = (timestamp) => {
	return formatDateTime(timestamp, 'HH:mm:ss')
}

/**
 * 显示加载提示
 * @param {String} title - 提示文字
 */
export const showLoading = (title = '加载中...') => {
	uni.showLoading({
		title: title,
		mask: true
	})
}

/**
 * 隐藏加载提示
 */
export const hideLoading = () => {
	uni.hideLoading()
}

/**
 * 显示成功提示
 * @param {String} title - 提示文字
 */
export const showSuccess = (title) => {
	uni.showToast({
		title: title,
		icon: 'success',
		duration: 2000
	})
}

/**
 * 显示错误提示
 * @param {String} title - 提示文字
 */
export const showError = (title) => {
	uni.showToast({
		title: title,
		icon: 'none',
		duration: 3000
	})
}

/**
 * 显示确认对话框
 * @param {String} content - 对话框内容
 * @param {String} title - 对话框标题
 * @returns {Promise} 返回Promise，resolve为true表示确认，false表示取消
 */
export const showConfirm = (content, title = '提示') => {
	return new Promise((resolve) => {
		uni.showModal({
			title: title,
			content: content,
			success: (res) => {
				resolve(res.confirm)
			},
			fail: () => {
				resolve(false)
			}
		})
	})
}

/**
 * 防抖函数
 * @param {Function} fn - 需要防抖的函数
 * @param {Number} delay - 延迟时间（毫秒）
 * @returns {Function} 防抖后的函数
 */
export const debounce = (fn, delay = 300) => {
	let timer = null
	return function(...args) {
		if (timer) clearTimeout(timer)
		timer = setTimeout(() => {
			fn.apply(this, args)
		}, delay)
	}
}

/**
 * 节流函数
 * @param {Function} fn - 需要节流的函数
 * @param {Number} delay - 延迟时间（毫秒）
 * @returns {Function} 节流后的函数
 */
export const throttle = (fn, delay = 300) => {
	let lastTime = 0
	return function(...args) {
		const now = Date.now()
		if (now - lastTime >= delay) {
			lastTime = now
			fn.apply(this, args)
		}
	}
}

/**
 * 深拷贝
 * @param {Object} obj - 需要拷贝的对象
 * @returns {Object} 拷贝后的新对象
 */
export const deepClone = (obj) => {
	if (obj === null || typeof obj !== 'object') return obj
	if (obj instanceof Date) return new Date(obj)
	if (obj instanceof Array) return obj.map(item => deepClone(item))
	
	const cloned = {}
	for (let key in obj) {
		if (obj.hasOwnProperty(key)) {
			cloned[key] = deepClone(obj[key])
		}
	}
	return cloned
}

/**
 * 生成唯一ID
 * @returns {String} 唯一ID字符串
 */
export const generateUUID = () => {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
		const r = Math.random() * 16 | 0
		const v = c === 'x' ? r : (r & 0x3 | 0x8)
		return v.toString(16)
	})
}
