'use strict';

/**
 * 云函数：加工入库与成本分摊（process_and_stock_in）
 * 
 * 功能说明：
 * 1. 将原果加工成成品，按重量比例分摊成本
 * 2. 更新库存记录（支持移动加权平均成本计算）
 * 3. 生成加工单记录
 * 4. 更新采购单状态
 * 
 * 数据一致性：使用 uniCloud 数据库事务保证
 */

const db = uniCloud.database();
const dbCmd = db.command;

/**
 * 生成批次ID
 * 格式：BATCH + 时间戳 + 随机数
 */
function generateBatchId() {
	const timestamp = Date.now();
	const random = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
	return `BATCH${timestamp}${random}`;
}

/**
 * 生成加工单号
 * 格式：JG + 年月日 + 4位序号
 * 
 * 注意：在 SaaS 多租户环境下，建议在 order_no 字段上加唯一索引
 * 防止并发请求生成重复单号
 */
async function generateOrderNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = `JG${dateStr}`;
	
	// 查询当日最大序号
	// FIXME: 第二期 SaaS 化时，建议在 processing_orders 表的 order_no 字段加唯一索引
	// 防止并发请求导致单号重复
	const countResult = await transaction.collection('processing_orders')
		.where({
			order_no: new RegExp(`^${prefix}`)
		})
		.count();
	
	const seq = (countResult.total + 1).toString().padStart(4, '0');
	return `${prefix}${seq}`;
}

/**
 * 云函数入口
 */
