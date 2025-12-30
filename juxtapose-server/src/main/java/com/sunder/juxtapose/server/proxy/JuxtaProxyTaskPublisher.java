package com.sunder.juxtapose.server.proxy;

import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.auth.SimpleAuthenticationStrategy;
import com.sunder.juxtapose.common.handler.RelayMessageWriteEncoder;
import com.sunder.juxtapose.common.mesage.AuthRequestMessage;
import com.sunder.juxtapose.common.mesage.AuthResponseMessage;
import com.sunder.juxtapose.common.mesage.Message;
import com.sunder.juxtapose.common.mesage.PingMessage;
import com.sunder.juxtapose.common.mesage.PongMessage;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.CertComponent;
import com.sunder.juxtapose.server.ProxyCoreComponent;
import com.sunder.juxtapose.server.ProxyTaskPublisher;
import com.sunder.juxtapose.server.ProxyTaskRequest;
import com.sunder.juxtapose.server.conf.ServerConfig;
import com.sunder.juxtapose.server.session.ClientSession;
import com.sunder.juxtapose.server.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * @author : sunder
 * @date : 19:35 2025/08/26
 */
public class JuxtaProxyTaskPublisher extends BaseCompositeComponent<ProxyCoreComponent>
        implements ProxyTaskPublisher {
    public final static String NAME = "USER_DEF_PROXY_COMPONENT";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private boolean tls; // 是否开启ssl加密
    private String userName;
    private String password;
    private CertComponent certComponent;
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
        workGroup = Platform.createEventLoopGroup(4);
        serverSocketChannel = Platform.serverSocketChannelClass();

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
                            ChannelPipeline cp = channel.pipeline();
                            if (tls) {
                                cp.addLast(certComponent.getSslContext().newHandler(channel.alloc()));
                            }
                            cp.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                                    Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                            cp.addLast(RelayMessageWriteEncoder.INSTANCE);
                            cp.addLast(new AuthMessageHandler());
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
     * 心跳检测处理
     */
    private class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private final SessionManager sessionManager;

        public HeartbeatHandler(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
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
         * 处理读取超时，现默认客户端已经断开，降低内存使用
         *
         * @param ctx io.netty.channel.ChannelHandlerContext
         */
        private void handleReaderIdle(ChannelHandlerContext ctx) {
            String sessionId = ctx.channel().id().asShortText();
            ClientSession session = sessionManager.getSession(sessionId);
            session.close();
        }

    }

    /**
     * 鉴权处理器
     */
    private class AuthMessageHandler extends ChannelInboundHandlerAdapter {
        private final boolean auth;
        private AuthenticationStrategy authStrategy;

        public AuthMessageHandler() {
            this.auth = JuxtaProxyTaskPublisher.this.auth;
            if (this.auth) {
                this.authStrategy = new SimpleAuthenticationStrategy(
                        JuxtaProxyTaskPublisher.this.userName, JuxtaProxyTaskPublisher.this.password);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;

                byte serviceId = byteBuf.getByte(byteBuf.readerIndex());
                if (serviceId == AuthRequestMessage.SERVICE_ID) {
                    AuthRequestMessage message = new AuthRequestMessage(byteBuf);
                    if (!auth || authStrategy.checkPermission(message.getUserName(), message.getPassword())) {
                        ctx.writeAndFlush(new AuthResponseMessage(true)).addListener(f -> logger.info("write auth "
                                + "response success!"));
                    } else {
                        AuthResponseMessage authMsg = new AuthResponseMessage(false, "401");
                        ctx.writeAndFlush(authMsg).addListener(ChannelFutureListener.CLOSE);
                    }
                } else {
                    ctx.fireChannelRead(msg);
                }
            } else {
                ctx.fireChannelRead(msg);
            }

        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error(cause.getMessage(), cause);
            ctx.close();
        }
    }

    /**
     * 从客户端传过来的代理中继消息，包含着需要的连接信息
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {

        public ProxyRelayMessageHandler() {
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
                ByteBuf byteBuf = (ByteBuf) msg;
                byte serviceId = byteBuf.getByte(byteBuf.readerIndex());
                if (serviceId == PingMessage.SERVICE_ID) {
                    new PingMessage(byteBuf);
                    ctx.writeAndFlush(new PongMessage(), ctx.voidPromise());
                } else if (serviceId == PongMessage.SERVICE_ID) {
                    new PongMessage(byteBuf);
                } else if (serviceId == ProxyRequestMessage.SERVICE_ID) {
                    ProxyRequestMessage message = new ProxyRequestMessage(byteBuf);

                    ProxyTaskRequest request = new ProxyTaskRequest(ProxyProtocol.JUXTA, message, ctx.channel());
                    JuxtaProxyTaskPublisher.this.publishProxyTask(request);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }

}