package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.ChatVisitor;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 根据IP和绑定状态查询访客列表
     */
    List<ChatVisitor> selectByIpAndUnbound(@Param("ip") String ip, @Param("days") int days);

    /**
     * 绑定用户ID到访客记录
     */
    int bindUserIdByIp(@Param("userId") Long userId, @Param("ip") String ip, @Param("days") int days);
}
