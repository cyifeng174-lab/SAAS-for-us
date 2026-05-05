<template>
	<view class="container">
		<!-- ==================== 顶部搜索栏 ==================== -->
		<view class="search-bar">
			<input
				class="search-input"
				v-model="keyword"
				placeholder="搜索供应商名称/电话"
				@confirm="loadList"
			/>
			<view class="add-btn" @click="openAdd">
				<text class="add-icon">+</text>
				<text class="add-text">新增</text>
			</view>
		</view>

		<!-- ==================== 列表区域 ==================== -->
		<scroll-view
			scroll-y
			class="list-area"
			:refresher-enabled="true"
			:refresher-triggered="refreshing"
			@refresherrefresh="onRefresh"
		>
			<!-- 空状态 -->
			<view class="empty" v-if="list.length === 0 && !loading">
				<text class="empty-icon">🏭</text>
				<text class="empty-text">暂无供应商</text>
				<text class="empty-hint">点击上方按钮添加供应商</text>
			</view>

			<!-- 供应商卡片列表 -->
			<view class="card" v-for="item in list" :key="item._id">
				<!-- 卡片主体信息 -->
				<view class="card-body" @click="showDetail(item)">
					<view class="card-header">
						<text class="name">{{ item.name }}</text>
						<view class="status-tag" :class="item.status">
							{{ statusText(item.status) }}
						</view>
					</view>

					<view class="info-row" v-if="item.phone">
						<text class="info-label">电话：</text>
						<text class="info-value phone">{{ item.phone }}</text>
						<text class="call-btn" @click.stop="callPhone(item.phone)">📞</text>
					</view>

					<view class="info-row" v-if="item.contact_person">
						<text class="info-label">联系人：</text>
						<text class="info-value">{{ item.contact_person }}</text>
					</view>

					<view class="info-row" v-if="item.origin">
						<text class="info-label">产地：</text>
						<text class="info-value origin">{{ item.origin }}</text>
					</view>

					<!-- 统计信息栏 -->
					<view class="stats-row">
						<view class="stat-item">
							<text class="stat-label">累计采购</text>
							<text class="stat-value purchase">¥{{ fenToYuan(item.total_purchase_fen) }}</text>
						</view>
						<view class="stat-item" v-if="item.total_debt_fen !== 0">
							<text class="stat-label">当前欠款</text>
							<text class="stat-value" :class="{ 'debt': item.total_debt_fen > 0 }">
								¥{{ fenToYuan(Math.abs(item.total_debt_fen)) }}
							</text>
						</view>
						<view class="stat-item">
							<text class="stat-label">采购次数</text>
							<text class="stat-value">{{ item.purchase_count }}次</text>
						</view>
					</view>
				</view>

				<!-- 操作按钮 -->
				<view class="card-actions">
					<view class="action-btn edit" @click="openEdit(item)">编辑</view>
					<view class="action-btn toggle" @click="toggleStatus(item)">
						{{ item.status === 'active' ? '停用' : '启用' }}
					</view>
					<view class="action-btn delete" @click="doDelete(item)">删除</view>
				</view>
			</view>
		</scroll-view>

		<!-- ==================== 新增/编辑弹窗 ==================== -->
		<view class="popup-mask" v-if="showPopup" @click="closePopup"></view>
		<view class="popup-container" :class="{ show: showPopup }">
			<view class="popup-header">
				<text class="popup-title">{{ editingId ? '编辑' : '新增' }}供应商</text>
				<text class="popup-close" @click="closePopup">×</text>
			</view>

			<view class="popup-content">
				<view class="form-group">
					<text class="label required">供应商名称</text>
					<input
						class="input"
						v-model="form.name"
						placeholder="如：港湾老农A1"
						maxlength="30"
						:focus="!editingId"
					/>
				</view>

				<view class="form-group">
					<text class="label">联系电话</text>
					<input
						class="input"
						v-model="form.phone"
						placeholder="手机号码"
						maxlength="15"
						type="tel"
					/>
				</view>

				<view class="form-group">
					<text class="label">联系人</text>
					<input
						class="input"
						v-model="form.contact_person"
						placeholder="联系人姓名"
						maxlength="20"
					/>
				</view>

				<view class="form-group">
					<text class="label">地址</text>
					<input
						class="input"
						v-model="form.address"
						placeholder="供应商详细地址"
						maxlength="50"
					/>
				</view>

				<view class="form-group">
					<text class="label">主要产地</text>
					<input
						class="input"
						v-model="form.origin"
						placeholder="如：陕西洛川、山东烟台"
						maxlength="30"
					/>
				</view>

				<view class="form-group">
					<text class="label">备注</text>
					<input
						class="input"
						v-model="form.remark"
						placeholder="其他备注信息"
						maxlength="100"
					/>
				</view>

				<view class="popup-btns">
					<view class="btn cancel" @click="closePopup">取消</view>
					<view
						class="btn submit"
						:class="{ disabled: !form.name.trim() || submitting }"
						@click="submit"
					>
						{{ submitting ? '保存中...' : '保存' }}
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fenToYuan, showLoading, hideLoading, showSuccess, showError } from '@/utils/util.js'
import { supplierAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================
const keyword = ref('')
const list = ref([])
const loading = ref(false)
const refreshing = ref(false)
const showPopup = ref(false)
const editingId = ref('')
const submitting = ref(false)

// 表单数据
const form = ref({
	name: '',
	phone: '',
	contact_person: '',
	address: '',
	origin: '',
	remark: ''
})

// ==================== 方法 ====================

/**
 * 加载供应商列表
 * @param {Boolean} isRefresh - 是否为刷新操作
 */
const loadList = async (isRefresh = false) => {
	if (loading.value) return
	loading.value = true
	if (isRefresh) list.value = []

	try {
		// 调用本地 API 获取供应商列表
		const res = await supplierAPI.list({
			keyword: keyword.value,
			page: 0,
			page_size: 100
		})

		list.value = res.data.list || []
	} catch (e) {
		console.error('加载供应商列表失败：', e)
		showError('加载失败')
	} finally {
		loading.value = false
		refreshing.value = false
	}
}

/**
 * 下拉刷新
 */
const onRefresh = () => {
	refreshing.value = true
	loadList(true)
}

/**
 * 打开新增弹窗
 */
const openAdd = () => {
	editingId.value = ''
	form.value = {
		name: '',
		phone: '',
		contact_person: '',
		address: '',
		origin: '',
		remark: ''
	}
	showPopup.value = true
}

/**
 * 打开编辑弹窗
 * @param {Object} item - 供应商对象
 */
const openEdit = (item) => {
	editingId.value = item._id
	form.value = {
		name: item.name,
		phone: item.phone || '',
		contact_person: item.contact_person || '',
		address: item.address || '',
		origin: item.origin || '',
		remark: item.remark || ''
	}
	showPopup.value = true
}

/**
 * 关闭弹窗
 */
const closePopup = () => {
	showPopup.value = false
	editingId.value = ''
	submitting.value = false
}

/**
 * 提交表单（新增或编辑）
 */
const submit = async () => {
	if (!form.value.name.trim()) {
		showError('请输入供应商名称')
		return
	}

	submitting.value = true

	try {
		showLoading('保存中...')

		// 调用本地 API 新增或更新
		const data = { ...form.value }

		if (editingId.value) {
			await supplierAPI.update(editingId.value, data)
		} else {
			await supplierAPI.create(data)
		}

		hideLoading()
		showSuccess(editingId.value ? '更新成功' : '添加成功')
		closePopup()
		loadList(true)
	} catch (e) {
		hideLoading()
		showError('操作失败：' + e.message)
		console.error('提交失败：', e)
	} finally {
		submitting.value = false
	}
}

/**
 * 删除供应商
 * @param {Object} item - 供应商对象
 */
const doDelete = async (item) => {
	const r = await uni.showModal({
		title: '确认删除',
		content: `确定要删除"${item.name}"吗？删除后不可恢复`,
		confirmColor: '#F44336'
	})

	if (!r.confirm) return

	try {
		showLoading('删除中...')

		// 调用本地 API 删除供应商
		await supplierAPI.remove(item._id)

		hideLoading()
		showSuccess('删除成功')
		loadList(true)
	} catch (e) {
		hideLoading()
		showError('删除失败')
		console.error('删除失败：', e)
	}
}

/**
 * 切换供应商状态（启用/停用）
 * @param {Object} item - 供应商对象
 */
const toggleStatus = async (item) => {
	const actionText = item.status === 'active' ? '停用' : '启用'

	try {
		// 调用本地 API 切换供应商状态
		const newStatus = item.status === 'active' ? 'inactive' : 'active'
		await supplierAPI.update(item._id, { status: newStatus })

		showSuccess(`${actionText}成功`)
		loadList(true)
	} catch (e) {
		showError(`${actionText}失败`)
		console.error(`${actionText}失败：`, e)
	}
}

/**
 * 显示供应商详情（预留功能）
 * @param {Object} item - 供应商对象
 */
const showDetail = (item) => {
	// 预留：可跳转到详情页或显示更多信息
	console.log('查看详情：', item)
}

/**
 * 拨打电话
 * @param {String} phone - 电话号码
 */
const callPhone = (phone) => {
	if (!phone) return
	uni.makePhoneCall({ phoneNumber: phone })
}

/**
 * 获取状态文本
 * @param {String} status - 状态值
 */
const statusText = (status) => {
	const map = {
		active: '正常',
		inactive: '停用',
		deleted: '已删除'
	}
	return map[status] || '未知'
}

// ==================== 生命周期 ====================
onMounted(() => {
	loadList(true)
})
</script>

<style lang="scss" scoped>
/* ==================== 容器 ==================== */
.container {
	min-height: 100vh;
	background-color: #F5F5F5;
}

/* ==================== 搜索栏 ==================== */
.search-bar {
	display: flex;
	padding: 20rpx 30rpx;
	background-color: #FFFFFF;
	gap: 20rpx;
	align-items: center;
	position: sticky;
	top: 0;
	z-index: 10;
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}

.search-input {
	flex: 1;
	height: 80rpx;
	padding: 0 28rpx;
	background-color: #F8F8F8;
	border-radius: 16rpx;
	font-size: 30rpx;
}

.add-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 0 32rpx;
	height: 80rpx;
	background: linear-gradient(135deg, #4A90E2, #357ABD);
	color: #FFFFFF;
	border-radius: 16rpx;
	font-size: 28rpx;
	font-weight: 600;
	white-space: nowrap;

	&:active {
		transform: scale(0.96);
	}
}

.add-icon {
	font-size: 36rpx;
	font-weight: bold;
}

.add-text {
	font-size: 28rpx;
}

/* ==================== 列表区域 ==================== */
.list-area {
	padding: 24rpx 30rpx;
	padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

/* 空状态 */
.empty {
	text-align: center;
	padding: 200rpx 0;
}

.empty-icon {
	font-size: 120rpx;
	display: block;
	margin-bottom: 30rpx;
}

.empty-text {
	font-size: 34rpx;
	color: #999999;
	margin-top: 20rpx;
	display: block;
	font-weight: 500;
}

.empty-hint {
	font-size: 26rpx;
	color: #CCCCCC;
	margin-top: 16rpx;
	display: block;
}

/* ==================== 卡片样式 ==================== */
.card {
	background-color: #FFFFFF;
	border-radius: 20rpx;
	margin-bottom: 24rpx;
	overflow: hidden;
	box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.06);
}

.card-body {
	padding: 32rpx;
}

/* 卡片头部 */
.card-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 24rpx;
}

