package com.sunder.juxtapose.client.handler;

import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.ConnectionManager;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : sunder
 * @date : 16:18 2023/6/21
 */
public class TcpProxyMessageHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(TcpProxyMessageHandler.class);
    private final String connectId;
    private final ProxyRequest proxyRequest;
    private final ConnectionManager connectionManager;

    public TcpProxyMessageHandler(ProxyRequest request, ConnectionManager connectionManager) {
        this.connectId = request.getSerialId().toString();
        this.proxyRequest = request;
        this.connectionManager = connectionManager;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Connection connection = connectionManager.getConnection(connectId);
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            try {
                proxyRequest.transferMessage(byteBuf.retain());
            } finally {
                byteBuf.release();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Socks5 client channel encountered an error[{}].", cause.getMessage(), cause);
        Connection connection = connectionManager.getConnection(connectId);
        if (connection != null) {
            connection.close();
        }
    }

}
