package com.ruoyi.chat.netty.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.domain.entity.CsConfig;
import com.ruoyi.chat.domain.entity.CsMessage;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.protocol.ChatMessage;
import com.ruoyi.chat.protocol.MessageType;
import com.ruoyi.chat.netty.manager.ConnectionManager;
import com.ruoyi.chat.netty.session.UserSession;
import com.ruoyi.chat.service.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysUserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * WebSocket消息处理器
 *
 * @author ruoyi
 */
@Component
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private IChatVisitorService chatVisitorService;

    @Autowired
    private ICsSessionService csSessionService;

    @Autowired
    private ICsMessageService csMessageService;

    @Autowired
    private CustomerServiceRedisManager redisManager;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ICsConfigService csConfigService;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("WebSocket连接建立: {}", ctx.channel().id());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("WebSocket连接断开: {}", ctx.channel().id());

        // 获取用户会话信息
        UserSession userSession = connectionManager.getUserSessionByChannel(ctx.channel().id());
        if (userSession != null) {
            Long userId = userSession.getUserId();
            SysUser user = sysUserService.selectUserById(userId);
            if (user != null && Integer.valueOf(1).equals(user.getIsCustomerService())) {
                // 客服异常断线不立即标记为offline，由定时任务根据心跳超时处理（保留会话5分钟）
                logger.info("客服WS断开: {} ({}), 不修改Redis在线状态，由心跳检查任务处理", user.getNickName(), userId);
            }
        }

        // 移除连接
        connectionManager.removeConnection(ctx.channel().id());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            handleTextFrame(ctx, (TextWebSocketFrame) frame);
        } else {
            logger.warn("不支持的WebSocket帧类型: {}", frame.getClass().getSimpleName());
        }
    }

    /**
     * 处理文本消息帧
     */
    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            String text = frame.text();
            logger.debug("收到WebSocket消息: {}", text);
            
            // 解析消息
            ChatMessage message = JSON.parseObject(text, ChatMessage.class);
            if (message == null) {
                logger.warn("无法解析消息: {}", text);
                return;
            }
            
            // 处理不同类型的消息
            switch (message.getType()) {
                case AUTH:
                    handleAuthMessage(ctx, message);
                    break;
                case GUEST_AUTH:
                    handleGuestAuthMessage(ctx, message);
                    break;
                case CS_CHAT:
                    handleCsChatMessage(ctx, message);
                    break;
                case HEARTBEAT:
                    handleHeartbeat(ctx, message);
                    break;
                default:
                    logger.warn("未知的消息类型: {}", message.getType());
            }
        } catch (Exception e) {
            logger.error("处理WebSocket消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理认证消息
     */
    private void handleAuthMessage(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            Long userId = message.getFromUserId();
            String userNickname = message.getFromUserNickname();
            String userAvatar = message.getFromUserAvatar();
            
            if (userId == null || userNickname == null) {
                logger.warn("认证消息缺少必要信息: userId={}, nickname={}", userId, userNickname);
                sendErrorResponse(ctx, "认证失败：缺少用户信息");
                return;
            }
            
            // 添加连接
            connectionManager.addConnection(userId, ctx.channel(), userNickname, userAvatar);
            
            // 发送认证成功响应
            ChatMessage authResponse = new ChatMessage();
            authResponse.setType(MessageType.AUTH_SUCCESS);
            authResponse.setContent("认证成功");
            authResponse.setTimestamp(new Date());
            
            ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(authResponse)));
            
            // 客服自动上线
            SysUser user = sysUserService.selectUserById(userId);
            if (user != null && Integer.valueOf(1).equals(user.getIsCustomerService())) {
                int maxSessions = 5;
                try {
                    CsConfig config = csConfigService.getOrCreateDefault(userId);
                    if (config != null && config.getMaxSessions() != null) {
                        maxSessions = config.getMaxSessions();
                    }
                } catch (Exception e) {
                    logger.warn("获取客服配置失败，使用默认值: {}", e.getMessage());
                }
                redisManager.setCsStatus(userId, "online", maxSessions);
                logger.info("客服上线: {} ({})", userNickname, userId);
            }

            logger.info("用户认证成功: {} ({})", userNickname, userId);
        } catch (Exception e) {
            logger.error("处理认证消息失败: {}", e.getMessage(), e);
            sendErrorResponse(ctx, "认证失败");
        }
    }

    /**
     * 处理访客认证消息
     */
    private void handleGuestAuthMessage(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            String visitorToken = message.getVisitorToken();
            if (visitorToken == null || visitorToken.isEmpty()) {
                logger.warn("访客认证消息缺少 visitorToken");
                sendErrorResponse(ctx, "认证失败：缺少访客Token");
                return;
            }

            ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
            if (visitor == null) {
                logger.warn("访客Token无效: {}", visitorToken);
                sendErrorResponse(ctx, "认证失败：访客不存在");
                return;
            }

            connectionManager.addVisitorConnection(visitorToken, ctx.channel(), visitor.getNickname());

            ChatMessage authResponse = new ChatMessage();
            authResponse.setType(MessageType.AUTH_SUCCESS);
            authResponse.setContent("访客认证成功");
            authResponse.setTimestamp(new Date());

            ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(authResponse)));
            logger.info("访客认证成功: {} ({})", visitor.getNickname(), visitorToken);
        } catch (Exception e) {
            logger.error("处理访客认证消息失败: {}", e.getMessage(), e);
            sendErrorResponse(ctx, "访客认证失败");
        }
    }

    /**
     * 处理客服聊天消息
     */
    private void handleCsChatMessage(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            Long sessionId = null;
            if (message.getSessionId() != null) {
                try {
                    sessionId = Long.valueOf(message.getSessionId());
                } catch (NumberFormatException e) {
                    logger.warn("无效的客服会话ID: {}", message.getSessionId());
                    sendErrorResponse(ctx, "无效的会话ID");
                    return;
                }
            }

            if (sessionId == null) {
                logger.warn("CS_CHAT缺少会话ID, message={}", message.getMessageId());
                sendErrorResponse(ctx, "缺少会话ID");
                return;
            }

            CsSession session = csSessionService.getById(sessionId);
            if (session == null) {
                logger.warn("CS_CHAT会话不存在, sessionId={}", sessionId);
                sendErrorResponse(ctx, "会话不存在或已结束");
                return;
            }
            if (!Integer.valueOf(CsSession.Status.ACTIVE).equals(session.getStatus())) {
                logger.warn("CS_CHAT会话状态非ACTIVE, sessionId={}, status={}", sessionId, session.getStatus());
                sendErrorResponse(ctx, "会话不存在或已结束");
                return;
            }

            // 判断发送者身份
            UserSession userSession = connectionManager.getUserSessionByChannel(ctx.channel().id());
            boolean isCs = false;
            Long senderUserId = null;
            String senderNickname = null;

            if (userSession != null && userSession.getUserId() != null) {
                // 已登录用户（客服）
                senderUserId = userSession.getUserId();
                senderNickname = userSession.getUserNickname();
                if (session.getCsUserId().equals(senderUserId)) {
                    isCs = true;
                } else {
                    logger.warn("CS_CHAT客服无权发送, sessionId={}, senderUserId={}", sessionId, senderUserId);
                    sendErrorResponse(ctx, "无权发送消息到此会话");
                    return;
                }
            } else {
                // 访客
                String visitorToken = connectionManager.getVisitorTokenByChannel(ctx.channel().id());
                logger.info("CS_CHAT访客发送, sessionId={}, visitorToken={}", sessionId, visitorToken);
                ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
                if (visitor == null) {
                    logger.warn("CS_CHAT访客不存在, visitorToken={}", visitorToken);
                    sendErrorResponse(ctx, "无权发送消息到此会话");
                    return;
                }
                if (!visitor.getId().equals(session.getVisitorId())) {
                    logger.warn("CS_CHAT访客ID不匹配, visitorId={}, sessionVisitorId={}", visitor.getId(), session.getVisitorId());
                    sendErrorResponse(ctx, "无权发送消息到此会话");
                    return;
                }
                senderNickname = visitor.getNickname();
            }

            // 保存消息
            int fromType = isCs ? CsMessage.FromType.CS : CsMessage.FromType.VISITOR;
            csMessageService.sendMessage(sessionId, fromType, senderUserId, message.getContent());
            logger.info("CS_CHAT消息已保存, sessionId={}, fromType={}, isCs={}", sessionId, fromType, isCs);

            // 构造推送消息
            ChatMessage pushMsg = new ChatMessage();
            pushMsg.setType(MessageType.CS_CHAT);
            pushMsg.setSessionId(String.valueOf(sessionId));
            pushMsg.setContent(message.getContent());
            pushMsg.setFromUserId(senderUserId);
            pushMsg.setFromUserNickname(senderNickname);
            pushMsg.setTimestamp(new Date());

            String pushJson = JSON.toJSONString(pushMsg);

            // 推送给接收方
            if (isCs) {
                // 客服发给访客，推送给访客
                ChatVisitor visitor = chatVisitorService.getById(session.getVisitorId());
                if (visitor != null) {
                    boolean sent = connectionManager.sendMessageToVisitor(visitor.getVisitorToken(),
                            new TextWebSocketFrame(pushJson));
                    logger.info("CS_CHAT客服->访客推送结果, sent={}", sent);
                }
            } else {
                // 访客发给客服，推送给客服
                boolean sent = connectionManager.sendMessageToUser(session.getCsUserId(),
                        new TextWebSocketFrame(pushJson));
                logger.info("CS_CHAT访客->客服推送结果, csUserId={}, sent={}", session.getCsUserId(), sent);
            }

            // 发送确认
            ChatMessage ack = new ChatMessage();
            ack.setType(MessageType.AUTH_SUCCESS);
            ack.setMessageId(message.getMessageId());
            ack.setTimestamp(new Date());
            ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(ack)));

        } catch (Exception e) {
            logger.error("处理客服消息失败: {}", e.getMessage(), e);
            sendErrorResponse(ctx, "消息发送失败");
        }
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, ChatMessage message) {
        // 更新客服心跳时间（如果已认证）
        UserSession userSession = connectionManager.getUserSessionByChannel(ctx.channel().id());
        if (userSession != null && userSession.getUserId() != null) {
            redisManager.updateHeartbeat(userSession.getUserId());
        }

        // 发送心跳响应
        ChatMessage heartbeatResponse = new ChatMessage();
        heartbeatResponse.setType(MessageType.HEARTBEAT_RESPONSE);
        heartbeatResponse.setTimestamp(new Date());

        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(heartbeatResponse)));
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String errorMessage) {
        ChatMessage errorResponse = new ChatMessage();
        errorResponse.setType(MessageType.ERROR);
        errorResponse.setContent(errorMessage);
        errorResponse.setTimestamp(new Date());
        
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(errorResponse)));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocket处理异常: {}", cause.getMessage(), cause);
        
        // 移除连接
        connectionManager.removeConnection(ctx.channel().id());
        
        // 关闭连接
        ctx.close();
    }
}