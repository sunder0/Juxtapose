package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.ProxyServerNodeManager;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.connection.Connection;
import com.sunder.juxtapose.client.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.BaseComponent;
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
import io.netty.util.ReferenceCountUtil;

import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 15:38 2023/7/5
 */
public class JuxtaProxyRequestSubscriber extends BaseComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver {
    public final static String NAME = "JUXTA_PROXY_SERVER";

    private Class<? extends SocketChannel> socketChannel;
    private EventLoopGroup eventLoopGroup;

    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private DefaultConnectionManager connManager;
    private SocketChannel relayChannel; // 和中继服务器通信的channel

    public JuxtaProxyRequestSubscriber(ProxyServerNodeConfig cfg, CertComponent certComponent,
            ProxyServerNodeManager parent) {
        super(cfg.name, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        this.cfg = cfg;
        this.certComponent = certComponent;

        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        connManager = getModuleByName(DefaultConnectionManager.NAME, true, DefaultConnectionManager.class);

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
                                certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.server, cfg.port));
                    }
                    pipeline.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                            Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                    pipeline.addLast(new ProxyRelayMessageHandler());
                    pipeline.addLast(RelayMessageWriteEncoder.INSTANCE);
                }
            }).connect(cfg.server, cfg.port).await().addListener(f -> {
                if (f.isSuccess()) {
                    logger.info("Connect Juxta proxy relay server[{}:{}] successful!", cfg.server, cfg.port);
                } else {
                    logger.info("Connect Juxta proxy relay server[{}:{}] failed!", cfg.server, cfg.port, f.cause());
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
        Connection connection = connManager.createConnection(ProxyProtocol.JUXTA, request);

        connection.bindProxyChannel(relayChannel);
        connection.activeMessageTransfer(this);
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        Connection connection = connManager.getConnection(serialId.toString());

        ProxyRequestMessage proxyMessage = new ProxyRequestMessage(
                serialId, connection.getContent().getProxyHost(), connection.getContent().getProxyPort(), message);
        connection.writeMessage(proxyMessage);
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

                switch (serviceId) {
                    case PingMessage.SERVICE_ID:
                    case PongMessage.SERVICE_ID:
                        break;
                    case AuthResponseMessage.SERVICE_ID:
                        handleAuthResponseMessage(ctx, new AuthResponseMessage(byteBuf));
                        break;
                    case ProxyResponseMessage.SERVICE_ID:
                        handleProxyResponseMessage(ctx, new ProxyResponseMessage(byteBuf));
                        break;
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        private void handleAuthResponseMessage(ChannelHandlerContext ctx, AuthResponseMessage message) {
            if (!message.isPassed()) {
                logger.error("Proxy server[{}:{}] auth verify failed, errorMsg:[{}].", cfg.server, cfg.port,
                        message.getMessage());
                ctx.close();
                JuxtaProxyRequestSubscriber.this.destroy();
            }
        }

        private void handleProxyResponseMessage(ChannelHandlerContext ctx, ProxyResponseMessage message) {
            logger.debug("receive proxy server message...[{}]", message.getSerialId());
            if (message.isSuccess()) {
                DefaultConnectionManager connManager = JuxtaProxyRequestSubscriber.this.connManager;
                Connection connection = connManager.getConnection(message.getSerialId().toString());
                if (connection != null) {
                    connection.readMessage(message.getContent());
                } else {
                    ReferenceCountUtil.release(message.getContent());
                }
            } else {
                ReferenceCountUtil.release(message.getContent());
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
        return cfg.server + ":" + cfg.port;
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
