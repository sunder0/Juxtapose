package com.sunder.juxtapose.common.connection;


import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.List;
import java.util.Map;

/**
 * @author : sunder
 * @date : 16:31 2025/09/16
 */
public interface ConnectionManager {
    /**
     * 创建新连接
     *
     * @param request com.sunder.juxtapose.client.ProxyRequest
     * @return com.sunder.juxtapose.client.connection.ProxyConnection
     */
    Connection createConnection(ProxyProtocol protocol, ProxyRequest request);

    /**
     * 关闭连接
     *
     * @param connectionId 连接id
     * @return
     */
    ChannelFuture closeConnection(String connectionId);

    /**
     * 移除连接
     *
     * @param connectionId 连接id
     * @return
     */
    Connection removeConnection(String connectionId);

    /**
     * 获取连接
     *
     * @param connectionId
     * @return
     */
    Connection getConnection(String connectionId);

    /**
     * 根据client channel获取与其关联的connections
     *
     * @param clientChannel 客户端channel
     * @return
     */
    List<Connection> getConnectionsByClientChannel(Channel clientChannel);

    /**
     * 根据client channel获取与其关联的connections
     *
     * @param proxyChannel 代理channel
     * @return
     */
    List<Connection> getConnectionsByProxyChannel(Channel proxyChannel);

    /**
     * 是否存在连接
     *
     * @param connectionId
     * @return
     */
    boolean containsConnection(String connectionId);

    /**
     * @return 获取活跃连接
     */
    Map<String, Connection> getActiveConnections();

}
