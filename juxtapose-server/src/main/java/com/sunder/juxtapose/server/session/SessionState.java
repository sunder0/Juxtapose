package com.sunder.juxtapose.server.session;

/**
 * @author : denglinhai
 * @date : 11:35 2025/08/26
 */
public enum SessionState {
    /**
     * 初始状态，Channel已创建但尚未完成连接准备
     */
    INIT,

    /**
     * 连接已建立，但尚未进行任何交互
     */
    CONNECTED,

    /**
     * 正在认证过程中（已收到认证请求，等待处理结果）
     */
    AUTHENTICATING,

    /**
     * 已成功通过身份验证
     */
    AUTHENTICATED,

    /**
     * 会话活跃，正在处理业务
     */
    ACTIVE,

    /**
     * 会话空闲（超过一定时间无活动）
     */
    IDLE,

    /**
     * 会话暂停（如流量控制、手动暂停）
     */
    SUSPENDED,

    /**
     * 连接已断开，但Session仍保留一段时间（用于等待重连或数据清理）
     */
    DISCONNECTED,

    /**
     * Session已关闭，所有资源已释放
     */
    CLOSED;

    /**
     * 验证下一个状态转换是否合理
     *
     * @param next 下一个状态
     * @return bool
     */
    public boolean after(SessionState next) {
        // 这里简化了转换规则，实际应根据业务需求设计
        switch (this) {
            case INIT:
                return next == SessionState.CONNECTED || next == SessionState.CLOSED;
            case CONNECTED:
                return next == SessionState.AUTHENTICATING ||
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case AUTHENTICATING:
                return next == SessionState.AUTHENTICATED ||
                        next == SessionState.CONNECTED || // 认证失败
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case AUTHENTICATED:
                return next == SessionState.ACTIVE ||
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case ACTIVE:
                return next == SessionState.IDLE ||
                        next == SessionState.SUSPENDED ||
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case IDLE:
                return next == SessionState.ACTIVE ||
                        next == SessionState.SUSPENDED ||
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case SUSPENDED:
                return next == SessionState.ACTIVE ||
                        next == SessionState.IDLE ||
                        next == SessionState.DISCONNECTED ||
                        next == SessionState.CLOSED;
            case DISCONNECTED:
                return next == SessionState.CLOSED;
            case CLOSED:
                return false; // 终态
            default:
                return false;
        }
    }
}
