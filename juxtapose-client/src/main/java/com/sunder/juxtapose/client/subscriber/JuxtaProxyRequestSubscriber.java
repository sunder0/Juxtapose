package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
import com.sunder.juxtapose.common.pool.FixedChannelPool;
import com.sunder.juxtapose.client.ProxyServerNodeManager;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.group.ProxyNodeLatencyTest;
import com.sunder.juxtapose.client.group.ProxyServerUrlTestVisitor;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.handler.RelayMessageWriteEncoder;
import com.sunder.juxtapose.common.mesage.AuthRequestMessage;
import com.sunder.juxtapose.common.mesage.AuthResponseMessage;
import com.sunder.juxtapose.common.mesage.Message;
import com.sunder.juxtapose.common.mesage.PingMessage;
import com.sunder.juxtapose.common.mesage.PongMessage;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.common.mesage.ProxyResponseMessage;
import com.sunder.juxtapose.common.proxy.ProxyMessageReceiver;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.common.proxy.ProxyRequestSubscriber;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.ReferenceCountUtil;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author : sunder
 * @date : 15:38 2023/7/5
 */
public class JuxtaProxyRequestSubscriber extends BaseComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver, ProxyNodeLatencyTest {
    public final static String NAME = "JUXTA_PROXY_SERVER";

    private Bootstrap bootstrap;
    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private DefaultConnectionManager connManager;
    private FixedChannelPool fixedChannelPool;

    public JuxtaProxyRequestSubscriber(ProxyServerNodeConfig cfg, CertComponent certComponent,
            ProxyServerNodeManager parent) {
        super(cfg.name, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        this.cfg = cfg;
        this.certComponent = certComponent;

        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(Platform.createEventLoopGroup(2))
                .channel(Platform.socketChannelClass())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new ChannelTrafficShapingHandler(1000));
                if (cfg.tls) {
                    pipeline.addLast(
                            certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.server, cfg.port));
                }
                pipeline.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                        Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                pipeline.addLast(RelayMessageWriteEncoder.INSTANCE);
            }
        });

        this.bootstrap = bootstrap;
        this.connManager = getModuleByName(DefaultConnectionManager.NAME, true, DefaultConnectionManager.class);
        this.fixedChannelPool = new JuxtaFixedChannelPool(bootstrap.config().group(), 1, 10 * 1_000);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        cfg.latency = testLatency();
    }

    @Override
    protected void destroyInternal() {
        parent.removeProxyRequestSubscriber(this);

        super.destroyInternal();
    }

    @Override
    public Connection subscribe(ProxyRequest request) {
        try {
            Connection connection = connManager.createConnection(ProxyProtocol.JUXTA, request);
            fixedChannelPool.acquire(request, connection).thenAccept(new Consumer<Channel>() {
                @Override
                public void accept(Channel channel) {
                    connection.bindProxyChannel((SocketChannel) channel);
                    connection.activeMessageTransfer(JuxtaProxyRequestSubscriber.this);
                }
            }).exceptionally(ex -> {
                throw new ComponentException("Start ProxyRelayServerComponent failed!", ex);
            });

            return connection;
        } catch (Exception ex) {
            throw new ComponentException("Start ProxyRelayServerComponent failed!", ex);
        }
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        Connection connection = connManager.getConnection(serialId.toString());

        ProxyRequestMessage proxyMessage = new ProxyRequestMessage(
                serialId, connection.getContent().getProxyHost(), connection.getContent().getProxyPort(), message);
        connection.writeMessage(proxyMessage);
    }

    @Override
    public long testLatency() {
        ProxyServerUrlTestVisitor urlTestVisitor = parent.getUrlLatencyTestSupport();
        return urlTestVisitor.testUrl(this);
    }

    // /**
    //  * 心跳检测处理
    //  */
    // private class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    //
    //     public HeartbeatHandler() {
    //         //this.sessionManager = sessionManager;
    //     }
    //
    //     @Override
    //     public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    //         // 30秒写空闲，超过则发送一个心跳命令
    //         ctx.pipeline().addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
    //     }
    //
    //     @Override
    //     public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    //         if (evt instanceof IdleStateEvent) {
    //             IdleStateEvent event = (IdleStateEvent) evt;
    //
    //             switch (event.state()) {
    //                 case READER_IDLE:
    //                     handleReaderIdle(ctx);
    //                     break;
    //                 case WRITER_IDLE:
    //                 case ALL_IDLE:
    //                     break;
    //             }
    //         } else {
    //             super.userEventTriggered(ctx, evt);
    //         }
    //     }
    //
    //     /**
    //      * 处理读取超时，现默认客户端已经断开，降低内存使用
    //      *
    //      * @param ctx io.netty.channel.ChannelHandlerContext
    //      */
    //     private void handleReaderIdle(ChannelHandlerContext ctx) {
    //         String sessionId = ctx.channel().id().asShortText();
    //         ClientSession session = sessionManager.getSession(sessionId);
    //         session.close();
    //     }
    //
    // }

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

            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                byte serviceId = byteBuf.getByte(byteBuf.readerIndex());

                switch (serviceId) {
                    case PingMessage.SERVICE_ID:
                        new PingMessage(byteBuf);
                        break;
                    case PongMessage.SERVICE_ID:
                        new PongMessage(byteBuf);
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
                JuxtaProxyRequestSubscriber.this.destroy();
            } else {
                // nothing to do...
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

    /**
     * juxta协议的fixedChannelPool
     */
    private class JuxtaFixedChannelPool extends FixedChannelPool {

        public JuxtaFixedChannelPool(EventLoopGroup group, int maximumPoolSize, long keepAliveTime) {
            super(group, maximumPoolSize, keepAliveTime);
        }

        @Override
        protected ChannelFuture createNewChannel0(ProxyRequest request, Connection connection) {
            return bootstrap.clone().connect(cfg.server, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new ProxyRelayMessageHandler());
                    ChannelTrafficShapingHandler trafficHandler =
                            cf.channel().pipeline().get(ChannelTrafficShapingHandler.class);
                    connection.bindTrafficCounter(trafficHandler.trafficCounter());

                    logger.info("Connect Juxta proxy relay server[{}:{}] successful!", cfg.server, cfg.port);
                } else {
                    logger.info("Connect Juxta proxy relay server[{}:{}] failed!", cfg.server, cfg.port,
                            cf.cause());
                }
            });
        }
    }

    @Override
    public String proxyUri() {
        return cfg.server + ":" + cfg.port;
    }

    @Override
    public long proxyLatency() {
        return cfg.latency;
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public ProxyProtocol proxyProtocol() {
        return ProxyProtocol.JUXTA;
    }
}
