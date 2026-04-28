package com.ruoyi.chat.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.domain.entity.CsConfig;
import com.ruoyi.chat.domain.entity.CsMessage;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.protocol.ChatMessage;
import com.ruoyi.chat.protocol.MessageType;
import com.ruoyi.chat.service.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@ChannelHandler.Sharable
public class ChatChannelHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger logger = LoggerFactory.getLogger(ChatChannelHandler.class);

    private static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final AttributeKey<Boolean> AUTH_KEY = AttributeKey.valueOf("authenticated");
    private static final AttributeKey<String> VISITOR_TOKEN_KEY = AttributeKey.valueOf("visitorToken");

    @Autowired
    private ChatConnectionManager connectionManager;

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private IChatSessionService chatSessionService;

    @Autowired
    private com.ruoyi.system.service.ISysUserService sysUserService;

    @Autowired
    private IChatVisitorService chatVisitorService;

    @Autowired
    private ICsSessionService csSessionService;

    @Autowired
    private ICsMessageService csMessageService;

    @Autowired
    private CustomerServiceRedisManager redisManager;

    @Autowired
    private ICsConfigService csConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final java.util.Map<String, io.netty.channel.Channel> visitorChannels = new java.util.concurrent.ConcurrentHashMap<>();

    public void sendMessageToVisitor(String visitorToken, ChatMessage message) {
        io.netty.channel.Channel ch = visitorChannels.get(visitorToken);
        if (ch != null && ch.isActive()) {
            sendMessage(ch, message);
        }
    }

    public void sendMessageToUser(Long userId, ChatMessage message) {
        io.netty.channel.Channel ch = connectionManager.getUserChannel(userId);
        if (ch != null && ch.isActive()) {
            sendMessage(ch, message);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("WebSocket连接建立：{}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            SysUser user = sysUserService.selectUserById(userId);
            if (user != null && Integer.valueOf(1).equals(user.getIsCustomerService())) {
                logger.info("客服WS断开: {} ({}), 不修改Redis在线状态", user.getNickName(), userId);
            }
            connectionManager.removeConnection(ctx.channel());
            broadcastUserStatus(userId, MessageType.USER_OFFLINE);
        }
        String visitorToken = ctx.channel().attr(VISITOR_TOKEN_KEY).get();
        if (visitorToken != null) {
            visitorChannels.remove(visitorToken);
            logger.info("访客断开：{}", visitorToken);
        }
        logger.info("WebSocket连接断开：{}", ctx.channel().id().asShortText());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        try {
            String text = frame.text();
            logger.debug("收到WebSocket消息：{}", text);

            ChatMessage message = objectMapper.readValue(text, ChatMessage.class);
            handleMessage(ctx, message);
        } catch (Exception e) {
            logger.error("处理WebSocket消息异常", e);
            sendErrorMessage(ctx, "消息格式错误");
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                logger.warn("连接读超时，关闭连接：{}", ctx.channel().id().asShortText());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocket连接异常：{}", ctx.channel().id().asShortText(), cause);
        ctx.close();
    }

    private void handleMessage(ChannelHandlerContext ctx, ChatMessage message) {
        MessageType type = message.getType();
        
        switch (type) {
            case AUTH:
                handleAuth(ctx, message);
                break;
            case GUEST_AUTH:
                handleGuestAuth(ctx, message);
                break;
            case HEARTBEAT:
                handleHeartbeat(ctx, message);
                break;
            case PRIVATE_CHAT:
                handlePrivateChat(ctx, message);
                break;
            case GROUP_CHAT:
                handleGroupChat(ctx, message);
                break;
            case CS_CHAT:
                handleCsChat(ctx, message);
                break;
            case MESSAGE_READ:
                handleMessageRead(ctx, message);
                break;
            default:
                logger.warn("未知消息类型：{}", type);
                break;
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            Long userId = message.getFromUserId();
            logger.info("收到认证请求，userId: {}", userId);
            
            if (userId != null && userId > 0) {
                ctx.channel().attr(USER_ID_KEY).set(userId);
                ctx.channel().attr(AUTH_KEY).set(true);
                connectionManager.addConnection(userId, ctx.channel());
                logger.info("用户已加入连接管理, userId={}, 当前在线数={}", userId, connectionManager.getOnlineUserCount());

                ChatMessage response = new ChatMessage(MessageType.AUTH_SUCCESS);
                response.setMessageId(UUID.randomUUID().toString());
                sendMessage(ctx, response);

                // 客服WS认证成功，只记录日志，不自动修改上下线状态（由HTTP接口控制）
                SysUser user = sysUserService.selectUserById(userId);
                if (user != null && Integer.valueOf(1).equals(user.getIsCustomerService())) {
                    logger.info("客服WS认证成功: {} ({})", user.getNickName(), userId);
                }

                broadcastUserStatus(userId, MessageType.USER_ONLINE);

                logger.info("用户 {} 认证成功", userId);
            } else {
                logger.warn("认证失败，userId无效: {}", userId);
                sendAuthFailedMessage(ctx, "用户ID无效: " + userId);
            }
        } catch (Exception e) {
            logger.error("处理认证消息异常", e);
            sendAuthFailedMessage(ctx, "认证失败: " + e.getMessage());
        }
    }

    private void handleGuestAuth(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            String visitorToken = message.getVisitorToken();
            if (StringUtils.isEmpty(visitorToken)) {
                logger.warn("访客认证缺少visitorToken");
                sendAuthFailedMessage(ctx, "认证失败：缺少访客Token");
                return;
            }

            ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
            if (visitor == null) {
                logger.warn("访客Token无效: {}", visitorToken);
                sendAuthFailedMessage(ctx, "认证失败：访客不存在");
                return;
            }

            ctx.channel().attr(VISITOR_TOKEN_KEY).set(visitorToken);
            visitorChannels.put(visitorToken, ctx.channel());

            ChatMessage response = new ChatMessage(MessageType.AUTH_SUCCESS);
            response.setMessageId(UUID.randomUUID().toString());
            sendMessage(ctx, response);
            logger.info("访客认证成功: {} ({})", visitor.getNickname(), visitorToken);
        } catch (Exception e) {
            logger.error("处理访客认证异常", e);
            sendAuthFailedMessage(ctx, "访客认证失败");
        }
    }

    private void handleCsChat(ChannelHandlerContext ctx, ChatMessage message) {
        try {
            Long sessionId = null;
            if (message.getSessionId() != null) {
                try {
                    sessionId = Long.valueOf(message.getSessionId());
                } catch (NumberFormatException e) {
                    logger.warn("无效的客服会话ID: {}", message.getSessionId());
                    sendErrorMessage(ctx, "无效的会话ID");
                    return;
                }
            }

            if (sessionId == null) {
                logger.warn("CS_CHAT缺少会话ID");
                sendErrorMessage(ctx, "缺少会话ID");
                return;
            }

            CsSession session = csSessionService.getById(sessionId);
            if (session == null) {
                logger.warn("CS_CHAT会话不存在, sessionId={}", sessionId);
                sendErrorMessage(ctx, "会话不存在或已结束");
                return;
            }
            if (!Integer.valueOf(CsSession.Status.ACTIVE).equals(session.getStatus())) {
                logger.warn("CS_CHAT会话状态非ACTIVE, sessionId={}, status={}", sessionId, session.getStatus());
                sendErrorMessage(ctx, "会话不存在或已结束");
                return;
            }

            Long userId = ctx.channel().attr(USER_ID_KEY).get();
            String visitorToken = ctx.channel().attr(VISITOR_TOKEN_KEY).get();
            boolean isCs = false;
            Long senderUserId = null;
            String senderNickname = null;

            if (userId != null) {
                // 已登录用户（客服）
                senderUserId = userId;
                SysUser user = sysUserService.selectUserById(userId);
                senderNickname = user != null ? user.getNickName() : "客服";
                if (!session.getCsUserId().equals(senderUserId)) {
                    logger.warn("CS_CHAT客服无权发送, sessionId={}, senderUserId={}", sessionId, senderUserId);
                    sendErrorMessage(ctx, "无权发送消息到此会话");
                    return;
                }
                isCs = true;
            } else if (visitorToken != null) {
                // 访客
                ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
                if (visitor == null) {
                    logger.warn("CS_CHAT访客不存在, visitorToken={}", visitorToken);
                    sendErrorMessage(ctx, "无权发送消息到此会话");
                    return;
                }
                if (!visitor.getId().equals(session.getVisitorId())) {
                    logger.warn("CS_CHAT访客ID不匹配, visitorId={}, sessionVisitorId={}", visitor.getId(), session.getVisitorId());
                    sendErrorMessage(ctx, "无权发送消息到此会话");
                    return;
                }
                if (visitor.getBoundUserId() != null) {
                    SysUser boundUser = sysUserService.selectUserById(visitor.getBoundUserId());
                    senderNickname = boundUser != null ? boundUser.getNickName() : visitor.getNickname();
                } else {
                    senderNickname = visitor.getNickname();
                }
            } else {
                logger.warn("CS_CHAT未认证的发送者");
                sendErrorMessage(ctx, "请先进行身份认证");
                return;
            }

            // 保存消息
            int fromType = isCs ? CsMessage.FromType.CS : CsMessage.FromType.VISITOR;
            csMessageService.sendMessage(sessionId, fromType, senderUserId, message.getContent());
            logger.info("CS_CHAT消息已保存, sessionId={}, fromType={}, isCs={}", sessionId, fromType, isCs);

            // 构造推送消息
            ChatMessage pushMsg = new ChatMessage(MessageType.CS_CHAT);
            pushMsg.setSessionId(String.valueOf(sessionId));
            pushMsg.setContent(message.getContent());
            pushMsg.setFromUserId(senderUserId);
            pushMsg.setFromUserNickname(senderNickname);
            pushMsg.setTimestamp(new java.util.Date());
            pushMsg.setMessageId(message.getMessageId());

            // 推送给接收方
            if (isCs) {
                // 客服发给访客
                ChatVisitor visitor = chatVisitorService.getById(session.getVisitorId());
                if (visitor != null) {
                    Channel ch = visitorChannels.get(visitor.getVisitorToken());
                    if (ch != null && ch.isActive()) {
                        sendMessage(ch, pushMsg);
                        logger.info("CS_CHAT客服->访客推送成功");
                    } else {
                        logger.info("CS_CHAT访客不在线, visitorToken={}", visitor.getVisitorToken());
                    }
                }
            } else {
                // 访客发给客服
                logger.info("CS_CHAT准备推送给客服, csUserId={}, 在线用户={}", session.getCsUserId(), connectionManager.getOnlineUserIds());
                Channel csChannel = connectionManager.getUserChannel(session.getCsUserId());
                if (csChannel != null && csChannel.isActive()) {
                    sendMessage(csChannel, pushMsg);
                    logger.info("CS_CHAT访客->客服推送成功, csUserId={}", session.getCsUserId());
                } else {
                    logger.warn("CS_CHAT客服通道不可用, csUserId={}, channel={}", session.getCsUserId(), csChannel);
                }
                // 访客消息统一累加客服未读数（前端查看后会清零）
                csSessionService.incrementUnreadCount(sessionId);
            }

            // 发送确认
            ChatMessage ack = new ChatMessage(MessageType.AUTH_SUCCESS);
            ack.setMessageId(message.getMessageId());
            ack.setTimestamp(new java.util.Date());
            sendMessage(ctx, ack);

        } catch (Exception e) {
            logger.error("处理客服消息异常", e);
            sendErrorMessage(ctx, "消息发送失败");
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, ChatMessage message) {
        ChatMessage response = new ChatMessage(MessageType.HEARTBEAT_RESPONSE);
        response.setMessageId(UUID.randomUUID().toString());
        sendMessage(ctx, response);
    }

    private void handlePrivateChat(ChannelHandlerContext ctx, ChatMessage message) {
        if (!isAuthenticated(ctx)) {
            sendErrorMessage(ctx, "未认证");
            return;
        }

        Long fromUserId = ctx.channel().attr(USER_ID_KEY).get();
        Long toUserId = message.getToUserId();
        String sessionId = message.getSessionId();
        
        logger.debug("处理私聊消息 - fromUserId: {}, toUserId: {}, sessionId: {}", fromUserId, toUserId, sessionId);
        
        if (StringUtils.isEmpty(sessionId)) {
            sendErrorMessage(ctx, "会话ID不能为空");
            return;
        }

        if (toUserId == null || toUserId == 0) {
            List<com.ruoyi.chat.domain.entity.ChatSessionMember> members = chatSessionService.getSessionMembers(sessionId);
            for (com.ruoyi.chat.domain.entity.ChatSessionMember member : members) {
                if (!member.getUserId().equals(fromUserId)) {
                    toUserId = member.getUserId();
                    break;
                }
            }
            if (toUserId == null) {
                logger.warn("私聊会话 {} 中找不到除 {} 以外的其他成员", sessionId, fromUserId);
                sendErrorMessage(ctx, "无法找到接收者");
                return;
            }
            logger.debug("从数据库查询到接收者: {}", toUserId);
        }

        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);

        com.ruoyi.common.core.domain.entity.SysUser user = sysUserService.selectUserById(fromUserId);
        String nickname = user != null ? user.getUserName() : null;

        io.netty.channel.Channel toChannel = connectionManager.getUserChannel(toUserId);
        if (toChannel != null && toChannel.isActive()) {
            ChatMessage pushMessage = new ChatMessage();
            pushMessage.setType(message.getType());
            pushMessage.setMessageId(message.getMessageId() != null ? message.getMessageId() : UUID.randomUUID().toString());
            pushMessage.setSessionId(sessionId);
            pushMessage.setFromUserId(fromUserId);
            pushMessage.setToUserId(toUserId);
            pushMessage.setContent(message.getContent());
            pushMessage.setContentType(message.getContentType());
            pushMessage.setTimestamp(message.getTimestamp() != null ? message.getTimestamp() : new java.util.Date());
            pushMessage.setExtra(message.getExtra());
            pushMessage.setFromUserNickname(nickname);
            
            sendMessage(toChannel, pushMessage);
            logger.debug("消息已推送给接收者: {}", toUserId);
        } else {
            logger.debug("接收者 {} 不在线，消息将通过HTTP保存", toUserId);
        }
        
        ChatMessage senderMessage = new ChatMessage();
        senderMessage.setType(message.getType());
        senderMessage.setMessageId(message.getMessageId() != null ? message.getMessageId() : UUID.randomUUID().toString());
        senderMessage.setSessionId(sessionId);
        senderMessage.setFromUserId(fromUserId);
        senderMessage.setToUserId(toUserId);
        senderMessage.setContent(message.getContent());
        senderMessage.setContentType(message.getContentType());
        senderMessage.setTimestamp(message.getTimestamp() != null ? message.getTimestamp() : new java.util.Date());
        senderMessage.setExtra(message.getExtra());
        senderMessage.setFromUserNickname(nickname);
        
        sendMessage(ctx, senderMessage);
        logger.debug("确认消息已发送给发送者: {}", fromUserId);

        logger.info("私聊消息：{} -> {}", fromUserId, toUserId);
    }

    private void handleGroupChat(ChannelHandlerContext ctx, ChatMessage message) {
        if (!isAuthenticated(ctx)) {
            sendErrorMessage(ctx, "未认证");
            return;
        }

        Long fromUserId = ctx.channel().attr(USER_ID_KEY).get();
        String sessionId = message.getSessionId();
        
        if (StringUtils.isEmpty(sessionId)) {
            sendErrorMessage(ctx, "会话ID不能为空");
            return;
        }

        message.setMessageId(UUID.randomUUID().toString());
        message.setFromUserId(fromUserId);
        
        com.ruoyi.common.core.domain.entity.SysUser user = sysUserService.selectUserById(fromUserId);
        if (user != null) {
            message.setFromUserNickname(user.getUserName());
        }

        chatMessageService.saveGroupMessage(message);

        Set<Long> members = connectionManager.getSessionMembers(sessionId);
        for (Long memberId : members) {
            if (!memberId.equals(fromUserId)) {
                io.netty.channel.Channel memberChannel = connectionManager.getUserChannel(memberId);
                if (memberChannel != null && memberChannel.isActive()) {
                    sendMessage(memberChannel, message);
                }
            }
        }

        logger.info("群聊消息：{} -> 会话 {}", fromUserId, sessionId);
    }

    private void handleMessageRead(ChannelHandlerContext ctx, ChatMessage message) {
        if (!isAuthenticated(ctx)) {
            sendErrorMessage(ctx, "未认证");
            return;
        }

        Long userId = ctx.channel().attr(USER_ID_KEY).get();
        chatMessageService.markMessageAsRead(message.getMessageId(), userId);
        
        logger.info("消息已读：用户 {} 消息 {}", userId, message.getMessageId());
    }

    private boolean isAuthenticated(ChannelHandlerContext ctx) {
        Boolean authenticated = ctx.channel().attr(AUTH_KEY).get();
        return authenticated != null && authenticated;
    }

    private void sendMessage(ChannelHandlerContext ctx, ChatMessage message) {
        sendMessage(ctx.channel(), message);
    }

    private void sendMessage(io.netty.channel.Channel channel, ChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            logger.error("发送消息异常", e);
        }
    }

    private void sendErrorMessage(ChannelHandlerContext ctx, String error) {
        ChatMessage message = new ChatMessage(MessageType.ERROR);
        message.setContent(error);
        message.setMessageId(UUID.randomUUID().toString());
        sendMessage(ctx, message);
    }

    private void sendAuthFailedMessage(ChannelHandlerContext ctx, String reason) {
        ChatMessage message = new ChatMessage(MessageType.AUTH_FAILED);
        message.setContent(reason);
        message.setMessageId(UUID.randomUUID().toString());
        sendMessage(ctx, message);
    }

    private void broadcastUserStatus(Long userId, MessageType statusType) {
        ChatMessage message = new ChatMessage(statusType);
        message.setFromUserId(userId);
        message.setMessageId(UUID.randomUUID().toString());
        
        for (Long onlineUserId : connectionManager.getOnlineUserIds()) {
            if (!onlineUserId.equals(userId)) {
                io.netty.channel.Channel channel = connectionManager.getUserChannel(onlineUserId);
                if (channel != null && channel.isActive()) {
                    sendMessage(channel, message);
                }
            }
        }
    }
}
