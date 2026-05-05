package com.saas.fruit.dto.request;

import lombok.Data;

/**
 * 供应商更新请求DTO
 * 用于修改已有供应商信息
 */
@Data
public class SupplierUpdateRequest {

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 联系人 */
    private String contactPerson;

    /** 地址 */
    private String address;

    /** 主要产地 */
    private String origin;

    /** 供应商类型：farmer/wholesaler/cooperative */
    private String supplierType;

    /** 备注 */
    private String remark;
}
