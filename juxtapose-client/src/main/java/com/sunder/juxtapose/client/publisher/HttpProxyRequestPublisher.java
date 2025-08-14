package com.sunder.juxtapose.client.publisher;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestPublisher;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;

/**
 * @author : denglinhai
 * @date : 15:49 2025/08/13
 */
public class HttpProxyRequestPublisher extends BaseComponent<ProxyCoreComponent> implements ProxyRequestPublisher {
    public final static String NAME = "HTTP_PROXY_REQUEST_PUBLISHER";

    private String host;
    private int port;
    private EventLoopGroup eventLoopGroup;

    public HttpProxyRequestPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        this.port = cfg.getHttpPort();
        this.host = cfg.getHttpHost();

        this.eventLoopGroup = new NioEventLoopGroup(3);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true);
            boot.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast(new HttpObjectAggregator(65536));
                    pipeline.addLast(new HttpRequestHandler());
                }
            });
            boot.bind(host, port).addListener(f -> {
                if (!f.isSuccess()) {
                    logger.error("Http(s) server start failure, address:[{}:{}].", host, port, f.cause());
                } else {
                    logger.info("Http(s) server start success, address:[{}:{}].", host, port);
                }
            }).await();
        } catch (Exception ex) {
            throw new ComponentException(ex);
        }

        super.startInternal();
    }

    @Override
    protected void stopInternal() {
        eventLoopGroup.shutdownGracefully();
        super.stopInternal();
    }

    @Override
    public void publishProxyRequest(ProxyRequest request) {
        getParentComponent().publishProxyRequest(request);
    }

    /**
     * http请求转发处理器
     */
    class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request)
                throws Exception {
            if (HttpMethod.CONNECT == request.method()) {
                logger.info("connect http request...");

                handleConnectMethod(ctx, request);
            } else {
                logger.warn("others http request...");

                handleOthersMethod(ctx, request);
            }
        }

        private void handleConnectMethod(ChannelHandlerContext ctx, FullHttpRequest request) {
            String[] parts = request.uri().split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;
            ProxyRequest pr = new ProxyRequest(host, port, ctx.channel());
            HttpProxyRequestPublisher.this.publishProxyRequest(pr);

            HttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("Connection Established".getBytes()));
            ctx.write(response);
            ctx.pipeline().addLast(new TunnelProxyHandler(pr));
            ctx.flush();
        }

        private void handleOthersMethod(ChannelHandlerContext ctx, FullHttpRequest request) {
            String[] parts = request.uri().split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;
            ProxyRequest pr = new ProxyRequest(host, port, ctx.channel());
            HttpProxyRequestPublisher.this.publishProxyRequest(pr);

            ctx.pipeline().addLast(new PlaintextProxyHandler(pr));
            ctx.fireChannelRead(request);
        }

        /**
         * 发送错误响应
         */
        private void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    status,
                    Unpooled.copiedBuffer(message.getBytes())
            );
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * http(s) tunnel管道处理器
     */
    private class TunnelProxyHandler extends ChannelInboundHandlerAdapter {
        private ProxyRequest proxyRequest;

        public TunnelProxyHandler(ProxyRequest proxyRequest) {
            this.proxyRequest = proxyRequest;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;
                try {
                    proxyRequest.transferMessage(byteBuf.retain());
                } finally {
                    byteBuf.release();
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.pipeline().remove(HttpObjectAggregator.class);
            ctx.pipeline().remove(HttpServerCodec.class);
            ctx.pipeline().remove(HttpRequestHandler.class);
        }
    }

    /**
     * 其他直接http 协议处理，直接转发
     */
    private class PlaintextProxyHandler extends ChannelInboundHandlerAdapter {
        private ProxyRequest proxyRequest;
        private EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());

        public PlaintextProxyHandler(ProxyRequest proxyRequest) {
            this.proxyRequest = proxyRequest;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                try {
                    // 写入请求到通道（触发编码）
                    channel.writeOutbound(request);
                    // 读取编码后的ByteBuf
                    CompositeByteBuf composite = Unpooled.compositeBuffer();
                    while (true) {
                        ByteBuf buf = channel.readOutbound();
                        if (buf == null) {
                            break;
                        }
                        composite.addComponent(true, buf);
                    }

                    // 4. 确保资源释放
                    request.release();
                    proxyRequest.transferMessage(composite.retain());
                } finally {
                    channel.close();
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.pipeline().remove(HttpRequestHandler.class);
        }
    }

}
