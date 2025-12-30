package com.sunder.juxtapose.server.connection;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.ConnectionState;
import com.sunder.juxtapose.common.connection.ConnectionStateListener;
import com.sunder.juxtapose.common.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.server.ProxyCoreComponent;

/**
 * @author : sunder
 * @date : 23:41 2025/12/29
 */
public class UpstreamConnectionManager extends DefaultConnectionManager<ProxyCoreComponent> {
    public final static String NAME = "UPSTREAM_CONNECTION_MANAGER";

    public UpstreamConnectionManager(ProxyCoreComponent belongComponent) {
        super(NAME, belongComponent);
    }

    @Override
    public UpstreamConnection createConnection(ProxyProtocol protocol, ProxyRequest request) {
        UpstreamConnection connection = new UpstreamConnection(protocol, request);
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
}
