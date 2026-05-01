<template>
	<view class="receive-container">
		<!-- ==================== 成功页面（覆盖层） ==================== -->
		<view class="success-page" v-if="showSuccessPage">
			<view class="success-content">
				<view class="success-icon">✓</view>
				<text class="success-title">收款成功</text>

				<view class="receipt-info">
					<view class="info-item">
						<text class="info-label">收款流水号</text>
						<text class="info-value highlight">{{ successInfo.ledger_no }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">客户名称</text>
						<text class="info-value">{{ successInfo.customer_name }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">收款金额</text>
						<text class="info-value amount">¥{{ fenToYuan(successInfo.amount_fen) }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">核销金额</text>
						<text class="info-value">¥{{ fenToYuan(successInfo.write_off_amount_fen) }}</text>
					</view>
					<view class="info-item" v-if="successInfo.remaining_amount_fen > 0">
						<text class="info-label">剩余欠款</text>
						<text class="info-value debt">¥{{ fenToYuan(successInfo.remaining_debt_fen) }}</text>
					</view>
				</view>

				<!-- 核销明细 -->
				<view class="write-off-details" v-if="successInfo.write_off_details && successInfo.write_off_details.length > 0">
					<view class="details-header">
						<text class="details-title">核销明细</text>
					</view>
					<view class="details-list">
						<view class="detail-item" v-for="(item, index) in successInfo.write_off_details" :key="index">
							<view class="detail-main">
								<text class="detail-order-no">{{ item.order_no }}</text>
								<text class="detail-amount">¥{{ fenToYuan(item.write_off_amount_fen) }}</text>
							</view>
							<text class="detail-time">{{ formatDateTime(item.order_time, 'YYYY-MM-DD HH:mm') }}</text>
						</view>
					</view>
				</view>

				<view class="success-actions">
					<view class="action-btn primary" @click="continueReceive">
						<text class="btn-text">继续收款</text>
					</view>
					<view class="action-btn secondary" @click="viewLedger">
						<text class="btn-text">查看账本</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 主内容区域 ==================== -->
		<view class="main-content" v-show="!showSuccessPage">
			<!-- ==================== 客户选择区域 ==================== -->
			<view class="customer-section">
				<view class="section-title">客户信息</view>
				<view class="customer-selector" @click="showCustomerPicker">
					<view class="customer-info" v-if="selectedCustomer">
						<view class="customer-main">
							<text class="customer-name">{{ selectedCustomer.name }}</text>
							<text class="customer-phone" v-if="selectedCustomer.phone">{{ selectedCustomer.phone }}</text>
						</view>
						<view class="customer-debt">
							<text class="debt-label">当前欠款：</text>
							<text class="debt-amount" :class="{ 'has-debt': selectedCustomer.total_debt_fen > 0 }">
								¥{{ fenToYuan(selectedCustomer.total_debt_fen || 0) }}
							</text>
						</view>
					</view>
					<view class="customer-placeholder" v-else>
						<text class="placeholder-text">点击选择客户</text>
					</view>
					<text class="arrow-icon">></text>
				</view>
			</view>

			<!-- ==================== 收款信息区域 ==================== -->
			<view class="payment-section">
				<view class="section-title">收款信息</view>

				<!-- 收款金额 -->
				<view class="amount-input-area" @click="showAmountKeyboard">
					<text class="input-label">收款金额（元）</text>
					<view class="amount-display">
						<text class="currency">¥</text>
						<text class="amount-value" :class="{ 'has-value': receiveAmountFen > 0 }">
							{{ receiveAmountYuan || '0.00' }}
						</text>
					</view>
					<text class="input-hint">点击输入金额</text>
				</view>

				<!-- 收款方式 -->
				<view class="payment-method-area">
					<text class="input-label">收款方式</text>
					<view class="method-list">
						<view
							class="method-item"
							v-for="method in paymentMethods"
							:key="method.value"
							:class="{ active: selectedMethod === method.value }"
							@click="selectMethod(method.value)"
						>
							<text class="method-name">{{ method.label }}</text>
						</view>
					</view>
				</view>

				<!-- 收款时间 -->
				<view class="datetime-area" @click="showDatePicker">
					<text class="input-label">收款时间</text>
					<view class="datetime-display">
						<text class="datetime-value">{{ formatDateTime(receiveTime, 'YYYY-MM-DD HH:mm') }}</text>
					</view>
				</view>

				<!-- 备注 -->
				<view class="remark-area">
					<text class="input-label">备注（可选）</text>
					<input
						class="remark-input"
						v-model="remark"
						placeholder="请输入备注信息"
						maxlength="200"
					/>
				</view>
			</view>

			<!-- ==================== 核销明细区域 ==================== -->
			<view class="write-off-section" v-if="selectedCustomer && unpaidOrders.length > 0">
				<view class="section-header">
					<text class="section-title">核销明细</text>
					<text class="order-count">共 {{ unpaidOrders.length }} 笔欠款</text>
				</view>

				<scroll-view class="order-list" scroll-y>
					<view
						class="order-item"
						v-for="(order, index) in unpaidOrders"
						:key="order._id"
						:class="{ 'will-write-off': order.willWriteOff }"
					>
						<view class="order-header">
							<text class="order-no">{{ order.order_no }}</text>
							<view class="order-status">
								<text class="status-tag" v-if="order.willWriteOff">待核销</text>
								<text class="status-tag partial" v-else-if="order.partialWriteOff">部分核销</text>
							</view>
						</view>
						<view class="order-details">
							<view class="detail-row">
								<text class="detail-label">订单金额</text>
								<text class="detail-value">¥{{ fenToYuan(order.total_amount_fen) }}</text>
							</view>
							<view class="detail-row">
								<text class="detail-label">欠款金额</text>
								<text class="detail-value debt">¥{{ fenToYuan(order.debt_amount_fen) }}</text>
							</view>
							<view class="detail-row">
								<text class="detail-label">下单时间</text>
								<text class="detail-value">{{ formatDateTime(order.create_time, 'YYYY-MM-DD HH:mm') }}</text>
							</view>
							<view class="detail-row write-off-row" v-if="order.willWriteOff">
								<text class="detail-label">核销金额</text>
								<text class="detail-value highlight">¥{{ fenToYuan(order.write_off_amount_fen) }}</text>
							</view>
						</view>
					</view>
				</scroll-view>

				<!-- 核销汇总 -->
				<view class="write-off-summary">
					<view class="summary-row">
						<text class="summary-label">本次核销金额</text>
						<text class="summary-value">¥{{ fenToYuan(totalWriteOffFen) }}</text>
					</view>
					<view class="summary-row">
						<text class="summary-label">核销后剩余欠款</text>
						<text class="summary-value debt">¥{{ fenToYuan(remainingDebtFen) }}</text>
					</view>
				</view>
			</view>

			<!-- 无欠款提示 -->
			<view class="no-debt-section" v-if="selectedCustomer && unpaidOrders.length === 0">
				<text class="no-debt-text">该客户暂无欠款</text>
			</view>
		</view>

		<!-- ==================== 底部提交区域 ==================== -->
		<view class="submit-section" v-show="!showSuccessPage">
			<view class="submit-info">
				<view class="info-row">
					<text class="info-label">收款金额</text>
					<text class="info-value amount">¥{{ fenToYuan(receiveAmountFen) }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">核销金额</text>
					<text class="info-value">¥{{ fenToYuan(totalWriteOffFen) }}</text>
				</view>
				<view class="info-row" v-if="remainingAmountFen > 0">
					<text class="info-label">剩余金额（预收）</text>
					<text class="info-value prepay">¥{{ fenToYuan(remainingAmountFen) }}</text>
				</view>
			</view>
			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="submitReceive">
				<text class="submit-text">确认收款</text>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：选择客户 ==================== -->
		<view class="popup-mask" v-if="showCustomerPopup" @click="closeCustomerPopup"></view>
		<view class="popup-container customer-popup" :class="{ show: showCustomerPopup }">
			<view class="popup-header">
				<text class="popup-title">选择客户</text>
				<text class="popup-close" @click="closeCustomerPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="customerSearchKeyword"
						placeholder="搜索客户名称或电话"
					/>
				</view>

				<!-- 客户列表 -->
				<scroll-view class="customer-list" scroll-y>
					<view
						class="customer-item"
						v-for="customer in filteredCustomers"
						:key="customer._id"
						:class="{ selected: selectedCustomer && selectedCustomer._id === customer._id }"
						@click="selectCustomer(customer)"
					>
						<view class="customer-main">
							<text class="customer-item-name">{{ customer.name }}</text>
							<text class="customer-item-phone" v-if="customer.phone">{{ customer.phone }}</text>
						</view>
						<view class="customer-debt-info" v-if="customer.total_debt_fen > 0">
							<text class="debt-text">欠款 ¥{{ fenToYuan(customer.total_debt_fen) }}</text>
						</view>
					</view>

					<view class="empty-list" v-if="filteredCustomers.length === 0">
						<text class="empty-text">暂无客户数据</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入收款金额 ==================== -->
		<view class="popup-mask" v-if="showAmountPopup" @click.stop></view>
		<view class="popup-container amount-popup" :class="{ show: showAmountPopup }">
			<view class="popup-header">
				<text class="popup-title">输入收款金额</text>
				<text class="popup-close" @click="closeAmountPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="amount-display-area">
					<text class="currency-icon">¥</text>
					<text class="amount-input-value">{{ amountInputValue || '0.00' }}</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputAmountKey('1')">1</view>
						<view class="key" @click="inputAmountKey('2')">2</view>
						<view class="key" @click="inputAmountKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputAmountKey('4')">4</view>
						<view class="key" @click="inputAmountKey('5')">5</view>
						<view class="key" @click="inputAmountKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputAmountKey('7')">7</view>
						<view class="key" @click="inputAmountKey('8')">8</view>
						<view class="key" @click="inputAmountKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputAmountKey('.')">.</view>
						<view class="key" @click="inputAmountKey('0')">0</view>
						<view class="key delete-key" @click="deleteAmountKey">删除</view>
					</view>
				</view>

				<!-- 快捷金额按钮 -->
				<view class="quick-amount-btns">
					<view class="quick-btn" @click="setQuickAmount('all')">全额收款</view>
					<view class="quick-btn" @click="setQuickAmount('half')">收一半</view>
					<view class="quick-btn" @click="setQuickAmount('clear')">清空</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmAmount">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 日期时间选择器 ==================== -->
		<picker
			mode="multiSelector"
			:value="dateTimePickerValue"
			:range="dateTimePickerRange"
			@change="onDateTimeChange"
			@columnchange="onDateTimeColumnChange"
			v-if="showDateTimePicker"
		>
			<view></view>
		</picker>
	</view>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { fenToYuan, yuanToFen, formatDateTime, showLoading, hideLoading, showSuccess, showError } from '@/utils/util.js'

// ==================== 响应式数据 ====================

// 客户相关
const selectedCustomer = ref(null) // 当前选中的客户
const customerList = ref([]) // 客户列表
const customerSearchKeyword = ref('') // 客户搜索关键词
const showCustomerPopup = ref(false) // 是否显示客户选择弹窗

// 收款信息
const receiveAmountFen = ref(0) // 收款金额（分）
const receiveAmountYuan = ref('') // 收款金额（元，用于显示）
const selectedMethod = ref('wechat') // 收款方式
const receiveTime = ref(Date.now()) // 收款时间
const remark = ref('') // 备注

// 收款方式列表
const paymentMethods = [
	{ label: '微信', value: 'wechat' },
	{ label: '支付宝', value: 'alipay' },
	{ label: '现金', value: 'cash' },
	{ label: '银行转账', value: 'bank' }
]

// 待核销订单
const unpaidOrders = ref([]) // 未结清的订单列表

// 数字键盘相关
const showAmountPopup = ref(false) // 是否显示金额输入弹窗
const amountInputValue = ref('') // 金额输入值

// 成功页面
const showSuccessPage = ref(false) // 是否显示成功页面
const successInfo = ref({}) // 成功信息

// 日期时间选择器
const showDateTimePicker = ref(false)
const dateTimePickerValue = ref([0, 0, 0, 0, 0])
const dateTimePickerRange = ref([[], [], [], [], []])

// ==================== 计算属性 ====================

// 过滤后的客户列表
const filteredCustomers = computed(() => {
	if (!customerSearchKeyword.value) {
		return customerList.value
	}
	const keyword = customerSearchKeyword.value.toLowerCase()
	return customerList.value.filter(customer => {
		return (
			(customer.name && customer.name.toLowerCase().includes(keyword)) ||
			(customer.phone && customer.phone.includes(keyword))
		)
	})
})

// 总核销金额（分）
const totalWriteOffFen = computed(() => {
	return unpaidOrders.value.reduce((sum, order) => {
		return sum + (order.write_off_amount_fen || 0)
	}, 0)
})

// 核销后剩余欠款（分）
const remainingDebtFen = computed(() => {
	if (!selectedCustomer.value) return 0
	const currentDebt = selectedCustomer.value.total_debt_fen || 0
	return Math.max(0, currentDebt - totalWriteOffFen.value)
})

// 剩余金额（预收）
const remainingAmountFen = computed(() => {
	return Math.max(0, receiveAmountFen.value - totalWriteOffFen.value)
})

// 是否可以提交
const canSubmit = computed(() => {
	// 必须选择客户
	if (!selectedCustomer.value) return false
	// 收款金额必须大于0
	if (receiveAmountFen.value <= 0) return false
	return true
})

// ==================== 监听器 ====================

// 监听收款金额变化，自动计算核销
watch(receiveAmountFen, (newAmount) => {
	calculateWriteOff(newAmount)
})

// ==================== 方法 ====================

/**
 * 显示客户选择弹窗
 */
const showCustomerPicker = () => {
	showCustomerPopup.value = true
	loadCustomers()
}

/**
 * 关闭客户选择弹窗
 */
const closeCustomerPopup = () => {
	showCustomerPopup.value = false
	customerSearchKeyword.value = ''
}

/**
 * 加载客户列表
 */
const loadCustomers = async () => {
	try {
		showLoading('加载客户...')
		const db = uniCloud.database()
		const res = await db.collection('customers')
			.where({
				status: 'active'
			})
			.orderBy('last_order_time', 'desc')
			.limit(100)
			.get()

		customerList.value = res.data || []
		hideLoading()
	} catch (error) {
		hideLoading()
		showError('加载客户失败：' + error.message)
		console.error('加载客户失败：', error)
	}
}

/**
 * 选择客户
 * @param {Object} customer - 客户对象
 */
const selectCustomer = async (customer) => {
	selectedCustomer.value = customer
	closeCustomerPopup()

	// 加载该客户的未结清订单
	await loadUnpaidOrders(customer._id)
}

/**
 * 加载客户的未结清订单
 * @param {String} customerId - 客户ID
 */
const loadUnpaidOrders = async (customerId) => {
	try {
		showLoading('加载订单...')
		const db = uniCloud.database()
		const res = await db.collection('sales_orders')
			.where({
				customer_id: customerId,
				payment_status: db.command.in(['unpaid', 'partial']) // 未付款或部分付款
			})
			.orderBy('create_time', 'asc') // 按时间正序，先进先出
			.limit(50)
			.get()

		// 处理订单数据，计算每笔订单的欠款金额
		unpaidOrders.value = (res.data || []).map(order => ({
			...order,
			debt_amount_fen: order.debt_amount_fen || (order.total_amount_fen - (order.paid_amount_fen || 0)),
			write_off_amount_fen: 0, // 本次核销金额
			willWriteOff: false, // 是否将被核销
			partialWriteOff: false // 是否部分核销
		}))

		// 根据当前收款金额计算核销
		calculateWriteOff(receiveAmountFen.value)

		hideLoading()
	} catch (error) {
		hideLoading()
		showError('加载订单失败：' + error.message)
		console.error('加载订单失败：', error)
	}
}

/**
 * 计算核销金额（先进先出）
 * @param {Number} amountFen - 收款金额（分）
 */
const calculateWriteOff = (amountFen) => {
	if (!amountFen || amountFen <= 0) {
		// 清空所有核销
		unpaidOrders.value.forEach(order => {
			order.write_off_amount_fen = 0
			order.willWriteOff = false
			order.partialWriteOff = false
		})
		return
	}

	let remainingAmount = amountFen

	// 按先进先出原则核销
	unpaidOrders.value.forEach(order => {
		if (remainingAmount <= 0) {
			// 金额已用完，后续订单不核销
			order.write_off_amount_fen = 0
			order.willWriteOff = false
			order.partialWriteOff = false
		} else {
			const orderDebt = order.debt_amount_fen || 0

			if (remainingAmount >= orderDebt) {
				// 可以全额核销该订单
				order.write_off_amount_fen = orderDebt
				order.willWriteOff = true
				order.partialWriteOff = false
				remainingAmount -= orderDebt
			} else {
				// 只能部分核销
				order.write_off_amount_fen = remainingAmount
				order.willWriteOff = true
				order.partialWriteOff = true
				remainingAmount = 0
			}
		}
	})
}

/**
 * 选择收款方式
 * @param {String} method - 收款方式
 */
const selectMethod = (method) => {
	selectedMethod.value = method
}

/**
 * 显示金额输入弹窗
 */
const showAmountKeyboard = () => {
	amountInputValue.value = receiveAmountFen.value > 0 ? fenToYuan(receiveAmountFen.value) : ''
	showAmountPopup.value = true
}

/**
 * 关闭金额输入弹窗
 */
const closeAmountPopup = () => {
	showAmountPopup.value = false
}

/**
 * 输入金额按键
 * @param {String} key - 按键值
 */
const inputAmountKey = (key) => {
	const currentValue = amountInputValue.value

	// 限制小数点
	if (key === '.') {
		if (currentValue.includes('.')) {
			return
		}
	}

	// 限制小数位数（最多2位）
	if (currentValue.includes('.')) {
		const decimalPart = currentValue.split('.')[1]
		if (decimalPart && decimalPart.length >= 2) {
			return
		}
	}

	// 限制整数位数（最多7位，支持百万级金额）
	if (!currentValue.includes('.') && currentValue.length >= 7 && key !== '.') {
		return
	}

	amountInputValue.value = currentValue + key
}

/**
 * 删除金额按键
 */
const deleteAmountKey = () => {
	amountInputValue.value = amountInputValue.value.slice(0, -1)
}

/**
 * 设置快捷金额
 * @param {String} type - 类型：all-全额收款，half-收一半，clear-清空
 */
const setQuickAmount = (type) => {
	if (type === 'all') {
		// 全额收款：收取客户当前全部欠款
		if (selectedCustomer.value && selectedCustomer.value.total_debt_fen > 0) {
			amountInputValue.value = fenToYuan(selectedCustomer.value.total_debt_fen)
		}
	} else if (type === 'half') {
		// 收一半
		if (selectedCustomer.value && selectedCustomer.value.total_debt_fen > 0) {
			const halfAmount = Math.floor(selectedCustomer.value.total_debt_fen / 2)
			amountInputValue.value = fenToYuan(halfAmount)
		}
	} else if (type === 'clear') {
		amountInputValue.value = ''
	}
}

/**
 * 确认金额
 */
const confirmAmount = () => {
	receiveAmountFen.value = yuanToFen(amountInputValue.value)
	receiveAmountYuan.value = amountInputValue.value
	closeAmountPopup()
}

/**
 * 显示日期选择器
 */
const showDatePicker = () => {
	// 使用 uni-app 的日期时间选择器
	uni.showActionSheet({
		itemList: ['选择日期时间'],
		success: () => {
			// 简化处理：使用当前时间，允许用户手动修改
			// 实际项目中可以使用第三方日期时间选择器组件
			showError('请使用默认时间或联系开发添加日期选择功能')
		}
	})
}

/**
 * 提交收款
 */
const submitReceive = async () => {
	// 校验
	if (!canSubmit.value) {
		if (!selectedCustomer.value) {
			showError('请选择客户')
		} else if (receiveAmountFen.value <= 0) {
			showError('请输入收款金额')
		}
		return
	}

	try {
		showLoading('正在处理...')

		// 构建核销明细
		const writeOffDetails = unpaidOrders.value
			.filter(order => order.write_off_amount_fen > 0)
			.map(order => ({
				order_id: order._id,
				order_no: order.order_no,
				order_time: order.create_time,
				order_amount_fen: order.total_amount_fen,
				order_debt_fen: order.debt_amount_fen,
				write_off_amount_fen: order.write_off_amount_fen
			}))

		// 调用云函数
		const res = await uniCloud.callFunction({
			name: 'finance_receive',
			data: {
				customer_id: selectedCustomer.value._id,
				amount_fen: receiveAmountFen.value,
				payment_method: selectedMethod.value,
				receive_time: receiveTime.value,
				remark: remark.value,
				write_off_details: writeOffDetails
			}
		})

		hideLoading()

		if (res.result.code === 0) {
			// 收款成功
			showSuccess('收款成功')
			successInfo.value = {
				...res.result.data,
				customer_name: selectedCustomer.value.name
			}
			showSuccessPage.value = true
		} else {
			showError(res.result.message || '收款失败')
		}
	} catch (error) {
		hideLoading()
		showError('收款失败：' + error.message)
		console.error('收款失败：', error)
	}
}

/**
 * 继续收款
 */
const continueReceive = () => {
	// 重置表单
	selectedCustomer.value = null
	receiveAmountFen.value = 0
	receiveAmountYuan.value = ''
	selectedMethod.value = 'wechat'
	receiveTime.value = Date.now()
	remark.value = ''
	unpaidOrders.value = []
	showSuccessPage.value = false
	successInfo.value = {}
}

/**
 * 查看账本
 */
const viewLedger = () => {
	// 跳转到客户账本页面
	uni.navigateTo({
		url: '/pages/customer/list'
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时，可以预加载客户列表
	// loadCustomers()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.receive-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	padding-bottom: 300rpx; // 为底部提交区域留出空间
}

/* ==================== 成功页面 ==================== */
.success-page {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: #FFFFFF;
	z-index: 400;
	overflow-y: auto;
}

.success-content {
	padding: 60rpx 40rpx;
	text-align: center;
}

.success-icon {
	width: 120rpx;
	height: 120rpx;
	background-color: #4CAF50;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	margin: 0 auto 30rpx;
	font-size: 60rpx;
	color: #FFFFFF;
}

.success-title {
	font-size: 40rpx;
	color: #333333;
	font-weight: 600;
	display: block;
	margin-bottom: 40rpx;
}

.receipt-info {
	background-color: #F8F8F8;
	border-radius: 16rpx;
	padding: 30rpx;
	margin-bottom: 30rpx;
}

.info-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.info-label {
	font-size: 28rpx;
	color: #666666;
}

.info-value {
	font-size: 28rpx;
	color: #333333;

	&.highlight {
		color: #4A90E2;
		font-weight: 600;
	}

	&.amount {
		color: #4CAF50;
		font-size: 36rpx;
		font-weight: 600;
	}

	&.debt {
		color: #FF5722;
		font-weight: 500;
	}
}

.write-off-details {
	background-color: #F8F8F8;
	border-radius: 16rpx;
	padding: 30rpx;
	margin-bottom: 40rpx;
	text-align: left;
}

.details-header {
	margin-bottom: 20rpx;
	padding-bottom: 20rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.details-title {
	font-size: 30rpx;
	color: #333333;
	font-weight: 600;
}

.details-list {
	//
}

.detail-item {
	padding: 20rpx 0;
	border-bottom: 1rpx solid #EEEEEE;

	&:last-child {
		border-bottom: none;
	}
}

.detail-main {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 10rpx;
}

.detail-order-no {
	font-size: 28rpx;
	color: #333333;
}

.detail-amount {
	font-size: 28rpx;
	color: #4A90E2;
	font-weight: 500;
}

.detail-time {
	font-size: 24rpx;
	color: #999999;
}

.success-actions {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.action-btn {
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 16rpx;

	&.primary {
		background-color: #4A90E2;
	}

	&.secondary {
		background-color: #F8F8F8;
		border: 2rpx solid #4A90E2;
	}
}

.btn-text {
	font-size: 32rpx;
	font-weight: 600;

	.primary & {
		color: #FFFFFF;
	}

	.secondary & {
		color: #4A90E2;
	}
}

/* ==================== 主内容区域 ==================== */
.main-content {
	//
}

/* ==================== 客户选择区域 ==================== */
.customer-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.section-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 20rpx;
	display: block;
}

.customer-selector {
	display: flex;
	align-items: center;
	padding: 30rpx;
	background-color: #F8F8F8;
	border-radius: 16rpx;
}

.customer-info {
	flex: 1;
}

.customer-main {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;
}

.customer-name {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
}

.customer-phone {
	font-size: 28rpx;
	color: #666666;
	margin-left: 20rpx;
}

.customer-debt {
	display: flex;
	align-items: center;
}

.debt-label {
	font-size: 28rpx;
	color: #999999;
}

.debt-amount {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;

	&.has-debt {
		color: #FF5722;
	}
}

.customer-placeholder {
	flex: 1;
}

.placeholder-text {
	font-size: 32rpx;
	color: #CCCCCC;
}

.arrow-icon {
	font-size: 32rpx;
	color: #CCCCCC;
	margin-left: 20rpx;
}

/* ==================== 收款信息区域 ==================== */
.payment-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.amount-input-area {
	padding: 30rpx;
	background-color: #F8F8F8;
	border-radius: 16rpx;
	margin-bottom: 20rpx;
}

.input-label {
	font-size: 28rpx;
	color: #666666;
	display: block;
	margin-bottom: 15rpx;
}

.amount-display {
	display: flex;
	align-items: baseline;
	margin-bottom: 10rpx;
}

.currency {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
}

.amount-value {
	font-size: 48rpx;
	color: #CCCCCC;
	font-weight: 600;
	margin-left: 10rpx;

	&.has-value {
		color: #4CAF50;
	}
}

.input-hint {
	font-size: 24rpx;
	color: #CCCCCC;
}

.payment-method-area {
	margin-bottom: 20rpx;
}

.method-list {
	display: flex;
	flex-wrap: wrap;
	gap: 15rpx;
}

.method-item {
	padding: 20rpx 30rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	border: 2rpx solid transparent;

	&.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}
}

.method-name {
	font-size: 28rpx;
	color: #666666;

	.active & {
		color: #4A90E2;
		font-weight: 500;
	}
}

.datetime-area {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 25rpx 0;
	border-bottom: 1rpx solid #EEEEEE;
}

.datetime-display {
	//
}

.datetime-value {
	font-size: 28rpx;
	color: #333333;
}

.remark-area {
	padding-top: 20rpx;
}

.remark-input {
	width: 100%;
	height: 70rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 28rpx;
}

/* ==================== 核销明细区域 ==================== */
.write-off-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.section-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 20rpx;
}

.order-count {
	font-size: 26rpx;
	color: #999999;
}

.order-list {
	max-height: 500rpx;
}

.order-item {
	padding: 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	margin-bottom: 15rpx;
	border: 2rpx solid transparent;

	&.will-write-off {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}
}

.order-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 15rpx;
}

.order-no {
	font-size: 28rpx;
	color: #333333;
	font-weight: 500;
}

.status-tag {
	font-size: 24rpx;
	color: #4CAF50;
	background-color: #FFFFFF;
	padding: 5rpx 15rpx;
	border-radius: 6rpx;

	&.partial {
		color: #FF9800;
	}
}

.order-details {
	//
}

.detail-row {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 10rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.detail-label {
	font-size: 26rpx;
	color: #999999;
}

.detail-value {
	font-size: 26rpx;
	color: #333333;

	&.debt {
		color: #FF5722;
		font-weight: 500;
	}

	&.highlight {
		color: #4CAF50;
		font-weight: 600;
	}
}

.write-off-row {
	margin-top: 15rpx;
	padding-top: 15rpx;
	border-top: 1rpx solid #EEEEEE;
}

.write-off-summary {
	margin-top: 20rpx;
	padding-top: 20rpx;
	border-top: 2rpx solid #EEEEEE;
}

.summary-row {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.summary-label {
	font-size: 28rpx;
	color: #666666;
}

.summary-value {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;

	&.debt {
		color: #FF5722;
	}
}

/* ==================== 无欠款提示 ==================== */
.no-debt-section {
	background-color: #FFFFFF;
	padding: 60rpx 30rpx;
	text-align: center;
}

.no-debt-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* ==================== 底部提交区域 ==================== */
.submit-section {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	background-color: #FFFFFF;
	padding: 30rpx;
	box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);
	z-index: 100;
}

.submit-info {
	margin-bottom: 20rpx;
}

.info-row {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.info-label {
	font-size: 28rpx;
	color: #666666;
}

.info-value {
	font-size: 28rpx;
	color: #333333;

	&.amount {
		color: #4CAF50;
		font-size: 36rpx;
		font-weight: 600;
	}

	&.prepay {
		color: #4A90E2;
	}
}

.submit-btn {
	height: 100rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	border-radius: 16rpx;

	&.disabled {
		background-color: #CCCCCC;
	}
}

.submit-text {
	font-size: 36rpx;
	color: #FFFFFF;
	font-weight: 600;
}

/* ==================== 弹窗遮罩 ==================== */
.popup-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
	z-index: 200;
}

/* ==================== 半屏弹窗容器 ==================== */
.popup-container {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: #FFFFFF;
	border-radius: 24rpx 24rpx 0 0;
	z-index: 300;
	transform: translateY(100%);
	transition: transform 0.3s;

	&.show {
		transform: translateY(0);
	}
}

.popup-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.popup-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.popup-close {
	font-size: 48rpx;
	color: #999999;
	line-height: 1;
}

.popup-content {
	max-height: 70vh;
	overflow-y: auto;
}

/* ==================== 客户选择弹窗 ==================== */
.search-box {
	padding: 20rpx 30rpx;
}

.search-input {
	width: 100%;
	height: 70rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 28rpx;
}

.customer-list {
	max-height: 50vh;
}

.customer-item {
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;

	&.selected {
		background-color: #E3F2FD;
	}
}

.customer-item-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
}

.customer-item-phone {
	font-size: 28rpx;
	color: #666666;
	margin-left: 20rpx;
}

.customer-debt-info {
	margin-top: 10rpx;
}

.debt-text {
	font-size: 24rpx;
	color: #FF5722;
}

.empty-list {
	padding: 80rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* ==================== 金额输入弹窗 ==================== */
.amount-display-area {
	padding: 40rpx 30rpx;
	text-align: center;
	background-color: #F8F8F8;
	display: flex;
	align-items: baseline;
	justify-content: center;
}

.currency-icon {
	font-size: 40rpx;
	color: #333333;
	font-weight: 600;
}

.amount-input-value {
	font-size: 56rpx;
	color: #333333;
	font-weight: 600;
	margin-left: 10rpx;
}

/* 自定义数字键盘 */
.custom-keyboard {
	padding: 20rpx;
	background-color: #F8F8F8;
}

.keyboard-row {
	display: flex;
	margin-bottom: 10rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.key {
	flex: 1;
	height: 100rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #FFFFFF;
	border-radius: 12rpx;
	font-size: 40rpx;
	color: #333333;
	margin: 0 5rpx;
	box-shadow: 0 2rpx 5rpx rgba(0, 0, 0, 0.05);

	&:active {
		background-color: #EEEEEE;
	}

	&.delete-key {
		background-color: #FFEBE9;
		color: #FF5722;
	}
}

.quick-amount-btns {
	display: flex;
	gap: 15rpx;
	padding: 20rpx 30rpx;
}

.quick-btn {
	flex: 1;
	height: 70rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #E3F2FD;
	border-radius: 12rpx;
	font-size: 28rpx;
	color: #4A90E2;
}

.confirm-btn {
	margin: 20rpx 30rpx 30rpx;
	height: 100rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	border-radius: 16rpx;
}

.confirm-text {
	font-size: 32rpx;
	color: #FFFFFF;
	font-weight: 600;
}
</style>
