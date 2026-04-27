export const MessageType = {
    AUTH: 'AUTH',
    GUEST_AUTH: 'GUEST_AUTH',
    AUTH_SUCCESS: 'AUTH_SUCCESS',
    AUTH_FAILED: 'AUTH_FAILED',
    PRIVATE_CHAT: 'PRIVATE_CHAT',
    GROUP_CHAT: 'GROUP_CHAT',
    CS_CHAT: 'CS_CHAT',
    HEARTBEAT: 'HEARTBEAT',
    HEARTBEAT_RESPONSE: 'HEARTBEAT_RESPONSE',
    USER_ONLINE: 'USER_ONLINE',
    USER_OFFLINE: 'USER_OFFLINE',
    MESSAGE_READ: 'MESSAGE_READ',
    SYSTEM_NOTICE: 'SYSTEM_NOTICE',
    ERROR: 'ERROR'
};

export const ContentType = {
    TEXT: 'TEXT',
    IMAGE: 'IMAGE',
    EMOJI: 'EMOJI',
    FILE: 'FILE'
};

// 直接连接后端 9999 端口，使用当前访问的主机名（支持局域网访问）
const host = window.location.hostname || 'localhost';
export const WS_URL = `ws://${host}:9999/ws`;
