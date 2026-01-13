package com.sunder.juxtapose.server.handler;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.mesage.ProxyResponseMessage;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import com.sunder.juxtapose.server.proxy.HttpProxyTaskPublisher.TunnelProxyHandler;
import com.sunder.juxtapose.server.proxy.JuxtaProxyTaskPublisher.ProxyRelayMessageHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connection.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // 60秒写空闲，超过视作关闭连接
        ctx.pipeline().addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            switch (event.state()) {
                case READER_IDLE:
                case WRITER_IDLE:
                    break;
                case ALL_IDLE:
                    handleAllIdle(ctx);
                    break;
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 处理读取写入都超时，默认与真实服务器断开
     *
     * @param ctx io.netty.channel.ChannelHandlerContext
     */
    private void handleAllIdle(ChannelHandlerContext ctx) {
        logger.info("Channel[{}] all idle timeout, will close channel...", ctx.channel().id());
        ctx.close();
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;

            if (request.getProtocol() == ProxyProtocol.SOCKS5 || request.getProtocol() == ProxyProtocol.HTTP) {
                handleHttpOrSocks5Message(ctx, byteBuf);
            } else if (request.getProtocol() == ProxyProtocol.JUXTA) {
                handleJuxtaMessage(ctx, byteBuf);
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * 处理juxta协议返回消息
     *
     * @param ctx
     * @param byteBuf
     */
    private void handleJuxtaMessage(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        ProxyResponseMessage message = new ProxyResponseMessage(request.getSerialId(), byteBuf);

        Channel channel = request.getClientChannel();
        if (channel.isWritable()) {
            connection.readMessage(message.serialize(ctx.alloc()));
        } else {
            if (channel.isActive()) {
                ProxyRelayMessageHandler handler = channel.pipeline().get(ProxyRelayMessageHandler.class);
                handler.writePendingWrites(channel, message.serialize(ctx.alloc()));
            } else {
                byteBuf.release();
                connection.close();
            }
        }
    }

    /**
     * 处理http、Socks5协议返回消息
     *
     * @param ctx
     * @param byteBuf
     */
    private void handleHttpOrSocks5Message(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        if (request.getProtocol() == ProxyProtocol.SOCKS5) {
            connection.readMessage(byteBuf);
            return;
        }

        Channel channel = request.getClientChannel();
        if (channel.isWritable()) {
            connection.readMessage(byteBuf);
        } else {
            if (channel.isActive()) {
                TunnelProxyHandler handler = channel.pipeline().get(TunnelProxyHandler.class);
                handler.writePendingWrites(channel, byteBuf);
            } else {
                byteBuf.release();
                connection.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        connection.close();
        logger.error("Real channel encountered an error[{}].", cause.getMessage(), cause);
        ctx.channel().close().addListener((ChannelFutureListener) channelFuture -> {
            logger.info("Real channel channel close[{}]...", ctx.channel().id());
        });
    }
}
