<template>
	<view class="billing-container">
		<!-- ==================== 顶部区域 ==================== -->
		<view class="top-section">
			<!-- 销售类型切换 -->
			<view class="sale-type-switch">
				<view
					class="type-btn"
					:class="{ active: saleType === 'wholesale' }"
					@click="switchSaleType('wholesale')"
				>
					批发
				</view>
				<view
					class="type-btn"
					:class="{ active: saleType === 'retail' }"
					@click="switchSaleType('retail')"
				>
					零售
				</view>
			</view>

			<!-- 客户选择器 -->
			<view class="customer-selector" @click="showCustomerPicker">
				<view class="customer-info">
					<text class="label">{{ saleType === 'wholesale' ? '客户（必选）' : '客户（可选）' }}</text>
					<text class="customer-name" v-if="selectedCustomer">
						{{ selectedCustomer.name }}
						<text class="customer-phone" v-if="selectedCustomer.phone">（{{ selectedCustomer.phone }}）</text>
					</text>
					<text class="placeholder" v-else>点击选择客户</text>
				</view>
				<view class="arrow">></view>
			</view>

			<!-- 客户欠款余额 -->
			<view class="customer-debt" v-if="selectedCustomer">
				<text class="debt-label">当前欠款：</text>
				<text class="debt-amount" :class="{ 'has-debt': selectedCustomer.total_debt_fen > 0 }">
					¥{{ fenToYuan(selectedCustomer.total_debt_fen || 0) }}
				</text>
			</view>
		</view>

		<!-- ==================== 商品列表区域 ==================== -->
		<view class="products-section">
			<view class="section-header">
				<text class="title">已选商品</text>
				<text class="count" v-if="orderItems.length > 0">（{{ orderItems.length }}项）</text>
			</view>

			<!-- 空状态 -->
			<view class="empty-state" v-if="orderItems.length === 0">
				<text class="empty-text">暂无商品，请点击下方按钮添加</text>
			</view>

			<!-- 商品列表 -->
			<view class="product-list" v-else>
				<view
					class="product-card"
					v-for="(item, index) in orderItems"
					:key="index"
				>
					<view class="product-header">
						<text class="product-name">{{ item.product_name }}</text>
						<view class="product-actions">
							<text class="action-btn edit" @click="editProduct(index)">修改</text>
							<text class="action-btn delete" @click="deleteProduct(index)">删除</text>
						</view>
					</view>

					<view class="product-details">
						<view class="detail-row">
							<text class="detail-label">规格：</text>
							<text class="detail-value">{{ item.spec || '-' }}</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">等级：</text>
							<text class="detail-value">{{ item.grade || '-' }}</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">重量：</text>
							<text class="detail-value highlight">{{ item.weight_jin }} 斤</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">单价：</text>
							<text class="detail-value highlight">¥{{ fenToYuan(item.unit_price_fen) }}/斤</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">小计：</text>
							<text class="detail-value subtotal">¥{{ fenToYuan(item.subtotal_fen) }}</text>
						</view>
					</view>

					<!-- 库存不足提示 -->
					<view class="stock-warning" v-if="item.stockWarning">
						<text class="warning-text">{{ item.stockWarning }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 添加商品按钮 ==================== -->
		<view class="add-product-section">
			<view class="add-btn" @click="showInventoryPicker">
				<text class="add-icon">+</text>
				<text class="add-text">添加商品</text>
			</view>
		</view>

		<!-- ==================== 底部结算区域（固定） ==================== -->
		<view class="settlement-section">
			<view class="settlement-info">
				<view class="info-row">
					<text class="info-label">总重量：</text>
					<text class="info-value">{{ totalWeightJin.toFixed(2) }} 斤</text>
				</view>
				<view class="info-row">
					<text class="info-label">总金额：</text>
					<text class="info-value highlight">¥{{ fenToYuan(totalAmountFen) }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">预估毛利：</text>
					<text class="info-value profit">¥{{ fenToYuan(estimatedProfitFen) }}</text>
				</view>
			</view>

			<view class="payment-section">
				<view class="payment-input" @click="showPaymentKeyboard">
					<text class="payment-label">实收金额：</text>
					<text class="payment-value" :class="{ 'has-input': paidAmountFen > 0 }">
						¥{{ fenToYuan(paidAmountFen) }}
					</text>
					<text class="payment-hint">（点击输入）</text>
				</view>

				<view class="debt-display" v-if="totalAmountFen > 0">
					<text class="debt-label">欠款金额：</text>
					<text class="debt-value" :class="{ 'has-debt': debtAmountFen > 0 }">
						¥{{ fenToYuan(debtAmountFen) }}
					</text>
				</view>
			</view>

			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="submitOrder">
				<text class="submit-text">确认开单</text>
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
						@input="searchCustomers"
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

		<!-- ==================== 半屏弹窗：选择库存商品 ==================== -->
		<view class="popup-mask" v-if="showInventoryPopup" @click="closeInventoryPopup"></view>
		<view class="popup-container inventory-popup" :class="{ show: showInventoryPopup }">
			<view class="popup-header">
				<text class="popup-title">选择商品</text>
				<text class="popup-close" @click="closeInventoryPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="inventorySearchKeyword"
						placeholder="搜索商品名称"
						@input="searchInventory"
					/>
				</view>

				<!-- 库存列表 -->
				<scroll-view class="inventory-list" scroll-y>
					<view
						class="inventory-item"
						v-for="inventory in filteredInventory"
						:key="inventory._id"
						@click="selectInventory(inventory)"
					>
						<view class="inventory-main">
							<text class="inventory-name">{{ inventory.product_name }}</text>
							<view class="inventory-specs">
								<text class="spec-tag" v-if="inventory.spec">{{ inventory.spec }}</text>
								<text class="spec-tag" v-if="inventory.grade">{{ inventory.grade }}</text>
							</view>
						</view>
						<view class="inventory-stock">
							<text class="stock-label">库存：</text>
							<text class="stock-value" :class="{ 'low-stock': inventory.current_stock_jin <= inventory.warning_stock_jin }">
								{{ inventory.current_stock_jin.toFixed(2) }} 斤
							</text>
						</view>
						<view class="inventory-price" v-if="inventory.suggested_price_fen">
							<text class="price-label">建议售价：</text>
							<text class="price-value">¥{{ fenToYuan(inventory.suggested_price_fen) }}/斤</text>
						</view>
					</view>

					<view class="empty-list" v-if="filteredInventory.length === 0">
						<text class="empty-text">暂无库存数据</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入重量和单价 ==================== -->
		<view class="popup-mask" v-if="showKeyboardPopup" @click.stop></view>
		<view class="popup-container keyboard-popup" :class="{ show: showKeyboardPopup }">
			<view class="popup-header">
				<text class="popup-title">{{ editingProduct ? '修改商品' : '添加商品' }}</text>
				<text class="popup-close" @click="closeKeyboardPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 商品信息 -->
				<view class="selected-product-info" v-if="selectedInventory">
					<text class="product-title">{{ selectedInventory.product_name }}</text>
					<view class="product-specs">
						<text class="spec-tag" v-if="selectedInventory.spec">{{ selectedInventory.spec }}</text>
						<text class="spec-tag" v-if="selectedInventory.grade">{{ selectedInventory.grade }}</text>
					</view>
					<view class="stock-info">
						<text class="stock-label">当前库存：</text>
						<text class="stock-value">{{ selectedInventory.current_stock_jin.toFixed(2) }} 斤</text>
					</view>
				</view>

				<!-- 输入区域 -->
				<view class="input-section">
					<view class="input-row">
						<text class="input-label">重量（斤）：</text>
						<view class="input-box" @click="focusInput('weight')">
							<text class="input-value" :class="{ 'has-value': inputWeight }">
								{{ inputWeight || '0.00' }}
							</text>
						</view>
					</view>

					<view class="input-row">
						<text class="input-label">单价（元/斤）：</text>
						<view class="input-box" @click="focusInput('price')">
							<text class="input-value" :class="{ 'has-value': inputPrice }">
								{{ inputPrice || '0.00' }}
							</text>
						</view>
					</view>

					<view class="input-row subtotal-row">
						<text class="input-label">小计：</text>
						<text class="subtotal-value">¥{{ calculateSubtotal() }}</text>
					</view>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputKey('1')">1</view>
						<view class="key" @click="inputKey('2')">2</view>
						<view class="key" @click="inputKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputKey('4')">4</view>
						<view class="key" @click="inputKey('5')">5</view>
						<view class="key" @click="inputKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputKey('7')">7</view>
						<view class="key" @click="inputKey('8')">8</view>
						<view class="key" @click="inputKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputKey('.')">.</view>
						<view class="key" @click="inputKey('0')">0</view>
						<view class="key delete-key" @click="deleteKey">删除</view>
					</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmProduct">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入实收金额 ==================== -->
		<view class="popup-mask" v-if="showPaymentInputPopup" @click.stop></view>
		<view class="popup-container payment-popup" :class="{ show: showPaymentInputPopup }">
			<view class="popup-header">
				<text class="popup-title">输入实收金额</text>
				<text class="popup-close" @click="closePaymentInputPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="payment-display">
					<text class="payment-label">实收金额（元）：</text>
					<text class="payment-amount">¥{{ paymentInputValue || '0.00' }}</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputPaymentKey('1')">1</view>
						<view class="key" @click="inputPaymentKey('2')">2</view>
						<view class="key" @click="inputPaymentKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaymentKey('4')">4</view>
						<view class="key" @click="inputPaymentKey('5')">5</view>
						<view class="key" @click="inputPaymentKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaymentKey('7')">7</view>
						<view class="key" @click="inputPaymentKey('8')">8</view>
						<view class="key" @click="inputPaymentKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaymentKey('.')">.</view>
						<view class="key" @click="inputPaymentKey('0')">0</view>
						<view class="key delete-key" @click="deletePaymentKey">删除</view>
					</view>
				</view>

				<!-- 快捷金额按钮 -->
				<view class="quick-amount-btns">
					<view class="quick-btn" @click="setQuickAmount('all')">全额收款</view>
					<view class="quick-btn" @click="setQuickAmount('clear')">清空</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmPayment">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 成功页面 ==================== -->
		<view class="success-page" v-if="showSuccessPage">
			<view class="success-content">
				<view class="success-icon">✓</view>
				<text class="success-title">开单成功</text>

				<view class="order-info">
					<view class="info-item">
						<text class="info-label">订单号：</text>
						<text class="info-value">{{ successOrderInfo.order_no }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">客户：</text>
						<text class="info-value">{{ successOrderInfo.customer_name }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">总金额：</text>
						<text class="info-value highlight">¥{{ fenToYuan(successOrderInfo.total_amount_fen) }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">实收：</text>
						<text class="info-value">¥{{ fenToYuan(successOrderInfo.paid_amount_fen) }}</text>
					</view>
					<view class="info-item" v-if="successOrderInfo.debt_amount_fen > 0">
						<text class="info-label">欠款：</text>
						<text class="info-value debt">¥{{ fenToYuan(successOrderInfo.debt_amount_fen) }}</text>
					</view>
				</view>

				<view class="success-actions">
					<view class="action-btn primary" @click="continueBilling">
						<text class="btn-text">继续开单</text>
					</view>
					<view class="action-btn secondary" @click="viewOrder">
						<text class="btn-text">查看订单</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, yuanToFen, showLoading, hideLoading, showSuccess, showError } from '@/utils/util.js'

// ==================== 响应式数据 ====================

// 销售类型：wholesale-批发，retail-零售
const saleType = ref('wholesale')

// 客户相关
const selectedCustomer = ref(null) // 当前选中的客户
const customerList = ref([]) // 客户列表
const customerSearchKeyword = ref('') // 客户搜索关键词
const showCustomerPopup = ref(false) // 是否显示客户选择弹窗

// 库存相关
const inventoryList = ref([]) // 库存列表
const inventorySearchKeyword = ref('') // 库存搜索关键词
const showInventoryPopup = ref(false) // 是否显示库存选择弹窗
const selectedInventory = ref(null) // 当前选中的库存商品

// 订单明细
const orderItems = ref([]) // 已选商品列表

// 数字键盘相关
const showKeyboardPopup = ref(false) // 是否显示数字键盘弹窗
const inputWeight = ref('') // 输入的重量
const inputPrice = ref('') // 输入的单价
const currentInputField = ref('weight') // 当前输入字段：weight 或 price
const editingProductIndex = ref(-1) // 正在编辑的商品索引，-1表示新增

// 实收金额输入
const showPaymentInputPopup = ref(false) // 是否显示实收金额输入弹窗
const paymentInputValue = ref('') // 实收金额输入值
const paidAmountFen = ref(0) // 实收金额（分）

// 成功页面
const showSuccessPage = ref(false) // 是否显示成功页面
const successOrderInfo = ref({}) // 成功订单信息

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

// 过滤后的库存列表
const filteredInventory = computed(() => {
	if (!inventorySearchKeyword.value) {
		return inventoryList.value
	}
	const keyword = inventorySearchKeyword.value.toLowerCase()
	return inventoryList.value.filter(inventory => {
		return (
			(inventory.product_name && inventory.product_name.toLowerCase().includes(keyword)) ||
			(inventory.spec && inventory.spec.toLowerCase().includes(keyword)) ||
			(inventory.grade && inventory.grade.toLowerCase().includes(keyword))
		)
	})
})

// 总重量（斤）
const totalWeightJin = computed(() => {
	return orderItems.value.reduce((sum, item) => sum + (parseFloat(item.weight_jin) || 0), 0)
})

// 总金额（分）
const totalAmountFen = computed(() => {
	return orderItems.value.reduce((sum, item) => sum + (parseInt(item.subtotal_fen) || 0), 0)
})

// 预估毛利（分）
// 注意：这里使用库存的平均成本估算，实际毛利以云函数FIFO计算为准
const estimatedProfitFen = computed(() => {
	return orderItems.value.reduce((sum, item) => {
		const subtotal = parseInt(item.subtotal_fen) || 0
		const cost = parseInt(item.estimated_cost_fen) || 0
		return sum + (subtotal - cost)
	}, 0)
})

// 欠款金额（分）
const debtAmountFen = computed(() => {
	return Math.max(0, totalAmountFen.value - paidAmountFen.value)
})

// 是否可以提交订单
const canSubmit = computed(() => {
	// 批发模式必须选择客户
	if (saleType.value === 'wholesale' && !selectedCustomer.value) {
		return false
	}
	// 必须有商品
	if (orderItems.value.length === 0) {
		return false
	}
	// 不能有库存不足的商品
	const hasStockIssue = orderItems.value.some(item => item.stockWarning)
	if (hasStockIssue) {
		return false
	}
	return true
})

// ==================== 方法 ====================

/**
 * 切换销售类型
 * @param {String} type - 销售类型：wholesale 或 retail
 */
const switchSaleType = (type) => {
	saleType.value = type
	// 切换类型时清空已选客户和商品
	selectedCustomer.value = null
	orderItems.value = []
	paidAmountFen.value = 0
}

/**
 * 显示客户选择弹窗
 */
const showCustomerPicker = () => {
	showCustomerPopup.value = true
	// 加载客户列表
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
				status: 'active' // 只查询正常状态的客户
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
 * 搜索客户（防抖处理）
 */
let customerSearchTimer = null
const searchCustomers = () => {
	if (customerSearchTimer) clearTimeout(customerSearchTimer)
	customerSearchTimer = setTimeout(() => {
		// 计算属性会自动过滤，这里不需要额外处理
	}, 300)
}

/**
 * 选择客户
 * @param {Object} customer - 客户对象
 */
const selectCustomer = (customer) => {
	selectedCustomer.value = customer
	closeCustomerPopup()
}

/**
 * 显示库存选择弹窗
 */
const showInventoryPicker = () => {
	showInventoryPopup.value = true
	// 加载库存列表
	loadInventory()
}

/**
 * 关闭库存选择弹窗
 */
const closeInventoryPopup = () => {
	showInventoryPopup.value = false
	inventorySearchKeyword.value = ''
}

/**
 * 加载库存列表
 */
const loadInventory = async () => {
	try {
		showLoading('加载库存...')
		const db = uniCloud.database()
		const res = await db.collection('inventories')
			.where({
				current_stock_jin: db.command.gt(0) // 只查询有库存的商品
			})
			.orderBy('update_time', 'desc')
			.limit(100)
			.get()

		inventoryList.value = res.data || []
		hideLoading()
	} catch (error) {
		hideLoading()
		showError('加载库存失败：' + error.message)
		console.error('加载库存失败：', error)
	}
}

/**
 * 搜索库存（防抖处理）
 */
let inventorySearchTimer = null
const searchInventory = () => {
	if (inventorySearchTimer) clearTimeout(inventorySearchTimer)
	inventorySearchTimer = setTimeout(() => {
		// 计算属性会自动过滤，这里不需要额外处理
	}, 300)
}

/**
 * 选择库存商品
 * @param {Object} inventory - 库存对象
 */
const selectInventory = (inventory) => {
	selectedInventory.value = inventory
	// 重置输入
	inputWeight.value = ''
	inputPrice.value = inventory.suggested_price_fen ? fenToYuan(inventory.suggested_price_fen) : ''
	currentInputField.value = 'weight'
	editingProductIndex.value = -1 // 新增模式

	// 关闭库存选择弹窗，打开数字键盘弹窗
	closeInventoryPopup()
	showKeyboardPopup.value = true
}

/**
 * 编辑商品
 * @param {Number} index - 商品索引
 */
const editProduct = (index) => {
	const item = orderItems.value[index]
	editingProductIndex.value = index

	// 查找对应的库存记录
	const inventory = inventoryList.value.find(inv => inv._id === item.inventory_id)
	if (inventory) {
		selectedInventory.value = inventory
	} else {
		// 如果找不到库存记录，构造一个临时对象
		selectedInventory.value = {
			_id: item.inventory_id,
			product_name: item.product_name,
			spec: item.spec,
			grade: item.grade,
			current_stock_jin: 999999 // 编辑时不限制库存
		}
	}

	// 填充现有值
	inputWeight.value = String(item.weight_jin)
	inputPrice.value = fenToYuan(item.unit_price_fen)
	currentInputField.value = 'weight'

	// 打开数字键盘弹窗
	showKeyboardPopup.value = true
}

/**
 * 删除商品
 * @param {Number} index - 商品索引
 */
const deleteProduct = (index) => {
	uni.showModal({
		title: '确认删除',
		content: '确定要删除该商品吗？',
		success: (res) => {
			if (res.confirm) {
				orderItems.value.splice(index, 1)
			}
		}
	})
}

/**
 * 关闭数字键盘弹窗
 */
const closeKeyboardPopup = () => {
	showKeyboardPopup.value = false
	selectedInventory.value = null
	inputWeight.value = ''
	inputPrice.value = ''
	editingProductIndex.value = -1
}

/**
 * 聚焦输入字段
 * @param {String} field - 字段名：weight 或 price
 */
const focusInput = (field) => {
	currentInputField.value = field
}

/**
 * 输入按键
 * @param {String} key - 按键值
 */
const inputKey = (key) => {
	const currentValue = currentInputField.value === 'weight' ? inputWeight.value : inputPrice.value

	// 限制小数点
	if (key === '.') {
		if (currentValue.includes('.')) {
			return // 已有小数点，不能再输入
		}
	}

	// 限制小数位数（最多2位）
	if (currentValue.includes('.')) {
		const decimalPart = currentValue.split('.')[1]
		if (decimalPart && decimalPart.length >= 2) {
			return // 小数位已达上限
		}
	}

	// 限制整数位数（最多6位）
	if (!currentValue.includes('.') && currentValue.length >= 6 && key !== '.') {
		return
	}

	// 拼接输入
	const newValue = currentValue + key

	// 更新值
	if (currentInputField.value === 'weight') {
		inputWeight.value = newValue
	} else {
		inputPrice.value = newValue
	}
}

/**
 * 删除按键
 */
const deleteKey = () => {
	const currentValue = currentInputField.value === 'weight' ? inputWeight.value : inputPrice.value
	const newValue = currentValue.slice(0, -1)

	if (currentInputField.value === 'weight') {
		inputWeight.value = newValue
	} else {
		inputPrice.value = newValue
	}
}

/**
 * 计算小计
 */
const calculateSubtotal = () => {
	const weight = parseFloat(inputWeight.value) || 0
	const price = parseFloat(inputPrice.value) || 0
	const subtotal = weight * price
	return subtotal.toFixed(2)
}

/**
 * 确认添加/修改商品
 */
const confirmProduct = () => {
	const weight = parseFloat(inputWeight.value)
	const priceFen = yuanToFen(inputPrice.value)

	// 校验
	if (!weight || weight <= 0) {
		showError('请输入有效的重量')
		return
	}

	if (!priceFen || priceFen <= 0) {
		showError('请输入有效的单价')
		return
	}

	// 校验库存（新增时校验，编辑时不校验）
	if (editingProductIndex.value === -1 && selectedInventory.value) {
		const availableStock = selectedInventory.value.current_stock_jin || 0
		if (weight > availableStock) {
			showError(`库存不足，当前库存 ${availableStock.toFixed(2)} 斤`)
			return
		}
	}

	// 计算小计
	const subtotalFen = Math.floor(weight * priceFen)

	// 估算成本（用于前端显示预估毛利）
	const estimatedCostFen = selectedInventory.value && selectedInventory.value.unit_cost_fen
		? Math.floor(weight * selectedInventory.value.unit_cost_fen)
		: 0

	// 构建订单项
	const orderItem = {
		inventory_id: selectedInventory.value._id,
		product_name: selectedInventory.value.product_name,
		spec: selectedInventory.value.spec || '',
		grade: selectedInventory.value.grade || '',
		weight_jin: weight,
		unit_price_fen: priceFen,
		subtotal_fen: subtotalFen,
		estimated_cost_fen: estimatedCostFen,
		stockWarning: '' // 库存警告信息
	}

	// 检查库存是否充足（相对于当前库存总量）
	if (selectedInventory.value) {
		const availableStock = selectedInventory.value.current_stock_jin || 0
		// 如果是编辑模式，需要加上原来的重量
		const compareWeight = editingProductIndex.value >= 0
			? weight - (orderItems.value[editingProductIndex.value].weight_jin || 0)
			: weight

		if (compareWeight > availableStock) {
			orderItem.stockWarning = `库存不足，当前库存 ${availableStock.toFixed(2)} 斤`
		}
	}

	// 添加或更新订单项
	if (editingProductIndex.value >= 0) {
		// 编辑模式
		orderItems.value[editingProductIndex.value] = orderItem
	} else {
		// 新增模式
		orderItems.value.push(orderItem)
	}

	// 关闭弹窗
	closeKeyboardPopup()
}

/**
 * 显示实收金额输入弹窗
 */
const showPaymentKeyboard = () => {
	paymentInputValue.value = paidAmountFen.value > 0 ? fenToYuan(paidAmountFen.value) : ''
	showPaymentInputPopup.value = true
}

/**
 * 关闭实收金额输入弹窗
 */
const closePaymentInputPopup = () => {
	showPaymentInputPopup.value = false
}

/**
 * 输入实收金额按键
 * @param {String} key - 按键值
 */
const inputPaymentKey = (key) => {
	const currentValue = paymentInputValue.value

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

	// 限制整数位数（最多6位）
	if (!currentValue.includes('.') && currentValue.length >= 6 && key !== '.') {
		return
	}

	paymentInputValue.value = currentValue + key
}

/**
 * 删除实收金额按键
 */
const deletePaymentKey = () => {
	paymentInputValue.value = paymentInputValue.value.slice(0, -1)
}

/**
 * 设置快捷金额
 * @param {String} type - 类型：all-全额收款，clear-清空
 */
const setQuickAmount = (type) => {
	if (type === 'all') {
		paymentInputValue.value = fenToYuan(totalAmountFen.value)
	} else if (type === 'clear') {
		paymentInputValue.value = ''
	}
}

/**
 * 确认实收金额
 */
const confirmPayment = () => {
	paidAmountFen.value = yuanToFen(paymentInputValue.value)
	closePaymentInputPopup()
}

/**
 * 提交订单
 */
const submitOrder = async () => {
	// 校验
	if (!canSubmit.value) {
		if (saleType.value === 'wholesale' && !selectedCustomer.value) {
			showError('批发模式必须选择客户')
		} else if (orderItems.value.length === 0) {
			showError('请添加商品')
		}
		return
	}

	try {
		showLoading('正在开单...')

		// 构建订单明细
		const orderItemsData = orderItems.value.map(item => ({
			inventory_id: item.inventory_id,
			weight_jin: item.weight_jin,
			unit_price_fen: item.unit_price_fen
		}))

		// 调用云函数
		const res = await uniCloud.callFunction({
			name: 'sales_order_create',
			data: {
				customer_id: selectedCustomer.value._id,
				sale_type: saleType.value,
				order_items: orderItemsData,
				paid_amount_fen: paidAmountFen.value
			}
		})

		hideLoading()

		if (res.result.code === 0) {
			// 开单成功
			showSuccess('开单成功')
			successOrderInfo.value = res.result.data
			showSuccessPage.value = true

			// 刷新客户列表（更新欠款余额）
			loadCustomers()
		} else {
			showError(res.result.message || '开单失败')
		}
	} catch (error) {
		hideLoading()
		showError('开单失败：' + error.message)
		console.error('开单失败：', error)
	}
}

/**
 * 继续开单
 */
const continueBilling = () => {
	// 重置表单
	orderItems.value = []
	paidAmountFen.value = 0
	showSuccessPage.value = false
	successOrderInfo.value = {}
	// 保留客户和销售类型，方便连续开单
}

/**
 * 查看订单
 */
const viewOrder = () => {
	// 跳转到订单详情页（这里简化处理，跳转到销售历史页）
	uni.navigateTo({
		url: '/pages/sales/history'
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时，预加载客户和库存数据（可选）
	// loadCustomers()
	// loadInventory()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.billing-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	padding-bottom: 500rpx; // 为底部结算区域留出空间
}

/* ==================== 顶部区域 ==================== */
.top-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

/* 销售类型切换 */
.sale-type-switch {
	display: flex;
	margin-bottom: 30rpx;
	border-radius: 12rpx;
	overflow: hidden;
	border: 2rpx solid #4A90E2;
}

.type-btn {
	flex: 1;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 32rpx;
	color: #4A90E2;
	background-color: #FFFFFF;
	transition: all 0.3s;

	&.active {
		background-color: #4A90E2;
		color: #FFFFFF;
	}
}

/* 客户选择器 */
.customer-selector {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 30rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	margin-bottom: 20rpx;
}

.customer-info {
	flex: 1;
}

.label {
	font-size: 28rpx;
	color: #999999;
	margin-bottom: 10rpx;
	display: block;
}

.customer-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
}

.customer-phone {
	font-size: 28rpx;
	color: #666666;
	margin-left: 10rpx;
}

.placeholder {
	font-size: 32rpx;
	color: #CCCCCC;
}

.arrow {
	font-size: 32rpx;
	color: #CCCCCC;
	margin-left: 20rpx;
}

/* 客户欠款余额 */
.customer-debt {
	display: flex;
	align-items: center;
	padding: 20rpx 30rpx;
	background-color: #FFF8E1;
	border-radius: 12rpx;
}

.debt-label {
	font-size: 28rpx;
	color: #666666;
}

.debt-amount {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;

	&.has-debt {
		color: #FF5722;
	}
}

/* ==================== 商品列表区域 ==================== */
.products-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.section-header {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;
}

.title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.count {
	font-size: 28rpx;
	color: #999999;
	margin-left: 10rpx;
}

/* 空状态 */
.empty-state {
	padding: 80rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* 商品列表 */
.product-list {
	//
}

.product-card {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.product-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.product-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	flex: 1;
}

.product-actions {
	display: flex;
	gap: 20rpx;
}

.action-btn {
	font-size: 28rpx;
	padding: 10rpx 20rpx;
	border-radius: 8rpx;

	&.edit {
		color: #4A90E2;
		background-color: #E3F2FD;
	}

	&.delete {
		color: #FF5722;
		background-color: #FFEBE9;
	}
}

.product-details {
	//
}

.detail-row {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.detail-label {
	font-size: 28rpx;
	color: #999999;
	width: 140rpx;
}

.detail-value {
	font-size: 28rpx;
	color: #333333;
	flex: 1;

	&.highlight {
		color: #4A90E2;
		font-weight: 500;
	}

	&.subtotal {
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}
}

/* 库存不足提示 */
.stock-warning {
	margin-top: 20rpx;
	padding: 15rpx 20rpx;
	background-color: #FFEBE9;
	border-radius: 8rpx;
}

.warning-text {
	font-size: 28rpx;
	color: #FF5722;
}

/* ==================== 添加商品按钮 ==================== */
.add-product-section {
	padding: 30rpx;
}

.add-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 100rpx;
	background-color: #FFFFFF;
	border: 2rpx dashed #4A90E2;
	border-radius: 12rpx;
}

.add-icon {
	font-size: 48rpx;
	color: #4A90E2;
	margin-right: 10rpx;
}

.add-text {
	font-size: 32rpx;
	color: #4A90E2;
}

/* ==================== 底部结算区域 ==================== */
.settlement-section {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	background-color: #FFFFFF;
	padding: 30rpx;
	box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);
	z-index: 100;
}

.settlement-info {
	margin-bottom: 20rpx;
}

.info-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
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

	&.highlight {
		color: #FF5722;
		font-weight: 600;
		font-size: 36rpx;
	}

	&.profit {
		color: #4CAF50;
		font-weight: 500;
	}
}

.payment-section {
	margin-bottom: 20rpx;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.payment-input {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;
}

.payment-label {
	font-size: 28rpx;
	color: #666666;
}

.payment-value {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	margin-left: 10rpx;

	&.has-input {
		color: #4A90E2;
	}
}

.payment-hint {
	font-size: 24rpx;
	color: #CCCCCC;
	margin-left: 10rpx;
}

.debt-display {
	display: flex;
	align-items: center;
}

.debt-label {
	font-size: 28rpx;
	color: #666666;
}

.debt-value {
	font-size: 28rpx;
	color: #333333;
	margin-left: 10rpx;

	&.has-debt {
		color: #FF5722;
		font-weight: 500;
	}
}

.submit-btn {
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	border-radius: 12rpx;

	&.disabled {
		background-color: #CCCCCC;
	}
}

.submit-text {
	font-size: 32rpx;
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
.customer-popup {
	//
}

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

.customer-main {
	display: flex;
	align-items: center;
	margin-bottom: 10rpx;
}

.customer-item-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
	flex: 1;
}

.customer-item-phone {
	font-size: 28rpx;
	color: #666666;
}

.customer-debt-info {
	//
}

.debt-text {
	font-size: 24rpx;
	color: #FF5722;
}

.empty-list {
	padding: 80rpx 0;
	text-align: center;
}

/* ==================== 库存选择弹窗 ==================== */
.inventory-popup {
	//
}

.inventory-list {
	max-height: 50vh;
}

.inventory-item {
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.inventory-main {
	margin-bottom: 15rpx;
}

.inventory-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
	display: block;
	margin-bottom: 10rpx;
}

.inventory-specs {
	display: flex;
	gap: 10rpx;
}

.spec-tag {
	font-size: 24rpx;
	color: #4A90E2;
	background-color: #E3F2FD;
	padding: 5rpx 15rpx;
	border-radius: 6rpx;
}

.inventory-stock {
	display: flex;
	align-items: center;
	margin-bottom: 10rpx;
}

.stock-label {
	font-size: 28rpx;
	color: #999999;
}

.stock-value {
	font-size: 28rpx;
	color: #333333;
	margin-left: 10rpx;

	&.low-stock {
		color: #FF5722;
	}
}

.inventory-price {
	display: flex;
	align-items: center;
}

.price-label {
	font-size: 28rpx;
	color: #999999;
}

.price-value {
	font-size: 28rpx;
	color: #4A90E2;
	margin-left: 10rpx;
}

/* ==================== 数字键盘弹窗 ==================== */
.keyboard-popup {
	//
}

.selected-product-info {
	padding: 30rpx;
	background-color: #F8F8F8;
}

.product-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	display: block;
	margin-bottom: 15rpx;
}

.stock-info {
	display: flex;
	align-items: center;
	margin-top: 15rpx;
}

.input-section {
	padding: 30rpx;
}

.input-row {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;
}

.input-label {
	font-size: 28rpx;
	color: #666666;
	width: 200rpx;
}

.input-box {
	flex: 1;
	height: 70rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	display: flex;
	align-items: center;
}

.input-value {
	font-size: 32rpx;
	color: #CCCCCC;

	&.has-value {
		color: #333333;
		font-weight: 500;
	}
}

.subtotal-row {
	margin-top: 20rpx;
	padding-top: 20rpx;
	border-top: 1rpx solid #EEEEEE;
}

.subtotal-value {
	font-size: 36rpx;
	color: #FF5722;
	font-weight: 600;
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
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #FFFFFF;
	border-radius: 12rpx;
	font-size: 36rpx;
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

.confirm-btn {
	margin: 30rpx;
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	border-radius: 12rpx;
}

.confirm-text {
	font-size: 32rpx;
	color: #FFFFFF;
	font-weight: 600;
}

/* ==================== 实收金额输入弹窗 ==================== */
.payment-popup {
	//
}

.payment-display {
	padding: 30rpx;
	text-align: center;
	background-color: #F8F8F8;
}

.payment-amount {
	font-size: 48rpx;
	color: #4A90E2;
	font-weight: 600;
	display: block;
	margin-top: 15rpx;
}

.quick-amount-btns {
	display: flex;
	gap: 20rpx;
	padding: 0 30rpx;
	margin-bottom: 20rpx;
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

/* ==================== 成功页面 ==================== */
.success-page {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: #FFFFFF;
	z-index: 400;
	display: flex;
	align-items: center;
	justify-content: center;
}

.success-content {
	width: 80%;
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
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
	display: block;
	margin-bottom: 40rpx;
}

.order-info {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 30rpx;
	margin-bottom: 40rpx;
}

.info-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
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
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}

	&.debt {
		color: #FF5722;
		font-weight: 500;
	}
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
	border-radius: 12rpx;

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
</style>
