<template>
	<view class="inventory-container">
		<!-- ==================== 顶部搜索区域 ==================== -->
		<view class="search-section">
			<!-- 搜索框 -->
			<view class="search-box">
				<text class="search-icon">🔍</text>
				<input
					class="search-input"
					v-model="searchKeyword"
					placeholder="搜索商品名称"
					@confirm="handleSearch"
					@input="debounceSearch"
				/>
				<text class="clear-btn" v-if="searchKeyword" @click="clearSearch">×</text>
			</view>

			<!-- 筛选按钮 -->
			<view class="filter-btn" @click="showFilterPopup = true">
				<text class="filter-icon">⚙</text>
				<text class="filter-text">筛选</text>
				<view class="filter-badge" v-if="hasActiveFilter"></view>
			</view>

			<!-- 刷新按钮 -->
			<view class="refresh-btn" @click="handleRefresh">
				<text class="refresh-icon" :class="{ rotating: isRefreshing }">↻</text>
			</view>
		</view>

		<!-- ==================== 库存统计概览 ==================== -->
		<view class="stats-section">
			<view class="stat-item">
				<text class="stat-value">{{ totalCount }}</text>
				<text class="stat-label">商品种类</text>
			</view>
			<view class="stat-item">
				<text class="stat-value highlight">{{ totalStockJin.toFixed(2) }}</text>
				<text class="stat-label">总库存(斤)</text>
			</view>
			<view class="stat-item">
				<text class="stat-value price">¥{{ fenToYuan(totalCostFen) }}</text>
				<text class="stat-label">库存总值</text>
			</view>
		</view>

		<!-- ==================== 库存列表区域 ==================== -->
		<scroll-view
			class="list-section"
			scroll-y
			:refresher-enabled="true"
			:refresher-triggered="isRefreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<!-- 空状态 -->
			<view class="empty-state" v-if="inventoryList.length === 0 && !isLoading">
				<text class="empty-icon">📦</text>
				<text class="empty-text">暂无库存数据</text>
				<text class="empty-hint">请先进行加工入库操作</text>
			</view>

			<!-- 库存列表 -->
			<view class="inventory-list" v-else>
				<view
					class="inventory-card"
					v-for="item in inventoryList"
					:key="item._id"
					@click="goToDetail(item._id)"
				>
					<!-- 卡片头部 -->
					<view class="card-header">
						<text class="product-name">{{ item.product_name }}</text>
						<view class="status-tag" :class="getStatusClass(item)">
							{{ getStatusText(item) }}
						</view>
					</view>

					<!-- 规格等级 -->
					<view class="card-specs">
						<text class="spec-tag" v-if="item.spec">{{ item.spec }}</text>
						<text class="spec-tag" v-if="item.grade">{{ item.grade }}</text>
						<text class="origin-tag" v-if="item.origin">{{ item.origin }}</text>
					</view>

					<!-- 库存信息 -->
					<view class="card-info">
						<view class="info-item stock">
							<text class="info-label">当前库存</text>
							<text class="info-value large" :class="{ 'low-stock': isLowStock(item) }">
								{{ item.current_stock_jin?.toFixed(2) || '0.00' }} 斤
							</text>
						</view>
						<view class="info-item">
							<text class="info-label">成本单价</text>
							<text class="info-value">¥{{ fenToYuan(item.unit_cost_fen) }}/斤</text>
						</view>
					</view>

					<!-- 快捷操作按钮 -->
					<view class="card-actions">
						<view class="action-btn detail" @click.stop="goToDetail(item._id)">
							<text class="btn-text">查看详情</text>
						</view>
						<view class="action-btn billing" @click.stop="goToBilling(item)" v-if="item.current_stock_jin > 0">
							<text class="btn-text">快捷开单</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 加载更多 -->
			<view class="load-more" v-if="inventoryList.length > 0">
				<text class="load-text" v-if="isLoading">加载中...</text>
				<text class="load-text" v-else-if="!hasMore">没有更多了</text>
				<text class="load-text" v-else>上拉加载更多</text>
			</view>
		</scroll-view>

		<!-- ==================== 底部操作区域（固定） ==================== -->
		<view class="bottom-section">
			<view class="bottom-btn stock-in" @click="goToStockIn">
				<text class="btn-icon">+</text>
				<text class="btn-text">快捷入库</text>
			</view>
			<view class="bottom-btn billing" @click="goToBilling()">
				<text class="btn-icon">📝</text>
				<text class="btn-text">快捷开单</text>
			</view>
		</view>

		<!-- ==================== 半屏弹窗：筛选 ==================== -->
		<view class="popup-mask" v-if="showFilterPopup" @click="closeFilterPopup"></view>
		<view class="popup-container filter-popup" :class="{ show: showFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">筛选条件</text>
				<text class="popup-close" @click="closeFilterPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 等级筛选 -->
				<view class="filter-group">
					<text class="filter-label">等级</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterGrade === '' }"
							@click="filterGrade = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option"
							v-for="grade in gradeOptions"
							:key="grade"
							:class="{ active: filterGrade === grade }"
							@click="filterGrade = grade"
						>
							<text class="option-text">{{ grade }}</text>
						</view>
					</view>
				</view>

				<!-- 规格筛选 -->
				<view class="filter-group">
					<text class="filter-label">规格</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterSpec === '' }"
							@click="filterSpec = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option"
							v-for="spec in specOptions"
							:key="spec"
							:class="{ active: filterSpec === spec }"
							@click="filterSpec = spec"
						>
							<text class="option-text">{{ spec }}</text>
						</view>
					</view>
				</view>

				<!-- 库存状态筛选 -->
				<view class="filter-group">
					<text class="filter-label">库存状态</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterStatus === '' }"
							@click="filterStatus = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option status-normal"
							:class="{ active: filterStatus === 'normal' }"
							@click="filterStatus = 'normal'"
						>
							<text class="option-text">正常</text>
						</view>
						<view
							class="filter-option status-warning"
							:class="{ active: filterStatus === 'warning' }"
							@click="filterStatus = 'warning'"
						>
							<text class="option-text">低库存</text>
						</view>
						<view
							class="filter-option status-out"
							:class="{ active: filterStatus === 'out_of_stock' }"
							@click="filterStatus = 'out_of_stock'"
						>
							<text class="option-text">缺货</text>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="filter-actions">
					<view class="reset-btn" @click="resetFilter">
						<text class="reset-text">重置</text>
					</view>
					<view class="confirm-btn" @click="applyFilter">
						<text class="confirm-text">确定</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, showLoading, hideLoading, showError, debounce } from '@/utils/util.js'
import { inventoryAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 搜索关键词
const searchKeyword = ref('')

// 筛选条件
const showFilterPopup = ref(false) // 是否显示筛选弹窗
const filterGrade = ref('') // 等级筛选
const filterSpec = ref('') // 规格筛选
const filterStatus = ref('') // 状态筛选

// 等级选项（从数据库动态获取）
const gradeOptions = ref(['特级', '一级', '二级', '三级'])

// 规格选项（从数据库动态获取）
const specOptions = ref([])

// 库存列表
const inventoryList = ref([])

// 分页相关
const currentPage = ref(0) // 当前页码
const pageSize = 20 // 每页条数
const totalCount = ref(0) // 总数量
const hasMore = ref(true) // 是否有更多数据

// 加载状态
const isLoading = ref(false) // 是否正在加载
const isRefreshing = ref(false) // 是否正在刷新

// ==================== 计算属性 ====================

// 总库存量（斤）
const totalStockJin = computed(() => {
	return inventoryList.value.reduce((sum, item) => sum + (item.current_stock_jin || 0), 0)
})

// 总库存成本（分）
const totalCostFen = computed(() => {
	return inventoryList.value.reduce((sum, item) => {
		const stock = item.current_stock_jin || 0
		const cost = item.unit_cost_fen || 0
		return sum + Math.floor(stock * cost)
	}, 0)
})

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterGrade.value !== '' || filterSpec.value !== '' || filterStatus.value !== ''
})

// ==================== 方法 ====================

/**
 * 加载库存列表
 * @param {Boolean} isRefresh - 是否是刷新操作
 */
