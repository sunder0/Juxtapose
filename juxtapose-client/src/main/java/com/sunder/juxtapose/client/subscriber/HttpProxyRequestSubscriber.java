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
import com.sunder.juxtapose.common.connection.ConnectionState;
import com.sunder.juxtapose.common.connection.DefaultConnectionManager;
import com.sunder.juxtapose.common.proxy.ProxyMessageReceiver;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.common.proxy.ProxyRequestSubscriber;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
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
import java.util.Objects;

/**
 * @author : sunder
 * @date : 17:46 2025/09/02
 */
public class HttpProxyRequestSubscriber extends BaseComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver, ProxyNodeLatencyTest {
    public final static String NAME = "HTTP_PROXY_SERVER";

    private Bootstrap bootstrap;
    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private DefaultConnectionManager connManager;

    public HttpProxyRequestSubscriber(ProxyServerNodeConfig cfg, CertComponent certComponent,
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
            Connection connection = connManager.createConnection(ProxyProtocol.HTTP, request);
            bootstrap.clone().connect(cfg.server, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new HttpRelayMessageHandler(request, connection));
                    ChannelTrafficShapingHandler trafficHandler =
                            cf.channel().pipeline().get(ChannelTrafficShapingHandler.class);
                    connection.bindTrafficCounter(trafficHandler.trafficCounter());

                    logger.info("Connect Http proxy relay server[{}:{}] successful!", cfg.server, cfg.port);
                } else {
                    logger.info("Connect Http proxy relay server[{}:{}] failed!", cfg.server, cfg.port, cf.cause());
                }
            }).await();

            return connection;
        } catch (Exception ex) {
            throw new ComponentException("Start HttpRelayServerComponent failed!", ex);
        }
    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        Connection connection = connManager.getConnection(serialId.toString());
        connection.writeMessage(message);
    }

    @Override
    public long testLatency() {
        ProxyServerUrlTestVisitor urlTestVisitor = parent.getUrlLatencyTestSupport();
        return urlTestVisitor.testUrl(this);
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
            connection.bindProxyChannel((SocketChannel) ctx.channel());

            sendHttpConnectMessage(ctx);

            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                if (response.status() == HttpResponseStatus.UNAUTHORIZED) {
                    logger.error("Http proxy server auth fail.");
                    connection.close();
                }

                if (response.status() == HttpResponseStatus.OK) {
                    connection.changeState(ConnectionState.AUTHENTICATED);
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
            logger.error(cause.getMessage(), cause);
            ctx.channel().close().addListener((ChannelFutureListener) channelFuture -> {
                HttpProxyRequestSubscriber.this.destroy();
            });
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
                connection.changeState(ConnectionState.AUTHENTICATING);
            }
            ctx.channel().writeAndFlush(httpRequest);
        }

    }

    /**
     * 建立http通道后直接转发原始数据
     */
    private class HttpTunnelMessageHandler extends ChannelInboundHandlerAdapter {
        private final Connection connection;

        public HttpTunnelMessageHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                logger.debug("receive proxy server message...[{}]", connection.getConnectId());
                connection.readMessage(msg);
            } else {
                ctx.fireChannelRead(ctx);
            }
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
