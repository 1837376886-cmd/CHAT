package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.chat.domain.entity.CsVisitorTag;
import com.ruoyi.chat.mapper.CsVisitorTagMapper;
import com.ruoyi.chat.service.ICsVisitorTagService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 访客标签Service实现
 *
 * @author ruoyi
 */
@Service
public class CsVisitorTagServiceImpl extends ServiceImpl<CsVisitorTagMapper, CsVisitorTag> implements ICsVisitorTagService {

    @Override
    public List<CsVisitorTag> selectByVisitorId(Long visitorId) {
        return baseMapper.selectByVisitorId(visitorId);
    }
}
