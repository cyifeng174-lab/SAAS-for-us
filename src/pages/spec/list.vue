<template>
	<view class="spec-container">
		<!-- ==================== 顶部搜索区域 ==================== -->
		<view class="search-section">
			<view class="search-box">
				<text class="search-icon">🔍</text>
				<input
					class="search-input"
					v-model="searchKeyword"
					placeholder="搜索规格名称/值"
					@confirm="handleSearch"
					@input="debounceSearch"
				/>
				<text class="clear-btn" v-if="searchKeyword" @click="clearSearch">×</text>
			</view>
			<view class="add-btn" @click="openAddCategory">
				<text class="add-icon">+</text>
			</view>
		</view>

		<!-- ==================== 统计概览 ==================== -->
		<view class="stats-section">
			<view class="stat-item">
				<text class="stat-value">{{ specList.length }}</text>
				<text class="stat-label">规格类型</text>
			</view>
			<view class="stat-item">
				<text class="stat-value">{{ totalValues }}</text>
				<text class="stat-label">规格值</text>
			</view>
		</view>

		<!-- ==================== 规格列表 ==================== -->
		<scroll-view
			class="list-section"
			scroll-y
			:refresher-enabled="true"
			:refresher-triggered="isRefreshing"
			@refresherrefresh="onRefresh"
		>
			<view class="empty-state" v-if="specList.length === 0 && !isLoading">
				<text class="empty-icon">📏</text>
				<text class="empty-text">暂无规格数据</text>
				<text class="empty-hint">点击右上角 + 添加规格类型</text>
			</view>

			<view class="spec-list" v-else>
				<view
					class="spec-card"
					v-for="category in specList"
					:key="category._id"
				>
					<!-- 卡片头部 -->
					<view class="card-header">
						<view class="header-left">
							<text class="category-name">{{ category.name }}</text>
							<text class="category-unit" v-if="category.unit">{{ category.unit }}</text>
						</view>
						<view class="header-right">
							<view class="action-btn edit" @click="openEditCategory(category)">
								<text class="btn-text">编辑</text>
							</view>
							<view class="action-btn manage" @click="openValueManage(category)">
								<text class="btn-text">规格值</text>
							</view>
							<view class="action-btn link" @click="openLinkPage(category)">
								<text class="btn-text">关联</text>
							</view>
						</view>
					</view>

					<!-- 规格值标签 -->
					<view class="value-tags" v-if="category.values && category.values.length > 0">
						<view class="value-tag" v-for="v in category.values" :key="v._id">
							{{ v.label || v.value }}
						</view>
					</view>
					<view class="no-values" v-else>
						<text class="no-value-text">暂无规格值</text>
					</view>

					<!-- 底部信息 -->
					<view class="card-footer" v-if="category.remark">
						<text class="remark-text">备注：{{ category.remark }}</text>
					</view>
				</view>
			</view>
		</scroll-view>

		<!-- ==================== 新增/编辑规格类型弹窗 ==================== -->
		<view class="popup-mask" v-if="showCategoryPopup" @click="closeCategoryPopup"></view>
		<view class="popup-container category-popup" :class="{ show: showCategoryPopup }">
			<view class="popup-header">
				<text class="popup-title">{{ isEditing ? '编辑规格类型' : '新增规格类型' }}</text>
				<text class="popup-close" @click="closeCategoryPopup">×</text>
			</view>
			<view class="popup-content">
				<view class="form-group">
					<text class="form-label required">类型名称</text>
					<input class="form-input" v-model="categoryForm.name" placeholder="如：果径规格" maxlength="30" />
				</view>
				<view class="form-group">
					<text class="form-label">单位</text>
					<input class="form-input" v-model="categoryForm.unit" placeholder="如：mm、g、斤" maxlength="10" />
				</view>
				<view class="form-group">
					<text class="form-label">排序</text>
					<input class="form-input" v-model="categoryForm.sort_order" type="number" placeholder="数字越小越靠前" />
				</view>
				<view class="form-group">
					<text class="form-label">备注</text>
					<textarea class="form-textarea" v-model="categoryForm.remark" placeholder="备注信息" maxlength="200" />
				</view>
				<view class="form-actions">
					<view class="cancel-btn" @click="closeCategoryPopup">
						<text class="cancel-text">取消</text>
					</view>
					<view class="submit-btn" :class="{ disabled: !canSubmitCategory }" @click="submitCategory">
						<text class="submit-text">{{ isSubmitting ? '保存中...' : '保存' }}</text>
					</view>
				</view>
				<view class="danger-zone" v-if="isEditing">
					<view class="delete-btn" @click="confirmDeleteCategory">
						<text class="delete-text">删除此规格类型</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 规格值管理弹窗 ==================== -->
		<view class="popup-mask" v-if="showValuePopup" @click="closeValuePopup"></view>
		<view class="popup-container value-popup" :class="{ show: showValuePopup }">
			<view class="popup-header">
				<text class="popup-title">管理规格值 - {{ currentCategory?.name }}</text>
				<text class="popup-close" @click="closeValuePopup">×</text>
			</view>
			<view class="popup-content">
				<!-- 批量添加 -->
				<view class="batch-add-section">
					<text class="section-label">批量添加</text>
					<view class="batch-input-row">
						<input class="form-input batch-input" v-model="batchValuesStr" placeholder="逗号分隔，如：80,90,100" />
					</view>
					<view class="batch-input-row">
						<input class="form-input batch-input" v-model="batchLabelsStr" placeholder="标签（可选），逗号分隔" />
					</view>
					<view class="batch-btn" @click="handleBatchAdd">
						<text class="batch-btn-text">批量添加</text>
					</view>
				</view>

				<!-- 已有值列表 -->
				<view class="section-label">已有规格值（共 {{ currentValues.length }} 个）</view>
				<view class="value-list">
					<view class="value-item" v-for="v in currentValues" :key="v._id">
						<view class="value-info">
							<text class="value-text">{{ v.value }}</text>
							<text class="value-label" v-if="v.label !== v.value">（{{ v.label }}）</text>
						</view>
						<view class="value-actions">
							<view class="small-btn edit" @click="startEditValue(v)">
								<text class="small-btn-text">改</text>
							</view>
							<view class="small-btn delete" @click="handleDeleteValue(v)">
								<text class="small-btn-text">删</text>
							</view>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 编辑单个规格值弹窗 ==================== -->
		<view class="popup-mask" v-if="showEditValuePopup" @click="showEditValuePopup = false"></view>
		<view class="popup-container edit-value-popup" :class="{ show: showEditValuePopup }">
			<view class="popup-header">
				<text class="popup-title">编辑规格值</text>
				<text class="popup-close" @click="showEditValuePopup = false">×</text>
			</view>
			<view class="popup-content">
				<view class="form-group">
					<text class="form-label required">规格值</text>
					<input class="form-input" v-model="valueForm.value" maxlength="20" />
				</view>
				<view class="form-group">
					<text class="form-label">显示标签</text>
					<input class="form-input" v-model="valueForm.label" maxlength="20" />
				</view>
				<view class="form-actions">
					<view class="cancel-btn" @click="showEditValuePopup = false">
						<text class="cancel-text">取消</text>
					</view>
					<view class="submit-btn" @click="submitValueEdit">
						<text class="submit-text">保存</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ==================== 商品关联弹窗 ==================== -->
		<view class="popup-mask" v-if="showLinkPopup" @click="showLinkPopup = false"></view>
		<view class="popup-container link-popup" :class="{ show: showLinkPopup }">
			<view class="popup-header">
				<text class="popup-title">关联商品 - {{ currentCategory?.name }}</text>
				<text class="popup-close" @click="showLinkPopup = false">×</text>
			</view>
			<view class="popup-content">
				<!-- 选择已有商品种类 -->
				<view class="section-label">选择已有商品种类</view>
				<view class="product-chips" v-if="productCategories.length > 0">
					<view
						class="product-chip"
						:class="{ selected: linkForm.product_name === pc.name && !linkedProducts.find(l => l.product_name === pc.name) }"
						v-for="pc in availableProductCategories"
						:key="pc._id"
						@click="selectProductCategory(pc)"
					>
						<text class="chip-name">{{ pc.name }}</text>
						<text class="chip-linked" v-if="linkedProducts.find(l => l.product_name === pc.name)">已关联</text>
					</view>
				</view>
				<view class="no-products" v-else>
					<text class="no-product-text">暂无商品种类，请在下方添加</text>
				</view>

				<!-- 快速新增商品种类 -->
				<view class="quick-add-section">
					<text class="section-label">快速新增商品种类</text>
					<view class="quick-add-row">
						<input class="form-input quick-input" v-model="newProductName" placeholder="输入新商品名称" maxlength="20" />
						<view class="quick-add-btn" @click="quickAddProduct">
							<text class="quick-add-text">添加</text>
						</view>
					</view>
				</view>

				<!-- 已选商品 -->
				<view class="selected-product" v-if="linkForm.product_name">
					<text class="selected-label">已选商品：</text>
					<text class="selected-name">{{ linkForm.product_name }}</text>
					<text class="selected-clear" @click="linkForm.product_name = ''">×</text>
				</view>

				<view class="form-group">
					<view class="checkbox-row" @click="linkForm.is_required = !linkForm.is_required">
						<view class="checkbox-box" :class="{ checked: linkForm.is_required }">
							<text class="check-mark" v-if="linkForm.is_required">✓</text>
						</view>
						<text class="checkbox-label">该规格为必选项</text>
					</view>
				</view>
				<view class="form-actions">
					<view class="cancel-btn" @click="showLinkPopup = false">
						<text class="cancel-text">取消</text>
					</view>
					<view class="submit-btn" :class="{ disabled: !linkForm.product_name }" @click="submitLink">
						<text class="submit-text">关联</text>
					</view>
				</view>

				<!-- 已有关联 -->
				<view class="section-label" style="margin-top: 30rpx;" v-if="linkedProducts.length > 0">
					已关联商品（{{ linkedProducts.length }} 个）
				</view>
				<view class="linked-list" v-if="linkedProducts.length > 0">
					<view class="linked-item" v-for="link in linkedProducts" :key="link._id">
						<text class="linked-name">{{ link.product_name }}</text>
						<text class="linked-required" v-if="link.is_required">必选</text>
						<view class="small-btn delete" @click="handleDeleteLink(link)">
							<text class="small-btn-text">解除</text>
						</view>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showLoading, hideLoading, showSuccess, showError, showConfirm, debounce } from '@/utils/util.js'
