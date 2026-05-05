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

			<!-- 客户筛选 -->
			<view class="customer-filter" @click="showCustomerFilterPopup = true">
				<text class="filter-text">{{ selectedCustomerName || '全部客户' }}</text>
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
					<text class="stat-value price">¥{{ fenToYuan(totalSalesFen) }}</text>
					<text class="stat-label">总销售额</text>
				</view>
				<view class="stat-item">
					<text class="stat-value profit">¥{{ fenToYuan(totalProfitFen) }}</text>
					<text class="stat-label">总毛利</text>
				</view>
			</view>
			<view class="stat-row">
				<view class="stat-item">
					<text class="stat-value">{{ totalCount }}</text>
					<text class="stat-label">订单数</text>
				</view>
				<view class="stat-item">
					<text class="stat-value debt">¥{{ fenToYuan(totalDebtFen) }}</text>
					<text class="stat-label">总欠款</text>
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
				<text class="empty-text">暂无销售记录</text>
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
							<view class="type-tag" :class="order.sale_type">
								{{ order.sale_type === 'wholesale' ? '批发' : '零售' }}
							</view>
						</view>
						<view class="payment-tag" :class="order.payment_status">
							{{ getPaymentStatusText(order.payment_status) }}
						</view>
					</view>

					<!-- 客户信息 -->
					<view class="card-customer">
						<text class="customer-name">{{ order.customer_name || '散客' }}</text>
						<text class="customer-phone" v-if="order.customer_phone">{{ order.customer_phone }}</text>
					</view>

					<!-- 金额信息 -->
					<view class="card-amount">
						<view class="amount-main">
							<text class="amount-label">总金额</text>
							<text class="amount-value">¥{{ fenToYuan(order.total_amount_fen) }}</text>
						</view>
						<view class="amount-sub">
							<text class="profit-text">毛利: ¥{{ fenToYuan(order.total_profit_fen) }}</text>
							<text class="debt-text" v-if="order.debt_amount_fen > 0">
								欠款: ¥{{ fenToYuan(order.debt_amount_fen) }}
							</text>
						</view>
					</view>

					<!-- 时间信息 -->
					<view class="card-footer">
						<text class="time-text">{{ formatDateTime(order.create_time, 'MM-DD HH:mm') }}</text>
						<text class="weight-text">{{ order.total_weight_jin?.toFixed(2) || '0.00' }}斤</text>
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

		<!-- ==================== 半屏弹窗：客户筛选 ==================== -->
		<view class="popup-mask" v-if="showCustomerFilterPopup" @click="closeCustomerFilterPopup"></view>
		<view class="popup-container customer-popup" :class="{ show: showCustomerFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">选择客户</text>
				<text class="popup-close" @click="closeCustomerFilterPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 搜索框 -->
				<view class="search-box">
					<input
						class="search-input"
						v-model="customerSearchKeyword"
						placeholder="搜索客户名称"
						@input="searchCustomers"
					/>
				</view>

				<!-- 客户列表 -->
				<scroll-view class="customer-list" scroll-y>
					<!-- 全部客户选项 -->
					<view
						class="customer-item"
						:class="{ selected: !selectedCustomerId }"
						@click="selectCustomerFilter(null)"
					>
						<text class="customer-item-name">全部客户</text>
					</view>

					<view
						class="customer-item"
						v-for="customer in filteredCustomerList"
						:key="customer._id"
						:class="{ selected: selectedCustomerId === customer._id }"
						@click="selectCustomerFilter(customer)"
					>
						<text class="customer-item-name">{{ customer.name }}</text>
						<text class="customer-item-phone" v-if="customer.phone">{{ customer.phone }}</text>
					</view>

					<view class="empty-list" v-if="filteredCustomerList.length === 0">
						<text class="empty-text">暂无客户数据</text>
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
				<!-- 销售类型筛选 -->
				<view class="filter-group">
					<text class="filter-label">销售类型</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterSaleType === '' }"
							@click="filterSaleType = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option wholesale"
							:class="{ active: filterSaleType === 'wholesale' }"
							@click="filterSaleType = 'wholesale'"
						>
							<text class="option-text">批发</text>
						</view>
						<view
							class="filter-option retail"
							:class="{ active: filterSaleType === 'retail' }"
							@click="filterSaleType = 'retail'"
						>
							<text class="option-text">零售</text>
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
							class="filter-option partial"
							:class="{ active: filterPaymentStatus === 'partial' }"
							@click="filterPaymentStatus = 'partial'"
						>
							<text class="option-text">部分</text>
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
				<text class="popup-title">订单详情</text>
				<text class="popup-close" @click="closeDetailPopup">×</text>
			</view>

			<scroll-view class="popup-content" scroll-y v-if="currentOrder">
				<!-- 订单基本信息 -->
				<view class="detail-section">
					<view class="detail-row">
						<text class="detail-label">订单号</text>
						<text class="detail-value">{{ currentOrder.order_no }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">客户</text>
						<text class="detail-value">{{ currentOrder.customer_name || '散客' }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">销售类型</text>
						<text class="detail-value">{{ currentOrder.sale_type === 'wholesale' ? '批发' : '零售' }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">下单时间</text>
						<text class="detail-value">{{ formatDateTime(currentOrder.create_time) }}</text>
					</view>
					<view class="detail-row" v-if="currentOrder.remark">
						<text class="detail-label">备注</text>
						<text class="detail-value">{{ currentOrder.remark }}</text>
					</view>
				</view>

				<!-- 商品明细 -->
				<view class="detail-section">
					<text class="section-title">商品明细</text>
					<view class="item-list">
						<view class="item-row" v-for="(item, index) in currentOrder.order_items" :key="index">
							<view class="item-info">
								<text class="item-name">{{ item.product_name }}</text>
								<view class="item-specs">
									<text class="spec-tag" v-if="item.spec">{{ item.spec }}</text>
									<text class="spec-tag" v-if="item.grade">{{ item.grade }}</text>
								</view>
							</view>
							<view class="item-data">
								<text class="item-weight">{{ item.weight_jin }}斤 × ¥{{ fenToYuan(item.unit_price_fen) }}/斤</text>
								<text class="item-subtotal">¥{{ fenToYuan(item.subtotal_fen) }}</text>
							</view>
						</view>
					</view>
				</view>

				<!-- 付款信息 -->
				<view class="detail-section">
					<text class="section-title">付款信息</text>
					<view class="payment-info">
						<view class="payment-row">
							<text class="payment-label">总金额</text>
							<text class="payment-value">¥{{ fenToYuan(currentOrder.total_amount_fen) }}</text>
						</view>
						<view class="payment-row">
							<text class="payment-label">总成本</text>
							<text class="payment-value">¥{{ fenToYuan(currentOrder.total_cost_fen) }}</text>
						</view>
						<view class="payment-row">
							<text class="payment-label">毛利</text>
							<text class="payment-value profit">¥{{ fenToYuan(currentOrder.total_profit_fen) }}</text>
						</view>
						<view class="payment-row">
							<text class="payment-label">已收款</text>
							<text class="payment-value">¥{{ fenToYuan(currentOrder.paid_amount_fen) }}</text>
						</view>
						<view class="payment-row" v-if="currentOrder.debt_amount_fen > 0">
							<text class="payment-label">欠款</text>
							<text class="payment-value debt">¥{{ fenToYuan(currentOrder.debt_amount_fen) }}</text>
						</view>
						<view class="payment-row">
							<text class="payment-label">付款状态</text>
							<view class="payment-tag" :class="currentOrder.payment_status">
								{{ getPaymentStatusText(currentOrder.payment_status) }}
							</view>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="detail-actions" v-if="currentOrder.status === 'completed'">
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
import { salesAPI, customerAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 日期筛选选项
const dateFilterOptions = [
	{ label: '今天', value: 'today' },
	{ label: '昨天', value: 'yesterday' },
	{ label: '本周', value: 'week' },
	{ label: '本月', value: 'month' },
	{ label: '全部', value: 'all' }
]
const dateFilterIndex = ref(0) // 当前选中的日期筛选索引

// 客户筛选
const showCustomerFilterPopup = ref(false) // 是否显示客户筛选弹窗
const customerList = ref([]) // 客户列表
const customerSearchKeyword = ref('') // 客户搜索关键词
const selectedCustomerId = ref('') // 选中的客户ID
const selectedCustomerName = ref('') // 选中的客户名称

// 更多筛选
const showMoreFilterPopup = ref(false) // 是否显示更多筛选弹窗
const filterSaleType = ref('') // 销售类型筛选
const filterPaymentStatus = ref('') // 付款状态筛选

// 订单列表
const orderList = ref([]) // 订单列表

// 分页相关
const currentPage = ref(0) // 当前页码
const pageSize = 20 // 每页条数
const totalCount = ref(0) // 总数量
const hasMore = ref(true) // 是否有更多数据

// 加载状态
const isLoading = ref(false) // 是否正在加载
const isRefreshing = ref(false) // 是否正在刷新

// 统计数据
const totalSalesFen = ref(0) // 总销售额（分）
const totalProfitFen = ref(0) // 总毛利（分）
const totalDebtFen = ref(0) // 总欠款（分）

// 订单详情
const showDetailPopup = ref(false) // 是否显示详情弹窗
const currentOrder = ref(null) // 当前查看的订单

// ==================== 计算属性 ====================

// 过滤后的客户列表
const filteredCustomerList = computed(() => {
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

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterSaleType.value !== '' || filterPaymentStatus !== ''
})

// ==================== 方法 ====================

/**
 * 获取日期范围
 * @param {String} filterValue - 筛选值
 * @returns {Object} 包含 startTime 和 endTime 的对象
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
			weekStart.setDate(weekStart.getDate() - weekStart.getDay() + 1) // 周一
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
 * @param {Boolean} isRefresh - 是否是刷新操作
 */
const loadOrderList = async (isRefresh = false) => {
	if (isLoading.value) return

	try {
		isLoading.value = true

		// 如果是刷新，重置分页
		if (isRefresh) {
			currentPage.value = 0
			orderList.value = []
		}

		// 构建请求参数（传入筛选条件）
		const dateValue = dateFilterOptions[dateFilterIndex.value].value
		const { startTime, endTime } = getDateRange(dateValue)

		// 调用本地 API 获取销售订单历史
		// 注意：start_date/end_date 格式需与后端约定一致（ISO字符串或时间戳）
		const params = {
			page: currentPage.value,
			page_size: pageSize,
			order_by_field: 'create_time',
			order_by_direction: 'desc'
		}
		if (startTime !== null) {
			params.start_date = startTime
			params.end_date = endTime
		}
		if (selectedCustomerId.value) {
			params.customer_id = selectedCustomerId.value
		}
		if (filterSaleType.value) {
			params.sale_type = filterSaleType.value
		}
		if (filterPaymentStatus.value) {
			params.payment_status = filterPaymentStatus.value
		}

		const res = await salesAPI.history(params)

		if (res.code === 0) {
			const dataList = res.data.list || []

			if (isRefresh) {
				orderList.value = dataList
			} else {
				orderList.value = [...orderList.value, ...dataList]
			}

			// 更新分页状态
			hasMore.value = dataList.length >= pageSize
			currentPage.value++

			// 更新统计数据（从 API 返回的 data 中获取聚合信息，或客户端计算）
			if (isRefresh) {
				totalCount.value = res.data.total || dataList.length
				totalSalesFen.value = res.data.totalAmountFen || 0
				totalProfitFen.value = res.data.totalProfitFen || 0
				totalDebtFen.value = res.data.totalDebtFen || 0
			}
		} else {
			showError(res.message || '加载订单失败')
		}

	} catch (error) {
		showError('加载订单失败：' + error.message)
		console.error('加载订单失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 加载统计数据（已整合到 loadOrderList 中，统计数据从 salesAPI.history 返回的 data 中获取）
 * 若后端 API 不支持统计聚合，需要单独调用统计端点
 * TODO: 如果 salesAPI.history 不返回 totalAmountFen/totalProfitFen/totalDebtFen，
 *       需要在后端新增 salesAPI.statistics(params) 端点
 */
const loadStatistics = async () => {
	try {
		// 统计数据已在 loadOrderList 中通过 API 响应直接获取
		// 此处保留函数占位，避免旧代码调用报错
		console.log('loadStatistics: 统计数据已由 loadOrderList 中的 API 响应处理')
	} catch (error) {
		console.error('加载统计数据失败：', error)
	}
}

/**
 * 加载客户列表（通过本地 API 查询）
 */
const loadCustomerList = async () => {
	try {
		// 调用本地 API 获取客户列表
		const res = await customerAPI.list({
			page: 0,
			page_size: 200,
			order_by_field: 'name',
			order_by_direction: 'asc'
		})

		console.log('历史-客户列表API返回:', res)

		if (res.code === 0) {
			customerList.value = res.data.list || []
		}
	} catch (error) {
		console.error('加载客户列表失败：', error)
	}
}

/**
 * 搜索客户
 */
let customerSearchTimer = null
const searchCustomers = () => {
	if (customerSearchTimer) clearTimeout(customerSearchTimer)
	customerSearchTimer = setTimeout(() => {
		// 计算属性会自动过滤
	}, 300)
}

/**
 * 日期筛选变化
 * @param {Object} e - 事件对象
 */
const onDateFilterChange = (e) => {
	dateFilterIndex.value = e.detail.value
	loadOrderList(true)
}

/**
 * 关闭客户筛选弹窗
 */
const closeCustomerFilterPopup = () => {
	showCustomerFilterPopup.value = false
}

/**
 * 选择客户筛选
 * @param {Object} customer - 客户对象，null表示全部客户
 */
const selectCustomerFilter = (customer) => {
	if (customer) {
		selectedCustomerId.value = customer._id
		selectedCustomerName.value = customer.name
	} else {
		selectedCustomerId.value = ''
		selectedCustomerName.value = ''
	}
	closeCustomerFilterPopup()
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
	filterSaleType.value = ''
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
 * 获取付款状态文本
 * @param {String} status - 付款状态
 * @returns {String} 状态文本
 */
const getPaymentStatusText = (status) => {
	const statusMap = {
		'unpaid': '未付',
		'partial': '部分',
		'paid': '已付'
	}
	return statusMap[status] || status
}

/**
 * 显示订单详情
 * @param {Object} order - 订单对象
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

	// 确认操作
	const confirmed = await showConfirm('确定要作废该订单吗？作废后不可恢复。', '作废订单确认')
	if (!confirmed) return

	try {
		showLoading('正在作废...')

		// TODO: 当前 salesAPI 没有 cancel 方法，需要后端提供 salesAPI.cancel(id) 端点
		// 原有 uniCloud 调用已移除，待后端实现后启用以下代码：
		// const res = await salesAPI.cancel(currentOrder.value._id)
		// if (res.code === 0) { ... }
		//
		// 临时替代方案：直接提示用户该功能暂不可用
		showError('订单作废功能暂不可用，请联系管理员')
		return
	} catch (error) {
		hideLoading()
		showError('作废失败：' + error.message)
		console.error('作废订单失败：', error)
	}
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时获取数据
	loadOrderList(true)
	loadCustomerList()
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

.customer-filter {
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

	&.profit {
		color: #4CAF50;
	}

	&.debt {
		color: #FF9800;
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
	gap: 15rpx;
}

.order-no {
	font-size: 28rpx;
	color: #666666;
}

.type-tag {
	font-size: 22rpx;
	padding: 6rpx 16rpx;
	border-radius: 6rpx;

	&.wholesale {
		background-color: #E3F2FD;
		color: #4A90E2;
	}

	&.retail {
		background-color: #FFF8E1;
		color: #FF9800;
	}
}

.payment-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

	&.unpaid {
		background-color: #FFEBE9;
		color: #F44336;
	}

	&.partial {
		background-color: #FFF8E1;
		color: #FF9800;
	}

	&.paid {
		background-color: #E8F5E9;
		color: #4CAF50;
	}
}

.card-customer {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;
}

.customer-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.customer-phone {
	font-size: 26rpx;
	color: #999999;
	margin-left: 15rpx;
}

.card-amount {
	margin-bottom: 20rpx;
}

.amount-main {
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
	font-size: 40rpx;
	color: #FF5722;
	font-weight: 600;
}

.amount-sub {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

.profit-text {
	font-size: 26rpx;
	color: #4CAF50;
}

.debt-text {
	font-size: 26rpx;
	color: #FF9800;
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

.weight-text {
	font-size: 26rpx;
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

/* ==================== 客户筛选弹窗 ==================== */
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

.customer-item-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 500;
}

.customer-item-phone {
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

	&.wholesale.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}

	&.retail.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.unpaid.active {
		background-color: #FFEBE9;
		border-color: #F44336;
	}

	&.partial.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
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

	.filter-option.wholesale.active & {
		color: #4A90E2;
	}

	.filter-option.retail.active & {
		color: #FF9800;
	}

	.filter-option.unpaid.active & {
		color: #F44336;
	}

	.filter-option.partial.active & {
		color: #FF9800;
	}

	.filter-option.paid.active & {
		color: #4CAF50;
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
}

/* 商品明细列表 */
.item-list {
	//
}

.item-row {
	display: flex;
	justify-content: space-between;
	padding: 20rpx 0;
	border-bottom: 1rpx solid #F5F5F5;

	&:last-child {
		border-bottom: none;
	}
}

.item-info {
	flex: 1;
}

.item-name {
	font-size: 28rpx;
	color: #333333;
	font-weight: 500;
	display: block;
	margin-bottom: 10rpx;
}

.item-specs {
	display: flex;
	gap: 10rpx;
}

.spec-tag {
	font-size: 22rpx;
	color: #4A90E2;
	background-color: #E3F2FD;
	padding: 4rpx 12rpx;
	border-radius: 6rpx;
}

.item-data {
	text-align: right;
}

.item-weight {
	font-size: 26rpx;
	color: #666666;
	display: block;
	margin-bottom: 8rpx;
}

.item-subtotal {
	font-size: 30rpx;
	color: #FF5722;
	font-weight: 600;
}

/* 付款信息 */
.payment-info {
	//
}

.payment-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.payment-label {
	font-size: 28rpx;
	color: #666666;
}

.payment-value {
	font-size: 28rpx;
	color: #333333;

	&.profit {
		color: #4CAF50;
		font-weight: 600;
	}

	&.debt {
		color: #FF9800;
		font-weight: 600;
	}
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
