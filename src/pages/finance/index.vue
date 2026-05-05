<template>
	<view class="finance-container">
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
					<text class="stat-value income">¥{{ fenToYuan(totalIncomeFen) }}</text>
					<text class="stat-label">总收入</text>
				</view>
				<view class="stat-item">
					<text class="stat-value expense">¥{{ fenToYuan(totalExpenseFen) }}</text>
					<text class="stat-label">总支出</text>
				</view>
			</view>
			<view class="stat-row">
				<view class="stat-item">
					<text class="stat-value profit" :class="totalProfitFen >= 0 ? 'positive' : 'negative'">
						¥{{ fenToYuan(Math.abs(totalProfitFen)) }}
					</text>
					<text class="stat-label">{{ totalProfitFen >= 0 ? '净利润' : '净亏损' }}</text>
				</view>
				<view class="stat-item">
					<text class="stat-value count">{{ totalCount }}</text>
					<text class="stat-label">流水数</text>
				</view>
			</view>
		</view>

		<!-- ==================== 流水列表区域 ==================== -->
		<scroll-view
			class="list-section"
			scroll-y
			:refresher-enabled="true"
			:refresher-triggered="isRefreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<!-- 空状态 -->
			<view class="empty-state" v-if="ledgerList.length === 0 && !isLoading">
				<text class="empty-icon">📊</text>
				<text class="empty-text">暂无财务流水</text>
				<text class="empty-hint">下拉刷新或修改筛选条件</text>
			</view>

			<!-- 流水列表 -->
			<view class="ledger-list" v-else>
				<view
					class="ledger-card"
					v-for="ledger in ledgerList"
					:key="ledger._id"
					@click="showLedgerDetail(ledger)"
				>
					<!-- 卡片头部：类型图标 + 金额 -->
					<view class="card-header">
						<view class="type-icon" :class="ledger.type">
							{{ ledger.type === 'income' ? '💰' : '💸' }}
						</view>
						<view class="amount-wrapper">
							<text class="amount-value" :class="ledger.type">
								{{ ledger.type === 'income' ? '+' : '-' }}¥{{ fenToYuan(ledger.amount_fen) }}
							</text>
						</view>
					</view>

					<!-- 流水信息 -->
					<view class="card-content">
						<view class="info-row">
							<text class="info-label">业务类型</text>
							<text class="info-value">{{ getBusinessTypeText(ledger.business_type) }}</text>
						</view>
						<view class="info-row">
							<text class="info-label">关联单号</text>
							<text class="info-value link" v-if="ledger.related_order_no" @click.stop="viewRelatedOrder(ledger)">
								{{ ledger.related_order_no }} >
							</text>
							<text class="info-value" v-else>-</text>
						</view>
						<view class="info-row">
							<text class="info-label">备注</text>
							<text class="info-value remark">{{ ledger.remark || '-' }}</text>
						</view>
					</view>

					<!-- 时间信息 -->
					<view class="card-footer">
						<text class="time-text">{{ formatDateTime(ledger.create_time, 'MM-DD HH:mm:ss') }}</text>
						<view class="balance-wrapper" v-if="ledger.balance_after_fen">
							<text class="balance-label">余额</text>
							<text class="balance-value">¥{{ fenToYuan(ledger.balance_after_fen) }}</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 加载更多 -->
			<view class="load-more" v-if="ledgerList.length > 0">
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
				<!-- 收支类型筛选 -->
				<view class="filter-group">
					<text class="filter-label">收支类型</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterType === '' }"
							@click="filterType = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option income"
							:class="{ active: filterType === 'income' }"
							@click="filterType = 'income'"
						>
							<text class="option-text">收入</text>
						</view>
						<view
							class="filter-option expense"
							:class="{ active: filterType === 'expense' }"
							@click="filterType = 'expense'"
						>
							<text class="option-text">支出</text>
						</view>
					</view>
				</view>

				<!-- 业务类型筛选 -->
				<view class="filter-group">
					<text class="filter-label">业务类型</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterBusinessType === '' }"
							@click="filterBusinessType = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option purchase"
							:class="{ active: filterBusinessType === 'purchase' }"
							@click="filterBusinessType = 'purchase'"
						>
							<text class="option-text">采购</text>
						</view>
						<view
							class="filter-option sales"
							:class="{ active: filterBusinessType === 'sales' }"
							@click="filterBusinessType = 'sales'"
						>
							<text class="option-text">销售</text>
						</view>
						<view
							class="filter-option processing"
							:class="{ active: filterBusinessType === 'processing' }"
							@click="filterBusinessType = 'processing'"
						>
							<text class="option-text">加工</text>
						</view>
						<view
							class="filter-option receive"
							:class="{ active: filterBusinessType === 'receive' }"
							@click="filterBusinessType = 'receive'"
						>
							<text class="option-text">收款</text>
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

		<!-- ==================== 半屏弹窗：流水详情 ==================== -->
		<view class="popup-mask" v-if="showDetailPopup" @click="closeDetailPopup"></view>
		<view class="popup-container detail-popup" :class="{ show: showDetailPopup }">
			<view class="popup-header">
				<text class="popup-title">流水详情</text>
				<text class="popup-close" @click="closeDetailPopup">×</text>
			</view>

			<scroll-view class="popup-content" scroll-y v-if="currentLedger">
				<!-- 流水基本信息 -->
				<view class="detail-section">
					<view class="type-header" :class="currentLedger.type">
						<text class="type-icon">{{ currentLedger.type === 'income' ? '💰' : '💸' }}</text>
						<text class="type-text">{{ currentLedger.type === 'income' ? '收入' : '支出' }}</text>
					</view>
					
					<view class="amount-display" :class="currentLedger.type">
						<text class="amount-label">金额</text>
						<text class="amount-value">
							{{ currentLedger.type === 'income' ? '+' : '-' }}¥{{ fenToYuan(currentLedger.amount_fen) }}
						</text>
					</view>
				</view>

				<!-- 详细信息 -->
				<view class="detail-section">
					<view class="detail-row">
						<text class="detail-label">流水号</text>
						<text class="detail-value">{{ currentLedger.ledger_no }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">业务类型</text>
						<text class="detail-value">{{ getBusinessTypeText(currentLedger.business_type) }}</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">关联单号</text>
						<text class="detail-value link" v-if="currentLedger.related_order_no" @click="viewRelatedOrder(currentLedger)">
							{{ currentLedger.related_order_no }} >
						</text>
						<text class="detail-value" v-else>-</text>
					</view>
					<view class="detail-row">
						<text class="detail-label">创建时间</text>
						<text class="detail-value">{{ formatDateTime(currentLedger.create_time) }}</text>
					</view>
					<view class="detail-row" v-if="currentLedger.balance_after_fen">
						<text class="detail-label">账户余额</text>
						<text class="detail-value">¥{{ fenToYuan(currentLedger.balance_after_fen) }}</text>
					</view>
					<view class="detail-row" v-if="currentLedger.remark">
						<text class="detail-label">备注</text>
						<text class="detail-value remark">{{ currentLedger.remark }}</text>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="detail-actions">
					<view class="action-btn" @click="closeDetailPopup">
						<text class="btn-text">关闭</text>
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, formatDateTime, showLoading, hideLoading, showError } from '@/utils/util.js'
import { financeAPI } from '@/utils/api.js'

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
const filterType = ref('')
const filterBusinessType = ref('')

