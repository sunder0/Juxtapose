package com.sunder.juxtapose.client.connection;

/**
 * @author : denglinhai
 * @date : 14:08 2025/08/26
 */
@FunctionalInterface
public interface ConnectionStateListener {

    /**
     * 监听状态变化
     *
     * @param connection connection
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void onStateChanged(Connection connection, ConnectionState oldState, ConnectionState newState);
}
