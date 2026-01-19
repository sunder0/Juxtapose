package com.sunder.juxtapose.server;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.proxy.ProxyMessageReceiver;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.server.connection.UpstreamConnection;
import com.sunder.juxtapose.server.connection.UpstreamConnectionManager;
import com.sunder.juxtapose.server.handler.ProxyTaskHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : sunder
 * @date : 11:40 2023/7/10
 *         上游tcp消息分发器，把被代理的消息真实发送给目标服务器
 */
public class TcpProxyDispatchComponent extends BaseCompositeComponent<ProxyCoreComponent> {
    public final static String NAME = "TCP_PROXY_DISPATCHER";

    private final Bootstrap bootstrap;
    private final UpstreamConnectionManager connManager;
    private ExecutorService dispatcherExecutor;
    private final List<ProxyTaskSubscriber> proxySubscribers = new CopyOnWriteArrayList<>();

    public TcpProxyDispatchComponent(ProxyCoreComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);

        this.bootstrap = new Bootstrap().group(Platform.createEventLoopGroup(8))
                .channel(Platform.socketChannelClass())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)       // 禁用Nagle算法， 提高响应速度
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        this.connManager = getModuleByName(UpstreamConnectionManager.NAME, true, UpstreamConnectionManager.class);
    }

    @Override
    protected void initInternal() {
        int cpus = Runtime.getRuntime().availableProcessors();
        this.dispatcherExecutor = new ThreadPoolExecutor(
                cpus,
                cpus,
                10,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("Tcp-Proxy-Dispatcher-").build(),
                new ThreadPoolExecutor.DiscardPolicy());

        for (int i = 0; i < cpus; i++) {
            this.dispatcherExecutor.execute(new ProxyTask());
        }

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        super.startInternal();
    }

    @Override
    protected void stopInternal() {
        super.stopInternal();
    }

    @Override
    protected void destroyInternal() {
        this.dispatcherExecutor.shutdownNow();

        super.destroyInternal();
    }

    public void publishProxyTask(ProxyTaskRequest request) {
        int size = proxySubscribers.size();
        int hash = request.hashCode();
        proxySubscribers.get(Math.abs(hash % size)).subscribe(request);
    }

    /**
     * 代理任务，打开一个对外连接
     */
    private class ProxyTask implements Runnable, ProxyTaskSubscriber, ProxyMessageReceiver {
        private final BlockingQueue<ProxyTaskRequest> taskQueue;

        public ProxyTask() {
            this.taskQueue = new ArrayBlockingQueue<>(128);
            TcpProxyDispatchComponent.this.proxySubscribers.add(this);
        }

        @Override
        public void run() {
            try {
                Thread thread = Thread.currentThread();
                while (!thread.isInterrupted()) {
                    final ProxyTaskRequest request = ProxyTask.this.taskQueue.poll(20, TimeUnit.MILLISECONDS);
                    if (request == null) {
                        continue;
                    }

                    boolean connected = connManager.containsConnection(request.getSerialId().toString());
                    if (!connected) {
                        logger.info("start proxy connect[{}] real server[{}].", request.getSerialId(),
                                request.getHost());

                        ProxyRequest proxy = new ProxyRequest(request.getProtocol(), request.getSerialId(),
                                request.getHost(), request.getPort(), request.getClientChannel());
                        UpstreamConnection connection = connManager.createConnection(request.getProtocol(), proxy);

                        ChannelFuture channelFuture = bootstrap.clone()
                                .handler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel channel) {
                                        channel.pipeline().addLast(new UpstreamHeartbeatHandler(connection));
                                        channel.pipeline().addLast(new ProxyTaskHandler(proxy, connection));
                                    }
                                }).connect(request.getHost(), request.getPort());
                        channelFuture.addListener(new CompleteChannelFutureListen(proxy, connection, this));

                        // 将第一次收到的消息塞进消息缓存队列
                        ByteBuf firstBytebuf = request.getContent();
                        try {
                            proxy.transferMessage(firstBytebuf.retain());
                            connection.setChannelFuture(channelFuture);
                        } finally {
                            firstBytebuf.release();
                        }
                    } else {
                        logger.debug("reuse proxy connection[{}]", request.getHost());
                        UpstreamConnection connection =
                                (UpstreamConnection) connManager.getConnection(request.getSerialId().toString());
                        if (connection == null) {
                            continue;
                        }

                        ProxyRequest proxy = connection.getProxyRequest();
                        ChannelFuture cf = connection.getChannelFuture();
                        if (!cf.isDone() || (cf.isDone() && cf.isSuccess())) {
                            ByteBuf byteBuf = request.getContent();
                            try {
                                proxy.transferMessage(byteBuf.retain());
                            } finally {
                                byteBuf.release();
                            }
                        }
                    }
                }
            } catch (InterruptedException ex) {
                logger.error("Proxy task thread interrupted, {}", ex.getMessage(), ex);
            } finally {
                proxySubscribers.remove(this);
            }
        }

        @Override
        public void receive(Long serialId, ByteBuf message) {
            Connection connection = connManager.getConnection(serialId.toString());
            connection.writeMessage(message);
        }

        @Override
        public void subscribe(ProxyTaskRequest request) {
            boolean result = taskQueue.offer(request);
        }

    }

    /**
     * 对connect的监听，主要做两件事：
     * 1.判断是否连接目标服务器是否成功
     * 2.激活connection，开始传输数据到目标服务器
     */
    private class CompleteChannelFutureListen implements ChannelFutureListener {
        private final ProxyRequest request;
        private final Connection connection;
        private final ProxyTask proxyTask;

        public CompleteChannelFutureListen(ProxyRequest request, UpstreamConnection connection,
                ProxyTask proxyTask) {
            this.request = request;
            this.connection = connection;
            this.proxyTask = proxyTask;
        }

        @Override
        public void operationComplete(ChannelFuture cf) throws Exception {
            if (cf.isSuccess()) {
                connection.bindProxyChannel((SocketChannel) cf.channel());
                connection.activeMessageTransfer(proxyTask);
                logger.info("Connect to the real server:[{}:{}] successfully, serialId[{}].",
                        request.getHost(), request.getPort(), request.getSerialId());
            } else {
                logger.info("Connect to the real server:[{}:{}] failed, serialId[{}].",
                        request.getHost(), request.getPort(), request.getSerialId(), cf.cause());
                connection.close();
            }
        }
    }

    /**
     * 心跳检测处理
     */
    private class UpstreamHeartbeatHandler extends ChannelInboundHandlerAdapter {
        private final Connection connection;

        public UpstreamHeartbeatHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // 90秒读空闲，超过视作关闭连接
            ctx.pipeline().addLast(new IdleStateHandler(90, 0, 0, TimeUnit.SECONDS));
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
         * 处理读取超时，现默认服务端已经断开，降低内存使用
         *
         * @param ctx io.netty.channel.ChannelHandlerContext
         */
        private void handleReaderIdle(ChannelHandlerContext ctx) {
            logger.warn("upstream connection[{}] reader idle "
                    + "timeout, will close connection.", connection.getConnectId());
            connection.close();
        }

    }

}
