package com.saas.fruit.dto.request;

import lombok.Data;

/**
 * 供应商创建请求DTO
 * 用于新增供应商时接收前端提交的数据
 */
@Data
public class SupplierCreateRequest {

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
