package com.ruoyi.chat.service;

import com.ruoyi.common.core.redis.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 客服Redis状态管理器
 * 管理客服在线状态、接待数、等待集合、挂起分配等
 *
 * @author ruoyi
 */
@Component
public class CustomerServiceRedisManager {

    @Autowired
    private RedisCache redisCache;

    private static final String CS_STATUS_PREFIX = "cs:status:";
    private static final String CS_WAITING_KEY = "cs:waiting";
    private static final String CS_PENDING_PREFIX = "cs:pending:";
    private static final String VISITOR_SESSION_PREFIX = "cs:visitor:";

    // ==================== 客服状态管理 ====================

    /**
     * 设置客服状态
     */
    public void setCsStatus(Long csUserId, String status, Integer maxSessions) {
        setCsStatus(csUserId, status, maxSessions, null);
    }

    /**
     * 设置客服状态（可指定接待数，null则保持原值）
     */
    public void setCsStatus(Long csUserId, String status, Integer maxSessions, Integer activeCount) {
        String key = CS_STATUS_PREFIX + csUserId;
        Map<String, Object> map = new HashMap<>();
        map.put("status", status);
        map.put("maxSessions", maxSessions != null ? maxSessions : 5);
        map.put("activeCount", activeCount != null ? activeCount : getActiveCount(csUserId));
        map.put("lastHeartbeat", System.currentTimeMillis());
        redisCache.setCacheMap(key, map);
        redisCache.expire(key, 3600, TimeUnit.SECONDS);
    }

    /**
     * 获取客服状态
     */
    public String getCsStatus(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        Object status = redisCache.getCacheMapValue(key, "status");
        return status != null ? status.toString() : "offline";
    }

    /**
     * 获取客服最大接待数
     */
    public int getMaxSessions(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        Object max = redisCache.getCacheMapValue(key, "maxSessions");
        return max != null ? Integer.parseInt(max.toString()) : 5;
    }

    /**
     * 获取客服当前接待数
     */
    public int getActiveCount(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        Object count = redisCache.getCacheMapValue(key, "activeCount");
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    /**
     * 增加接待数
     */
    public void incrementActiveCount(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        redisCache.redisTemplate.opsForHash().increment(key, "activeCount", 1);
    }

    /**
     * 减少接待数
     */
    public void decrementActiveCount(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        Long count = redisCache.redisTemplate.opsForHash().increment(key, "activeCount", -1);
        if (count != null && count < 0) {
            redisCache.setCacheMapValue(key, "activeCount", 0);
        }
    }

    /**
     * 更新最后心跳时间
     */
    public void updateHeartbeat(Long csUserId) {
        String key = CS_STATUS_PREFIX + csUserId;
        redisCache.setCacheMapValue(key, "lastHeartbeat", System.currentTimeMillis());
    }

    /**
     * 获取所有在线客服的userId列表
     */
    public List<Long> getOnlineCsUserIds() {
        List<Long> result = new ArrayList<>();
        Collection<String> keys = redisCache.keys(CS_STATUS_PREFIX + "*");
        if (keys == null) return result;
        for (String key : keys) {
            Object status = redisCache.getCacheMapValue(key, "status");
            if ("online".equals(status)) {
                String userIdStr = key.substring(CS_STATUS_PREFIX.length());
                result.add(Long.valueOf(userIdStr));
            }
        }
        return result;
    }

    // ==================== 等待集合 ====================

    /**
     * 添加访客到等待队列（FIFO，去重）
     */
    public void addToWaiting(String visitorToken) {
        List<Object> list = redisCache.redisTemplate.opsForList().range(CS_WAITING_KEY, 0, -1);
        if (list == null || !list.contains(visitorToken)) {
            redisCache.redisTemplate.opsForList().rightPush(CS_WAITING_KEY, visitorToken);
        }
    }

    /**
     * 从等待队列弹出一个访客（FIFO）
     */
    public String popFromWaiting() {
        Object obj = redisCache.redisTemplate.opsForList().leftPop(CS_WAITING_KEY);
        return obj != null ? obj.toString() : null;
    }

    /**
     * 从等待队列移除指定访客
     */
    public void removeFromWaiting(String visitorToken) {
        redisCache.redisTemplate.opsForList().remove(CS_WAITING_KEY, 0, visitorToken);
    }

    /**
     * 获取等待队列大小
     */
    public long getWaitingCount() {
        Long size = redisCache.redisTemplate.opsForList().size(CS_WAITING_KEY);
        return size != null ? size : 0;
    }

    /**
     * 获取访客在等待队列中的位置（1-based，0表示不在队列）
     */
    public int getWaitingPosition(String visitorToken) {
        List<Object> list = redisCache.redisTemplate.opsForList().range(CS_WAITING_KEY, 0, -1);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < list.size(); i++) {
            if (visitorToken.equals(list.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    // ==================== 上次客服挂起（Pending） ====================

    /**
     * 设置挂起访客（用于上次客服离线等待）
     */
    public void setPending(Long csUserId, String visitorToken, int timeoutSeconds) {
        String key = CS_PENDING_PREFIX + csUserId;
        redisCache.setCacheObject(key, visitorToken, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取挂起访客
     */
    public String getPending(Long csUserId) {
        Object obj = redisCache.getCacheObject(CS_PENDING_PREFIX + csUserId);
        return obj != null ? obj.toString() : null;
    }

    /**
     * 清除挂起访客
     */
    public void clearPending(Long csUserId) {
        redisCache.deleteObject(CS_PENDING_PREFIX + csUserId);
    }

    // ==================== 访客会话映射 ====================

    /**
     * 设置访客当前会话信息
     */
    public void setVisitorSession(String visitorToken, Long sessionId, Long csUserId) {
        String key = VISITOR_SESSION_PREFIX + visitorToken;
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", sessionId);
        map.put("csUserId", csUserId);
        redisCache.setCacheMap(key, map);
        redisCache.expire(key, 7200, TimeUnit.SECONDS);
    }

    /**
     * 获取访客当前会话信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getVisitorSession(String visitorToken) {
        String key = VISITOR_SESSION_PREFIX + visitorToken;
        return redisCache.getCacheMap(key);
    }

    /**
     * 清除访客会话信息
     */
    public void clearVisitorSession(String visitorToken) {
        redisCache.deleteObject(VISITOR_SESSION_PREFIX + visitorToken);
    }

    /**
     * 客服是否在线且有容量
     */
    public boolean isCsAvailable(Long csUserId) {
        String status = getCsStatus(csUserId);
        if (!"online".equals(status)) {
            return false;
        }
        int active = getActiveCount(csUserId);
        int max = getMaxSessions(csUserId);
        return active < max;
    }
}
