'use strict';

/**
 * 云函数：销售开单与先进先出（sales_order_create）
 * 
 * 功能说明：
 * 1. 销售开单，支持批发/零售
 * 2. 先进先出（FIFO）扣减批次库存
 * 3. 计算真实成本（基于批次实际成本）
 * 4. 联动更新客户账本
 * 5. 生成财务流水记录
 * 
 * 数据一致性：全程使用 uniCloud 数据库事务保证
 * 金额单位：统一使用"分"为单位，避免浮点数精度问题
 */

const db = uniCloud.database();
const dbCmd = db.command;

/**
 * 生成销售单号
 * 格式：XS + 年月日 + 4位序号
 * 
 * 注意：在 SaaS 多租户环境下，建议在 order_no 字段上加唯一索引
 * 防止并发请求生成重复单号
 */
async function generateOrderNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = `XS${dateStr}`;
	
	// 查询当日最大序号
	const countResult = await transaction.collection('sales_orders')
		.where({
			order_no: new RegExp(`^${prefix}`)
		})
		.count();
	
	const seq = (countResult.total + 1).toString().padStart(4, '0');
	return `${prefix}${seq}`;
}

/**
 * 生成流水号
 * 格式：SK + 年月日 + 4位序号（收款）
 *       FK + 年月日 + 4位序号（付款）
 */
async function generateLedgerNo(transaction, type) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = type === 'receivable' ? 'SK' : 'FK';
	
	// 查询当日最大序号
	const countResult = await transaction.collection('financial_ledgers')
		.where({
			ledger_no: new RegExp(`^${prefix}${dateStr}`)
		})
		.count();
	
	const seq = (countResult.total + 1).toString().padStart(4, '0');
	return `${prefix}${dateStr}${seq}`;
}

/**
 * FIFO 扣减批次库存
 * 
 * @param {Array} batchList - 批次列表（已按入库时间正序排列）
 * @param {Number} requiredWeightJin - 需要扣减的重量（斤）
 * @returns {Object} { 
 *   costTotalFen: 实际成本总计（分）,
 *   deductedBatches: 扣减的批次明细数组,
 *   newBatchList: 更新后的批次列表
 * }
 */
function fifoDeductBatches(batchList, requiredWeightJin) {
	// 按入库时间正序排列（先进先出）
	const sortedBatches = [...batchList].sort((a, b) => {
		return (a.inbound_time || 0) - (b.inbound_time || 0);
	});
	
	let remainingWeight = requiredWeightJin;
	let costTotalFen = 0;
	const deductedBatches = [];
	const newBatchList = [];
	
	for (const batch of sortedBatches) {
		if (remainingWeight <= 0) {
			// 已扣减完毕，剩余批次保留
			newBatchList.push(batch);
			continue;
		}
		
		const batchStock = batch.stock_jin || 0;
		const batchUnitCost = batch.unit_cost_fen || 0;
		
		if (batchStock <= 0) {
			// 该批次已无库存，跳过
			continue;
		}
		
		if (batchStock >= remainingWeight) {
			// 该批次库存足够，部分扣减
			const deductWeight = remainingWeight;
			const deductCost = Math.floor(deductWeight * batchUnitCost);
			
			costTotalFen += deductCost;
			deductedBatches.push({
				batch_id: batch.batch_id,
				batch_no: batch.purchase_batch_no || '',
				deduct_weight_jin: deductWeight,
				unit_cost_fen: batchUnitCost,
				cost_fen: deductCost
			});
			
			// 更新该批次剩余库存
			const newBatchStock = batchStock - deductWeight;
			if (newBatchStock > 0) {
				newBatchList.push({
					...batch,
					stock_jin: newBatchStock
				});
			}
			
			remainingWeight = 0;
		} else {
			// 该批次库存不足，全部扣减
			const deductWeight = batchStock;
			const deductCost = Math.floor(deductWeight * batchUnitCost);
			
			costTotalFen += deductCost;
			deductedBatches.push({
				batch_id: batch.batch_id,
				batch_no: batch.purchase_batch_no || '',
				deduct_weight_jin: deductWeight,
				unit_cost_fen: batchUnitCost,
				cost_fen: deductCost
			});
			
			remainingWeight -= deductWeight;
			// 该批次库存已扣完，不加入 newBatchList
		}
	}
	
	// 检查是否扣减成功
	if (remainingWeight > 0) {
		throw new Error(`库存不足，还差 ${remainingWeight.toFixed(2)} 斤`);
	}
	
	return {
		costTotalFen,
		deductedBatches,
		newBatchList
	};
}

/**
 * 云函数入口
 */
