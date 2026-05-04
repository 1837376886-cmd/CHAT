package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.ChatVisitor;
import org.apache.ibatis.annotations.Param;


/**
 * 访客信息Mapper接口
 *
 * @author ruoyi
 */
public interface ChatVisitorMapper extends BaseMapper<ChatVisitor> {

    /**
     * 根据访客Token查询
     */
    ChatVisitor selectByVisitorToken(@Param("visitorToken") String visitorToken);

    /**
     * 根据IP查询最近活跃的访客
     */
    ChatVisitor selectRecentByIp(@Param("ip") String ip, @Param("days") int days);
}
