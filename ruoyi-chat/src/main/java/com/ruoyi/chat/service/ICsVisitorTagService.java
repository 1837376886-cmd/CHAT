package com.ruoyi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.chat.domain.entity.CsVisitorTag;

import java.util.List;

/**
 * 访客标签Service接口
 *
 * @author ruoyi
 */
public interface ICsVisitorTagService extends IService<CsVisitorTag> {

    /**
     * 查询访客的所有标签
     */
    List<CsVisitorTag> selectByVisitorId(Long visitorId);
}
