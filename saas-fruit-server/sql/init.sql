-- ============================================
-- 水果进销存SaaS系统 - 数据库初始化脚本
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- 金额单位：全部以"分"为单位（INT类型）
-- ============================================

CREATE DATABASE IF NOT EXISTS saas_fruit
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE saas_fruit;

-- ==================== 1. 用户表 ====================
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信openid',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（SaaS隔离）',
    `role` VARCHAR(20) NOT NULL DEFAULT 'admin' COMMENT '角色：admin-管理员,staff-员工',
    `nickname` VARCHAR(50) DEFAULT '微信用户' COMMENT '昵称',
    `avatar_url` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '手机号',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive/banned',
    `last_login_time` BIGINT DEFAULT 0 COMMENT '最后登录时间戳(ms)',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳(ms)',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳(ms)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==================== 2. 客户表 ====================
CREATE TABLE IF NOT EXISTS `customers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `customer_no` VARCHAR(32) NOT NULL COMMENT '客户编号（KH202605030001）',
    `name` VARCHAR(50) NOT NULL COMMENT '客户名称',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    `address` VARCHAR(200) DEFAULT '' COMMENT '地址',
    `customer_type` VARCHAR(20) NOT NULL DEFAULT 'wholesale' COMMENT '客户类型：wholesale/retail/both',
    `credit_limit_fen` INT NOT NULL DEFAULT 0 COMMENT '信用额度（分）0=不限',
    `total_debt_fen` INT NOT NULL DEFAULT 0 COMMENT '当前总欠款（分）',
    `total_sales_fen` INT NOT NULL DEFAULT 0 COMMENT '累计销售额（分）',
    `total_paid_fen` INT NOT NULL DEFAULT 0 COMMENT '累计已收款（分）',
    `order_count` INT NOT NULL DEFAULT 0 COMMENT '订单数量',
    `first_order_time` BIGINT DEFAULT 0 COMMENT '首次下单时间戳',
    `last_order_time` BIGINT DEFAULT 0 COMMENT '最近下单时间戳',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive/blacklist/deleted',
    `delete_time` BIGINT DEFAULT NULL COMMENT '软删除时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_no` (`customer_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_phone` (`phone`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- ==================== 3. 供应商表 ====================
CREATE TABLE IF NOT EXISTS `suppliers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `supplier_no` VARCHAR(32) NOT NULL COMMENT '供应商编号（GYS20260001）',
    `name` VARCHAR(50) NOT NULL COMMENT '供应商名称',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    `contact_person` VARCHAR(50) DEFAULT '' COMMENT '联系人',
    `address` VARCHAR(200) DEFAULT '' COMMENT '地址',
    `origin` VARCHAR(50) DEFAULT '' COMMENT '主要产地',
    `supplier_type` VARCHAR(20) NOT NULL DEFAULT 'farmer' COMMENT '供应商类型：farmer/wholesaler/cooperative',
    `total_debt_fen` INT NOT NULL DEFAULT 0 COMMENT '当前总欠款（分，正=我欠供应商）',
    `total_purchase_fen` INT NOT NULL DEFAULT 0 COMMENT '累计采购额（分）',
    `total_paid_fen` INT NOT NULL DEFAULT 0 COMMENT '累计已付款（分）',
    `purchase_count` INT NOT NULL DEFAULT 0 COMMENT '采购次数',
    `first_purchase_time` BIGINT DEFAULT 0 COMMENT '首次采购时间戳',
    `last_purchase_time` BIGINT DEFAULT 0 COMMENT '最近采购时间戳',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive/blacklist/deleted',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_no` (`supplier_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ==================== 4. 采购单表 ====================
CREATE TABLE IF NOT EXISTS `purchases` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `batch_no` VARCHAR(32) NOT NULL COMMENT '采购批次号（CG202605030001）',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(50) NOT NULL COMMENT '供应商名称（冗余）',
    `fruit_name` VARCHAR(50) NOT NULL COMMENT '原果名称',
    `origin` VARCHAR(50) DEFAULT '' COMMENT '产地',
    `weight_jin` DECIMAL(10,2) NOT NULL COMMENT '重量（斤）',
    `unit_price_fen` INT NOT NULL COMMENT '单价（分/斤）',
    `total_amount_fen` INT NOT NULL COMMENT '总金额（分）',
    `payment_status` VARCHAR(20) NOT NULL DEFAULT 'unpaid' COMMENT '付款状态：unpaid/partial/paid',
    `paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '已付金额（分）',
    `debt_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '欠款金额（分）',
    `purchase_date` BIGINT NOT NULL COMMENT '采购日期时间戳',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/processing/completed/cancelled',
    `cancel_time` BIGINT DEFAULT NULL COMMENT '作废时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_purchase_date` (`purchase_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单表';

-- ==================== 5. 销售订单表 ====================
CREATE TABLE IF NOT EXISTS `sales_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（XS202605030001）',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(50) NOT NULL COMMENT '客户名称（冗余）',
    `customer_phone` VARCHAR(20) DEFAULT '' COMMENT '客户电话（冗余）',
    `sale_type` VARCHAR(20) NOT NULL COMMENT '销售类型：wholesale/retail',
    `total_weight_jin` DECIMAL(10,2) NOT NULL COMMENT '总重量（斤）',
    `total_amount_fen` INT NOT NULL COMMENT '总金额（分）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '总成本（分，FIFO计算）',
    `total_profit_fen` INT NOT NULL DEFAULT 0 COMMENT '毛利（分）',
    `paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    `debt_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '欠款金额（分）',
    `payment_status` VARCHAR(20) NOT NULL DEFAULT 'unpaid' COMMENT '收款状态：unpaid/partial/paid',
    `status` VARCHAR(20) NOT NULL DEFAULT 'completed' COMMENT '状态：completed/cancelled',
    `sale_date` BIGINT NOT NULL COMMENT '下单时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_sale_date` (`sale_date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单表';

-- ==================== 6. 销售订单明细表 ====================
CREATE TABLE IF NOT EXISTS `sales_order_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID（关联sales_orders.id）',
    `inventory_id` BIGINT NOT NULL COMMENT '库存ID（关联inventories.id）',
    `product_name` VARCHAR(50) NOT NULL COMMENT '商品名称（冗余）',
    `grade` VARCHAR(50) DEFAULT '' COMMENT '等级',
    `spec` VARCHAR(50) DEFAULT '' COMMENT '规格',
    `weight_jin` DECIMAL(10,2) NOT NULL COMMENT '重量（斤）',
    `unit_price_fen` INT NOT NULL COMMENT '单价（分/斤）',
    `subtotal_fen` INT NOT NULL COMMENT '小计金额（分）',
    `cost_price_fen` INT NOT NULL DEFAULT 0 COMMENT '成本单价（分/斤，FIFO）',
    `cost_total_fen` INT NOT NULL DEFAULT 0 COMMENT '总成本（分）',
    `profit_fen` INT NOT NULL DEFAULT 0 COMMENT '利润（分）',
    `batch_id` VARCHAR(64) DEFAULT '' COMMENT '出库批次ID',
    `batch_no` VARCHAR(32) DEFAULT '' COMMENT '出库批次号',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_inventory_id` (`inventory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

-- ==================== 7. 库存表 ====================
CREATE TABLE IF NOT EXISTS `inventories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `product_name` VARCHAR(50) NOT NULL COMMENT '成品名称',
    `fruit_name` VARCHAR(50) DEFAULT '' COMMENT '水果名称',
    `grade` VARCHAR(50) DEFAULT '' COMMENT '等级',
    `spec` VARCHAR(50) DEFAULT '' COMMENT '规格',
    `origin` VARCHAR(50) DEFAULT '' COMMENT '产地',
    `current_stock_jin` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '当前库存（斤）',
    `unit_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '加权平均成本（分/斤）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '库存总成本（分）',
    `suggested_price_fen` INT DEFAULT 0 COMMENT '建议售价（分/斤）',
    `batch_list` JSON DEFAULT NULL COMMENT '来源批次信息（JSON，记录FIFO批次）',
    `total_inbound_jin` DECIMAL(10,2) DEFAULT 0 COMMENT '累计入库（斤）',
    `total_outbound_jin` DECIMAL(10,2) DEFAULT 0 COMMENT '累计出库（斤）',
    `warning_stock_jin` DECIMAL(10,2) DEFAULT 10.00 COMMENT '预警库存量（斤）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '状态：normal/low_stock/out_of_stock',
    `last_inbound_time` BIGINT DEFAULT 0 COMMENT '最近入库时间戳',
    `last_outbound_time` BIGINT DEFAULT 0 COMMENT '最近出库时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_name` (`product_name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- ==================== 8. 加工订单表 ====================
CREATE TABLE IF NOT EXISTS `processing_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '加工单号（JG202605030001）',
    `purchase_id` BIGINT DEFAULT NULL COMMENT '来源采购单ID',
    `purchase_batch_no` VARCHAR(32) DEFAULT '' COMMENT '来源采购批次号',
    `fruit_name` VARCHAR(50) NOT NULL COMMENT '原果名称',
    `input_weight_jin` DECIMAL(10,2) NOT NULL COMMENT '原果投入重量（斤）',
    `labor_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '人工费用（分）',
    `packaging_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '包装费用（分）',
    `transport_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '运输费用（分）',
    `other_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '其他费用（分）',
    `total_processing_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '加工总成本（分）',
    `purchase_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '采购成本（分）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '总成本（分）',
    `output_products` JSON DEFAULT NULL COMMENT '产出商品列表（JSON）',
    `total_output_weight_jin` DECIMAL(10,2) DEFAULT 0 COMMENT '总产出重量（斤）',
    `loss_weight_jin` DECIMAL(10,2) DEFAULT 0 COMMENT '损耗重量（斤）',
    `loss_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '损耗率（%）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'completed' COMMENT '状态：completed/cancelled',
    `processing_date` BIGINT NOT NULL COMMENT '加工时间戳',
    `cancel_time` BIGINT DEFAULT NULL COMMENT '作废时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_purchase_id` (`purchase_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工订单表';

-- ==================== 9. 财务流水表 ====================
CREATE TABLE IF NOT EXISTS `financial_ledgers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `ledger_no` VARCHAR(32) NOT NULL COMMENT '流水号（FK/SK+日期+序号）',
    `ledger_type` VARCHAR(20) NOT NULL COMMENT '流水类型：receivable-应收/payable-应付',
    `transaction_type` VARCHAR(30) NOT NULL COMMENT '交易类型：sale_debt/purchase_debt/payment_received/payment_made',
    `amount_fen` INT NOT NULL COMMENT '金额（分）',
    `balance_before_fen` INT NOT NULL COMMENT '交易前余额（分）',
    `balance_after_fen` INT NOT NULL COMMENT '交易后余额（分）',
    `related_party_type` VARCHAR(20) NOT NULL COMMENT '关联方类型：customer/supplier',
    `related_party_id` BIGINT NOT NULL COMMENT '关联方ID',
    `related_party_name` VARCHAR(50) NOT NULL COMMENT '关联方名称（冗余）',
    `related_order_type` VARCHAR(30) DEFAULT '' COMMENT '关联订单类型：sales_order/purchase_order/none',
    `related_order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
    `related_order_no` VARCHAR(32) DEFAULT '' COMMENT '关联订单号',
    `write_off_details` JSON DEFAULT NULL COMMENT '核销明细（JSON数组）',
    `payment_method` VARCHAR(20) DEFAULT '' COMMENT '收款方式：wechat/alipay/cash/bank',
    `transaction_time` BIGINT NOT NULL COMMENT '交易时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_no` (`ledger_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_related_party` (`related_party_type`, `related_party_id`),
    KEY `idx_related_order` (`related_order_type`, `related_order_id`),
    KEY `idx_transaction_time` (`transaction_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水表';

-- ==================== 10. 规格类型表 ====================
CREATE TABLE IF NOT EXISTS `spec_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `category_no` VARCHAR(32) DEFAULT '' COMMENT '类型编号（SPEC20260001）',
    `name` VARCHAR(50) NOT NULL COMMENT '规格类型名称',
    `unit` VARCHAR(20) DEFAULT '' COMMENT '单位',
    `sort_order` INT DEFAULT 0 COMMENT '排序值',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/archived',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格类型表';

-- ==================== 11. 规格值表 ====================
CREATE TABLE IF NOT EXISTS `spec_values` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `category_id` BIGINT NOT NULL COMMENT '关联规格类型ID',
    `category_name` VARCHAR(50) DEFAULT '' COMMENT '规格类型名称（冗余）',
    `value` VARCHAR(50) NOT NULL COMMENT '规格值',
    `label` VARCHAR(50) DEFAULT '' COMMENT '显示标签',
    `sort_order` INT DEFAULT 0 COMMENT '排序值',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/archived',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值表';

-- ==================== 12. 商品种类表 ====================
CREATE TABLE IF NOT EXISTS `product_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '商品名称',
    `default_unit` VARCHAR(20) DEFAULT '斤' COMMENT '默认单位',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/archived',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品种类表';

-- ==================== 13. 商品规格关联表 ====================
CREATE TABLE IF NOT EXISTS `product_spec_links` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID',
    `product_name` VARCHAR(50) NOT NULL COMMENT '成品名称',
    `category_id` BIGINT NOT NULL COMMENT '规格类型ID',
    `category_name` VARCHAR(50) DEFAULT '' COMMENT '规格类型名称（冗余）',
    `is_required` TINYINT(1) DEFAULT 0 COMMENT '是否必选',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_product_name` (`product_name`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格关联表';

-- ==================== 14. 导出日志表 ====================
CREATE TABLE IF NOT EXISTS `export_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `module` VARCHAR(50) DEFAULT '' COMMENT '导出模块',
    `module_name` VARCHAR(50) DEFAULT '' COMMENT '模块中文名',
    `format` VARCHAR(20) DEFAULT '' COMMENT '导出格式：csv/json/excel',
    `date_from` VARCHAR(20) DEFAULT '' COMMENT '筛选开始日期',
    `date_to` VARCHAR(20) DEFAULT '' COMMENT '筛选截止日期',
    `status_filter` VARCHAR(20) DEFAULT '' COMMENT '状态筛选',
    `count` INT DEFAULT 0 COMMENT '导出条数',
    `total_count` INT DEFAULT 0 COMMENT '符合条件的总数',
    `result` VARCHAR(20) DEFAULT 'success' COMMENT '结果：success/failed',
    `create_time` BIGINT NOT NULL COMMENT '操作时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出日志表';
