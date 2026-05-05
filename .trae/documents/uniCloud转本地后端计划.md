# uniCloud → SpringBoot + MyBatis + MySQL 迁移计划

## 一、为什么选 SpringBoot + MyBatis + MySQL

| 对比维度 | uniCloud (MongoDB) | SpringBoot + MyBatis + MySQL |
|---|---|---|
| **事务支持** | 需副本集配置，门槛高 | `@Transactional` 一行注解，开箱即用 |
| **库存并发扣减** | 文档锁，易超卖 | `SELECT ... FOR UPDATE` 行级锁，安全可靠 |
| **财务对账** | 聚合管道写起来复杂 | SQL `SUM/GROUP BY/JOIN` 直观高效 |
| **金额精度** | Number 类型可能丢精度 | `DECIMAL(12,2)` 天然精确 |
| **本地调试** | 需要 uniCloud 环境 | IDEA 直接断点调试，秒级热加载 |
| **部署成本** | 按量付费，不可控 | 轻量服务器 ¥68/月，流量不限 |
| **学习曲线** | uniCloud 特有 API | SpringBoot 是 Java 生态最主流框架 |

### 关键结论
你的进销存系统核心场景（采购→加工→库存→销售→收款）是一个**典型的 OLTP 事务链**，MySQL 的关系模型 + Spring 事务管理是最佳搭配。

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                     微信小程序前端                        │
│                  uni-app (Vue 3)                         │
│                                                         │
│  pages/login  pages/dashboard  pages/sales/billing      │
│  pages/purchase/create  pages/inventory/list  ...       │
└─────────────────────┬───────────────────────────────────┘
                      │  HTTP/HTTPS (uni.request)
                      │  JSON 格式
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   Nginx (可选，生产用)                    │
│              反向代理 + HTTPS + 静态资源                  │
│                   port 443 → port 8080                   │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│              SpringBoot 2.7.x / 3.x                      │
│              (内嵌 Tomcat, port 8080)                     │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐    │
│  │ Controller│→│ Service  │→│  Mapper (MyBatis)   │    │
│  │  (REST)   │  │ (业务逻辑)│  │  XML / 注解 SQL     │    │
│  └──────────┘  └──────────┘  └────────┬───────────┘    │
│                                        │                │
│  ┌───────────────────────────────────┘                 │
│  │  MyBatis-Plus (可选，简化 CRUD)                      │
│  │  Spring Transaction (@Transactional)                │
│  │  JWT Filter (认证拦截器)                              │
│  │  Knife4j / Swagger (API 文档)                        │
│  └───────────────────────────────────────────────────── │
└─────────────────────┬───────────────────────────────────┘
                      │  JDBC
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   MySQL 8.0                              │
│                                                         │
│  users  customers  suppliers  purchases                  │
│  sales_orders  sales_order_items  inventories           │
│  processing_orders  processing_order_items              │
│  financial_ledgers  spec_categories  spec_values        │
│  product_categories  product_spec_links  export_logs    │
└─────────────────────────────────────────────────────────┘
```

---

## 三、MySQL 数据库设计

### 3.1 MongoDB 文档 → MySQL 关系表 对照

由于 MongoDB 是文档型，sales_orders 中内嵌了 `order_items` 数组，转为 MySQL 需要拆表：

```
MongoDB                               MySQL
─────────────────────────────────────────────────────
users                                 users
customers                             customers
suppliers                             suppliers
purchases                             purchases
sales_orders {                        sales_orders
  order_items: [...]  ──拆表──→       sales_order_items
}
processing_orders {                   processing_orders
  items: [...]       ──拆表──→        processing_order_items
}
financial_ledgers {                   financial_ledgers
  write_off_details: [...] ──保留──→  write_off_details (JSON列)
}
inventories                           inventories
spec_categories                       spec_categories
spec_values                           spec_values
product_categories                    product_categories
product_spec_links                    product_spec_links
export_logs                           export_logs
```

### 3.2 核心建表 SQL

```sql
-- ==================== 1. 用户表 ====================
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信openid',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID（SaaS隔离）',
    `role` VARCHAR(20) NOT NULL DEFAULT 'admin' COMMENT '角色：admin-管理员',
    `nickname` VARCHAR(50) DEFAULT '微信用户' COMMENT '昵称',
    `avatar_url` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    `last_login_time` BIGINT DEFAULT 0 COMMENT '最后登录时间戳(ms)',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳(ms)',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳(ms)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==================== 2. 客户表 ====================