.name {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
	flex: 1;
}

.status-tag {
	font-size: 22rpx;
	padding: 6rpx 18rpx;
	border-radius: 20rpx;
	font-weight: 500;

	&.active {
		background-color: #E8F5E9;
		color: #4CAF50;
	}

	&.inactive {
		background-color: #FFF3E0;
		color: #FF9800;
	}

	&.deleted {
		background-color: #FFEBEE;
		color: #F44336;
	}
}

/* 信息行 */
.info-row {
	display: flex;
	align-items: center;
	margin-bottom: 18rpx;

	&:last-child {
		margin-bottom: 0;
	}
}

.info-label {
	font-size: 26rpx;
	color: #999999;
	width: 120rpx;
	flex-shrink: 0;
}

.info-value {
	font-size: 28rpx;
	color: #333333;
	flex: 1;

	&.phone {
		color: #4A90E2;
		font-weight: 500;
	}

	&.origin {
		color: #666666;
	}
}

.call-btn {
	font-size: 36rpx;
	margin-left: 16rpx;
	padding: 8rpx;
}

/* 统计信息栏 */
.stats-row {
	display: flex;
	gap: 24rpx;
	margin-top: 28rpx;
	padding-top: 24rpx;
	border-top: 1rpx solid #F0F0F0;
}

