package com.ruoyi.chat.controller;

import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.domain.entity.CsConfig;
import com.ruoyi.chat.domain.entity.CsMessage;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.service.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 在线客服Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/cs")
public class CustomerServiceController {

    @Autowired
    private IChatVisitorService chatVisitorService;

    @Autowired
    private ICsSessionService csSessionService;

    @Autowired
    private ICsMessageService csMessageService;

    @Autowired
    private CustomerServiceAllocationService allocationService;

    @Autowired
    private CustomerServiceRedisManager redisManager;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ICsConfigService csConfigService;

    @Autowired
    private com.ruoyi.chat.netty.ChatChannelHandler chatChannelHandler;

    // ==================== 访客端接口（无需登录） ====================

    /**
     * 访客接入
     */
    @PostMapping("/connect")
    public AjaxResult connect(@RequestBody Map<String, String> params,
                               HttpServletRequest request) {
        String visitorToken = params.get("visitorToken");
        if (visitorToken == null || visitorToken.isEmpty()) {
            visitorToken = UUID.randomUUID().toString().replace("-", "");
        }

        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String sourcePage = params.get("sourcePage");

        ChatVisitor visitor = chatVisitorService.getOrCreateVisitor(visitorToken, ip, userAgent, sourcePage);

        CustomerServiceAllocationService.AllocationResult result = allocationService.allocate(visitor);

        Map<String, Object> data = new HashMap<>();
        data.put("visitorToken", visitorToken);
        data.put("success", result.isSuccess());
        data.put("waiting", result.isWaiting());
        data.put("waitingForLastCs", result.isWaitingForLastCs());
        data.put("sessionId", result.getSessionId());
        data.put("csUserId", result.getCsUserId());
        data.put("csNickname", result.getCsNickname());
        data.put("message", result.getMessage());

        return AjaxResult.success(data);
    }

    /**
     * 访客发送消息
     */
    @PostMapping("/message/send")
    public AjaxResult sendMessage(@RequestBody Map<String, Object> params) {
        String visitorToken = (String) params.get("visitorToken");
        Long sessionId = Long.valueOf(params.get("sessionId").toString());
        String content = (String) params.get("content");

        if (visitorToken == null || sessionId == null || content == null) {
            return AjaxResult.error("参数错误");
        }

        ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
        if (visitor == null) {
            return AjaxResult.error("访客不存在");
        }

        CsSession session = csSessionService.getById(sessionId);
        if (session == null || !session.getStatus().equals(CsSession.Status.ACTIVE)) {
            return AjaxResult.error("会话不存在或已结束");
        }

        if (!session.getVisitorId().equals(visitor.getId())) {
            return AjaxResult.error("无权操作");
        }

        csMessageService.sendMessage(sessionId, CsMessage.FromType.VISITOR, null, content);
        return AjaxResult.success();
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/session/history/{sessionId}")
    public AjaxResult getSessionHistory(@PathVariable Long sessionId,
                                         @RequestParam String visitorToken) {
        ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
        if (visitor == null) {
            return AjaxResult.error("访客不存在");
        }

        CsSession session = csSessionService.getById(sessionId);
        if (session == null || !session.getVisitorId().equals(visitor.getId())) {
            return AjaxResult.error("无权查看");
        }

        List<CsMessage> messages = csMessageService.selectMessagesBySessionId(sessionId);
        return AjaxResult.success(messages);
    }

    // ==================== 客服端接口（需客服角色） ====================

    /**
     * 客服上线
     */
    @PostMapping("/online")
    public AjaxResult csOnline() {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }
        CsConfig config = csConfigService.getOrCreateDefault(user.getUserId());
        redisManager.setCsStatus(user.getUserId(), "online", config.getMaxSessions());
        return AjaxResult.success("已上线");
    }

