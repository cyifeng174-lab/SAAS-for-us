<template>
	<view class="export-container">
		<!-- 模块选择 -->
		<view class="card">
			<view class="card-title">选择导出数据</view>
			<view class="module-grid">
				<view
					class="module-item"
					:class="{ active: currentModule === m.key }"
					v-for="m in modules"
					:key="m.key"
					@click="currentModule = m.key"
				>
					<text class="module-emoji">{{ m.emoji }}</text>
					<text class="module-name">{{ m.name }}</text>
				</view>
			</view>
		</view>

		<!-- 日期 -->
		<view class="card">
			<view class="card-title">时间范围（可选）</view>
			<view class="date-row">
				<picker mode="date" :value="dateFrom" @change="e => dateFrom = e.detail.value">
					<view class="date-box" :class="{ empty: !dateFrom }">{{ dateFrom || '开始日期' }}</view>
				</picker>
				<text class="date-sep">—</text>
				<picker mode="date" :value="dateTo" @change="e => dateTo = e.detail.value">
					<view class="date-box" :class="{ empty: !dateTo }">{{ dateTo || '截止日期' }}</view>
				</picker>
			</view>
		</view>

		<!-- 格式 -->
		<view class="card">
			<view class="card-title">导出格式</view>
			<view class="format-row">
				<view class="format-item" :class="{ active: exportFormat === 'excel' }" @click="exportFormat = 'excel'">Excel</view>
				<view class="format-item" :class="{ active: exportFormat === 'csv' }" @click="exportFormat = 'csv'">CSV</view>
				<view class="format-item" :class="{ active: exportFormat === 'json' }" @click="exportFormat = 'json'">JSON</view>
			</view>
		</view>

		<!-- 导出按钮 -->
		<view class="big-export-btn" :class="{ loading: isExporting }" @click="doExport">
			<text class="big-export-text">{{ isExporting ? '导出中...' : '一键导出' }}</text>
		</view>

		<!-- 结果 -->
		<view class="card result-card" v-if="exportResult">
			<view class="result-row">
				<text class="result-icon">✅</text>
				<view class="result-info">
					<text class="result-name">{{ exportResult.fileName }}</text>
					<text class="result-num">{{ exportResult.exportedCount }} 条数据</text>
				</view>
			</view>
			<view class="result-btns">
				<view class="btn-copy" @click="copyContent">{{ isCopied ? '已复制✓' : '复制全部' }}</view>
				<view class="btn-save" @click="saveContent">保存到手机</view>
			</view>
		</view>

		<!-- 历史 -->
		<view class="card" v-if="history.length > 0">
			<view class="card-title">
				最近导出
				<text class="clear-link" @click="clearHistory">清空</text>
			</view>
			<view class="history-item" v-for="h in history" :key="h._id">
				<text class="h-name">{{ h.module_name }}</text>
				<text class="h-count">{{ h.count }}条</text>
				<text class="h-time">{{ h._createTime }}</text>
			</view>
		</view>

		<view class="safe-bottom"></view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { exportAPI } from '@/utils/api.js'

const modules = [
	{ key: 'customers', name: '客户账本', emoji: '👥' },
	{ key: 'sales_orders', name: '销售订单', emoji: '📦' },
	{ key: 'purchases', name: '采购订单', emoji: '🛒' },
	{ key: 'inventories', name: '库存数据', emoji: '📋' },
	{ key: 'financial_ledgers', name: '财务流水', emoji: '💰' },
	{ key: 'spec_all', name: '规格库', emoji: '📏' },
	{ key: 'product_cats', name: '商品种类', emoji: '🏷️' },
	{ key: 'suppliers', name: '供应商', emoji: '🏭' }
]

const currentModule = ref('customers')
const dateFrom = ref('')
const dateTo = ref('')
const exportFormat = ref('excel')
const isExporting = ref(false)
const exportResult = ref(null)
const history = ref([])
const isCopied = ref(false)
const fileContent = ref('')

const doExport = async () => {
	if (isExporting.value) return
	isExporting.value = true
	exportResult.value = null
	try {
		uni.showLoading({ title: '导出中...' })
		const res = await exportAPI.submit({
			action: 'export',
			module: currentModule.value,
			format: exportFormat.value,
			date_from: dateFrom.value,
			date_to: dateTo.value
		})
		uni.hideLoading()
		if (res.code === 0) {
			exportResult.value = res.data
			fileContent.value = res.data.fileContent
			loadHistory()
		} else {
			uni.showToast({ title: res.message, icon: 'none' })
		}
	} catch (e) {
		uni.hideLoading()
		uni.showToast({ title: '导出失败', icon: 'none' })
	} finally {
		isExporting.value = false
	}
}