.stat-item {
	flex: 1;
	text-align: center;
}

.stat-label {
	font-size: 22rpx;
	color: #999999;
	display: block;
	margin-bottom: 8rpx;
}

.stat-value {
	font-size: 28rpx;
	color: #333333;
	font-weight: 600;

	&.purchase {
		color: #4A90E2;
	}

	&.debt {
		color: #FF5722;
	}
}

/* 操作按钮区 */
.card-actions {
	display: flex;
	gap: 16rpx;
	padding: 20rpx 32rpx;
	border-top: 1rpx solid #F0F0F0;
	background-color: #FAFAFA;
}

.action-btn {
	flex: 1;
	height: 72rpx;
	line-height: 72rpx;
	text-align: center;
	border-radius: 12rpx;
	font-size: 26rpx;
	font-weight: 500;

	&:active {
		transform: scale(0.96);
	}

	&.edit {
		background-color: #E3F2FD;
		color: #4A90E2;
	}

	&.toggle {
		background-color: #FFF3E0;
		color: #FF9800;
	}

	&.delete {
		background-color: #FFEBEE;
		color: #F44336;
	}
}

/* ==================== 弹窗样式 ==================== */
.popup-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.5);
	z-index: 200;
}

.popup-container {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: #FFFFFF;
	border-radius: 32rpx 32rpx 0 0;
	z-index: 300;
	transform: translateY(100%);
	transition: transform 0.3s ease-out;
	max-height: 85vh;

	&.show {
		transform: translateY(0);
	}
}