import { specAPI, productAPI } from '@/utils/api.js'

// ==================== 响应式数据 ====================
const searchKeyword = ref('')
const specList = ref([])
const isLoading = ref(false)
const isRefreshing = ref(false)
const isSubmitting = ref(false)

// 类型弹窗
const showCategoryPopup = ref(false)
const isEditing = ref(false)
const editingCategoryId = ref('')
const categoryForm = ref({ name: '', unit: '', sort_order: 0, remark: '' })

// 规格值弹窗
const showValuePopup = ref(false)
const currentCategory = ref(null)
const currentValues = ref([])
const batchValuesStr = ref('')
const batchLabelsStr = ref('')

// 编辑单个值
const showEditValuePopup = ref(false)
const editingValueId = ref('')
const valueForm = ref({ value: '', label: '' })

// 关联弹窗
const showLinkPopup = ref(false)
const linkedProducts = ref([])
const linkForm = ref({ product_name: '', is_required: false })

// 商品种类
const productCategories = ref([])
const newProductName = ref('')

// ==================== 计算属性 ====================
const totalValues = computed(() => {
	let total = 0
	specList.value.forEach(c => { total += (c.values || []).length })
	return total
})

const availableProductCategories = computed(() => {
	return productCategories.value
})

const canSubmitCategory = computed(() => {
	return categoryForm.value.name.trim() !== '' && !isSubmitting.value
})

