<template>
	<view class="history-container">
		<!-- ==================== 顶部筛选区域 ==================== -->
		<view class="filter-section">
			<!-- 日期筛选 -->
			<view class="date-filter">
				<picker
					mode="selector"
					:range="dateFilterOptions"
					range-key="label"
					:value="dateFilterIndex"
					@change="onDateFilterChange"
				>
					<view class="filter-picker">
						<text class="picker-text">{{ dateFilterOptions[dateFilterIndex].label }}</text>
						<text class="picker-arrow">▼</text>
					</view>
				</picker>
			</view>

			<!-- 供应商筛选 -->
			<view class="supplier-filter" @click="showSupplierFilterPopup = true">
				<text class="filter-text">{{ selectedSupplierName || '全部供应商' }}</text>
				<text class="filter-arrow">▼</text>
			</view>

			<!-- 更多筛选按钮 -->
			<view class="more-filter-btn" @click="showMoreFilterPopup = true">
				<text class="more-icon">⚙</text>
				<view class="filter-badge" v-if="hasActiveFilter"></view>
			</view>
		</view>

		<!-- ==================== 统计概览区域 ==================== -->
		<view class="stats-section">
			<view class="stat-row">
				<view class="stat-item">
					<text class="stat-value price">¥{{ fenToYuan(totalPurchaseAmountFen) }}</text>
					<text class="stat-label">总采购金额</text>
				</view>
				<view class="stat-item">
					<text class="stat-value weight">{{ totalWeightJin.toFixed(2) }}</text>
					<text class="stat-label">总入库 (斤)</text>
				</view>
			</view>
			<view class="stat-row">
				<view class="stat-item">
					<text class="stat-value">{{ totalCount }}</text>
					<text class="stat-label">订单数</text>
				</view>
				<view class="stat-item">
					<text class="stat-value avg">¥{{ avgUnitPriceYuan }}/斤</text>
					<text class="stat-label">平均单价</text>
				</view>
			</view>
		</view>

		<!-- ==================== 订单列表区域 ==================== -->
		<scroll-view
			class="list-section"
			scroll-y
			:refresher-enabled="true"
			:refresher-triggered="isRefreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<!-- 空状态 -->
			<view class="empty-state" v-if="orderList.length === 0 && !isLoading">
				<text class="empty-icon">📋</text>
				<text class="empty-text">暂无采购记录</text>
				<text class="empty-hint">下拉刷新或修改筛选条件</text>
			</view>

			<!-- 订单列表 -->
			<view class="order-list" v-else>
				<view
					class="order-card"
					v-for="order in orderList"
					:key="order._id"
					@click="showOrderDetail(order)"
				>
					<!-- 卡片头部：订单号 + 状态标签 -->
					<view class="card-header">
						<view class="order-no-wrapper">
							<text class="order-no">{{ order.order_no }}</text>
						</view>
						<view class="status-tag" :class="order.status">
							{{ getStatusText(order.status) }}
						</view>
					</view>

					<!-- 供应商信息 -->
					<view class="card-supplier">
						<text class="supplier-name">{{ order.supplier_name || '未知供应商' }}</text>
						<text class="supplier-phone" v-if="order.supplier_phone">{{ order.supplier_phone }}</text>
					</view>

					<!-- 商品和金额信息 -->
					<view class="card-content">
						<view class="product-info">
							<text class="product-name">{{ order.fruit_name }}</text>
							<view class="product-tags">
								<text class="origin-tag" v-if="order.origin">{{ order.origin }}</text>
							</view>
						</view>
						<view class="amount-row">
							<text class="amount-label">重量</text>
							<text class="amount-value">{{ order.total_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
						</view>
						<view class="amount-row">
							<text class="amount-label">单价</text>
							<text class="amount-value">¥{{ fenToYuan(order.unit_cost_fen) }}/斤</text>
						</view>
						<view class="amount-row total-row">
							<text class="amount-label">总金额</text>
							<text class="amount-value highlight">¥{{ fenToYuan(order.total_amount_fen) }}</text>
						</view>
					</view>

					<!-- 时间信息 -->
					<view class="card-footer">
						<text class="time-text">{{ formatDateTime(order.create_time, 'MM-DD HH:mm') }}</text>
						<view class="payment-status" v-if="order.payment_status">
							<text class="payment-icon" :class="order.payment_status">
								{{ order.payment_status === 'paid' ? '💰' : '⏳' }}
							</text>
							<text class="payment-text">{{ getPaymentStatusText(order.payment_status) }}</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 加载更多 -->
			<view class="load-more" v-if="orderList.length > 0">
				<text class="load-text" v-if="isLoading">加载中...</text>
				<text class="load-text" v-else-if="!hasMore">没有更多了</text>
				<text class="load-text" v-else>上拉加载更多</text>
			</view>
		</scroll-view>

		<!-- ==================== 半屏弹窗：供应商筛选 ==================== -->
		<view class="popup-mask" v-if="showSupplierFilterPopup" @click="closeSupplierFilterPopup"></view>
		<view class="popup-container supplier-popup" :class="{ show: showSupplierFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">选择供应商</text>
				<text class="popup-close" @click="closeSupplierFilterPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="supplierSearchKeyword"
						placeholder="搜索供应商名称"
						@input="searchSuppliers"
					/>
				</view>

				<!-- 供应商列表 -->
				<scroll-view class="supplier-list" scroll-y>
					<!-- 全部供应商选项 -->
					<view
						class="supplier-item"
						:class="{ selected: !selectedSupplierId }"
						@click="selectSupplierFilter(null)"
					>
						<text class="supplier-item-name">全部供应商</text>
					</view>

					<view
						class="supplier-item"
						v-for="supplier in filteredSupplierList"
						:key="supplier._id"
						:class="{ selected: selectedSupplierId === supplier._id }"
						@click="selectSupplierFilter(supplier)"
					>
						<text class="supplier-item-name">{{ supplier.name }}</text>
						<text class="supplier-item-phone" v-if="supplier.phone">{{ supplier.phone }}</text>
					</view>

					<view class="empty-list" v-if="filteredSupplierList.length === 0">
						<text class="empty-text">暂无供应商数据</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：更多筛选 ==================== -->
		<view class="popup-mask" v-if="showMoreFilterPopup" @click="closeMoreFilterPopup"></view>
		<view class="popup-container more-filter-popup" :class="{ show: showMoreFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">更多筛选</text>
				<text class="popup-close" @click="closeMoreFilterPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 状态筛选 -->
				<view class="filter-group">
					<text class="filter-label">订单状态</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterStatus === '' }"
							@click="filterStatus = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option pending"
							:class="{ active: filterStatus === 'pending' }"
							@click="filterStatus = 'pending'"
						>
							<text class="option-text">待入库</text>
						</view>
						<view
							class="filter-option completed"
							:class="{ active: filterStatus === 'completed' }"
							@click="filterStatus = 'completed'"
						>
							<text class="option-text">已完成</text>
						</view>
						<view
							class="filter-option cancelled"
							:class="{ active: filterStatus === 'cancelled' }"
							@click="filterStatus = 'cancelled'"
						>
							<text class="option-text">已取消</text>
						</view>
					</view>
				</view>

				<!-- 付款状态筛选 -->
				<view class="filter-group">
					<text class="filter-label">付款状态</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterPaymentStatus === '' }"
							@click="filterPaymentStatus = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option unpaid"
							:class="{ active: filterPaymentStatus === 'unpaid' }"
							@click="filterPaymentStatus = 'unpaid'"
						>
							<text class="option-text">未付</text>
						</view>
						<view
							class="filter-option paid"
							:class="{ active: filterPaymentStatus === 'paid' }"
							@click="filterPaymentStatus = 'paid'"
						>
							<text class="option-text">已付</text>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="filter-actions">
					<view class="reset-btn" @click="resetMoreFilter">
						<text class="reset-text">重置</text>
					</view>
					<view class="confirm-btn" @click="applyMoreFilter">
						<text class="confirm-text">确定</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：订单详情 ==================== -->
		<view class="popup-mask" v-if="showDetailPopup" @click="closeDetailPopup"></view>
		<view class="popup-container detail-popup" :class="{ show: showDetailPopup }">
			<view class="popup-header">
				<text class="popup-title">采购单详情</text>
				<text class="popup-close" @click="closeDetailPopup">×</text>
			</view>

			<scroll-view class="popup-content" scroll-y v-if="currentOrder">
				<!-- 采购单基本信息 -->
				<view class="detail-section">
					<view class="detail-row">
						<text class="detail-label">采购单号</text>
						<text class="detail-value">{{ currentOrder.order_no }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">供应商</text>
						<text class="detail-value">{{ currentOrder.supplier_name || '未知' }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">联系电话</text>
						<text class="detail-value">{{ currentOrder.supplier_phone || '未填写' }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">状态</text>
						<view class="status-tag" :class="currentOrder.status">
							{{ getStatusText(currentOrder.status) }}
						</view>
					</view>
					<view class="detail-row">
						<text class="detail-label">创建时间</text>
						<text class="detail-value">{{ formatDateTime(currentOrder.create_time) }}</text>
					</view>
					<view class="detail-row" v-if="currentOrder.remark">
						<text class="detail-label">备注</text>
						<text class="detail-value">{{ currentOrder.remark }}</text>
					</view>
				</view>

				<!-- 商品信息 -->
				<view class="detail-section">
					<text class="section-title">商品信息</text>
					<view class="product-detail">
						<view class="detail-row">
							<text class="detail-label">原果名称</text>
							<text class="detail-value">{{ currentOrder.fruit_name }}</text>
						</view>
						<view class="detail-row" v-if="currentOrder.origin">
							<text class="detail-label">产地</text>
							<text class="detail-value">{{ currentOrder.origin }}</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">采购重量</text>
							<text class="detail-value">{{ currentOrder.total_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">单位成本</text>
							<text class="detail-value">¥{{ fenToYuan(currentOrder.unit_cost_fen) }}/斤</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">总金额</text>
							<text class="detail-value highlight">¥{{ fenToYuan(currentOrder.total_amount_fen) }}</text>
						</view>
					</view>
				</view>

				<!-- 入库信息 -->
				<view class="detail-section" v-if="currentOrder.status === 'completed'">
					<text class="section-title">入库信息</text>
					<view class="stock-in-info">
						<view class="detail-row">
							<text class="detail-label">入库时间</text>
							<text class="detail-value">{{ formatDateTime(currentOrder.stock_in_time) }}</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">入库重量</text>
							<text class="detail-value">{{ currentOrder.stock_in_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
						</view>
						<view class="detail-row" v-if="currentOrder.processing_order_id">
							<text class="detail-label">关联加工单</text>
							<text class="detail-value link" @click="viewProcessingOrder(currentOrder.processing_order_id)">
								查看详情 >
							</text>
						</view>
					</view>
				</view>

				<!-- 付款信息 -->
				<view class="detail-section">
					<text class="section-title">付款信息</text>
					<view class="payment-info">
						<view class="detail-row">
							<text class="detail-label">付款状态</text>
							<text class="detail-value">{{ getPaymentStatusText(currentOrder.payment_status || 'unpaid') }}</text>
						</view>
						<view class="detail-row" v-if="currentOrder.paid_amount_fen">
							<text class="detail-label">已付金额</text>
							<text class="detail-value">¥{{ fenToYuan(currentOrder.paid_amount_fen) }}</text>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="detail-actions" v-if="currentOrder.status === 'pending'">
					<view class="action-btn cancel" @click="cancelOrder">
						<text class="btn-text">作废订单</text>
					</view>
				</view>
				<view class="detail-actions" v-else-if="currentOrder.status === 'cancelled'">
					<view class="status-hint">
						<text class="hint-text">该订单已作废</text>
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, formatDateTime, showLoading, hideLoading, showSuccess, showError, showConfirm } from '@/utils/util.js'
import { purchaseAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 日期筛选选项
const dateFilterOptions = [
	{ label: '今天', value: 'today' },
	{ label: '昨天', value: 'yesterday' },
	{ label: '本周', value: 'week' },
	{ label: '本月', value: 'month' },
	{ label: '全部', value: 'all' }
]
const dateFilterIndex = ref(0)

// 供应商筛选
const showSupplierFilterPopup = ref(false)
const supplierList = ref([])
const supplierSearchKeyword = ref('')
const selectedSupplierId = ref('')
const selectedSupplierName = ref('')

// 更多筛选
const showMoreFilterPopup = ref(false)
const filterStatus = ref('')
const filterPaymentStatus = ref('')

// 订单列表
const orderList = ref([])

// 分页相关
const currentPage = ref(0)
const pageSize = 20
const totalCount = ref(0)
const hasMore = ref(true)

// 加载状态
const isLoading = ref(false)
const isRefreshing = ref(false)

// 统计数据
const totalPurchaseAmountFen = ref(0)
const totalWeightJin = ref(0)
const avgUnitPriceYuan = ref(0)

// 订单详情
const showDetailPopup = ref(false)
const currentOrder = ref(null)

// ==================== 计算属性 ====================

// 过滤后的供应商列表
const filteredSupplierList = computed(() => {
	if (!supplierSearchKeyword.value) {
		return supplierList.value
	}
	const keyword = supplierSearchKeyword.value.toLowerCase()
	return supplierList.value.filter(supplier => {
		return (
			(supplier.name && supplier.name.toLowerCase().includes(keyword)) ||
			(supplier.phone && supplier.phone.includes(keyword))
		)
	})
})

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterStatus.value !== '' || filterPaymentStatus.value !== ''
})

// ==================== 方法 ====================

/**
 * 获取日期范围
 */
const getDateRange = (filterValue) => {
	const now = new Date()
	const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())

	let startTime, endTime

	switch (filterValue) {
		case 'today':
			startTime = today.getTime()
			endTime = now.getTime()
			break
		case 'yesterday':
			const yesterday = new Date(today)
			yesterday.setDate(yesterday.getDate() - 1)
			startTime = yesterday.getTime()
			endTime = today.getTime() - 1
			break
		case 'week':
			const weekStart = new Date(today)
			weekStart.setDate(weekStart.getDate() - weekStart.getDay() + 1)
			startTime = weekStart.getTime()
			endTime = now.getTime()
			break
		case 'month':
			const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
			startTime = monthStart.getTime()
			endTime = now.getTime()
			break
		case 'all':
		default:
			startTime = null
			endTime = null
			break
	}

	return { startTime, endTime }
}

/**
 * 加载订单列表
 */
const loadOrderList = async (isRefresh = false) => {
	if (isLoading.value) return

	try {
		isLoading.value = true

		// TODO: 当前后端没有 purchase 列表查询端点，暂时保留 uniCloud 直接查询逻辑
		// 待后端提供 purchaseAPI.list(params) 接口后替换为：const res = await purchaseAPI.list(params)

		if (isRefresh) {
			currentPage.value = 0
			orderList.value = []
		}

		const db = uniCloud.database()
		const dbCmd = db.command

		let whereCondition = {
			status: dbCmd.neq('deleted')
		}

		// 日期筛选
		const dateValue = dateFilterOptions[dateFilterIndex.value].value
		const { startTime, endTime } = getDateRange(dateValue)
		if (startTime !== null) {
			whereCondition.create_time = dbCmd.gte(startTime).and(dbCmd.lte(endTime))
		}

		// 供应商筛选
		if (selectedSupplierId.value) {
			whereCondition.supplier_id = selectedSupplierId.value
		}

		// 状态筛选
		if (filterStatus.value) {
			whereCondition.status = filterStatus.value
		}

		// 付款状态筛选
		if (filterPaymentStatus.value) {
			whereCondition.payment_status = filterPaymentStatus.value
		}

		const skipCount = currentPage.value * pageSize

		const res = await db.collection('purchases')
			.where(whereCondition)
			.orderBy('create_time', 'desc')
			.skip(skipCount)
			.limit(pageSize)
			.get()

		if (isRefresh) {
			orderList.value = res.data || []
		} else {
			orderList.value = [...orderList.value, ...(res.data || [])]
		}

		hasMore.value = (res.data || []).length >= pageSize
		currentPage.value++

		if (isRefresh) {
			await loadStatistics()
		}

	} catch (error) {
		showError('加载采购订单失败：' + error.message)
		console.error('加载采购订单失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 加载统计数据
 */
const loadStatistics = async () => {
	try {
		// TODO: 统计数据应从 purchaseAPI 聚合端点获取，当前保留 uniCloud 直接查询
		const db = uniCloud.database()
		const dbCmd = db.command

		let whereCondition = {
			status: dbCmd.neq('deleted')
		}

		const dateValue = dateFilterOptions[dateFilterIndex.value].value
		const { startTime, endTime } = getDateRange(dateValue)
		if (startTime !== null) {
			whereCondition.create_time = dbCmd.gte(startTime).and(dbCmd.lte(endTime))
		}

		if (selectedSupplierId.value) {
			whereCondition.supplier_id = selectedSupplierId.value
		}

		if (filterStatus.value) {
			whereCondition.status = filterStatus.value
		}

		if (filterPaymentStatus.value) {
			whereCondition.payment_status = filterPaymentStatus.value
		}

		const res = await db.collection('purchases')
			.where(whereCondition)
			.field({
				total_amount_fen: true,
				total_weight_jin: true,
				unit_cost_fen: true
			})
			.get()

		const data = res.data || []

		totalCount.value = data.length
		totalPurchaseAmountFen.value = data.reduce((sum, item) => sum + (item.total_amount_fen || 0), 0)
		totalWeightJin.value = data.reduce((sum, item) => sum + (item.total_weight_jin || 0), 0)
		
		// 计算平均单价
		if (totalWeightJin.value > 0) {
			avgUnitPriceYuan.value = (totalPurchaseAmountFen.value / totalWeightJin.value / 100).toFixed(2)
		} else {
			avgUnitPriceYuan.value = 0
		}

	} catch (error) {
		console.error('加载统计数据失败：', error)
	}
}

/**
 * 加载供应商列表
 */
const loadSupplierList = async () => {
	try {
		// TODO: 应使用 supplierAPI.list({ status: 'active', page_size: 200 }) 替代
		const db = uniCloud.database()
		const res = await db.collection('suppliers')
			.where({
				status: 'active'
			})
			.orderBy('name', 'asc')
			.limit(200)
			.get()

		supplierList.value = res.data || []
	} catch (error) {
		console.error('加载供应商列表失败：', error)
	}
}

/**
 * 搜索供应商
 */
let supplierSearchTimer = null
const searchSuppliers = () => {
	if (supplierSearchTimer) clearTimeout(supplierSearchTimer)
	supplierSearchTimer = setTimeout(() => {
		// 计算属性会自动过滤
	}, 300)
}

/**
 * 日期筛选变化
 */
const onDateFilterChange = (e) => {
	dateFilterIndex.value = e.detail.value
	loadOrderList(true)
}

/**
 * 关闭供应商筛选弹窗
 */
const closeSupplierFilterPopup = () => {
	showSupplierFilterPopup.value = false
}

/**
 * 选择供应商筛选
 */
const selectSupplierFilter = (supplier) => {
	if (supplier) {
		selectedSupplierId.value = supplier._id
		selectedSupplierName.value = supplier.name
	} else {
		selectedSupplierId.value = ''
		selectedSupplierName.value = ''
	}
	closeSupplierFilterPopup()
	loadOrderList(true)
}

/**
 * 关闭更多筛选弹窗
 */
const closeMoreFilterPopup = () => {
	showMoreFilterPopup.value = false
}

/**
 * 重置更多筛选
 */
const resetMoreFilter = () => {
	filterStatus.value = ''
	filterPaymentStatus.value = ''
}

/**
 * 应用更多筛选
 */
const applyMoreFilter = () => {
	closeMoreFilterPopup()
	loadOrderList(true)
}

/**
 * 下拉刷新
 */
const onRefresh = () => {
	isRefreshing.value = true
	loadOrderList(true)
}

/**
 * 加载更多
 */
const loadMore = () => {
	if (!hasMore.value || isLoading.value) return
	loadOrderList(false)
}

/**
 * 获取状态文本
 */
const getStatusText = (status) => {
	const statusMap = {
		'pending': '待入库',
		'completed': '已完成',
		'cancelled': '已取消'
	}
	return statusMap[status] || status
}

/**
 * 获取付款状态文本
 */
const getPaymentStatusText = (status) => {
	const statusMap = {
		'unpaid': '未付款',
		'paid': '已付款'
	}
	return statusMap[status] || status
}

/**
 * 显示订单详情
 */
const showOrderDetail = (order) => {
	currentOrder.value = order
	showDetailPopup.value = true
}

/**
 * 关闭详情弹窗
 */
const closeDetailPopup = () => {
	showDetailPopup.value = false
	currentOrder.value = null
}

/**
 * 作废订单
 */
const cancelOrder = async () => {
	if (!currentOrder.value) return

	const confirmed = await showConfirm('确定要作废该订单吗？作废后不可恢复。', '作废订单确认')
	if (!confirmed) return

	try {
		showLoading('正在作废...')

		// 调用本地 API 撤销采购单
		const res = await purchaseAPI.cancel(currentOrder.value._id)

		hideLoading()

		if (res.code === 0) {
			showSuccess('订单已作废')
			closeDetailPopup()
			loadOrderList(true)
		} else {
			showError(res.message || '作废失败')
		}
	} catch (error) {
		hideLoading()
		showError('作废失败：' + error.message)
		console.error('作废订单失败：', error)
	}
}

/**
 * 查看关联的加工单
 */
const viewProcessingOrder = (orderId) => {
	uni.navigateTo({
		url: `/pages/processing/detail?id=${orderId}`
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	loadOrderList(true)
	loadSupplierList()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.history-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	display: flex;
	flex-direction: column;
}

/* ==================== 顶部筛选区域 ==================== */
.filter-section {
	display: flex;
	align-items: center;
	padding: 20rpx 30rpx;
	background-color: #FFFFFF;
	gap: 15rpx;
}

.date-filter {
	flex: 1;
}

.filter-picker {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 70rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.picker-text {
	font-size: 28rpx;
	color: #333333;
}

.picker-arrow {
	font-size: 20rpx;
	color: #999999;
	margin-left: 10rpx;
}

.supplier-filter {
	flex: 1.5;
	display: flex;
	align-items: center;
	justify-content: center;
	height: 70rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.filter-text {
	font-size: 28rpx;
	color: #333333;
	max-width: 200rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.filter-arrow {
	font-size: 20rpx;
	color: #999999;
	margin-left: 10rpx;
}

.more-filter-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 70rpx;
	height: 70rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	position: relative;
}

.more-icon {
	font-size: 32rpx;
	color: #666666;
}

.filter-badge {
	position: absolute;
	top: 12rpx;
	right: 12rpx;
	width: 16rpx;
	height: 16rpx;
	background-color: #FF5722;
	border-radius: 50%;
}

/* ==================== 统计概览区域 ==================== */
.stats-section {
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}

.stat-row {
	display: flex;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.stat-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.stat-value {
	font-size: 40rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 10rpx;

	&.price {
		color: #FF5722;
	}

	&.weight {
		color: #4A90E2;
	}

	&.avg {
		color: #9C27B0;
	}
}

.stat-label {
	font-size: 24rpx;
	color: #999999;
}

/* ==================== 订单列表区域 ==================== */
.list-section {
	flex: 1;
	padding: 0 30rpx;
}

/* 空状态 */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 150rpx 0;
}

.empty-icon {
	font-size: 100rpx;
	margin-bottom: 30rpx;
}

.empty-text {
	font-size: 32rpx;
	color: #999999;
	margin-bottom: 15rpx;
}

.empty-hint {
	font-size: 26rpx;
	color: #CCCCCC;
}

/* 订单列表 */
.order-list {
	padding-bottom: 30rpx;
}

.order-card {
	background-color: #FFFFFF;
	border-radius: 16rpx;
	padding: 30rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.05);
}

.card-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.order-no-wrapper {
	display: flex;
	align-items: center;
}

.order-no {
	font-size: 28rpx;
	color: #666666;
}

.status-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

	&.pending {
		background-color: #FFF8E1;
		color: #FF9800;
	}

	&.completed {
		background-color: #E8F5E9;
		color: #4CAF50;
	}

	&.cancelled {
		background-color: #FFEBE9;
		color: #F44336;
	}
}

.card-supplier {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;
}

.supplier-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.supplier-phone {
	font-size: 26rpx;
	color: #999999;
	margin-left: 15rpx;
}

.card-content {
	margin-bottom: 20rpx;
}

.product-info {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 15rpx;
}

.product-name {
	font-size: 30rpx;
	color: #333333;
	font-weight: 600;
}

.product-tags {
	display: flex;
	gap: 10rpx;
}

.origin-tag {
	font-size: 24rpx;
	color: #4A90E2;
	background-color: #E3F2FD;
	padding: 6rpx 16rpx;
	border-radius: 8rpx;
}

.amount-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10rpx;
}

.amount-label {
	font-size: 26rpx;
	color: #999999;
}

.amount-value {
	font-size: 28rpx;
	color: #333333;

	&.highlight {
		color: #FF5722;
		font-weight: 600;
		font-size: 32rpx;
	}
}

.total-row {
	margin-top: 10rpx;
	padding-top: 10rpx;
	border-top: 1rpx solid #F0F0F0;
}

.card-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding-top: 20rpx;
	border-top: 1rpx solid #EEEEEE;
}

.time-text {
	font-size: 26rpx;
	color: #999999;
}

.payment-status {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.payment-icon {
	font-size: 28rpx;

	&.paid {
		color: #4CAF50;
	}

	&.unpaid {
		color: #FF9800;
	}
}

.payment-text {
	font-size: 24rpx;
	color: #666666;
}

/* 加载更多 */
.load-more {
	padding: 30rpx 0;
	text-align: center;
}

.load-text {
	font-size: 26rpx;
	color: #999999;
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

/* ==================== 供应商筛选弹窗 ==================== */
.supplier-popup {
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

.supplier-list {
	max-height: 50vh;
}

.supplier-item {
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;

	&.selected {
		background-color: #E3F2FD;
	}
}

.supplier-item-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
}

.supplier-item-phone {
	font-size: 26rpx;
	color: #999999;
	margin-left: 15rpx;
}

.empty-list {
	padding: 80rpx 0;
	text-align: center;
}

/* ==================== 更多筛选弹窗 ==================== */
.more-filter-popup {
	//
}

.filter-group {
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;

	&:last-of-type {
		border-bottom: none;
	}
}

.filter-label {
	font-size: 30rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 20rpx;
	display: block;
}

.filter-options {
	display: flex;
	flex-wrap: wrap;
	gap: 20rpx;
}

.filter-option {
	padding: 15rpx 30rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	border: 2rpx solid transparent;

	&.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}

	&.pending.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.completed.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}

	&.cancelled.active {
		background-color: #FFEBE9;
		border-color: #F44336;
	}

	&.unpaid.active {
		background-color: #FFEBE9;
		border-color: #F44336;
	}

	&.paid.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}
}

.option-text {
	font-size: 28rpx;
	color: #333333;

	.filter-option.active & {
		color: #4A90E2;
	}

	.filter-option.pending.active & {
		color: #FF9800;
	}

	.filter-option.completed.active & {
		color: #4CAF50;
	}

	.filter-option.cancelled.active & {
		color: #F44336;
	}
}

.filter-actions {
	display: flex;
	gap: 20rpx;
	padding: 30rpx;
}

.reset-btn {
	flex: 1;
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.reset-text {
	font-size: 32rpx;
	color: #666666;
}

.confirm-btn {
	flex: 2;
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

/* ==================== 订单详情弹窗 ==================== */
.detail-popup {
	//
}

.detail-section {
	padding: 30rpx;
	border-bottom: 1rpx solid #EEEEEE;

	&:last-of-type {
		border-bottom: none;
	}
}

.section-title {
	font-size: 30rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 20rpx;
	display: block;
}

.detail-row {
	display: flex;
	align-items: flex-start;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.detail-label {
	font-size: 28rpx;
	color: #999999;
	width: 160rpx;
	flex-shrink: 0;
}

.detail-value {
	font-size: 28rpx;
	color: #333333;
	flex: 1;

	&.highlight {
		color: #FF5722;
		font-weight: 600;
	}

	&.link {
		color: #4A90E2;
	}
}

/* 商品详情 */
.product-detail {
	//
}

/* 入库信息 */
.stock-in-info {
	//
}

/* 付款信息 */
.payment-info {
	//
}

/* 操作按钮 */
.detail-actions {
	padding: 30rpx;
}

.action-btn {
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 12rpx;

	&.cancel {
		background-color: #FFEBE9;
		border: 2rpx solid #F44336;
	}
}

.action-btn .btn-text {
	font-size: 32rpx;

	.cancel & {
		color: #F44336;
	}
}

.status-hint {
	text-align: center;
	padding: 20rpx;
}

.hint-text {
	font-size: 28rpx;
	color: #999999;
}
</style>
