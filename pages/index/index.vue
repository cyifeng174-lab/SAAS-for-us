<template>
	<view class="container">
		<!-- ==================== 今日数据卡片 ==================== -->
		<view class="section today-stats">
			<view class="section-title">今日数据</view>
			<view class="stats-grid">
				<!-- 今日销售额 - 最突出显示 -->
				<view class="stat-card main-stat" @click="goToSalesHistory">
					<view class="stat-label">今日销售额</view>
					<view class="stat-value primary">¥{{ todayStats.totalAmount }}</view>
					<view class="stat-sub">{{ todayStats.orderCount }}笔订单</view>
				</view>
				
				<!-- 今日毛利 -->
				<view class="stat-card" @click="goToSalesHistory">
					<view class="stat-label">今日毛利</view>
					<view class="stat-value success">¥{{ todayStats.totalProfit }}</view>
				</view>
				
				<!-- 今日新增欠款 -->
				<view class="stat-card" @click="goToCustomerList">
					<view class="stat-label">今日新增欠款</view>
					<view class="stat-value warning">¥{{ todayStats.totalDebt }}</view>
				</view>
			</view>
		</view>

		<!-- ==================== 待办提醒区域 ==================== -->
		<view class="section todo-reminder" v-if="hasReminders">
			<view class="section-title">待办提醒</view>
			
			<!-- 欠款超额客户提醒 -->
			<view class="reminder-card danger" v-if="overdueCustomers.length > 0" @click="goToCustomerList">
				<view class="reminder-icon">
					<text class="icon-text">!</text>
				</view>
				<view class="reminder-content">
					<view class="reminder-title">欠款超额提醒</view>
					<view class="reminder-desc">{{ overdueCustomers.length }}位客户欠款超额</view>
				</view>
				<view class="reminder-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 低库存提醒 -->
			<view class="reminder-card warning" v-if="lowStockItems.length > 0" @click="goToInventoryList">
				<view class="reminder-icon">
					<text class="icon-text">!</text>
				</view>
				<view class="reminder-content">
					<view class="reminder-title">低库存提醒</view>
					<view class="reminder-desc">{{ lowStockItems.length }}种商品库存不足</view>
				</view>
				<view class="reminder-arrow">
					<text>&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 快捷入口区域 ==================== -->
		<view class="section quick-entry">
			<view class="section-title">快捷入口</view>
			<view class="entry-grid">
				<!-- 扫码开单 - 预留功能 -->
				<view class="entry-item" @click="handleScanCode">
					<view class="entry-icon scan">
						<text class="icon-text">扫</text>
					</view>
					<view class="entry-label">扫码开单</view>
				</view>
				
				<!-- 快捷入库 -->
				<view class="entry-item" @click="goToProcessing">
					<view class="entry-icon inbound">
						<text class="icon-text">入</text>
					</view>
					<view class="entry-label">快捷入库</view>
				</view>
				
				<!-- 查看库存 -->
				<view class="entry-item" @click="goToInventoryList">
					<view class="entry-icon inventory">
						<text class="icon-text">库</text>
					</view>
					<view class="entry-label">查看库存</view>
				</view>
				
				<!-- 客户账本 -->
				<view class="entry-item" @click="goToCustomerList">
					<view class="entry-icon customer">
						<text class="icon-text">客</text>
					</view>
					<view class="entry-label">客户账本</view>
				</view>
			</view>
		</view>

		<!-- ==================== 最近订单区域 ==================== -->
		<view class="section recent-orders">
			<view class="section-header">
				<view class="section-title">最近订单</view>
				<view class="section-more" @click="goToSalesHistory">查看全部 &gt;</view>
			</view>
			
			<!-- 订单列表 -->
			<view class="order-list" v-if="recentOrders.length > 0">
				<view class="order-item" v-for="order in recentOrders" :key="order._id" @click="goToOrderDetail(order)">
					<view class="order-main">
						<view class="order-no">{{ order.order_no }}</view>
						<view class="order-customer">{{ order.customer_name }}</view>
					</view>
					<view class="order-info">
						<view class="order-amount">¥{{ formatAmount(order.total_amount_fen) }}</view>
						<view class="order-time">{{ formatOrderTime(order.create_time) }}</view>
					</view>
				</view>
			</view>
			
			<!-- 空状态 -->
			<view class="empty-state" v-else>
				<view class="empty-text">暂无订单记录</view>
				<view class="empty-action" @click="goToBilling">立即开单</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fenToYuan, formatDateTime, showError } from '@/utils/util.js'