exports.main = async (event, context) => {
	// 解构入参
	const {
		purchase_id,
		costs = {},
		outputs = [],
		operator_id = '',
		operator_name = ''
	} = event;
	
	// 入参校验
	if (!purchase_id) {
		return {
			code: 400,
			message: '缺少必要参数：purchase_id'
		};
	}
	
	if (!outputs || outputs.length === 0) {
		return {
			code: 400,
			message: '产出成品列表不能为空'
		};
	}
	
	// 校验每个产出项的必要字段
	for (let i = 0; i < outputs.length; i++) {
		const item = outputs[i];
		if (!item.product_name) {
			return {
				code: 400,
				message: `第${i + 1}个产出项缺少 product_name`
			};
		}
		if (!item.weight_jin || item.weight_jin <= 0) {
			return {
				code: 400,
				message: `第${i + 1}个产出项的 weight_jin 必须大于0`
			};
		}
	}
	
	// 提取各项加工费用（单位：分，默认为0）
	const laborCostFen = parseInt(costs.labor_cost_fen) || 0;
	const packagingCostFen = parseInt(costs.packaging_cost_fen) || 0;
	const transportCostFen = parseInt(costs.transport_cost_fen) || 0;
	const otherCostFen = parseInt(costs.other_cost_fen) || 0;
	
	// 计算加工总成本
	const totalProcessingCostFen = laborCostFen + packagingCostFen + transportCostFen + otherCostFen;
	
	try {
		// 使用事务执行所有数据库操作
		const result = await db.runTransaction(async (transaction) => {
			// ==================== 第一步：查询采购单信息 ====================
			const purchaseRes = await transaction.collection('purchases')
				.doc(purchase_id)
				.get();
			
			if (!purchaseRes.data || purchaseRes.data.length === 0) {
				throw new Error(`采购单不存在：${purchase_id}`);
			}
			
			const purchase = purchaseRes.data[0];
			
			// 校验采购单状态
			if (purchase.status === 'completed') {
				throw new Error('该采购单已完成加工，不能重复操作');
			}
			
			if (purchase.status === 'cancelled') {
				throw new Error('该采购单已作废，无法进行加工');
			}
			
			// 获取原果采购总成本（单位：分）
			const purchaseCostFen = purchase.total_amount_fen || 0;
			
			// 获取原果重量
			const inputWeightJin = purchase.weight_jin || 0;
			
			// ==================== 第二步：计算总成本和总产出重量 ====================
			// 总成本 = 原果采购成本 + 各项加工费用
			const totalCostFen = purchaseCostFen + totalProcessingCostFen;
			
			// 计算总产出重量
			const totalOutputWeightJin = outputs.reduce((sum, item) => {
				return sum + (parseFloat(item.weight_jin) || 0);
			}, 0);
			
			// 校验总产出重量
			if (totalOutputWeightJin <= 0) {
				throw new Error('总产出重量必须大于0');
			}
			
			// 计算损耗
			const lossWeightJin = inputWeightJin - totalOutputWeightJin;
			const lossRate = inputWeightJin > 0 ? (lossWeightJin / inputWeightJin * 100) : 0;
			
			// ==================== 第三步：成本按重量分摊 ====================
			// 遍历产出成品，计算每个成品的分摊成本
			let allocatedTotalFen = 0; // 已分摊成本累计（用于处理舍入误差）
			const outputProducts = outputs.map((item, index) => {
				const weightJin = parseFloat(item.weight_jin) || 0;
				
				// 计算重量占比
				const weightRatio = weightJin / totalOutputWeightJin;
				
				// 计算分摊成本（最后一个使用差额法，避免舍入误差）
				let allocatedCostFen;
				if (index === outputs.length - 1) {
					// 最后一个：总成本 - 已分摊累计
					allocatedCostFen = totalCostFen - allocatedTotalFen;
				} else {
					// 向下取整
					allocatedCostFen = Math.floor(totalCostFen * weightRatio);
					allocatedTotalFen += allocatedCostFen;
				}
				
				// 计算单位成本（分/斤），向下取整
				const unitCostFen = weightJin > 0 ? Math.floor(allocatedCostFen / weightJin) : 0;
				
				return {
					product_name: item.product_name,
					fruit_name: item.fruit_name || purchase.fruit_name || '',
					grade: item.grade || '',
					spec: item.spec || '',
					origin: item.origin || purchase.origin || '',
					weight_jin: weightJin,
					weight_ratio: weightRatio,
					allocated_cost_fen: allocatedCostFen,
					unit_cost_fen: unitCostFen,
					inventory_id: '' // 后续更新
				};
			});
			
			// ==================== 第四步：先生成加工单，获取真实 ID ====================
			// 关键：必须先插入加工单获取真实的 _id，再用于库存批次的关联
			const currentTime = Date.now();
			const orderNo = await generateOrderNo(transaction);
			
			// FIXME: 第二期 SaaS 化时需从 context/token 中获取 tenant_id
			const tenantId = 'default';
			
			// 构建加工单数据（此时 inventory_id 还是空的，后面更新）
			const processingOrder = {
				tenant_id: tenantId,
				order_no: orderNo,
				purchase_id: purchase_id,
				purchase_batch_no: purchase.batch_no,
				fruit_name: purchase.fruit_name,
				input_weight_jin: inputWeightJin,
				labor_cost_fen: laborCostFen,
				packaging_cost_fen: packagingCostFen,
				transport_cost_fen: transportCostFen,
				other_cost_fen: otherCostFen,
				total_processing_cost_fen: totalProcessingCostFen,
				purchase_cost_fen: purchaseCostFen,
				total_cost_fen: totalCostFen,
				output_products: outputProducts,
				total_output_weight_jin: totalOutputWeightJin,
				loss_weight_jin: lossWeightJin,
				loss_rate: parseFloat(lossRate.toFixed(2)),
				processing_date: currentTime,
				status: 'completed',
				remark: '',
				create_time: currentTime,
				update_time: currentTime,
				operator_id: operator_id,
				operator_name: operator_name
			};
			
			// 插入加工单，获取真实的数据库 _id
			const addOrderRes = await transaction.collection('processing_orders').add(processingOrder);
			const realProcessingOrderId = addOrderRes.id;
			
			// ==================== 第五步：更新/创建库存记录 ====================
			// 遍历产出成品，更新库存
			for (let i = 0; i < outputProducts.length; i++) {
				const product = outputProducts[i];
				
				// 根据产品名称、等级、规格查找现有库存
				const inventoryRes = await transaction.collection('inventories')
					.where({
						product_name: product.product_name,
						grade: product.grade,
						spec: product.spec
					})
					.get();
				
				const existingInventory = inventoryRes.data && inventoryRes.data[0];
				
				if (existingInventory) {
					// ==================== 更新现有库存 ====================
					const oldStockJin = existingInventory.current_stock_jin || 0;
					const oldUnitCostFen = existingInventory.unit_cost_fen || 0;
					const oldTotalCostFen = existingInventory.total_cost_fen || 0;
					const newStockJin = oldStockJin + product.weight_jin;
					
					// 计算移动加权平均成本
					// 公式：(旧库存总量 × 旧平均单价 + 本次入库重量 × 本次单位成本) / 最新总库存
					const newUnitCostFen = newStockJin > 0 
						? Math.floor((oldStockJin * oldUnitCostFen + product.weight_jin * product.unit_cost_fen) / newStockJin)
						: product.unit_cost_fen;
					
					// 计算库存总成本：直接累加更精准，避免二次取整的误差
					const newTotalCostFen = oldTotalCostFen + product.allocated_cost_fen;
					
					// 生成新批次对象（使用真实的加工单 ID）
					const newBatch = {
						batch_id: generateBatchId(),
						processing_order_id: realProcessingOrderId,
						purchase_batch_no: purchase.batch_no,
						stock_jin: product.weight_jin,
						unit_cost_fen: product.unit_cost_fen,
						inbound_time: currentTime
					};
					
					// 更新库存记录
					await transaction.collection('inventories')
						.doc(existingInventory._id)
						.update({
							current_stock_jin: newStockJin,
							unit_cost_fen: newUnitCostFen,
							total_cost_fen: newTotalCostFen,
							total_inbound_jin: dbCmd.inc(product.weight_jin),
							last_inbound_time: currentTime,
							update_time: currentTime,
							status: newStockJin > (existingInventory.warning_stock_jin || 0) ? 'normal' : 'low_stock',
							batch_list: dbCmd.push(newBatch)
						});
					
					// 记录库存ID
					product.inventory_id = existingInventory._id;
					
				} else {
					// ==================== 创建新库存记录 ====================
					// 生成新批次对象（使用真实的加工单 ID）
					const newBatch = {
						batch_id: generateBatchId(),
						processing_order_id: realProcessingOrderId,
						purchase_batch_no: purchase.batch_no,
						stock_jin: product.weight_jin,
						unit_cost_fen: product.unit_cost_fen,
						inbound_time: currentTime
					};
					
					const newInventory = {
						// FIXME: 第二期 SaaS 化时需从 context/token 中获取 tenant_id
						tenant_id: tenantId,
						product_name: product.product_name,
						fruit_name: product.fruit_name,
						grade: product.grade,
						spec: product.spec,
						origin: product.origin,
						current_stock_jin: product.weight_jin,
						unit_cost_fen: product.unit_cost_fen,
						total_cost_fen: product.allocated_cost_fen,
						suggested_price_fen: 0,
						batch_list: [newBatch],
						total_inbound_jin: product.weight_jin,
						total_outbound_jin: 0,
						warning_stock_jin: 0,
						status: 'normal',
						last_inbound_time: currentTime,
						remark: '',
						create_time: currentTime,
						update_time: currentTime
					};
					
					const addRes = await transaction.collection('inventories').add(newInventory);
					product.inventory_id = addRes.id;
				}
			}
			
			// ==================== 第六步：更新加工单中的 inventory_id ====================
			// 将库存操作完成后获得的 inventory_id 回写到加工单
			await transaction.collection('processing_orders')
				.doc(realProcessingOrderId)
				.update({
					output_products: outputProducts,
					update_time: currentTime
				});
			
			// ==================== 第七步：更新采购单状态 ====================
			await transaction.collection('purchases')
				.doc(purchase_id)
				.update({
					status: 'completed',
					update_time: currentTime
				});
			
			// 返回成功结果
			return {
				order_id: realProcessingOrderId,
				order_no: orderNo,
				purchase_id: purchase_id,
				purchase_batch_no: purchase.batch_no,
				input_weight_jin: inputWeightJin,
				total_output_weight_jin: totalOutputWeightJin,
				loss_weight_jin: lossWeightJin,
				loss_rate: parseFloat(lossRate.toFixed(2)),
				total_cost_fen: totalCostFen,
				output_products: outputProducts
			};
		});
		
		// 事务执行成功
		return {
			code: 0,
			message: '加工入库成功',
			data: result
		};
		
	} catch (error) {
		// 事务执行失败
		console.error('加工入库失败：', error);
		
		// 根据错误类型返回不同的错误信息
		if (error.message.includes('采购单不存在')) {
			return {
				code: 404,
				message: error.message
			};
		}
		
		if (error.message.includes('已完成加工') || error.message.includes('已作废')) {
			return {
				code: 400,
				message: error.message
			};
		}
		
		return {
			code: 500,
			message: `加工入库失败：${error.message}`
		};
	}
};
