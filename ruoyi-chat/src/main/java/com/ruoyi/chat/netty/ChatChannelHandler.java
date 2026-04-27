package com.ruoyi.chat.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.chat.protocol.ChatMessage;
import com.ruoyi.chat.protocol.MessageType;
import com.ruoyi.chat.service.IChatMessageService;
import com.ruoyi.chat.service.IChatSessionService;
import com.ruoyi.common.utils.StringUtils;
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

    @Autowired
    private ChatConnectionManager connectionManager;

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private IChatSessionService chatSessionService;

    @Autowired
    private com.ruoyi.system.service.ISysUserService sysUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("WebSocket连接建立：{}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            connectionManager.removeConnection(ctx.channel());
            broadcastUserStatus(userId, MessageType.USER_OFFLINE);
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
            case HEARTBEAT:
                handleHeartbeat(ctx, message);
                break;
            case PRIVATE_CHAT:
                handlePrivateChat(ctx, message);
                break;
            case GROUP_CHAT:
                handleGroupChat(ctx, message);
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
                
                ChatMessage response = new ChatMessage(MessageType.AUTH_SUCCESS);
                response.setMessageId(UUID.randomUUID().toString());
                sendMessage(ctx, response);
                
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
