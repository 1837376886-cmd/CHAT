package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.chat.domain.entity.CsConfig;
import com.ruoyi.chat.mapper.CsConfigMapper;
import com.ruoyi.chat.service.ICsConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 客服配置Service实现
 *
 * @author ruoyi
 */
@Service
public class CsConfigServiceImpl extends ServiceImpl<CsConfigMapper, CsConfig> implements ICsConfigService {

    @Autowired
    private CsConfigMapper csConfigMapper;

    @Override
    public CsConfig selectByUserId(Long userId) {
        return csConfigMapper.selectByUserId(userId);
    }

    @Override
    public CsConfig getOrCreateDefault(Long userId) {
        CsConfig config = csConfigMapper.selectByUserId(userId);
        if (config == null) {
            config = new CsConfig();
            config.setUserId(userId);
            config.setMaxSessions(5);
            config.setStatus(1);
            csConfigMapper.insert(config);
        }
        return config;
    }
}