.popup-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 36rpx 32rpx;
	border-bottom: 1rpx solid #EEEEEE;
}

.popup-title {
	font-size: 36rpx;
	color: #333333;
	font-weight: 600;
}

.popup-close {
	font-size: 52rpx;
	color: #999999;
	line-height: 1;
	padding: 10rpx;
}

.popup-content {
	padding: 32rpx;
	max-height: calc(85vh - 120rpx);
	overflow-y: auto;
}

/* 表单组 */
.form-group {
	margin-bottom: 28rpx;
}

.label {
	font-size: 28rpx;
	color: #333333;
	margin-bottom: 14rpx;
	display: block;
	font-weight: 500;

	&.required::before {
		content: '*';
		color: #F44336;
		margin-right: 8rpx;
	}
}

.input {
	width: 100%;
	height: 92rpx;
	padding: 0 28rpx;
	background-color: #F8F8F8;
	border-radius: 16rpx;
	font-size: 30rpx;
	color: #333333;
	box-sizing: border-box;

	&:focus {
		background-color: #FFFFFF;
		border: 2rpx solid #4A90E2;
	}
}

/* 弹窗按钮 */
.popup-btns {
	display: flex;
	gap: 20rpx;
	margin-top: 40rpx;
	padding-top: 20rpx;
}

.btn {
	flex: 1;
	height: 96rpx;
	line-height: 96rpx;
	text-align: center;
	border-radius: 16rpx;
	font-size: 32rpx;
	font-weight: 600;

	&:active {
		transform: scale(0.98);
	}

	&.cancel {
		background-color: #F5F5F5;
		color: #666666;
	}

	&.submit {
		background: linear-gradient(135deg, #4A90E2, #357ABD);
		color: #FFFFFF;

		&.disabled {
			background: linear-gradient(135deg, #CCCCCC, #BBBBBB);
			opacity: 0.6;
		}
	}
}
</style>