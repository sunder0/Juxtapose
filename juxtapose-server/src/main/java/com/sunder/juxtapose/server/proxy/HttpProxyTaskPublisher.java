package com.sunder.juxtapose.server.proxy;

import cn.hutool.core.lang.Pair;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.auth.SimpleAuthenticationStrategy;
import com.sunder.juxtapose.common.id.IdGenerator;
import com.sunder.juxtapose.common.id.SnowflakeIdGenerator;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.CertComponent;
import com.sunder.juxtapose.server.ProxyCoreComponent;
import com.sunder.juxtapose.server.ProxyTaskPublisher;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import com.sunder.juxtapose.server.conf.ServerConfig;
import com.sunder.juxtapose.server.session.ClientSession;
import com.sunder.juxtapose.server.session.SessionManager;
import com.sunder.juxtapose.server.session.SessionState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

/**
 * @author : denglinhai
 * @date : 19:36 2025/08/26
 */
public class HttpProxyTaskPublisher extends BaseCompositeComponent<ProxyCoreComponent> implements ProxyTaskPublisher {
    public final static String NAME = "HTTP_PROXY_TASK_PUBLISHER";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private boolean tls; // 是否开启tls加密
    private String userName;
    private String password;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;

    private final IdGenerator idGenerator;
    private SessionManager sessionManager;
    private CertComponent certComponent;

