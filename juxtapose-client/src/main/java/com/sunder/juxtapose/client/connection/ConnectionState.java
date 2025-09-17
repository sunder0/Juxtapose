package com.sunder.juxtapose.client.connection;

/**
 * @author : denglinhai
 * @date : 16:59 2025/09/16
 */
public enum ConnectionState {
    /**
     * 刚初始化
     */
    INIT,

    /**
     * 连接已建立
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
     * 活跃，正在处理业务
     */
    ACTIVE,

    /**
     * 空闲（超过一定时间无活动）
     */
    IDLE,

    /**
     * 暂停（如流量控制、手动暂停）
     */
    SUSPENDED,

    /**
     * 连接已关闭
     */
    CLOSED,

    /**
     * 连接出现错误
     */
    ERROR;

    /**
     * 验证下一个状态转换是否合理
     *
     * @param next 下一个状态
     * @return bool
     */
    public boolean after(ConnectionState next) {
        // 这里简化了转换规则，实际应根据业务需求设计
        switch (this) {
            case INIT:
                return next == ConnectionState.CONNECTED || next == ConnectionState.CLOSED;
            case CONNECTED:
                return next == ConnectionState.AUTHENTICATING ||
                        next == ConnectionState.ACTIVE ||
                        next == ConnectionState.AUTHENTICATED ||
                        next == ConnectionState.CLOSED;
            case AUTHENTICATING:
                return next == ConnectionState.AUTHENTICATED ||
                        next == ConnectionState.CONNECTED || // 认证失败
                        next == ConnectionState.CLOSED;
            case AUTHENTICATED:
                return next == ConnectionState.ACTIVE ||
                        next == ConnectionState.CLOSED;
            case ACTIVE:
                return next == ConnectionState.IDLE ||
                        next == ConnectionState.SUSPENDED ||
                        next == ConnectionState.CLOSED;
            case IDLE:
                return next == ConnectionState.ACTIVE ||
                        next == ConnectionState.SUSPENDED ||
                        next == ConnectionState.CLOSED;
            case SUSPENDED:
                return next == ConnectionState.ACTIVE ||
                        next == ConnectionState.IDLE ||
                        next == ConnectionState.CLOSED;
            case CLOSED:
                return false; // 终态
            default:
                return false;
        }
    }
}
