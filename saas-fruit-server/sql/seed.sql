-- ============================================
-- 水果进销存SaaS系统 - 测试数据
-- ============================================

USE saas_fruit;

-- Mock用户
INSERT INTO users (id, openid, tenant_id, role, nickname, status, last_login_time, create_time, update_time)
VALUES (1, 'mock_openid_dev_001', 'default', 'admin', '测试管理员', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 示例客户
INSERT INTO customers (id, tenant_id, customer_no, name, phone, customer_type, status, create_time, update_time)
VALUES (1, 'default', 'KH202605030001', '张记水果超市', '13800138001', 'wholesale', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (2, 'default', 'KH202605030002', '李明水果批发', '13800138002', 'both', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 示例供应商
INSERT INTO suppliers (id, tenant_id, supplier_no, name, phone, origin, supplier_type, status, create_time, update_time)
VALUES (1, 'default', 'GYS20260001', '王大山果园', '13900139001', '陕西洛川', 'farmer', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (2, 'default', 'GYS20260002', '山东果品合作社', '13900139002', '山东烟台', 'cooperative', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 示例规格类型
INSERT INTO spec_categories (id, tenant_id, category_no, name, unit, sort_order, status, create_time, update_time)
VALUES (1, 'default', 'SPEC20260001', '果径规格', 'mm', 1, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (2, 'default', 'SPEC20260002', '等级', '', 2, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 示例规格值
INSERT INTO spec_values (id, tenant_id, category_id, category_name, value, label, sort_order, status, create_time, update_time)
VALUES (1, 'default', 1, '果径规格', '80', '80果', 1, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (2, 'default', 1, '果径规格', '90', '90果', 2, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (3, 'default', 1, '果径规格', '100', '100果', 3, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (4, 'default', 2, '等级', '特级', '特级', 1, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (5, 'default', 2, '等级', '一级', '一级', 2, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (6, 'default', 2, '等级', '二级', '二级', 3, 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

-- 示例商品种类
INSERT INTO product_categories (id, tenant_id, name, default_unit, status, create_time, update_time)
VALUES (1, 'default', '红富士苹果', '斤', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
       (2, 'default', '脐橙', '斤', 'active', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);
