package com.saas.fruit.dto.request;

import lombok.Data;

/**
 * 客户创建请求DTO
 * 用于新增客户时接收前端提交的数据
 */
@Data
public class CustomerCreateRequest {

    /** 客户名称 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 客户类型：wholesale/retail/both */
    private String customerType;

    /** 信用额度（分），前端传入的是"元"需由Controller层转换为"分" */
    private Integer creditLimitFen;

    /** 备注 */
    private String remark;
}
