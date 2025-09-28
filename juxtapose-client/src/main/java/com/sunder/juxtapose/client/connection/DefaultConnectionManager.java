package com.sunder.juxtapose.client.connection;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.SystemAppContext;
import com.sunder.juxtapose.common.BaseModule;
import com.sunder.juxtapose.common.ProxyProtocol;
import io.netty.channel.ChannelFuture;
import io.netty.handler.traffic.TrafficCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : denglinhai
 * @date : 17:10 2025/09/16
 */
public class DefaultConnectionManager extends BaseModule<ProxyCoreComponent> implements ConnectionManager {
    public final static String NAME = "DEFAULT_CONNECTION_MANAGER";

    private final Logger logger;
    private final ScheduledThreadPoolExecutor executor;
    // 存放sessionId->session的映射
    private final Map<String, Connection> connectionMap = new ConcurrentHashMap<>(16);

    public DefaultConnectionManager(ProxyCoreComponent belongComponent) {
        super(NAME, belongComponent);
        this.logger = LoggerFactory.getLogger(DefaultConnectionManager.class);
        this.executor = new ScheduledThreadPoolExecutor(2,
                ThreadFactoryBuilder.create().setNamePrefix("ConnectionManage-").build());

        this.executor.scheduleAtFixedRate(this::maintainConnections, 5, 60, TimeUnit.SECONDS);
        this.executor.scheduleAtFixedRate(this::reportStats, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public Connection createConnection(ProxyProtocol protocol, ProxyRequest request) {
        ProxyConnection connection = new ProxyConnection(protocol, request);
        connectionMap.put(request.getSerialId().toString(), connection);
        logger.info("Connection added:[{}], total:[{}]", connection.getConnectId(), connectionMap.size());

        // 注册关闭监听
        connection.addConnectionStateListener(new ConnectionStateListener() {
            @Override
            public void onStateChanged(Connection connection, ConnectionState oldState, ConnectionState newState) {
                if (newState == ConnectionState.CLOSED) {
                    closeConnection(connection.getConnectId());
                }
            }
        });

        return connection;
    }

    @Override
    public ChannelFuture closeConnection(String connectionId) {
        Connection connection = connectionMap.remove(connectionId);
        logger.info("Connection removed:[{}], total:[{}]", connection.getConnectId(), connectionMap.size());

        return connection.close();
    }

    @Override
    public Connection getConnection(String connectionId) {
        return connectionMap.get(connectionId);
    }

    @Override
    public Map<String, Connection> getActiveConnections() {
        return Collections.unmodifiableMap(connectionMap);
    }

    /**
     * 维护连接，清理无效的(空闲+已关闭或报错的)
     */
    private void maintainConnections() {
        int cleaned = 0;
        long now = System.currentTimeMillis();
        for (Connection connection : connectionMap.values()) {
            // 清理超时或无效连接
            if (connection.getState() == ConnectionState.CLOSED || connection.getState() == ConnectionState.ERROR) {
                closeConnection(connection.getConnectId());
                cleaned++;
            }

            // 清理空闲连接
            ConnectionStats stats = connection.getStats();
            if (now - stats.getLastActivityTime() > TimeUnit.HOURS.toMillis(1)) {
                closeConnection(connection.getConnectId());
                cleaned++;
            }

            // 清理长时间未认证的连接
            if (connection.getState() == ConnectionState.CONNECTED &&
                    (now - stats.getLastActivityTime()) > TimeUnit.MINUTES.toMillis(1)) {
                closeConnection(connection.getConnectId());
                logger.warn("Connection[{}] authentication timeout, closing", connection.getConnectId());
                cleaned++;
            }

        }

        if (cleaned > 0) {
            logger.info("Cleaned up {} invalid connection...", cleaned);
        }
    }

    /**
     * 报告连接的状态, 发布给ui展示
     */
    private void reportStats() {
        ConnectionStats totalStats = new ConnectionStats();
        for (Connection conn : connectionMap.values()) {
            TrafficCounter counter = conn.getTrafficCounter();
            if (counter == null) {
                continue;
            }
            totalStats.setBytesUploaded(totalStats.getBytesUploaded() + counter.lastReadBytes());
            totalStats.setBytesDownloaded(totalStats.getBytesDownloaded() + counter.lastWrittenBytes());
        }

        // 发布统计信息（用于UI显示）
        SystemAppContext.CONTEXT.setUploadBytes(totalStats.getBytesUploaded());
        SystemAppContext.CONTEXT.setDownloadBytes(totalStats.getBytesDownloaded());
    }

}