// ==================== 方法 ====================
const loadSpecList = async (isRefresh = false) => {
	if (isLoading.value) return
	try {
		isLoading.value = true
		if (isRefresh) specList.value = []

		const res = await specAPI.list({ keyword: searchKeyword.value })

		console.log('规格列表返回:', res)
		if (res.code === 0) {
			specList.value = res.data.list || []
		} else {
			showError(res.message || '加载失败')
		}
	} catch (error) {
		showError('加载失败：' + error.message)
		console.error('加载规格列表失败：', error)
	} finally {
		isLoading.value = false
		isRefreshing.value = false
	}
}

const debounceSearch = debounce(() => { loadSpecList(true) }, 400)
const handleSearch = () => { loadSpecList(true) }
const clearSearch = () => { searchKeyword.value = ''; loadSpecList(true) }
const onRefresh = () => { isRefreshing.value = true; loadSpecList(true) }

// ==================== 分类操作 ====================
const openAddCategory = () => {
	isEditing.value = false
	editingCategoryId.value = ''
	categoryForm.value = { name: '', unit: '', sort_order: 0, remark: '' }
	showCategoryPopup.value = true
}

const openEditCategory = (category) => {
	isEditing.value = true
	editingCategoryId.value = category._id
	categoryForm.value = {
		name: category.name || '',
		unit: category.unit || '',
		sort_order: category.sort_order || 0,
		remark: category.remark || ''
	}
	showCategoryPopup.value = true
}

