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
					<text class="stat-value weight">{{ totalInputWeightJin.toFixed(2) }}</text>
					<text class="stat-label">总投入 (斤)</text>
				</view>
				<view class="stat-item">
					<text class="stat-value output">{{ totalOutputWeightJin.toFixed(2) }}</text>
					<text class="stat-label">总出品 (斤)</text>
				</view>
			</view>
			<view class="stat-row">
				<view class="stat-item">
					<text class="stat-value">{{ totalCount }}</text>
					<text class="stat-label">订单数</text>
				</view>
				<view class="stat-item">
					<text class="stat-value loss">{{ avgLossRate }}%</text>
					<text class="stat-label">平均损耗</text>
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
				<text class="empty-text">暂无加工记录</text>
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

					<!-- 商品信息 -->
					<view class="card-product">
						<text class="product-name">{{ order.product_name }}</text>
						<view class="product-tags">
							<text class="spec-tag" v-if="order.spec">{{ order.spec }}</text>
							<text class="grade-tag" v-if="order.grade">{{ order.grade }}</text>
						</view>
					</view>

					<!-- 加工数据 -->
					<view class="card-content">
						<view class="data-row">
							<view class="data-item">
								<text class="data-label">投入重量</text>
								<text class="data-value">{{ order.input_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
							</view>
							<view class="data-item">
								<text class="data-label">出品重量</text>
								<text class="data-value output">{{ order.output_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
							</view>
						</view>
						<view class="loss-row">
							<text class="loss-label">损耗率</text>
							<text class="loss-value" :class="getLossRateClass(order)">
								{{ calculateLossRate(order) }}%
							</text>
						</view>
					</view>

					<!-- 时间信息 -->
					<view class="card-footer">
						<text class="time-text">{{ formatDateTime(order.create_time, 'MM-DD HH:mm') }}</text>
						<view class="payment-status" v-if="order.stock_in_status">
							<text class="status-icon">{{ order.stock_in_status === 'completed' ? '📦' : '⏳' }}</text>
							<text class="status-text">{{ order.stock_in_status === 'completed' ? '已入库' : '待入库' }}</text>
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

		<!-- ==================== 半屏弹窗：更多筛选 ==================== -->
		<view class="popup-mask" v-if="showMoreFilterPopup" @click="closeMoreFilterPopup"></view>
		<view class="popup-container more-filter-popup" :class="{ show: showMoreFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">筛选</text>
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
							:class="{ active: filterStatus === 'processing' }"
							@click="filterStatus = 'processing'"
						>
							<text class="option-text">加工中</text>
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

				<!-- 入库状态筛选 -->
				<view class="filter-group">
					<text class="filter-label">入库状态</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterStockInStatus === '' }"
							@click="filterStockInStatus = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option pending"
							:class="{ active: filterStockInStatus === 'pending' }"
							@click="filterStockInStatus = 'pending'"
						>
							<text class="option-text">待入库</text>
						</view>
						<view
							class="filter-option completed"
							:class="{ active: filterStockInStatus === 'completed' }"
							@click="filterStockInStatus = 'completed'"
						>
							<text class="option-text">已入库</text>
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
				<text class="popup-title">加工单详情</text>
				<text class="popup-close" @click="closeDetailPopup">×</text>
			</view>

			<scroll-view class="popup-content" scroll-y v-if="currentOrder">
				<!-- 加工单基本信息 -->
				<view class="detail-section">
					<view class="detail-row">
						<text class="detail-label">加工单号</text>
						<text class="detail-value">{{ currentOrder.order_no }}</text>
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
							<text class="detail-label">商品名称</text>
							<text class="detail-value">{{ currentOrder.product_name }}</text>
						</view>
						<view class="detail-row" v-if="currentOrder.spec">
							<text class="detail-label">规格</text>
							<text class="detail-value">{{ currentOrder.spec }}</text>
						</view>
						<view class="detail-row" v-if="currentOrder.grade">
							<text class="detail-label">等级</text>
							<text class="detail-value">{{ currentOrder.grade }}</text>
						</view>
						<view class="detail-row" v-if="currentOrder.origin">
							<text class="detail-label">产地</text>
							<text class="detail-value">{{ currentOrder.origin }}</text>
						</view>
					</view>
				</view>

				<!-- 加工数据 -->
				<view class="detail-section">
					<text class="section-title">加工数据</text>
					<view class="processing-data">
						<view class="data-grid">
							<view class="data-grid-item">
								<text class="grid-label">投入重量</text>
								<text class="grid-value">{{ currentOrder.input_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
							</view>
							<view class="data-grid-item">
								<text class="grid-label">出品重量</text>
								<text class="grid-value output">{{ currentOrder.output_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
							</view>
							<view class="data-grid-item">
								<text class="grid-label">损耗重量</text>
								<text class="grid-value loss">{{ (currentOrder.loss_weight_jin || 0).toFixed(2) }} 斤</text>
							</view>
							<view class="data-grid-item">
								<text class="grid-label">损耗率</text>
								<text class="grid-value" :class="getLossRateClass(currentOrder)">
									{{ calculateLossRate(currentOrder) }}%
								</text>
							</view>
						</view>
					</view>
				</view>

				<!-- 批次信息 -->
				<view class="detail-section" v-if="currentOrder.batch_list && currentOrder.batch_list.length > 0">
					<text class="section-title">批次信息</text>
					<view class="batch-list">
						<view class="batch-item" v-for="(batch, index) in currentOrder.batch_list" :key="index">
							<view class="batch-header">
								<text class="batch-index">{{ index + 1 }}</text>
								<text class="batch-no">{{ batch.batch_id }}</text>
							</view>
							<view class="batch-detail">
								<view class="detail-row">
									<text class="detail-label">入库重量</text>
									<text class="detail-value">{{ batch.stock_in_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
								</view>
								<view class="detail-row">
									<text class="detail-label">单位成本</text>
									<text class="detail-value">¥{{ fenToYuan(batch.unit_cost_fen) }}/斤</text>
								</view>
							</view>
						</view>
					</view>
				</view>

				<!-- 入库信息 -->
				<view class="detail-section" v-if="currentOrder.stock_in_status === 'completed'">
					<text class="section-title">入库信息</text>
					<view class="stock-in-info">
						<view class="detail-row">
							<text class="detail-label">入库时间</text>
							<text class="detail-value">{{ formatDateTime(currentOrder.stock_in_time) }}</text>
						</view>
						<view class="detail-row">
							<text class="detail-label">入库重量</text>
							<text class="detail-value">{{ currentOrder.output_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="detail-actions" v-if="currentOrder.status === 'processing'">
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
import { processingAPI } from '@/utils/api.js'

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

// 更多筛选
const showMoreFilterPopup = ref(false)
const filterStatus = ref('')
const filterStockInStatus = ref('')

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
const totalInputWeightJin = ref(0)
const totalOutputWeightJin = ref(0)
const avgLossRate = ref(0)

// 订单详情
const showDetailPopup = ref(false)
const currentOrder = ref(null)

// ==================== 计算属性 ====================

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterStatus.value !== '' || filterStockInStatus.value !== ''
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

		if (isRefresh) {
			currentPage.value = 0
			orderList.value = []
		}

		// 构建请求参数
		const dateValue = dateFilterOptions[dateFilterIndex.value].value
		const { startTime, endTime } = getDateRange(dateValue)

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
		if (filterStatus.value) {
			params.status = filterStatus.value
		}
		if (filterStockInStatus.value) {
			params.stock_in_status = filterStockInStatus.value
		}

		// 调用本地 API 获取加工订单历史
		const res = await processingAPI.history(params)

		if (res.code === 0) {
			const dataList = res.data.list || []

			if (isRefresh) {
				orderList.value = dataList
			} else {
				orderList.value = [...orderList.value, ...dataList]
			}

			hasMore.value = dataList.length >= pageSize
			currentPage.value++

			// 统计数据从 API 响应中获取
			if (isRefresh) {
				totalCount.value = res.data.total || dataList.length
				totalInputWeightJin.value = res.data.totalInputWeightJin || 0
				totalOutputWeightJin.value = res.data.totalOutputWeightJin || 0
				avgLossRate.value = res.data.avgLossRate || 0
			}
		} else {
			showError(res.message || '加载加工订单失败')
		}

	} catch (error) {
		showError('加载加工订单失败：' + error.message)
		console.error('加载加工订单失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 加载统计数据（已整合到 loadOrderList 中，统计数据从 processingAPI.history 返回的 data 中获取）
 * TODO: 如果 processingAPI.history 不返回 totalInputWeightJin/totalOutputWeightJin/avgLossRate，
 *       需要在后端新增 processingAPI.statistics(params) 端点
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
 * 日期筛选变化
 */
const onDateFilterChange = (e) => {
	dateFilterIndex.value = e.detail.value
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
	filterStockInStatus.value = ''
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
		'processing': '加工中',
		'completed': '已完成',
		'cancelled': '已取消'
	}
	return statusMap[status] || status
}

/**
 * 计算损耗率
 */
const calculateLossRate = (order) => {
	const input = order.input_weight_jin || 0
	const output = order.output_weight_jin || 0
	if (input <= 0) return '0.0'
	const loss = input - output
	const rate = (loss / input) * 100
	return rate.toFixed(1)
}

/**
 * 获取损耗率样式类
 */
const getLossRateClass = (order) => {
	const rate = parseFloat(calculateLossRate(order))
	if (rate <= 5) return 'low'
	if (rate <= 10) return 'normal'
	return 'high'
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

		// 调用本地 API 作废加工订单
		const res = await processingAPI.cancel(currentOrder.value._id)

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

// ==================== 生命周期 ====================

onMounted(() => {
	loadOrderList(true)
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

.more-filter-btn {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	height: 70rpx;
	padding: 0 20rpx;
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

	&.weight {
		color: #4A90E2;
	}

	&.output {
		color: #4CAF50;
	}

	&.loss {
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
}

.order-no {
	font-size: 28rpx;
	color: #666666;
}

.status-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

	&.processing {
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

.card-product {
	margin-bottom: 20rpx;
}

.product-name {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	display: block;
	margin-bottom: 10rpx;
}

.product-tags {
	display: flex;
	gap: 10rpx;
}

.spec-tag,
.grade-tag {
	font-size: 24rpx;
	padding: 6rpx 16rpx;
	border-radius: 8rpx;
}

.spec-tag {
	background-color: #E3F2FD;
	color: #4A90E2;
}

.grade-tag {
	background-color: #F3E5F5;
	color: #9C27B0;
}

.card-content {
	margin-bottom: 20rpx;
}

.data-row {
	display: flex;
	margin-bottom: 15rpx;
}

.data-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.data-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 8rpx;
}

.data-value {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;

	&.output {
		color: #4CAF50;
	}
}

.loss-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 15rpx 20rpx;
	background-color: #F8F8F8;
	border-radius: 8rpx;
}

.loss-label {
	font-size: 26rpx;
	color: #666666;
}

.loss-value {
	font-size: 32rpx;
	font-weight: 600;

	&.low {
		color: #4CAF50;
	}

	&.normal {
		color: #FF9800;
	}

	&.high {
		color: #F44336;
	}
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

.status-icon {
	font-size: 28rpx;
}

.status-text {
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
}

.option-text {
	font-size: 28rpx;
	color: #333333;

	.filter-option.active & {
		color: #4A90E2;
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

/* 加工数据 */
.processing-data {
	//
}

.data-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 20rpx;
}

.data-grid-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.grid-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 10rpx;
}

.grid-value {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;

	&.output {
		color: #4CAF50;
	}

	&.loss {
		color: #FF9800;
	}
}

/* 批次信息 */
.batch-list {
	//
}

.batch-item {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 20rpx;
	margin-bottom: 15rpx;
}

.batch-header {
	display: flex;
	align-items: center;
	margin-bottom: 15rpx;
}

.batch-index {
	width: 48rpx;
	height: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	color: #FFFFFF;
	border-radius: 50%;
	font-size: 26rpx;
	font-weight: 600;
	margin-right: 15rpx;
}

.batch-no {
	font-size: 28rpx;
	color: #666666;
}

.batch-detail {
	//
}

/* 入库信息 */
.stock-in-info {
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
