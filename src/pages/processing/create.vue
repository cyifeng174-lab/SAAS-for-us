<template>
	<view class="processing-container">
		<!-- ==================== 顶部区域：选择采购单 ==================== -->
		<view class="top-section">
			<!-- 选择采购单按钮 -->
			<view class="select-purchase-btn" @click="showPurchasePicker" v-if="!selectedPurchase">
				<text class="btn-icon">+</text>
				<text class="btn-text">选择采购单</text>
			</view>

			<!-- 原果信息卡片 -->
			<view class="purchase-card" v-if="selectedPurchase">
				<view class="card-header">
					<text class="card-title">原果信息</text>
					<text class="change-btn" @click="showPurchasePicker">更换</text>
				</view>
				
				<view class="card-content">
					<view class="info-row">
						<text class="info-label">原果名称：</text>
						<text class="info-value highlight">{{ selectedPurchase.fruit_name }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">产地：</text>
						<text class="info-value">{{ selectedPurchase.origin || '-' }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">采购重量：</text>
						<text class="info-value highlight">{{ selectedPurchase.weight_jin }} 斤</text>
					</view>
					<view class="info-row">
						<text class="info-label">采购成本：</text>
						<text class="info-value price">¥{{ fenToYuan(selectedPurchase.total_amount_fen) }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">供应商：</text>
						<text class="info-value">{{ selectedPurchase.supplier_name || '-' }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 加工费用区域 ==================== -->
		<view class="cost-section" v-if="selectedPurchase">
			<view class="section-header">
				<text class="section-title">加工费用</text>
			</view>

			<view class="cost-list">
				<!-- 人工费 -->
				<view class="cost-item" @click="showCostKeyboard('labor')">
					<text class="cost-label">人工费</text>
					<view class="cost-input-box">
						<text class="cost-value" :class="{ 'has-value': costs.labor_cost_fen > 0 }">
							¥{{ fenToYuan(costs.labor_cost_fen) }}
						</text>
						<text class="cost-unit">元</text>
					</view>
				</view>

				<!-- 包装费 -->
				<view class="cost-item" @click="showCostKeyboard('packaging')">
					<text class="cost-label">包装费</text>
					<view class="cost-input-box">
						<text class="cost-value" :class="{ 'has-value': costs.packaging_cost_fen > 0 }">
							¥{{ fenToYuan(costs.packaging_cost_fen) }}
						</text>
						<text class="cost-unit">元</text>
					</view>
				</view>

				<!-- 运费 -->
				<view class="cost-item" @click="showCostKeyboard('transport')">
					<text class="cost-label">运费</text>
					<view class="cost-input-box">
						<text class="cost-value" :class="{ 'has-value': costs.transport_cost_fen > 0 }">
							¥{{ fenToYuan(costs.transport_cost_fen) }}
						</text>
						<text class="cost-unit">元</text>
					</view>
				</view>

				<!-- 其他费用 -->
				<view class="cost-item" @click="showCostKeyboard('other')">
					<text class="cost-label">其他费用</text>
					<view class="cost-input-box">
						<text class="cost-value" :class="{ 'has-value': costs.other_cost_fen > 0 }">
							¥{{ fenToYuan(costs.other_cost_fen) }}
						</text>
						<text class="cost-unit">元</text>
					</view>
				</view>
			</view>

			<!-- 加工总成本 -->
			<view class="total-cost-row">
				<text class="total-label">加工总成本</text>
				<text class="total-value">¥{{ fenToYuan(totalProcessingCostFen) }}</text>
			</view>
		</view>

		<!-- ==================== 产出成品区域 ==================== -->
		<view class="output-section" v-if="selectedPurchase">
			<view class="section-header">
				<text class="section-title">产出成品</text>
				<text class="count-badge" v-if="outputProducts.length > 0">{{ outputProducts.length }}</text>
			</view>

			<!-- 空状态 -->
			<view class="empty-state" v-if="outputProducts.length === 0">
				<text class="empty-text">请添加加工产出的成品</text>
			</view>

			<!-- 成品列表 -->
			<view class="product-list" v-else>
				<view class="product-card" v-for="(item, index) in outputProducts" :key="index">
					<view class="product-header">
						<text class="product-name">{{ item.product_name || '未命名成品' }}</text>
						<text class="delete-btn" @click="deleteProduct(index)">删除</text>
					</view>

					<view class="product-info">
						<view class="info-item">
							<text class="item-label">规格：</text>
							<text class="item-value">{{ item.spec || '-' }}</text>
						</view>
						<view class="info-item">
							<text class="item-label">等级：</text>
							<text class="item-value">{{ item.grade || '-' }}</text>
						</view>
						<view class="info-item">
							<text class="item-label">重量：</text>
							<text class="item-value highlight">{{ item.weight_jin }} 斤</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 添加成品按钮 -->
			<view class="add-product-btn" @click="showAddProductPopup">
				<text class="add-icon">+</text>
				<text class="add-text">添加成品</text>
			</view>

			<!-- 统计信息 -->
			<view class="stats-card" v-if="outputProducts.length > 0">
				<view class="stat-row">
					<text class="stat-label">总产出重量</text>
					<text class="stat-value">{{ totalOutputWeightJin.toFixed(2) }} 斤</text>
				</view>
				<view class="stat-row">
					<text class="stat-label">损耗重量</text>
					<text class="stat-value" :class="{ 'has-loss': lossWeightJin > 0 }">
						{{ lossWeightJin.toFixed(2) }} 斤
					</text>
				</view>
				<view class="stat-row">
					<text class="stat-label">损耗率</text>
					<text class="stat-value" :class="{ 'has-loss': lossRate > 5 }">
						{{ lossRate.toFixed(2) }}%
					</text>
				</view>
			</view>
		</view>

		<!-- ==================== 底部提交区域（固定） ==================== -->
		<view class="submit-section" v-if="selectedPurchase">
			<view class="submit-info">
				<view class="info-row">
					<text class="info-label">总成本</text>
					<text class="info-value price">¥{{ fenToYuan(totalCostFen) }}</text>
				</view>
				<view class="info-row">
					<text class="info-label">总产出重量</text>
					<text class="info-value">{{ totalOutputWeightJin.toFixed(2) }} 斤</text>
				</view>
			</view>

			<view class="submit-actions">
				<!-- 预览成本分摊按钮 -->
				<view class="preview-btn" @click="showCostAllocationPopup">
					<text class="preview-text">预览成本分摊</text>
				</view>

				<!-- 确认入库按钮 -->
				<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="submitProcessing">
					<text class="submit-text">确认入库</text>
				</view>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：选择采购单 ==================== -->
		<view class="popup-mask" v-if="showPurchasePopup" @click="closePurchasePopup"></view>
		<view class="popup-container purchase-popup" :class="{ show: showPurchasePopup }">
			<view class="popup-header">
				<text class="popup-title">选择采购单</text>
				<text class="popup-close" @click="closePurchasePopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="purchaseSearchKeyword"
						placeholder="搜索原果名称或供应商"
						@input="searchPurchases"
					/>
				</view>

				<!-- 采购单列表 -->
				<scroll-view class="purchase-list" scroll-y>
					<view
						class="purchase-item"
						v-for="purchase in filteredPurchases"
						:key="purchase._id"
						@click="selectPurchase(purchase)"
					>
						<view class="purchase-main">
							<text class="purchase-name">{{ purchase.fruit_name }}</text>
							<text class="purchase-origin" v-if="purchase.origin">{{ purchase.origin }}</text>
						</view>
						<view class="purchase-details">
							<view class="detail-item">
								<text class="detail-label">重量：</text>
								<text class="detail-value">{{ purchase.weight_jin }} 斤</text>
							</view>
							<view class="detail-item">
								<text class="detail-label">成本：</text>
								<text class="detail-value price">¥{{ fenToYuan(purchase.total_amount_fen) }}</text>
							</view>
						</view>
						<view class="purchase-supplier" v-if="purchase.supplier_name">
							<text class="supplier-label">供应商：</text>
							<text class="supplier-value">{{ purchase.supplier_name }}</text>
						</view>
					</view>

					<view class="empty-list" v-if="filteredPurchases.length === 0">
						<text class="empty-text">暂无待加工的采购单</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：添加成品 ==================== -->
		<view class="popup-mask" v-if="showProductPopup" @click.stop></view>
		<view class="popup-container product-popup" :class="{ show: showProductPopup }">
			<view class="popup-header">
				<text class="popup-title">添加成品</text>
				<text class="popup-close" @click="closeProductPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 成品名称 -->
				<view class="form-item">
					<text class="form-label">成品名称</text>
					<input
						class="form-input"
						v-model="productForm.product_name"
						placeholder="如：80果一级"
						maxlength="50"
					/>
				</view>

				<!-- 规格输入 -->
				<view class="form-item">
					<text class="form-label">规格</text>
					<input
						class="form-input"
						v-model="productForm.spec"
						placeholder="如：80果、90果"
						maxlength="20"
					/>
				</view>

				<!-- 等级选择 -->
				<view class="form-item">
					<text class="form-label">等级</text>
					<view class="grade-options">
						<view
							class="grade-option"
							v-for="grade in gradeOptions"
							:key="grade"
							:class="{ active: productForm.grade === grade }"
							@click="selectGrade(grade)"
						>
							<text class="grade-text">{{ grade }}</text>
						</view>
					</view>
				</view>

				<!-- 重量输入 -->
				<view class="form-item" @click="showWeightKeyboard">
					<text class="form-label">重量（斤）</text>
					<view class="form-input-box">
						<text class="form-value" :class="{ 'has-value': productForm.weight_jin }">
							{{ productForm.weight_jin || '点击输入' }}
						</text>
					</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmAddProduct">
					<text class="confirm-text">确认添加</text>
				</view>
			</view>
		</view>

		<!-- ==================== 数字键盘弹窗：输入费用 ==================== -->
		<view class="popup-mask" v-if="showCostInputPopup" @click.stop></view>
		<view class="popup-container keyboard-popup" :class="{ show: showCostInputPopup }">
			<view class="popup-header">
				<text class="popup-title">{{ currentCostLabel }}</text>
				<text class="popup-close" @click="closeCostInputPopup">×</text>
			</view>

			<view class="popup-content">
				<view class="input-display">
					<text class="display-value">¥{{ costInputValue || '0.00' }}</text>
				</view>

				<!-- 自定义数字键盘 -->
				<view class="custom-keyboard">
					<view class="keyboard-row">
						<view class="key" @click="inputCostKey('1')">1</view>
						<view class="key" @click="inputCostKey('2')">2</view>
						<view class="key" @click="inputCostKey('3')">3</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputCostKey('4')">4</view>
						<view class="key" @click="inputCostKey('5')">5</view>
						<view class="key" @click="inputCostKey('6')">6</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputCostKey('7')">7</view>
						<view class="key" @click="inputCostKey('8')">8</view>
						<view class="key" @click="inputCostKey('9')">9</view>
					</view>
					<view class="keyboard-row">
						<view class="key" @click="inputCostKey('.')">.</view>
						<view class="key" @click="inputCostKey('0')">0</view>
						<view class="key delete-key" @click="deleteCostKey">删除</view>
					</view>
				</view>

				<!-- 快捷操作 -->
				<view class="quick-btns">
					<view class="quick-btn" @click="clearCostInput">清空</view>
				</view>

				<!-- 确认按钮 -->
				<view class="confirm-btn" @click="confirmCostInput">
					<text class="confirm-text">确认</text>
				</view>
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

		<!-- ==================== 半屏弹窗：成本分摊预览 ==================== -->
		<view class="popup-mask" v-if="showAllocationPopup" @click="closeAllocationPopup"></view>
		<view class="popup-container allocation-popup" :class="{ show: showAllocationPopup }">
			<view class="popup-header">
				<text class="popup-title">成本分摊预览</text>
				<text class="popup-close" @click="closeAllocationPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 总成本信息 -->
				<view class="allocation-summary">
					<view class="summary-row">
						<text class="summary-label">采购成本</text>
						<text class="summary-value">¥{{ fenToYuan(purchaseCostFen) }}</text>
					</view>
					<view class="summary-row">
						<text class="summary-label">加工费用</text>
						<text class="summary-value">¥{{ fenToYuan(totalProcessingCostFen) }}</text>
					</view>
					<view class="summary-row total">
						<text class="summary-label">总成本</text>
						<text class="summary-value price">¥{{ fenToYuan(totalCostFen) }}</text>
					</view>
				</view>

				<!-- 分摊明细 -->
				<view class="allocation-list" v-if="allocationList.length > 0">
					<view class="allocation-item" v-for="(item, index) in allocationList" :key="index">
						<view class="allocation-header">
							<text class="allocation-name">{{ item.product_name }}</text>
							<text class="allocation-weight">{{ item.weight_jin }} 斤</text>
						</view>
						<view class="allocation-details">
							<view class="detail-row">
								<text class="detail-label">重量占比</text>
								<text class="detail-value">{{ (item.weight_ratio * 100).toFixed(2) }}%</text>
							</view>
							<view class="detail-row">
								<text class="detail-label">分摊成本</text>
								<text class="detail-value price">¥{{ fenToYuan(item.allocated_cost_fen) }}</text>
							</view>
							<view class="detail-row">
								<text class="detail-label">单位成本</text>
								<text class="detail-value price">¥{{ fenToYuan(item.unit_cost_fen) }}/斤</text>
							</view>
						</view>
					</view>
				</view>

				<view class="empty-list" v-else>
					<text class="empty-text">请先添加产出成品</text>
				</view>
			</view>
		</view>

		<!-- ==================== 成功页面 ==================== -->
		<view class="success-page" v-if="showSuccessPage">
			<view class="success-content">
				<view class="success-icon">✓</view>
				<text class="success-title">入库成功</text>

				<view class="order-info">
					<view class="info-item">
						<text class="info-label">加工单号</text>
						<text class="info-value">{{ successInfo.order_no }}</text>
					</view>
					<view class="info-item">
						<text class="info-label">总产出重量</text>
						<text class="info-value">{{ successInfo.total_output_weight_jin?.toFixed(2) }} 斤</text>
					</view>
					<view class="info-item">
						<text class="info-label">损耗率</text>
						<text class="info-value">{{ successInfo.loss_rate?.toFixed(2) }}%</text>
					</view>
				</view>

				<!-- 各成品单位成本 -->
				<view class="product-cost-list" v-if="successInfo.output_products?.length > 0">
					<view class="list-title">各成品单位成本</view>
					<view class="cost-item" v-for="(item, index) in successInfo.output_products" :key="index">
						<text class="cost-name">{{ item.product_name }}</text>
						<text class="cost-value">¥{{ fenToYuan(item.unit_cost_fen) }}/斤</text>
					</view>
				</view>

				<view class="success-actions">
					<view class="action-btn primary" @click="continueProcessing">
						<text class="btn-text">继续加工</text>
					</view>
					<view class="action-btn secondary" @click="viewInventory">
						<text class="btn-text">查看库存</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, yuanToFen, showLoading, hideLoading, showSuccess, showError } from '@/utils/util.js'
import { processingAPI, purchaseAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 采购单相关
const selectedPurchase = ref(null) // 当前选中的采购单
const purchaseList = ref([]) // 采购单列表
const purchaseSearchKeyword = ref('') // 采购单搜索关键词
const showPurchasePopup = ref(false) // 是否显示采购单选择弹窗

// 加工费用（单位：分）
const costs = ref({
	labor_cost_fen: 0,      // 人工费
	packaging_cost_fen: 0,  // 包装费
	transport_cost_fen: 0,  // 运费
	other_cost_fen: 0       // 其他费用
})

// 费用输入相关
const showCostInputPopup = ref(false) // 是否显示费用输入弹窗
const currentCostType = ref('') // 当前输入的费用类型
const costInputValue = ref('') // 费用输入值

// 产出成品列表
const outputProducts = ref([])

// 成品表单
const showProductPopup = ref(false) // 是否显示添加成品弹窗
const productForm = ref({
	product_name: '',
	spec: '',
	grade: '',
	weight_jin: ''
})

// 重量输入相关
const showWeightInputPopup = ref(false) // 是否显示重量输入弹窗
const weightInputValue = ref('') // 重量输入值

// 成本分摊预览
const showAllocationPopup = ref(false) // 是否显示成本分摊预览弹窗

// 成功页面
const showSuccessPage = ref(false) // 是否显示成功页面
const successInfo = ref({}) // 成功信息

// 等级选项
const gradeOptions = ['特级', '一级', '二级', '三级']

// ==================== 计算属性 ====================

// 过滤后的采购单列表
const filteredPurchases = computed(() => {
	if (!purchaseSearchKeyword.value) {
		return purchaseList.value
	}
	const keyword = purchaseSearchKeyword.value.toLowerCase()
	return purchaseList.value.filter(purchase => {
		return (
			(purchase.fruit_name && purchase.fruit_name.toLowerCase().includes(keyword)) ||
			(purchase.supplier_name && purchase.supplier_name.toLowerCase().includes(keyword)) ||
			(purchase.origin && purchase.origin.toLowerCase().includes(keyword))
		)
	})
})

// 当前费用标签
const currentCostLabel = computed(() => {
	const labels = {
		labor: '人工费',
		packaging: '包装费',
		transport: '运费',
		other: '其他费用'
	}
	return labels[currentCostType.value] || '费用'
})

// 加工总成本（分）
const totalProcessingCostFen = computed(() => {
	return costs.value.labor_cost_fen +
		costs.value.packaging_cost_fen +
		costs.value.transport_cost_fen +
		costs.value.other_cost_fen
})

// 采购成本（分）
const purchaseCostFen = computed(() => {
	return selectedPurchase.value?.total_amount_fen || 0
})

// 总成本（分）= 采购成本 + 加工费用
const totalCostFen = computed(() => {
	return purchaseCostFen.value + totalProcessingCostFen.value
})

// 总产出重量（斤）
const totalOutputWeightJin = computed(() => {
	return outputProducts.value.reduce((sum, item) => sum + (parseFloat(item.weight_jin) || 0), 0)
})

// 损耗重量（斤）
const lossWeightJin = computed(() => {
	const inputWeight = selectedPurchase.value?.weight_jin || 0
	return Math.max(0, inputWeight - totalOutputWeightJin.value)
})

// 损耗率（%）
const lossRate = computed(() => {
	const inputWeight = selectedPurchase.value?.weight_jin || 0
	if (inputWeight <= 0) return 0
	return (lossWeightJin.value / inputWeight) * 100
})

// 是否可以提交
const canSubmit = computed(() => {
	// 必须选择采购单
	if (!selectedPurchase.value) return false
	// 必须至少有一个产出成品
	if (outputProducts.value.length === 0) return false
	// 产出重量必须大于0
	if (totalOutputWeightJin.value <= 0) return false
	return true
})

// 成本分摊列表
const allocationList = computed(() => {
	if (outputProducts.value.length === 0 || totalOutputWeightJin.value <= 0) {
		return []
	}

	let allocatedTotalFen = 0
	return outputProducts.value.map((item, index) => {
		const weightJin = parseFloat(item.weight_jin) || 0
		const weightRatio = weightJin / totalOutputWeightJin.value

		// 计算分摊成本（最后一个使用差额法，避免舍入误差）
		let allocatedCostFen
		if (index === outputProducts.value.length - 1) {
			allocatedCostFen = totalCostFen.value - allocatedTotalFen
		} else {
			allocatedCostFen = Math.floor(totalCostFen.value * weightRatio)
			allocatedTotalFen += allocatedCostFen
		}

		// 计算单位成本（分/斤）
		const unitCostFen = weightJin > 0 ? Math.floor(allocatedCostFen / weightJin) : 0

		return {
			product_name: item.product_name || '未命名成品',
			weight_jin: weightJin,
			weight_ratio: weightRatio,
			allocated_cost_fen: allocatedCostFen,
			unit_cost_fen: unitCostFen
		}
	})
})

// ==================== 方法 ====================

/**
 * 显示采购单选择弹窗
 */
const showPurchasePicker = () => {
	showPurchasePopup.value = true
	loadPurchases()
}

/**
 * 关闭采购单选择弹窗
 */
const closePurchasePopup = () => {
	showPurchasePopup.value = false
	purchaseSearchKeyword.value = ''
}

/**
 * 加载待加工的采购单列表
 */
const loadPurchases = async () => {
	try {
		showLoading('加载采购单...')
		// TODO: 当前 purchaseAPI 没有 list 方法，暂时保留 uniCloud 直接查询逻辑
		// 待后端提供 purchaseAPI.list(params) 接口后替换：
		// const res = await purchaseAPI.list({ status: 'pending', page_size: 50 })
		// if (res.code === 0) { purchaseList.value = res.data.list || [] }
		const db = uniCloud.database()
		const res = await db.collection('purchases')
			.where({
				status: 'pending' // 只查询待加工状态的采购单
			})
			.orderBy('purchase_date', 'desc')
			.limit(50)
			.get()

		purchaseList.value = res.data || []
		hideLoading()
	} catch (error) {
		hideLoading()
		showError('加载采购单失败：' + error.message)
		console.error('加载采购单失败：', error)
	}
}

/**
 * 搜索采购单（防抖处理）
 */
let purchaseSearchTimer = null
const searchPurchases = () => {
	if (purchaseSearchTimer) clearTimeout(purchaseSearchTimer)
	purchaseSearchTimer = setTimeout(() => {
		// 计算属性会自动过滤，这里不需要额外处理
	}, 300)
}

/**
 * 选择采购单
 * @param {Object} purchase - 采购单对象
 */
const selectPurchase = (purchase) => {
	selectedPurchase.value = purchase
	// 重置费用和成品列表
	costs.value = {
		labor_cost_fen: 0,
		packaging_cost_fen: 0,
		transport_cost_fen: 0,
		other_cost_fen: 0
	}
	outputProducts.value = []
	closePurchasePopup()
}

/**
 * 显示费用输入弹窗
 * @param {String} type - 费用类型
 */
const showCostKeyboard = (type) => {
	currentCostType.value = type
	// 获取当前费用值并转换为元
	const currentValue = costs.value[`${type}_cost_fen`] || 0
	costInputValue.value = currentValue > 0 ? fenToYuan(currentValue) : ''
	showCostInputPopup.value = true
}

/**
 * 关闭费用输入弹窗
 */
const closeCostInputPopup = () => {
	showCostInputPopup.value = false
	currentCostType.value = ''
	costInputValue.value = ''
}

/**
 * 输入费用按键
 * @param {String} key - 按键值
 */
const inputCostKey = (key) => {
	const currentValue = costInputValue.value

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

	costInputValue.value = currentValue + key
}

/**
 * 删除费用按键
 */
const deleteCostKey = () => {
	costInputValue.value = costInputValue.value.slice(0, -1)
}

/**
 * 清空费用输入
 */
const clearCostInput = () => {
	costInputValue.value = ''
}

/**
 * 确认费用输入
 */
const confirmCostInput = () => {
	const fenValue = yuanToFen(costInputValue.value)
	costs.value[`${currentCostType.value}_cost_fen`] = fenValue
	closeCostInputPopup()
}

/**
 * 显示添加成品弹窗
 */
const showAddProductPopup = () => {
	// 重置表单
	productForm.value = {
		product_name: selectedPurchase.value?.fruit_name || '',
		spec: '',
		grade: '',
		weight_jin: ''
	}
	showProductPopup.value = true
}

/**
 * 关闭添加成品弹窗
 */
const closeProductPopup = () => {
	showProductPopup.value = false
}

/**
 * 选择等级
 * @param {String} grade - 等级
 */
const selectGrade = (grade) => {
	productForm.value.grade = grade
	// 自动生成成品名称
	if (productForm.value.spec && grade) {
		productForm.value.product_name = `${productForm.value.spec}${grade}`
	}
}

/**
 * 显示重量输入弹窗
 */
const showWeightKeyboard = () => {
	weightInputValue.value = productForm.value.weight_jin ? String(productForm.value.weight_jin) : ''
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
	productForm.value.weight_jin = weightInputValue.value
	closeWeightInputPopup()
}

/**
 * 确认添加成品
 */
const confirmAddProduct = () => {
	// 校验
	if (!productForm.value.product_name) {
		showError('请输入成品名称')
		return
	}
	if (!productForm.value.weight_jin || parseFloat(productForm.value.weight_jin) <= 0) {
		showError('请输入有效的重量')
		return
	}

	// 添加到列表
	outputProducts.value.push({
		product_name: productForm.value.product_name,
		spec: productForm.value.spec,
		grade: productForm.value.grade,
		weight_jin: parseFloat(productForm.value.weight_jin),
		fruit_name: selectedPurchase.value?.fruit_name || '',
		origin: selectedPurchase.value?.origin || ''
	})

	closeProductPopup()
}

/**
 * 删除成品
 * @param {Number} index - 成品索引
 */
const deleteProduct = (index) => {
	uni.showModal({
		title: '确认删除',
		content: '确定要删除该成品吗？',
		success: (res) => {
			if (res.confirm) {
				outputProducts.value.splice(index, 1)
			}
		}
	})
}

/**
 * 显示成本分摊预览弹窗
 */
const showCostAllocationPopup = () => {
	if (outputProducts.value.length === 0) {
		showError('请先添加产出成品')
		return
	}
	showAllocationPopup.value = true
}

/**
 * 关闭成本分摊预览弹窗
 */
const closeAllocationPopup = () => {
	showAllocationPopup.value = false
}

/**
 * 提交加工入库
 */
const submitProcessing = async () => {
	// 校验
	if (!canSubmit.value) {
		if (!selectedPurchase.value) {
			showError('请选择采购单')
		} else if (outputProducts.value.length === 0) {
			showError('请添加产出成品')
		} else if (totalOutputWeightJin.value <= 0) {
			showError('产出重量必须大于0')
		}
		return
	}

	try {
		showLoading('正在入库...')

		// 构建请求参数
		const requestData = {
			purchase_id: selectedPurchase.value._id,
			costs: {
				labor_cost_fen: costs.value.labor_cost_fen,
				packaging_cost_fen: costs.value.packaging_cost_fen,
				transport_cost_fen: costs.value.transport_cost_fen,
				other_cost_fen: costs.value.other_cost_fen
			},
			outputs: outputProducts.value.map(item => ({
				product_name: item.product_name,
				grade: item.grade,
				spec: item.spec,
				weight_jin: item.weight_jin,
				fruit_name: item.fruit_name,
				origin: item.origin
			}))
		}

		// 调用本地 API 进行加工入库
		const res = await processingAPI.create(requestData)

		hideLoading()

		if (res.code === 0) {
			// 入库成功
			showSuccess('入库成功')
			// 适配 API 返回数据（兼容 camelCase / snake_case）
			const apiData = res.data
			successInfo.value = {
				order_no: apiData.orderNo || apiData.order_no || '',
				purchase_batch_no: apiData.purchaseBatchNo || apiData.purchase_batch_no || '',
				fruit_name: apiData.fruitName || apiData.fruit_name || '',
				output_products: apiData.outputProducts || apiData.output_products || outputProducts.value,
				total_cost_fen: apiData.totalCostFen || apiData.total_cost_fen || 0
			}
			showSuccessPage.value = true
		} else {
			showError(res.message || '入库失败')
		}
	} catch (error) {
		hideLoading()
		showError('入库失败：' + error.message)
		console.error('入库失败：', error)
	}
}

/**
 * 继续加工
 */
const continueProcessing = () => {
	// 重置表单
	selectedPurchase.value = null
	costs.value = {
		labor_cost_fen: 0,
		packaging_cost_fen: 0,
		transport_cost_fen: 0,
		other_cost_fen: 0
	}
	outputProducts.value = []
	showSuccessPage.value = false
	successInfo.value = {}
}

/**
 * 查看库存
 */
const viewInventory = () => {
	// 跳转到库存页面
	uni.switchTab({
		url: '/pages/inventory/list'
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时可以预加载采购单列表（可选）
	// loadPurchases()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.processing-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	padding-bottom: 320rpx; // 为底部提交区域留出空间
}

/* ==================== 顶部区域 ==================== */
.top-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

/* 选择采购单按钮 */
.select-purchase-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 120rpx;
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

/* 原果信息卡片 */
.purchase-card {
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

.card-title {
	font-size: 32rpx;
	color: #4A90E2;
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
	padding: 25rpx 30rpx;
}

.info-row {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;

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

	&.highlight {
		color: #333333;
		font-weight: 500;
	}

	&.price {
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}
}

/* ==================== 加工费用区域 ==================== */
.cost-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.section-header {
	display: flex;
	align-items: center;
	margin-bottom: 25rpx;
}

.section-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.count-badge {
	font-size: 24rpx;
	color: #FFFFFF;
	background-color: #4A90E2;
	padding: 5rpx 15rpx;
	border-radius: 20rpx;
	margin-left: 15rpx;
}

.cost-list {
	//
}

.cost-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 25rpx 30rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.cost-label {
	font-size: 30rpx;
	color: #333333;
}

.cost-input-box {
	display: flex;
	align-items: center;
}

.cost-value {
	font-size: 30rpx;
	color: #CCCCCC;

	&.has-value {
		color: #333333;
		font-weight: 500;
	}
}

.cost-unit {
	font-size: 26rpx;
	color: #999999;
	margin-left: 5rpx;
}

.total-cost-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 25rpx 30rpx;
	background-color: #FFF8E1;
	border-radius: 12rpx;
	margin-top: 20rpx;
}

.total-label {
	font-size: 30rpx;
	color: #666666;
}

.total-value {
	font-size: 36rpx;
	color: #FF5722;
	font-weight: 600;
}

/* ==================== 产出成品区域 ==================== */
.output-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

/* 空状态 */
.empty-state {
	padding: 60rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* 成品列表 */
.product-list {
	margin-bottom: 20rpx;
}

.product-card {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 25rpx 30rpx;
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

.delete-btn {
	font-size: 26rpx;
	color: #FF5722;
	padding: 10rpx 20rpx;
	background-color: #FFEBE9;
	border-radius: 8rpx;
}

.product-info {
	display: flex;
	flex-wrap: wrap;
}

.info-item {
	width: 50%;
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;
}

.item-label {
	font-size: 26rpx;
	color: #999999;
}

.item-value {
	font-size: 26rpx;
	color: #333333;

	&.highlight {
		color: #4A90E2;
		font-weight: 500;
	}
}

/* 添加成品按钮 */
.add-product-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 100rpx;
	background-color: #FFFFFF;
	border: 2rpx dashed #4A90E2;
	border-radius: 12rpx;
	margin-bottom: 20rpx;
}

.add-icon {
	font-size: 40rpx;
	color: #4A90E2;
	margin-right: 10rpx;
}

.add-text {
	font-size: 30rpx;
	color: #4A90E2;
}

/* 统计信息 */
.stats-card {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 25rpx 30rpx;
}

.stat-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.stat-label {
	font-size: 28rpx;
	color: #666666;
}

.stat-value {
	font-size: 28rpx;
	color: #333333;

	&.has-loss {
		color: #FF5722;
		font-weight: 500;
	}
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
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.submit-info .info-row {
	display: flex;
	align-items: center;
}

.submit-info .info-label {
	font-size: 26rpx;
	color: #666666;
	margin-right: 10rpx;
}

.submit-info .info-value {
	font-size: 28rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}
}

.submit-actions {
	display: flex;
	gap: 20rpx;
}

.preview-btn {
	flex: 1;
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #F8F8F8;
	border: 2rpx solid #4A90E2;
	border-radius: 12rpx;
}

.preview-text {
	font-size: 30rpx;
	color: #4A90E2;
}

.submit-btn {
	flex: 2;
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

/* ==================== 采购单选择弹窗 ==================== */
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

.purchase-list {
	max-height: 50vh;
}

.purchase-item {
	padding: 25rpx 30rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.purchase-main {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;
}

.purchase-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	flex: 1;
}

.purchase-origin {
	font-size: 26rpx;
	color: #999999;
	background-color: #F8F8F8;
	padding: 5rpx 15rpx;
	border-radius: 6rpx;
	margin-left: 15rpx;
}

.purchase-details {
	display: flex;
	margin-bottom: 15rpx;
}

.detail-item {
	display: flex;
	align-items: center;
	margin-right: 30rpx;
}

.detail-label {
	font-size: 26rpx;
	color: #999999;
}

.detail-value {
	font-size: 26rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 500;
	}
}

.purchase-supplier {
	display: flex;
	align-items: center;
}

.supplier-label {
	font-size: 24rpx;
	color: #999999;
}

.supplier-value {
	font-size: 24rpx;
	color: #666666;
}

.empty-list {
	padding: 80rpx 0;
	text-align: center;
}

/* ==================== 添加成品弹窗 ==================== */
.form-item {
	padding: 25rpx 30rpx;
	border-bottom: 1rpx solid #EEEEEE;
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
}

.form-value {
	font-size: 30rpx;
	color: #CCCCCC;

	&.has-value {
		color: #333333;
	}
}

/* 等级选择 */
.grade-options {
	display: flex;
	flex-wrap: wrap;
	gap: 15rpx;
}

.grade-option {
	padding: 15rpx 30rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	border: 2rpx solid transparent;

	&.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}
}

.grade-text {
	font-size: 28rpx;
	color: #333333;

	.grade-option.active & {
		color: #4A90E2;
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

/* ==================== 成本分摊预览弹窗 ==================== */
.allocation-summary {
	padding: 25rpx 30rpx;
	background-color: #F8F8F8;
}

.summary-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}

	&.total {
		padding-top: 15rpx;
		border-top: 1rpx solid #EEEEEE;
	}
}

.summary-label {
	font-size: 28rpx;
	color: #666666;
}

.summary-value {
	font-size: 28rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}
}

.allocation-list {
	padding: 0 30rpx;
}

.allocation-item {
	padding: 25rpx 0;
	border-bottom: 1rpx solid #EEEEEE;
}

.allocation-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;
}

.allocation-name {
	font-size: 30rpx;
	color: #333333;
	font-weight: 500;
}

.allocation-weight {
	font-size: 26rpx;
	color: #4A90E2;
}

.allocation-details {
	//
}

.allocation-details .detail-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.allocation-details .detail-label {
	font-size: 26rpx;
	color: #999999;
}

.allocation-details .detail-value {
	font-size: 26rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 500;
	}
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
	margin-bottom: 30rpx;
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
}

.order-info .info-value {
	font-size: 26rpx;
	color: #333333;
}

.product-cost-list {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 25rpx 30rpx;
	margin-bottom: 40rpx;
}

.list-title {
	font-size: 28rpx;
	color: #666666;
	margin-bottom: 20rpx;
	display: block;
}

.product-cost-list .cost-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.product-cost-list .cost-name {
	font-size: 28rpx;
	color: #333333;
}

.product-cost-list .cost-value {
	font-size: 28rpx;
	color: #FF5722;
	font-weight: 500;
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
