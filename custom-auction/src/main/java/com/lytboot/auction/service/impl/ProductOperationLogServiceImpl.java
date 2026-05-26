package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.ProductOperationLog;
import com.lytboot.auction.mapper.ProductOperationLogMapper;
import com.lytboot.auction.service.IProductOperationLogService;
import com.lytboot.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品操作记录Service实现
 */
@Service
public class ProductOperationLogServiceImpl extends ServiceImpl<ProductOperationLogMapper, ProductOperationLog> implements IProductOperationLogService {

    @Autowired
    private ProductOperationLogMapper operationLogMapper;

    @Override
    public List<ProductOperationLog> selectByProductId(Long productId) {
        return operationLogMapper.selectList(
            new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getProductId, productId)
                .orderByDesc(ProductOperationLog::getCreateTime)
        );
    }

    @Override
    public void addLog(Long productId, String operationType, Long activityId, String oldInfo, String newInfo) {
        ProductOperationLog log = new ProductOperationLog();
        log.setProductId(productId);
        log.setOperationType(operationType);
        log.setActivityId(activityId);
        log.setOldInfo(oldInfo);
        log.setNewInfo(newInfo);
        try {
            log.setCreateBy(SecurityUtils.getUserId());
        } catch (Exception ignored) {
            // 非登录场景（如定时任务）可能获取不到用户ID
        }
        operationLogMapper.insert(log);
    }

    @Override
    public void addLog(Long productId, String operationType, Long activityId) {
        addLog(productId, operationType, activityId, null, null);
    }

    @Override
    public void addLog(Long productId, String operationType) {
        addLog(productId, operationType, null, null, null);
    }
}
