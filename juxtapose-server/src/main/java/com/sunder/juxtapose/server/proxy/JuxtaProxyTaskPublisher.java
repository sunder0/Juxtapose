package com.sunder.juxtapose.server.proxy;

import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.auth.SimpleAuthenticationStrategy;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.handler.RelayMessageWriteEncoder;
import com.sunder.juxtapose.common.mesage.AuthRequestMessage;
import com.sunder.juxtapose.common.mesage.AuthResponseMessage;
import com.sunder.juxtapose.common.mesage.Message;
import com.sunder.juxtapose.common.mesage.PingMessage;
import com.sunder.juxtapose.common.mesage.PongMessage;
import com.sunder.juxtapose.common.mesage.ProxyCloseMessage;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.CertComponent;
import com.sunder.juxtapose.server.ProxyCoreComponent;
import com.sunder.juxtapose.server.ProxyTaskPublisher;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import com.sunder.juxtapose.server.conf.ServerConfig;
import com.sunder.juxtapose.server.connection.UpstreamConnectionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author : sunder
 * @date : 19:35 2025/08/26
 */
public class JuxtaProxyTaskPublisher extends BaseCompositeComponent<ProxyCoreComponent>
        implements ProxyTaskPublisher {
    public final static String NAME = "JUXTA_PROXY_PROXY_COMPONENT";

    private final static int LOW_WATER_MARK = 32 * 1024; // 低水位线
    private final static int HIGH_WATER_MARK = 4 * 1024 * 1024; // 高水位线

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private boolean tls; // 是否开启ssl加密
    private String userName;
    private String password;
    private CertComponent certComponent;
    private UpstreamConnectionManager connManager;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    private Class<? extends ServerSocketChannel> serverSocketChannel;

    public JuxtaProxyTaskPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        this.host = cfg.getProxyHost();
        this.port = cfg.getProxyPort();
        this.auth = cfg.getProxyAuth();
        this.tls = cfg.getProxyTls();
        if (this.auth) {
            this.userName = cfg.getProxyUserName();
            this.password = cfg.getProxyPassword();
        }

        bossGroup = Platform.createEventLoopGroup(1);
        workGroup = Platform.createEventLoopGroup(8);
        serverSocketChannel = Platform.serverSocketChannelClass();

        certComponent = getParentComponent().getChildComponentByName(CertComponent.NAME, CertComponent.class);
        connManager = getModuleByName(UpstreamConnectionManager.NAME, true, UpstreamConnectionManager.class);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(bossGroup, workGroup)
                    .channel(serverSocketChannel)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline cp = channel.pipeline();
                            if (tls) {
                                cp.addLast(certComponent.getSslContext().newHandler(channel.alloc()));
                            }
                            cp.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                                    Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                            cp.addLast(RelayMessageWriteEncoder.INSTANCE);
                            cp.addLast(new ProxyRelayMessageHandler());
                        }
                    });
            boot.bind(host, port).addListener(f -> {
                if (!f.isSuccess()) {
                    logger.error("Juxta proxy server start failure, address:[{}:{}]", host, port, f.cause());
                } else {
                    logger.info("Juxta proxy server start success, address:[{}:{}]", host, port);
                }
            }).await();
        } catch (Exception ex) {
            throw new ComponentException(ex);
        }

        super.startInternal();
    }

    @Override
    public void publishProxyTask(ProxyTaskRequest request) {
        parent.getDispatcher().publishProxyTask(request);
    }

    /**
     * 从客户端传过来的代理中继消息，包含着需要的连接信息
     */
    public class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {
        private final boolean auth; // 是否需要认证
        private AuthenticationStrategy authStrategy; // 认证策略
        private volatile boolean writing = false; // 是否正在写入
        private final Deque<ByteBuf> pendingWrites = new ConcurrentLinkedDeque<>();

        public ProxyRelayMessageHandler() {
            this.auth = JuxtaProxyTaskPublisher.this.auth;
            if (this.auth) {
                this.authStrategy = new SimpleAuthenticationStrategy(
                        JuxtaProxyTaskPublisher.this.userName, JuxtaProxyTaskPublisher.this.password);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.info("Juxta client channel close[{}]...", ctx.channel().id());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // 60秒读空闲，超过视作关闭连接
            ctx.pipeline().addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;

                switch (event.state()) {
                    case READER_IDLE:
                        handleReaderIdle(ctx);
                        break;
                    case WRITER_IDLE:
                    case ALL_IDLE:
                        break;
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        /**
         * 处理读取超时，现默认客户端已经断开
         *
         * @param ctx io.netty.channel.ChannelHandlerContext
         */
        private void handleReaderIdle(ChannelHandlerContext ctx) {
            logger.info("Channel[{}] reader idle timeout, will close channel...", ctx.channel().id());
            ctx.close();
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
                            ctx.writeAndFlush(new PongMessage(), ctx.voidPromise());
                            break;
                        case PongMessage.SERVICE_ID:
                            new PongMessage(byteBuf);
                            break;
                        case AuthRequestMessage.SERVICE_ID:
                            handleAuthMessage(byteBuf, ctx);
                            break;
                        case ProxyRequestMessage.SERVICE_ID:
                            handleRequestMessage(byteBuf, ctx);
                            break;
                        case ProxyCloseMessage.SERVICE_ID:
                            handleCloseMessage(byteBuf, ctx);
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
         * 处理认证消息
         *
         * @param byteBuf
         * @param ctx
         */
        private void handleAuthMessage(ByteBuf byteBuf, ChannelHandlerContext ctx) {
            AuthRequestMessage message = new AuthRequestMessage(byteBuf);

            if (!auth || authStrategy.checkPermission(message.getUserName(), message.getPassword())) {
                ctx.writeAndFlush(new AuthResponseMessage(true)).addListener(f -> logger.info("write auth "
                        + "response success!"));
            } else {
                AuthResponseMessage authMsg = new AuthResponseMessage(false, "401");
                ctx.writeAndFlush(authMsg).addListener(ChannelFutureListener.CLOSE);
            }
        }

        /**
         * 处理代理消息
         *
         * @param byteBuf
         * @param ctx
         */
        private void handleRequestMessage(ByteBuf byteBuf, ChannelHandlerContext ctx) {
            ProxyRequestMessage message = new ProxyRequestMessage(byteBuf);

            ProxyTaskRequest request = new ProxyTaskRequest(ProxyProtocol.JUXTA, message, ctx.channel());
            JuxtaProxyTaskPublisher.this.publishProxyTask(request);
        }

        /**
         * 处理关闭消息
         *
         * @param byteBuf
         * @param ctx
         */
        private void handleCloseMessage(ByteBuf byteBuf, ChannelHandlerContext ctx) {
            ProxyCloseMessage message = new ProxyCloseMessage(byteBuf);
            logger.info("Receive proxy close message[{}, {}].", message.getSerialId(), ctx.channel().id());

            Connection connection = connManager.getConnection(message.getSerialId().toString());
            if (connection != null) {
                connection.close();
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            if (ctx.channel().isWritable()) {
                logger.info("Channel[{}] is writable, will write pending data...", ctx.channel().id());
                flushPendingWrites(ctx);
                notifyUpstreamToResumeReading(ctx);
            } else {
                logger.info("Channel[{}] is not writable! Pending writes:[{}].", ctx.channel().id(),
                        pendingWrites.size());
                notifyUpstreamToPauseReading(ctx);
            }

            super.channelWritabilityChanged(ctx);
        }

        /**
         * channel不可写时，在写入队列等待处理
         */
        public boolean writePendingWrites(Channel channel, ByteBuf message) {
            if (channel.isActive() && pendingWrites.size() > 999) {
                // Todo：暂时策略：尝试写入最老的数据
                ByteBuf oldest = pendingWrites.poll();
                channel.writeAndFlush(oldest);
                logger.error("Try write oldest message due to network speed slow...");
                return pendingWrites.offer(message);
            }

            if (!channel.isWritable() && channel.isActive()) {
                return pendingWrites.offer(message);
            } else if (channel.isWritable() && channel.isActive()) {
                channel.writeAndFlush(message);
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
                ByteBuf byteBuf = pendingWrites.poll();
                channel.write(byteBuf);
                logger.info("write pending data size:{}k, channel:[{}].", byteBuf.readableBytes() / 1024,
                        ctx.channel().id());
            }
            channel.flush();
            writing = false;
        }

        /**
         * 通知上游停止读
         *
         * @param ctx
         */
        private void notifyUpstreamToPauseReading(ChannelHandlerContext ctx) {
            Channel clientChannel = ctx.channel();
            List<Connection> connections = connManager.getConnectionsByClientChannel(clientChannel);
            for (Connection connection : connections) {
                Channel channel = connection.getProxyChannel();
                if (channel != null && channel.isActive()) {
                    channel.config().setAutoRead(false);
                }
            }
        }

        /**
         * 通知上游恢复读
         *
         * @param ctx
         */
        private void notifyUpstreamToResumeReading(ChannelHandlerContext ctx) {
            Channel clientChannel = ctx.channel();
            List<Connection> connections = connManager.getConnectionsByClientChannel(clientChannel);
            for (Connection connection : connections) {
                Channel channel = connection.getProxyChannel();
                if (channel != null && channel.isActive()) {
                    channel.config().setAutoRead(true);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Juxta client channel encountered an error[{}].", cause.getMessage(), cause);
            ctx.channel().close().addListener((ChannelFutureListener) channelFuture -> {
                logger.info("Juxta channel close[{}]...", ctx.channel().id());
            });
        }
    }

}