const closeCategoryPopup = () => { showCategoryPopup.value = false }

const submitCategory = async () => {
	if (!canSubmitCategory.value) return
	try {
		isSubmitting.value = true
		showLoading('保存中...')
		const action = isEditing.value ? 'category_update' : 'category_add'
		const data = { action, ...categoryForm.value }
		if (isEditing.value) data.category_id = editingCategoryId.value

		const res = await specAPI.manage(data)
		hideLoading()

		if (res.code === 0) {
			showSuccess(isEditing.value ? '更新成功' : '新增成功')
			closeCategoryPopup()
			loadSpecList(true)
		} else {
			showError(res.message || '操作失败')
		}
	} catch (error) {
		hideLoading()
		showError('操作失败：' + error.message)
	} finally {
		isSubmitting.value = false
	}
}

const confirmDeleteCategory = async () => {
	const confirmed = await showConfirm('删除后将同时删除该类型下的所有规格值和关联关系，确定继续？', '确认删除')
	if (!confirmed) return
	try {
		showLoading('删除中...')
		const res = await specAPI.manage({ action: 'category_delete', category_id: editingCategoryId.value })
		hideLoading()
		if (res.code === 0) {
			showSuccess('删除成功')
			closeCategoryPopup()
			loadSpecList(true)
		} else {
			showError(res.message || '删除失败')
		}
	} catch (error) {
		hideLoading()
		showError('删除失败：' + error.message)
	}
}

// ==================== 规格值操作 ====================
const openValueManage = (category) => {
	currentCategory.value = category
	currentValues.value = [...(category.values || [])]
	batchValuesStr.value = ''
	batchLabelsStr.value = ''
	showValuePopup.value = true
}

const closeValuePopup = () => {
	showValuePopup.value = false
	loadSpecList(true)
}

