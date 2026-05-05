<template>
	<view class="container">
		<!-- 搜索 -->
		<view class="search-bar">
			<input class="search-input" v-model="keyword" placeholder="搜索商品名称" @confirm="loadList" />
			<view class="add-btn" @click="openAdd">+ 新增</view>
		</view>

		<!-- 列表 -->
		<scroll-view scroll-y class="list-area" :refresher-enabled="true" :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
			<view class="empty" v-if="list.length === 0 && !loading">
				<text class="empty-icon">🏷️</text>
				<text class="empty-text">暂无商品种类</text>
				<text class="empty-hint">点击上方按钮添加</text>
			</view>

			<view class="card" v-for="item in list" :key="item._id">
				<view class="card-header">
					<text class="name">{{ item.name }}</text>
					<text class="unit">{{ item.default_unit || '斤' }}</text>
				</view>
				<view class="card-actions">
					<view class="act edit" @click="openEdit(item)">编辑</view>
					<view class="act del" @click="doDelete(item)">删除</view>
				</view>
			</view>
		</scroll-view>

		<!-- 新增/编辑弹窗 -->
		<view class="mask" v-if="showPopup" @click="showPopup = false"></view>
		<view class="popup" :class="{ show: showPopup }">
			<view class="popup-title">{{ editingId ? '编辑' : '新增' }}商品种类</view>
			<view class="form-group">
				<text class="label required">商品名称</text>
				<input class="input" v-model="form.name" placeholder="如：红富士苹果" maxlength="20" />
			</view>
			<view class="form-group">
				<text class="label">默认单位</text>
				<input class="input" v-model="form.default_unit" placeholder="如：斤、箱、筐" maxlength="10" />
			</view>
			<view class="popup-btns">
				<view class="btn cancel" @click="showPopup = false">取消</view>
				<view class="btn submit" :class="{ disabled: !form.name.trim() }" @click="submit">保存</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { productAPI, specAPI } from '@/utils/api.js'

const keyword = ref('')
const list = ref([])
const loading = ref(false)
const refreshing = ref(false)
const showPopup = ref(false)
const editingId = ref('')
const form = ref({ name: '', default_unit: '斤' })

const loadList = async (isRefresh = false) => {
	if (loading.value) return
	loading.value = true
	if (isRefresh) list.value = []
	try {
		const res = await productAPI.list({ keyword: keyword.value })
		if (res.code === 0) list.value = res.data.list || []
	} catch (e) { uni.showToast({ title: '加载失败', icon: 'none' }) }
	finally { loading.value = false; refreshing.value = false }
}

const onRefresh = () => { refreshing.value = true; loadList(true) }
const openAdd = () => { editingId.value = ''; form.value = { name: '', default_unit: '斤' }; showPopup.value = true }
const openEdit = (item) => { editingId.value = item._id; form.value = { name: item.name, default_unit: item.default_unit || '斤' }; showPopup.value = true }

const submit = async () => {
	if (!form.value.name.trim()) return
	try {
		uni.showLoading({ title: '保存中' })
		let res
		if (editingId.value) {
			// 更新商品种类
			res = await specAPI.manage({ action: 'product_update', category_id: editingId.value, ...form.value })
		} else {
			// 新增商品种类
			res = await productAPI.create({ ...form.value })
		}
		uni.hideLoading()
		if (res.code === 0) {
			uni.showToast({ title: '保存成功', icon: 'success' })
			showPopup.value = false
			loadList(true)
		} else {
			uni.showToast({ title: res.message, icon: 'none' })
		}
	} catch (e) { uni.hideLoading(); uni.showToast({ title: '保存失败', icon: 'none' }) }
}

const doDelete = async (item) => {
	const r = await uni.showModal({ title: '确认删除', content: `删除"${item.name}"后不可恢复` })
	if (!r.confirm) return
	await specAPI.manage({ action: 'product_delete', category_id: item._id })
	loadList(true)
}

onMounted(() => loadList(true))
</script>

<style lang="scss" scoped>
.container { min-height: 100vh; background: #F5F5F5; }
.search-bar { display: flex; padding: 20rpx 30rpx; background: #FFF; gap: 20rpx; align-items: center; }
.search-input { flex: 1; height: 72rpx; padding: 0 24rpx; background: #F8F8F8; border-radius: 12rpx; font-size: 28rpx; }
.add-btn { padding: 0 30rpx; height: 72rpx; line-height: 72rpx; background: #4A90E2; color: #FFF; border-radius: 12rpx; font-size: 28rpx; font-weight: 600; white-space: nowrap; }

.list-area { padding: 20rpx 30rpx; }
.empty { text-align: center; padding: 180rpx 0; }
.empty-icon { font-size: 90rpx; display: block; }
.empty-text { font-size: 30rpx; color: #999; margin-top: 20rpx; display: block; }
.empty-hint { font-size: 24rpx; color: #CCC; margin-top: 10rpx; }

.card { background: #FFF; border-radius: 14rpx; padding: 24rpx 28rpx; margin-bottom: 18rpx; display: flex; justify-content: space-between; align-items: center; }
.card-header { display: flex; align-items: center; gap: 16rpx; }
.name { font-size: 32rpx; color: #333; font-weight: 500; }
.unit { font-size: 22rpx; color: #999; background: #F0F0F0; padding: 4rpx 14rpx; border-radius: 8rpx; }
.card-actions { display: flex; gap: 16rpx; }
.act { padding: 10rpx 24rpx; border-radius: 8rpx; font-size: 24rpx; }
.act.edit { background: #E3F2FD; color: #4A90E2; }
.act.del { background: #FFEBEE; color: #F44336; }

.mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 200; }
.popup { position: fixed; left: 0; right: 0; bottom: 0; background: #FFF; border-radius: 24rpx 24rpx 0 0; z-index: 300; transform: translateY(100%); transition: .3s; padding: 30rpx; &.show { transform: translateY(0); } }
.popup-title { font-size: 34rpx; color: #333; font-weight: 600; margin-bottom: 30rpx; }
.form-group { margin-bottom: 24rpx; }
.label { font-size: 28rpx; color: #333; margin-bottom: 12rpx; display: block; &.required::before { content: '*'; color: #F44336; margin-right: 6rpx; } }
.input { width: 100%; height: 88rpx; padding: 0 22rpx; background: #F8F8F8; border-radius: 12rpx; font-size: 28rpx; }
.popup-btns { display: flex; gap: 18rpx; margin-top: 34rpx; }
.btn { flex: 1; height: 90rpx; line-height: 90rpx; text-align: center; border-radius: 14rpx; font-size: 30rpx; }
.btn.cancel { background: #F5F5F5; color: #666; }
.btn.submit { background: #4A90E2; color: #FFF; font-weight: 600; &.disabled { background: #CCC; } }
</style>
