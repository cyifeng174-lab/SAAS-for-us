<template>
	<view class="detail-container">
		<!-- ==================== 加载状态 ==================== -->
		<view class="loading-state" v-if="isLoading">
			<text class="loading-text">加载中...</text>
		</view>

		<!-- ==================== 空状态 ==================== -->
		<view class="empty-state" v-else-if="!inventoryDetail">
			<text class="empty-icon">📦</text>
			<text class="empty-text">未找到库存信息</text>
			<view class="back-btn" @click="goBack">
				<text class="back-text">返回列表</text>
			</view>
		</view>

		<!-- ==================== 主内容区域 ==================== -->
		<view class="content-section" v-else>
			<!-- ==================== 基本信息卡片 ==================== -->
			<view class="info-card">
				<!-- 商品名称 -->
				<view class="product-header">
					<text class="product-name">{{ inventoryDetail.product_name }}</text>
					<view class="status-tag" :class="getStatusClass(inventoryDetail)">
						{{ getStatusText(inventoryDetail) }}
					</view>
				</view>

				<!-- 规格等级产地标签 -->
				<view class="tags-row">
					<text class="tag spec-tag" v-if="inventoryDetail.spec">{{ inventoryDetail.spec }}</text>
					<text class="tag grade-tag" v-if="inventoryDetail.grade">{{ inventoryDetail.grade }}</text>
					<text class="tag origin-tag" v-if="inventoryDetail.origin">{{ inventoryDetail.origin }}</text>
				</view>

				<!-- 库存信息 -->
				<view class="stock-highlight">
					<text class="stock-label">当前库存</text>
					<view class="stock-value-row">
						<text class="stock-value" :class="{ 'low-stock': isLowStock(inventoryDetail) }">
							{{ (inventoryDetail.current_stock_jin || 0).toFixed(2) }}
						</text>
						<text class="stock-unit">斤</text>
					</view>
				</view>

				<!-- 详细信息 -->
				<view class="detail-info">
					<view class="info-row">
						<text class="info-label">平均成本单价</text>
						<text class="info-value price">
							¥{{ fenToYuan(inventoryDetail.unit_cost_fen) }}/斤
						</text>
					</view>
					<view class="info-row">
						<text class="info-label">库存总成本</text>
						<text class="info-value price">
							¥{{ fenToYuan(inventoryDetail.total_cost_fen) }}
						</text>
					</view>
					<view class="info-row">
						<text class="info-label">建议售价</text>
						<text class="info-value">
							¥{{ fenToYuan(inventoryDetail.suggested_price_fen) }}/斤
						</text>
					</view>
					<view class="info-row" v-if="inventoryDetail.warning_stock_jin > 0">
						<text class="info-label">库存预警值</text>
						<text class="info-value">
							{{ inventoryDetail.warning_stock_jin }} 斤
						</text>
					</view>
				</view>

				<!-- 累计统计 -->
				<view class="stats-row">
					<view class="stat-item">
						<text class="stat-value">{{ (inventoryDetail.total_inbound_jin || 0).toFixed(2) }}</text>
						<text class="stat-label">累计入库(斤)</text>
					</view>
					<view class="stat-divider"></view>
					<view class="stat-item">
						<text class="stat-value">{{ (inventoryDetail.total_outbound_jin || 0).toFixed(2) }}</text>
						<text class="stat-label">累计出库(斤)</text>
					</view>
				</view>
			</view>

			<!-- ==================== 批次明细区域 ==================== -->
			<view class="batch-section">
				<view class="section-header">
					<text class="section-title">批次明细</text>
					<text class="section-subtitle">(先进先出)</text>
				</view>

				<!-- 批次列表 -->
				<view class="batch-list" v-if="batchList.length > 0">
					<view
						class="batch-card"
						v-for="(batch, index) in batchList"
						:key="batch.batch_id"
					>
						<!-- 批次头部 -->
						<view class="batch-header">
							<view class="batch-no">
								<text class="batch-index">{{ index + 1 }}</text>
								<text class="batch-id">{{ batch.batch_id }}</text>
							</view>
							<view class="batch-weight">
								<text class="weight-value">{{ (batch.stock_jin || 0).toFixed(2) }}</text>
								<text class="weight-unit">斤</text>
							</view>
						</view>

						<!-- 批次详情 -->
						<view class="batch-detail">
							<view class="batch-row">
								<text class="batch-label">入库时间</text>
								<text class="batch-value">{{ formatDateTime(batch.inbound_time, 'YYYY-MM-DD HH:mm') }}</text>
							</view>
							<view class="batch-row">
								<text class="batch-label">单位成本</text>
								<text class="batch-value price">¥{{ fenToYuan(batch.unit_cost_fen) }}/斤</text>
							</view>
							<view class="batch-row" v-if="batch.processing_order_id">
								<text class="batch-label">加工单号</text>
								<text class="batch-value link" @click="viewProcessingOrder(batch.processing_order_id)">
									查看详情 >
								</text>
							</view>
						</view>
					</view>
				</view>

				<!-- 空批次状态 -->
				<view class="empty-batch" v-else>
					<text class="empty-text">暂无批次明细</text>
				</view>
			</view>
		</view>

		<!-- ==================== 底部操作按钮区域 ==================== -->
		<view class="bottom-section" v-if="inventoryDetail">
			<view class="action-btn adjust-btn" @click="handleAdjustStock">
				<text class="btn-icon">⚙</text>
				<text class="btn-text">调整库存</text>
			</view>
			<view class="action-btn billing-btn" @click="goToBilling">
				<text class="btn-icon">📝</text>
				<text class="btn-text">快捷开单</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, formatDateTime, showLoading, hideLoading, showError } from '@/utils/util.js'
import { inventoryAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 库存ID（从页面参数获取）
const inventoryId = ref('')

// 库存详情数据
const inventoryDetail = ref(null)

// 批次列表（按入库时间排序）
const batchList = ref([])

// 加载状态
const isLoading = ref(false)

// ==================== 计算属性 ====================

// ==================== 方法 ====================

/**
 * 加载库存详情
 */
const loadInventoryDetail = async () => {
	if (!inventoryId.value) {
		showError('缺少库存ID参数')
		return
	}

	try {
		isLoading.value = true
		showLoading('加载中...')

		// 调用库存详情API
		const res = await inventoryAPI.detail(inventoryId.value)

		if (res.code === 0 && res.data) {
			// API返回的是对象本身（不是数组）
			inventoryDetail.value = res.data

			// 处理批次列表（按入库时间排序，最早的在前）
			if (inventoryDetail.value.batch_list && inventoryDetail.value.batch_list.length > 0) {
				batchList.value = [...inventoryDetail.value.batch_list].sort((a, b) => {
					return (a.inbound_time || 0) - (b.inbound_time || 0)
				})
			} else {
				batchList.value = []
			}
		} else {
			inventoryDetail.value = null
			batchList.value = []
		}

	} catch (error) {
		showError('加载库存详情失败：' + error.message)
		console.error('加载库存详情失败：', error)
	} finally {
		isLoading.value = false
		hideLoading()
	}
}

/**
 * 获取库存状态样式类
 * @param {Object} item - 库存项
 * @returns {String} 样式类名
 */
const getStatusClass = (item) => {
	if (!item.current_stock_jin || item.current_stock_jin <= 0) {
		return 'status-out'
	}
	if (item.current_stock_jin <= (item.warning_stock_jin || 0)) {
		return 'status-warning'
	}
	return 'status-normal'
}

/**
 * 获取库存状态文本
 * @param {Object} item - 库存项
 * @returns {String} 状态文本
 */
const getStatusText = (item) => {
	if (!item.current_stock_jin || item.current_stock_jin <= 0) {
		return '缺货'
	}
	if (item.current_stock_jin <= (item.warning_stock_jin || 0)) {
		return '低库存'
	}
	return '正常'
}

/**
 * 判断是否低库存
 * @param {Object} item - 库存项
 * @returns {Boolean}
 */
const isLowStock = (item) => {
	return item.current_stock_jin <= (item.warning_stock_jin || 0)
}

/**
 * 跳转到销售开单页面
 */
const goToBilling = () => {
	// 跳转到开单页面并传递商品ID
	uni.switchTab({
		url: '/pages/sales/billing'
	})
	// 通过事件传递选中的商品
	uni.$emit('selectInventory', inventoryDetail.value)
}

/**
 * 调整库存（预留功能）
 */
const handleAdjustStock = () => {
	// TODO: 实现库存调整功能
	uni.showToast({
		title: '功能开发中，敬请期待',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 查看加工单详情
 * @param {String} orderId - 加工单ID
 */
const viewProcessingOrder = (orderId) => {
	// TODO: 跳转到加工单详情页面
	uni.showToast({
		title: '加工单详情功能开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 返回上一页
 */
const goBack = () => {
	uni.navigateBack()
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 获取页面参数
	const pages = getCurrentPages()
	const currentPage = pages[pages.length - 1]
	const options = currentPage.options || {}

	// 获取库存ID
	inventoryId.value = options.id || ''

	// 加载库存详情
	if (inventoryId.value) {
		loadInventoryDetail()
	} else {
		showError('缺少库存ID参数')
	}
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.detail-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	padding-bottom: 140rpx;
}

/* ==================== 加载状态 ==================== */
.loading-state {
	display: flex;
	align-items: center;
	justify-content: center;
	min-height: 100vh;
}

.loading-text {
	font-size: 32rpx;
	color: #999999;
}

/* ==================== 空状态 ==================== */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	min-height: 100vh;
	padding: 0 60rpx;
}

.empty-icon {
	font-size: 120rpx;
	margin-bottom: 30rpx;
}

.empty-text {
	font-size: 32rpx;
	color: #999999;
	margin-bottom: 40rpx;
}

.back-btn {
	min-width: 200rpx;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #4A90E2;
	border-radius: 40rpx;
	padding: 0 40rpx;
}

.back-text {
	font-size: 30rpx;
	color: #FFFFFF;
	font-weight: 500;
}

/* ==================== 主内容区域 ==================== */
.content-section {
	padding: 20rpx 30rpx;
}

/* ==================== 基本信息卡片 ==================== */
.info-card {
	background-color: #FFFFFF;
	border-radius: 20rpx;
	padding: 40rpx 30rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

/* 商品头部 */
.product-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 25rpx;
}

.product-name {
	font-size: 40rpx;
	color: #333333;
	font-weight: 700;
	flex: 1;
	margin-right: 20rpx;
}

.status-tag {
	font-size: 26rpx;
	padding: 10rpx 24rpx;
	border-radius: 24rpx;
	font-weight: 500;

	&.status-normal {
		background-color: #E8F5E9;
		color: #4CAF50;
	}

	&.status-warning {
		background-color: #FFF8E1;
		color: #FF9800;
	}

	&.status-out {
		background-color: #FFEBE9;
		color: #F44336;
	}
}

/* 标签行 */
.tags-row {
	display: flex;
	flex-wrap: wrap;
	gap: 15rpx;
	margin-bottom: 30rpx;
}

.tag {
	font-size: 26rpx;
	padding: 10rpx 20rpx;
	border-radius: 10rpx;
}

.spec-tag {
	background-color: #E3F2FD;
	color: #4A90E2;
}

.grade-tag {
	background-color: #F3E5F5;
	color: #9C27B0;
}

.origin-tag {
	background-color: #F5F5F5;
	color: #666666;
}

/* 库存高亮显示 */
.stock-highlight {
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
	border-radius: 16rpx;
	padding: 30rpx;
	margin-bottom: 30rpx;
}

.stock-label {
	font-size: 28rpx;
	color: rgba(255, 255, 255, 0.9);
	margin-bottom: 15rpx;
	display: block;
}

.stock-value-row {
	display: flex;
	align-items: baseline;
}

.stock-value {
	font-size: 64rpx;
	color: #FFFFFF;
	font-weight: 700;
	line-height: 1;
}

.stock-unit {
	font-size: 32rpx;
	color: rgba(255, 255, 255, 0.9);
	margin-left: 10rpx;
}

/* 详细信息 */
.detail-info {
	margin-bottom: 30rpx;
}

.info-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 20rpx 0;
	border-bottom: 1rpx solid #F0F0F0;

	&:last-child {
		border-bottom: none;
	}
}

.info-label {
	font-size: 28rpx;
	color: #666666;
}

.info-value {
	font-size: 30rpx;
	color: #333333;
	font-weight: 500;

	&.price {
		color: #FF5722;
	}
}

/* 累计统计 */
.stats-row {
	display: flex;
	align-items: center;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 25rpx 0;
	margin-top: 10rpx;
}

.stat-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.stat-value {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 8rpx;
}

.stat-label {
	font-size: 24rpx;
	color: #999999;
}

.stat-divider {
	width: 1rpx;
	height: 60rpx;
	background-color: #E0E0E0;
}

/* ==================== 批次明细区域 ==================== */
.batch-section {
	background-color: #FFFFFF;
	border-radius: 20rpx;
	padding: 30rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.section-header {
	display: flex;
	align-items: baseline;
	margin-bottom: 25rpx;
}

.section-title {
	font-size: 34rpx;
	color: #333333;
	font-weight: 600;
}

.section-subtitle {
	font-size: 26rpx;
	color: #999999;
	margin-left: 10rpx;
}

/* 批次列表 */
.batch-list {
	//
}

.batch-card {
	background-color: #F8F8F8;
	border-radius: 16rpx;
	padding: 25rpx;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.batch-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.batch-no {
	display: flex;
	align-items: center;
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

.batch-id {
	font-size: 28rpx;
	color: #666666;
}

.batch-weight {
	display: flex;
	align-items: baseline;
}

.weight-value {
	font-size: 36rpx;
	color: #FF5722;
	font-weight: 600;
}

.weight-unit {
	font-size: 26rpx;
	color: #999999;
	margin-left: 8rpx;
}

.batch-detail {
	//
}

.batch-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 12rpx 0;

	&:first-child {
		padding-top: 0;
	}
}

.batch-label {
	font-size: 26rpx;
	color: #999999;
}

.batch-value {
	font-size: 28rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 500;
	}

	&.link {
		color: #4A90E2;
	}
}

/* 空批次状态 */
.empty-batch {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 60rpx 0;
}

.empty-batch .empty-text {
	font-size: 28rpx;
	color: #999999;
}

/* ==================== 底部操作按钮区域 ==================== */
.bottom-section {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	display: flex;
	gap: 20rpx;
	padding: 20rpx 30rpx;
	background-color: #FFFFFF;
	box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);
	z-index: 100;
}

.action-btn {
	flex: 1;
	height: 100rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 16rpx;
}

.adjust-btn {
	background-color: #F8F8F8;
	border: 2rpx solid #E0E0E0;
}

.billing-btn {
	background-color: #4A90E2;
}

.btn-icon {
	font-size: 36rpx;
	margin-right: 10rpx;

	.adjust-btn & {
		color: #666666;
	}

	.billing-btn & {
		color: #FFFFFF;
	}
}

.btn-text {
	font-size: 32rpx;
	font-weight: 600;

	.adjust-btn & {
		color: #666666;
	}

	.billing-btn & {
		color: #FFFFFF;
	}
}
</style>