const copyContent = async () => {
	if (!fileContent.value) return
	await uni.setClipboardData({ data: fileContent.value })
	isCopied.value = true
	uni.showToast({ title: '已复制到剪贴板', icon: 'success' })
	setTimeout(() => isCopied.value = false, 2000)
}

const saveContent = () => {
	if (!fileContent.value) return
	uni.setClipboardData({
		data: fileContent.value,
		success: () => {
			uni.showModal({
				title: '已复制到剪贴板',
				content: `文件名：${exportResult.value.fileName}\n\n打开WPS/记事本→新建→粘贴→保存即可。`,
				showCancel: false
			})
		}
	})
}

const loadHistory = async () => {
	const res = await exportAPI.submit({ action: 'history' })
	if (res.code === 0) {
		history.value = (res.data.list || []).slice(0, 10)
	}
}

const clearHistory = async () => {
	await exportAPI.submit({ action: 'clear_log' })
	history.value = []
}

onMounted(() => loadHistory())
</script>

<style lang="scss" scoped>
.export-container { min-height: 100vh; background: #F5F5F5; padding: 30rpx; }
.card { background: #FFF; border-radius: 20rpx; padding: 30rpx; margin-bottom: 25rpx; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04); }
.card-title { font-size: 32rpx; color: #333; font-weight: 600; margin-bottom: 22rpx; display: flex; justify-content: space-between; }

.module-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.module-item {
	padding: 18rpx 22rpx; border-radius: 12rpx; border: 2rpx solid #EEE;
	background: #FAFAFA; display: flex; flex-direction: column; align-items: center; min-width: 148rpx;
	&.active { background: #E3F2FD; border-color: #4A90E2; }
}
.module-emoji { font-size: 36rpx; margin-bottom: 6rpx; }
.module-name { font-size: 24rpx; color: #333; }

.date-row { display: flex; align-items: center; gap: 20rpx; }
.date-box { flex: 1; height: 80rpx; line-height: 80rpx; text-align: center; background: #F8F8F8; border-radius: 12rpx; font-size: 28rpx; color: #333;
	&.empty { color: #BBB; }
}
.date-sep { font-size: 28rpx; color: #999; }

.format-row { display: flex; gap: 20rpx; }
.format-item { flex: 1; height: 80rpx; line-height: 80rpx; text-align: center; background: #F8F8F8; border-radius: 12rpx; font-size: 30rpx; color: #666; font-weight: 600;
	&.active { background: #4A90E2; color: #FFF; }
}

.big-export-btn {
	width: 100%; height: 110rpx; line-height: 110rpx; text-align: center;
	background: #4A90E2; border-radius: 16rpx; margin-bottom: 30rpx;
	&.loading { opacity: 0.7; }
}
.big-export-text { font-size: 40rpx; color: #FFF; font-weight: 700; }

.result-card { border-left: 6rpx solid #66BB6A; }
.result-row { display: flex; align-items: center; margin-bottom: 20rpx; }
.result-icon { font-size: 44rpx; margin-right: 16rpx; }
.result-info { display: flex; flex-direction: column; }
.result-name { font-size: 28rpx; color: #333; font-weight: 500; }
.result-num { font-size: 24rpx; color: #999; margin-top: 4rpx; }
.result-btns { display: flex; gap: 18rpx; }
.btn-copy { flex: 1; height: 76rpx; line-height: 76rpx; text-align: center; background: #F8F8F8; border-radius: 12rpx; font-size: 28rpx; color: #333; }
.btn-save { flex: 1; height: 76rpx; line-height: 76rpx; text-align: center; background: #66BB6A; border-radius: 12rpx; font-size: 28rpx; color: #FFF; font-weight: 600; }

.clear-link { font-size: 24rpx; color: #F44336; font-weight: 400; }
.history-item { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid #F0F0F0; }
.h-name { font-size: 26rpx; color: #333; flex: 1; }
.h-count { font-size: 22rpx; color: #999; margin: 0 16rpx; }
.h-time { font-size: 22rpx; color: #BBB; }
.safe-bottom { height: 60rpx; }
</style>
