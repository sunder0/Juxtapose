package com.sunder.juxtapose.client.publisher;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestPublisher;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.dns.StandardDnsResolverPool;
import com.sunder.juxtapose.client.handler.TcpProxyMessageHandler;
import com.sunder.juxtapose.client.system.MacOSSystemProxySetting;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.auth.SimpleAuthenticationStrategy;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
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
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author : denglinhai
 * @date : 16:32 2025/07/19
 */
public class Socks5ProxyRequestPublisher extends BaseCompositeComponent<ProxyCoreComponent>
        implements ProxyRequestPublisher {
    public final static String NAME = "SOCKS5_PROXY_REQUEST_PUBLISHER";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private String userName;
    private String password;
    private Class<? extends ServerSocketChannel> serverSocketChannel;
    private EventLoopGroup eventLoopGroup;
    private StandardDnsResolverPool dnsResolver = StandardDnsResolverPool.dnsResolver;

    public Socks5ProxyRequestPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        this.port = cfg.getSocks5Port();
        this.host = cfg.getSocks5Host();
        this.auth = cfg.getSocks5Auth();
        if (this.auth) {
            this.userName = cfg.getSocks5User();
            this.password = cfg.getSocks5Pwd();
        }

        if (Platform.isMac()) {
            addChildComponent(new MacOSSystemProxySetting(this));
        }

        this.serverSocketChannel = Platform.serverSocketChannelClass();
        this.eventLoopGroup = Platform.createEventLoopGroup(3);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(eventLoopGroup)
                    .channel(serverSocketChannel)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
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
    protected void stopInternal() {
        eventLoopGroup.shutdownGracefully();
        super.stopInternal();
    }

    @Override
    public void publishProxyRequest(ProxyRequest request) {
        getParentComponent().publishProxyRequest(request);
    }

    /**
     * sock5请求处理器
     */
    class SocksRequestHandler extends SimpleChannelInboundHandler<SocksRequest> {
        private boolean auth; // 是否需要鉴权
        private AuthenticationStrategy authStrategy;
        private boolean authPass; // 鉴权通过

        public SocksRequestHandler() {
            this.auth = Socks5ProxyRequestPublisher.this.auth;
            this.authPass = !this.auth;
            this.authStrategy = new SimpleAuthenticationStrategy(Socks5ProxyRequestPublisher.this.userName,
                    Socks5ProxyRequestPublisher.this.password);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksRequest request) throws Exception {
            ChannelPipeline cp = ctx.pipeline();
            switch (request.requestType()) {
                case INIT: {  // 如果是Socks5初始化请求
                    logger.info("Socks init...");
                    if (!auth) {
                        cp.addFirst(new SocksCmdRequestDecoder());
                        ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                    } else {
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
                        SocksCmdRequest cmd = (SocksCmdRequest) request;
                        ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FORBIDDEN, cmd.addressType()))
                                .addListener(ChannelFutureListener.CLOSE);
                        return;
                    }

                    processCommandRequest(ctx, (SocksCmdRequest) request);
                    break;
                }
                case UNKNOWN:
                default: {  // 未知请求关闭连接
                    logger.info("Unknown socks command, from address: {}", ctx.channel().localAddress().toString());
                    ctx.close();
                }
            }
        }

        private void processCommandRequest(ChannelHandlerContext ctx, SocksCmdRequest request) {
            String host = request.host();
            int port = request.port();

            logger.info("Socks command request to {}:{}", host, port);
            switch (request.addressType()) {
                case DOMAIN: {
                    dnsResolver.resolveAsync(host).addListener(f -> {
                        if (f.isSuccess()) {
                            InetAddress ip = (InetAddress) f.getNow();
                            processCommandRequest(ctx, host, port, host, ip, request.addressType(), request.cmdType());
                        } else {
                            logger.error("Unknown Host[{}] error.", host, f.cause());
                            ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.HOST_UNREACHABLE,
                                    request.addressType())).addListener(ChannelFutureListener.CLOSE);
                        }
                    });
                }
                break;

                case IPv4:
                case IPv6: {
                    try {
                        // 1. 验证ip是否合法 2.标准化处理, todo: getByName不能校验格式问题，不能校验是否是ip
                        InetAddress ip = InetAddress.getByName(host);
                        processCommandRequest(ctx, host, port, host, ip, request.addressType(), request.cmdType());
                    } catch (UnknownHostException ex) {
                        logger.error("host address[{}] format error.", host, ex);
                        ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.ADDRESS_NOT_SUPPORTED,
                                request.addressType())).addListener(ChannelFutureListener.CLOSE);
                    }
                }
                break;

                case UNKNOWN:
                    throw new RuntimeException("Unsupported address type...");
            }
        }

        private void processCommandRequest(ChannelHandlerContext ctx, String host, int port, String domain,
                InetAddress ip, SocksAddressType addressType, SocksCmdType cmdType) {
            switch (cmdType) {
                case CONNECT: {
                    logger.info("connect cmd...");
                    ProxyRequest pr = new ProxyRequest(host, port, domain, ip, ctx.channel());
                    Socks5ProxyRequestPublisher.this.publishProxyRequest(pr);

                    ctx.channel().eventLoop().execute(() -> {
                        if (ctx.pipeline().get(SocksRequestHandler.class) != null) {
                            ctx.pipeline().addLast(new TcpProxyMessageHandler(pr));
                            ctx.pipeline().remove(SocksRequestHandler.this);
                            ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, addressType));
                        }
                    });
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
                    ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.COMMAND_NOT_SUPPORTED, addressType));
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
                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
            } else {
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
        }
    }

}
