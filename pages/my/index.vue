<template>
	<view class="container">
		<!-- ==================== 用户信息区域 ==================== -->
		<view class="user-section">
			<view class="user-info" @click="handleEditUser">
				<!-- 用户头像 -->
				<view class="user-avatar">
					<text class="avatar-text">用</text>
				</view>
				
				<!-- 用户基本信息 -->
				<view class="user-detail">
					<view class="user-name">{{ userInfo.name }}</view>
					<view class="user-phone">{{ userInfo.phone }}</view>
				</view>
				
				<!-- 编辑按钮 -->
				<view class="user-edit">
					<text class="edit-text">编辑</text>
					<text class="arrow">&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 基础数据维护区域 ==================== -->
		<view class="section">
			<view class="section-title">基础数据维护</view>
			
			<!-- 商品规格库管理 -->
			<view class="menu-item" @click="handleSpecLibrary">
				<view class="menu-icon spec">
					<text class="icon-text">规</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">商品规格库</view>
					<view class="menu-desc">管理商品规格信息</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 客户管理 -->
			<view class="menu-item" @click="goToCustomerList">
				<view class="menu-icon customer">
					<text class="icon-text">客</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">客户管理</view>
					<view class="menu-desc">管理客户信息与信用额度</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 供应商管理 -->
			<view class="menu-item" @click="handleSupplierManage">
				<view class="menu-icon supplier">
					<text class="icon-text">供</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">供应商管理</view>
					<view class="menu-desc">管理供应商信息</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 成品名称库管理 -->
			<view class="menu-item last" @click="handleProductLibrary">
				<view class="menu-icon product">
					<text class="icon-text">品</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">成品名称库</view>
					<view class="menu-desc">管理成品名称与分类</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 系统设置区域 ==================== -->
		<view class="section">
			<view class="section-title">系统设置</view>
			
			<!-- 基础设置 -->
			<view class="menu-item" @click="handleBasicSettings">
				<view class="menu-icon setting">
					<text class="icon-text">设</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">基础设置</view>
					<view class="menu-desc">系统基础参数配置</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 通知设置 -->
			<view class="menu-item" @click="handleNotificationSettings">
				<view class="menu-icon notification">
					<text class="icon-text">通</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">通知设置</view>
					<view class="menu-desc">消息提醒与通知管理</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 关于我们 -->
			<view class="menu-item last" @click="goToAbout">
				<view class="menu-icon about">
					<text class="icon-text">关</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">关于我们</view>
					<view class="menu-desc">版本信息与服务协议</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 数据导出区域 ==================== -->
		<view class="section">
			<view class="section-title">数据导出</view>
			
			<!-- 日结数据导出 -->
			<view class="menu-item" @click="handleDailyExport">
				<view class="menu-icon export-day">
					<text class="icon-text">日</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">日结数据导出</view>
					<view class="menu-desc">导出每日销售与库存报表</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 月度报表导出 -->
			<view class="menu-item last" @click="handleMonthlyExport">
				<view class="menu-icon export-month">
					<text class="icon-text">月</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">月度报表导出</view>
					<view class="menu-desc">导出月度经营分析报表</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 其他功能区域 ==================== -->
		<view class="section">
			<view class="section-title">其他</view>
			
			<!-- 清除缓存 -->
			<view class="menu-item" @click="handleClearCache">
				<view class="menu-icon cache">
					<text class="icon-text">清</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">清除缓存</view>
					<view class="menu-desc">清理本地缓存数据</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
			
			<!-- 退出登录 -->
			<view class="menu-item last" @click="handleLogout">
				<view class="menu-icon logout">
					<text class="icon-text">退</text>
				</view>
				<view class="menu-content">
					<view class="menu-label">退出登录</view>
					<view class="menu-desc">退出当前账号</view>
				</view>
				<view class="menu-arrow">
					<text>&gt;</text>
				</view>
			</view>
		</view>

		<!-- ==================== 版本信息 ==================== -->
		<view class="version-info">
			<text>水果进销存 v1.0.0</text>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'

// ==================== 响应式数据 ====================

