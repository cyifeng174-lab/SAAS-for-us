package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SpecManageRequest;
import com.saas.fruit.entity.ProductCategory;
import com.saas.fruit.entity.ProductSpecLink;
import com.saas.fruit.entity.SpecCategory;
import com.saas.fruit.entity.SpecValue;
import com.saas.fruit.mapper.ProductCategoryMapper;
import com.saas.fruit.mapper.ProductSpecLinkMapper;
import com.saas.fruit.mapper.SpecCategoryMapper;
import com.saas.fruit.mapper.SpecValueMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.SpecService;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 规格管理服务实现类
 * 翻译自 spec_manage 云函数的所有 action 逻辑
 * 负责规格分类、规格值、商品规格关联、商品种类的增删改查
 * 所有操作均在当前租户上下文下进行
 */
@Slf4j
@Service
public class SpecServiceImpl implements SpecService {

    @Autowired
    private SpecCategoryMapper specCategoryMapper;

    @Autowired
    private SpecValueMapper specValueMapper;

    @Autowired
    private ProductSpecLinkMapper productSpecLinkMapper;

    @Autowired
    private ProductCategoryMapper productCategoryMapper;

    // ==================== 规格分类管理 ====================

    /**
     * 分页查询规格分类列表
     * 对应 JS spec_manage 云函数中 action=listCategories 的逻辑
     */
    @Override
    public PageResponse<SpecCategory> listCategories(String keyword, String status, int page, int pageSize) {
        String tenantId = LoginUserContext.getTenantId();

        LambdaQueryWrapper<SpecCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SpecCategory::getTenantId, tenantId);