// 流水列表
const ledgerList = ref([])

// 分页相关
const currentPage = ref(0)
const pageSize = 20
const totalCount = ref(0)
const hasMore = ref(true)

// 加载状态
const isLoading = ref(false)
const isRefreshing = ref(false)

// 统计数据
const totalIncomeFen = ref(0)
const totalExpenseFen = ref(0)
const totalProfitFen = ref(0)

// 流水详情
const showDetailPopup = ref(false)
const currentLedger = ref(null)

// ==================== 计算属性 ====================

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterType.value !== '' || filterBusinessType.value !== ''
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
 * 加载流水列表
 */
const loadLedgerList = async (isRefresh = false) => {
	if (isLoading.value) return

	try {
		isLoading.value = true

		if (isRefresh) {
			currentPage.value = 0
			ledgerList.value = []
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
		if (filterType.value) {
			params.type = filterType.value
		}
		if (filterBusinessType.value) {
			params.business_type = filterBusinessType.value
		}

		// 调用本地 API 获取财务流水
		const res = await financeAPI.ledgers(params)

		if (res.code === 0) {
			const dataList = res.data.list || []

			if (isRefresh) {
				ledgerList.value = dataList
			} else {
				ledgerList.value = [...ledgerList.value, ...dataList]
			}

			hasMore.value = dataList.length >= pageSize
			currentPage.value++

			// 统计数据从 API 响应中获取
			if (isRefresh) {
				totalCount.value = res.data.total || dataList.length
				totalIncomeFen.value = res.data.totalIncomeFen || 0
				totalExpenseFen.value = res.data.totalExpenseFen || 0
				totalProfitFen.value = res.data.totalProfitFen || 0
			}
		} else {
			showError(res.message || '加载财务流水失败')
		}

	} catch (error) {
		showError('加载财务流水失败：' + error.message)
		console.error('加载财务流水失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 加载统计数据（已整合到 loadLedgerList 中，统计数据从 financeAPI.ledgers 返回的 data 中获取）
 * TODO: 如果 financeAPI.ledgers 不返回 totalIncomeFen/totalExpenseFen/totalProfitFen，
 *       需要在后端新增 financeAPI.statistics(params) 端点
 */
const loadStatistics = async () => {
	try {
		// 统计数据已在 loadLedgerList 中通过 API 响应直接获取
		// 此处保留函数占位，避免旧代码调用报错
		console.log('loadStatistics: 统计数据已由 loadLedgerList 中的 API 响应处理')
	} catch (error) {
		console.error('加载统计数据失败：', error)
	}
}

/**
 * 日期筛选变化
 */
const onDateFilterChange = (e) => {
	dateFilterIndex.value = e.detail.value
	loadLedgerList(true)
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
	filterType.value = ''
	filterBusinessType.value = ''
}

/**
 * 应用更多筛选
 */
const applyMoreFilter = () => {
	closeMoreFilterPopup()
	loadLedgerList(true)
}

/**
 * 下拉刷新
 */
const onRefresh = () => {
	isRefreshing.value = true
	loadLedgerList(true)
}

/**
 * 加载更多
 */
const loadMore = () => {
	if (!hasMore.value || isLoading.value) return
	loadLedgerList(false)
}

/**
 * 获取业务类型文本
 */
const getBusinessTypeText = (businessType) => {
	const typeMap = {
		'purchase': '采购',
		'sales': '销售',
		'processing': '加工',
		'receive': '收款',
		'adjustment': '调整'
	}
	return typeMap[businessType] || businessType
}

/**
 * 显示流水详情
 */
const showLedgerDetail = (ledger) => {
	currentLedger.value = ledger
	showDetailPopup.value = true
}

/**
 * 关闭详情弹窗
 */
const closeDetailPopup = () => {
	showDetailPopup.value = false
	currentLedger.value = null
}

/**
 * 查看关联订单
 */
const viewRelatedOrder = (ledger) => {
	if (!ledger.related_order_no) return

	// 根据业务类型跳转到不同的订单详情页面
	switch (ledger.business_type) {
		case 'purchase':
			// 跳转到采购详情（待实现）
			showError('采购详情功能开发中')
			break
		case 'sales':
			// 跳转到销售详情
			uni.navigateTo({
				url: '/pages/sales/history'
			})
			break
		case 'processing':
			// 跳转到加工详情（待实现）
			showError('加工详情功能开发中')
			break
		default:
			showError('暂无详情')
	}
}

// ==================== 生命周期 ====================

onMounted(() => {
	loadLedgerList(true)
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.finance-container {
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

	&.income {
		color: #4CAF50;
	}

	&.expense {
		color: #F44336;
	}

	&.profit {
		&.positive {
			color: #4CAF50;
		}

		&.negative {
			color: #F44336;
		}
	}

	&.count {
		color: #4A90E2;
	}
}

.stat-label {
	font-size: 24rpx;
	color: #999999;
}

/* ==================== 流水列表区域 ==================== */
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

/* 流水列表 */
.ledger-list {
	padding-bottom: 30rpx;
}

.ledger-card {
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

.type-icon {
	width: 80rpx;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #F8F8F8;
	border-radius: 50%;
	font-size: 40rpx;

	&.income {
		background-color: #E8F5E9;
	}

	&.expense {
		background-color: #FFEBE9;
	}
}

.amount-wrapper {
	text-align: right;
}

.amount-value {
	font-size: 40rpx;
	font-weight: 600;

	&.income {
		color: #4CAF50;
	}

	&.expense {
		color: #F44336;
	}
}

.card-content {
	margin-bottom: 20rpx;
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
	font-size: 26rpx;
	color: #999999;
	width: 160rpx;
}

.info-value {
	font-size: 28rpx;
	color: #333333;
	flex: 1;

	&.link {
		color: #4A90E2;
		text-decoration: underline;
	}

	&.remark {
		color: #666666;
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

.balance-wrapper {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.balance-label {
	font-size: 24rpx;
	color: #999999;
}

.balance-value {
	font-size: 28rpx;
	color: #4A90E2;
	font-weight: 600;
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

	&.income.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}

	&.expense.active {
		background-color: #FFEBE9;
		border-color: #F44336;
	}

	&.purchase.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}

	&.sales.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.processing.active {
		background-color: #F3E5F5;
		border-color: #9C27B0;
	}

	&.receive.active {
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

/* ==================== 流水详情弹窗 ==================== */
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

.type-header {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	margin-bottom: 20rpx;
	gap: 15rpx;

	&.income {
		background-color: #E8F5E9;
	}

	&.expense {
		background-color: #FFEBE9;
	}
}

.type-icon {
	font-size: 48rpx;
}

.type-text {
	font-size: 32rpx;
	font-weight: 600;

	&.income {
		color: #4CAF50;
	}

	&.expense {
		color: #F44336;
	}
}

.amount-display {
	text-align: center;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;

	&.income {
		background-color: #E8F5E9;
	}

	&.expense {
		background-color: #FFEBE9;
	}
}

.amount-label {
	font-size: 26rpx;
	color: #999999;
	display: block;
	margin-bottom: 10rpx;
}

.amount-value {
	font-size: 48rpx;
	font-weight: 600;

	&.income {
		color: #4CAF50;
	}

	&.expense {
		color: #F44336;
	}
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

	&.link {
		color: #4A90E2;
		text-decoration: underline;
	}

	&.remark {
		color: #666666;
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
	background-color: #4A90E2;
	border-radius: 12rpx;
}

.btn-text {
	font-size: 32rpx;
	color: #FFFFFF;
	font-weight: 600;
}
</style>
