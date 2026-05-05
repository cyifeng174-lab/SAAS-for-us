package com.saas.fruit.controller;

import com.saas.fruit.common.ApiResponse;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SpecManageRequest;
import com.saas.fruit.entity.SpecCategory;
import com.saas.fruit.service.SpecService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 规格管理控制器
 * 负责规格分类和规格值的增删改查
 * 支持通过action参数路由到不同的业务方法
 */
@Slf4j
@RestController
@RequestMapping("/api/specs")
public class SpecController {

    @Autowired
    private SpecService specService;

    /**
     * 查询规格分类列表
     */
    @GetMapping
    public ApiResponse<PageResponse<SpecCategory>> listCategories(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        log.info("[规格分类列表] 关键词={}, 状态={}, 页码={}", keyword, status, page);

        PageResponse<SpecCategory> result = specService.listCategories(keyword, status, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 规格管理统一入口
     * 通过请求体中的action字段路由到不同的业务方法
     *
     * 支持的action值：
     * - createCategory：新增规格分类
     * - updateCategory：更新规格分类
     * - deleteCategory：删除规格分类
     * - createValue：新增规格值
     * - updateValue：更新规格值
     * - deleteValue：删除规格值
     * - batchAddValues：批量新增规格值
     * - linkProduct：新增商品规格关联
     * - unlinkProduct：删除商品规格关联
     */
    @PostMapping
    public ApiResponse<?> handleAction(@RequestBody SpecManageRequest req) {
        String action = req.getAction();

        if (action == null || action.isEmpty()) {
            throw new com.saas.fruit.common.BusinessException(400, "缺少action参数");
        }

        log.info("[规格管理] action={}", action);

        switch (action) {
            // ========== 分类管理 ==========
            case "createCategory":
                return ApiResponse.success(specService.addCategory(req), "规格分类创建成功");

            case "updateCategory":
                specService.updateCategory(req);
                return ApiResponse.success(null, "规格分类更新成功");

            case "deleteCategory":
                specService.deleteCategory(req.getCategoryId());
                return ApiResponse.success(null, "规格分类已删除");

            // ========== 规格值管理 ==========
            case "createValue":
                return ApiResponse.success(specService.addValue(req), "规格值创建成功");

            case "updateValue":
                specService.updateValue(req);
                return ApiResponse.success(null, "规格值更新成功");

            case "deleteValue":
                specService.deleteValue(req.getValueId());
                return ApiResponse.success(null, "规格值已删除");

            case "batchAddValues":
                specService.batchAddValues(req);
                return ApiResponse.success(null, "规格值批量新增成功");

            // ========== 商品规格关联 ==========
            case "linkProduct":
                return ApiResponse.success(specService.addLink(req), "商品规格关联创建成功");

            case "unlinkProduct":
                specService.deleteLink(req.getValueId());
                return ApiResponse.success(null, "商品规格关联已删除");

            default:
                throw new com.saas.fruit.common.BusinessException(400, "不支持的action: " + action);
        }
    }
}
