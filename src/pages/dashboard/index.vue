<template>
	<view class="dashboard-container">
		<!-- 标题 -->
		<view class="header">
			<text class="header-title">经营看板</text>
			<text class="header-date">{{ todayStr }}</text>
		</view>

		<!-- 今日概览 -->
		<view class="section">
			<view class="section-title">📊 今日概览</view>
			<view class="overview-grid">
				<view class="ov-card sales">
					<text class="ov-value">{{ data.today.sales_yuan }}</text>
					<text class="ov-label">今日销售额(元)</text>
				</view>
				<view class="ov-card receive">
					<text class="ov-value">{{ data.today.received_yuan }}</text>
					<text class="ov-label">今日已收款(元)</text>
				</view>
				<view class="ov-card order">
					<text class="ov-value">{{ data.today.order_count }}</text>
					<text class="ov-label">今日订单数</text>
				</view>
			</view>
		</view>

		<!-- 月度概览 -->
		<view class="section">
			<view class="section-title">📈 本月经营</view>
			<view class="month-row">
				<view class="month-item">
					<text class="month-num">{{ data.month.sales_yuan }}</text>
					<text class="month-label">本月销售额(元)</text>
				</view>
			</view>
		</view>

		<!-- 客户欠款 -->
		<view class="section">
			<view class="section-title">💳 客户与欠款</view>
			<view class="debt-overview">
				<view class="debt-card">
					<text class="debt-num">{{ data.customers.total }}</text>
					<text class="debt-label">客户总数</text>
				</view>
				<view class="debt-card danger">
					<text class="debt-num">{{ data.customers.total_debt_yuan }}</text>
					<text class="debt-label">总应收款(元)</text>
				</view>
			</view>

			<!-- 欠款TOP10 -->
			<view class="top-title" v-if="data.customers.top_debt.length > 0">
				<text>🔔 欠款排行 TOP{{ data.customers.top_debt.length }}</text>
			</view>
			<view class="debt-list" v-if="data.customers.top_debt.length > 0">
				<view class="debt-item" v-for="(d, idx) in data.customers.top_debt" :key="idx">
					<view class="debt-rank" :class="'rank' + (idx + 1)">{{ idx + 1 }}</view>
					<view class="debt-info">
						<text class="debt-name">{{ d.name }}</text>
						<text class="debt-phone" v-if="d.phone">{{ d.phone }}</text>
					</view>
					<text class="debt-money">{{ d.debt_yuan }}</text>
				</view>
			</view>
			<view class="no-debt" v-else>
				<text class="no-text">🎉 所有客户均已结清</text>
			</view>
		</view>

		<!-- 库存概览 -->
		<view class="section">
			<view class="section-title">📦 库存概览</view>
			<view class="stock-grid">
				<view class="stock-card">
					<text class="stock-num">{{ data.inventory.total_count }}</text>
					<text class="stock-label">总库存量</text>
				</view>
				<view class="stock-card">
					<text class="stock-num">{{ data.inventory.total_value_yuan }}</text>
					<text class="stock-label">库存总值(元)</text>
				</view>
				<view class="stock-card">
					<text class="stock-num">{{ data.inventory.product_count }}</text>
					<text class="stock-label">商品种类</text>
				</view>
			</view>

			<view class="quick-links">
				<view class="link-btn" @click="goStock">查看库存</view>
				<view class="link-btn" @click="goProduct">商品管理</view>
			</view>
		</view>

		<!-- 快捷操作 -->
		<view class="section">
			<view class="section-title">⚡ 快捷操作</view>
			<view class="quick-grid">
				<view class="quick-btn" @click="goBilling">
					<text class="quick-emoji">📝</text>
					<text class="quick-text">销售开单</text>
				</view>
				<view class="quick-btn" @click="goCustomers">
					<text class="quick-emoji">👥</text>
					<text class="quick-text">客户管理</text>
				</view>
				<view class="quick-btn" @click="goFinance">
					<text class="quick-emoji">💰</text>
					<text class="quick-text">收款核销</text>
				</view>
				<view class="quick-btn" @click="goExport">
					<text class="quick-emoji">📥</text>
					<text class="quick-text">数据导出</text>
				</view>
			</view>
		</view>

		<view class="safe-bottom"></view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { dashboardAPI } from '@/utils/api.js'

const todayStr = ref(new Date().toLocaleDateString('zh-CN', {
	year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short'
}))

const data = ref({
	today: { sales_yuan: '0.00', received_yuan: '0.00', order_count: 0 },
	month: { sales_yuan: '0.00' },
	customers: { total: 0, total_debt_yuan: '0.00', top_debt: [] },
	inventory: { total_value_yuan: '0.00', total_count: 0, product_count: 0 }
})

/**
 * 加载看板数据
 * 调用本地API替换原uniCloud云函数调用
 * 将API返回的驼峰字段映射为模板使用的蛇形字段
 * 金额相关字段：totalDebtYuan/debtYuan 以"分"存储，需除以100转为"元"
 */