exports.main = async (event, context) => {
	// 解构入参
	const {
		customer_id,
		sale_type = 'wholesale',
		order_items = [],
		paid_amount_fen = 0,
		operator_id = '',
		operator_name = ''
	} = event;
	
	// ==================== 入参校验 ====================
	
	// 校验客户ID
	if (!customer_id) {
		return {
			code: 400,
			message: '缺少必要参数：customer_id'
		};
	}
	
	// 校验销售类型
	if (!['wholesale', 'retail'].includes(sale_type)) {
		return {
			code: 400,
			message: '销售类型 sale_type 必须是 wholesale 或 retail'
		};
	}
	
	// 校验订单明细
	if (!order_items || order_items.length === 0) {
		return {
			code: 400,
			message: '订单明细 order_items 不能为空'
		};
	}
	
	// 校验每个订单明细项
	for (let i = 0; i < order_items.length; i++) {
		const item = order_items[i];
		
		if (!item.inventory_id) {
			return {
				code: 400,
				message: `第${i + 1}个订单项缺少 inventory_id`
			};
		}
		
		if (!item.weight_jin || item.weight_jin <= 0) {
			return {
				code: 400,
				message: `第${i + 1}个订单项的 weight_jin 必须大于0`
			};
		}
		
		if (item.unit_price_fen === undefined || item.unit_price_fen === null || item.unit_price_fen < 0) {
			return {
				code: 400,
				message: `第${i + 1}个订单项的 unit_price_fen 无效`
			};
		}
	}
	
	// 转换金额为整型（确保精度）
	const paidAmountFen = parseInt(paid_amount_fen) || 0;
	
	if (paidAmountFen < 0) {
		return {
			code: 400,
			message: '实收金额 paid_amount_fen 不能为负数'
		};
	}
	
	try {
		// ==================== 使用事务执行所有数据库操作 ====================
		const result = await db.runTransaction(async (transaction) => {
			const currentTime = Date.now();
			
			// FIXME: 第二期 SaaS 化时需从 context/token 中获取 tenant_id
			const tenantId = 'default';
			
			// ==================== 第一步：查询客户信息 ====================
			const customerRes = await transaction.collection('customers')
				.doc(customer_id)
				.get();
			
			if (!customerRes.data || customerRes.data.length === 0) {
				throw new Error(`客户不存在：${customer_id}`);
			}
			
			const customer = customerRes.data[0];
			
			// 校验客户状态
			if (customer.status === 'inactive') {
				throw new Error('该客户已停用，无法开单');
			}
			
			if (customer.status === 'blacklist') {
				throw new Error('该客户已被列入黑名单，无法开单');
			}
			
			// ==================== 第二步：查询库存并校验 ====================
			// 收集所有需要查询的库存ID
			const inventoryIds = order_items.map(item => item.inventory_id);
			
			// 批量查询库存记录
			const inventoryRes = await transaction.collection('inventories')
				.where({
					_id: dbCmd.in(inventoryIds)
				})
				.get();
			
			// 构建库存映射表
			const inventoryMap = {};
			for (const inv of (inventoryRes.data || [])) {
				inventoryMap[inv._id] = inv;
			}
			
			// 校验库存是否存在、库存是否充足
			for (const item of order_items) {
				const inventory = inventoryMap[item.inventory_id];
				
				if (!inventory) {
					throw new Error(`库存记录不存在：${item.inventory_id}`);
				}
				
				const currentStock = inventory.current_stock_jin || 0;
				const requiredWeight = parseFloat(item.weight_jin) || 0;
				
				if (currentStock < requiredWeight) {
					throw new Error(`商品【${inventory.product_name}】库存不足，当前库存 ${currentStock.toFixed(2)} 斤，需要 ${requiredWeight.toFixed(2)} 斤`);
				}
			}
			
			// ==================== 第三步：FIFO 扣减批次库存 ====================
			// 存储处理后的订单明细
			const processedOrderItems = [];
			// 存储需要更新的库存记录
			const inventoryUpdates = [];
			// 订单总成本
			let totalCostFen = 0;
			// 订单总金额
			let totalAmountFen = 0;
			// 订单总重量
			let totalWeightJin = 0;
			
			for (const item of order_items) {
				const inventory = inventoryMap[item.inventory_id];
				const weightJin = parseFloat(item.weight_jin) || 0;
				const unitPriceFen = parseInt(item.unit_price_fen) || 0;
				
				// 计算销售小计
				const subtotalFen = Math.floor(weightJin * unitPriceFen);
				
				// FIFO 扣减批次
				const batchList = inventory.batch_list || [];
				const fifoResult = fifoDeductBatches(batchList, weightJin);
				
				// 累计成本
				totalCostFen += fifoResult.costTotalFen;
				totalAmountFen += subtotalFen;
				totalWeightJin += weightJin;
				
				// 计算加权平均成本单价（用于显示）
				const avgCostPriceFen = weightJin > 0 
					? Math.floor(fifoResult.costTotalFen / weightJin) 
					: 0;
				
				// 构建处理后的订单明细
				// 注意：一个订单项可能扣减多个批次，这里取第一个批次的信息作为主要批次
				const primaryBatch = fifoResult.deductedBatches[0] || {};
				
				processedOrderItems.push({
					inventory_id: item.inventory_id,
					product_name: inventory.product_name,
					grade: inventory.grade,
					spec: inventory.spec,
					weight_jin: weightJin,
					unit_price_fen: unitPriceFen,
					subtotal_fen: subtotalFen,
					cost_price_fen: avgCostPriceFen,
					cost_total_fen: fifoResult.costTotalFen,
					profit_fen: subtotalFen - fifoResult.costTotalFen,
					batch_id: primaryBatch.batch_id || '',
					batch_no: primaryBatch.batch_no || '',
					// 记录所有扣减的批次明细（用于追溯）
					_deducted_batches: fifoResult.deductedBatches
				});
				
				// 计算新的库存数据
				const newStockJin = (inventory.current_stock_jin || 0) - weightJin;
				const newTotalOutboundJin = (inventory.total_outbound_jin || 0) + weightJin;
				
				// 计算新的库存总成本
				// 新库存总成本 = 旧库存总成本 - 本次出库成本
				const newTotalCostFen = (inventory.total_cost_fen || 0) - fifoResult.costTotalFen;
				
				// 计算新的移动加权平均成本
				const newUnitCostFen = newStockJin > 0 
					? Math.floor(newTotalCostFen / newStockJin) 
					: 0;
				
				// 判断库存状态
				let newStatus = 'normal';
				if (newStockJin <= 0) {
					newStatus = 'out_of_stock';
				} else if (newStockJin <= (inventory.warning_stock_jin || 0)) {
					newStatus = 'low_stock';
				}
				
				// 收集库存更新数据
				inventoryUpdates.push({
					inventory_id: item.inventory_id,
					updateData: {
						current_stock_jin: newStockJin,
						unit_cost_fen: newUnitCostFen,
						total_cost_fen: newTotalCostFen,
						total_outbound_jin: newTotalOutboundJin,
						last_outbound_time: currentTime,
						status: newStatus,
						update_time: currentTime,
						batch_list: fifoResult.newBatchList
					}
				});
			}
			
			// ==================== 第四步：计算欠款和收款状态 ====================
			const debtAmountFen = totalAmountFen - paidAmountFen;
			
			// 判断收款状态
			let paymentStatus = 'unpaid';
			if (paidAmountFen >= totalAmountFen) {
				paymentStatus = 'paid';
			} else if (paidAmountFen > 0) {
				paymentStatus = 'partial';
			}
			
			// ==================== 第五步：生成销售单号并创建销售订单 ====================
			const orderNo = await generateOrderNo(transaction);
			
			// 构建销售订单数据
			const salesOrder = {
				tenant_id: tenantId,
				order_no: orderNo,
				customer_id: customer_id,
				customer_name: customer.name,
				customer_phone: customer.phone || '',
				sale_type: sale_type,
				order_items: processedOrderItems.map(item => {
					// 移除内部字段 _deducted_batches，不存入数据库
					const { _deducted_batches, ...rest } = item;
					return rest;
				}),
				total_weight_jin: totalWeightJin,
				total_amount_fen: totalAmountFen,
				total_cost_fen: totalCostFen,
				total_profit_fen: totalAmountFen - totalCostFen,
				payment_status: paymentStatus,
				paid_amount_fen: paidAmountFen,
				debt_amount_fen: debtAmountFen,
				sale_date: currentTime,
				status: 'completed',
				remark: '',
				create_time: currentTime,
				update_time: currentTime,
				operator_id: operator_id,
				operator_name: operator_name
			};
			
			// 插入销售订单
			const addOrderRes = await transaction.collection('sales_orders').add(salesOrder);
			const orderId = addOrderRes.id;
			
			// ==================== 第六步：更新库存记录 ====================
			for (const update of inventoryUpdates) {
				await transaction.collection('inventories')
					.doc(update.inventory_id)
					.update(update.updateData);
			}
			
			// ==================== 第七步：更新客户表 ====================
			// 计算新的客户数据
			const newTotalDebtFen = (customer.total_debt_fen || 0) + debtAmountFen;
			const newTotalSalesFen = (customer.total_sales_fen || 0) + totalAmountFen;
			const newTotalPaidFen = (customer.total_paid_fen || 0) + paidAmountFen;
			const newOrderCount = (customer.order_count || 0) + 1;
			
			await transaction.collection('customers')
				.doc(customer_id)
				.update({
					total_debt_fen: newTotalDebtFen,
					total_sales_fen: newTotalSalesFen,
					total_paid_fen: newTotalPaidFen,
					order_count: newOrderCount,
					last_order_time: currentTime,
					// 如果是首单，记录首单时间
					...(customer.first_order_time ? {} : { first_order_time: currentTime }),
					update_time: currentTime
				});
			
			// ==================== 第八步：生成财务流水（修正版） ====================
			// 核心原则：先产生全额应收，再核销收款
			// 必须将一笔交易拆分为两个先后发生的独立事件：
			// 事件A：产生全额应收（客户买了货，账面欠款增加）
			// 事件B：发生付款核销（客户付款，账面欠款减少）
			let currentBalanceFen = customer.total_debt_fen || 0;
			
			// 1. 永远先生成一笔"全额应收"的销售欠款流水（只要总金额 > 0）
			if (totalAmountFen > 0) {
				const ledgerNoDebt = await generateLedgerNo(transaction, 'receivable');
				const balanceAfterDebtFen = currentBalanceFen + totalAmountFen;
				
				const debtLedger = {
					tenant_id: tenantId,
					ledger_no: ledgerNoDebt,
					ledger_type: 'receivable',
					transaction_type: 'sale_debt',
					amount_fen: totalAmountFen, // 注意：这里是全额，不是欠款
					balance_before_fen: currentBalanceFen,
					balance_after_fen: balanceAfterDebtFen,
					related_party_type: 'customer',
					related_party_id: customer_id,
					related_party_name: customer.name,
					related_order_type: 'sales_order',
					related_order_id: orderId,
					related_order_no: orderNo,
					// 欠款流水不需要 write_off_details
					transaction_time: currentTime,
					remark: `销售单 ${orderNo} 产生应收`,
					create_time: currentTime,
					operator_id: operator_id,
					operator_name: operator_name
				};
				
				await transaction.collection('financial_ledgers').add(debtLedger);
				currentBalanceFen = balanceAfterDebtFen; // 更新当前余额指针
			}
			
			// 2. 如果有实收金额，再生成一笔"收款核销"流水，去抵扣上面的应收
			if (paidAmountFen > 0) {
				const ledgerNoPayment = await generateLedgerNo(transaction, 'receivable');
				const balanceAfterPaymentFen = currentBalanceFen - paidAmountFen; // 收款减少欠款
				
				const paymentLedger = {
					tenant_id: tenantId,
					ledger_no: ledgerNoPayment,
					ledger_type: 'receivable',
					transaction_type: 'payment_received',
					amount_fen: paidAmountFen,
					balance_before_fen: currentBalanceFen,
					balance_after_fen: balanceAfterPaymentFen,
					related_party_type: 'customer',
					related_party_id: customer_id,
					related_party_name: customer.name,
					related_order_type: 'sales_order',
					related_order_id: orderId,
					related_order_no: orderNo,
					write_off_details: [{
						order_id: orderId,
						order_no: orderNo,
						amount_fen: paidAmountFen,
						order_debt_before_fen: totalAmountFen,
						order_debt_after_fen: debtAmountFen // 剩余欠款
					}],
					transaction_time: currentTime + 1, // 稍微错开1毫秒保证排序
					remark: `销售单 ${orderNo} 收款`,
					create_time: currentTime,
					operator_id: operator_id,
					operator_name: operator_name
				};
				
				await transaction.collection('financial_ledgers').add(paymentLedger);
			}
			
			// ==================== 返回成功结果 ====================
			return {
				order_id: orderId,
				order_no: orderNo,
				customer_id: customer_id,
				customer_name: customer.name,
				sale_type: sale_type,
				total_weight_jin: totalWeightJin,
				total_amount_fen: totalAmountFen,
				total_cost_fen: totalCostFen,
				total_profit_fen: totalAmountFen - totalCostFen,
				paid_amount_fen: paidAmountFen,
				debt_amount_fen: debtAmountFen,
				payment_status: paymentStatus,
				order_items: processedOrderItems.map(item => {
					// 移除内部字段
					const { _deducted_batches, ...rest } = item;
					return rest;
				})
			};
		});
		
		// 事务执行成功
		return {
			code: 0,
			message: '销售开单成功',
			data: result
		};
		
	} catch (error) {
		// 事务执行失败
		console.error('销售开单失败：', error);
		
		// 根据错误类型返回不同的错误信息
		if (error.message.includes('客户不存在')) {
			return {
				code: 404,
				message: error.message
			};
		}
		
		if (error.message.includes('已停用') || error.message.includes('黑名单')) {
			return {
				code: 403,
				message: error.message
			};
		}
		
		if (error.message.includes('库存不足') || error.message.includes('库存记录不存在')) {
			return {
				code: 400,
				message: error.message
			};
		}
		
		return {
			code: 500,
			message: `销售开单失败：${error.message}`
		};
	}
};