CREATE TABLE `customers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '客户名称',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    `address` VARCHAR(200) DEFAULT '' COMMENT '地址',
    `customer_type` VARCHAR(20) NOT NULL DEFAULT 'wholesale' COMMENT '客户类型：wholesale/retail/both',
    `credit_limit_fen` INT NOT NULL DEFAULT 0 COMMENT '信用额度（分）0=不限',
    `total_debt_fen` INT NOT NULL DEFAULT 0 COMMENT '当前总欠款（分）',
    `total_sales_fen` INT NOT NULL DEFAULT 0 COMMENT '累计销售额（分）',
    `total_paid_fen` INT NOT NULL DEFAULT 0 COMMENT '累计已收款（分）',
    `order_count` INT NOT NULL DEFAULT 0 COMMENT '订单数量',
    `last_order_time` BIGINT DEFAULT 0 COMMENT '最近下单时间戳',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_phone` (`phone`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- ==================== 3. 供应商表 ====================
CREATE TABLE `suppliers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '供应商名称',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    `origin` VARCHAR(50) DEFAULT '' COMMENT '主要产地',
    `total_debt_fen` INT NOT NULL DEFAULT 0 COMMENT '当前总欠款（分）',
    `total_purchase_fen` INT NOT NULL DEFAULT 0 COMMENT '累计采购额（分）',
    `total_paid_fen` INT NOT NULL DEFAULT 0 COMMENT '累计已付款（分）',
    `purchase_count` INT NOT NULL DEFAULT 0 COMMENT '采购次数',
    `first_purchase_time` BIGINT DEFAULT 0 COMMENT '首次采购时间戳',
    `last_purchase_time` BIGINT DEFAULT 0 COMMENT '最近采购时间戳',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive/blacklist',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ==================== 4. 采购单表 ====================
CREATE TABLE `purchases` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `batch_no` VARCHAR(20) NOT NULL COMMENT '采购批次号（CG202605030001）',
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
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending-待加工/processing-加工中/done-已完成/cancelled-已取消',
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
CREATE TABLE `sales_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `order_no` VARCHAR(20) NOT NULL COMMENT '订单号（XS202605030001）',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(50) NOT NULL COMMENT '客户名称（冗余）',
    `sale_type` VARCHAR(20) NOT NULL COMMENT '销售类型：wholesale/retail',
    `total_weight_jin` DECIMAL(10,2) NOT NULL COMMENT '总重量（斤）',
    `total_amount_fen` INT NOT NULL COMMENT '总金额（分）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '总成本（分，FIFO计算）',
    `gross_profit_fen` INT NOT NULL DEFAULT 0 COMMENT '毛利（分）',
    `paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    `debt_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '欠款金额（分）',
    `payment_status` VARCHAR(20) NOT NULL DEFAULT 'unpaid' COMMENT '收款状态：unpaid/partial/paid',
    `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '状态：normal/cancelled',
    `order_time` BIGINT NOT NULL COMMENT '下单时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_time` (`order_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单表';

-- ==================== 6. 销售订单明细表 ====================
CREATE TABLE `sales_order_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID（关联sales_orders.id）',
    `inventory_id` BIGINT NOT NULL COMMENT '库存ID（关联inventories.id）',
    `product_name` VARCHAR(50) NOT NULL COMMENT '商品名称（冗余）',
    `spec` VARCHAR(50) DEFAULT '' COMMENT '规格',
    `grade` VARCHAR(50) DEFAULT '' COMMENT '等级',
    `weight_jin` DECIMAL(10,2) NOT NULL COMMENT '重量（斤）',
    `unit_price_fen` INT NOT NULL COMMENT '单价（分/斤）',
    `subtotal_fen` INT NOT NULL COMMENT '小计金额（分）',
    `unit_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '单位成本（分/斤，FIFO）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '总成本（分）',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_inventory_id` (`inventory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

-- ==================== 7. 库存表 ====================
CREATE TABLE `inventories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `product_name` VARCHAR(50) NOT NULL COMMENT '商品名称',
    `spec` VARCHAR(50) DEFAULT '' COMMENT '规格',
    `grade` VARCHAR(50) DEFAULT '' COMMENT '等级',
    `current_stock_jin` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '当前库存（斤）',
    `unit_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '加权平均成本（分/斤）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '库存总成本（分）',
    `suggested_price_fen` INT DEFAULT 0 COMMENT '建议售价（分/斤）',
    `warning_stock_jin` DECIMAL(10,2) DEFAULT 10.00 COMMENT '预警库存量（斤）',
    `source_batches` JSON DEFAULT NULL COMMENT '来源批次信息（JSON，记录FIFO批次）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    `last_in_time` BIGINT DEFAULT 0 COMMENT '最近入库时间戳',
    `last_out_time` BIGINT DEFAULT 0 COMMENT '最近出库时间戳',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_name` (`product_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- ==================== 8. 加工订单表 ====================
CREATE TABLE `processing_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `order_no` VARCHAR(20) NOT NULL COMMENT '加工单号（JG202605030001）',
    `source_purchase_id` BIGINT DEFAULT NULL COMMENT '来源采购单ID',
    `source_batch_no` VARCHAR(20) DEFAULT '' COMMENT '来源采购批次号',
    `raw_fruit_name` VARCHAR(50) NOT NULL COMMENT '原果名称',
    `raw_weight_jin` DECIMAL(10,2) NOT NULL COMMENT '原果投入重量（斤）',
    `raw_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '原果成本（分）',
    `labor_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '人工费用（分）',
    `other_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '其他费用（分）',
    `total_cost_fen` INT NOT NULL DEFAULT 0 COMMENT '加工总成本（分）',
    `output_items` JSON DEFAULT NULL COMMENT '产出商品列表（JSON：[{name,spec,grade,weight_jin,...}]）',
    `waste_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '损耗率（%）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'done' COMMENT '状态：done/cancelled',
    `process_time` BIGINT NOT NULL COMMENT '加工时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    `update_time` BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_source_purchase_id` (`source_purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工订单表';

-- ==================== 9. 财务流水表 ====================
CREATE TABLE `financial_ledgers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
    `ledger_no` VARCHAR(20) NOT NULL COMMENT '流水号（FK202605030001/SK202605030001）',
    `ledger_type` VARCHAR(20) NOT NULL COMMENT '流水类型：payable-应付/receivable-应收',
    `transaction_type` VARCHAR(30) NOT NULL COMMENT '交易类型：purchase_debt/payment_made/sales_debt/payment_received',
    `amount_fen` INT NOT NULL COMMENT '金额（分）正=增加欠款，负=还款',
    `balance_before_fen` INT NOT NULL COMMENT '交易前余额（分）',
    `balance_after_fen` INT NOT NULL COMMENT '交易后余额（分）',
    `related_party_type` VARCHAR(20) NOT NULL COMMENT '关联方类型：supplier/customer',
    `related_party_id` BIGINT NOT NULL COMMENT '关联方ID',
    `related_party_name` VARCHAR(50) NOT NULL COMMENT '关联方名称（冗余）',
    `related_order_type` VARCHAR(30) DEFAULT '' COMMENT '关联订单类型：purchase_order/sales_order',
    `related_order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
    `related_order_no` VARCHAR(20) DEFAULT '' COMMENT '关联订单号',
    `write_off_details` JSON DEFAULT NULL COMMENT '核销明细（JSON数组）',
    `transaction_time` BIGINT NOT NULL COMMENT '交易时间戳',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '备注',
    `operator_id` VARCHAR(64) DEFAULT '' COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作人名称',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_no` (`ledger_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_related_party` (`related_party_id`),
    KEY `idx_related_order` (`related_order_id`),
    KEY `idx_transaction_time` (`transaction_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水表';

-- ==================== 10-13. 规格/商品/导出表（结构较简单，略） ====================
-- spec_categories, spec_values, product_categories, product_spec_links, export_logs
-- 这些表直接按 schema.json 转建表即可，结构简单不再展开
```

### 3.3 金额字段处理

```sql
-- 所有涉及金额的字段统一使用 INT，单位"分"
-- 查询时在 SQL 中转换：
SELECT total_amount_fen / 100 AS total_amount_yuan FROM sales_orders;

-- 前端展示用工具函数 fenToYuan/fenToYuan，与原有保持一致
-- MySQL 中的 DECIMAL(10,2) 仅用于重量等非金额的浮点数
```

---

## 四、SpringBoot 项目结构

```
saas-fruit-server/                     # 后端项目（独立于uni-app项目）
├── pom.xml                            # Maven依赖
├── src/main/java/com/saas/fruit/
│   ├── FruitApplication.java          # SpringBoot启动类
│   ├── config/
│   │   ├── WebConfig.java             # CORS跨域配置
│   │   ├── MyBatisPlusConfig.java     # MyBatis-Plus分页插件
│   │   └── Knife4jConfig.java         # API文档配置
│   ├── security/
│   │   ├── JwtTokenUtil.java          # JWT生成/验证工具
│   │   ├── JwtAuthFilter.java         # JWT认证过滤器
│   │   └── LoginUser.java             # 登录用户上下文
│   ├── controller/
│   │   ├── AuthController.java        # 登录/注册
│   │   ├── CustomerController.java    # 客户管理
│   │   ├── SupplierController.java    # 供应商管理
│   │   ├── PurchaseController.java    # 采购管理
│   │   ├── SalesController.java       # 销售管理
│   │   ├── InventoryController.java   # 库存管理
│   │   ├── ProcessingController.java  # 加工管理
│   │   ├── FinanceController.java     # 财务管理
│   │   ├── SpecController.java        # 规格管理
│   │   ├── ProductController.java     # 商品种类
│   │   ├── DashboardController.java   # 经营看板
│   │   └── ExportController.java      # 数据导出
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── CustomerService.java
│   │   ├── SupplierService.java
│   │   ├── PurchaseService.java
│   │   ├── SalesService.java
│   │   ├── InventoryService.java
│   │   ├── ProcessingService.java
│   │   ├── FinanceService.java
│   │   ├── SpecService.java
│   │   ├── ProductService.java
│   │   ├── DashboardService.java
│   │   └── ExportService.java
│   ├── service/impl/                  # Service实现类
│   │   ├── AuthServiceImpl.java
│   │   ├── CustomerServiceImpl.java
│   │   └── ...（11个实现类）
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── CustomerMapper.java
│   │   ├── SupplierMapper.java
│   │   ├── PurchaseMapper.java
│   │   ├── SalesOrderMapper.java
│   │   ├── SalesOrderItemMapper.java
│   │   ├── InventoryMapper.java
│   │   ├── ProcessingOrderMapper.java
│   │   ├── FinancialLedgerMapper.java
│   │   ├── SpecCategoryMapper.java
│   │   ├── SpecValueMapper.java
│   │   ├── ProductCategoryMapper.java
│   │   ├── ProductSpecLinkMapper.java
│   │   └── ExportLogMapper.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Customer.java
│   │   └── ...（13个实体类）
│   ├── dto/                           # 请求/响应DTO
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   ├── CustomerCreateRequest.java
│   │   │   ├── PurchaseCreateRequest.java
│   │   │   ├── SalesOrderCreateRequest.java
│   │   │   └── ...（按需创建）
│   │   └── response/
│   │       ├── ApiResponse.java       # 统一响应：{code, message, data}
│   │       ├── PageResponse.java      # 分页响应：{code, message, data: {list, total, page, pageSize}}
│   │       ├── LoginResponse.java
│   │       ├── DashboardResponse.java
│   │       └── ...（按需创建）
│   ├── common/
│   │   ├── BusinessException.java     # 业务异常
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   └── utils/
│       ├── AmountUtil.java            # 金额转换（分↔元）
│       ├── BatchNoGenerator.java      # 单号生成器
│       └── DateUtil.java              # 时间工具
├── src/main/resources/
│   ├── application.yml                # 主配置
│   ├── application-dev.yml            # 开发环境
│   ├── application-prod.yml           # 生产环境
│   └── mapper/                        # MyBatis XML（复杂SQL放这里）
│       ├── CustomerMapper.xml
│       ├── PurchaseMapper.xml
│       ├── SalesOrderMapper.xml
│       ├── InventoryMapper.xml
│       ├── FinancialLedgerMapper.xml
│       └── DashboardMapper.xml
└── sql/
    ├── init.sql                       # 建表SQL（上面写的那些）
    └── seed.sql                       # 测试数据SQL
```

---

## 五、核心实现要点

### 5.1 pom.xml 核心依赖
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>  <!-- 稳定版，JDK8/11/17都支持 -->
</parent>

<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- MyBatis-Plus（比原生MyBatis更方便，自带分页、CRUD） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.5</version>
    </dependency>

    <!-- MySQL驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok（减少getter/setter代码） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Knife4j（API文档） -->
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi2-spring-boot-starter</artifactId>
        <version>4.4.0</version>
    </dependency>

    <!-- DevTools（热加载，仅开发用） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 5.2 事务处理（超级简单）

```java
// uniCloud 旧写法（复杂！）
const result = await db.runTransaction(async (transaction) => {
    await transaction.collection('purchases').add(purchase);
    await transaction.collection('financial_ledgers').add(ledger);
    await transaction.collection('suppliers').doc(id).update(data);
});

// SpringBoot 新写法（一行注解搞定！）
@Service
public class PurchaseServiceImpl implements PurchaseService {
    
    @Autowired
    private PurchaseMapper purchaseMapper;
    @Autowired
    private FinancialLedgerMapper ledgerMapper;
    @Autowired
    private SupplierMapper supplierMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)  // ← 就这一行！
    public void createPurchase(PurchaseCreateRequest req) {
        // 步骤1：插入采购单
        Purchase purchase = buildPurchase(req);
        purchaseMapper.insert(purchase);
        
        // 步骤2：生成财务流水
        FinancialLedger debtLedger = buildDebtLedger(purchase);
        ledgerMapper.insert(debtLedger);
        
        if (req.getPaidAmountFen() > 0) {
            FinancialLedger payLedger = buildPaymentLedger(purchase);
            ledgerMapper.insert(payLedger);
        }
        
        // 步骤3：更新供应商统计
        supplierMapper.updatePurchaseStats(req.getSupplierId(), purchase);
        
        // 任何一步抛异常，前面全部自动回滚！
    }
}
```

### 5.3 库存扣减（乐观锁 / 行级锁）

```java
// 方式1：行级锁（推荐，简单可靠）
@Select("SELECT * FROM inventories WHERE id = #{id} FOR UPDATE")
Inventory selectByIdForUpdate(@Param("id") Long id);

// 方式2：乐观锁（MyBatis-Plus自带）
@TableName("inventories")
public class Inventory {
    @Version  // 版本号，每次更新自动+1
    private Integer version;
    
    private BigDecimal currentStockJin;
}
```

### 5.4 统一响应格式

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private int code;       // 0成功，400参数错误，500服务器错误
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "操作成功", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

// 分页响应
@Data
public class PageResponse<T> extends ApiResponse<PageResponse.PageData<T>> {
    
    @Data
    public static class PageData<T> {
        private List<T> list;
        private long total;
        private int page;
        private int pageSize;
        private boolean hasMore;
    }
}
```

### 5.5 JWT 认证流程

```
1. 用户在小程序授权 → wx.login() 获取 code
2. 前端 POST /api/auth/login { code: "xxx" }
3. 后端调用微信 code2session 接口 → 获取 openid
4. 查 users 表 → 新用户插入，老用户更新登录时间
5. 生成 JWT token → 返回 { token, userId, tenantId, ... }
6. 前端存 token → 后续请求都带 Authorization: Bearer <token>
7. JwtAuthFilter 拦截器校验 token → 解析 userId/tenantId 到请求上下文
```

### 5.6 本地开发 Mock 登录

```java
// application-dev.yml
app:
  mock-login: true          # 本地开发跳过微信接口
  mock-openid: "mock_openid_dev_001"

// AuthServiceImpl.java
public LoginResponse login(String code) {
    String openid;
    if (mockLogin) {
        openid = mockOpenid;           // 本地直接用mock openid
    } else {
        openid = callWechatApi(code);  // 生产环境调微信接口
    }
    // ... 查库、生成JWT
}
```

---

## 六、前端适配（与方案无关，两种后端共用）

### 6.1 新增文件

**`config/api.config.js`**（项目根目录下新建 config 目录）
```javascript
// 环境切换开关
export const USE_LOCAL_API = true          // true=本地/自建服务器, false=uniCloud
export const API_BASE_URL = 'http://localhost:8080/api'  // 开发环境
// export const API_BASE_URL = 'https://your-domain.com/api'  // 生产环境
```

**`utils/api.js`**（统一请求封装）
```javascript
import { USE_LOCAL_API, API_BASE_URL } from '@/config/api.config.js'

const token = () => uni.getStorageSync('token')

async function request(url, method = 'GET', data = null) {
    if (USE_LOCAL_API) {
        // 本地/自建服务器：HTTP请求
        return new Promise((resolve, reject) => {
            uni.request({
                url: API_BASE_URL + url,
                method: method,
                data: data,
                header: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token()
                },
                success: (res) => {
                    if (res.statusCode === 200) {
                        resolve({ result: res.data })
                    } else {
                        reject(res.data)
                    }
                },
                fail: reject
            })
        })
    } else {
        // uniCloud模式：保持原有逻辑
        const cloudFunctionName = getCloudFunctionName(url)
        return uniCloud.callFunction({
            name: cloudFunctionName,
            data: data
        })
    }
}

function get(url, params = {}) {
    const query = Object.keys(params)
        .filter(k => params[k] !== undefined && params[k] !== '')
        .map(k => `${k}=${encodeURIComponent(params[k])}`)
        .join('&')
    return request(url + (query ? '?' + query : ''), 'GET')
}

function post(url, data) { return request(url, 'POST', data) }
function put(url, data) { return request(url, 'PUT', data) }
function del(url) { return request(url, 'DELETE') }

// ==================== API方法导出 ====================
export const authAPI = {
    login: (code) => post('/auth/login', { code }),
    check: () => get('/auth/check')
}

export const customerAPI = {
    list: (params) => get('/customers', params),
    detail: (id) => get(`/customers/${id}`),
    create: (data) => post('/customers', data),
    update: (id, data) => put(`/customers/${id}`, data),
    remove: (id) => del(`/customers/${id}`)
}

export const purchaseAPI = {
    create: (data) => post('/purchases', data),
    cancel: (id) => post(`/purchases/${id}/cancel`)
}

export const salesAPI = {
    create: (data) => post('/sales', data),
    history: (params) => get('/sales/history', params)
}

export const inventoryAPI = {
    list: (params) => get('/inventory', params),
    detail: (id) => get(`/inventory/${id}`)
}

export const processingAPI = {
    create: (data) => post('/processing', data),
    cancel: (id) => post(`/processing/${id}/cancel`),
    history: (params) => get('/processing/history', params)
}

export const financeAPI = {
    receive: (data) => post('/finance/receive', data),
    ledgers: (params) => get('/finance/ledgers', params)
}

export const dashboardAPI = {
    get: () => get('/dashboard')
}

export const supplierAPI = {
    list: (params) => get('/suppliers', params),
    create: (data) => post('/suppliers', data),
    update: (id, data) => put(`/suppliers/${id}`, data),
    remove: (id) => del(`/suppliers/${id}`)
}

export const specAPI = {
    list: () => get('/specs'),
    create: (data) => post('/specs', data),
    update: (id, data) => put(`/specs/${id}`, data),
    remove: (id) => del(`/specs/${id}`)
}

export const productAPI = {
    list: (params) => get('/products', params),
    create: (data) => post('/products', data)
}

export const exportAPI = {
    submit: (data) => post('/export', data)
}
```

### 6.2 页面改造示例

以 `pages/customer/list.vue` 为例：

```javascript
// ========== 改造前 ==========
const res = await uniCloud.callFunction({
    name: 'get_customer_list',
    data: {
        keyword: searchKeyword.value,
        customerType: filterType.value,
        debtStatus: filterDebtStatus.value,
        page: currentPage.value,
        pageSize: pageSize
    }
})

// ========== 改造后 ==========
import { customerAPI } from '@/utils/api.js'

const res = await customerAPI.list({
    keyword: searchKeyword.value,
    customerType: filterType.value,
    debtStatus: filterDebtStatus.value,
    page: currentPage.value,
    pageSize: pageSize
})
```

**改造模式：找到文件中所有的 `uniCloud.callFunction({ name: 'xxx' })` 和 `uniCloud.database()` → 替换为对应的 API 方法。**

---

## 七、服务器部署方案

### 7.1 推荐配置

| 项目 | 配置 | 月费参考 |
|---|---|---|
| 阿里云轻量应用服务器 | 2核2G, 50GB SSD, 3M带宽 | ¥68/月 |
| 腾讯云轻量应用服务器 | 同上 | ¥58/月 |
| 京东云轻量云主机 | 同上 | ¥50/月 |

**系统选 CentOS 7.9 或 Ubuntu 22.04**，这配置跑 SpringBoot + MySQL 完全够用。

### 7.2 部署步骤

```bash
# ========== 1. 服务器基础环境 ==========
# 安装 JDK 17
yum install -y java-17-openjdk java-17-openjdk-devel

# 安装 MySQL 8.0
yum install -y mysql80-server
systemctl start mysqld
systemctl enable mysqld

# 获取临时密码并修改
grep 'temporary password' /var/log/mysqld.log
mysql_secure_installation

# ========== 2. 创建数据库 ==========
mysql -u root -p
CREATE DATABASE saas_fruit DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
source /path/to/init.sql
EXIT;

# ========== 3. 打包部署 ==========
# 本地IDE打包
mvn clean package -DskipTests

# 上传jar包到服务器
scp target/saas-fruit-server-1.0.0.jar root@your-server:/opt/saas-fruit/

# ========== 4. 启动服务 ==========
# 直接用 java -jar 启动（后台运行）
nohup java -jar /opt/saas-fruit/saas-fruit-server-1.0.0.jar \
    --spring.profiles.active=prod \
    > /opt/saas-fruit/app.log 2>&1 &

# 或使用 systemd 管理（推荐）
cat > /etc/systemd/system/saas-fruit.service << 'EOF'
[Unit]
Description=SAAS Fruit Server
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/saas-fruit
ExecStart=/usr/bin/java -jar /opt/saas-fruit/saas-fruit-server-1.0.0.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl start saas-fruit
systemctl enable saas-fruit  # 开机自启

# ========== 5. 查看日志 ==========
tail -f /opt/saas-fruit/app.log
# 或
journalctl -u saas-fruit -f

# ========== 6. 开放端口 ==========
# 阿里云安全组/防火墙开放 8080 端口
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --reload
```

### 7.3 微信小程序域名配置

```
小程序后台 → 开发管理 → 开发设置 → 服务器域名
添加 request合法域名：https://your-domain.com
```

如果还没有域名，可以先用 IP + HTTP 在开发者工具中测试（开发阶段不校验域名）。

---

## 八、实施步骤总览

| 步骤 | 内容 | 说明 |
|---|---|---|
| **步骤1** | 搭建 SpringBoot 项目骨架 | Maven项目、依赖、启动类 |
| **步骤2** | 执行建表SQL | 在本地MySQL创建13张表 |
| **步骤3** | 创建13个Entity + 14个Mapper | 用MyBatis-Plus代码生成器快速生成 |
| **步骤4** | 迁移云函数→Service层 | 将17个云函数的JS逻辑翻译为Java |
| **步骤5** | 创建11个Controller | RESTful API接口 |
| **步骤6** | JWT认证 + 跨域配置 | 安全模块 |
| **步骤7** | 前端API层 + 页面改造 | `utils/api.js` + 20个页面的调用替换 |
| **步骤8** | 本地联调测试 | 后端跑IDEA，前端跑微信开发者工具 |
| **步骤9** | 购买服务器 + 部署 | 买轻量服务器，部署上线 |
| **步骤10** | 微信小程序配置域名 | 小程序后台配置request合法域名 |

---

## 九、文件变更清单

### 新增文件（后端独立项目）
`saas-fruit-server/` 下约 80+ 个文件（Entity、Mapper、Service、Controller、配置等）

### 新增文件（uni-app 前端项目内）
| 文件 | 说明 |
|---|---|
| `config/api.config.js` | 环境切换配置 |
| `utils/api.js` | 统一API调用封装 |

### 修改文件（前端页面）
| 文件 | 改动内容 |
|---|---|
| `pages/login/index.vue` | wx.login → api.js 调用 |
| `pages/dashboard/index.vue` | callFunction → dashboardAPI.get() |
| `pages/customer/list.vue` | callFunction → customerAPI.list() |
| `pages/customer/detail.vue` | callFunction → customerAPI.detail() |
| `pages/purchase/create.vue` | callFunction + database() → purchaseAPI + supplierAPI |
| `pages/purchase/list.vue` | 同上模式 |
| `pages/sales/billing.vue` | callFunction → salesAPI + customerAPI + inventoryAPI |
| `pages/sales/history.vue` | 同上模式 |
| `pages/inventory/list.vue` | callFunction → inventoryAPI.list() |
| `pages/inventory/detail.vue` | 同上模式 |
| `pages/processing/create.vue` | callFunction → processingAPI.create() |
| `pages/processing/list.vue` | 同上模式 |
| `pages/finance/index.vue` | callFunction → financeAPI.ledgers() |
| `pages/finance/receive.vue` | callFunction → financeAPI.receive() |
| `pages/supplier/list.vue` | callFunction → supplierAPI |
| `pages/spec/list.vue` | callFunction → specAPI |
| `pages/product/list.vue` | callFunction → productAPI |
| `pages/settings/export.vue` | callFunction → exportAPI |
| `pages/my/index.vue` | 用户信息展示（如有后端调用需改） |

### 保持不动
| 目录/文件 | 原因 |
|---|---|
| `uniCloud-aliyun/` | 保留原始云函数代码，作为备份 |
| `uni_modules/` | uni-app模块不动 |
| `static/` | 静态资源不动 |
| `utils/util.js` | 通用工具函数（金额转换等）共用 |
| `pages.json` | 路由配置不动 |
| `manifest.json` | 基本不动 |

---

## 十、总结

| 对比维度 | Node.js方案 | **SpringBoot方案（推荐）** |
|---|---|---|
| 事务实现 | 手动管理MongoDB Session | `@Transactional` 自动管理 |
| 库存并发 | 需要额外处理 | `SELECT FOR UPDATE` 天然支持 |
| SQL灵活性 | 聚合管道 | SQL直观，报表查询方便 |
| 项目成熟度 | 适合小项目 | 进销存这种OLTP系统的最佳选择 |
| 部署 | 需要Node环境 | `java -jar` 一个命令 |
| 服务器要求 | 512MB可以跑 | 2GB内存宽裕运行 |
| 月费成本 | ~¥50 | ~¥68 |
