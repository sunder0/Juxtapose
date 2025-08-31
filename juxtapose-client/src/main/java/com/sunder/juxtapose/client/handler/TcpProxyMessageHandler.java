package com.sunder.juxtapose.client.handler;

import com.sunder.juxtapose.client.ProxyRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 16:18 2023/6/21
 */
public class TcpProxyMessageHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(TcpProxyMessageHandler.class);
    private final ProxyRequest proxyRequest;

    public TcpProxyMessageHandler(ProxyRequest request) {
        this.proxyRequest = request;
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
        logger.error(cause.getMessage(), cause);
    }

}
