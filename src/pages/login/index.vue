<template>
	<view class="login-container">
		<!-- ==================== 背景装饰 ==================== -->
		<view class="bg-gradient"></view>
		<view class="bg-circle bg-circle-1"></view>
		<view class="bg-circle bg-circle-2"></view>
		<view class="bg-circle bg-circle-3"></view>
		
		<!-- ==================== Logo 和品牌区域 ==================== -->
		<view class="brand-section">
			<image class="logo-image" src="/static/logo.png" mode="aspectFit"></image>
			<view class="app-name">水果进销存</view>
			<view class="app-desc">专为水果批发商打造的智能管理工具</view>
		</view>
		
		<!-- ==================== 登录按钮区域 ==================== -->
		<view class="login-section">
			<!-- 未登录时显示登录按钮 -->
			<view v-if="!isLoggedIn">
				<button class="wechat-login-btn" :loading="loading" @click="handleWechatLogin">
					<image class="wechat-icon" src="/static/wechat-icon.png" mode="aspectFit"></image>
					<text class="btn-text">微信一键登录</text>
				</button>
				
				<view class="agreement-section">
					<checkbox-group @change="handleAgreementChange">
						<label class="agreement-label">
							<checkbox :checked="agreed" value="agreed" />
							<text class="agreement-text">我已阅读并同意</text>
							<text class="agreement-link" @click.stop="viewAgreement('user')">《用户协议》</text>
							<text class="agreement-text">和</text>
							<text class="agreement-link" @click.stop="viewAgreement('privacy')">《隐私政策》</text>
						</label>
					</checkbox-group>
				</view>
			</view>
			
			<!-- 已登录时显示用户信息 -->
			<view v-else class="logged-in-section">
				<view class="user-info-card">
					<image class="user-avatar" :src="userInfo.avatarUrl || '/static/default-avatar.png'" mode="aspectFill"></image>
					<view class="user-detail">
						<view class="user-nickname">{{ userInfo.nickName || '微信用户' }}</view>
						<view class="user-id">ID: {{ userInfo.userId }}</view>
					</view>
				</view>
				
				<button class="enter-system-btn" @click="enterSystem">
					<text>进入系统</text>
				</button>
			</view>
		</view>
		
		<!-- ==================== 底部功能特性 ==================== -->
		<view class="footer-info">
			<view class="feature-list">
				<view class="feature-item">
					<view class="feature-icon-box feature-sales">
						<text class="feature-icon">📊</text>
					</view>
					<text class="feature-text">销售管理</text>
				</view>
				<view class="feature-item">
					<view class="feature-icon-box feature-inventory">
						<text class="feature-icon">📦</text>
					</view>
					<text class="feature-text">库存管理</text>
				</view>
				<view class="feature-item">
					<view class="feature-icon-box feature-finance">
						<text class="feature-icon">💰</text>
					</view>
					<text class="feature-text">财务统计</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
/**
 * 登录页面
 * 提供微信一键登录功能
 */
import { authAPI } from '@/utils/api.js'

