package com.sunder.juxtapose.client.publisher;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestPublisher;
import com.sunder.juxtapose.client.TcpProxyMessageHandler;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
 * @date : 16:32 2025/07/19
 */
public class Socks5ProxyRequestPublisher extends BaseComponent<ProxyCoreComponent> implements ProxyRequestPublisher {
    public final static String NAME = "SOCKS5_PROXY_REQUEST_PUBLISHER";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private String userName;
    private String password;
    private EventLoopGroup eventLoopGroup;

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

        this.eventLoopGroup = new NioEventLoopGroup(3);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
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
        private String username;
        private String password;
        private boolean authPass; // 鉴权通过

        public SocksRequestHandler() {
            this.auth = Socks5ProxyRequestPublisher.this.auth;
            this.authPass = !this.auth;
            this.username = Socks5ProxyRequestPublisher.this.userName;
            this.password = Socks5ProxyRequestPublisher.this.password;
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
                        ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FORBIDDEN, SocksAddressType.IPv4))
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

            switch (request.cmdType()) {
                case CONNECT: {
                    logger.info("connect cmd...");
                    ProxyRequest pr = new ProxyRequest(host, port, ctx.channel());
                    Socks5ProxyRequestPublisher.this.publishProxyRequest(pr);

                    ctx.pipeline().addLast(new TcpProxyMessageHandler(pr)).remove(this);
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

            if (request.username().equals(username) && request.password().equals(password)) {
                logger.info("Socks5 auth success.");
                authPass = true;
                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
            } else {
                ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

}
