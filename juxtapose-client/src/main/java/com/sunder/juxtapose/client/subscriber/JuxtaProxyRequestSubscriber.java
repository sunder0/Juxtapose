package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
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
import com.sunder.juxtapose.common.pool.FixedChannelPool;
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
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.ReferenceCountUtil;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author : sunder
 * @date : 15:38 2023/7/5
 */
public class JuxtaProxyRequestSubscriber extends BaseComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver, ProxyNodeLatencyTest {
    public final static String NAME = "JUXTA_PROXY_SERVER";

    private final static int LOW_WATER_MARK = 32 * 1024; // 低水位线
    private final static int HIGH_WATER_MARK = 1024 * 1024; // 高水位线

    private Bootstrap bootstrap;
    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private DefaultConnectionManager<?> connManager;
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
        int coreThreads = Runtime.getRuntime().availableProcessors();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(Platform.createEventLoopGroup(coreThreads * 2))
                .channel(Platform.socketChannelClass())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024)  // 1MB发送缓冲区
                .option(ChannelOption.SO_RCVBUF, 1024 * 1024)  // 1MB接收缓冲区
                .option(ChannelOption.TCP_NODELAY, true)       // 禁用Nagle算法
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK));
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new ChannelTrafficShapingHandler(300 * 1024 * 1024, 300 * 1024 * 1024, 1000));
                if (cfg.tls) {
                    pipeline.addLast(
                            certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.server, cfg.port));
                }
                pipeline.addLast(RelayMessageWriteEncoder.INSTANCE);
                pipeline.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                        Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
            }
        });

        this.bootstrap = bootstrap;
        this.connManager = getModuleByName(DefaultConnectionManager.NAME, true, DefaultConnectionManager.class);
        this.fixedChannelPool = new JuxtaFixedChannelPool(this.bootstrap, 3);

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
        Channel channel = connection.getProxyChannel();
        if (channel.isWritable()) {
            connection.writeMessage(proxyMessage);
        } else {
            if (channel.isActive()) {
                ProxyRelayMessageHandler handler = channel.pipeline().get(ProxyRelayMessageHandler.class);
                handler.writePendingWrites(channel, proxyMessage);
            } else {
                message.release();
                logger.info("Proxy connection has been forcefully closed, and the received client message has been "
                        + "discarded...");
            }
        }
    }

    @Override
    public long testLatency() {
        ProxyServerUrlTestVisitor urlTestVisitor = parent.getUrlLatencyTestSupport();
        return urlTestVisitor.testUrl(this);
    }

    /**
     * 与代理服务器通信
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {
        private final Deque<ProxyRequestMessage> pendingWrites = new ConcurrentLinkedDeque<>();
        private volatile boolean writing = false; // 是否正在写入

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // 30秒写空闲，超过则发送一个心跳命令
            ctx.pipeline().addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;

                switch (event.state()) {
                    case READER_IDLE:
                        break;
                    case WRITER_IDLE:
                        handleWriterIdle(ctx);
                        break;
                    case ALL_IDLE:
                        break;
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        /**
         * 处理写超时，默认发送ping命令
         *
         * @param ctx io.netty.channel.ChannelHandlerContext
         */
        private void handleWriterIdle(ChannelHandlerContext ctx) {
            PingMessage message = new PingMessage();
            ctx.channel().writeAndFlush(message);
        }

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
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.error("Juxta proxy channel close an error[{}].", ctx.channel().id());
            List<Connection> connections = connManager.getConnectionsByProxyChannel(ctx.channel());
            for (Connection connection : connections) {
                connection.close();
            }
            fixedChannelPool.release(ctx.channel());
            connManager.unregisterTrafficHandler(ctx.channel());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                try {
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
                } finally {
                    byteBuf.release();
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        /**
         * 认证权限处理
         */
        private void handleAuthResponseMessage(ChannelHandlerContext ctx, AuthResponseMessage message) {
            if (!message.isPassed()) {
                logger.error("Proxy server[{}:{}] auth verify failed, errorMsg:[{}].", cfg.server, cfg.port,
                        message.getMessage());
                fixedChannelPool.release(ctx.channel());
            } else {
                // nothing to do...
            }
        }

        /**
         * 接受转发代理返回消息
         */
        private void handleProxyResponseMessage(ChannelHandlerContext ctx, ProxyResponseMessage message) {
            logger.debug("receive proxy server message...[{}]", message.getSerialId());
            if (message.isSuccess()) {
                DefaultConnectionManager<?> connManager = JuxtaProxyRequestSubscriber.this.connManager;
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
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            if (ctx.channel().isWritable()) {
                logger.info("Channel[{}] is writable, will write pending data...", ctx.channel().id());
                flushPendingWrites(ctx);
            } else {
                logger.info("Channel[{}] is not writable! Pending writes:[{}].", ctx.channel().id(),
                        pendingWrites.size());
            }

            super.channelWritabilityChanged(ctx);
        }

        /**
         * channel不可写时，在写入队列等待处理
         */
        public boolean writePendingWrites(Channel channel, ProxyRequestMessage message) {
            if (pendingWrites.size() > 999) {
                // Todo：暂时策略：尝试写入最老的数据
                ProxyRequestMessage oldest = pendingWrites.poll();
                channel.writeAndFlush(oldest);
                logger.error("Try write oldest message due to network speed slow...");
                return pendingWrites.offer(message);
            }

            if (!channel.isWritable() && channel.isActive()) {
                return pendingWrites.offer(message);
            }
            return false;
        }

        /**
         * 暂存的待处理数据写入channel
         *
         * @param ctx io.netty.channel.ChannelHandlerContext
         */
        private void flushPendingWrites(ChannelHandlerContext ctx) {
            if (writing) {
                return;
            }
            writing = true;

            Channel channel = ctx.channel();
            while (channel.isWritable() && !pendingWrites.isEmpty()) {
                channel.write(pendingWrites.poll());
            }
            channel.flush();
            writing = false;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Juxta proxy channel encountered an error[{}].", cause.getMessage(), cause);
            fixedChannelPool.release(ctx.channel());
            ctx.channel().close().addListener((ChannelFutureListener) channelFuture -> {
                logger.info("Juxta channel close[{}]...", ctx.channel().id());
            });
        }
    }

    /**
     * juxta协议的fixedChannelPool
     */
    private class JuxtaFixedChannelPool extends FixedChannelPool {

        public JuxtaFixedChannelPool(Bootstrap bootstrap, int maximumPoolSize) {
            super(bootstrap, maximumPoolSize);
        }

        @Override
        protected ChannelFuture createNewChannel0(ProxyRequest request, Connection connection) {
            return bootstrap.clone().connect(cfg.server, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new JuxtaProxyRequestSubscriber.ProxyRelayMessageHandler());
                    ChannelTrafficShapingHandler trafficHandler =
                            cf.channel().pipeline().get(ChannelTrafficShapingHandler.class);
                    connManager.registerTrafficHandler(cf.channel(), trafficHandler);

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
