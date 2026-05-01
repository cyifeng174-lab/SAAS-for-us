<template>
	<view class="purchase-container">
		<!-- ==================== 供应商选择区域 ==================== -->
		<view class="supplier-section">
			<view class="section-header">
				<text class="section-title">供应商信息</text>
			</view>

			<!-- 未选择供应商时显示选择按钮 -->
			<view class="select-supplier-btn" v-if="!selectedSupplier" @click="showSupplierPicker">
				<text class="btn-icon">+</text>
				<text class="btn-text">选择供应商</text>
			</view>

			<!-- 已选择供应商时显示供应商信息卡片 -->
			<view class="supplier-card" v-if="selectedSupplier">
				<view class="card-header">
					<text class="supplier-name">{{ selectedSupplier.name }}</text>
					<text class="change-btn" @click="showSupplierPicker">更换</text>
				</view>
				<view class="card-content">
					<view class="info-row">
						<text class="info-label">联系电话：</text>
						<text class="info-value">{{ selectedSupplier.phone || '未填写' }}</text>
					</view>
					<view class="info-row" v-if="selectedSupplier.origin">
						<text class="info-label">主要产地：</text>
						<text class="info-value">{{ selectedSupplier.origin }}</text>
					</view>
				</view>
			</view>

			<!-- 快速新增供应商按钮（预留） -->
			<view class="quick-add-btn" @click="quickAddSupplier" v-if="!selectedSupplier">
				<text class="quick-add-text">快速新增供应商</text>
			</view>
		</view>

		<!-- ==================== 采购信息录入区域 ==================== -->
		<view class="purchase-info-section">
			<view class="section-header">
				<text class="section-title">采购信息</text>
			</view>

			<!-- 原果名称 -->
			<view class="form-item">
				<text class="form-label">原果名称</text>
				<input
					class="form-input"
					v-model="purchaseForm.fruit_name"
					placeholder="请输入原果名称"
					maxlength="50"
				/>
			</view>

			<!-- 产地 -->
			<view class="form-item">
				<text class="form-label">产地</text>
				<input
					class="form-input"
					v-model="purchaseForm.origin"
					placeholder="请输入产地"
					maxlength="50"
				/>
			</view>

			<!-- 重量 -->
			<view class="form-item" @click="showWeightKeyboard">
				<text class="form-label">重量（斤）</text>
				<view class="form-input-box">
					<text class="form-value" :class="{ 'has-value': purchaseForm.weight_jin }">
						{{ purchaseForm.weight_jin || '点击输入' }}
					</text>
					<text class="form-unit">斤</text>
				</view>
			</view>

			<!-- 单价 -->
			<view class="form-item" @click="showPriceKeyboard">
				<text class="form-label">单价（元/斤）</text>
				<view class="form-input-box">
					<text class="form-value" :class="{ 'has-value': purchaseForm.unit_price }">
						{{ purchaseForm.unit_price || '点击输入' }}
					</text>
					<text class="form-unit">元/斤</text>
				</view>
			</view>

			<!-- 自动计算总金额 -->
			<view class="total-amount-row">
				<text class="total-label">总金额</text>
				<text class="total-value">¥{{ totalAmountYuan }}</text>
			</view>
		</view>

		<!-- ==================== 付款信息区域 ==================== -->
		<view class="payment-section">
			<view class="section-header">
				<text class="section-title">付款信息</text>
			</view>

			<!-- 付款状态选择 -->
			<view class="form-item">
				<text class="form-label">付款状态</text>
				<view class="status-options">
					<view
						class="status-option"
						v-for="status in paymentStatusOptions"
						:key="status.value"
						:class="{ active: purchaseForm.payment_status === status.value }"
						@click="selectPaymentStatus(status.value)"
					>
						<text class="status-text">{{ status.label }}</text>
					</view>
				</view>
			</view>

			<!-- 已付金额 -->
			<view class="form-item" @click="showPaidKeyboard" v-if="purchaseForm.payment_status !== 'unpaid'">
				<text class="form-label">已付金额（元）</text>
				<view class="form-input-box">
					<text class="form-value" :class="{ 'has-value': purchaseForm.paid_amount }">
						{{ purchaseForm.paid_amount || '点击输入' }}
					</text>
					<text class="form-unit">元</text>
				</view>
			</view>

			<!-- 欠款金额 -->
			<view class="debt-amount-row" v-if="purchaseForm.payment_status !== 'paid'">
				<text class="debt-label">欠款金额</text>
				<text class="debt-value">¥{{ debtAmountYuan }}</text>
			</view>
		</view>

		<!-- ==================== 底部提交区域（固定） ==================== -->
		<view class="submit-section">
			<view class="submit-info">
				<view class="info-column">
					<text class="info-label">总金额</text>
					<text class="info-value price">¥{{ totalAmountYuan }}</text>
				</view>
				<view class="info-column">
					<text class="info-label">已付金额</text>
					<text class="info-value">¥{{ paidAmountYuan }}</text>
				</view>
				<view class="info-column">
					<text class="info-label">欠款金额</text>
					<text class="info-value" :class="{ 'has-debt': debtAmountFen > 0 }">¥{{ debtAmountYuan }}</text>
				</view>
			</view>

			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="submitPurchase">
				<text class="submit-text">确认录入</text>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：选择供应商 ==================== -->
		<view class="popup-mask" v-if="showSupplierPopup" @click="closeSupplierPopup"></view>
		<view class="popup-container supplier-popup" :class="{ show: showSupplierPopup }">
			<view class="popup-header">
				<text class="popup-title">选择供应商</text>
				<text class="popup-close" @click="closeSupplierPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="supplierSearchKeyword"
						placeholder="搜索供应商名称或电话"
						@input="searchSuppliers"
					/>
				</view>

				<!-- 供应商列表 -->
				<scroll-view class="supplier-list" scroll-y>
					<view
						class="supplier-item"
						v-for="supplier in filteredSuppliers"
						:key="supplier._id"
						@click="selectSupplier(supplier)"
					>
						<view class="supplier-main">
							<text class="supplier-item-name">{{ supplier.name }}</text>
							<text class="supplier-phone" v-if="supplier.phone">{{ supplier.phone }}</text>
						</view>
						<view class="supplier-extra" v-if="supplier.origin">
							<text class="origin-tag">{{ supplier.origin }}</text>
						</view>
					</view>

					<view class="empty-list" v-if="filteredSuppliers.length === 0">
						<text class="empty-text">暂无供应商数据</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入重量 ==================== -->
		<view class="popup-mask" v-if="showWeightInputPopup" @click.stop></view>
		<view class="popup-container keyboard-popup" :class="{ show: showWeightInputPopup }">
			<view class="popup-header">
				<text class="popup-title">输入重量（斤）</text>
				<text class="popup-close" @click="closeWeightInputPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="input-display">
					<text class="display-value">{{ weightInputValue || '0.00' }} 斤</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputWeightKey('1')">1</view>
						<view class="key" @click="inputWeightKey('2')">2</view>
						<view class="key" @click="inputWeightKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputWeightKey('4')">4</view>
						<view class="key" @click="inputWeightKey('5')">5</view>
						<view class="key" @click="inputWeightKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputWeightKey('7')">7</view>
						<view class="key" @click="inputWeightKey('8')">8</view>
						<view class="key" @click="inputWeightKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputWeightKey('.')">.</view>
						<view class="key" @click="inputWeightKey('0')">0</view>
						<view class="key delete-key" @click="deleteWeightKey">删除</view>
					</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmWeightInput">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入单价 ==================== -->
		<view class="popup-mask" v-if="showPriceInputPopup" @click.stop></view>
		<view class="popup-container keyboard-popup" :class="{ show: showPriceInputPopup }">
			<view class="popup-header">
				<text class="popup-title">输入单价（元/斤）</text>
				<text class="popup-close" @click="closePriceInputPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="input-display">
					<text class="display-value">¥{{ priceInputValue || '0.00' }}/斤</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputPriceKey('1')">1</view>
						<view class="key" @click="inputPriceKey('2')">2</view>
						<view class="key" @click="inputPriceKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPriceKey('4')">4</view>
						<view class="key" @click="inputPriceKey('5')">5</view>
						<view class="key" @click="inputPriceKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPriceKey('7')">7</view>
						<view class="key" @click="inputPriceKey('8')">8</view>
						<view class="key" @click="inputPriceKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPriceKey('.')">.</view>
						<view class="key" @click="inputPriceKey('0')">0</view>
						<view class="key delete-key" @click="deletePriceKey">删除</view>
					</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmPriceInput">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入已付金额 ==================== -->
		<view class="popup-mask" v-if="showPaidInputPopup" @click.stop></view>
		<view class="popup-container keyboard-popup" :class="{ show: showPaidInputPopup }">
			<view class="popup-header">
				<text class="popup-title">输入已付金额（元）</text>
				<text class="popup-close" @click="closePaidInputPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="input-display">
					<text class="display-value">¥{{ paidInputValue || '0.00' }}</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputPaidKey('1')">1</view>
						<view class="key" @click="inputPaidKey('2')">2</view>
						<view class="key" @click="inputPaidKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaidKey('4')">4</view>
						<view class="key" @click="inputPaidKey('5')">5</view>
						<view class="key" @click="inputPaidKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaidKey('7')">7</view>
						<view class="key" @click="inputPaidKey('8')">8</view>
						<view class="key" @click="inputPaidKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputPaidKey('.')">.</view>
						<view class="key" @click="inputPaidKey('0')">0</view>
						<view class="key delete-key" @click="deletePaidKey">删除</view>
					</view>
				</view>

				<!-- 快捷操作 -->
				<view class="quick-btns">
					<view class="quick-btn" @click="setPaidFull">全额付款</view>
					<view class="quick-btn" @click="clearPaidInput">清空</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmPaidInput">
					<text class="confirm-text">确认</text>
				</view>
			</view>
		</view>

		<!-- ==================== 成功页面 ==================== -->
		<view class="success-page" v-if="showSuccessPage">
			<view class="success-content">
				<view class="success-icon">✓</view>
				<text class="success-title">采购录入成功</text>

				<view class="order-info">
					<view class="info-item">
						<text class="info-label">采购单号</text>
						<text class="info-value highlight">{{ successInfo.batch_no }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">供应商</text>
						<text class="info-value">{{ successInfo.supplier_name }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">原果名称</text>
						<text class="info-value">{{ successInfo.fruit_name }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">采购重量</text>
						<text class="info-value">{{ successInfo.weight_jin }} 斤</text>
					</view>
					<view class="info-item">
						<text class="info-label">总金额</text>
						<text class="info-value price">¥{{ fenToYuan(successInfo.total_amount_fen) }}</text>
					</view>
					<view class="info-item" v-if="successInfo.debt_amount_fen > 0">
						<text class="info-label">欠款金额</text>
						<text class="info-value warning">¥{{ fenToYuan(successInfo.debt_amount_fen) }}</text>
					</view>
				</view>

				<view class="success-actions">
					<view class="action-btn primary" @click="continuePurchase">
						<text class="btn-text">继续采购</text>
					</view>
					<view class="action-btn secondary" @click="goToProcessing">
						<text class="btn-text">去加工</text>
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

// 供应商相关
const selectedSupplier = ref(null) // 当前选中的供应商
const supplierList = ref([]) // 供应商列表
const supplierSearchKeyword = ref('') // 供应商搜索关键词
const showSupplierPopup = ref(false) // 是否显示供应商选择弹窗

// 采购表单
const purchaseForm = ref({
	fruit_name: '',       // 原果名称
	origin: '',           // 产地
	weight_jin: '',       // 重量（斤）
	unit_price: '',       // 单价（元/斤）
	payment_status: 'unpaid', // 付款状态
	paid_amount: ''       // 已付金额（元）
})

// 重量输入相关
const showWeightInputPopup = ref(false) // 是否显示重量输入弹窗
const weightInputValue = ref('') // 重量输入值

// 单价输入相关
const showPriceInputPopup = ref(false) // 是否显示单价输入弹窗
const priceInputValue = ref('') // 单价输入值

// 已付金额输入相关
const showPaidInputPopup = ref(false) // 是否显示已付金额输入弹窗
const paidInputValue = ref('') // 已付金额输入值

// 成功页面
const showSuccessPage = ref(false) // 是否显示成功页面
const successInfo = ref({}) // 成功信息

// 付款状态选项
const paymentStatusOptions = [
	{ value: 'unpaid', label: '未付' },
	{ value: 'partial', label: '部分' },
	{ value: 'paid', label: '已付' }
]

// ==================== 计算属性 ====================

// 过滤后的供应商列表
const filteredSuppliers = computed(() => {
	if (!supplierSearchKeyword.value) {
		return supplierList.value
	}
	const keyword = supplierSearchKeyword.value.toLowerCase()
	return supplierList.value.filter(supplier => {
		return (
			(supplier.name && supplier.name.toLowerCase().includes(keyword)) ||
			(supplier.phone && supplier.phone.includes(keyword)) ||
			(supplier.origin && supplier.origin.toLowerCase().includes(keyword))
		)
	})
})

// 总金额（分）
const totalAmountFen = computed(() => {
	const weight = parseFloat(purchaseForm.value.weight_jin) || 0
	const price = parseFloat(purchaseForm.value.unit_price) || 0
	return yuanToFen(weight * price)
})

// 总金额（元）
const totalAmountYuan = computed(() => {
	return fenToYuan(totalAmountFen.value)
})

// 已付金额（分）
const paidAmountFen = computed(() => {
	return yuanToFen(purchaseForm.value.paid_amount)
})

// 已付金额（元）
const paidAmountYuan = computed(() => {
	return fenToYuan(paidAmountFen.value)
})

// 欠款金额（分）
const debtAmountFen = computed(() => {
	return Math.max(0, totalAmountFen.value - paidAmountFen.value)
})

// 欠款金额（元）
const debtAmountYuan = computed(() => {
	return fenToYuan(debtAmountFen.value)
})

// 是否可以提交
const canSubmit = computed(() => {
	// 必须选择供应商
	if (!selectedSupplier.value) return false
	// 必须填写原果名称
	if (!purchaseForm.value.fruit_name) return false
	// 必须填写重量
	if (!purchaseForm.value.weight_jin || parseFloat(purchaseForm.value.weight_jin) <= 0) return false
	// 必须填写单价
	if (!purchaseForm.value.unit_price || parseFloat(purchaseForm.value.unit_price) <= 0) return false
	// 已付金额不能超过总金额
	if (paidAmountFen.value > totalAmountFen.value) return false
	return true
})

// ==================== 方法 ====================

/**
 * 显示供应商选择弹窗
 */
const showSupplierPicker = () => {
	showSupplierPopup.value = true
	loadSuppliers()
}

/**
 * 关闭供应商选择弹窗
 */
const closeSupplierPopup = () => {
	showSupplierPopup.value = false
	supplierSearchKeyword.value = ''
}

/**
 * 加载供应商列表
 */
const loadSuppliers = async () => {
	try {
		showLoading('加载供应商...')
		const db = uniCloud.database()
		const res = await db.collection('suppliers')
			.where({
				status: 'active' // 只查询正常状态的供应商
			})
			.orderBy('last_purchase_time', 'desc')
			.limit(50)
			.get()

		supplierList.value = res.data || []
		hideLoading()
	} catch (error) {
		hideLoading()
		showError('加载供应商失败：' + error.message)
		console.error('加载供应商失败：', error)
	}
}

/**
 * 搜索供应商（防抖处理）
 */
let supplierSearchTimer = null
const searchSuppliers = () => {
	if (supplierSearchTimer) clearTimeout(supplierSearchTimer)
	supplierSearchTimer = setTimeout(() => {
		// 计算属性会自动过滤，这里不需要额外处理
	}, 300)
}

/**
 * 选择供应商
 * @param {Object} supplier - 供应商对象
 */
const selectSupplier = (supplier) => {
	selectedSupplier.value = supplier
	// 如果供应商有主要产地，自动填充产地
	if (supplier.origin && !purchaseForm.value.origin) {
		purchaseForm.value.origin = supplier.origin
	}
	closeSupplierPopup()
}

/**
 * 快速新增供应商（预留功能）
 */
const quickAddSupplier = () => {
	showError('快速新增供应商功能开发中，请先在供应商管理中添加')
}

/**
 * 选择付款状态
 * @param {String} status - 付款状态
 */
const selectPaymentStatus = (status) => {
	purchaseForm.value.payment_status = status
	
	// 根据付款状态自动设置已付金额
	if (status === 'unpaid') {
		purchaseForm.value.paid_amount = ''
	} else if (status === 'paid') {
		// 已付状态，自动填充总金额
		purchaseForm.value.paid_amount = totalAmountYuan.value
	}
}

/**
 * 显示重量输入弹窗
 */
const showWeightKeyboard = () => {
	weightInputValue.value = purchaseForm.value.weight_jin ? String(purchaseForm.value.weight_jin) : ''
	showWeightInputPopup.value = true
}

/**
 * 关闭重量输入弹窗
 */
const closeWeightInputPopup = () => {
	showWeightInputPopup.value = false
}

/**
 * 输入重量按键
 * @param {String} key - 按键值
 */
const inputWeightKey = (key) => {
	const currentValue = weightInputValue.value

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

	weightInputValue.value = currentValue + key
}

/**
 * 删除重量按键
 */
const deleteWeightKey = () => {
	weightInputValue.value = weightInputValue.value.slice(0, -1)
}

/**
 * 确认重量输入
 */
const confirmWeightInput = () => {
	purchaseForm.value.weight_jin = weightInputValue.value
	closeWeightInputPopup()
}

/**
 * 显示单价输入弹窗
 */
const showPriceKeyboard = () => {
	priceInputValue.value = purchaseForm.value.unit_price ? String(purchaseForm.value.unit_price) : ''
	showPriceInputPopup.value = true
}

/**
 * 关闭单价输入弹窗
 */
const closePriceInputPopup = () => {
	showPriceInputPopup.value = false
}

/**
 * 输入单价按键
 * @param {String} key - 按键值
 */
const inputPriceKey = (key) => {
	const currentValue = priceInputValue.value

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

	// 限制整数位数（最多4位）
	if (!currentValue.includes('.') && currentValue.length >= 4 && key !== '.') {
		return
	}

	priceInputValue.value = currentValue + key
}

/**
 * 删除单价按键
 */
const deletePriceKey = () => {
	priceInputValue.value = priceInputValue.value.slice(0, -1)
}

/**
 * 确认单价输入
 */
const confirmPriceInput = () => {
	purchaseForm.value.unit_price = priceInputValue.value
	closePriceInputPopup()
}

/**
 * 显示已付金额输入弹窗
 */
const showPaidKeyboard = () => {
	paidInputValue.value = purchaseForm.value.paid_amount ? String(purchaseForm.value.paid_amount) : ''
	showPaidInputPopup.value = true
}

/**
 * 关闭已付金额输入弹窗
 */
const closePaidInputPopup = () => {
	showPaidInputPopup.value = false
}

/**
 * 输入已付金额按键
 * @param {String} key - 按键值
 */
const inputPaidKey = (key) => {
	const currentValue = paidInputValue.value

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

	paidInputValue.value = currentValue + key
}

/**
 * 删除已付金额按键
 */
const deletePaidKey = () => {
	paidInputValue.value = paidInputValue.value.slice(0, -1)
}

/**
 * 清空已付金额输入
 */
const clearPaidInput = () => {
	paidInputValue.value = ''
}

/**
 * 设置全额付款
 */
const setPaidFull = () => {
	paidInputValue.value = totalAmountYuan.value
}

/**
 * 确认已付金额输入
 */
const confirmPaidInput = () => {
	// 校验已付金额不能超过总金额
	const paidFen = yuanToFen(paidInputValue.value)
	if (paidFen > totalAmountFen.value) {
		showError('已付金额不能超过总金额')
		return
	}

	purchaseForm.value.paid_amount = paidInputValue.value

	// 自动更新付款状态
	if (paidFen >= totalAmountFen.value && totalAmountFen.value > 0) {
		purchaseForm.value.payment_status = 'paid'
	} else if (paidFen > 0) {
		purchaseForm.value.payment_status = 'partial'
	} else {
		purchaseForm.value.payment_status = 'unpaid'
	}

	closePaidInputPopup()
}

/**
 * 提交采购单
 */
const submitPurchase = async () => {
	// 校验
	if (!canSubmit.value) {
		if (!selectedSupplier.value) {
			showError('请选择供应商')
		} else if (!purchaseForm.value.fruit_name) {
			showError('请输入原果名称')
		} else if (!purchaseForm.value.weight_jin || parseFloat(purchaseForm.value.weight_jin) <= 0) {
			showError('请输入有效的重量')
		} else if (!purchaseForm.value.unit_price || parseFloat(purchaseForm.value.unit_price) <= 0) {
			showError('请输入有效的单价')
		} else if (paidAmountFen.value > totalAmountFen.value) {
			showError('已付金额不能超过总金额')
		}
		return
	}

	try {
		showLoading('正在提交...')

		// 构建请求参数
		const requestData = {
			supplier_id: selectedSupplier.value._id,
			supplier_name: selectedSupplier.value.name,
			fruit_name: purchaseForm.value.fruit_name,
			origin: purchaseForm.value.origin || '',
			weight_jin: parseFloat(purchaseForm.value.weight_jin),
			unit_price_fen: yuanToFen(purchaseForm.value.unit_price),
			total_amount_fen: totalAmountFen.value,
			payment_status: purchaseForm.value.payment_status,
			paid_amount_fen: paidAmountFen.value,
			debt_amount_fen: debtAmountFen.value
		}

		// 调用云函数
		const res = await uniCloud.callFunction({
			name: 'purchase_create',
			data: requestData
		})

		hideLoading()

		if (res.result.code === 0) {
			// 提交成功
			showSuccess('采购录入成功')
			successInfo.value = res.result.data
			showSuccessPage.value = true
		} else {
			showError(res.result.message || '采购录入失败')
		}
	} catch (error) {
		hideLoading()
		showError('采购录入失败：' + error.message)
		console.error('采购录入失败：', error)
	}
}

/**
 * 继续采购
 */
const continuePurchase = () => {
	// 重置表单
	selectedSupplier.value = null
	purchaseForm.value = {
		fruit_name: '',
		origin: '',
		weight_jin: '',
		unit_price: '',
		payment_status: 'unpaid',
		paid_amount: ''
	}
	showSuccessPage.value = false
	successInfo.value = {}
}

/**
 * 去加工
 */
const goToProcessing = () => {
	// 跳转到加工入库页面
	uni.navigateTo({
		url: '/pages/processing/create'
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时可以预加载供应商列表（可选）
	// loadSuppliers()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.purchase-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	padding-bottom: 280rpx; // 为底部提交区域留出空间
}

/* ==================== 通用区域样式 ==================== */
.section-header {
	padding: 30rpx 30rpx 20rpx;
}

.section-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

/* ==================== 供应商选择区域 ==================== */
.supplier-section {
	background-color: #FFFFFF;
	margin-bottom: 20rpx;
}

.select-supplier-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 120rpx;
	margin: 0 30rpx 20rpx;
	background-color: #F8F8F8;
	border: 2rpx dashed #4A90E2;
	border-radius: 12rpx;
}

.btn-icon {
	font-size: 48rpx;
	color: #4A90E2;
	margin-right: 10rpx;
}

.btn-text {
	font-size: 32rpx;
	color: #4A90E2;
}

.supplier-card {
	margin: 0 30rpx 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	overflow: hidden;
}

.card-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 25rpx 30rpx;
	background-color: #E3F2FD;
}

.supplier-name {
	font-size: 34rpx;
	color: #333333;
	font-weight: 600;
}

.change-btn {
	font-size: 28rpx;
	color: #4A90E2;
	padding: 10rpx 20rpx;
	background-color: #FFFFFF;
	border-radius: 8rpx;
}

.card-content {
	padding: 20rpx 30rpx;
}

.info-row {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.info-label {
	font-size: 28rpx;
	color: #666666;
	width: 160rpx;
}

.info-value {
	font-size: 28rpx;
	color: #333333;
	flex: 1;
}

.quick-add-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 80rpx;
	margin: 0 30rpx 30rpx;
	background-color: #FFF8E1;
	border-radius: 12rpx;
}

.quick-add-text {
	font-size: 28rpx;
	color: #FF9800;
}

/* ==================== 采购信息录入区域 ==================== */
.purchase-info-section {
	background-color: #FFFFFF;
	margin-bottom: 20rpx;
}

.form-item {
	padding: 25rpx 30rpx;
	border-bottom: 1rpx solid #EEEEEE;

	&:last-child {
		border-bottom: none;
	}
}

.form-label {
	font-size: 28rpx;
	color: #666666;
	display: block;
	margin-bottom: 15rpx;
}

.form-input {
	width: 100%;
	height: 80rpx;
	padding: 0 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 30rpx;
}

.form-input-box {
	width: 100%;
	height: 80rpx;
	padding: 0 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.form-value {
	font-size: 30rpx;
	color: #CCCCCC;

	&.has-value {
		color: #333333;
		font-weight: 500;
	}
}

.form-unit {
	font-size: 26rpx;
	color: #999999;
}

.total-amount-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 25rpx 30rpx;
	background-color: #FFF8E1;
	margin: 0 30rpx 30rpx;
	border-radius: 12rpx;
}

.total-label {
	font-size: 30rpx;
	color: #666666;
}

.total-value {
	font-size: 40rpx;
	color: #FF5722;
	font-weight: 600;
}

/* ==================== 付款信息区域 ==================== */
.payment-section {
	background-color: #FFFFFF;
	margin-bottom: 20rpx;
}

.status-options {
	display: flex;
	gap: 20rpx;
}

.status-option {
	flex: 1;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	border: 2rpx solid transparent;

	&.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}
}

.status-text {
	font-size: 30rpx;
	color: #333333;

	.status-option.active & {
		color: #4A90E2;
		font-weight: 500;
	}
}

.debt-amount-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 25rpx 30rpx;
	background-color: #FFEBE9;
	margin: 0 30rpx 30rpx;
	border-radius: 12rpx;
}

.debt-label {
	font-size: 30rpx;
	color: #666666;
}

.debt-value {
	font-size: 36rpx;
	color: #FF5722;
	font-weight: 600;
}

/* ==================== 底部提交区域 ==================== */
.submit-section {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	background-color: #FFFFFF;
	padding: 25rpx 30rpx;
	box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);
	z-index: 100;
}