export default {
	data() {
		return {
			// 登录状态
			isLoggedIn: false,
			loading: false,
			agreed: false,
			
			// 用户信息
			userInfo: {
				avatarUrl: '',
				nickName: '',
				userId: ''
			}
		}
	},
	
	onLoad() {
		// 页面加载时检查登录状态
		this.checkLoginStatus()
	},
	
	onShow() {
		// 页面显示时再次检查（防止从其他页面返回）
		this.checkLoginStatus()
	},
	
	methods: {
		/**
		 * 检查登录状态
		 */
		checkLoginStatus() {
			const token = uni.getStorageSync('token')
			const nickname = uni.getStorageSync('nickname')
			const avatarUrl = uni.getStorageSync('avatar_url')
			const userId = uni.getStorageSync('user_id')
			
			if (token) {
				this.isLoggedIn = true
				this.userInfo = {
					avatarUrl: avatarUrl || '',
					nickName: nickname || '微信用户',
					userId: userId ? userId.substring(0, 8) + '...' : ''
				}
			}
		},
		
		/**
		 * 处理微信登录
		 */
		handleWechatLogin() {
			// 检查是否同意协议
			if (!this.agreed) {
				uni.showModal({
					title: '提示',
					content: '请先阅读并同意用户协议和隐私政策',
					showCancel: false,
					confirmText: '我知道了'
				})
				return
			}
			
			if (this.loading) return
			
			this.loading = true
			
			// 第一步：获取微信登录凭证
			uni.showLoading({ 
				title: '正在登录...', 
				mask: true 
			})
			
			uni.login({
				provider: 'weixin',
				success: (loginRes) => {
					console.log('微信登录凭证：', loginRes.code)
					
					if (!loginRes.code) {
						this.loginError('获取微信登录凭证失败')
						return
					}
					
					// 第二步：调用云函数完成登录
					this.callLoginFunction(loginRes.code)
				},
				fail: (err) => {
					console.error('微信登录失败：', err)
					this.loginError('微信登录失败，请重试')
				}
			})
		},
		
		/**
		 * 调用本地API完成登录
		 */
		async callLoginFunction(code) {
			try {
				const result = await authAPI.login(code)
				console.log('API返回：', result)
				
				// 检查API返回结果
				if (result.code !== 0) {
					this.loginError(result.message || '登录失败')
					return
				}
				
				// 登录成功，保存用户信息
				this.loginSuccess(result.data)
			} catch (err) {
				console.error('调用API失败：', err)
				this.loginError('登录失败，请检查网络')
			}
		},
		
		/**
		 * 登录成功处理
		 * 将API返回的驼峰字段映射为存储使用的蛇形字段
		 */
		loginSuccess(data) {
			// API返回的字段为驼峰命名：token, userId, tenantId, role, nickname, avatarUrl, isNewUser
			const { token, userId, tenantId, role, nickname, avatarUrl, isNewUser } = data
			
			// 将登录信息存入本地缓存（存储键名使用蛇形命名以保持兼容）
			uni.setStorageSync('token', token)
			uni.setStorageSync('user_id', userId)
			uni.setStorageSync('tenant_id', tenantId)
			uni.setStorageSync('role', role)
			uni.setStorageSync('nickname', nickname || '微信用户')
			uni.setStorageSync('avatar_url', avatarUrl || '')
			
			uni.hideLoading()
			
			uni.showToast({
				title: isNewUser ? '🎉 注册成功' : '✅ 登录成功',
				icon: 'success',
				duration: 1500
			})
			
			// 更新用户信息显示
			this.isLoggedIn = true
			this.userInfo = {
				avatarUrl: avatarUrl || '',
				nickName: nickname || '微信用户',
				userId: userId.substring(0, 8) + '...'
			}
			
			this.loading = false
		},
		
		/**
		 * 登录失败处理
		 */
		loginError(message) {
			uni.hideLoading()
			this.loading = false
			
			uni.showToast({
				title: message || '登录失败，请重试',
				icon: 'none',
				duration: 3000
			})
		},
		
		/**
		 * 处理协议勾选
		 */
		handleAgreementChange(e) {
			this.agreed = e.detail.value.includes('agreed')
		},
		
		/**
		 * 查看协议
		 */
		viewAgreement(type) {
			if (type === 'user') {
				uni.showModal({
					title: '用户协议',
					content: '欢迎使用水果进销存 SaaS 系统！\n\n本协议是您使用本服务的基础，请您仔细阅读：\n\n1. 本系统提供销售、库存、财务等管理功能\n2. 您需要提供真实有效的信息\n3. 请妥善保管您的账号信息\n4. 本系统会保护您的数据安全\n\n点击确认表示您已阅读并同意以上条款。',
					showCancel: false,
					confirmText: '我已阅读',
					confirmColor: '#FF6B35'
				})
			} else {
				uni.showModal({
					title: '隐私政策',
					content: '我们非常重视您的隐私保护：\n\n1. 我们会收集您的微信账号信息用于登录\n2. 您的业务数据仅对您可见\n3. 我们不会向第三方泄露您的信息\n4. 您可以随时删除账号和数据\n\n点击确认表示您已阅读并同意以上条款。',
					showCancel: false,
					confirmText: '我已阅读',
					confirmColor: '#FF6B35'
				})
			}
		},
		
		/**
		 * 进入系统
		 */
		enterSystem() {
			uni.switchTab({
				url: '/pages/index/index'
			})
		}
	}
}
</script>

<style lang="scss" scoped>
/* ==================== 登录容器样式 ==================== */
.login-container {
	min-height: 100vh;
	background: linear-gradient(180deg, #FF8A65 0%, #FF6B35 100%);
	position: relative;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	padding: 0 48rpx;
}

/* ==================== 背景渐变 ==================== */
.bg-gradient {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: linear-gradient(135deg, #FF8A65 0%, #FF6B35 50%, #F7931E 100%);
	z-index: 0;
}

/* ==================== 背景装饰圆形 ==================== */
.bg-circle {
	position: absolute;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.12);
}

.bg-circle-1 {
	width: 500rpx;
	height: 500rpx;
	top: -150rpx;
	right: -150rpx;
}

.bg-circle-2 {
	width: 350rpx;
	height: 350rpx;
	bottom: 300rpx;
	left: -150rpx;
}

.bg-circle-3 {
	width: 250rpx;
	height: 250rpx;
	bottom: 100rpx;
	right: -80rpx;
}

/* ==================== 品牌区域样式 ==================== */
.brand-section {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding-top: 120rpx;
	margin-bottom: 80rpx;
	position: relative;
	z-index: 1;
}

.logo-image {
	width: 180rpx;
	height: 180rpx;
	margin-bottom: 32rpx;
	border-radius: 36rpx;
	background-color: #FFFFFF;
	box-shadow: 0 12rpx 48rpx rgba(0, 0, 0, 0.15);
	padding: 20rpx;
}

.app-name {
	font-size: 44rpx;
	font-weight: 700;
	color: #FFFFFF;
	margin-bottom: 16rpx;
	letter-spacing: 2rpx;
	text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.2);
}

