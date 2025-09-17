package com.sunder.juxtapose.client.connection;

import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.common.ProxyProtocol;
import io.netty.channel.ChannelFuture;

import java.util.Map;

/**
 * @author : denglinhai
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
     * 获取连接
     *
     * @param connectionId
     * @return
     */
    Connection getConnection(String connectionId);

    /**
     * @return 获取活跃连接
     */
    Map<String, Connection> getActiveConnections();

}
