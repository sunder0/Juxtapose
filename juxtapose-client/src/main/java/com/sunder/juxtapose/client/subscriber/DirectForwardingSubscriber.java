package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.ProxyServerNodeManager;
import com.sunder.juxtapose.client.connection.Connection;
import com.sunder.juxtapose.client.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ProxyProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

import java.util.Objects;


/**
 * @author : denglinhai
 * @date : 17:52 2023/6/21
 *         直接转发请求组件
 */
public class DirectForwardingSubscriber extends BaseComponent<ProxyServerNodeManager> implements ProxyRequestSubscriber,
        ProxyMessageReceiver {
    public final static String NAME = "DIRECT_FORWARDING_SUBSCRIBER";

    private final Bootstrap bootstrap;
    private DefaultConnectionManager connManager;

    public DirectForwardingSubscriber(ProxyServerNodeManager parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(2))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000);
        this.bootstrap = bootstrap;

        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        connManager = getModuleByName(DefaultConnectionManager.NAME, true, DefaultConnectionManager.class);
    }

    @Override
    public void subscribe(ProxyRequest request) {
        Connection connection = connManager.createConnection(ProxyProtocol.DIRECT, request);
        bootstrap.clone().handler(new DirectForwardingHandler(connection))
                .connect(request.getHost(), request.getPort()).addListener(f -> {
                    if (f.isSuccess()) {
                        logger.info("Direct connect address[{}:{}] successful!", request.getHost(), request.getPort());
                    } else {
                        logger.info("Direct connect address[{}:{}] failed!", request.getHost(), request.getPort(), f.cause());
                    }
                });
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        Connection connection;
        if ((connection = connManager.getConnection(serialId.toString())) != null) {
            connection.writeMessage(message);
        }
    }

    /**
     * 直接请求处理器，仅转发，不做任何处理
     */
    private class DirectForwardingHandler extends ChannelInboundHandlerAdapter {
        private final Connection connection;

        public DirectForwardingHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            connection.bindProxyChannel((SocketChannel) ctx.channel());

            ChannelTrafficShapingHandler trafficHandler = new ChannelTrafficShapingHandler(1000);
            ctx.pipeline().addLast(trafficHandler);
            connection.bindTrafficCounter(trafficHandler.trafficCounter());

            connection.activeMessageTransfer(DirectForwardingSubscriber.this);

            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                connection.readMessage(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
            connection.close();
        }
    }

    @Override
    public boolean isProxy() {
        return false;
    }

}
