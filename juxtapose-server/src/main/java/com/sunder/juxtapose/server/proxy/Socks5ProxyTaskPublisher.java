package com.sunder.juxtapose.server.proxy;

import com.sunder.juxtapose.common.BaseComponent;
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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksAuthRequest;
import io.netty.handler.codec.socks.SocksAuthRequestDecoder;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksAuthStatus;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksRequest;

/**
 * @author : denglinhai
 * @date : 19:36 2025/08/26
 */
public class Socks5ProxyTaskPublisher extends BaseComponent<ProxyCoreComponent> implements ProxyTaskPublisher {
    public final static String NAME = "SOCKS5_PROXY_COMPONENT";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private String userName;
    private String password;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    private Class<? extends ServerSocketChannel> serverSocketChannel;

    private final IdGenerator idGenerator;
    private SessionManager sessionManager;
    private CertComponent certComponent;

    public Socks5ProxyTaskPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
        this.idGenerator = new SnowflakeIdGenerator(0, 0);
    }

    @Override
    protected void initInternal() {
        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        this.host = cfg.getProxyHost();
        this.port = cfg.getProxyPort();
        this.auth = cfg.getProxyAuth();
        if (this.auth) {
            this.userName = cfg.getProxyUserName();
            this.password = cfg.getProxyPassword();
        }

        this.bossGroup = Platform.createEventLoopGroup(1);
        this.workGroup = Platform.createEventLoopGroup(4);
        this.serverSocketChannel = Platform.serverSocketChannelClass();

        sessionManager = getModuleByName(SessionManager.NAME, true, SessionManager.class);
        certComponent = getParentComponent().getChildComponentByName(CertComponent.NAME, CertComponent.class);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(bossGroup, workGroup)
                    .channel(serverSocketChannel)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            // pipeline.addLast(certComponent.getSslContext().newHandler(channel.alloc()));
                            pipeline.addLast(new SocksInitRequestDecoder());
                            pipeline.addLast(new SocksMessageEncoder());
                            pipeline.addLast(new SocksRequestHandler());
                        }
                    });
            boot.bind(host, port).addListener(f -> {
                if (!f.isSuccess()) {
                    logger.error("Socks5 server start failure, address:[{}:{}].", host, port, f.cause());
                } else {
                    logger.info("Socks5 server start success, address:[{}:{}].", host, port);
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
     * sock5请求处理器
     */
    private class SocksRequestHandler extends SimpleChannelInboundHandler<SocksRequest> {
        private final boolean auth; // 是否需要鉴权
        private AuthenticationStrategy authStrategy;
        private boolean authPass; // 鉴权通过
        private ClientSession clientSession;

        public SocksRequestHandler() {
            this.auth = Socks5ProxyTaskPublisher.this.auth;
            this.authPass = !this.auth;
            if (this.auth) {
                this.authStrategy = new SimpleAuthenticationStrategy(Socks5ProxyTaskPublisher.this.userName,
                        Socks5ProxyTaskPublisher.this.password);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String sessionId = ctx.channel().id().asShortText();
            clientSession = ClientSession.buildChannelBoundSession(sessionId, (SocketChannel) ctx.channel());
            Socks5ProxyTaskPublisher.this.sessionManager.addSession(clientSession);

            super.channelActive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksRequest request) throws Exception {
            ChannelPipeline cp = ctx.pipeline();
            switch (request.requestType()) {
                case INIT: {  // 如果是Socks5初始化请求
                    logger.info("Socks init...");
                    if (!auth) {
                        clientSession.changeState(SessionState.AUTHENTICATED);
                        cp.addFirst(new SocksCmdRequestDecoder());
                        ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                    } else {
                        clientSession.changeState(SessionState.AUTHENTICATING);
                        cp.addFirst(new SocksAuthRequestDecoder());
                        ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.AUTH_PASSWORD));
                    }

                    break;
                }
                case AUTH: {  // 如果是Socks5认证请求
                    processAuthRequest(ctx, (SocksAuthRequest) request);
                    break;
                }
                case CMD: {  // 如果是Socks5命令请求
                    if (!authPass) {
                        clientSession.changeState(SessionState.DISCONNECTED);
                        ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FORBIDDEN, SocksAddressType.IPv4))
                                .addListener(ChannelFutureListener.CLOSE);
                        return;
                    }

                    processCommandRequest(ctx, (SocksCmdRequest) request);
                    break;
                }
                case UNKNOWN:
                default: {  // 未知请求关闭连接
                    logger.info("Unknown socks command, from cmd:[{}], address: {}", request.requestType(),
                            ctx.channel().localAddress().toString());
                    ctx.close();
                }
            }
        }

        private void processCommandRequest(ChannelHandlerContext ctx, SocksCmdRequest request) {
            String host = request.host();
            int port = request.port();

            logger.info("Socks command request to {}:{}", host, port);

            switch (request.cmdType()) {
                case CONNECT: {
                    logger.info("connect cmd...");
                    clientSession.updateActivityTime();

                    long serialId = idGenerator.nextId();
                    ctx.pipeline().addLast(new SocksRelayHandler(serialId, host, port, clientSession)).remove(this);
                    ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType()));
                }
                break;

                case UDP: {
                    logger.info("udp cmd...");
                    //                ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4,
                    //                        null, ((DatagramChannel) future.channel()).localAddress().getPort()));
                    //                ChannelFuture future = bindUdpTunnelService(ctx.channel().closeFuture());
                    //                future.addListener(ch -> {
                    //                    if (!ch.isSuccess()) {
                    //                        ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, SocksAddressType.IPv4));
                    //                        ctx.close();
                    //                    }
                    //
                    //                    ctx.pipeline().remove(this);
                    //                    ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4,
                    //                            null, ((DatagramChannel) future.channel()).localAddress().getPort()));
                    //                });
                }
                break;

                default: {
                    logger.info("Socks command request is not CONNECT or UDP.");
                    ctx.writeAndFlush(
                            new SocksCmdResponse(SocksCmdStatus.COMMAND_NOT_SUPPORTED, SocksAddressType.IPv4));
                    ctx.close();
                }
            }
        }

        private void processAuthRequest(ChannelHandlerContext ctx, SocksAuthRequest request) {
            if (!auth) {
                authPass = true;
                logger.info("No authentication required.");
                return;
            }

            if (authStrategy.checkPermission(request.username(), request.password())) {
                logger.info("Socks5 auth success.");
                authPass = true;

                clientSession.changeState(SessionState.AUTHENTICATED);
                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
            } else {
                clientSession.changeState(SessionState.DISCONNECTED);
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    /**
     * socks5中继消息
     */
    private class SocksRelayHandler extends ChannelInboundHandlerAdapter {
        private long serialId;
        private String proxyHost;
        private Integer proxyPort;
        private ClientSession clientSession;

        public SocksRelayHandler(long serialId, String proxyHost, Integer proxyPort, ClientSession clientSession) {
            this.serialId = serialId;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.clientSession = clientSession;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ProxyRequestMessage message = new ProxyRequestMessage(serialId, proxyHost, proxyPort, (ByteBuf) msg);
                ProxyTaskRequest ptr = new ProxyTaskRequest(ProxyProtocol.SOCKS5, message, clientSession);
                Socks5ProxyTaskPublisher.this.publishProxyTask(ptr);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
        }
    }

}