    /**
     * 客服下线
     */
    @PostMapping("/offline")
    public AjaxResult csOffline() {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }
        redisManager.setCsStatus(user.getUserId(), "offline", redisManager.getMaxSessions(user.getUserId()));
        return AjaxResult.success("已下线");
    }

    /**
     * 客服工作台会话列表
     */
    @GetMapping("/workbench/sessions")
    public AjaxResult getWorkbenchSessions() {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }

        List<CsSession> sessions = csSessionService.selectActiveSessionsByCsUserId(user.getUserId());
        return AjaxResult.success(sessions);
    }

    /**
     * 客服结束会话
     */
    @PostMapping("/session/close/{sessionId}")
    public AjaxResult closeSession(@PathVariable Long sessionId) {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }

        CsSession session = csSessionService.getById(sessionId);
        if (session == null) {
            return AjaxResult.error("会话不存在");
        }

        if (!session.getCsUserId().equals(user.getUserId())) {
            return AjaxResult.error("无权操作");
        }

        csSessionService.closeSession(sessionId);
        redisManager.decrementActiveCount(user.getUserId());

        csMessageService.sendSystemMessage(sessionId, "会话已结束");

        // 推送会话结束通知给访客
        try {
            ChatVisitor visitor = chatVisitorService.getById(session.getVisitorId());
            if (visitor != null) {
                com.ruoyi.chat.protocol.ChatMessage systemMsg = new com.ruoyi.chat.protocol.ChatMessage(com.ruoyi.chat.protocol.MessageType.SYSTEM_NOTICE);
                systemMsg.setContent("会话已结束");
                systemMsg.setMessageId(java.util.UUID.randomUUID().toString());
                chatChannelHandler.sendMessageToVisitor(visitor.getVisitorToken(), systemMsg);
            }
        } catch (Exception e) {
            // 推送失败不影响关闭流程
        }

        // 尝试消费等待集合
        allocationService.tryConsumeWaitingAfterClose(user.getUserId());

        return AjaxResult.success();
    }

    /**
     * 客服加载会话历史消息
     */
    @GetMapping("/session/{sessionId}/messages")
    public AjaxResult getSessionMessages(@PathVariable Long sessionId) {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }
        CsSession session = csSessionService.getById(sessionId);
        if (session == null || !session.getCsUserId().equals(user.getUserId())) {
            return AjaxResult.error("无权操作");
        }
        List<CsMessage> messages = csMessageService.selectMessagesBySessionId(sessionId);
        return AjaxResult.success(messages);
    }

    /**
     * 客服发送消息
     */
    @PostMapping("/cs/message/send")
    public AjaxResult csSendMessage(@RequestBody Map<String, Object> params) {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (!Integer.valueOf(1).equals(user.getIsCustomerService())) {
            return AjaxResult.error("无权访问");
        }

        Long sessionId = Long.valueOf(params.get("sessionId").toString());
        String content = (String) params.get("content");

        CsSession session = csSessionService.getById(sessionId);
        if (session == null || !session.getCsUserId().equals(user.getUserId())) {
            return AjaxResult.error("无权操作");
        }

        csMessageService.sendMessage(sessionId, CsMessage.FromType.CS, user.getUserId(), content);
        return AjaxResult.success();
    }

    // ==================== 已登录用户接口 ====================

    /**
     * 历史会话消息明细
     */
    @GetMapping("/my/history/{sessionId}/messages")
    public AjaxResult getMyHistoryMessages(@PathVariable Long sessionId) {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        // 校验该会话是否属于当前用户
        CsSession session = csSessionService.getById(sessionId);
        if (session == null) {
            return AjaxResult.error("会话不存在");
        }
        ChatVisitor visitor = chatVisitorService.getById(session.getVisitorId());
        if (visitor == null || !user.getUserId().equals(visitor.getBoundUserId())) {
            return AjaxResult.error("无权查看");
        }
        List<CsMessage> messages = csMessageService.selectMessagesBySessionId(sessionId);
        return AjaxResult.success(messages);
    }

    /**
     * 我的客服历史
     */
    @GetMapping("/my/history")
    public AjaxResult getMyHistory() {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        List<CsSession> sessions = csSessionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CsSession>()
                        .inSql("visitor_id", "SELECT id FROM chat_visitor WHERE bound_user_id = " + user.getUserId())
                        .orderByDesc("create_time")
        );
        return AjaxResult.success(sessions);
    }

    /**
     * 确认绑定匿名客服历史
     */
    @PostMapping("/bind/confirm")
    public AjaxResult confirmBind(HttpServletRequest request) {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        String ip = getClientIp(request);
        int count = chatVisitorService.bindByLogin(user.getUserId(), ip);
        return AjaxResult.success("已绑定 " + count + " 条历史记录");
    }

    // ==================== 管理端接口 ====================

    /**
     * 客服列表
     */
    @GetMapping("/staff/list")
    public AjaxResult getStaffList() {
        SysUser query = new SysUser();
        query.setIsCustomerService(1);
        List<SysUser> list = sysUserService.selectUserList(query);
        return AjaxResult.success(list);
    }

    /**
     * 设置/取消客服身份
     */
    @PostMapping("/staff/set")
    public AjaxResult setStaff(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Integer isCs = Integer.valueOf(params.get("isCustomerService").toString());

        SysUser user = sysUserService.selectUserById(userId);
        if (user == null) {
            return AjaxResult.error("用户不存在");
        }

        user.setIsCustomerService(isCs);
        sysUserService.updateUser(user);

        return AjaxResult.success();
    }

    // ==================== 工具方法 ====================

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