// ==================== 响应式数据 ====================

// 今日统计数据
const todayStats = ref({
	totalAmount: '0.00',    // 今日销售额（元）
	totalProfit: '0.00',    // 今日毛利（元）
	totalDebt: '0.00',      // 今日新增欠款（元）
	orderCount: 0           // 今日订单数
})

// 欠款超额客户列表
const overdueCustomers = ref([])

// 低库存商品列表
const lowStockItems = ref([])

// 最近订单列表
const recentOrders = ref([])

// 加载状态
const loading = ref(false)

// ==================== 计算属性 ====================

// 是否有待办提醒
const hasReminders = computed(() => {
	return overdueCustomers.value.length > 0 || lowStockItems.value.length > 0
})

// ==================== 数据加载方法 ====================

/**
 * 获取今日统计数据
 * 查询 sales_orders 表，统计今日销售额、毛利、欠款、订单数
 */
const loadTodayStats = async () => {
	try {
		// 获取今日时间范围（0点到23:59:59）
		const today = new Date()
		const startOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0, 0).getTime()
		const endOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 23, 59, 59).getTime()
		
		// 使用 uniCloud DB 查询今日订单
		const db = uniCloud.database()
		const result = await db.collection('sales_orders')
			.where({
				create_time: db.command.gte(startOfDay).and(db.command.lte(endOfDay))
			})
			.field({
				total_amount_fen: true,
				total_profit_fen: true,
				total_debt_fen: true
			})
			.get()
		
		// 统计数据
		let totalAmountFen = 0
		let totalProfitFen = 0
		let totalDebtFen = 0
		
		if (result.data && result.data.length > 0) {
			result.data.forEach(order => {
				totalAmountFen += order.total_amount_fen || 0
				totalProfitFen += order.total_profit_fen || 0
				totalDebtFen += order.total_debt_fen || 0
			})
		}
		
		// 更新统计数据（分转元）
		todayStats.value = {
			totalAmount: fenToYuan(totalAmountFen),
			totalProfit: fenToYuan(totalProfitFen),
			totalDebt: fenToYuan(totalDebtFen),
			orderCount: result.data ? result.data.length : 0
		}
	} catch (error) {
		console.error('获取今日统计失败:', error)
		showError('获取今日统计失败')
	}
}

/**
 * 获取欠款超额客户
 * 查询 customers 表，找出欠款超过信用额度的客户
 */
const loadOverdueCustomers = async () => {
	try {
		const db = uniCloud.database()
		const dbCmd = db.command
		
		// 查询欠款超过信用额度的客户
		// 条件：total_debt_fen > credit_limit_fen 且 credit_limit_fen > 0
		const result = await db.collection('customers')
			.where({
				total_debt_fen: dbCmd.gt(0),
				credit_limit_fen: dbCmd.gt(0)
			})
			.field({
				name: true,
				total_debt_fen: true,
				credit_limit_fen: true
			})
			.get()
		
		// 筛选出真正超额的客户
		if (result.data) {
			overdueCustomers.value = result.data.filter(customer => {
				return customer.total_debt_fen > customer.credit_limit_fen
			})
		}
	} catch (error) {
		console.error('获取欠款超额客户失败:', error)
	}
}

/**
 * 获取低库存商品
 * 查询 inventories 表，找出库存低于预警值的商品
 */
const loadLowStockItems = async () => {
	try {
		const db = uniCloud.database()
		
		// 查询所有库存记录
		const result = await db.collection('inventories')
			.where({
				current_stock_jin: db.command.gt(0)  // 只查询有库存的
			})
			.field({
				product_name: true,
				current_stock_jin: true,
				warning_stock_jin: true
			})
			.get()
		
		// 筛选出低库存商品
		if (result.data) {
			lowStockItems.value = result.data.filter(item => {
				// 如果设置了预警值，且当前库存低于预警值
				if (item.warning_stock_jin && item.warning_stock_jin > 0) {
					return item.current_stock_jin < item.warning_stock_jin
				}
				return false
			})
		}
	} catch (error) {
		console.error('获取低库存商品失败:', error)
	}
}

/**
 * 获取最近订单
 * 查询 sales_orders 表，获取最近5笔订单
 */
