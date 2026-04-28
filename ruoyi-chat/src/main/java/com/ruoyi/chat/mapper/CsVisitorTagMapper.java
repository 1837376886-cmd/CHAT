package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.CsVisitorTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 访客标签Mapper接口
 *
 * @author ruoyi
 */
public interface CsVisitorTagMapper extends BaseMapper<CsVisitorTag> {

    /**
     * 查询访客的所有标签
     */
    List<CsVisitorTag> selectByVisitorId(@Param("visitorId") Long visitorId);
}
