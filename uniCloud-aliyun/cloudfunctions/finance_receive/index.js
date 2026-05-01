'use strict';

/**
 * 云函数：收款核销（finance_receive）
 * 
 * 功能说明：
 * 1. 记录客户收款
 * 2. 自动核销欠款（先进先出）
 * 3. 更新客户欠款余额
 * 4. 更新销售订单付款状态
 * 5. 生成财务流水记录
 * 
 * 数据一致性：全程使用 uniCloud 数据库事务保证
 * 金额单位：统一使用"分"为单位，避免浮点数精度问题
 */

const db = uniCloud.database();
const dbCmd = db.command;

/**
 * 生成收款流水号
 * 格式：SK + 年月日 + 4位序号
 * 
 * 注意：在 SaaS 多租户环境下，建议在 ledger_no 字段上加唯一索引
 * 防止并发请求生成重复流水号
 */
async function generateLedgerNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = `SK${dateStr}`;
	
	// 查询当日最大序号
	const countResult = await transaction.collection('financial_ledgers')
		.where({
			ledger_no: new RegExp(`^${prefix}`)
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
		customer_id,
		amount_fen,
		payment_method = 'cash',
		receive_time,
		remark = '',
		write_off_details = [],
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
	
	// 校验收款金额
	const receiveAmountFen = parseInt(amount_fen) || 0;
	
	if (receiveAmountFen <= 0) {
		return {
			code: 400,
			message: '收款金额必须大于0'
		};
	}
	
	// 校验收款方式
	const validPaymentMethods = ['wechat', 'alipay', 'cash', 'bank'];
	if (!validPaymentMethods.includes(payment_method)) {
		return {
			code: 400,
			message: '收款方式无效，必须是 wechat/alipay/cash/bank 之一'
		};
	}
	
	// 校验收款时间
	const receiveTime = receive_time ? parseInt(receive_time) : Date.now();
	
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
				throw new Error('该客户已停用，无法收款');
			}
			
			if (customer.status === 'blacklist') {
				throw new Error('该客户已被列入黑名单，无法收款');
			}
			
			// ==================== 第二步：处理核销明细 ====================
			// 计算本次核销总金额
			let totalWriteOffFen = 0;
			const processedWriteOffDetails = [];
			
			// 如果有传入核销明细，则按明细核销
			if (write_off_details && write_off_details.length > 0) {
				for (const detail of write_off_details) {
					const writeOffAmountFen = parseInt(detail.write_off_amount_fen) || 0;
					
					if (writeOffAmountFen <= 0) {
						continue;
					}
					
					// 查询订单信息，校验欠款金额
					const orderRes = await transaction.collection('sales_orders')
						.doc(detail.order_id)
						.get();
					
					if (!orderRes.data || orderRes.data.length === 0) {
						throw new Error(`订单不存在：${detail.order_id}`);
					}
					
					const order = orderRes.data[0];
					
					// 校验订单是否属于该客户
					if (order.customer_id !== customer_id) {
						throw new Error(`订单 ${detail.order_no} 不属于该客户`);
					}
					
					// 校验订单付款状态
					if (order.payment_status === 'paid') {
						throw new Error(`订单 ${detail.order_no} 已结清，无需核销`);
					}
					
					// 获取订单当前欠款金额
					const orderDebtFen = order.debt_amount_fen || 0;
					
					// 校验核销金额不能超过欠款金额
					if (writeOffAmountFen > orderDebtFen) {
						throw new Error(`订单 ${detail.order_no} 核销金额不能超过欠款金额 ¥${(orderDebtFen / 100).toFixed(2)}`);
					}
					
					// 计算核销后的订单欠款
					const newOrderDebtFen = orderDebtFen - writeOffAmountFen;
					
					// 计算核销后的订单已付金额
					const newOrderPaidFen = (order.paid_amount_fen || 0) + writeOffAmountFen;
					
					// 判断订单付款状态
					let newPaymentStatus = order.payment_status;
					if (newOrderDebtFen <= 0) {
						newPaymentStatus = 'paid';
					} else if (newOrderPaidFen > 0) {
						newPaymentStatus = 'partial';
					}
					
					// 记录处理后的核销明细
					processedWriteOffDetails.push({
						order_id: detail.order_id,
						order_no: detail.order_no,
						order_time: detail.order_time || order.create_time,
						order_amount_fen: order.total_amount_fen,
						order_debt_fen: orderDebtFen,
						write_off_amount_fen: writeOffAmountFen,
						order_debt_after_fen: newOrderDebtFen,
						new_payment_status: newPaymentStatus,
						new_paid_amount_fen: newOrderPaidFen
					});
					
					totalWriteOffFen += writeOffAmountFen;
				}
			}
			
			// 校验核销金额不能超过收款金额
			if (totalWriteOffFen > receiveAmountFen) {
				throw new Error(`核销金额 ¥${(totalWriteOffFen / 100).toFixed(2)} 不能超过收款金额 ¥${(receiveAmountFen / 100).toFixed(2)}`);
			}
			
			// ==================== 第三步：生成收款流水号 ====================
			const ledgerNo = await generateLedgerNo(transaction);
			
			// ==================== 第四步：计算余额变化 ====================
			const balanceBeforeFen = customer.total_debt_fen || 0;
			
			// 收款后欠款余额 = 原欠款 - 核销金额
			// 注意：如果收款金额大于核销金额，差额作为预收款（暂不处理，仅记录）
			const balanceAfterFen = balanceBeforeFen - totalWriteOffFen;
			
			// 计算剩余金额（预收）
			const remainingAmountFen = receiveAmountFen - totalWriteOffFen;
			
			// ==================== 第五步：插入财务流水记录 ====================
			const ledger = {
				tenant_id: tenantId,
				ledger_no: ledgerNo,
				ledger_type: 'receivable',
				transaction_type: 'payment_received',
				amount_fen: receiveAmountFen,
				balance_before_fen: balanceBeforeFen,
				balance_after_fen: balanceAfterFen,
				related_party_type: 'customer',
				related_party_id: customer_id,
				related_party_name: customer.name,
				related_order_type: 'none', // 收款可能对应多笔订单
				related_order_id: '',
				related_order_no: '',
				write_off_details: processedWriteOffDetails.map(detail => ({
					order_id: detail.order_id,
					order_no: detail.order_no,
					order_time: detail.order_time,
					order_amount_fen: detail.order_amount_fen,
					order_debt_fen: detail.order_debt_fen,
					write_off_amount_fen: detail.write_off_amount_fen
				})),
				payment_method: payment_method,
				transaction_time: receiveTime,
				remark: remark || `客户 ${customer.name} 收款`,
				create_time: currentTime,
				operator_id: operator_id,
				operator_name: operator_name
			};
			
			// 如果有剩余金额（预收），在备注中说明
			if (remainingAmountFen > 0) {
				ledger.remark += `，预收 ¥${(remainingAmountFen / 100).toFixed(2)}`;
			}
			
			await transaction.collection('financial_ledgers').add(ledger);
			
			// ==================== 第六步：更新销售订单付款状态 ====================
			for (const detail of processedWriteOffDetails) {
				await transaction.collection('sales_orders')
					.doc(detail.order_id)
					.update({
						payment_status: detail.new_payment_status,
						paid_amount_fen: detail.new_paid_amount_fen,
						debt_amount_fen: detail.order_debt_after_fen,
						update_time: currentTime
					});
			}
			
			// ==================== 第七步：更新客户欠款余额 ====================
			// 计算新的客户数据
			const newTotalDebtFen = balanceAfterFen;
			const newTotalPaidFen = (customer.total_paid_fen || 0) + receiveAmountFen;
			
			await transaction.collection('customers')
				.doc(customer_id)
				.update({
					total_debt_fen: newTotalDebtFen,
					total_paid_fen: newTotalPaidFen,
					update_time: currentTime
				});
			
			// ==================== 返回成功结果 ====================
			return {
				ledger_no: ledgerNo,
				customer_id: customer_id,
				customer_name: customer.name,
				amount_fen: receiveAmountFen,
				write_off_amount_fen: totalWriteOffFen,
				remaining_amount_fen: remainingAmountFen,
				remaining_debt_fen: newTotalDebtFen,
				balance_before_fen: balanceBeforeFen,
				balance_after_fen: balanceAfterFen,
				write_off_details: processedWriteOffDetails.map(detail => ({
					order_id: detail.order_id,
					order_no: detail.order_no,
					order_time: detail.order_time,
					order_amount_fen: detail.order_amount_fen,
					order_debt_fen: detail.order_debt_fen,
					write_off_amount_fen: detail.write_off_amount_fen
				}))
			};
		});
		
		// 事务执行成功
		return {
			code: 0,
			message: '收款成功',
			data: result
		};
		
	} catch (error) {
		// 事务执行失败
		console.error('收款失败：', error);
		
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
		
		if (error.message.includes('订单不存在') || error.message.includes('不属于该客户') || error.message.includes('已结清')) {
			return {
				code: 400,
				message: error.message
			};
		}
		
		if (error.message.includes('核销金额') || error.message.includes('核销金额不能超过')) {
			return {
				code: 400,
				message: error.message
			};
		}
		
		return {
			code: 500,
			message: `收款失败：${error.message}`
		};
	}
};