    public HttpProxyTaskPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
        this.idGenerator = new SnowflakeIdGenerator(1, 0);
    }

    @Override
    protected void initInternal() {
        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        this.port = cfg.getProxyPort();
        this.host = cfg.getProxyHost();
        this.auth = cfg.getProxyAuth();
        this.tls = cfg.getProxyTls();

        if (this.auth) {
            this.userName = cfg.getProxyUserName();
            this.password = cfg.getProxyPassword();
        }

        this.bossGroup = Platform.createEventLoopGroup(1);
        this.workGroup = Platform.createEventLoopGroup(4);

        this.sessionManager = getModuleByName(SessionManager.NAME, true, SessionManager.class);
        this.certComponent = getParentComponent().getChildComponentByName(CertComponent.NAME, CertComponent.class);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(bossGroup, workGroup)
                    .channel(Platform.serverSocketChannelClass())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true);
            boot.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (tls) {
                        pipeline.addLast(certComponent.getSslContext().newHandler(channel.alloc()));
                    }
                    pipeline.addLast(new HttpRequestDecoder());
                    pipeline.addLast(new HttpResponseEncoder());
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
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
        super.stopInternal();
    }

    @Override
    public void publishProxyTask(ProxyTaskRequest request) {
        parent.getDispatcher().publishProxyTask(request);
    }

    /**
     * http请求转发处理器
     */
    private class HttpRequestHandler extends SimpleChannelInboundHandler<HttpObject> {
        private boolean authPass;
        private ClientSession clientSession;
        private AuthenticationStrategy authStrategy;

        public HttpRequestHandler() {
            this.authPass = !HttpProxyTaskPublisher.this.auth;
            if (HttpProxyTaskPublisher.this.auth) {
                this.authStrategy = new SimpleAuthenticationStrategy(HttpProxyTaskPublisher.this.userName,
                        HttpProxyTaskPublisher.this.password);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String sessionId = ctx.channel().id().asShortText();
            clientSession = ClientSession.buildChannelBoundSession(sessionId, (SocketChannel) ctx.channel());
            HttpProxyTaskPublisher.this.sessionManager.addSession(clientSession);

            super.channelActive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                clientSession.changeState(SessionState.AUTHENTICATING);

                if (HttpMethod.CONNECT.equals(request.method())) {
                    logger.info("connect http[{}] request...", request.uri());

                    handleConnectMethod(ctx, request);
                } else {
                    logger.warn("others[{}] http[{}] request...", request.method(), request.uri());

                    handleOthersMethod(ctx, request);
                }
            }
        }

        /**
         * 处理HTTPS的connect请求，回答connect后会建立一条通道进行传输
         *
         * @param ctx 调用链上下文
         * @param request HttpRequest（请求行+请求头）
         */
        private void handleConnectMethod(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
            if (!authPass && !basicAuthentication(ctx, request)) {
                logger.error("http proxy auth fail, url[{}].", request.uri());
                return;
            } else {
                logger.info("connect http[{}] request auth passed.", request.uri());
            }

            clientSession.changeState(SessionState.AUTHENTICATED);

            Pair<String, Integer> hostInfo = parseHostInfoFromURI(ctx, request);
            HttpResponse response = new DefaultFullHttpResponse(
                    request.protocolVersion(), HttpResponseStatus.OK
            );
            ctx.writeAndFlush(response).addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    ctx.pipeline().addLast(new TunnelProxyHandler(
                            idGenerator.nextId(), hostInfo.getKey(), hostInfo.getValue(), clientSession));
                    clientSession.updateActivityTime();
                }
            });
        }

        /**
         * 处理非connect请求的普通HTTP请求
         *
         * @param ctx 调用链上下文
         * @param request HttpRequest（请求行+请求头）
         */
        private void handleOthersMethod(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
            if (!authPass && !basicAuthentication(ctx, request)) {
                logger.error("http proxy auth fail, url[{}].", request.uri());
                return;
            } else {
                logger.info("connect http[{}] request auth passed.", request.uri());
            }

            clientSession.changeState(SessionState.AUTHENTICATED);

            Pair<String, Integer> hostInfo = parseHostInfoFromURI(ctx, request);

            // 修改请求URI为相对路径
            URI uri = new URI(request.uri());
            if (uri.getHost() != null) {
                // 绝对URI (例如: http://example.com/path)
                String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                request.setUri(path);
            }

            ctx.pipeline().addLast(new PlaintextProxyHandler(
                    idGenerator.nextId(), hostInfo.getKey(), hostInfo.getValue(), clientSession, request));
        }

        /**
         * basic权限认证
         *
         * @param ctx 调用上下文
         * @param request http请求
         * @return 是否认证成功
         */
        private boolean basicAuthentication(ChannelHandlerContext ctx, HttpRequest request) {
            // eg:Proxy-Authorization: Basic cm9vdDpyb290
            String encodedCredentials = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
            if (encodedCredentials == null) {
                sendErrorResponse(ctx, HttpResponseStatus.UNAUTHORIZED, request.protocolVersion(),
                        "proxy-authorization absent");
                return false;
            }

            String credentials = new String(
                    Base64.getDecoder().decode(encodedCredentials.replace("Basic ", "")),
                    CharsetUtil.UTF_8
            );

            // 验证用户名和密码
            String[] userParts = credentials.split(":", 2);
            if (userParts.length != 2) {
                sendErrorResponse(ctx, HttpResponseStatus.UNAUTHORIZED, request.protocolVersion(), "Unauthorized");
                return false;
            }

            if (authStrategy != null && authStrategy.checkPermission(userParts[0], userParts[1])) {
                return authPass = true;
            } else {
                sendErrorResponse(ctx, HttpResponseStatus.UNAUTHORIZED, request.protocolVersion(), "Unauthorized");
                return false;
            }
        }

        /**
         * 从URI中解析HOST和port
         *
         * @param request http请求
         * @return host -> port
         * @throws Exception
         */
        private Pair<String, Integer> parseHostInfoFromURI(ChannelHandlerContext ctx, HttpRequest request)
                throws Exception {
            URI uri = null;
            try {
                uri = new URI(request.uri());
            } catch (URISyntaxException ex) {
                // eg: 5dfaddfb-90a1-4fa5-841e-0a3a560c76b9.gheapi.com:80
                String[] hostParts = host.split(":", 2);
                String host = hostParts[0];
                int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 80;
                return new Pair<>(host, port);
            }
            if (uri.getHost() != null) {
                // 绝对URI (例如: http://example.com/path)
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 80 : uri.getPort();
                return new Pair<>(host, port);
            } else {
                // 相对URI，从Host头获取主机信息
                host = request.headers().get(HttpHeaderNames.HOST);
                if (host == null) {
                    sendErrorResponse(ctx, HttpResponseStatus.BAD_REQUEST, request.protocolVersion(),
                            "Missing Host header");
                    throw new RuntimeException("Missing Host header");
                }

                // 处理可能包含端口的Host头
                String[] hostParts = host.split(":", 2);
                String host = hostParts[0];
                int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 80;
                return new Pair<>(host, port);
            }
        }

        /**
         * 发送错误响应
         */
        private void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, HttpVersion version,
                String message) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    version,
                    status,
                    Unpooled.copiedBuffer(message.getBytes())
            );
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            clientSession.changeState(SessionState.DISCONNECTED);
        }
    }

    /**
     * http(s) tunnel管道处理器
     */
    private class TunnelProxyHandler extends ChannelInboundHandlerAdapter {
        private long serialId;
        private String proxyHost;
        private int proxyPort;
        private ClientSession clientSession;

        public TunnelProxyHandler(long serialId, String proxyHost, int proxyPort, ClientSession clientSess) {
            this.serialId = serialId;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.clientSession = clientSess;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                clientSession.updateActivityTime();
                ProxyRequestMessage message = new ProxyRequestMessage(serialId, proxyHost, proxyPort, (ByteBuf) msg);
                ProxyTaskRequest ptr = new ProxyTaskRequest(ProxyProtocol.HTTP, message, clientSession);
                HttpProxyTaskPublisher.this.publishProxyTask(ptr);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // connect成功后建立通道就使用原始数据进行传输
            ctx.pipeline().remove(HttpResponseEncoder.class);
            ctx.pipeline().remove(HttpRequestDecoder.class);
            ctx.pipeline().remove(HttpRequestHandler.class);
        }
    }

    /**
     * 其他直接http 协议处理，直接转发
     */
    private class PlaintextProxyHandler extends ChannelInboundHandlerAdapter {
        private long serialId;
        private String proxyHost;
        private int proxyPort;
        private ClientSession clientSession;
        private final EmbeddedChannel writeEncoder = new EmbeddedChannel(new HttpRequestEncoder() {
            @Override
            protected boolean isContentAlwaysEmpty(HttpRequest msg) {
                return true;
            }
        });

        public PlaintextProxyHandler(long serialId, String proxyHost, int proxyPort, ClientSession clientSession,
                HttpRequest firstRequest) {
            this.serialId = serialId;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.clientSession = clientSession;

            writeEncoder.writeOutbound(firstRequest);
            ByteBuf result = writeEncoder.readOutbound();
            writeEncoder.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
            try {
                clientSession.updateActivityTime();
                ProxyRequestMessage message = new ProxyRequestMessage(serialId, proxyHost, proxyPort, result.retain());
                ProxyTaskRequest ptr = new ProxyTaskRequest(ProxyProtocol.HTTP, message, clientSession);
                HttpProxyTaskPublisher.this.publishProxyTask(ptr);
            } finally {
                ReferenceCountUtil.release(result);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;

                HttpHeaders header = request.headers();
                if (header.contains(HttpHeaderNames.PROXY_CONNECTION)) {
                    String val = header.get(HttpHeaderNames.PROXY_CONNECTION);
                    header.remove(HttpHeaderNames.PROXY_CONNECTION).add(HttpHeaderNames.CONNECTION, val);
                }

                URI uri = new URI(request.uri());
                String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                request.setUri(path);

                writeEncoder.writeOutbound(msg);
                ByteBuf buf = writeEncoder.readOutbound();
                writeEncoder.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
                try {
                    clientSession.updateActivityTime();
                    ProxyRequestMessage message = new ProxyRequestMessage(serialId, proxyHost, proxyPort, buf.retain());
                    ProxyTaskRequest ptr = new ProxyTaskRequest(ProxyProtocol.HTTP, message, clientSession);
                    HttpProxyTaskPublisher.this.publishProxyTask(ptr);
                } finally {
                    ReferenceCountUtil.release(buf);
                }
            } else if (msg instanceof HttpContent) {
                try {
                    ProxyRequestMessage message = new ProxyRequestMessage(
                            serialId, proxyHost, proxyPort, ((HttpContent) msg).content().retain());
                    ProxyTaskRequest ptr = new ProxyTaskRequest(ProxyProtocol.HTTP, message, clientSession);
                    HttpProxyTaskPublisher.this.publishProxyTask(ptr);
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // 从代理服务器返回的就是http报文，不需要在此encode，但是需要decode从客户端传过来的request
            ctx.pipeline().remove(HttpResponseEncoder.class);
            ctx.pipeline().remove(HttpRequestHandler.class);
        }
    }
}
