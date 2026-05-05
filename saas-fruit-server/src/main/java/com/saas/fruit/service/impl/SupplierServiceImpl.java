package com.saas.fruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.fruit.common.BusinessException;
import com.saas.fruit.common.PageResponse;
import com.saas.fruit.dto.request.SupplierCreateRequest;
import com.saas.fruit.dto.request.SupplierUpdateRequest;
import com.saas.fruit.entity.Supplier;
import com.saas.fruit.mapper.SupplierMapper;
import com.saas.fruit.security.LoginUserContext;
import com.saas.fruit.service.SupplierService;
import com.saas.fruit.utils.BatchNoGenerator;
import com.saas.fruit.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 供应商管理服务实现类
 * 负责供应商的增删改查、搜索筛选、分页列表、状态切换
 * 所有操作均在当前租户上下文下进行，确保多租户数据隔离
 */
@Slf4j
@Service
public class SupplierServiceImpl implements SupplierService {

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private BatchNoGenerator batchNoGenerator;

    /**
     * 分页查询供应商列表
     * 支持关键词模糊搜索 name/phone/contactPerson/supplierNo，按状态筛选
     * 排除已软删除（status=deleted）的记录
     */
    @Override
    public PageResponse<Supplier> list(String keyword, String status, int page, int pageSize) {

        // 获取当前租户ID，确保数据隔离
        String tenantId = LoginUserContext.getTenantId();

        // 构建查询条件
        LambdaQueryWrapper<Supplier> queryWrapper = new LambdaQueryWrapper<>();

        // 多租户数据隔离
        queryWrapper.eq(Supplier::getTenantId, tenantId);

        // 排除已软删除的供应商
        queryWrapper.ne(Supplier::getStatus, "deleted");

        // 关键词模糊搜索：名称/电话/联系人/供应商编号
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Supplier::getName, keyword)
                    .or()
                    .like(Supplier::getPhone, keyword)
                    .or()
                    .like(Supplier::getContactPerson, keyword)
                    .or()
                    .like(Supplier::getSupplierNo, keyword));
        }

        // 状态筛选：active/inactive/blacklist
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Supplier::getStatus, status);
        }

        // 按创建时间倒序排列
        queryWrapper.orderByDesc(Supplier::getCreateTime);

        // 执行分页查询
        Page<Supplier> pageResult = supplierMapper.selectPage(
                new Page<>(page + 1, pageSize),
                queryWrapper
        );

        log.debug("[供应商列表] 租户={}, 关键词={}, 状态={}, 总数={}, 当前页={}",
                tenantId, keyword, status, pageResult.getTotal(), page);

        return PageResponse.success(
                pageResult.getRecords(),
                pageResult.getTotal(),
                page,
                pageSize
        );
    }

    /**
     * 创建新供应商
     * 生成供应商编号（GYS + 年份 + 4位序号），校验名称唯一性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Supplier create(SupplierCreateRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验供应商名称唯一性（同租户下不可重复） ==========
        Long nameCount = supplierMapper.selectCount(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getTenantId, tenantId)
                        .eq(Supplier::getName, req.getName())
                        .ne(Supplier::getStatus, "deleted")
        );
        if (nameCount > 0) {
            throw new BusinessException(400, "供应商名称【" + req.getName() + "】已存在，请更换名称");
        }

        // ========== 生成供应商编号：GYS + 年份 + 4位序号 ==========
        // 序号基于该年份已创建的供应商数 + 1
        String year = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        long yearCount = supplierMapper.selectCount(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getTenantId, tenantId)
                        .likeRight(Supplier::getSupplierNo, "GYS" + year)
        );
        String supplierNo = batchNoGenerator.genSupplierNo(yearCount + 1);

        // ========== 构建并插入供应商实体 ==========
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setSupplierNo(supplierNo);
        supplier.setName(req.getName());
        supplier.setPhone(req.getPhone());
        supplier.setContactPerson(req.getContactPerson());
        supplier.setAddress(req.getAddress());
        supplier.setOrigin(req.getOrigin());
        supplier.setSupplierType(req.getSupplierType() != null ? req.getSupplierType() : "wholesaler");
        // 新供应商初始财务数据
        supplier.setTotalDebtFen(0);
        supplier.setTotalPurchaseFen(0);
        supplier.setTotalPaidFen(0);
        supplier.setPurchaseCount(0);
        supplier.setStatus("active");
        supplier.setRemark(req.getRemark());
        supplier.setCreateTime(now);
        supplier.setUpdateTime(now);

        supplierMapper.insert(supplier);
        log.info("[供应商创建] 供应商编号={}, 名称={}, 租户={}", supplierNo, req.getName(), tenantId);

        return supplier;
    }

    /**
     * 更新供应商信息
     * 校验供应商存在后更新名称、电话、联系人、地址、产地等基本信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierUpdateRequest req) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验供应商是否存在且属于当前租户 ==========
        Supplier existing = supplierMapper.selectOne(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getId, req.getSupplierId())
                        .eq(Supplier::getTenantId, tenantId)
                        .ne(Supplier::getStatus, "deleted")
        );

        if (existing == null) {
            throw new BusinessException(404, "供应商不存在或已被删除");
        }

        // ========== 校验名称唯一性（排除自身） ==========
        if (StringUtils.hasText(req.getName()) && !req.getName().equals(existing.getName())) {
            Long nameCount = supplierMapper.selectCount(
                    new LambdaQueryWrapper<Supplier>()
                            .eq(Supplier::getTenantId, tenantId)
                            .eq(Supplier::getName, req.getName())
                            .ne(Supplier::getStatus, "deleted")
                            .ne(Supplier::getId, req.getSupplierId())
            );
            if (nameCount > 0) {
                throw new BusinessException(400, "供应商名称【" + req.getName() + "】已被其他供应商使用");
            }
        }

        // ========== 更新供应商信息 ==========
        existing.setName(req.getName());
        existing.setPhone(req.getPhone());
        existing.setContactPerson(req.getContactPerson());
        existing.setAddress(req.getAddress());
        existing.setOrigin(req.getOrigin());
        existing.setSupplierType(req.getSupplierType());
        existing.setRemark(req.getRemark());
        existing.setUpdateTime(now);

        supplierMapper.updateById(existing);
        log.info("[供应商更新] 供应商ID={}, 名称={}, 租户={}", req.getSupplierId(), req.getName(), tenantId);
    }

    /**
     * 删除供应商（软删除）
     * 将供应商状态标记为deleted
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验供应商是否存在且属于当前租户 ==========
        Supplier supplier = supplierMapper.selectOne(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getId, id)
                        .eq(Supplier::getTenantId, tenantId)
                        .ne(Supplier::getStatus, "deleted")
        );

        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在或已被删除");
        }

        // ========== 执行软删除 ==========
        supplier.setStatus("deleted");
        supplier.setUpdateTime(now);

        supplierMapper.updateById(supplier);
        log.info("[供应商删除] 供应商ID={}, 名称={}, 租户={}", id, supplier.getName(), tenantId);
    }

    /**
     * 查询供应商详情
     */
    @Override
    public Supplier detail(Long id) {
        String tenantId = LoginUserContext.getTenantId();

        // 查询供应商，校验存在且属于当前租户
        Supplier supplier = supplierMapper.selectOne(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getId, id)
                        .eq(Supplier::getTenantId, tenantId)
                        .ne(Supplier::getStatus, "deleted")
        );

        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在或已被删除");
        }

        log.debug("[供应商详情] 供应商ID={}, 租户={}", id, tenantId);
        return supplier;
    }

    /**
     * 切换供应商启用/停用状态
     * active <-> inactive 互相切换
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        String tenantId = LoginUserContext.getTenantId();
        long now = DateUtil.now();

        // ========== 校验供应商是否存在且属于当前租户 ==========
        Supplier supplier = supplierMapper.selectOne(
                new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getId, id)
                        .eq(Supplier::getTenantId, tenantId)
                        .ne(Supplier::getStatus, "deleted")
        );

        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在或已被删除");
        }

        // ========== active <-> inactive 切换 ==========
        String currentStatus = supplier.getStatus();
        String newStatus;
        if ("active".equals(currentStatus)) {
            newStatus = "inactive";
        } else if ("inactive".equals(currentStatus)) {
            newStatus = "active";
        } else {
            throw new BusinessException(400, "当前状态【" + currentStatus + "】不支持切换，仅active和inactive可互相切换");
        }

        supplier.setStatus(newStatus);
        supplier.setUpdateTime(now);

        supplierMapper.updateById(supplier);
        log.info("[供应商状态切换] 供应商ID={}, 名称={}, {} -> {}, 租户={}",
                id, supplier.getName(), currentStatus, newStatus, tenantId);
    }
}