const loadRecentOrders = async () => {
	try {
		const db = uniCloud.database()
		
		const result = await db.collection('sales_orders')
			.orderBy('create_time', 'desc')
			.limit(5)
			.field({
				_id: true,
				order_no: true,
				customer_name: true,
				total_amount_fen: true,
				create_time: true,
				order_status: true
			})
			.get()
		
		recentOrders.value = result.data || []
	} catch (error) {
		console.error('获取最近订单失败:', error)
	}
}

/**
 * 加载所有数据
 */
const loadAllData = async () => {
	if (loading.value) return
	
	loading.value = true
	uni.showLoading({ title: '加载中...', mask: true })
	
	try {
		// 并行加载所有数据
		await Promise.all([
			loadTodayStats(),
			loadOverdueCustomers(),
			loadLowStockItems(),
			loadRecentOrders()
		])
	} catch (error) {
		console.error('加载数据失败:', error)
	} finally {
		loading.value = false
		uni.hideLoading()
	}
}

// ==================== 格式化方法 ====================

/**
 * 格式化金额（分转元）
 * @param {Number} fen - 金额（分）
 * @returns {String} 金额（元）
 */
const formatAmount = (fen) => {
	return fenToYuan(fen)
}

/**
 * 格式化订单时间
 * @param {Number} timestamp - 时间戳
 * @returns {String} 格式化后的时间
 */
const formatOrderTime = (timestamp) => {
	if (!timestamp) return ''
	
	const now = new Date()
	const orderTime = new Date(timestamp)
	const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
	const orderDay = new Date(orderTime.getFullYear(), orderTime.getMonth(), orderTime.getDate())
	
	// 如果是今天，只显示时间
	if (orderDay.getTime() === today.getTime()) {
		return formatDateTime(timestamp, 'HH:mm')
	}
	
	// 如果是昨天
	const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
	if (orderDay.getTime() === yesterday.getTime()) {
		return '昨天 ' + formatDateTime(timestamp, 'HH:mm')
	}
	
	// 其他显示日期
	return formatDateTime(timestamp, 'MM-DD HH:mm')
}

// ==================== 页面跳转方法 ====================

/**
 * 跳转到销售历史页面
 */
const goToSalesHistory = () => {
	uni.navigateTo({
		url: '/pages/sales/history'
	})
}

/**
 * 跳转到客户列表页面
 */
const goToCustomerList = () => {
	uni.navigateTo({
		url: '/pages/customer/list'
	})
}

/**
 * 跳转到库存列表页面
 */
const goToInventoryList = () => {
	// 库存页面是 TabBar 页面，使用 switchTab
	uni.switchTab({
		url: '/pages/inventory/list'
	})
}

/**
 * 跳转到加工入库页面
 */
const goToProcessing = () => {
	uni.navigateTo({
		url: '/pages/processing/create'
	})
}

/**
 * 跳转到开单页面
 */
const goToBilling = () => {
	uni.switchTab({
		url: '/pages/sales/billing'
	})
}

/**
 * 跳转到订单详情
 * @param {Object} order - 订单对象
 */
const goToOrderDetail = (order) => {
	uni.navigateTo({
		url: `/pages/sales/history?order_id=${order._id}`
	})
}

/**
 * 扫码开单（预留功能）
 * 当前显示提示，后续可接入扫码功能
 */
const handleScanCode = () => {
	uni.showToast({
		title: '扫码功能开发中',
		icon: 'none',
		duration: 2000
	})
}

// ==================== 生命周期 ====================

// 页面加载时获取数据
onMounted(() => {
	loadAllData()
})

// 页面显示时刷新数据（从其他页面返回时）
onShow(() => {
	loadAllData()
})
</script>

<style lang="scss" scoped>
/* ==================== 容器样式 ==================== */
.container {
	min-height: 100vh;
	background-color: #F5F7FA;
	padding-bottom: 30rpx;
}

/* ==================== 通用区块样式 ==================== */
.section {
	background-color: #FFFFFF;
	margin: 20rpx 24rpx;
	border-radius: 16rpx;
	padding: 24rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.section-title {
	font-size: 32rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 24rpx;
}

.section-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 24rpx;
}

.section-more {
	font-size: 26rpx;
	color: #4A90E2;
}

/* ==================== 今日数据卡片样式 ==================== */
.stats-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 20rpx;
}