const handleBatchAdd = async () => {
	if (!batchValuesStr.value.trim()) {
		showError('请输入规格值')
		return
	}
	try {
		showLoading('添加中...')
		const res = await specAPI.manage({
			action: 'batch_add_values',
			category_id: currentCategory.value._id,
			values_str: batchValuesStr.value,
			labels_str: batchLabelsStr.value
		})
		hideLoading()
		if (res.code === 0) {
			const d = res.data
			showSuccess(`新增 ${d.added} 条，跳过 ${d.skipped} 条重复`)
			batchValuesStr.value = ''
			batchLabelsStr.value = ''
			// 刷新当前分类的值
			const refreshRes = await specAPI.list()
			if (refreshRes.code === 0) {
				const found = (refreshRes.data.list || []).find(c => c._id === currentCategory.value._id)
				if (found) {
					currentCategory.value = found
					currentValues.value = [...(found.values || [])]
				}
			}
		} else {
			showError(res.message || '添加失败')
		}
	} catch (error) {
		hideLoading()
		showError('添加失败：' + error.message)
	}
}

const startEditValue = (v) => {
	editingValueId.value = v._id
	valueForm.value = { value: v.value, label: v.label || '' }
	showEditValuePopup.value = true
}

const submitValueEdit = async () => {
	if (!valueForm.value.value.trim()) { showError('规格值不能为空'); return }
	try {
		showLoading('保存中...')
		const res = await specAPI.manage({
			action: 'value_update',
			value_id: editingValueId.value,
			...valueForm.value
		})
		hideLoading()
		if (res.code === 0) {
			showSuccess('更新成功')
			showEditValuePopup.value = false
			// 刷新当前分类的值
			const refreshRes = await specAPI.list()
			if (refreshRes.code === 0) {
				const found = (refreshRes.data.list || []).find(c => c._id === currentCategory.value._id)
				if (found) {
					currentCategory.value = found
					currentValues.value = [...(found.values || [])]
				}
			}
		} else {
			showError(res.message || '更新失败')
		}
	} catch (error) {
		hideLoading()
		showError('更新失败：' + error.message)
	}
}

const handleDeleteValue = async (v) => {
	const confirmed = await showConfirm(`确定删除规格值"${v.value}"？`, '确认删除')
	if (!confirmed) return
	try {
		showLoading('删除中...')
		const res = await specAPI.manage({ action: 'value_delete', value_id: v._id })
		hideLoading()
		if (res.code === 0) {
			showSuccess('删除成功')
			const refreshRes = await specAPI.list()
			if (refreshRes.code === 0) {
				const found = (refreshRes.data.list || []).find(c => c._id === currentCategory.value._id)
				if (found) {
					currentCategory.value = found
					currentValues.value = [...(found.values || [])]
				}
			}
		} else {
			showError(res.message || '删除失败')
		}
	} catch (error) {
		hideLoading()
		showError('删除失败：' + error.message)
	}
}

// ==================== 关联操作 ====================
const loadProductCategories = async () => {
	try {
		const res = await productAPI.list()
		if (res.code === 0) {
			productCategories.value = res.data.list || []
		}
	} catch (error) {
		console.error('加载商品种类失败：', error)
	}
}

const selectProductCategory = (pc) => {
	if (linkedProducts.value.find(l => l.product_name === pc.name)) {
		showError('该商品已关联此规格类型')
		return
	}
	linkForm.value.product_name = pc.name
}

const quickAddProduct = async () => {
	const name = newProductName.value.trim()
	if (!name) { showError('请输入商品名称'); return }
	try {
		showLoading('添加中...')
		const res = await productAPI.create({ name })
		hideLoading()
		if (res.code === 0) {
			showSuccess('添加成功')
			linkForm.value.product_name = name
			newProductName.value = ''
			await loadProductCategories()
		} else {
			showError(res.message || '添加失败')
		}
	} catch (error) {
		hideLoading()
		showError('添加失败：' + error.message)
	}
}

const openLinkPage = async (category) => {
	currentCategory.value = category
	linkForm.value = { product_name: '', is_required: false }
	showLinkPopup.value = true
	await loadProductCategories()
	const res = await specAPI.manage({ action: 'get_link', product_name: '' })
	if (res.code === 0) {
		linkedProducts.value = (res.data.links || []).filter(l => l.category_id === category._id)
	}
}

