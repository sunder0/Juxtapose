package com.sunder.juxtapose.common.connection;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.BaseModule;
import com.sunder.juxtapose.common.Component;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author : sunder
 * @date : 17:10 2025/09/16
 */
public class DefaultConnectionManager<T extends Component<?>> extends BaseModule<T> implements ConnectionManager {
    public final static String NAME = "DEFAULT_CONNECTION_MANAGER";

    protected final Logger logger;
    private final ScheduledThreadPoolExecutor executor;
    // 存放connectionId->Connection的映射
    protected final Map<String, Connection> connectionMap = new ConcurrentHashMap<>(16);
    // channel -> TrafficHandlerInfo
    private final Map<Channel, TrafficHandlerInfo> trafficHandlers = new ConcurrentHashMap<>(16);
    // 存放connectionStats数据的定时报告监听
    private final List<Consumer<ConnectionStats>> listeners = new CopyOnWriteArrayList<>();

    public DefaultConnectionManager(T belongComponent) {
        super(NAME, belongComponent);
        this.logger = LoggerFactory.getLogger(DefaultConnectionManager.class);
        this.executor = new ScheduledThreadPoolExecutor(2,
                ThreadFactoryBuilder.create().setNamePrefix("ConnectionManage-").build());

        this.executor.scheduleAtFixedRate(this::maintainConnections, 5, 30, TimeUnit.SECONDS);
        this.executor.scheduleAtFixedRate(this::reportStats, 1, 1, TimeUnit.SECONDS);
    }

    public DefaultConnectionManager(String name, T belongComponent) {
        super(name, belongComponent);
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
                    removeConnection(connection.getConnectId());
                }
            }
        });

        return connection;
    }

    @Override
    public ChannelFuture closeConnection(String connectionId) {
        Connection connection = connectionMap.remove(connectionId);
        logger.info("Connection removed:[{}, {}], total:[{}]", connection.getConnectId(),
                connection.getProxyRequest().getHost(), connectionMap.size());

        return connection.close();
    }

    @Override
    public Connection removeConnection(String connectionId) {
        Connection connection = connectionMap.remove(connectionId);
        logger.info("Connection removed:[{}], total:[{}]", connection.getConnectId(), connectionMap.size());

        return connection;
    }

    @Override
    public Connection getConnection(String connectionId) {
        return connectionMap.get(connectionId);
    }

    @Override
    public List<Connection> getConnectionsByClientChannel(Channel clientChannel) {
        return connectionMap.values().stream()
                .filter(c -> c.getProxyChannel() != null && c.getProxyRequest().getClientChannel()
                        .equals(clientChannel))
                .collect(Collectors.toList());
    }

    @Override
    public List<Connection> getConnectionsByProxyChannel(Channel proxyChannel) {
        return connectionMap.values().stream()
                .filter(c -> c.getProxyChannel() != null && c.getProxyChannel().equals(proxyChannel))
                .collect(Collectors.toList());
    }

    @Override
    public boolean containsConnection(String connectionId) {
        return connectionMap.containsKey(connectionId);
    }

    @Override
    public Map<String, Connection> getActiveConnections() {
        return Collections.unmodifiableMap(connectionMap);
    }

    /**
     * 注册TrafficShapingHandler
     */
    public void registerTrafficHandler(Channel channel, ChannelTrafficShapingHandler handler) {
        trafficHandlers.put(channel, new TrafficHandlerInfo(channel, handler));
    }

    /**
     * 注销TrafficShapingHandler
     */
    public TrafficHandlerInfo unregisterTrafficHandler(Channel channel) {
        return trafficHandlers.remove(channel);
    }

    /**
     * 添加对stats数据的报告监听
     *
     * @param listener java.util.function.Consumer
     */
    public void addConnectionStatsListener(Consumer<ConnectionStats> listener) {
        this.listeners.add(listener);
    }

    /**
     * 维护连接，清理无效的(空闲+已关闭或报错的)
     */
    private void maintainConnections() {
        // todo: 修改成监听模式
        int cleaned = 0;
        long now = System.currentTimeMillis();
        for (Connection connection : connectionMap.values()) {
            // 清理空闲连接
            ConnectionStats stats = connection.getStats();
            if (now - stats.getLastActivityTime() > TimeUnit.MINUTES.toMillis(5)) {
                connection.close();
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
        for (TrafficHandlerInfo handlerInfo : trafficHandlers.values()) {
            if (!handlerInfo.getChannel().isActive()) {
                continue;
            }
            TrafficCounter counter = handlerInfo.getTrafficShapingHandler().trafficCounter();

            totalStats.setBytesUploaded(totalStats.getBytesUploaded() + (counter.cumulativeWrittenBytes()
                    - handlerInfo.getLastCumulativeWrittenBytes()));
            totalStats.setBytesDownloaded(totalStats.getBytesDownloaded() + (counter.cumulativeReadBytes()
                    - handlerInfo.getLastCumulativeReadBytes()));
            handlerInfo.setLastCumulativeWrittenBytes(counter.cumulativeWrittenBytes());
            handlerInfo.setLastCumulativeReadBytes(counter.cumulativeReadBytes());
        }

        // 发布统计信息（用于UI显示）
        for (Consumer<ConnectionStats> listener : listeners) {
            listener.accept(totalStats);
        }
    }

}
