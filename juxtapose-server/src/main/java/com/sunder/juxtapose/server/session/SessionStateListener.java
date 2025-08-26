package com.sunder.juxtapose.server.session;

/**
 * @author : denglinhai
 * @date : 14:08 2025/08/26
 */
public interface SessionStateListener {

    /**
     * 监听状态变化
     *
     * @param session session
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void onStateChanged(ClientSession session, SessionState oldState, SessionState newState);
}
