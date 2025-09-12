package com.sunder.juxtapose.client.subscriber;

import com.sunder.juxtapose.client.CertComponent;
import com.sunder.juxtapose.client.ProxyMessageReceiver;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.ProxyServerNodeManager;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 17:46 2025/09/02
 */
public class HttpProxyRequestSubscriber extends BaseCompositeComponent<ProxyServerNodeManager>
        implements ProxyRequestSubscriber, ProxyMessageReceiver {
    public final static String NAME = "HTTP_PROXY_SERVER";

    private Bootstrap bootstrap;
    private final ProxyServerNodeConfig cfg;
    private CertComponent certComponent;
    private Map<Long, SocketChannel> relayChannel = new ConcurrentHashMap<>(16); // 和中继服务器通信的channel
    private final Map<Long, ProxyRequest> activeProxy = new ConcurrentHashMap<>(16); // 活跃的代理

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
                if (cfg.tls) {
                    pipeline.addLast(
                            certComponent.getSslContext().newHandler(socketChannel.alloc(), cfg.host, cfg.port));
                }
                pipeline.addLast(new HttpRequestEncoder());
                pipeline.addLast(new HttpResponseDecoder());
            }
        });
        this.bootstrap = bootstrap;

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        super.startInternal();
    }

    @Override
    protected void destroyInternal() {
        parent.removeProxyRequestSubscriber(this);

        super.destroyInternal();
    }

    @Override
    public void subscribe(ProxyRequest request) {
        try {
            bootstrap.clone().connect(cfg.host, cfg.port).addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    cf.channel().pipeline().addLast(new HttpRelayMessageHandler(request));
                    relayChannel.put(request.getSerialId(), (SocketChannel) cf.channel());

                    String uri = "http://" + request.getHost() + ":" + request.getPort();
                    HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, uri);
                    if (cfg.auth) {
                        String basicEncode = Base64.getEncoder().encodeToString(
                                (cfg.username + ":" + cfg.password).getBytes(StandardCharsets.UTF_8));
                        httpRequest.headers().add(HttpHeaderNames.PROXY_AUTHORIZATION, "Basic " + basicEncode);
                    }
                    cf.channel().writeAndFlush(httpRequest);

                    logger.info("Connect Http proxy relay server[{}:{}] successful!", cfg.host, cfg.port);
                } else {
                    logger.info("Connect Http proxy relay server[{}:{}] failed!", cfg.host, cfg.port, cf.cause());
                }
            }).await();
        } catch (Exception ex) {
            throw new ComponentException("Start HttpRelayServerComponent failed!", ex);
        }

    }

    @Override
    public void receive(Long serialId, ByteBuf message) {
        SocketChannel socketChannel = relayChannel.get(serialId);
        socketChannel.writeAndFlush(message, socketChannel.newPromise());
    }

    /**
     * 与代理服务器通信， 判断是否建立http通道成功
     */
    private class HttpRelayMessageHandler extends ChannelInboundHandlerAdapter {
        private final ProxyRequest request;

        public HttpRelayMessageHandler(ProxyRequest request) {
            this.request = request;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            relayChannel.put(request.getSerialId(), (SocketChannel) ctx.channel());
            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                if (response.status() == HttpResponseStatus.UNAUTHORIZED) {
                    logger.error("Http proxy server auth fail.");
                    ctx.close();
                }

                if (response.status() == HttpResponseStatus.OK) {
                    // http通道建好后才允许传递消息
                    logger.info("removing HTTP codecs and relay handler for tunnel mode.");
                    ctx.pipeline().remove(HttpRequestEncoder.class);
                    ctx.pipeline().remove(HttpResponseDecoder.class);
                    ctx.pipeline().remove(HttpRelayMessageHandler.class);

                    ctx.pipeline().addLast(new HttpTunnelMessageHandler(request));
                    request.setProxyMessageReceiver(HttpProxyRequestSubscriber.this);
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
    }

    /**
     * 建立http通道后直接转发原始数据
     */
    private class HttpTunnelMessageHandler extends ChannelInboundHandlerAdapter {
        private final ProxyRequest request;

        public HttpTunnelMessageHandler(ProxyRequest request) {
            this.request = request;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                logger.debug("receive proxy server message...[{}]", request.getSerialId());
                request.returnMessage((ByteBuf) msg);
            } else {
                ctx.fireChannelRead(ctx);
            }
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
