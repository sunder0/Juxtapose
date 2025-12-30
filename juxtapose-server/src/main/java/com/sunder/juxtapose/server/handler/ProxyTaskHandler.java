package com.sunder.juxtapose.server.handler;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.mesage.ProxyResponseMessage;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : sunder
 * @date : 12:02 2023/7/14
 *         连上目标服务器的代理任务的数据处理handler
 */
public class ProxyTaskHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger;
    private final ProxyRequest request;
    private final Connection connection;

    public ProxyTaskHandler(ProxyRequest request, Connection connection) {
        this.request = request;
        this.connection = connection;
        this.logger = LoggerFactory.getLogger(ProxyTaskRequest.class);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            if (request.getProtocol() == ProxyProtocol.SOCKS5 || request.getProtocol() == ProxyProtocol.HTTP) {
                connection.readMessage(byteBuf);
            } else if (request.getProtocol() == ProxyProtocol.JUXTA) {
                ProxyResponseMessage message = new ProxyResponseMessage(request.getSerialId(), byteBuf);
                connection.readMessage(message.serialize(ctx.alloc()));
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        connection.close();
    }
}
