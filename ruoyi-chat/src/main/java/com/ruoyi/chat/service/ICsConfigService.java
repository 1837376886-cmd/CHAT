package com.ruoyi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.chat.domain.entity.CsConfig;

/**
 * 客服配置Service接口
 *
 * @author ruoyi
 */
public interface ICsConfigService extends IService<CsConfig> {

    /**
     * 根据用户ID查询客服配置
     */
    CsConfig selectByUserId(Long userId);

    /**
     * 获取默认配置（不存在则创建）
     */
    CsConfig getOrCreateDefault(Long userId);
}
