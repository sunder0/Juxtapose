package com.sunder.juxtapose.server.proxy;

import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
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
import com.sunder.juxtapose.server.session.SessionState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * @author : denglinhai
 * @date : 19:35 2025/08/26
 */
public class JuxtaProxyTaskPublisher extends BaseCompositeComponent<ProxyCoreComponent> implements ProxyTaskPublisher {
    public final static String NAME = "USER_DEF_PROXY_COMPONENT";

    private String host;
    private int port;
    private boolean auth; // 是否开启了鉴权
    private String userName;
    private String password;
    private CertComponent certComponent;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    private SessionManager sessionManager;

    public JuxtaProxyTaskPublisher(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
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

        bossGroup = new NioEventLoopGroup(1);
        workGroup = new NioEventLoopGroup(4);

        sessionManager = getModuleByName(SessionManager.NAME, true, SessionManager.class);
        certComponent = getParentComponent().getChildComponentByName(CertComponent.NAME, CertComponent.class);

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline cp = channel.pipeline();
                            cp.addLast(certComponent.getSslContext().newHandler(channel.alloc()));
                            cp.addLast(new LengthFieldBasedFrameDecoder(Message.LENGTH_MAX_FRAME,
                                    Message.LENGTH_FILED_OFFSET, Message.LENGTH_FILED_LENGTH, 0, 0));
                            cp.addLast(RelayMessageWriteEncoder.INSTANCE);
                            cp.addLast(new ClientSessionHandler());
                            cp.addLast(new ProxyRelayMessageHandler());
                        }
                    });
            boot.bind(host, port).addListener(f -> {
                if (!f.isSuccess()) {
                    logger.error("Proxy server start failure, address:[{}:{}]", host, port, f.cause());
                } else {
                    logger.info("Proxy server start success, address:[{}:{}]", host, port);
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

    private class ClientSessionHandler extends ChannelInboundHandlerAdapter {
        private final boolean auth;
        private final SessionManager sessionManager;
        private AuthenticationStrategy authStrategy;

        public ClientSessionHandler() {
            this.auth = JuxtaProxyTaskPublisher.this.auth;
            this.sessionManager = JuxtaProxyTaskPublisher.this.sessionManager;
            if (this.auth) {
                this.authStrategy = new SimpleAuthenticationStrategy(
                        JuxtaProxyTaskPublisher.this.userName, JuxtaProxyTaskPublisher.this.password);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String sessionId = ctx.channel().id().asShortText();
            ClientSession session = ClientSession.buildChannelBoundSession(sessionId, (SocketChannel) ctx.channel());
            sessionManager.addSession(session);

            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf byteBuf = (ByteBuf) msg;

                byte serviceId = byteBuf.getByte(byteBuf.readerIndex());
                if (serviceId == AuthRequestMessage.SERVICE_ID) {
                    ClientSession session = sessionManager.getSession(ctx.channel().id().asShortText());
                    if (session.getState() != SessionState.CONNECTED
                            && session.getState() != SessionState.AUTHENTICATING) {
                        ctx.writeAndFlush(new AuthResponseMessage(false, "repeat authentication"));
                    }

                    session.changeState(SessionState.AUTHENTICATING);

                    AuthRequestMessage message = new AuthRequestMessage(byteBuf);
                    if (!auth || authStrategy.checkPermission(message.getUserName(), message.getPassword())) {
                        session.changeState(SessionState.AUTHENTICATED);
                        ctx.writeAndFlush(new AuthResponseMessage(true));
                    } else {
                        session.changeState(SessionState.DISCONNECTED);
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
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            String sessionId = ctx.channel().id().asShortText();
            ClientSession session = sessionManager.removeSession(sessionId);

            if (session != null && session.getState() != SessionState.CLOSED) {
                session.changeState(SessionState.DISCONNECTED);
                ctx.channel().attr(ClientSession.SESSION_KEY).set(null);
            }

            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ClientSession session = sessionManager.getSession(ctx.channel().id().asShortText());
            if (session != null) {
                logger.error("Exception caught for session:[{}].", session.getSessionId(), cause);
                session.changeState(SessionState.DISCONNECTED);
            }
            ctx.close();
        }
    }

    /**
     * 从客户端传过来的代理中继消息，包含着需要的连接信息
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {

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

                    SessionManager sessionManager = JuxtaProxyTaskPublisher.this.sessionManager;
                    ClientSession clientSession = sessionManager.getSession(ctx.channel().id().asShortText());
                    clientSession.updateActivityTime();

                    ProxyTaskRequest request = new ProxyTaskRequest(ProxyProtocol.JUXTA, message, clientSession);
                    JuxtaProxyTaskPublisher.this.publishProxyTask(request);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

    }

}