const submitLink = async () => {
	if (!linkForm.value.product_name.trim()) { showError('请选择商品种类'); return }
	try {
		showLoading('关联中...')
		const res = await specAPI.manage({
			action: 'link_product',
			product_name: linkForm.value.product_name,
			category_id: currentCategory.value._id,
			is_required: linkForm.value.is_required
		})
		hideLoading()
		if (res.code === 0) {
			showSuccess('关联成功')
			linkForm.value.product_name = ''
			await loadProductCategories()
			const linkRes = await specAPI.manage({ action: 'get_link', product_name: '' })
			if (linkRes.code === 0) {
				linkedProducts.value = (linkRes.data.links || []).filter(l => l.category_id === currentCategory.value._id)
			}
		} else {
			showError(res.message || '关联失败')
		}
	} catch (error) {
		hideLoading()
		showError('关联失败：' + error.message)
	}
}

const handleDeleteLink = async (link) => {
	const confirmed = await showConfirm(`确定解除"${link.product_name}"的关联？`, '确认解除')
	if (!confirmed) return
	try {
		showLoading('解除中...')
		const res = await specAPI.manage({ action: 'unlink_product', link_id: link._id })
		hideLoading()
		if (res.code === 0) {
			showSuccess('已解除关联')
			linkedProducts.value = linkedProducts.value.filter(l => l._id !== link._id)
		} else {
			showError(res.message || '操作失败')
		}
	} catch (error) {
		hideLoading()
		showError('操作失败：' + error.message)
	}
}

// ==================== 生命周期 ====================
onMounted(() => { loadSpecList(true) })
</script>

<style lang="scss" scoped>
.spec-container {
	min-height: 100vh;
	background-color: #F8F8F8;
	display: flex;
	flex-direction: column;
}

