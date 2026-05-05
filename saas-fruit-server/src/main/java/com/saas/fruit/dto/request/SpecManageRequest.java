package com.saas.fruit.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 规格管理通用请求DTO
 * 对应 spec_manage 云函数的所有可能参数
 * 支持分类管理、规格值管理、产品规格关联等多种操作
 * 通过 action 字段区分不同的业务操作类型
 */
@Data
public class SpecManageRequest {

    // ==================== 通用字段 ====================

    /**
     * 操作类型（通用action字段）
     * 可能的取值包括：createCategory, updateCategory, deleteCategory, listCategories,
     * createValue, updateValue, deleteValue, listValues, linkProduct, unlinkProduct 等
     */
    private String action;

    // ==================== 分类管理字段 ====================

    /** 分类ID，用于指定操作的分类 */
    private Long categoryId;

    /** 分类名称 */
    private String name;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：active/inactive */
    private String status;

    /** 分类列表，批量操作时使用 */
    private List<CategoryItem> categories;

    /**
     * 分类项，用于批量创建/更新分类
     */
    @Data
    public static class CategoryItem {

        /** 分类ID（更新时使用） */
        private Long categoryId;

        /** 分类名称 */
        private String name;

        /** 排序序号 */
        private Integer sortOrder;

        /** 状态 */
        private String status;
    }

    // ==================== 规格值管理字段 ====================

    /** 规格值ID */
    private Long valueId;

    /** 规格值 */
    private String value;

    /** 规格值显示标签 */
    private String label;

    /** 规格值列表（逗号分隔字符串） */
    private String valuesStr;

    /** 规格值标签列表（逗号分隔字符串） */
    private String labelsStr;

    // ==================== 产品规格关联字段 ====================

    /** 成品名称 */
    private String productName;

    /** 计量单位 */
    private String unit;

    /** 是否必选 */
    private Boolean isRequired;

    // ==================== 查询/分页字段 ====================

    /** 搜索关键字 */
    private String keyword;

    /** 当前页码，从1开始 */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    // ==================== 通用备注字段 ====================

    /** 备注 */
    private String remark;
}