const loadDashboard = async () => {
	try {
		const result = await dashboardAPI.get()
		console.log('看板数据:', result)
		if (result.code === 0) {
			const d = result.data
			// 适配数据映射：驼峰 -> 蛇形，同时处理金额单位转换（分 -> 元）
			data.value = {
				today: {
					sales_yuan: d.today.salesYuan ?? '0.00',
					received_yuan: d.today.receivedYuan ?? '0.00',
					order_count: d.today.orderCount ?? 0
				},
				month: {
					sales_yuan: d.month.salesYuan ?? '0.00'
				},
				customers: {
					total: d.customers.total ?? 0,
					// totalDebtYuan 以"分"存储，除以100转为"元"
					total_debt_yuan: d.customers.totalDebtYuan != null
						? (d.customers.totalDebtYuan / 100).toFixed(2)
						: '0.00',
					// topDebt -> top_debt，内部字段同步映射
					top_debt: (d.customers.topDebt || []).map(item => ({
						name: item.name,
						phone: item.phone,
						// debtYuan 以"分"存储，除以100转为"元"
						debt_yuan: item.debtYuan != null
							? (item.debtYuan / 100).toFixed(2)
							: '0.00'
					}))
				},
				inventory: {
					total_value_yuan: d.inventory.totalValueYuan ?? '0.00',
					total_count: d.inventory.totalCount ?? 0,
					product_count: d.inventory.productCount ?? 0
				}
			}
		}
	} catch (e) {
		console.error('加载看板失败:', e)
	}
}

const goBilling = () => uni.switchTab({ url: '/pages/sales/billing' })
const goStock = () => uni.switchTab({ url: '/pages/inventory/list' })
const goCustomers = () => uni.navigateTo({ url: '/pages/customer/list' })
const goFinance = () => uni.navigateTo({ url: '/pages/finance/receive' })
const goProduct = () => uni.navigateTo({ url: '/pages/product/list' })
const goExport = () => uni.navigateTo({ url: '/pages/settings/export' })

onMounted(() => loadDashboard())
</script>

<style lang="scss" scoped>
.dashboard-container { min-height: 100vh; background: #F5F5F5; padding: 0 30rpx; }

.header { padding: 30rpx 0 20rpx; }
.header-title { font-size: 44rpx; color: #333; font-weight: 700; display: block; }
.header-date { font-size: 26rpx; color: #999; margin-top: 8rpx; display: block; }

.section { margin-bottom: 30rpx; }
.section-title { font-size: 32rpx; color: #333; font-weight: 600; margin-bottom: 20rpx; }

/* 今日概览 */
.overview-grid { display: flex; gap: 18rpx; }
.ov-card {
	flex: 1; background: #FFF; border-radius: 16rpx; padding: 26rpx 20rpx;
	text-align: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04);
}
.ov-value { font-size: 48rpx; color: #4A90E2; font-weight: 700; display: block;
	.receive & { color: #66BB6A; }
	.order & { color: #FF9800; }
}
.ov-label { font-size: 22rpx; color: #999; margin-top: 10rpx; display: block; }

/* 月度 */
.month-row { display: flex; }
.month-item { flex: 1; background: #FFF; border-radius: 16rpx; padding: 30rpx; text-align: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04); }
.month-num { font-size: 48rpx; color: #E91E63; font-weight: 700; display: block; }
.month-label { font-size: 24rpx; color: #999; margin-top: 10rpx; }

/* 欠款 */
.debt-overview { display: flex; gap: 18rpx; margin-bottom: 20rpx; }
.debt-card {
	flex: 1; background: #FFF; border-radius: 16rpx; padding: 26rpx 20rpx;
	text-align: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04);
	&.danger { background: #FFF5F5; }
}
.debt-num { font-size: 44rpx; color: #333; font-weight: 700; display: block;
	.danger & { color: #F44336; }
}
.debt-label { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }

.top-title { font-size: 26rpx; color: #666; margin-bottom: 16rpx; }
.debt-list { background: #FFF; border-radius: 16rpx; overflow: hidden; }
.debt-item { display: flex; align-items: center; padding: 22rpx 24rpx; border-bottom: 1rpx solid #F0F0F0; }
.debt-rank {
	width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center;
	border-radius: 50%; background: #F0F0F0; font-size: 24rpx; color: #999; font-weight: 600; margin-right: 20rpx;
	&.rank1 { background: #FF9800; color: #FFF; }
	&.rank2 { background: #607D8B; color: #FFF; }
	&.rank3 { background: #795548; color: #FFF; }
}
.debt-info { flex: 1; display: flex; flex-direction: column; }
.debt-name { font-size: 28rpx; color: #333; }
.debt-phone { font-size: 22rpx; color: #999; margin-top: 2rpx; }
.debt-money { font-size: 30rpx; color: #F44336; font-weight: 600;
	&::before { content: '¥'; font-size: 22rpx; }
}
.no-debt { text-align: center; padding: 40rpx 0; }
.no-text { font-size: 28rpx; color: #999; }

/* 库存 */
.stock-grid { display: flex; gap: 18rpx; margin-bottom: 20rpx; }
.stock-card {
	flex: 1; background: #FFF; border-radius: 16rpx; padding: 26rpx 16rpx;
	text-align: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04);
}
.stock-num { font-size: 40rpx; color: #4A90E2; font-weight: 700; display: block; }
.stock-label { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }

.quick-links { display: flex; gap: 18rpx; }
.link-btn { flex: 1; height: 70rpx; line-height: 70rpx; text-align: center; background: #FFF; border-radius: 12rpx; font-size: 26rpx; color: #4A90E2; font-weight: 500; }

/* 快捷操作 */
.quick-grid { display: flex; gap: 18rpx; }
.quick-btn {
	flex: 1; background: #FFF; border-radius: 16rpx; padding: 30rpx 0;
	text-align: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04);
}
.quick-emoji { font-size: 44rpx; display: block; }
.quick-text { font-size: 24rpx; color: #333; margin-top: 12rpx; display: block; }

.safe-bottom { height: 50rpx; }
</style>
