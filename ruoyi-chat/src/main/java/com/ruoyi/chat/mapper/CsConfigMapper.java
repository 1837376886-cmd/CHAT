package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.CsConfig;

/**
 * 客服配置Mapper接口
 *
 * @author ruoyi
 */
public interface CsConfigMapper extends BaseMapper<CsConfig> {

    /**
     * 根据用户ID查询客服配置
     */
    CsConfig selectByUserId(Long userId);
}
