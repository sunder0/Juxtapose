package com.sunder.juxtapose.common.connection;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.mesage.ProxyCloseMessage;
import com.sunder.juxtapose.common.proxy.ProxyMessageReceiver;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : sunder
 * @date : 17:09 2025/09/16
 *         默认的代理连接，由两个channel组成，客户端channel和其对应的代理channel，两个都是active才算connection正常
 */
public class ProxyConnection implements Connection {
    public static final AttributeKey<ProxyConnection> CONNECT_KEY = AttributeKey.valueOf("CONNECTION");

    protected final Logger logger;
    protected final Lock lock = new ReentrantLock();

    protected Long connectId;
    protected ProxyProtocol protocol;
    protected volatile ConnectionState state;
    protected ProxyRequest proxyRequest; // 包含代理请求信息和连接用户端的channel
    protected SocketChannel proxyChannel; // 连接代理服务器channel
    private ConnectionContent connectionContent; // 连接内容信息
    private ConnectionStats connectionStats; // 连接统计信息
    private TrafficCounter trafficCounter; // 流量统计
    private List<ConnectionStateListener> listeners = new CopyOnWriteArrayList<>();

    public ProxyConnection(ProxyProtocol protocol, ProxyRequest proxyRequest) {
        this.protocol = protocol;
        this.logger = LoggerFactory.getLogger(ProxyConnection.class);
        this.proxyRequest = Objects.requireNonNull(proxyRequest);
        this.connectId = proxyRequest.getSerialId();
        this.state = ConnectionState.INIT;
        this.connectionStats = new ConnectionStats();

        this.connectionContent = new ConnectionContent(protocol, proxyRequest.getClientChannel().remoteAddress(),
                proxyRequest.getHost(), proxyRequest.getPort());
    }

    @Override
    public boolean changeState(ConnectionState newState) {
        try {
            lock.lock();
            if (!state.after(newState)) {
                logger.warn("Invalid state transition: {} -> {} for session {}", state, newState, connectId);
                return false;
            }

            ConnectionState oldState = state;
            state = newState;
            connectionStats.setLastStateChangeTime(System.currentTimeMillis());

            logger.info("Connection {} state changed: {} -> {}.", connectId, oldState, newState);
            onStateChanged(oldState, newState);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void bindProxyChannel(SocketChannel channel) {
        try {
            lock.lock();
            this.proxyChannel = channel;
            changeState(ConnectionState.CONNECTED);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void bindTrafficCounter(TrafficCounter trafficCounter) {
        this.trafficCounter = trafficCounter;
    }

    @Override
    public void activeMessageTransfer(ProxyMessageReceiver receiver) {
        boolean bind = (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED)
                && proxyChannel != null;
        if (!bind) {
            logger.error("No proxy channel is bound, will been close connection...");
            close();
            return;
        }
        this.proxyRequest.setProxyMessageReceiver(receiver);
        changeState(ConnectionState.READY);
    }

    @Override
    public ChannelFuture writeMessage(Object message) {
        if (proxyChannel.isActive() && isWritableState()) {
            return proxyChannel.writeAndFlush(message);
        } else {
            logger.warn("Attempted client -> proxy node send message in non-writable state: {}, channel active: {}",
                    state, proxyChannel.isActive());
            if (!proxyChannel.isActive()) {
                logger.info("Proxy channel closed, terminating the connection[{}].", connectId);
                closeForce();
            }
            if (message instanceof ByteBuf) {
                ReferenceCountUtil.release(message);
            }
            return null;
        }
    }

    @Override
    public ChannelFuture readMessage(Object message) {
        if (proxyRequest.isActive() && isReadableState()) {
            updateActivityTime();
            return proxyRequest.returnMessage(message);
        } else {
            logger.warn("Attempted proxy node -> client send message in non-writable state: {}, channel active: {}",
                    state, proxyRequest.isActive());
            if (!proxyRequest.isActive()) {
                logger.info("Client channel closed, terminating the connection[{}].", connectId);
                close();
            }
            if (message instanceof ByteBuf) {
                ReferenceCountUtil.release(message);
            }
            return null;
        }
    }

    @Override
    public void updateActivityTime() {
        connectionStats.setLastActivityTime(System.currentTimeMillis());
        // 如果当前是IDLE或者AUTHENTICATED状态，有活动时恢复为ACTIVE状态
        if (state == ConnectionState.IDLE || state == ConnectionState.AUTHENTICATED
                || state == ConnectionState.CONNECTED || state == ConnectionState.READY) {
            changeState(ConnectionState.ACTIVE);
        }
    }

    @Override
    public ChannelFuture close() {
        try {
            lock.lock();
            if (state != ConnectionState.CLOSED) {
                changeState(ConnectionState.CLOSED);
                // 通知对端对应的连接关闭
                if (proxyChannel != null && proxyChannel.isActive()) {
                    if (protocol == ProxyProtocol.JUXTA) {
                        proxyChannel.writeAndFlush(new ProxyCloseMessage(connectId));
                    }
                    if (protocol == ProxyProtocol.DIRECT) {
                        proxyChannel.close();
                    }
                }
                return proxyRequest.close().addListener(
                        f -> logger.info("Close connection[{}] success.", connectId));
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public ChannelFuture closeForce() {
        try {
            lock.lock();
            if (state != ConnectionState.CLOSED) {
                changeState(ConnectionState.CLOSED);
                proxyRequest.close();
                if (proxyChannel != null && proxyChannel.isActive()) {
                    return proxyChannel.close().addListener(f ->
                            logger.info("Close connection[{}] success.", connectId));
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public boolean addConnectionStateListener(ConnectionStateListener listener) {
        return listeners.add(listener);
    }

    @Override
    public boolean removeConnectionStateListener(ConnectionStateListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public String getConnectId() {
        return connectId.toString();
    }

    @Override
    public ConnectionState getState() {
        return state;
    }

    @Override
    public ConnectionStats getStats() {
        return connectionStats;
    }

    @Override
    public ConnectionContent getContent() {
        return connectionContent;
    }

    @Override
    public TrafficCounter getTrafficCounter() {
        return trafficCounter;
    }

    @Override
    public ProxyRequest getProxyRequest() {
        return proxyRequest;
    }

    @Override
    public Channel getProxyChannel() {
        return proxyChannel;
    }

    /**
     * 状态变化回调
     *
     * @param oldState 旧状态
     * @param newState 新状态
     */
    private void onStateChanged(ConnectionState oldState, ConnectionState newState) {
        // 通知监听器
        notifyStateListeners(oldState, newState);
    }

    private void notifyStateListeners(ConnectionState oldState, ConnectionState newState) {
        if (listeners.isEmpty()) {
            return;
        }

        listeners.forEach(l -> l.onStateChanged(this, oldState, newState));
    }

    protected boolean isReadableState() {
        return state == ConnectionState.AUTHENTICATED || state == ConnectionState.ACTIVE
                || state == ConnectionState.IDLE || state == ConnectionState.CONNECTED
                || state == ConnectionState.READY;
    }

    protected boolean isWritableState() {
        return state == ConnectionState.AUTHENTICATED || state == ConnectionState.ACTIVE
                || state == ConnectionState.IDLE || state == ConnectionState.CONNECTED
                || state == ConnectionState.READY;
    }

}
