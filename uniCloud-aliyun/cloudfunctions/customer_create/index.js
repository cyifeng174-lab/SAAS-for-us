'use strict';

/**
 * 云函数：新增客户（customer_create）
 * 
 * 功能说明：
 * 1. 新增客户信息
 * 2. 自动生成客户编号
 * 3. 数据校验（名称唯一性等）
 * 
 * 数据一致性：使用 uniCloud 数据库事务保证
 * 金额单位：统一使用"分"为单位，避免浮点数精度问题
 */

const db = uniCloud.database();
const dbCmd = db.command;

/**
 * 生成客户编号
 * 格式：KH + 年月日 + 4位序号
 * 
 * 注意：在 SaaS 多租户环境下，建议在 customer_no 字段上加唯一索引
 * 防止并发请求生成重复编号
 */
async function generateCustomerNo(transaction) {
	const now = new Date();
	const dateStr = `${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
	const prefix = `KH${dateStr}`;
	
	// 查询当日最大序号
	const countResult = await transaction.collection('customers')
		.where({
			customer_no: new RegExp(`^${prefix}`)
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
		name,
		phone = '',
		address = '',
		customer_type = 'wholesale',
		credit_limit_fen = 0,
		remark = ''
	} = event;
	
	// ==================== 入参校验 ====================
	
	// 校验客户名称
	if (!name || !name.trim()) {
		return {
			code: 400,
			message: '客户名称不能为空'
		};
	}
	
	// 校验客户类型
	if (!['wholesale', 'retail', 'both'].includes(customer_type)) {
		return {
			code: 400,
			message: '客户类型 customer_type 必须是 wholesale、retail 或 both'
		};
	}
	
	// 校验电话格式（如果填写了）
	if (phone && !/^1[3-9]\d{9}$/.test(phone.trim())) {
		return {
			code: 400,
			message: '请输入正确的手机号码'
		};
	}
	
	// 转换金额为整型（确保精度）
	const creditLimitFen = parseInt(credit_limit_fen) || 0;
	
	if (creditLimitFen < 0) {
		return {
			code: 400,
			message: '信用额度不能为负数'
		};
	}
	
	try {
		// ==================== 使用事务执行所有数据库操作 ====================
		const result = await db.runTransaction(async (transaction) => {
			const currentTime = Date.now();
			
			// FIXME: 第二期 SaaS 化时需从 context/token 中获取 tenant_id
			const tenantId = 'default';
			
			// ==================== 第一步：检查客户名称是否重复 ====================
			const existCustomerRes = await transaction.collection('customers')
				.where({
					tenant_id: tenantId,
					name: name.trim()
				})
				.get();
			
			if (existCustomerRes.data && existCustomerRes.data.length > 0) {
				throw new Error('客户名称已存在，请使用其他名称');
			}
			
			// ==================== 第二步：检查电话是否重复（如果填写了） ====================
			if (phone && phone.trim()) {
				const existPhoneRes = await transaction.collection('customers')
					.where({
						tenant_id: tenantId,
						phone: phone.trim()
					})
					.get();
				
				if (existPhoneRes.data && existPhoneRes.data.length > 0) {
					throw new Error('该电话号码已被其他客户使用');
				}
			}
			
			// ==================== 第三步：生成客户编号 ====================
			const customerNo = await generateCustomerNo(transaction);
			
			// ==================== 第四步：创建客户记录 ====================
			const customerData = {
				tenant_id: tenantId,
				customer_no: customerNo,
				name: name.trim(),
				phone: phone.trim(),
				address: address.trim(),
				customer_type: customer_type,
				total_debt_fen: 0,           // 初始欠款为0
				total_sales_fen: 0,          // 初始销售额为0
				total_paid_fen: 0,           // 初始收款为0
				order_count: 0,              // 初始订单数为0
				credit_limit_fen: creditLimitFen, // 信用额度
				status: 'active',            // 默认状态为正常
				remark: remark.trim(),
				create_time: currentTime,
				update_time: currentTime
			};
			
			// 插入客户记录
			const addRes = await transaction.collection('customers').add(customerData);
			const customerId = addRes.id;
			
			// ==================== 返回成功结果 ====================
			return {
				customer_id: customerId,
				customer_no: customerNo,
				name: name.trim(),
				phone: phone.trim(),
				customer_type: customer_type
			};
		});
		
		// 事务执行成功
		return {
			code: 0,
			message: '客户添加成功',
			data: result
		};
		
	} catch (error) {
		// 事务执行失败
		console.error('新增客户失败：', error);
		
		// 根据错误类型返回不同的错误信息
		if (error.message.includes('已存在') || error.message.includes('已被使用')) {
			return {
				code: 409,
				message: error.message
			};
		}
		
		return {
			code: 500,
			message: `新增客户失败：${error.message}`
		};
	}
};