.app-desc {
	font-size: 26rpx;
	color: rgba(255, 255, 255, 0.9);
	text-align: center;
	line-height: 1.6;
}

/* ==================== 登录按钮区域样式 ==================== */
.login-section {
	flex: 1;
	display: flex;
	flex-direction: column;
	justify-content: center;
	position: relative;
	z-index: 1;
}

.wechat-login-btn {
	width: 100%;
	height: 96rpx;
	background: linear-gradient(135deg, #07C160 0%, #05A850 100%);
	border-radius: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border: none;
	box-shadow: 0 12rpx 32rpx rgba(7, 193, 96, 0.35);
	margin-bottom: 32rpx;
	padding: 0;
	line-height: normal;
	
	&::after {
		border: none;
	}
}

.wechat-icon {
	width: 48rpx;
	height: 48rpx;
	margin-right: 16rpx;
}

.btn-text {
	font-size: 32rpx;
	font-weight: 600;
	color: #FFFFFF;
}

/* ==================== 协议区域样式 ==================== */
.agreement-section {
	margin-top: 24rpx;
}

.agreement-label {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	justify-content: center;
}

.agreement-text {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.85);
	margin: 0 4rpx;
}

.agreement-link {
	font-size: 24rpx;
	color: #FFFFFF;
	text-decoration: underline;
	font-weight: 600;
	margin: 0 4rpx;
}

/* ==================== 已登录用户信息卡片 ==================== */
.logged-in-section {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.user-info-card {
	width: 100%;
	background: rgba(255, 255, 255, 0.98);
	border-radius: 24rpx;
	padding: 40rpx 32rpx;
	display: flex;
	align-items: center;
	margin-bottom: 40rpx;
	box-shadow: 0 12rpx 48rpx rgba(0, 0, 0, 0.12);
}

.user-avatar {
	width: 120rpx;
	height: 120rpx;
	border-radius: 50%;
	border: 4rpx solid #FF6B35;
	flex-shrink: 0;
	background-color: #F5F5F5;
}

.user-detail {
	flex: 1;
	margin-left: 32rpx;
}

.user-nickname {
	font-size: 36rpx;
	font-weight: 600;
	color: #333333;
	margin-bottom: 12rpx;
}

.user-id {
	font-size: 26rpx;
	color: #999999;
}

.enter-system-btn {
	width: 100%;
	height: 96rpx;
	background: linear-gradient(135deg, #FF6B35 0%, #F7931E 100%);
	border-radius: 48rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border: none;
	box-shadow: 0 12rpx 32rpx rgba(255, 107, 53, 0.35);
	
	&::after {
		border: none;
	}
	
	text {
		font-size: 32rpx;
		font-weight: 600;
		color: #FFFFFF;
	}
}

/* ==================== 底部功能特性 ==================== */
.footer-info {
	padding: 40rpx 0 60rpx;
	position: relative;
	z-index: 1;
}

.feature-list {
	display: flex;
	justify-content: space-around;
	background: rgba(255, 255, 255, 0.18);
	border-radius: 24rpx;
	padding: 32rpx 24rpx;
	backdrop-filter: blur(10rpx);
}

.feature-item {
	display: flex;
	flex-direction: column;
	align-items: center;
}

.feature-icon-box {
	width: 96rpx;
	height: 96rpx;
	border-radius: 24rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
	background: rgba(255, 255, 255, 0.95);
	box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.1);
}

.feature-sales {
	background: linear-gradient(135deg, #FFE5D0 0%, #FFD4BE 100%);
}

.feature-inventory {
	background: linear-gradient(135deg, #D0FFE5 0%, #BEFFD4 100%);
}

.feature-finance {
	background: linear-gradient(135deg, #FFF4D0 0%, #FFE9BE 100%);
}

.feature-icon {
	font-size: 44rpx;
}

.feature-text {
	font-size: 24rpx;
	color: #FFFFFF;
	font-weight: 500;
}
</style>
