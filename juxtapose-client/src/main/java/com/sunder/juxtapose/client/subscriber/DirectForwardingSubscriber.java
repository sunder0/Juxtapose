package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author : denglinhai
 * @date : 17:52 2023/6/21
 *         直接转发请求组件
 */
public class DirectForwardingSubscriber extends BaseComponent<ProxyCoreComponent> implements ProxyRequestSubscriber,
        ProxyMessageReceiver {
    public final static String NAME = "DIRECT_FORWARDING_SUBSCRIBER";

    private final Bootstrap bootstrap;
    private Map<Long, SocketChannel> relayChannel = new ConcurrentHashMap<>();

    public DirectForwardingSubscriber(ProxyCoreComponent parent) {
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
    public void subscribe(ProxyRequest request) {
        bootstrap.clone().handler(new DirectForwardingHandler(request))
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
        SocketChannel channel;
        if ((channel = relayChannel.get(serialId)) != null) {
            channel.writeAndFlush(message);
        }
    }

    /**
     * 直接请求处理器，仅转发，不做任何处理
     */
    private class DirectForwardingHandler extends ChannelInboundHandlerAdapter {
        private final ProxyRequest request;

        public DirectForwardingHandler(ProxyRequest request) {
            this.request = request;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            relayChannel.putIfAbsent(request.getSerialId(), (SocketChannel) ctx.channel());
            request.setProxyMessageReceiver(DirectForwardingSubscriber.this);

            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                request.returnMessage(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
        }
    }

    @Override
    public boolean isProxy() {
        return false;
    }

}
