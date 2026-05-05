package com.saas.fruit.dto.request;

import lombok.Data;

/**
 * 客户更新请求DTO
 * 用于修改已有客户信息，customerId和name为必填字段
 */
@Data
public class CustomerUpdateRequest {

    /** 客户ID（必填） */
    private Long customerId;

    /** 客户名称（必填） */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;
}
