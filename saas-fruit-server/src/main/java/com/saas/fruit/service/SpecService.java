package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SpecManageRequest;
import com.saas.fruit.entity.ProductCategory;
import com.saas.fruit.entity.ProductSpecLink;
import com.saas.fruit.entity.SpecCategory;
import com.saas.fruit.entity.SpecValue;

/**
 * 规格管理服务接口
 * 负责规格分类、规格值、商品规格关联以及商品种类的增删改查
 * 翻译自 spec_manage 云函数的所有 action 逻辑
 */
public interface SpecService {

    // ==================== 规格分类管理 ====================

    /**
     * 分页查询规格分类列表
     * 支持关键词搜索和状态筛选
     *
     * @param keyword  搜索关键词（模糊匹配分类名称）
     * @param status   状态筛选：active/archived，为null则不过滤
     * @param page     页码（从0开始）
     * @param pageSize 每页条数
     * @return 分页响应
     */
    PageResponse<SpecCategory> listCategories(String keyword, String status, int page, int pageSize);

    /**
     * 新增规格分类
     * 校验分类名称在当前租户下的唯一性
     *
     * @param req 规格管理请求（含分类名称、单位、排序等）
     * @return 新增的规格分类实体
     */
    SpecCategory addCategory(SpecManageRequest req);

    /**
     * 更新规格分类
     * 校验分类存在后更新名称、排序等信息
     *
     * @param req 规格管理请求
     */
    void updateCategory(SpecManageRequest req);

    /**
     * 删除规格分类（逻辑删除，状态改为archived）
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);

    // ==================== 规格值管理 ====================

    /**
     * 新增规格值
     * 校验所属分类存在，校验规格值在当前分类下唯一
     *
     * @param req 规格管理请求（含分类ID、规格值、标签等）
     * @return 新增的规格值实体
     */
    SpecValue addValue(SpecManageRequest req);

    /**
     * 更新规格值
     *
     * @param req 规格管理请求
     */
    void updateValue(SpecManageRequest req);

    /**
     * 删除规格值（逻辑删除）
     *
     * @param id 规格值ID
     */
    void deleteValue(Long id);

    /**
     * 批量新增规格值
     * 将逗号分隔的 valuesStr 和 labelsStr 解析为多条规格值记录
     *
     * @param req 规格管理请求（含分类ID、valuesStr、labelsStr）
     */
    void batchAddValues(SpecManageRequest req);

    // ==================== 商品规格关联 ====================

    /**
     * 新增商品规格关联
     *
     * @param req 规格管理请求（含成品名称、分类ID、是否必选等）
     * @return 新增的关联实体
     */
    ProductSpecLink addLink(SpecManageRequest req);

    /**
     * 删除商品规格关联
     *
     * @param id 关联ID
     */
    void deleteLink(Long id);

    // ==================== 商品种类管理 ====================

    /**
     * 分页查询商品种类列表
     *
     * @param keyword  搜索关键词
     * @param page     页码（从0开始）
     * @param pageSize 每页条数
     * @return 分页响应
     */
    PageResponse<ProductCategory> listProducts(String keyword, int page, int pageSize);

    /**
     * 新增商品种类
     *
     * @param req 规格管理请求（含商品名称、默认单位等）
     * @return 新增的商品种类实体
     */
    ProductCategory addProduct(SpecManageRequest req);

    /**
     * 更新商品种类
     *
     * @param req 规格管理请求
     */
    void updateProduct(SpecManageRequest req);

    /**
     * 删除商品种类（逻辑删除，状态改为archived）
     *
     * @param id 商品种类ID
     */
    void deleteProduct(Long id);
}
