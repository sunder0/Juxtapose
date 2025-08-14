package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 15:38 2023/7/5
 */
public class ProxyRelayServerComponent extends BaseCompositeComponent<ProxyCoreComponent>
        implements ProxyRequestSubscriber, ProxyMessageReceiver {
    public final static String NAME = "PROXY_REPLAY_SERVER_COMPONENT";

    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private SocketChannel relayChannel; // 和中继服务器通信的channel
    private final Map<Long, ProxyRequest> activeProxy = new ConcurrentHashMap<>(16); // 活跃的代理

    public ProxyRelayServerComponent(ProxyServerNodeConfig cfg, ProxyCoreComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        this.cfg = cfg;

        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        this.certComponent = new CertComponent(this);
        addChildComponent(certComponent);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(new NioEventLoopGroup(2))
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addLast(
                            certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.host, cfg.port));
                    pipeline.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                            Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                    pipeline.addLast(new ProxyRelayMessageHandler());
                    pipeline.addLast(RelayMessageWriteEncoder.INSTANCE);
                }
            }).connect(cfg.host, cfg.port).await().addListener(f -> {
                if (f.isSuccess()) {
                    logger.info("Connect proxy relay server[{}:{}] successful!", cfg.host, cfg.port);
                } else {
                    logger.info("Connect proxy relay server[{}:{}] failed!", cfg.host, cfg.port, f.cause());
                }
            });
        } catch (Exception ex) {
            throw new ComponentException("Start ProxyRelayServerComponent failed!", ex);
        }

        super.startInternal();
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

        SocketChannel socketChannel = ProxyRelayServerComponent.this.relayChannel;
        socketChannel.writeAndFlush(proxyMessage, socketChannel.newPromise());
    }

    /**
     * 与代理服务器通信
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (AuthenticationStrategy.SIMPLE.equals(cfg.auth)) {
                AuthRequestMessage message = new AuthRequestMessage(cfg.userName, cfg.password);
                ctx.writeAndFlush(message);
            } else {
                // nothing to do...
            }

            ProxyRelayServerComponent.this.relayChannel = (SocketChannel) ctx.channel();
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
                        ProxyRelayServerComponent.this.destroy();
                    }

                } else if (serviceId == ProxyResponseMessage.SERVICE_ID) {
                    ProxyResponseMessage message = new ProxyResponseMessage(byteBuf);
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
        }
    }

    public String getHost() {
        return cfg.host;
    }

    public Integer getPort() {
        return cfg.port;
    }

}
