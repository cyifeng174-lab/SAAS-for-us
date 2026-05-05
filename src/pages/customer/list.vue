<template>
	<view class="customer-container">
		<!-- ==================== 顶部搜索区域 ==================== -->
		<view class="search-section">
			<!-- 搜索框 -->
			<view class="search-box">
				<text class="search-icon">🔍</text>
				<input
					class="search-input"
					v-model="searchKeyword"
					placeholder="搜索客户名称/电话"
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

			<!-- 新增客户按钮 -->
			<view class="add-btn" @click="openAddPopup">
				<text class="add-icon">+</text>
			</view>
		</view>

		<!-- ==================== 账本统计概览 ==================== -->
		<view class="stats-section">
			<view class="stat-item">
				<text class="stat-value">{{ totalCustomers }}</text>
				<text class="stat-label">客户总数</text>
			</view>
			<view class="stat-item">
				<text class="stat-value debt">¥{{ fenToYuan(totalDebtFen) }}</text>
				<text class="stat-label">总欠款</text>
			</view>
			<view class="stat-item">
				<text class="stat-value price">¥{{ fenToYuan(totalSalesFen) }}</text>
				<text class="stat-label">累计销售</text>
			</view>
		</view>

		<!-- ==================== 客户列表区域 ==================== -->
		<scroll-view
			class="list-section"
			scroll-y
			:refresher-enabled="true"
			:refresher-triggered="isRefreshing"
			@refresherrefresh="onRefresh"
			@scrolltolower="loadMore"
		>
			<!-- 空状态 -->
			<view class="empty-state" v-if="customerList.length === 0 && !isLoading">
				<text class="empty-icon">👥</text>
				<text class="empty-text">暂无客户数据</text>
				<text class="empty-hint">点击右上角 + 添加客户</text>
			</view>

			<!-- 客户列表 -->
			<view class="customer-list" v-else>
				<view
					class="customer-card"
					v-for="item in customerList"
					:key="item._id"
					@click="goToDetail(item._id)"
				>
					<!-- 卡片头部：客户名称 + 类型标签 -->
					<view class="card-header">
						<text class="customer-name">{{ item.name }}</text>
						<view class="type-tag" :class="getTypeClass(item.customer_type)">
							{{ getTypeText(item.customer_type) }}
						</view>
					</view>

					<!-- 联系电话 -->
					<view class="card-phone" v-if="item.phone" @click.stop="callPhone(item.phone)">
						<text class="phone-icon">📞</text>
						<text class="phone-text">{{ item.phone }}</text>
					</view>

					<!-- 欠款信息（重点突出） -->
					<view class="card-debt">
						<view class="debt-item">
							<text class="debt-label">当前欠款</text>
							<text class="debt-value" :class="{ 'has-debt': item.total_debt_fen > 0, 'over-credit': isOverCredit(item) }">
								¥{{ fenToYuan(item.total_debt_fen) }}
							</text>
						</view>
						<view class="debt-item" v-if="item.credit_limit_fen > 0">
							<text class="debt-label">信用额度</text>
							<text class="debt-value credit">¥{{ fenToYuan(item.credit_limit_fen) }}</text>
						</view>
					</view>

					<!-- 销售统计 -->
					<view class="card-stats">
						<view class="stats-item">
							<text class="stats-label">累计销售</text>
							<text class="stats-value">¥{{ fenToYuan(item.total_sales_fen) }}</text>
						</view>
						<view class="stats-item">
							<text class="stats-label">订单数量</text>
							<text class="stats-value">{{ item.order_count || 0 }} 单</text>
						</view>
						<view class="stats-item">
							<text class="stats-label">最近下单</text>
							<text class="stats-value">{{ formatLastOrderTime(item.last_order_time) }}</text>
						</view>
					</view>

					<!-- 快捷操作按钮 -->
					<view class="card-actions">
						<view class="action-btn billing" @click.stop="goToBilling(item)">
							<text class="btn-text">开单</text>
						</view>
						<view class="action-btn receive" @click.stop="goToReceive(item)" v-if="item.total_debt_fen > 0">
							<text class="btn-text">收款</text>
						</view>
						<view class="action-btn detail" @click.stop="goToDetail(item._id)">
							<text class="btn-text">详情</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 加载更多 -->
			<view class="load-more" v-if="customerList.length > 0">
				<text class="load-text" v-if="isLoading">加载中...</text>
				<text class="load-text" v-else-if="!hasMore">没有更多了</text>
				<text class="load-text" v-else>上拉加载更多</text>
			</view>
		</scroll-view>

		<!-- ==================== 半屏弹窗：筛选 ==================== -->
		<view class="popup-mask" v-if="showFilterPopup" @click="closeFilterPopup"></view>
		<view class="popup-container filter-popup" :class="{ show: showFilterPopup }">
			<view class="popup-header">
				<text class="popup-title">筛选条件</text>
				<text class="popup-close" @click="closeFilterPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 客户类型筛选 -->
				<view class="filter-group">
					<text class="filter-label">客户类型</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterType === '' }"
							@click="filterType = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option type-wholesale"
							:class="{ active: filterType === 'wholesale' }"
							@click="filterType = 'wholesale'"
						>
							<text class="option-text">批发</text>
						</view>
						<view
							class="filter-option type-retail"
							:class="{ active: filterType === 'retail' }"
							@click="filterType = 'retail'"
						>
							<text class="option-text">零售</text>
						</view>
						<view
							class="filter-option type-both"
							:class="{ active: filterType === 'both' }"
							@click="filterType = 'both'"
						>
							<text class="option-text">批发+零售</text>
						</view>
					</view>
				</view>

				<!-- 欠款状态筛选 -->
				<view class="filter-group">
					<text class="filter-label">欠款状态</text>
					<view class="filter-options">
						<view
							class="filter-option"
							:class="{ active: filterDebtStatus === '' }"
							@click="filterDebtStatus = ''"
						>
							<text class="option-text">全部</text>
						</view>
						<view
							class="filter-option debt-no"
							:class="{ active: filterDebtStatus === 'no_debt' }"
							@click="filterDebtStatus = 'no_debt'"
						>
							<text class="option-text">无欠款</text>
						</view>
						<view
							class="filter-option debt-has"
							:class="{ active: filterDebtStatus === 'has_debt' }"
							@click="filterDebtStatus = 'has_debt'"
						>
							<text class="option-text">有欠款</text>
						</view>
						<view
							class="filter-option debt-over"
							:class="{ active: filterDebtStatus === 'over_credit' }"
							@click="filterDebtStatus = 'over_credit'"
						>
							<text class="option-text">超额欠款</text>
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

		<!-- ==================== 半屏弹窗：新增客户 ==================== -->
		<view class="popup-mask" v-if="showAddPopup" @click="closeAddPopup"></view>
		<view class="popup-container add-popup" :class="{ show: showAddPopup }">
			<view class="popup-header">
				<text class="popup-title">新增客户</text>
				<text class="popup-close" @click="closeAddPopup">×</text>
			</view>

			<view class="popup-content">
				<!-- 客户名称（必填） -->
				<view class="form-group">
					<text class="form-label required">客户名称</text>
					<input
						class="form-input"
						v-model="formData.name"
						placeholder="请输入客户名称"
						maxlength="50"
					/>
				</view>

				<!-- 联系电话 -->
				<view class="form-group">
					<text class="form-label">联系电话</text>
					<input
						class="form-input"
						v-model="formData.phone"
						placeholder="请输入联系电话"
						type="tel"
						maxlength="20"
					/>
				</view>

				<!-- 地址 -->
				<view class="form-group">
					<text class="form-label">地址</text>
					<input
						class="form-input"
						v-model="formData.address"
						placeholder="请输入客户地址"
						maxlength="200"
					/>
				</view>

				<!-- 客户类型 -->
				<view class="form-group">
					<text class="form-label">客户类型</text>
					<view class="type-options">
						<view
							class="type-option"
							:class="{ active: formData.customer_type === 'wholesale' }"
							@click="formData.customer_type = 'wholesale'"
						>
							<text class="type-text">批发</text>
						</view>
						<view
							class="type-option"
							:class="{ active: formData.customer_type === 'retail' }"
							@click="formData.customer_type = 'retail'"
						>
							<text class="type-text">零售</text>
						</view>
						<view
							class="type-option"
							:class="{ active: formData.customer_type === 'both' }"
							@click="formData.customer_type = 'both'"
						>
							<text class="type-text">批发+零售</text>
						</view>
					</view>
				</view>

				<!-- 信用额度 -->
				<view class="form-group">
					<text class="form-label">信用额度（元）</text>
					<input
						class="form-input"
						v-model="formData.credit_limit_yuan"
						placeholder="请输入信用额度，0表示不限"
						type="digit"
					/>
				</view>

				<!-- 备注 -->
				<view class="form-group">
					<text class="form-label">备注</text>
					<textarea
						class="form-textarea"
						v-model="formData.remark"
						placeholder="请输入备注信息"
						maxlength="500"
					/>
				</view>

				<!-- 提交按钮 -->
				<view class="form-actions">
					<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="submitForm">
						<text class="submit-text">{{ isSubmitting ? '提交中...' : '保存客户' }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fenToYuan, yuanToFen, formatDate, showLoading, hideLoading, showSuccess, showError, debounce } from '@/utils/util.js'
import { customerAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 搜索关键词
const searchKeyword = ref('')

// 筛选条件
const showFilterPopup = ref(false) // 是否显示筛选弹窗
const filterType = ref('') // 客户类型筛选
const filterDebtStatus = ref('') // 欠款状态筛选

// 客户列表
const customerList = ref([])

// 分页相关
const currentPage = ref(0) // 当前页码
const pageSize = 20 // 每页条数
const hasMore = ref(true) // 是否有更多数据

// 加载状态
const isLoading = ref(false) // 是否正在加载
const isRefreshing = ref(false) // 是否正在刷新

// 新增客户弹窗
const showAddPopup = ref(false) // 是否显示新增弹窗
const isSubmitting = ref(false) // 是否正在提交

// 新增客户表单数据
const formData = ref({
	name: '',           // 客户名称
	phone: '',          // 联系电话
	address: '',        // 地址
	customer_type: 'wholesale', // 客户类型，默认批发
	credit_limit_yuan: '', // 信用额度（元）
	remark: ''          // 备注
})

// ==================== 计算属性 ====================

// 客户总数
const totalCustomers = computed(() => {
	return customerList.value.length
})

// 总欠款（分）
const totalDebtFen = computed(() => {
	return customerList.value.reduce((sum, item) => sum + (item.total_debt_fen || 0), 0)
})

// 累计销售（分）
const totalSalesFen = computed(() => {
	return customerList.value.reduce((sum, item) => sum + (item.total_sales_fen || 0), 0)
})

// 是否有激活的筛选条件
const hasActiveFilter = computed(() => {
	return filterType.value !== '' || filterDebtStatus.value !== ''
})

// 是否可以提交表单
const canSubmit = computed(() => {
	return formData.value.name.trim() !== '' && !isSubmitting.value
})

// ==================== 方法 ====================

/**
 * 加载客户列表（通过云函数查询，解决 clientDB 前端直连问题）
 * @param {Boolean} isRefresh - 是否是刷新操作
 */
const loadCustomerList = async (isRefresh = false) => {
	if (isLoading.value) return

	try {
		isLoading.value = true

		if (isRefresh) {
			currentPage.value = 0
			customerList.value = []
		}

		// 调用本地 API 获取客户列表
		const res = await customerAPI.list({
			keyword: searchKeyword.value,
			customer_type: filterType.value,
			debt_status: filterDebtStatus.value,
			page: currentPage.value,
			page_size: pageSize
		})

		console.log('API 返回:', res.data)

		const resultData = res.data

		if (isRefresh) {
			customerList.value = resultData.list || []
		} else {
			customerList.value = [...customerList.value, ...(resultData.list || [])]
		}

		// 根据 total 计算是否还有更多数据
		hasMore.value = customerList.value.length < resultData.total
		currentPage.value++

	} catch (error) {
		showError('加载客户列表失败：' + error.message)
		console.error('加载客户列表失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

/**
 * 防抖搜索
 */
const debounceSearch = debounce(() => {
	loadCustomerList(true)
}, 500)

/**
 * 确认搜索
 */
const handleSearch = () => {
	loadCustomerList(true)
}

/**
 * 清空搜索
 */
const clearSearch = () => {
	searchKeyword.value = ''
	loadCustomerList(true)
}

/**
 * 下拉刷新
 */
const onRefresh = () => {
	isRefreshing.value = true
	loadCustomerList(true)
}

/**
 * 加载更多
 */
const loadMore = () => {
	if (!hasMore.value || isLoading.value) return
	loadCustomerList(false)
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
	filterType.value = ''
	filterDebtStatus.value = ''
}

/**
 * 应用筛选条件
 */
const applyFilter = () => {
	closeFilterPopup()
	loadCustomerList(true)
}

/**
 * 获取客户类型样式类
 * @param {String} type - 客户类型
 * @returns {String} 样式类名
 */
const getTypeClass = (type) => {
	switch (type) {
		case 'wholesale':
			return 'type-wholesale'
		case 'retail':
			return 'type-retail'
		case 'both':
			return 'type-both'
		default:
			return 'type-wholesale'
	}
}

/**
 * 获取客户类型文本
 * @param {String} type - 客户类型
 * @returns {String} 类型文本
 */
const getTypeText = (type) => {
	switch (type) {
		case 'wholesale':
			return '批发'
		case 'retail':
			return '零售'
		case 'both':
			return '批发+零售'
		default:
			return '批发'
	}
}

/**
 * 判断是否超额欠款
 * @param {Object} item - 客户数据
 * @returns {Boolean}
 */
const isOverCredit = (item) => {
	// 如果没有设置信用额度，则不算超额
	if (!item.credit_limit_fen || item.credit_limit_fen <= 0) {
		return false
	}
	// 欠款超过信用额度
	return item.total_debt_fen > item.credit_limit_fen
}

/**
 * 格式化最近下单时间
 * @param {Number} timestamp - 时间戳
 * @returns {String} 格式化后的时间
 */
const formatLastOrderTime = (timestamp) => {
	if (!timestamp) return '暂无'
	return formatDate(timestamp)
}

/**
 * 拨打电话
 * @param {String} phone - 电话号码
 */
const callPhone = (phone) => {
	if (!phone) return
	uni.makePhoneCall({
		phoneNumber: phone,
		fail: (err) => {
			console.error('拨打电话失败：', err)
		}
	})
}

/**
 * 跳转到客户详情
 * @param {String} id - 客户ID
 */
const goToDetail = (id) => {
	// 预留功能：跳转到客户详情页面
	uni.navigateTo({
		url: `/pages/customer/detail?id=${id}`
	})
}

/**
 * 跳转到销售开单
 * @param {Object} item - 客户数据
 */
const goToBilling = (item) => {
	// 跳转到开单页面并传递客户ID
	uni.switchTab({
		url: '/pages/sales/billing'
	})
	// 通过事件传递选中的客户
	uni.$emit('selectCustomer', item)
}

/**
 * 跳转到收款核销
 * @param {Object} item - 客户数据
 */
const goToReceive = (item) => {
	uni.navigateTo({
		url: `/pages/finance/receive?customer_id=${item._id}&customer_name=${encodeURIComponent(item.name)}`
	})
}

/**
 * 打开新增客户弹窗
 */
const openAddPopup = () => {
	// 重置表单
	formData.value = {
		name: '',
		phone: '',
		address: '',
		customer_type: 'wholesale',
		credit_limit_yuan: '',
		remark: ''
	}
	showAddPopup.value = true
}

/**
 * 关闭新增客户弹窗
 */
const closeAddPopup = () => {
	showAddPopup.value = false
}

/**
 * 提交新增客户表单
 */
const submitForm = async () => {
	// 表单校验
	if (!formData.value.name.trim()) {
		showError('请输入客户名称')
		return
	}

	// 电话号码格式校验（如果填写了）
	if (formData.value.phone && !/^1[3-9]\d{9}$/.test(formData.value.phone)) {
		showError('请输入正确的手机号码')
		return
	}

	if (isSubmitting.value) return

	try {
		isSubmitting.value = true
		showLoading('保存中...')

		// 构建客户数据
		const customerData = {
			name: formData.value.name.trim(),
			phone: formData.value.phone.trim(),
			address: formData.value.address.trim(),
			customer_type: formData.value.customer_type,
			credit_limit_fen: yuanToFen(formData.value.credit_limit_yuan), // 元转分
			remark: formData.value.remark.trim(),
			status: 'active', // 默认状态为正常
			total_debt_fen: 0, // 初始欠款为0
			total_sales_fen: 0, // 初始销售额为0
			total_paid_fen: 0, // 初始收款为0
			order_count: 0 // 初始订单数为0
		}

		// 调用本地 API 新增客户
		await customerAPI.create(customerData)

		hideLoading()
		showSuccess('客户添加成功')
		closeAddPopup()
		// 刷新列表
		loadCustomerList(true)

	} catch (error) {
		hideLoading()
		showError('添加客户失败：' + error.message)
		console.error('添加客户失败：', error)
	} finally {
		isSubmitting.value = false
	}
}

// ==================== 生命周期 ====================

onMounted(() => {
	// 页面加载时获取客户列表
	loadCustomerList(true)
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.customer-container {
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

.add-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 80rpx;
	height: 80rpx;
	background-color: #4A90E2;
	border-radius: 50%;
}

.add-icon {
	font-size: 40rpx;
	color: #FFFFFF;
	font-weight: 600;
}

/* ==================== 账本统计概览 ==================== */
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

	&.debt {
		color: #F44336;
	}

	&.price {
		color: #FF5722;
	}
}

.stat-label {
	font-size: 24rpx;
	color: #999999;
}

/* ==================== 客户列表区域 ==================== */
.list-section {
	flex: 1;
	padding: 0 30rpx 30rpx;
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

/* 客户列表 */
.customer-list {
	//
}

.customer-card {
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

.customer-name {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
	flex: 1;
}

.type-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

	&.type-wholesale {
		background-color: #E3F2FD;
		color: #4A90E2;
	}

	&.type-retail {
		background-color: #FFF8E1;
		color: #FF9800;
	}

	&.type-both {
		background-color: #E8F5E9;
		color: #4CAF50;
	}
}

.card-phone {
	display: flex;
	align-items: center;
	margin-bottom: 20rpx;
}

.phone-icon {
	font-size: 28rpx;
	margin-right: 10rpx;
}

.phone-text {
	font-size: 28rpx;
	color: #4A90E2;
}

/* 欠款信息 */
.card-debt {
	display: flex;
	background-color: #FFF8F8;
	padding: 25rpx;
	border-radius: 12rpx;
	margin-bottom: 20rpx;
}

.debt-item {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.debt-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 10rpx;
}

.debt-value {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;

	&.has-debt {
		color: #F44336;
	}

	&.over-credit {
		color: #FF5722;
		font-weight: 700;
	}

	&.credit {
		font-size: 28rpx;
		color: #666666;
		font-weight: 400;
	}
}

/* 销售统计 */
.card-stats {
	display: flex;
	margin-bottom: 25rpx;
	padding: 0 10rpx;
}

.stats-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.stats-label {
	font-size: 24rpx;
	color: #999999;
	margin-bottom: 8rpx;
}

.stats-value {
	font-size: 26rpx;
	color: #333333;
}

/* 快捷操作按钮 */
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

	&.billing {
		background-color: #4A90E2;
	}

	&.receive {
		background-color: #FF9800;
	}

	&.detail {
		background-color: #F8F8F8;
		border: 2rpx solid #4A90E2;
	}
}

.action-btn .btn-text {
	font-size: 28rpx;

	.billing &,
	.receive & {
		color: #FFFFFF;
		font-weight: 500;
	}

	.detail & {
		color: #4A90E2;
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

	&.type-wholesale.active {
		background-color: #E3F2FD;
		border-color: #4A90E2;
	}

	&.type-retail.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.type-both.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}

	&.debt-no.active {
		background-color: #E8F5E9;
		border-color: #4CAF50;
	}

	&.debt-has.active {
		background-color: #FFF8E1;
		border-color: #FF9800;
	}

	&.debt-over.active {
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

	.filter-option.type-wholesale.active & {
		color: #4A90E2;
	}

	.filter-option.type-retail.active & {
		color: #FF9800;
	}

	.filter-option.type-both.active & {
		color: #4CAF50;
	}

	.filter-option.debt-no.active & {
		color: #4CAF50;
	}

	.filter-option.debt-has.active & {
		color: #FF9800;
	}

	.filter-option.debt-over.active & {
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

/* ==================== 新增客户弹窗 ==================== */
.add-popup {
	.popup-content {
		padding-bottom: 60rpx;
	}
}

.form-group {
	margin-bottom: 30rpx;
}

.form-label {
	font-size: 28rpx;
	color: #333333;
	margin-bottom: 15rpx;
	display: block;

	&.required::before {
		content: '*';
		color: #F44336;
		margin-right: 8rpx;
	}
}

.form-input {
	width: 100%;
	height: 90rpx;
	padding: 0 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 28rpx;
	color: #333333;
}

.form-textarea {
	width: 100%;
	min-height: 150rpx;
	padding: 20rpx 25rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 28rpx;
	color: #333333;
}

.type-options {
	display: flex;
	gap: 20rpx;
}

.type-option {
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

.type-text {
	font-size: 28rpx;
	color: #333333;

	.type-option.active & {
		color: #4A90E2;
	}
}

.form-actions {
	margin-top: 40rpx;
}

.submit-btn {
	width: 100%;
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
</style>