.stat-card {
	flex: 1;
	min-width: 200rpx;
	background: linear-gradient(135deg, #F8F9FB 0%, #FFFFFF 100%);
	border-radius: 12rpx;
	padding: 24rpx 20rpx;
	border: 1rpx solid #EBEEF5;
}

/* 主数据卡片 - 销售额更突出 */
.stat-card.main-stat {
	flex-basis: 100%;
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
	border: none;
}

.stat-label {
	font-size: 26rpx;
	color: #666666;
	margin-bottom: 12rpx;
}

.main-stat .stat-label {
	color: rgba(255, 255, 255, 0.9);
}

.stat-value {
	font-size: 40rpx;
	font-weight: 700;
	margin-bottom: 8rpx;
}

.stat-value.primary {
	color: #4A90E2;
}

.main-stat .stat-value {
	font-size: 56rpx;
	color: #FFFFFF;
}

.stat-value.success {
	color: #52C41A;
}

.stat-value.warning {
	color: #FA8C16;
}

.stat-sub {
	font-size: 24rpx;
	color: #999999;
}

.main-stat .stat-sub {
	color: rgba(255, 255, 255, 0.8);
}

/* ==================== 待办提醒样式 ==================== */
.reminder-card {
	display: flex;
	align-items: center;
	padding: 24rpx 20rpx;
	border-radius: 12rpx;
	margin-bottom: 16rpx;
}

.reminder-card:last-child {
	margin-bottom: 0;
}

.reminder-card.danger {
	background-color: #FFF2F0;
	border: 1rpx solid #FFCCC7;
}

.reminder-card.warning {
	background-color: #FFFBE6;
	border: 1rpx solid #FFE58F;
}

.reminder-icon {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 20rpx;
}

.reminder-card.danger .reminder-icon {
	background-color: #FF4D4F;
}

.reminder-card.warning .reminder-icon {
	background-color: #FA8C16;
}

.icon-text {
	color: #FFFFFF;
	font-size: 32rpx;
	font-weight: 700;
}

.reminder-content {
	flex: 1;
}

.reminder-title {
	font-size: 30rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 8rpx;
}

.reminder-desc {
	font-size: 26rpx;
	color: #666666;
}

.reminder-arrow {
	font-size: 32rpx;
	color: #CCCCCC;
	padding: 0 10rpx;
}

/* ==================== 快捷入口样式 ==================== */
.entry-grid {
	display: flex;
	justify-content: space-around;
	flex-wrap: wrap;
}

.entry-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	width: 25%;
	min-width: 140rpx;
	padding: 20rpx 0;
}

.entry-icon {
	width: 100rpx;
	height: 100rpx;
	border-radius: 20rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
}

.entry-icon.scan {
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
}

.entry-icon.inbound {
	background: linear-gradient(135deg, #52C41A 0%, #389E0D 100%);
}

.entry-icon.inventory {
	background: linear-gradient(135deg, #722ED1 0%, #531DAB 100%);
}

.entry-icon.customer {
	background: linear-gradient(135deg, #FA8C16 0%, #D46B08 100%);
}

.entry-icon .icon-text {
	color: #FFFFFF;
	font-size: 40rpx;
	font-weight: 600;
}

.entry-label {
	font-size: 28rpx;
	color: #333333;
}

/* ==================== 最近订单样式 ==================== */
.order-list {
	/* 订单列表容器 */
}

.order-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 24rpx 0;
	border-bottom: 1rpx solid #EBEEF5;
}

.order-item:last-child {
	border-bottom: none;
}

.order-main {
	flex: 1;
}

.order-no {
	font-size: 28rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 8rpx;
}

.order-customer {
	font-size: 26rpx;
	color: #666666;
}

.order-info {
	text-align: right;
}

.order-amount {
	font-size: 32rpx;
	font-weight: 600;
	color: #FF4D4F;
	margin-bottom: 8rpx;
}

.order-time {
	font-size: 24rpx;
	color: #999999;
}

/* ==================== 空状态样式 ==================== */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 60rpx 0;
}

.empty-text {
	font-size: 28rpx;
	color: #999999;
	margin-bottom: 24rpx;
}

.empty-action {
	font-size: 30rpx;
	color: #FFFFFF;
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
	padding: 20rpx 60rpx;
	border-radius: 40rpx;
}
</style>
