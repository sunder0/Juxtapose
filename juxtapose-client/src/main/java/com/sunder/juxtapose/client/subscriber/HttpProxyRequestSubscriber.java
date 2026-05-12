package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
import com.sunder.juxtapose.client.ProxyServerNodeManager;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.group.ProxyNodeLatencyTest;
import com.sunder.juxtapose.client.group.ProxyServerUrlTestVisitor;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.pool.CachedChannelPool;
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
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author : sunder
 * @date : 17:46 2025/09/02
 */
public class HttpProxyRequestSubscriber extends BaseCompositeComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver, ProxyNodeLatencyTest {
    public final static String NAME = "HTTP_PROXY_SERVER";

    private final static int LOW_WATER_MARK = 32 * 1024; // 低水位线
    private final static int HIGH_WATER_MARK = 1024 * 1024; // 高水位线

    private Bootstrap bootstrap;
    private final ProxyServerNodeConfig cfg;
    private CertComponent<?> certComponent;
    private DefaultConnectionManager<?> connManager;
    private CachedChannelPool cachedChannelPool;

    public HttpProxyRequestSubscriber(ProxyServerNodeConfig cfg, ProxyServerNodeManager parent) {
        super(cfg.name, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        this.cfg = cfg;

        addChildComponent(certComponent = new CertComponent<>(cfg, this));
        parent.registerProxyRequestSubscriber(this);
    }

    @Override
    protected void initInternal() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(Platform.createEventLoopGroup(8))
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
                pipeline.addLast(new ChannelTrafficShapingHandler(1000));
                if (cfg.tls) {
                    pipeline.addLast(
                            certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.server, cfg.port));
                }
                pipeline.addLast(new HttpRequestEncoder());
                pipeline.addLast(new HttpResponseDecoder());
            }
        });
        this.bootstrap = bootstrap;

        this.connManager = getModuleByName(DefaultConnectionManager.NAME, true, DefaultConnectionManager.class);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        // 第一次启动测试下延迟
        testLatency();
    }

    @Override
    protected void destroyInternal() {
        parent.removeProxyRequestSubscriber(this);

        super.destroyInternal();
    }

    @Override
    public Connection subscribe(ProxyRequest request) {
        try {
            Connection connection = connManager.createConnection(ProxyProtocol.HTTP, request);
            bootstrap.clone().connect(cfg.server, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new HttpRelayMessageHandler(request, connection));
                    ChannelTrafficShapingHandler trafficHandler =
                            cf.channel().pipeline().get(ChannelTrafficShapingHandler.class);
                    connManager.registerTrafficHandler(cf.channel(), trafficHandler);

                    connection.bindProxyChannel((SocketChannel) cf.channel());

                    logger.info("Connect Http proxy relay server[{}:{}] successful!", cfg.server, cfg.port);
                } else {
                    logger.info("Connect Http proxy relay server[{}:{}] failed!", cfg.server, cfg.port, cf.cause());
                }
            });

            return connection;
        } catch (Exception ex) {
            throw new ComponentException("Start HttpRelayServerComponent failed!", ex);
        }
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        Connection connection = connManager.getConnection(serialId.toString());
        if (connection == null) {
            message.release();
            return;
        }

        Channel channel = connection.getProxyChannel();
        if (channel.isWritable()) {
            connection.writeMessage(message);
        } else {
            if (channel.isActive()) {
                HttpTunnelMessageHandler handler = channel.pipeline().get(HttpTunnelMessageHandler.class);
                handler.writePendingWrites(channel, message);
            } else {
                message.release();
                logger.info("Proxy connection has been forcefully closed, and the received client message has been "
                        + "discarded...");
            }
        }

    }

    @Override
    public CompletableFuture<Long> testLatency() {
        ProxyServerUrlTestVisitor urlTestVisitor = parent.getUrlLatencyTestSupport();
        CompletableFuture<Long> result = urlTestVisitor.testUrl(this);
        return result.whenComplete((latency, ex) -> cfg.latency = latency);
    }

    /**
     * http缓存连接池
     */
    private class HttpCachedChannelPool extends CachedChannelPool {

        public HttpCachedChannelPool(Bootstrap bootstrap, long maxIdleTime) {
            super(bootstrap, maxIdleTime);
        }

        @Override
        protected ChannelFuture createNewChannel0(ProxyRequest request, Connection connection) {
            return bootstrap.clone().connect(cfg.server, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new HttpRelayMessageHandler(request, connection));
                    ChannelTrafficShapingHandler trafficHandler =
                            cf.channel().pipeline().get(ChannelTrafficShapingHandler.class);

                    logger.info("Connect Http proxy relay server[{}:{}] successful!", cfg.server, cfg.port);
                } else {
                    logger.info("Connect Http proxy relay server[{}:{}] failed!", cfg.server, cfg.port, cf.cause());
                }
            });
        }
    }

    /**
     * 与代理服务器通信， 判断是否建立http通道成功
     */
    private class HttpRelayMessageHandler extends ChannelInboundHandlerAdapter {
        private final ProxyRequest request;
        private final Connection connection;

        public HttpRelayMessageHandler(ProxyRequest request, Connection connection) {
            this.request = request;
            this.connection = connection;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            sendHttpConnectMessage(ctx);

            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                if (response.status() == HttpResponseStatus.UNAUTHORIZED) {
                    logger.error("Http proxy server auth fail.");
                    connection.closeForce();
                    connManager.unregisterTrafficHandler(ctx.channel());
                }

                if (response.status() == HttpResponseStatus.OK) {
                    // http通道建好后才允许传递消息
                    logger.info("removing HTTP codecs and relay handler for tunnel mode.");
                    ctx.pipeline().remove(HttpRequestEncoder.class);
                    ctx.pipeline().remove(HttpResponseDecoder.class);
                    ctx.pipeline().remove(HttpRelayMessageHandler.class);

                    ctx.pipeline().addLast(new HttpTunnelMessageHandler(connection));
                    connection.activeMessageTransfer(HttpProxyRequestSubscriber.this);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Http(s) proxy channel encountered an error[{}].", cause.getMessage(), cause);
            connection.closeForce();
            connManager.unregisterTrafficHandler(ctx.channel());
        }

        /**
         * 发送http connect连接建立请求
         *
         * @param ctx 上下文
         */
        private void sendHttpConnectMessage(ChannelHandlerContext ctx) {
            String uri = "http://" + request.getHost() + ":" + request.getPort();
            HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, uri);
            if (cfg.auth) {
                String basicEncode = Base64.getEncoder()
                        .encodeToString((cfg.username + ":" + cfg.password).getBytes(StandardCharsets.UTF_8));
                httpRequest.headers().add(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic " + basicEncode);
            }
            httpRequest.headers().add(HttpHeaderNames.CONNECTION, request.getSerialId());
            ctx.channel().writeAndFlush(httpRequest);
        }

    }

    /**
     * 建立http通道后直接转发原始数据
     */
    private class HttpTunnelMessageHandler extends ChannelInboundHandlerAdapter {
        private Connection connection;
        private volatile boolean writing = false; // 是否正在写入
        private final Deque<ByteBuf> pendingWrites = new ConcurrentLinkedDeque<>();

        public HttpTunnelMessageHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.info("Http(s) proxy channel inactive close [{}].", ctx.channel().id());
            connection.closeForce();
            connManager.unregisterTrafficHandler(ctx.channel());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                logger.debug("receive proxy server message...[{}]", connection.getConnectId());
                connection.readMessage(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        /**
         * 更改连接，更改此channel隶属的connection
         *
         * @param connection 连接
         */
        public void changeConnection(Connection connection) {
            this.connection = connection;
        }

        public String getConnectionId() {
            return connection.getConnectId();
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
        public boolean writePendingWrites(Channel channel, ByteBuf message) {
            if (pendingWrites.size() > 999) {
                // Todo：暂时策略：尝试写入最老的数据
                ByteBuf oldest = pendingWrites.poll();
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
            logger.error("Http(s) proxy channel encountered an error[{}].", cause.getMessage(), cause);
            connection.closeForce();
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
        return ProxyProtocol.HTTP;
    }
}
