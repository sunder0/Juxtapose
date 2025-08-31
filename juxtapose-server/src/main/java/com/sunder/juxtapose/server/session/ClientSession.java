package com.sunder.juxtapose.server.session;

import com.sunder.juxtapose.common.Session;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : denglinhai
 * @date : 11:33 2025/08/26
 *         客户端关联的session
 */
public class ClientSession implements Session {
    public static final AttributeKey<ClientSession> SESSION_KEY = AttributeKey.valueOf("SESSION");

    private final Logger logger;
    private final Lock lock = new ReentrantLock();
    private volatile SessionState state = SessionState.INIT;
    private long lastStateChangeTime;

    private String sessionId;
    private SocketChannel channel;
    private InetSocketAddress clientAddress;
    private List<SessionStateListener> listeners = new CopyOnWriteArrayList<>();

    private long lastActivityTime;
    private long writeMsgCnt;


    public ClientSession(String sessionId, SocketChannel channel) {
        this.logger = LoggerFactory.getLogger(ClientSession.class);
        this.sessionId = sessionId;
        this.channel = channel;
        this.clientAddress = (InetSocketAddress) channel.remoteAddress();
    }

    /**
     * 构建一个绑定channel的session
     *
     * @param channel channel
     */
    public static ClientSession buildChannelBoundSession(String sessionId, SocketChannel channel) {
        ClientSession clientSession = new ClientSession(sessionId, channel);
        clientSession.changeState(SessionState.CONNECTED);
        channel.attr(ClientSession.SESSION_KEY).set(clientSession);
        return clientSession;
    }

    /**
     * 状态修改
     *
     * @param newState 新状态
     * @return bool
     */
    public boolean changeState(SessionState newState) {
        try {
            lock.lock();
            if (!state.after(newState)) {
                logger.warn("Invalid state transition: {} -> {} for session {}", state, newState, sessionId);
                return false;
            }

            SessionState oldState = state;
            state = newState;
            lastStateChangeTime = System.currentTimeMillis();

            logger.info("Session {} state changed: {} -> {}.", sessionId, oldState, newState);
            onStateChanged(oldState, newState);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 发送消息
     *
     * @param message 消息
     */
    public void writeAndFlush(Object message) {
        if (channel.isActive() && isWritableState()) {
            channel.writeAndFlush(message).addListener(future -> {
                if (future.isSuccess()) {
                    writeMsgCnt++;
                } else {
                    logger.warn("Failed to send message to session {}", sessionId, future.cause());
                    // 发送失败，可能需要关闭连接
                    changeState(SessionState.DISCONNECTED);
                }
            });
        } else {
            logger.warn("Attempted to send message in non-writable state: {}", state);
        }
    }

    /**
     * 更新活跃时间
     */
    public void updateActivityTime() {
        this.lastActivityTime = System.currentTimeMillis();
        // 如果当前是IDLE或者AUTHENTICATED状态，有活动时恢复为ACTIVE状态
        if (state == SessionState.IDLE || state == SessionState.AUTHENTICATED || state == SessionState.CONNECTED) {
            changeState(SessionState.ACTIVE);
        }
    }

    /**
     * 添加状态监听
     *
     * @param listener
     */
    public void addSessionStateListener(SessionStateListener listener) {
        this.listeners.add(listener);
    }

    /**
     * 移除状态监听
     *
     * @param listener
     */
    public void removeSessionStateListener(SessionStateListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * 关闭Session
     */
    public void close() {
        if (state != SessionState.CLOSED) {
            changeState(SessionState.CLOSED);
            if (channel.isActive()) {
                channel.attr(SESSION_KEY).set(null);
                channel.close().addListener(f -> logger.info("Close session[{}] success.", sessionId));
            }
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionState getState() {
        return state;
    }

    public long getLastStateChangeTime() {
        return lastStateChangeTime;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * 状态变化回调
     *
     * @param oldState 旧状态
     * @param newState 新状态
     */
    private void onStateChanged(SessionState oldState, SessionState newState) {
        // 停止旧状态的定时器
        // stopAllTimers();
        //
        // // 启动新状态需要的定时器
        // switch (newState) {
        //     case CONNECTED:
        //         startAuthenticationTimeoutTimer();
        //         break;
        //     case AUTHENTICATED:
        //     case ACTIVE:
        //         startIdleCheckTimer();
        //         startHeartbeatTimer();
        //         break;
        //     case IDLE:
        //         startIdleCheckTimer(); // 更频繁地检查是否恢复活动
        //         break;
        //     case DISCONNECTED:
        //         startSessionCleanupTimer();
        //         break;
        // }
        //
        // 通知监听器
        notifyStateListeners(oldState, newState);
    }

    private void notifyStateListeners(SessionState oldState, SessionState newState) {
        if (listeners.isEmpty()) {
            return;
        }

        listeners.forEach(l -> l.onStateChanged(this, oldState, newState));
    }

    private boolean isWritableState() {
        return state == SessionState.AUTHENTICATED || state == SessionState.ACTIVE || state == SessionState.IDLE;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ClientSession that = (ClientSession) object;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(channel, that.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, channel);
    }

    @Override
    public String toString() {
        return "ClientSession{" +
                "state=" + state +
                ", lastStateChangeTime=" + lastStateChangeTime +
                ", sessionId='" + sessionId + '\'' +
                ", clientAddress=" + clientAddress +
                ", lastActivityTime=" + lastActivityTime +
                ", writeMsgCnt=" + writeMsgCnt +
                '}';
    }
}
