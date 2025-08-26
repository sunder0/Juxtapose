package com.sunder.juxtapose.server.handler;

import com.sunder.juxtapose.common.mesage.ProxyResponseMessage;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 12:02 2023/7/14
 *         连上目标服务器的代理任务的数据处理handler
 */
public class ProxyTaskHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger;
    private final ProxyTaskRequest request;

    public ProxyTaskHandler(ProxyTaskRequest request) {
        this.request = request;
        this.logger = LoggerFactory.getLogger(ProxyTaskRequest.class);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            ProxyResponseMessage message = new ProxyResponseMessage(request.getMessage().getSerialId(), byteBuf);
            request.getClientChannel().writeAndFlush(message);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
    }
}
