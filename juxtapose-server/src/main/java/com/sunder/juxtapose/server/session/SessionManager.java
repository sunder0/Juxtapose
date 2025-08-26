package com.sunder.juxtapose.server.session;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.BaseModule;
import com.sunder.juxtapose.server.ProxyCoreComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : denglinhai
 * @date : 12:11 2025/08/26
 */
public class SessionManager extends BaseModule<ProxyCoreComponent> {
    public final static String NAME = "SESSION_MANAGER";

    private final Logger logger;
    private final ScheduledThreadPoolExecutor executor;
    // 存放sessionId->session的映射
    private Map<String, ClientSession> sessionMap = new ConcurrentHashMap<>(16);

    public SessionManager(ProxyCoreComponent belongComponent) {
        super(NAME, belongComponent);
        this.logger = LoggerFactory.getLogger(SessionManager.class);
        this.executor = new ScheduledThreadPoolExecutor(2,
                ThreadFactoryBuilder.create().setNamePrefix("SessionManage-").build());

        this.executor.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 60, TimeUnit.SECONDS);
    }

    public void addSession(ClientSession session) {
        sessionMap.putIfAbsent(session.getSessionId(), session);
        logger.info("Session added:[{}], total:[{}]", session.getSessionId(), sessionMap.size());

        // 添加状态监听器，用于在Session关闭时自动清理
        session.addSessionStateListener(new SessionStateListener() {
            @Override
            public void onStateChanged(ClientSession session, SessionState oldState, SessionState newState) {
                if (newState == SessionState.CLOSED) {
                    removeSession(session.getSessionId());
                }
            }
        });
    }

    public ClientSession removeSession(String sessionId) {
        ClientSession session = sessionMap.remove(sessionId);

        logger.info("Session removed:[{}], remaining:[{}]", sessionId, sessionMap.size());
        return session;
    }

    public ClientSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * 清理过期的session
     */
    private void cleanupExpiredSessions() {
        int cleaned = 0;
        long now = System.currentTimeMillis();

        for (ClientSession session : sessionMap.values()) {
            // 清理长时间处于DISCONNECTED状态的Session
            if (session.getState() == SessionState.DISCONNECTED &&
                    (now - session.getLastActivityTime()) > TimeUnit.HOURS.toMillis(12)) {
                session.close();
                cleaned++;
            }

            // 清理长时间未认证的Session
            else if (session.getState() == SessionState.CONNECTED &&
                    (now - session.getLastActivityTime()) > TimeUnit.MINUTES.toMillis(1)) {
                logger.warn("Session[{}] authentication timeout, closing", session.getSessionId());
                session.close();
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.info("Cleaned up {} expired sessions...", cleaned);
        }
    }

}
