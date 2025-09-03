package com.sunder.juxtapose.server;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.handler.ProxyTaskHandler;
import com.sunder.juxtapose.server.session.ClientSession;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

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
 * @author : denglinhai
 * @date : 11:40 2023/7/10
 */
public class TcpProxyDispatchComponent extends BaseCompositeComponent<ProxyCoreComponent> {
    public final static String NAME = "TCP_PROXY_DISPATCHER";

    private ExecutorService dispatcherExecutor;
    private final List<ProxyTaskSubscriber> proxySubscribers = new CopyOnWriteArrayList<>();

    public TcpProxyDispatchComponent(ProxyCoreComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
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
    private class ProxyTask implements Runnable, ProxyTaskSubscriber, Platform {
        private final BlockingQueue<ProxyTaskRequest> taskQueue;
        // todo: 需要清理代理关闭的链接
        private final ConcurrentActiveConMap activeConnects;

        public ProxyTask() {
            this.taskQueue = new ArrayBlockingQueue<>(64);
            this.activeConnects = new ConcurrentActiveConMap();
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
                    final ProxyRequestMessage message = request.getMessage();
                    final ClientSession clientSession = request.getClientSession();

                    ActiveProxyConnection conn = new ActiveProxyConnection(
                            message.getHost(), message.getPort(), message.getSerialId());

                    boolean connected = activeConnects.contains(clientSession, message.getSerialId());

                    if (!connected) {
                        logger.info("start proxy connection[{}]", request.getMessage().getHost());
                        Bootstrap bootstrap = new Bootstrap();

                        bootstrap.group(createEventLoopGroup(2))
                                .channel(getSocketChannelClass())
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.AUTO_CLOSE, true)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                                .handler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel socketChannel) {
                                        ChannelPipeline pipeline = socketChannel.pipeline();
                                        pipeline.addLast(new ProxyTaskHandler(request));
                                    }
                                });

                        ChannelFuture channelFuture = bootstrap.connect(message.getHost(), message.getPort())
                                .addListener(new CompleteChannelFutureListen(message, conn));
                        conn.setChannelFuture(channelFuture);
                        activeConnects.put(clientSession, conn);
                    } else {
                        logger.info("reuse proxy connection[{}]", request.getMessage().getHost());
                        conn = activeConnects.get(clientSession, message.getSerialId());
                        if (conn == null) {
                            continue;
                        }

                        ChannelFuture cf = conn.getChannelFuture();
                        if (cf.isDone() && cf.isSuccess()) {
                            if (cf.channel().isActive()) {
                                ByteBuf content;
                                while ((content = conn.getCache().poll()) != null) {
                                    cf.channel().writeAndFlush(content);
                                }
                                cf.channel().writeAndFlush(message.getContent());
                            }
                        } else if (!cf.isDone()) {
                            conn.getCache().offer(message.getContent());
                        } else {
                            // 连接失败
                            activeConnects.remove(clientSession, conn.getSerialId());
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
        public void subscribe(ProxyTaskRequest request) {
            boolean result = taskQueue.offer(request);
        }

    }

    /**
     * 对connect的监听，主要做两件事：
     * 1。是将第一条消息在connect成功后立马发出去
     * 2。是看是否在connect期间有累计的消息，有的话也发送出去
     */
    private class CompleteChannelFutureListen implements ChannelFutureListener {
        private ProxyRequestMessage message;
        private ActiveProxyConnection conn;

        public CompleteChannelFutureListen(ProxyRequestMessage message, ActiveProxyConnection conn) {
            this.message = message;
            this.conn = conn;
        }

        @Override
        public void operationComplete(ChannelFuture channelFuture) throws Exception {
            if (channelFuture.isSuccess()) {
                logger.info(
                        "[{}]Proxy server successfully connects to the target server:[{}:{}].",
                        message.getSerialId(), message.getHost(), message.getPort());
                ByteBuf content = message.getContent();
                do {
                    if (content != null) {
                        channelFuture.channel().writeAndFlush(content);
                    }
                } while ((content = conn.getCache().poll()) != null);
            } else {
                logger.info("[{}]Proxy server failed to connect to the target server:[{}:{}].",
                        message.getSerialId(), message.getHost(), message.getPort(), channelFuture.cause());
            }
        }
    }

}
