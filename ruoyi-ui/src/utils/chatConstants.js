export const MessageType = {
    AUTH: 'AUTH',
    GUEST_AUTH: 'GUEST_AUTH',
    AUTH_SUCCESS: 'AUTH_SUCCESS',
    AUTH_FAILED: 'AUTH_FAILED',
    CS_CHAT: 'CS_CHAT',
    HEARTBEAT: 'HEARTBEAT',
    HEARTBEAT_RESPONSE: 'HEARTBEAT_RESPONSE',
    USER_ONLINE: 'USER_ONLINE',
    USER_OFFLINE: 'USER_OFFLINE',
    SYSTEM_NOTICE: 'SYSTEM_NOTICE',
    ERROR: 'ERROR',
    CS_TRANSFER_REQUEST: 'CS_TRANSFER_REQUEST',
    CS_TRANSFER_ACCEPT: 'CS_TRANSFER_ACCEPT',
    CS_TRANSFER_REJECT: 'CS_TRANSFER_REJECT',
    CS_TRANSFER_RESULT: 'CS_TRANSFER_RESULT'
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
