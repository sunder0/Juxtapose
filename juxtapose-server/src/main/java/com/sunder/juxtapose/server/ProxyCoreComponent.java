package com.sunder.juxtapose.server;


import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.handler.RelayMessageWriteEncoder;
import com.sunder.juxtapose.common.mesage.Message;
import com.sunder.juxtapose.common.mesage.PingMessage;
import com.sunder.juxtapose.common.mesage.PongMessage;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.conf.ServerConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;


/**
 * @author : denglinhai
 * @date : 22:24 2025/07/21
 */
public final class ProxyCoreComponent extends BaseCompositeComponent<com.sunder.juxtapose.server.ServerBootstrap> {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    private String host;
    private int port;
    private CertComponent certComponent;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    private SocketChannel socketChannel;
    private TcpProxyDispatchComponent dispatcher;

    public ProxyCoreComponent(com.sunder.juxtapose.server.ServerBootstrap parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        this.host = cfg.getRelayServerHost();
        this.port = cfg.getRelayServerPort();

        bossGroup = new NioEventLoopGroup(1);
        workGroup = new NioEventLoopGroup(4);

        certComponent = new CertComponent(this);
        addChildComponent(certComponent);

        dispatcher = new TcpProxyDispatchComponent(this);
        addChildComponent(dispatcher);

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
    protected void destroyInternal() {
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();

        super.destroyInternal();
    }

    /**
     * 从客户端传过来的代理中继消息，包含着需要的连接信息
     */
    private class ProxyRelayMessageHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ProxyCoreComponent.this.socketChannel = (SocketChannel) ctx.channel();
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
                    ProxyTaskRequest request = new ProxyTaskRequest(message, ProxyCoreComponent.this.socketChannel);

                    dispatcher.publishProxyTask(request);
                }
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