.submit-info {
	display: flex;
	justify-content: space-around;
	margin-bottom: 20rpx;
}

.info-column {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.info-column .info-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 8rpx;
	width: auto;
}

.info-column .info-value {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;

	&.price {
		color: #FF5722;
	}

	&.has-debt {
		color: #FF5722;
	}
}

.submit-btn {
	height: 100rpx;
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
	font-size: 34rpx;
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

/* ==================== 供应商选择弹窗 ==================== */
.search-box {
	padding: 20rpx 30rpx;
}

.search-input {
	width: 100%;
	height: 80rpx;
	padding: 0 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 30rpx;
}

.supplier-list {
	max-height: 50vh;
}

.supplier-item {
	padding: 25rpx 30rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.supplier-main {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;
}

.supplier-item-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.supplier-phone {
	font-size: 28rpx;
	color: #4A90E2;
}

.supplier-extra {
	display: flex;
	align-items: center;
}

.origin-tag {
	font-size: 24rpx;
	color: #999999;
	background-color: #F8F8F8;
	padding: 5rpx 15rpx;
	border-radius: 6rpx;
}

.empty-list {
	padding: 80rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* ==================== 数字键盘弹窗 ==================== */
.input-display {
	padding: 40rpx 30rpx;
	text-align: center;
	background-color: #F8F8F8;
}

.display-value {
	font-size: 48rpx;
	color: #333333;
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

.quick-btns {
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
	width: 85%;
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
	padding: 25rpx 30rpx;
	margin-bottom: 40rpx;
}

.order-info .info-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.order-info .info-label {
	font-size: 26rpx;
	color: #666666;
	width: auto;
}

.order-info .info-value {
	font-size: 26rpx;
	color: #333333;

	&.highlight {
		color: #4A90E2;
		font-weight: 500;
	}

	&.price {
		color: #FF5722;
		font-weight: 600;
	}

	&.warning {
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
