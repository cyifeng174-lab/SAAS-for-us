'use strict';

/**
 * 云函数：采购单创建（purchase_create）
 * 
 * 功能说明：
 * 1. 创建采购单，记录向果农收货的信息
 * 2. 生成采购批次号（格式：CG + 年月日 + 4位序号）
 * 3. 更新供应商统计信息
 * 4. 生成财务流水（如有付款）
 * 
 * 数据一致性：全程使用 uniCloud 数据库事务保证
 * 金额单位：统一使用"分"为单位，避免浮点数精度问题
 */

const db = uniCloud.database();
const dbCmd = db.command;

/**
 * 生成采购批次号
 * 格式：CG + 年月日 + 4位序号
 * 
 * 注意：在 SaaS 多租户环境下，建议在 batch_no 字段上加唯一索引
 * 防止并发请求生成重复单号
 */
async function generateBatchNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = `CG${dateStr}`;
	
	// 查询当日最大序号
	const countResult = await transaction.collection('purchases')
		.where({
			batch_no: new RegExp(`^${prefix}`)
		})
		.count();
	
	const seq = (countResult.total + 1).toString().padStart(4, '0');
	return `${prefix}${seq}`;
}

/**
 * 生成流水号
 * 格式：FK + 年月日 + 4位序号（付款）
 */
async function generateLedgerNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = 'FK';
	
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
 * 云函数入口
 */
