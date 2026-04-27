class ChatWebSocket {
    constructor(url, options = {}) {
        this.url = url;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = options.maxReconnectAttempts || 5;
        this.reconnectInterval = options.reconnectInterval || 3000;
        this.heartbeatInterval = options.heartbeatInterval || 30000;
        this.heartbeatTimer = null;

        this.onOpen = options.onOpen || (() => {});
        this.onMessage = options.onMessage || (() => {});
        this.onClose = options.onClose || (() => {});
        this.onError = options.onError || (() => {});

        this.connect();
    }

    connect() {
        try {
            console.log('=== 开始连接 WebSocket ===');
            console.log('连接地址:', this.url);
            console.log('当前页面地址:', window.location.href);
            console.log('当前域名:', window.location.host);

            this.ws = new WebSocket(this.url);

            this.ws.onopen = (event) => {
                console.log('=== WebSocket 连接成功 ===');
                console.log('readyState:', this.ws.readyState);
                this.reconnectAttempts = 0;
                this.startHeartbeat();
                this.onOpen(event);
            };

            this.ws.onmessage = (event) => {
                const message = JSON.parse(event.data);
                console.log('=== 收到消息 ===', message);
                this.onMessage(message);
            };

            this.ws.onclose = (event) => {
                console.log('=== WebSocket 连接关闭 ===');
                console.log('关闭代码:', event.code, '原因:', event.reason);
                this.stopHeartbeat();
                this.onClose(event);
                this.handleReconnect();
            };

            this.ws.onerror = (event) => {
                console.error('=== WebSocket 连接错误 ===');
                console.error('错误事件:', event);
                console.error('URL:', this.url);
                console.error('readyState:', this.ws ? this.ws.readyState : 'null');
                console.error('浏览器控制台 Network 标签可以看到更多详情');
                this.onError(event);
            };
        } catch (error) {
            console.error('=== WebSocket 连接失败 ===', error);
            this.handleReconnect();
        }
    }

    handleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            setTimeout(() => {
                this.connect();
            }, this.reconnectInterval);
        } else {
            console.error('WebSocket 重连失败，已达到最大重连次数');
        }
    }

    startHeartbeat() {
        this.heartbeatTimer = setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.send({
                    type: 'HEARTBEAT',
                    timestamp: new Date().toISOString()
                });
            }
        }, this.heartbeatInterval);
    }

    stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    send(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            console.log('发送消息:', message);
            this.ws.send(JSON.stringify(message));
            return true;
        } else {
            console.warn('WebSocket 连接未就绪，消息发送失败');
            console.warn('readyState:', this.ws ? this.ws.readyState : 'null');
            return false;
        }
    }

    close() {
        this.stopHeartbeat();
        if (this.ws) {
            this.ws.close();
        }
    }
}

export default ChatWebSocket;