        // 关键词模糊搜索分类名称
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(SpecCategory::getName, keyword);
        }

        // 状态筛选
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(SpecCategory::getStatus, status);
        }
        // 默认不显示已归档的
        if (!StringUtils.hasText(status)) {
            queryWrapper.ne(SpecCategory::getStatus, "archived");
        }

        queryWrapper.orderByAsc(SpecCategory::getSortOrder);
        queryWrapper.orderByDesc(SpecCategory::getCreateTime);

        Page<SpecCategory> pageResult = specCategoryMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        log.debug("[规格分类列表] 租户={}, 关键词={}, 总数={}", tenantId, keyword, pageResult.getTotal());

        return PageResponse.success(pageResult.getRecords(), pageResult.getTotal(), page, pageSize);
    }

    /**
     * 新增规格分类
     * 对应 JS spec_manage 云函数中 action=createCategory 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecCategory addCategory(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // 校验分类名称
        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException(400, "分类名称不能为空");
        }

        // 校验分类名称唯一性
        Long count = specCategoryMapper.selectCount(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getTenantId, tenantId)
                        .eq(SpecCategory::getName, req.getName())
                        .ne(SpecCategory::getStatus, "archived")
        );
        if (count > 0) {
            throw new BusinessException(400, "规格分类【" + req.getName() + "】已存在");
        }

        // 生成分类编号
        String categoryNo = "SPEC" + DateUtil.getDateStr()
                + String.format("%04d", (specCategoryMapper.selectCount(
                new LambdaQueryWrapper<SpecCategory>().eq(SpecCategory::getTenantId, tenantId)) + 1));

        SpecCategory category = new SpecCategory();
        category.setTenantId(tenantId);
        category.setCategoryNo(categoryNo);
        category.setName(req.getName());
        category.setUnit(req.getUnit() != null ? req.getUnit() : "");
        category.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        category.setStatus("active");
        category.setRemark(req.getRemark());
        category.setCreateTime(now);
        category.setUpdateTime(now);

        specCategoryMapper.insert(category);
        log.info("[规格分类创建] 分类名称={}, 编号={}, 租户={}", req.getName(), categoryNo, tenantId);

        return category;
    }

    /**
     * 更新规格分类
     * 对应 JS spec_manage 云函数中 action=updateCategory 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (req.getCategoryId() == null) {
            throw new BusinessException(400, "分类ID不能为空");
        }

        SpecCategory existing = specCategoryMapper.selectOne(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getId, req.getCategoryId())
                        .eq(SpecCategory::getTenantId, tenantId)
        );

        if (existing == null) {
            throw new BusinessException(404, "规格分类不存在");
        }

        // 校验名称唯一性（排除自身）
        if (StringUtils.hasText(req.getName()) && !req.getName().equals(existing.getName())) {
            Long count = specCategoryMapper.selectCount(
                    new LambdaQueryWrapper<SpecCategory>()
                            .eq(SpecCategory::getTenantId, tenantId)
                            .eq(SpecCategory::getName, req.getName())
                            .ne(SpecCategory::getId, req.getCategoryId())
                            .ne(SpecCategory::getStatus, "archived")
            );
            if (count > 0) {
                throw new BusinessException(400, "规格分类【" + req.getName() + "】已存在");
            }
        }

        if (StringUtils.hasText(req.getName())) {
            existing.setName(req.getName());
        }
        if (StringUtils.hasText(req.getUnit())) {
            existing.setUnit(req.getUnit());
        }
        if (req.getSortOrder() != null) {
            existing.setSortOrder(req.getSortOrder());
        }
        if (StringUtils.hasText(req.getStatus())) {
            existing.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            existing.setRemark(req.getRemark());
        }
        existing.setUpdateTime(now);

        specCategoryMapper.updateById(existing);
        log.info("[规格分类更新] 分类ID={}, 名称={}, 租户={}", req.getCategoryId(), existing.getName(), tenantId);
    }

    /**
     * 删除规格分类（逻辑删除，状态改为archived）
     * 对应 JS spec_manage 云函数中 action=deleteCategory 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        SpecCategory existing = specCategoryMapper.selectOne(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getId, id)
                        .eq(SpecCategory::getTenantId, tenantId)
                        .ne(SpecCategory::getStatus, "archived")
        );

        if (existing == null) {
            throw new BusinessException(404, "规格分类不存在或已归档");
        }

        // 同时将该分类下的所有规格值标记为archived
        SpecValue updateValue = new SpecValue();
        updateValue.setStatus("archived");
        updateValue.setUpdateTime(now);
        specValueMapper.update(updateValue,
                new LambdaQueryWrapper<SpecValue>()
                        .eq(SpecValue::getCategoryId, id)
                        .eq(SpecValue::getTenantId, tenantId)
        );

        // 删除该分类的所有商品关联
        productSpecLinkMapper.delete(
                new LambdaQueryWrapper<ProductSpecLink>()
                        .eq(ProductSpecLink::getCategoryId, id)
                        .eq(ProductSpecLink::getTenantId, tenantId)
        );

        existing.setStatus("archived");
        existing.setUpdateTime(now);
        specCategoryMapper.updateById(existing);

        log.info("[规格分类删除] 分类ID={}, 名称={}, 租户={}", id, existing.getName(), tenantId);
    }

    // ==================== 规格值管理 ====================

    /**
     * 新增规格值
     * 对应 JS spec_manage 云函数中 action=createValue 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecValue addValue(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (req.getCategoryId() == null) {
            throw new BusinessException(400, "所属分类ID不能为空");
        }
        if (!StringUtils.hasText(req.getValue())) {
            throw new BusinessException(400, "规格值不能为空");
        }

        // 校验所属分类存在
        SpecCategory category = specCategoryMapper.selectOne(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getId, req.getCategoryId())
                        .eq(SpecCategory::getTenantId, tenantId)
                        .ne(SpecCategory::getStatus, "archived")
        );
        if (category == null) {
            throw new BusinessException(404, "所属规格分类不存在或已归档");
        }

        // 校验规格值在当前分类下唯一
        Long count = specValueMapper.selectCount(
                new LambdaQueryWrapper<SpecValue>()
                        .eq(SpecValue::getTenantId, tenantId)
                        .eq(SpecValue::getCategoryId, req.getCategoryId())
                        .eq(SpecValue::getValue, req.getValue())
                        .ne(SpecValue::getStatus, "archived")
        );
        if (count > 0) {
            throw new BusinessException(400, "该分类下规格值【" + req.getValue() + "】已存在");
        }

        SpecValue specValue = new SpecValue();
        specValue.setTenantId(tenantId);
        specValue.setCategoryId(req.getCategoryId());
        specValue.setCategoryName(category.getName());
        specValue.setValue(req.getValue());
        specValue.setLabel(StringUtils.hasText(req.getLabel()) ? req.getLabel() : req.getValue());
        specValue.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        specValue.setStatus("active");
        specValue.setCreateTime(now);
        specValue.setUpdateTime(now);

        specValueMapper.insert(specValue);
        log.info("[规格值创建] 分类={}, 规格值={}, 租户={}", category.getName(), req.getValue(), tenantId);

        return specValue;
    }

    /**
     * 更新规格值
     * 对应 JS spec_manage 云函数中 action=updateValue 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (req.getValueId() == null) {
            throw new BusinessException(400, "规格值ID不能为空");
        }

        SpecValue existing = specValueMapper.selectOne(
                new LambdaQueryWrapper<SpecValue>()
                        .eq(SpecValue::getId, req.getValueId())
                        .eq(SpecValue::getTenantId, tenantId)
                        .ne(SpecValue::getStatus, "archived")
        );

        if (existing == null) {
            throw new BusinessException(404, "规格值不存在或已归档");
        }

        if (StringUtils.hasText(req.getValue())) {
            existing.setValue(req.getValue());
        }
        if (req.getLabel() != null) {
            existing.setLabel(req.getLabel());
        }
        if (req.getSortOrder() != null) {
            existing.setSortOrder(req.getSortOrder());
        }
        if (StringUtils.hasText(req.getStatus())) {
            existing.setStatus(req.getStatus());
        }
        existing.setUpdateTime(now);

        specValueMapper.updateById(existing);
        log.info("[规格值更新] 规格值ID={}, 值={}, 租户={}", req.getValueId(), existing.getValue(), tenantId);
    }

    /**
     * 删除规格值（逻辑删除）
     * 对应 JS spec_manage 云函数中 action=deleteValue 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteValue(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        SpecValue existing = specValueMapper.selectOne(
                new LambdaQueryWrapper<SpecValue>()
                        .eq(SpecValue::getId, id)
                        .eq(SpecValue::getTenantId, tenantId)
                        .ne(SpecValue::getStatus, "archived")
        );

        if (existing == null) {
            throw new BusinessException(404, "规格值不存在或已归档");
        }

        existing.setStatus("archived");
        existing.setUpdateTime(now);
        specValueMapper.updateById(existing);

        log.info("[规格值删除] 规格值ID={}, 值={}, 租户={}", id, existing.getValue(), tenantId);
    }

    /**
     * 批量新增规格值
     * 对应 JS spec_manage 云函数中 action=batchAddValues 的逻辑
     * 将逗号分隔的 valuesStr 和 labelsStr 解析为多条规格值记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddValues(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (req.getCategoryId() == null) {
            throw new BusinessException(400, "所属分类ID不能为空");
        }
        if (!StringUtils.hasText(req.getValuesStr())) {
            throw new BusinessException(400, "规格值列表不能为空");
        }

        // 校验分类存在
        SpecCategory category = specCategoryMapper.selectOne(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getId, req.getCategoryId())
                        .eq(SpecCategory::getTenantId, tenantId)
                        .ne(SpecCategory::getStatus, "archived")
        );
        if (category == null) {
            throw new BusinessException(404, "所属规格分类不存在或已归档");
        }

        // 解析逗号分隔的字符串
        String[] valuesArr = req.getValuesStr().split(",");
        String[] labelsArr = StringUtils.hasText(req.getLabelsStr())
                ? req.getLabelsStr().split(",") : new String[0];

        int addedCount = 0;
        for (int i = 0; i < valuesArr.length; i++) {
            String val = valuesArr[i].trim();
            if (!StringUtils.hasText(val)) {
                continue;
            }

            // 检查是否已存在
            Long count = specValueMapper.selectCount(
                    new LambdaQueryWrapper<SpecValue>()
                            .eq(SpecValue::getTenantId, tenantId)
                            .eq(SpecValue::getCategoryId, req.getCategoryId())
                            .eq(SpecValue::getValue, val)
                            .ne(SpecValue::getStatus, "archived")
            );
            if (count > 0) {
                log.debug("[批量新增规格值] 跳过重复值: {}", val);
                continue;
            }

            String label = (i < labelsArr.length && StringUtils.hasText(labelsArr[i].trim()))
                    ? labelsArr[i].trim() : val;

            SpecValue specValue = new SpecValue();
            specValue.setTenantId(tenantId);
            specValue.setCategoryId(req.getCategoryId());
            specValue.setCategoryName(category.getName());
            specValue.setValue(val);
            specValue.setLabel(label);
            specValue.setSortOrder(i + 1);
            specValue.setStatus("active");
            specValue.setCreateTime(now);
            specValue.setUpdateTime(now);

            specValueMapper.insert(specValue);
            addedCount++;
        }

        log.info("[批量新增规格值] 分类={}, 新增数量={}, 租户={}", category.getName(), addedCount, tenantId);
    }

    // ==================== 商品规格关联 ====================

    /**
     * 新增商品规格关联
     * 对应 JS spec_manage 云函数中 action=linkProduct 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductSpecLink addLink(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (!StringUtils.hasText(req.getProductName())) {
            throw new BusinessException(400, "成品名称不能为空");
        }
        if (req.getCategoryId() == null) {
            throw new BusinessException(400, "规格分类ID不能为空");
        }

        // 校验分类存在
        SpecCategory category = specCategoryMapper.selectOne(
                new LambdaQueryWrapper<SpecCategory>()
                        .eq(SpecCategory::getId, req.getCategoryId())
                        .eq(SpecCategory::getTenantId, tenantId)
        );
        if (category == null) {
            throw new BusinessException(404, "规格分类不存在");
        }

        // 校验是否已存在相同关联
        Long count = productSpecLinkMapper.selectCount(
                new LambdaQueryWrapper<ProductSpecLink>()
                        .eq(ProductSpecLink::getTenantId, tenantId)
                        .eq(ProductSpecLink::getProductName, req.getProductName())
                        .eq(ProductSpecLink::getCategoryId, req.getCategoryId())
        );
        if (count > 0) {
            throw new BusinessException(400,
                    "商品【" + req.getProductName() + "】与分类【" + category.getName() + "】的关联已存在");
        }

        ProductSpecLink link = new ProductSpecLink();
        link.setTenantId(tenantId);
        link.setProductName(req.getProductName());
        link.setCategoryId(req.getCategoryId());
        link.setCategoryName(category.getName());
        link.setIsRequired(req.getIsRequired() != null ? req.getIsRequired() : false);
        link.setCreateTime(now);
        link.setUpdateTime(now);

        productSpecLinkMapper.insert(link);
        log.info("[商品规格关联] 商品={}, 分类={}, 租户={}", req.getProductName(), category.getName(), tenantId);

        return link;
    }

    /**
     * 删除商品规格关联
     * 对应 JS spec_manage 云函数中 action=unlinkProduct 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLink(Long id) {
        String tenantId = LoginUserContext.getTenantId();

        ProductSpecLink existing = productSpecLinkMapper.selectOne(
                new LambdaQueryWrapper<ProductSpecLink>()
                        .eq(ProductSpecLink::getId, id)
                        .eq(ProductSpecLink::getTenantId, tenantId)
        );

        if (existing == null) {
            throw new BusinessException(404, "商品规格关联不存在");
        }

        productSpecLinkMapper.deleteById(id);
        log.info("[商品规格关联删除] 关联ID={}, 商品={}, 租户={}", id, existing.getProductName(), tenantId);
    }

    // ==================== 商品种类管理 ====================

    /**
     * 分页查询商品种类列表
     * 对应 JS spec_manage 云函数中 action=listProducts 的逻辑
     */
    @Override
    public PageResponse<ProductCategory> listProducts(String keyword, int page, int pageSize) {
        String tenantId = LoginUserContext.getTenantId();

        LambdaQueryWrapper<ProductCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductCategory::getTenantId, tenantId);
        queryWrapper.ne(ProductCategory::getStatus, "archived");

        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(ProductCategory::getName, keyword);
        }

        queryWrapper.orderByDesc(ProductCategory::getCreateTime);

        Page<ProductCategory> pageResult = productCategoryMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        log.debug("[商品种类列表] 租户={}, 关键词={}, 总数={}", tenantId, keyword, pageResult.getTotal());

        return PageResponse.success(pageResult.getRecords(), pageResult.getTotal(), page, pageSize);
    }

    /**
     * 新增商品种类
     * 对应 JS spec_manage 云函数中 action=createProduct 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductCategory addProduct(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (!StringUtils.hasText(req.getName())) {
            throw new BusinessException(400, "商品名称不能为空");
        }

        // 校验商品名称唯一性
        Long count = productCategoryMapper.selectCount(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getTenantId, tenantId)
                        .eq(ProductCategory::getName, req.getName())
                        .ne(ProductCategory::getStatus, "archived")
        );
        if (count > 0) {
            throw new BusinessException(400, "商品【" + req.getName() + "】已存在");
        }

        ProductCategory product = new ProductCategory();
        product.setTenantId(tenantId);
        product.setName(req.getName());
        product.setDefaultUnit(req.getUnit() != null ? req.getUnit() : "斤");
        product.setStatus("active");
        product.setCreateTime(now);
        product.setUpdateTime(now);

        productCategoryMapper.insert(product);
        log.info("[商品种类创建] 名称={}, 租户={}", req.getName(), tenantId);

        return product;
    }

    /**
     * 更新商品种类
     * 对应 JS spec_manage 云函数中 action=updateProduct 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(SpecManageRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        if (req.getCategoryId() == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        ProductCategory existing = productCategoryMapper.selectOne(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getId, req.getCategoryId())
                        .eq(ProductCategory::getTenantId, tenantId)
                        .ne(ProductCategory::getStatus, "archived")
        );

        if (existing == null) {
            throw new BusinessException(404, "商品不存在或已归档");
        }

        // 校验名称唯一性（排除自身）
        if (StringUtils.hasText(req.getName()) && !req.getName().equals(existing.getName())) {
            Long count = productCategoryMapper.selectCount(
                    new LambdaQueryWrapper<ProductCategory>()
                            .eq(ProductCategory::getTenantId, tenantId)
                            .eq(ProductCategory::getName, req.getName())
                            .ne(ProductCategory::getId, req.getCategoryId())
                            .ne(ProductCategory::getStatus, "archived")
            );
            if (count > 0) {
                throw new BusinessException(400, "商品【" + req.getName() + "】已存在");
            }
            existing.setName(req.getName());
        }

        if (StringUtils.hasText(req.getUnit())) {
            existing.setDefaultUnit(req.getUnit());
        }
        existing.setUpdateTime(now);

        productCategoryMapper.updateById(existing);
        log.info("[商品种类更新] 商品ID={}, 名称={}, 租户={}", req.getCategoryId(), existing.getName(), tenantId);
    }

    /**
     * 删除商品种类（逻辑删除，状态改为archived）
     * 对应 JS spec_manage 云函数中 action=deleteProduct 的逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        ProductCategory existing = productCategoryMapper.selectOne(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getId, id)
                        .eq(ProductCategory::getTenantId, tenantId)
                        .ne(ProductCategory::getStatus, "archived")
        );

        if (existing == null) {
            throw new BusinessException(404, "商品不存在或已归档");
        }

        // 删除该商品的所有规格关联
        productSpecLinkMapper.delete(
                new LambdaQueryWrapper<ProductSpecLink>()
                        .eq(ProductSpecLink::getProductName, existing.getName())
                        .eq(ProductSpecLink::getTenantId, tenantId)
        );

        existing.setStatus("archived");
        existing.setUpdateTime(now);
        productCategoryMapper.updateById(existing);

        log.info("[商品种类删除] 商品ID={}, 名称={}, 租户={}", id, existing.getName(), tenantId);
    }
}