const loadInventoryList = async (isRefresh = false) => {
	if (isLoading.value) return

	try {
		isLoading.value = true

		// 如果是刷新，重置分页
		if (isRefresh) {
			currentPage.value = 0
			inventoryList.value = []
		}

		// 构建查询参数（传给本地API）
		const params = {
			keyword: searchKeyword.value,
			page: currentPage.value,
			pageSize: pageSize
		}

		// 调用库存列表API
		const res = await inventoryAPI.list(params)

		// 适配响应字段：支持 records（PageResponse标准）或 list（兼容旧格式）
		const records = res.data.records || res.data.list || []

		// 更新列表
		if (isRefresh) {
			inventoryList.value = records
		} else {
			inventoryList.value = [...inventoryList.value, ...records]
		}

		// 更新分页状态
		totalCount.value = res.data.total || inventoryList.value.length
		hasMore.value = records.length >= pageSize
		currentPage.value++

		// 提取规格选项（去重）
		extractSpecOptions(records)

	} catch (error) {
		showError('加载库存失败：' + error.message)
		console.error('加载库存失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 提取规格选项
 * @param {Array} list - 库存列表
 */
const extractSpecOptions = (list) => {
	const specs = new Set()
	list.forEach(item => {
		if (item.spec) {
			specs.add(item.spec)
		}
	})
	// 合并已有选项和新选项
	specOptions.value = [...new Set([...specOptions.value, ...Array.from(specs)])]
}

/**
 * 防抖搜索
 */
const debounceSearch = debounce(() => {
	loadInventoryList(true)
}, 500)

/**
 * 确认搜索
 */
const handleSearch = () => {
	loadInventoryList(true)
}

/**
 * 清空搜索
 */
const clearSearch = () => {
	searchKeyword.value = ''
	loadInventoryList(true)
}

/**
 * 下拉刷新
 */
const onRefresh = () => {
	isRefreshing.value = true
	loadInventoryList(true)
}

/**
 * 手动刷新
 */
const handleRefresh = () => {
	isRefreshing.value = true
	loadInventoryList(true)
}

/**
 * 加载更多
 */
const loadMore = () => {
	if (!hasMore.value || isLoading.value) return
	loadInventoryList(false)
}

/**
 * 关闭筛选弹窗
 */
const closeFilterPopup = () => {
	showFilterPopup.value = false
}

/**
 * 重置筛选条件
 */
const resetFilter = () => {
	filterGrade.value = ''
	filterSpec.value = ''
	filterStatus.value = ''
}

/**
 * 应用筛选条件
 */
const applyFilter = () => {
	closeFilterPopup()
	loadInventoryList(true)
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
 * 跳转到库存详情
 * @param {String} id - 库存ID
 */
const goToDetail = (id) => {
	uni.navigateTo({
		url: `/pages/inventory/detail?id=${id}`
	})
}

/**
 * 跳转到销售开单
 * @param {Object} item - 库存项（可选）
 */
const goToBilling = (item) => {
	if (item) {
		// 跳转到开单页面并传递商品ID
		uni.switchTab({
			url: '/pages/sales/billing'
		})
		// 通过事件传递选中的商品
		uni.$emit('selectInventory', item)
	} else {
		uni.switchTab({
			url: '/pages/sales/billing'
		})
	}
}

/**
 * 跳转到加工入库
 */
const goToStockIn = () => {
	uni.navigateTo({
		url: '/pages/processing/create'
	})
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时获取库存列表
	loadInventoryList(true)
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.inventory-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	display: flex;
	flex-direction: column;
}

/* ==================== 顶部搜索区域 ==================== */
.search-section {
	display: flex;
	align-items: center;
	padding: 20rpx 30rpx;
	background-color: #FFFFFF;
	gap: 20rpx;
}

.search-box {
	flex: 1;
	display: flex;
	align-items: center;
	height: 80rpx;
	padding: 0 25rpx;
	background-color: #F8F8F8;
	border-radius: 40rpx;
}

.search-icon {
	font-size: 28rpx;
	margin-right: 15rpx;
}

.search-input {
	flex: 1;
	height: 80rpx;
	font-size: 28rpx;
	color: #333333;
}

.clear-btn {
	font-size: 36rpx;
	color: #CCCCCC;
	padding: 10rpx;
}

.filter-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	min-width: 120rpx;
	height: 80rpx;
	background-color: #F8F8F8;
	border-radius: 40rpx;
	position: relative;
}

.filter-icon {
	font-size: 32rpx;
	color: #666666;
	margin-right: 8rpx;
}

.filter-text {
	font-size: 28rpx;
	color: #666666;
}

.filter-badge {
	position: absolute;
	top: 10rpx;
	right: 10rpx;
	width: 16rpx;
	height: 16rpx;
	background-color: #FF5722;
	border-radius: 50%;
}

.refresh-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 80rpx;
	height: 80rpx;
	background-color: #F8F8F8;
	border-radius: 50%;
}

.refresh-icon {
	font-size: 36rpx;
	color: #666666;
	transition: transform 0.3s;

	&.rotating {
		animation: rotate 1s linear infinite;
	}
}

@keyframes rotate {
	from {
		transform: rotate(0deg);
	}
	to {
		transform: rotate(360deg);
	}
}

/* ==================== 库存统计概览 ==================== */
.stats-section {
	display: flex;
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
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
	margin-bottom: 10rpx;

	&.highlight {
		color: #4A90E2;
	}

	&.price {
		color: #FF5722;
	}
}

.stat-label {
	font-size: 24rpx;
	color: #999999;
}

/* ==================== 库存列表区域 ==================== */
.list-section {
	flex: 1;
	padding: 0 30rpx 200rpx;
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

/* 库存列表 */
.inventory-list {
	//
}

.inventory-card {
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

.product-name {
	font-size: 34rpx;
	color: #333333;
	font-weight: 600;
	flex: 1;
}

.status-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

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

.card-specs {
	display: flex;
	flex-wrap: wrap;
	gap: 15rpx;
	margin-bottom: 25rpx;
}

.spec-tag {
	font-size: 24rpx;
	color: #4A90E2;
	background-color: #E3F2FD;
	padding: 8rpx 20rpx;
	border-radius: 8rpx;
}

.origin-tag {
	font-size: 24rpx;
	color: #666666;
	background-color: #F5F5F5;
	padding: 8rpx 20rpx;
	border-radius: 8rpx;
}

.card-info {
	display: flex;
	margin-bottom: 25rpx;
}

.info-item {
	flex: 1;
	display: flex;
	flex-direction: column;

	&.stock {
		flex: 1.2;
	}
}

.info-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 10rpx;
}

.info-value {
	font-size: 28rpx;
	color: #333333;

	&.large {
		font-size: 36rpx;
		font-weight: 600;
		color: #333333;
	}

	&.low-stock {
		color: #FF5722;
	}
}

.card-actions {
	display: flex;
	gap: 20rpx;
}

.action-btn {
	flex: 1;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 12rpx;

	&.detail {
		background-color: #F8F8F8;
		border: 2rpx solid #4A90E2;
	}

	&.billing {
		background-color: #4A90E2;
	}
}

.action-btn .btn-text {
	font-size: 28rpx;

	.detail & {
		color: #4A90E2;
	}

	.billing & {
		color: #FFFFFF;
		font-weight: 500;
	}
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

/* ==================== 底部操作区域 ==================== */
.bottom-section {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	display: flex;
	gap: 20rpx;
	padding: 20rpx 30rpx;
	background-color: #FFFFFF;
	box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);
	z-index: 100;
}

.bottom-btn {
	flex: 1;
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 12rpx;

	&.stock-in {
		background-color: #4CAF50;
	}

	&.billing {
		background-color: #4A90E2;
	}
}

.bottom-btn .btn-icon {
	font-size: 36rpx;
	color: #FFFFFF;
	margin-right: 10rpx;
}

.bottom-btn .btn-text {
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
	padding: 30rpx;
}

/* ==================== 筛选弹窗 ==================== */
.filter-group {
	margin-bottom: 40rpx;

	&:last-of-type {
		margin-bottom: 30rpx;
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

	&.status-normal.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}

	&.status-warning.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.status-out.active {
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

	.filter-option.status-normal.active & {
		color: #4CAF50;
	}

	.filter-option.status-warning.active & {
		color: #FF9800;
	}

	.filter-option.status-out.active & {
		color: #F44336;
	}
}

.filter-actions {
	display: flex;
	gap: 20rpx;
	margin-top: 30rpx;
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
</style>