exports.main = async (event, context) => {
	// 解构入参
	const {
		supplier_id,
		supplier_name,
		fruit_name,
		origin = '',
		weight_jin,
		unit_price_fen,
		total_amount_fen,
		payment_status = 'unpaid',
		paid_amount_fen = 0,
		debt_amount_fen = 0,
		operator_id = '',
		operator_name = '',
		remark = ''
	} = event;
	
	// ==================== 入参校验 ====================
	
	// 校验供应商ID
	if (!supplier_id) {
		return {
			code: 400,
			message: '缺少必要参数：supplier_id'
		};
	}
	
	// 校验供应商名称
	if (!supplier_name) {
		return {
			code: 400,
			message: '缺少必要参数：supplier_name'
		};
	}
	
	// 校验原果名称
	if (!fruit_name) {
		return {
			code: 400,
			message: '缺少必要参数：fruit_name'
		};
	}
	
	// 校验重量
	if (!weight_jin || weight_jin <= 0) {
		return {
			code: 400,
			message: '重量 weight_jin 必须大于0'
		};
	}
	
	// 校验单价
	if (unit_price_fen === undefined || unit_price_fen === null || unit_price_fen < 0) {
		return {
			code: 400,
			message: '单价 unit_price_fen 无效'
		};
	}
	
	// 校验总金额
	if (total_amount_fen === undefined || total_amount_fen === null || total_amount_fen < 0) {
		return {
			code: 400,
			message: '总金额 total_amount_fen 无效'
		};
	}
	
	// 校验付款状态
	if (!['unpaid', 'partial', 'paid'].includes(payment_status)) {
		return {
			code: 400,
			message: '付款状态 payment_status 必须是 unpaid、partial 或 paid'
		};
	}
	
	// 转换金额为整型（确保精度）
	const totalAmountFen = parseInt(total_amount_fen) || 0;
	const paidAmountFen = parseInt(paid_amount_fen) || 0;
	const debtAmountFen = parseInt(debt_amount_fen) || 0;
	const unitPriceFen = parseInt(unit_price_fen) || 0;
	const weightJin = parseFloat(weight_jin) || 0;
	
	// 校验已付金额不能超过总金额
	if (paidAmountFen > totalAmountFen) {
		return {
			code: 400,
			message: '已付金额不能超过总金额'
		};
	}
	
	try {
		// ==================== 使用事务执行所有数据库操作 ====================
		const result = await db.runTransaction(async (transaction) => {
			const currentTime = Date.now();
			
			// FIXME: 第二期 SaaS 化时需从 context/token 中获取 tenant_id
			const tenantId = 'default';
			
			// ==================== 第一步：查询供应商信息 ====================
			const supplierRes = await transaction.collection('suppliers')
				.doc(supplier_id)
				.get();
			
			if (!supplierRes.data || supplierRes.data.length === 0) {
				throw new Error(`供应商不存在：${supplier_id}`);
			}
			
			const supplier = supplierRes.data[0];
			
			// 校验供应商状态
			if (supplier.status === 'inactive') {
				throw new Error('该供应商已停用，无法采购');
			}
			
			if (supplier.status === 'blacklist') {
				throw new Error('该供应商已被列入黑名单，无法采购');
			}
			
			// ==================== 第二步：生成采购批次号并创建采购单 ====================
			const batchNo = await generateBatchNo(transaction);
			
			// 构建采购单数据
			const purchase = {
				tenant_id: tenantId,
				batch_no: batchNo,
				supplier_id: supplier_id,
				supplier_name: supplier_name,
				fruit_name: fruit_name,
				origin: origin,
				weight_jin: weightJin,
				unit_price_fen: unitPriceFen,
				total_amount_fen: totalAmountFen,
				payment_status: payment_status,
				paid_amount_fen: paidAmountFen,
				debt_amount_fen: debtAmountFen,
				purchase_date: currentTime,
				status: 'pending', // 待加工
				remark: remark,
				create_time: currentTime,
				update_time: currentTime,
				operator_id: operator_id,
				operator_name: operator_name
			};
			
			// 插入采购单
			const addPurchaseRes = await transaction.collection('purchases').add(purchase);
			const purchaseId = addPurchaseRes.id;
			
			// ==================== 第三步：更新供应商表 ====================
			// 计算新的供应商数据
			const newTotalDebtFen = (supplier.total_debt_fen || 0) + debtAmountFen;
			const newTotalPurchaseFen = (supplier.total_purchase_fen || 0) + totalAmountFen;
			const newTotalPaidFen = (supplier.total_paid_fen || 0) + paidAmountFen;
			const newPurchaseCount = (supplier.purchase_count || 0) + 1;
			
			await transaction.collection('suppliers')
				.doc(supplier_id)
				.update({
					total_debt_fen: newTotalDebtFen,
					total_purchase_fen: newTotalPurchaseFen,
					total_paid_fen: newTotalPaidFen,
					purchase_count: newPurchaseCount,
					last_purchase_time: currentTime,
					// 如果是首次采购，记录首次采购时间
					...(supplier.first_purchase_time ? {} : { first_purchase_time: currentTime }),
					update_time: currentTime
				});
			
			// ==================== 第四步：生成财务流水（如有付款） ====================
			// 核心原则：先产生全额应付，再核销付款
			// 必须将一笔交易拆分为两个先后发生的独立事件：
			// 事件A：产生全额应付（采购了货，账面欠款增加）
			// 事件B：发生付款核销（向供应商付款，账面欠款减少）
			let currentBalanceFen = supplier.total_debt_fen || 0;
			
			// 1. 永远先生成一笔"全额应付"的采购欠款流水（只要总金额 > 0）
			if (totalAmountFen > 0) {
				const ledgerNoDebt = await generateLedgerNo(transaction);
				const balanceAfterDebtFen = currentBalanceFen + totalAmountFen;
				
				const debtLedger = {
					tenant_id: tenantId,
					ledger_no: ledgerNoDebt,
					ledger_type: 'payable', // 应付账款
					transaction_type: 'purchase_debt',
					amount_fen: totalAmountFen, // 注意：这里是全额
					balance_before_fen: currentBalanceFen,
					balance_after_fen: balanceAfterDebtFen,
					related_party_type: 'supplier',
					related_party_id: supplier_id,
					related_party_name: supplier_name,
					related_order_type: 'purchase_order',
					related_order_id: purchaseId,
					related_order_no: batchNo,
					// 欠款流水不需要 write_off_details
					transaction_time: currentTime,
					remark: `采购单 ${batchNo} 产生应付`,
					create_time: currentTime,
					operator_id: operator_id,
					operator_name: operator_name
				};
				
				await transaction.collection('financial_ledgers').add(debtLedger);
				currentBalanceFen = balanceAfterDebtFen; // 更新当前余额指针
			}
			
			// 2. 如果有已付金额，再生成一笔"付款核销"流水，去抵扣上面的应付
			if (paidAmountFen > 0) {
				const ledgerNoPayment = await generateLedgerNo(transaction);
				const balanceAfterPaymentFen = currentBalanceFen - paidAmountFen; // 付款减少欠款
				
				const paymentLedger = {
					tenant_id: tenantId,
					ledger_no: ledgerNoPayment,
					ledger_type: 'payable', // 应付账款
					transaction_type: 'payment_made',
					amount_fen: paidAmountFen,
					balance_before_fen: currentBalanceFen,
					balance_after_fen: balanceAfterPaymentFen,
					related_party_type: 'supplier',
					related_party_id: supplier_id,
					related_party_name: supplier_name,
					related_order_type: 'purchase_order',
					related_order_id: purchaseId,
					related_order_no: batchNo,
					write_off_details: [{
						order_id: purchaseId,
						order_no: batchNo,
						amount_fen: paidAmountFen,
						order_debt_before_fen: totalAmountFen,
						order_debt_after_fen: debtAmountFen // 剩余欠款
					}],
					transaction_time: currentTime + 1, // 稍微错开1毫秒保证排序
					remark: `采购单 ${batchNo} 付款`,
					create_time: currentTime,
					operator_id: operator_id,
					operator_name: operator_name
				};
				
				await transaction.collection('financial_ledgers').add(paymentLedger);
			}
			
			// ==================== 返回成功结果 ====================
			return {
				_id: purchaseId,
				batch_no: batchNo,
				supplier_id: supplier_id,
				supplier_name: supplier_name,
				fruit_name: fruit_name,
				origin: origin,
				weight_jin: weightJin,
				unit_price_fen: unitPriceFen,
				total_amount_fen: totalAmountFen,
				payment_status: payment_status,
				paid_amount_fen: paidAmountFen,
				debt_amount_fen: debtAmountFen,
				purchase_date: currentTime,
				status: 'pending'
			};
		});
		
		// 事务执行成功
		return {
			code: 0,
			message: '采购录入成功',
			data: result
		};
		
	} catch (error) {
		// 事务执行失败
		console.error('采购录入失败：', error);
		
		// 根据错误类型返回不同的错误信息
		if (error.message.includes('供应商不存在')) {
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
		
		return {
			code: 500,
			message: `采购录入失败：${error.message}`
		};
	}
};
