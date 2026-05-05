package com.saas.fruit.service;

import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SupplierCreateRequest;
import com.saas.fruit.dto.request.SupplierUpdateRequest;
import com.saas.fruit.entity.Supplier;

/**
 * 供应商管理服务接口
 * 负责供应商的增删改查、搜索筛选、分页列表、状态切换等业务逻辑
 */
public interface SupplierService {

    /**
     * 分页查询供应商列表
     * 支持关键词模糊搜索（name/phone/contactPerson/supplierNo），按状态筛选
     *
     * @param keyword  搜索关键词
     * @param status   供应商状态筛选：active/inactive/blacklist，为null则不过滤（排除deleted）
     * @param page     页码（从0开始）
     * @param pageSize 每页条数
     * @return 分页响应，包含供应商列表及分页信息
     */
    PageResponse<Supplier> list(String keyword, String status, int page, int pageSize);

    /**
     * 创建新供应商
     * 自动生成供应商编号（GYS + 年份 + 4位序号），校验名称在当前租户下的唯一性
     *
     * @param req 供应商创建请求
     * @return 创建成功的供应商实体
     */
    Supplier create(SupplierCreateRequest req);

    /**
     * 更新供应商信息
     * 校验供应商存在后更新名称、电话、联系人、地址、产地等基本信息
     *
     * @param req 供应商更新请求
     */
    void update(SupplierUpdateRequest req);

    /**
     * 删除供应商（软删除）
     * 将供应商状态标记为deleted
     *
     * @param id 供应商ID
     */
    void delete(Long id);

    /**
     * 查询供应商详情
     *
     * @param id 供应商ID
     * @return 供应商实体
     */
    Supplier detail(Long id);

    /**
     * 切换供应商启用/停用状态
     * active <-> inactive 互相切换
     *
     * @param id 供应商ID
     */
    void toggleStatus(Long id);
}
