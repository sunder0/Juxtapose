package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.handler.RelayMessageWriteEncoder;
import com.sunder.juxtapose.common.mesage.AuthRequestMessage;
import com.sunder.juxtapose.common.mesage.AuthResponseMessage;
import com.sunder.juxtapose.common.mesage.Message;
import com.sunder.juxtapose.common.mesage.PingMessage;
import com.sunder.juxtapose.common.mesage.PongMessage;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.common.mesage.ProxyResponseMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 15:38 2023/7/5
 */
public class JuxtaProxyRequestSubscriber extends BaseCompositeComponent<ProxyCoreComponent>
        implements ProxyRequestSubscriber, ProxyMessageReceiver {
    public final static String NAME = "JUXTA_PROXY_SERVER";

    private Class<? extends SocketChannel> socketChannel;
    private EventLoopGroup eventLoopGroup;

    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private SocketChannel relayChannel; // 和中继服务器通信的channel
    private final Map<Long, ProxyRequest> activeProxy = new ConcurrentHashMap<>(16); // 活跃的代理

    public JuxtaProxyRequestSubscriber(ProxyServerNodeConfig cfg, CertComponent certComponent,
            ProxyCoreComponent parent) {
        super(NAME + "_" + cfg.host + ":" + cfg.port, Objects.requireNonNull(parent),
                ComponentLifecycleListener.INSTANCE);
        this.cfg = cfg;
        this.certComponent = certComponent;

        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        this.socketChannel = Platform.socketChannelClass();
        this.eventLoopGroup = Platform.createEventLoopGroup(2);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(socketChannel)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    if (cfg.tls) {
                        pipeline.addLast(
                                certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.host, cfg.port));
                    }
                    pipeline.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                            Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                    pipeline.addLast(new ProxyRelayMessageHandler());
                    pipeline.addLast(RelayMessageWriteEncoder.INSTANCE);
                }
            }).connect(cfg.host, cfg.port).await().addListener(f -> {
                if (f.isSuccess()) {
                    logger.info("Connect Juxta proxy relay server[{}:{}] successful!", cfg.host, cfg.port);
                } else {
                    logger.info("Connect Juxta proxy relay server[{}:{}] failed!", cfg.host, cfg.port, f.cause());
                }
            });
        } catch (Exception ex) {
            throw new ComponentException("Start ProxyRelayServerComponent failed!", ex);
        }

        super.startInternal();
    }

    @Override
    protected void destroyInternal() {
        parent.removeProxyRequestSubscriber(this);

        super.destroyInternal();
    }

    @Override
    public void subscribe(ProxyRequest request) {
        this.activeProxy.put(request.getSerialId(), request);

        request.setProxyMessageReceiver(this);
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        ProxyRequest request = activeProxy.get(serialId);
        ProxyRequestMessage proxyMessage = new ProxyRequestMessage(
                serialId, request.getHost(), request.getPort(), message);

        SocketChannel socketChannel = JuxtaProxyRequestSubscriber.this.relayChannel;
        socketChannel.writeAndFlush(proxyMessage, socketChannel.newPromise());
    }

    /**
     * 与代理服务器通信
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (cfg.auth) {
                AuthRequestMessage message = new AuthRequestMessage(cfg.username, cfg.password);
                ctx.channel().writeAndFlush(message);
            } else {
                // nothing to do...
            }

            JuxtaProxyRequestSubscriber.this.relayChannel = (SocketChannel) ctx.channel();
            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                byte serviceId = byteBuf.getByte(byteBuf.readerIndex());
                if (serviceId == PingMessage.SERVICE_ID) {

                } else if (serviceId == PongMessage.SERVICE_ID) {

                } else if (serviceId == AuthResponseMessage.SERVICE_ID) {
                    AuthResponseMessage message = new AuthResponseMessage(byteBuf);
                    if (!message.isPassed()) {
                        logger.error("Proxy server[{}:{}] auth verify failed, errorMsg:[{}].", cfg.host, cfg.port,
                                message.getMessage());
                        ctx.close();
                        JuxtaProxyRequestSubscriber.this.destroy();
                    }

                } else if (serviceId == ProxyResponseMessage.SERVICE_ID) {
                    ProxyResponseMessage message = new ProxyResponseMessage(byteBuf);
                    logger.info("receive proxy server message...[{}]", message.getSerialId());
                    if (message.isSuccess()) {
                        activeProxy.get(message.getSerialId()).returnMessage(message.getContent());
                    } else {
                        // ignore....
                    }
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
            ctx.channel().close().addListener((ChannelFutureListener) channelFuture -> {
                JuxtaProxyRequestSubscriber.this.destroy();
            });
        }
    }

    @Override
    public String proxyUri() {
        return cfg.host + ":" + cfg.port;
    }


    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public ProxyProtocol proxyMode() {
        return ProxyProtocol.JUXTA;
    }
}