/**
 * 用户信息
 * 当前使用模拟数据，后续对接用户系统
 */
const userInfo = ref({
	name: '水果批发商',        // 用户名称
	phone: '138****8888',     // 手机号（脱敏显示）
	avatar: ''                // 头像URL（预留）
})

// ==================== 功能处理方法 ====================

/**
 * 编辑用户信息（预留功能）
 * 当前显示提示，后续对接用户信息编辑功能
 */
const handleEditUser = () => {
	uni.showToast({
		title: '编辑功能开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 商品规格库管理（预留功能）
 * 当前显示提示，后续对接规格库管理页面
 */
const handleSpecLibrary = () => {
	uni.showToast({
		title: '商品规格库开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 跳转到客户列表页面
 * 已实现功能，直接跳转
 */
const goToCustomerList = () => {
	uni.navigateTo({
		url: '/pages/customer/list'
	})
}

/**
 * 供应商管理（预留功能）
 * 当前显示提示，后续对接供应商管理页面
 */
const handleSupplierManage = () => {
	uni.showToast({
		title: '供应商管理开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 成品名称库管理（预留功能）
 * 当前显示提示，后续对接成品库管理页面
 */
const handleProductLibrary = () => {
	uni.showToast({
		title: '成品名称库开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 基础设置（预留功能）
 * 当前显示提示，后续对接基础设置页面
 */
const handleBasicSettings = () => {
	uni.showToast({
		title: '基础设置开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 通知设置（预留功能）
 * 当前显示提示，后续对接通知设置页面
 */
const handleNotificationSettings = () => {
	uni.showToast({
		title: '通知设置开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 跳转到关于我们页面
 * 显示应用版本信息
 */
const goToAbout = () => {
	uni.showModal({
		title: '关于我们',
		content: '水果进销存小程序\n版本：v1.0.0\n\n专为水果批发商打造的进销存管理工具，支持销售开单、库存管理、客户账本等功能。',
		showCancel: false,
		confirmText: '知道了'
	})
}

/**
 * 日结数据导出（预留功能）
 * 当前显示提示，后续对接Excel导出功能
 */
const handleDailyExport = () => {
	uni.showToast({
		title: '日结导出功能开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 月度报表导出（预留功能）
 * 当前显示提示，后续对接Excel导出功能
 */
const handleMonthlyExport = () => {
	uni.showToast({
		title: '月度报表导出开发中',
		icon: 'none',
		duration: 2000
	})
}

/**
 * 清除缓存
 * 清理本地存储的缓存数据
 */
const handleClearCache = () => {
	uni.showModal({
		title: '确认清除',
		content: '确定要清除本地缓存吗？清除后部分数据需要重新加载。',
		success: (res) => {
			if (res.confirm) {
				// 执行清除缓存
				try {
					// 清除本地存储
					uni.clearStorageSync()
					
					uni.showToast({
						title: '缓存已清除',
						icon: 'success',
						duration: 2000
					})
				} catch (error) {
					console.error('清除缓存失败:', error)
					uni.showToast({
						title: '清除失败',
						icon: 'none',
						duration: 2000
					})
				}
			}
		}
	})
}

/**
 * 退出登录（预留功能）
 * 当前显示提示，后续对接用户认证系统
 */
const handleLogout = () => {
	uni.showModal({
		title: '确认退出',
		content: '确定要退出登录吗？',
		success: (res) => {
			if (res.confirm) {
				// 预留：后续对接用户认证系统
				uni.showToast({
					title: '退出功能开发中',
					icon: 'none',
					duration: 2000
				})
			}
		}
	})
}

// ==================== 生命周期 ====================

/**
 * 页面加载时
 * 可在此处加载用户信息等数据
 */
onMounted(() => {
	// 预留：从本地存储或云端加载用户信息
	console.log('我的页面加载完成')
})

/**
 * 页面显示时
 * 可在此处刷新数据
 */
onShow(() => {
	// 预留：刷新用户信息等数据
	console.log('我的页面显示')
})
</script>

<style lang="scss" scoped>
/* ==================== 容器样式 ==================== */
.container {
	min-height: 100vh;
	background-color: #F5F7FA;
	padding-bottom: 30rpx;
}

/* ==================== 用户信息区域样式 ==================== */
.user-section {
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
	padding: 40rpx 24rpx 50rpx;
	margin-bottom: 20rpx;
}

.user-info {
	display: flex;
	align-items: center;
	background-color: rgba(255, 255, 255, 0.15);
	border-radius: 16rpx;
	padding: 30rpx 24rpx;
}

.user-avatar {
	width: 120rpx;
	height: 120rpx;
	border-radius: 60rpx;
	background-color: rgba(255, 255, 255, 0.3);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 24rpx;
}

.avatar-text {
	color: #FFFFFF;
	font-size: 48rpx;
	font-weight: 600;
}

.user-detail {
	flex: 1;
}

.user-name {
	font-size: 36rpx;
	font-weight: 600;
	color: #FFFFFF;
	margin-bottom: 12rpx;
}

.user-phone {
	font-size: 28rpx;
	color: rgba(255, 255, 255, 0.85);
}

.user-edit {
	display: flex;
	align-items: center;
}

.edit-text {
	font-size: 28rpx;
	color: rgba(255, 255, 255, 0.9);
	margin-right: 8rpx;
}

.arrow {
	color: rgba(255, 255, 255, 0.7);
	font-size: 32rpx;
}

/* ==================== 通用区块样式 ==================== */
.section {
	background-color: #FFFFFF;
	margin: 0 24rpx 20rpx;
	border-radius: 16rpx;
	padding: 24rpx;
	box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.section-title {
	font-size: 32rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 24rpx;
	padding-left: 8rpx;
}

/* ==================== 菜单项样式 ==================== */
.menu-item {
	display: flex;
	align-items: center;
	padding: 28rpx 16rpx;
	border-bottom: 1rpx solid #EBEEF5;
	transition: background-color 0.2s;
}

.menu-item:active {
	background-color: #F8F9FB;
}

.menu-item.last {
	border-bottom: none;
}

.menu-icon {
	width: 80rpx;
	height: 80rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 20rpx;
}

.icon-text {
	color: #FFFFFF;
	font-size: 36rpx;
	font-weight: 600;
}

/* 不同功能模块的图标颜色 */
.menu-icon.spec {
	background: linear-gradient(135deg, #722ED1 0%, #531DAB 100%);
}

.menu-icon.customer {
	background: linear-gradient(135deg, #FA8C16 0%, #D46B08 100%);
}

.menu-icon.supplier {
	background: linear-gradient(135deg, #13C2C2 0%, #08979C 100%);
}

.menu-icon.product {
	background: linear-gradient(135deg, #EB2F96 0%, #C41D7F 100%);
}

.menu-icon.setting {
	background: linear-gradient(135deg, #4A90E2 0%, #357ABD 100%);
}

.menu-icon.notification {
	background: linear-gradient(135deg, #FAAD14 0%, #D48806 100%);
}

.menu-icon.about {
	background: linear-gradient(135deg, #52C41A 0%, #389E0D 100%);
}

.menu-icon.export-day {
	background: linear-gradient(135deg, #1890FF 0%, #096DD9 100%);
}

.menu-icon.export-month {
	background: linear-gradient(135deg, #2F54EB 0%, #1D39C4 100%);
}

.menu-icon.cache {
	background: linear-gradient(135deg, #FA541C 0%, #D4380D 100%);
}

.menu-icon.logout {
	background: linear-gradient(135deg, #FF4D4F 0%, #CF1322 100%);
}

.menu-content {
	flex: 1;
}

.menu-label {
	font-size: 32rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 8rpx;
}

.menu-desc {
	font-size: 26rpx;
	color: #999999;
}

.menu-arrow {
	font-size: 32rpx;
	color: #CCCCCC;
	padding: 0 10rpx;
}

/* ==================== 版本信息样式 ==================== */
.version-info {
	text-align: center;
	padding: 40rpx 0 20rpx;
	font-size: 26rpx;
	color: #CCCCCC;
}
</style>
