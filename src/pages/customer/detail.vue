<template>
	<view class="detail-container">
		<!-- ==================== 加载状态 ==================== -->
		<view class="loading-state" v-if="isLoading">
			<text class="loading-text">加载中...</text>
		</view>

		<!-- ==================== 空状态 ==================== -->
		<view class="empty-state" v-else-if="!customer">
			<text class="empty-icon">👤</text>
			<text class="empty-text">未找到客户信息</text>
			<view class="back-btn" @click="goBack">
				<text class="back-text">返回列表</text>
			</view>
		</view>

		<!-- ==================== 主内容区域 ==================== -->
		<view class="content-section" v-else>
			<!-- ==================== 客户基本信息卡片 ==================== -->
			<view class="info-card">
				<!-- 客户名称和状态 -->
				<view class="customer-header">
					<view class="customer-main">
						<text class="customer-name">{{ customer.name }}</text>
						<view class="status-tag" :class="customer.status">
							{{ customer.status === 'active' ? '正常' : '停用' }}
						</view>
					</view>
					<view class="edit-btn" @click="toggleEditMode">
						<text class="edit-icon">{{ isEditMode ? '✓' : '✎' }}</text>
						<text class="edit-text">{{ isEditMode ? '完成' : '编辑' }}</text>
					</view>
				</view>

				<!-- 编辑模式表单 -->
				<view class="edit-form" v-if="isEditMode">
					<view class="form-item">
						<text class="form-label">客户名称</text>
						<input
							class="form-input"
							v-model="editForm.name"
							placeholder="请输入客户名称"
							maxlength="50"
						/>
					</view>
					<view class="form-item">
						<text class="form-label">联系电话</text>
						<input
							class="form-input"
							v-model="editForm.phone"
							placeholder="请输入联系电话"
							type="number"
							maxlength="20"
						/>
					</view>
					<view class="form-item">
						<text class="form-label">联系地址</text>
						<input
							class="form-input"
							v-model="editForm.address"
							placeholder="请输入联系地址"
							maxlength="200"
						/>
					</view>
					<view class="form-item">
						<text class="form-label">备注</text>
						<textarea
							class="form-textarea"
							v-model="editForm.remark"
							placeholder="请输入备注信息"
							maxlength="500"
						/>
					</view>
				</view>

				<!-- 查看模式信息展示 -->
				<view class="info-list" v-else>
					<view class="info-row">
						<text class="info-label">联系电话</text>
						<text class="info-value link" v-if="customer.phone" @click="callPhone(customer.phone)">
							{{ customer.phone }} 📞
						</text>
						<text class="info-value" v-else>-</text>
					</view>
					<view class="info-row">
						<text class="info-label">联系地址</text>
						<text class="info-value" v-if="customer.address">{{ customer.address }}</text>
						<text class="info-value" v-else>-</text>
					</view>
					<view class="info-row">
						<text class="info-label">备注</text>
						<text class="info-value remark" v-if="customer.remark">{{ customer.remark }}</text>
						<text class="info-value" v-else>-</text>
					</view>
					<view class="info-row">
						<text class="info-label">创建时间</text>
						<text class="info-value">{{ formatDateTime(customer.create_time, 'YYYY-MM-DD') }}</text>
					</view>
				</view>
			</view>

			<!-- ==================== 统计信息卡片 ==================== -->
			<view class="stats-card">
				<view class="card-title">
					<text class="title-icon">📊</text>
					<text class="title-text">交易统计</text>
				</view>

				<view class="stats-grid">
					<view class="stat-item">
						<text class="stat-value price">¥{{ fenToYuan(totalSalesAmountFen) }}</text>
						<text class="stat-label">累计采购</text>
					</view>
					<view class="stat-item">
						<text class="stat-value count">{{ orderCount }}</text>
						<text class="stat-label">订单数</text>
					</view>
					<view class="stat-item">
						<text class="stat-value debt" :class="{ 'has-debt': customer.total_debt_fen > 0 }">
							¥{{ fenToYuan(customer.total_debt_fen || 0) }}
						</text>
						<text class="stat-label">当前欠款</text>
					</view>
					<view class="stat-item">
						<text class="stat-value date">{{ formatDateTime(customer.last_order_time, 'MM-DD') }}</text>
						<text class="stat-label">最近交易</text>
					</view>
				</view>
			</view>

			<!-- ==================== 订单历史区域 ==================== -->
			<view class="order-section">
				<view class="section-header">
					<text class="section-title">交易记录</text>
					<text class="section-subtitle">最近 10 条</text>
				</view>

				<!-- 空状态 -->
				<view class="empty-order" v-if="orderList.length === 0">
					<text class="empty-text">暂无交易记录</text>
				</view>

				<!-- 订单列表 -->
				<view class="order-list" v-else>
					<view
						class="order-card"
						v-for="order in orderList"
						:key="order._id"
						@click="viewOrderDetail(order)"
					>
						<view class="order-header">
							<text class="order-no">{{ order.order_no }}</text>
							<view class="payment-tag" :class="order.payment_status">
								{{ getPaymentStatusText(order.payment_status) }}
							</view>
						</view>
						<view class="order-content">
							<view class="order-info">
								<text class="info-label">总金额</text>
								<text class="info-value price">¥{{ fenToYuan(order.total_amount_fen) }}</text>
							</view>
							<view class="order-info">
								<text class="info-label">重量</text>
								<text class="info-value">{{ order.total_weight_jin?.toFixed(2) || '0.00' }} 斤</text>
							</view>
							<view class="order-info" v-if="order.debt_amount_fen > 0">
								<text class="info-label">欠款</text>
								<text class="info-value debt">¥{{ fenToYuan(order.debt_amount_fen) }}</text>
							</view>
						</view>
						<view class="order-footer">
							<text class="time-text">{{ formatDateTime(order.create_time, 'MM-DD HH:mm') }}</text>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 底部操作按钮区域 ==================== -->
		<view class="bottom-section" v-if="customer && !isEditMode">
			<view class="action-btn delete-btn" @click="deleteCustomer">
				<text class="btn-icon">🗑</text>
				<text class="btn-text">删除客户</text>
			</view>
			<view class="action-btn receive-btn" @click="receivePayment">
				<text class="btn-icon">💰</text>
				<text class="btn-text">收款</text>
			</view>
		</view>

		<!-- ==================== 确认编辑弹窗 ==================== -->
		<view class="confirm-dialog" v-if="showSaveConfirm">
			<view class="dialog-mask" @click="showSaveConfirm = false"></view>
			<view class="dialog-content">
				<text class="dialog-title">确认保存</text>
				<text class="dialog-message">确定要保存客户信息修改吗？</text>
				<view class="dialog-actions">
					<view class="dialog-btn cancel" @click="showSaveConfirm = false">
						<text class="btn-text">取消</text>
					</view>
					<view class="dialog-btn confirm" @click="saveCustomerInfo">
						<text class="btn-text">确定</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fenToYuan, formatDateTime, showLoading, hideLoading, showSuccess, showError, showConfirm } from '@/utils/util.js'
import { customerAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================

// 客户 ID（从页面参数获取）
const customerId = ref('')

// 客户详情
const customer = ref(null)

// 加载状态
const isLoading = ref(false)

// 编辑模式
const isEditMode = ref(false)

// 编辑表单
const editForm = ref({
	name: '',
	phone: '',
	address: '',
	remark: ''
})

// 订单列表
const orderList = ref([])

// 统计数据
const totalSalesAmountFen = ref(0)
const orderCount = ref(0)

// 确认保存弹窗
const showSaveConfirm = ref(false)

// ==================== 方法 ====================

/**
 * 加载客户详情（通过云函数查询客户信息和订单历史）
 */
const loadCustomerDetail = async () => {
	if (!customerId.value) {
		showError('缺少客户 ID 参数')
		return
	}

	try {
		isLoading.value = true
		showLoading('加载中...')

		// 调用本地 API 获取客户详情和订单历史
		const res = await customerAPI.detail(customerId.value)

		console.log('客户详情 API 返回:', res.data)

		const resultData = res.data

		customer.value = resultData.customer

		editForm.value = {
			name: customer.value.name || '',
			phone: customer.value.phone || '',
			address: customer.value.address || '',
			remark: customer.value.remark || ''
		}

		orderList.value = resultData.recentOrders || []
		totalSalesAmountFen.value = resultData.totalSalesAmountFen || 0
		orderCount.value = resultData.orderCount || 0

	} catch (error) {
		showError('加载客户详情失败：' + error.message)
		console.error('加载客户详情失败：', error)
	} finally {
		isLoading.value = false
		hideLoading()
	}
}

/**
 * 加载订单历史（已合并到 loadCustomerDetail 云函数中，此函数不再使用）
 * 保留注释供参考，实际调用已在 loadCustomerDetail 中完成
 */

/**
 * 切换编辑模式
 */
const toggleEditMode = () => {
	if (isEditMode.value) {
		// 从编辑模式切换到查看模式，弹出确认保存
		showSaveConfirm.value = true
	} else {
		// 进入编辑模式
		isEditMode.value = true
		// 重新加载表单数据（撤销之前的修改）
		editForm.value = {
			name: customer.value.name || '',
			phone: customer.value.phone || '',
			address: customer.value.address || '',
			remark: customer.value.remark || ''
		}
	}
}

/**
 * 保存客户信息
 */
const saveCustomerInfo = async () => {
	try {
		showLoading('保存中...')

		// 调用本地 API 更新
		await customerAPI.update(customerId.value, {
			name: editForm.value.name,
			phone: editForm.value.phone,
			address: editForm.value.address,
			remark: editForm.value.remark
		})

		hideLoading()
		showSuccess('保存成功')
		isEditMode.value = false
		// 重新加载客户详情
		await loadCustomerDetail()
	} catch (error) {
		hideLoading()
		showError('保存失败：' + error.message)
		console.error('保存客户信息失败：', error)
	}
}

/**
 * 删除客户
 */
const deleteCustomer = async () => {
	if (!customer.value) return

	// 检查是否有欠款
	if (customer.value.total_debt_fen > 0) {
		showError('该客户还有欠款未结清，无法删除')
		return
	}

	// 确认删除
	const confirmed = await showConfirm('确定要删除该客户吗？删除后不可恢复。', '删除客户确认')
	if (!confirmed) return

	try {
		showLoading('删除中...')

		// 调用本地 API 删除
		await customerAPI.remove(customerId.value)

		hideLoading()
		showSuccess('客户已删除')
		// 返回列表页
		setTimeout(() => {
			uni.navigateBack()
		}, 1500)
	} catch (error) {
		hideLoading()
		showError('删除失败：' + error.message)
		console.error('删除客户失败：', error)
	}
}

/**
 * 收款
 */
const receivePayment = () => {
	// 跳转到收款页面
	uni.navigateTo({
		url: `/pages/finance/receive?customer_id=${customerId.value}`
	})
}

/**
 * 拨打电话
 */
const callPhone = (phone) => {
	uni.makePhoneCall({
		phoneNumber: phone
	})
}

/**
 * 查看订单详情
 */
const viewOrderDetail = (order) => {
	// 跳转到销售历史页面查看订单详情
	uni.navigateTo({
		url: '/pages/sales/history'
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

	// 获取客户 ID
	customerId.value = options.id || ''

	// 加载客户详情
	if (customerId.value) {
		loadCustomerDetail()
	} else {
		showError('缺少客户 ID 参数')
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

/* ==================== 客户基本信息卡片 ==================== */
.info-card {
	background-color: #FFFFFF;
	border-radius: 20rpx;
	padding: 30rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

/* 客户头部 */
.customer-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 25rpx;
}

.customer-main {
	display: flex;
	align-items: center;
	flex: 1;
}

.customer-name {
	font-size: 40rpx;
	color: #333333;
	font-weight: 700;
	margin-right: 20rpx;
}

.status-tag {
	font-size: 24rpx;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;

	&.active {
		background-color: #E8F5E9;
		color: #4CAF50;
	}

	&.inactive {
		background-color: #F5F5F5;
		color: #999999;
	}
}

.edit-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 24rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.edit-icon {
	font-size: 28rpx;
	color: #4A90E2;
}

.edit-text {
	font-size: 28rpx;
	color: #4A90E2;
	font-weight: 500;
}

/* 编辑表单 */
.edit-form {
	//
}

.form-item {
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.form-label {
	font-size: 28rpx;
	color: #666666;
	margin-bottom: 10rpx;
	display: block;
}

.form-input {
	width: 100%;
	height: 80rpx;
	padding: 0 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 30rpx;
	color: #333333;
}

.form-textarea {
	width: 100%;
	min-height: 150rpx;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
	font-size: 30rpx;
	color: #333333;
	line-height: 1.6;
}

/* 信息列表 */
.info-list {
	//
}

.info-row {
	display: flex;
	align-items: flex-start;
	padding: 20rpx 0;
	border-bottom: 1rpx solid #F0F0F0;

	&:last-child {
		border-bottom: none;
	}
}

.info-label {
	font-size: 28rpx;
	color: #999999;
	width: 160rpx;
	flex-shrink: 0;
}

.info-value {
	font-size: 30rpx;
	color: #333333;
	flex: 1;

	&.link {
		color: #4A90E2;
		text-decoration: underline;
	}

	&.remark {
		color: #666666;
		line-height: 1.6;
	}
}

/* ==================== 统计信息卡片 ==================== */
.stats-card {
	background-color: #FFFFFF;
	border-radius: 20rpx;
	padding: 30rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.card-title {
	display: flex;
	align-items: center;
	margin-bottom: 25rpx;
}

.title-icon {
	font-size: 36rpx;
	margin-right: 15rpx;
}

.title-text {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.stats-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 20rpx;
}

.stat-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 20rpx;
	background-color: #F8F8F8;
	border-radius: 12rpx;
}

.stat-value {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
	margin-bottom: 10rpx;

	&.price {
		color: #FF5722;
	}

	&.count {
		color: #4A90E2;
	}

	&.debt {
		color: #333333;

		&.has-debt {
			color: #FF5722;
		}
	}

	&.date {
		color: #9C27B0;
		font-size: 32rpx;
	}
}

.stat-label {
	font-size: 26rpx;
	color: #999999;
}

/* ==================== 订单历史区域 ==================== */
.order-section {
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
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
}

.section-subtitle {
	font-size: 26rpx;
	color: #999999;
	margin-left: 10rpx;
}

/* 空订单状态 */
.empty-order {
	padding: 60rpx 0;
	text-align: center;
}

.empty-order .empty-text {
	font-size: 28rpx;
	color: #CCCCCC;
}

/* 订单列表 */
.order-list {
	//
}

.order-card {
	background-color: #F8F8F8;
	border-radius: 12rpx;
	padding: 25rpx;
	margin-bottom: 20rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.order-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 20rpx;
}

.order-no {
	font-size: 28rpx;
	color: #666666;
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

.order-content {
	margin-bottom: 20rpx;
}

.order-info {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 12rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.info-label {
	font-size: 26rpx;
	color: #999999;
}

.info-value {
	font-size: 28rpx;
	color: #333333;

	&.price {
		color: #FF5722;
		font-weight: 600;
	}

	&.debt {
		color: #FF9800;
		font-weight: 500;
	}
}

.order-footer {
	padding-top: 15rpx;
	border-top: 1rpx solid #E0E0E0;
}

.time-text {
	font-size: 24rpx;
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

.delete-btn {
	background-color: #F8F8F8;
	border: 2rpx solid #E0E0E0;
}

.receive-btn {
	background-color: #4CAF50;
}

.btn-icon {
	font-size: 36rpx;
	margin-right: 10rpx;

	.delete-btn & {
		color: #666666;
	}

	.receive-btn & {
		color: #FFFFFF;
	}
}

.btn-text {
	font-size: 32rpx;
	font-weight: 600;

	.delete-btn & {
		color: #666666;
	}

	.receive-btn & {
		color: #FFFFFF;
	}
}

/* ==================== 确认编辑弹窗 ==================== */
.confirm-dialog {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 200;
}

.dialog-mask {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
}

.dialog-content {
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	width: 80%;
	background-color: #FFFFFF;
	border-radius: 20rpx;
	padding: 40rpx 30rpx;
	box-shadow: 0 10rpx 40rpx rgba(0, 0, 0, 0.2);
}

.dialog-title {
	font-size: 32rpx;
	color: #333333;
	font-weight: 600;
	display: block;
	text-align: center;
	margin-bottom: 20rpx;
}

.dialog-message {
	font-size: 28rpx;
	color: #666666;
	display: block;
	text-align: center;
	margin-bottom: 40rpx;
}

.dialog-actions {
	display: flex;
	gap: 20rpx;
}

.dialog-btn {
	flex: 1;
	height: 90rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 12rpx;

	&.cancel {
		background-color: #F8F8F8;
	}

	&.confirm {
		background-color: #4A90E2;
	}
}

.btn-text {
	font-size: 32rpx;
	font-weight: 600;

	.cancel & {
		color: #666666;
	}

	.confirm & {
		color: #FFFFFF;
	}
}
</style>
