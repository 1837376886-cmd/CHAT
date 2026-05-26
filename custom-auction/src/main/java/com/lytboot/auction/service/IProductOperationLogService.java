package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.ProductOperationLog;

import java.util.List;

/**
 * 商品操作记录Service接口
 */
public interface IProductOperationLogService extends IService<ProductOperationLog> {

    /**
     * 根据商品ID查询操作记录
     */
    List<ProductOperationLog> selectByProductId(Long productId);

    /**
     * 添加操作记录
     *
     * @param productId      商品ID
     * @param operationType  操作类型编码
     * @param activityId     活动ID（可选）
     * @param oldInfo        旧信息JSON（可选）
     * @param newInfo        新信息JSON（可选）
     */
    void addLog(Long productId, String operationType, Long activityId, String oldInfo, String newInfo);

    /**
     * 添加操作记录（简化版）
     *
     * @param productId      商品ID
     * @param operationType  操作类型编码
     * @param activityId     活动ID（可选）
     */
    void addLog(Long productId, String operationType, Long activityId);

    /**
     * 添加操作记录（最简版）
     *
     * @param productId      商品ID
     * @param operationType  操作类型编码
     */
    void addLog(Long productId, String operationType);
}