/* ==================== 顶部搜索 ==================== */
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
.search-icon { font-size: 28rpx; margin-right: 15rpx; }
.search-input { flex: 1; height: 80rpx; font-size: 28rpx; color: #333333; }
.clear-btn { font-size: 36rpx; color: #CCCCCC; padding: 10rpx; }
.add-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 80rpx;
	height: 80rpx;
	background-color: #4A90E2;
	border-radius: 50%;
}
.add-icon { font-size: 40rpx; color: #FFFFFF; font-weight: 600; }

/* ==================== 统计 ==================== */
.stats-section {
	display: flex;
	background-color: #FFFFFF;
	padding: 30rpx;
	margin-bottom: 20rpx;
}
.stat-item { flex: 1; display: flex; flex-direction: column; align-items: center; }
.stat-value { font-size: 36rpx; color: #4A90E2; font-weight: 600; margin-bottom: 10rpx; }
.stat-label { font-size: 24rpx; color: #999999; }

/* ==================== 列表 ==================== */
.list-section { flex: 1; padding: 0 30rpx 30rpx; }

.empty-state {
	display: flex; flex-direction: column; align-items: center;
	justify-content: center; padding: 150rpx 0;
}
.empty-icon { font-size: 100rpx; margin-bottom: 30rpx; }
.empty-text { font-size: 32rpx; color: #999999; margin-bottom: 15rpx; }
.empty-hint { font-size: 26rpx; color: #CCCCCC; }

.spec-card {
	background-color: #FFFFFF;
	border-radius: 16rpx;
	padding: 30rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);
}
.card-header {
	display: flex; align-items: center; justify-content: space-between;
	margin-bottom: 20rpx;
}
.header-left { display: flex; align-items: center; gap: 15rpx; }
.category-name { font-size: 36rpx; color: #333333; font-weight: 600; }
.category-unit { font-size: 24rpx; color: #999999; background: #F0F0F0; padding: 4rpx 16rpx; border-radius: 8rpx; }
.header-right { display: flex; gap: 15rpx; }
.action-btn {
	padding: 10rpx 24rpx;
	border-radius: 8rpx;
	&.edit { background-color: #E3F2FD; }
	&.manage { background-color: #FFF3E0; }
	&.link { background-color: #E8F5E9; }
}
.btn-text { font-size: 26rpx; color: #4A90E2; }

.value-tags { display: flex; flex-wrap: wrap; gap: 15rpx; margin-bottom: 15rpx; }
.value-tag {
	font-size: 24rpx;
	color: #666666;
	background-color: #F5F5F5;
	padding: 8rpx 20rpx;
	border-radius: 20rpx;
}
.no-values { margin-bottom: 15rpx; }
.no-value-text { font-size: 24rpx; color: #CCCCCC; }

.card-footer { border-top: 1rpx solid #F0F0F0; padding-top: 15rpx; }
.remark-text { font-size: 24rpx; color: #999999; }

/* ==================== 弹窗通用 ==================== */
.popup-mask {
	position: fixed; top: 0; left: 0; right: 0; bottom: 0;
	background-color: rgba(0,0,0,0.5); z-index: 200;
}
.popup-container {
	position: fixed; left: 0; right: 0; bottom: 0;
	background-color: #FFFFFF; border-radius: 24rpx 24rpx 0 0;
	z-index: 300; transform: translateY(100%); transition: transform 0.3s;
	&.show { transform: translateY(0); }
}
.popup-header {
	display: flex; align-items: center; justify-content: space-between;
	padding: 30rpx; border-bottom: 1rpx solid #EEEEEE;
}
.popup-title { font-size: 32rpx; color: #333333; font-weight: 600; }
.popup-close { font-size: 48rpx; color: #999999; line-height: 1; }
.popup-content { max-height: 60vh; overflow-y: auto; padding: 30rpx; }

/* ==================== 表单 ==================== */
.form-group { margin-bottom: 30rpx; }
.form-label { font-size: 28rpx; color: #333333; margin-bottom: 15rpx; display: block;
	&.required::before { content: '*'; color: #F44336; margin-right: 8rpx; }
}
.form-input {
	width: 100%; height: 90rpx; padding: 0 25rpx;
	background-color: #F8F8F8; border-radius: 12rpx;
	font-size: 28rpx; color: #333333;
}
.form-textarea {
	width: 100%; min-height: 150rpx; padding: 20rpx 25rpx;
	background-color: #F8F8F8; border-radius: 12rpx;
	font-size: 28rpx; color: #333333;
}
.form-actions {
	display: flex; gap: 20rpx; margin-top: 40rpx;
}
.cancel-btn {
	flex: 1; height: 100rpx; display: flex; align-items: center;
	justify-content: center; background-color: #F8F8F8; border-radius: 12rpx;
}
.cancel-text { font-size: 32rpx; color: #666666; }
.submit-btn {
	flex: 2; height: 100rpx; display: flex; align-items: center;
	justify-content: center; background-color: #4A90E2; border-radius: 12rpx;
	&.disabled { background-color: #CCCCCC; }
}
.submit-text { font-size: 34rpx; color: #FFFFFF; font-weight: 600; }

.danger-zone { margin-top: 30rpx; }
.delete-btn {
	width: 100%; height: 80rpx; display: flex; align-items: center;
	justify-content: center; background-color: #FFEBEE; border-radius: 12rpx;
}
.delete-text { font-size: 28rpx; color: #F44336; }

/* ==================== 批量添加 ==================== */
.batch-add-section {
	background-color: #F0F8FF; padding: 25rpx; border-radius: 12rpx; margin-bottom: 30rpx;
}
.section-label { font-size: 28rpx; color: #333333; font-weight: 600; margin-bottom: 20rpx; }
.batch-input-row { margin-bottom: 15rpx; }
.batch-input { height: 70rpx; }
.batch-btn {
	width: 100%; height: 70rpx; display: flex; align-items: center;
	justify-content: center; background-color: #4A90E2; border-radius: 12rpx;
}
.batch-btn-text { font-size: 28rpx; color: #FFFFFF; font-weight: 500; }

/* ==================== 值列表 ==================== */
.value-list { margin-bottom: 20rpx; }
.value-item {
	display: flex; align-items: center; justify-content: space-between;
	padding: 20rpx 0; border-bottom: 1rpx solid #F0F0F0;
}
.value-info { display: flex; align-items: center; }
.value-text { font-size: 30rpx; color: #333333; font-weight: 500; }
.value-label { font-size: 24rpx; color: #999999; margin-left: 8rpx; }
.value-actions { display: flex; gap: 15rpx; }
.small-btn {
	padding: 8rpx 20rpx; border-radius: 8rpx;
	&.edit { background-color: #E3F2FD; }
	&.delete { background-color: #FFEBEE; }
}
.small-btn-text { font-size: 24rpx; color: #4A90E2;
	.delete & { color: #F44336; }
}

/* ==================== 关联 ==================== */
.checkbox-row { display: flex; align-items: center; }
.checkbox-box {
	width: 40rpx; height: 40rpx; border: 2rpx solid #CCCCCC; border-radius: 8rpx;
	display: flex; align-items: center; justify-content: center; margin-right: 15rpx;
	&.checked { background-color: #4A90E2; border-color: #4A90E2; }
}
.check-mark { font-size: 28rpx; color: #FFFFFF; }
.checkbox-label { font-size: 28rpx; color: #333333; }

/* 商品种类选择 */
.product-chips {
	display: flex; flex-wrap: wrap; gap: 15rpx; margin-bottom: 20rpx;
}
.product-chip {
	padding: 14rpx 28rpx; border-radius: 12rpx;
	background-color: #F5F5F5; border: 2rpx solid transparent;
	&.selected { background-color: #E3F2FD; border-color: #4A90E2; }
}
.chip-name { font-size: 26rpx; color: #333333;
	.selected & { color: #4A90E2; font-weight: 500; }
}
.chip-linked { font-size: 20rpx; color: #999999; margin-left: 8rpx; }
.no-products { margin-bottom: 20rpx; }
.no-product-text { font-size: 24rpx; color: #CCCCCC; }

.quick-add-section {
	background-color: #FFFBE6; padding: 25rpx; border-radius: 12rpx; margin-bottom: 25rpx;
}
.quick-add-row { display: flex; gap: 15rpx; align-items: center; }
.quick-input { flex: 1; height: 70rpx; }
.quick-add-btn {
	padding: 0 30rpx; height: 70rpx; display: flex; align-items: center;
	justify-content: center; background-color: #4A90E2; border-radius: 12rpx;
	white-space: nowrap;
}
.quick-add-text { font-size: 26rpx; color: #FFFFFF; font-weight: 500; }

.selected-product {
	display: flex; align-items: center; padding: 20rpx;
	background-color: #E8F5E9; border-radius: 12rpx; margin-bottom: 25rpx;
}
.selected-label { font-size: 24rpx; color: #999999; }
.selected-name { font-size: 28rpx; color: #2E7D32; font-weight: 600; margin: 0 15rpx; }
.selected-clear { font-size: 32rpx; color: #999999; margin-left: auto; padding: 10rpx; }

.linked-list { margin-top: 20rpx; }
.linked-item {
	display: flex; align-items: center; justify-content: space-between;
	padding: 20rpx 0; border-bottom: 1rpx solid #F0F0F0;
}
.linked-name { font-size: 28rpx; color: #333333; flex: 1; }
.linked-required { font-size: 22rpx; color: #F44336; background: #FFEBEE;
	padding: 4rpx 12rpx; border-radius: 8rpx; margin-right: 15rpx; }

/* ==================== 编辑单个值弹窗 ==================== */
.edit-value-popup {
	max-height: 50vh;
	.popup-content { max-height: 40vh; }
}
</style>
