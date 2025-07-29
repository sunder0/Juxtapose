package com.sunder.juxtapose.server;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

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
    private class ProxyTask implements Runnable, ProxyTaskSubscriber {
        private final BlockingQueue<ProxyTaskRequest> taskQueue;
        private final ConcurrentActiveConMap activeConnects;

        public ProxyTask() {
            this.taskQueue = new ArrayBlockingQueue<>(16);
            this.activeConnects = new ConcurrentActiveConMap();
            TcpProxyDispatchComponent.this.proxySubscribers.add(this);
        }

        @Override
        public void run() {
            try {
                Thread thread = Thread.currentThread();
                while (!thread.isInterrupted()) {
                    final ProxyTaskRequest request = ProxyTask.this.taskQueue.poll(2, TimeUnit.SECONDS);
                    if (request == null) {
                        continue;
                    }
                    final ProxyRequestMessage message = request.getMessage();

                    ActiveProxyConnection con = new ActiveProxyConnection(
                            message.getHost(), message.getPort(), message.getSerialId());

                    boolean connected = activeConnects.contains(request.getClientChannel(), message.getSerialId());

                    if (!connected) {
                        Bootstrap bootstrap = new Bootstrap();
                        bootstrap.group(new NioEventLoopGroup(2))
                                .channel(NioSocketChannel.class)
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.AUTO_CLOSE, true)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000);

                        ChannelFuture channelFuture = bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) {
                                ChannelPipeline pipeline = socketChannel.pipeline();
                                pipeline.addLast(new ProxyTaskHandler(request));
                            }

                        }).connect(message.getHost(), message.getPort()).addListener(
                                f -> {
                                    if (f.isSuccess()) {
                                        logger.info(
                                                "[{}]Proxy server successfully connects to the target server:[{}:{}].",
                                                message.getSerialId(), message.getHost(), message.getPort());
                                    } else {
                                        logger.info("[{}]Proxy server failed to connect to the target server:[{}:{}].",
                                                message.getSerialId(), message.getHost(), message.getPort(), f.cause());
                                    }
                                });

                        con.setChannelFuture(channelFuture);
                        activeConnects.put(request.getClientChannel(), con);
                    } else {
                        logger.info("Reuse proxy connection[{}]", request.getMessage().getHost());
                        con = activeConnects.get(request.getClientChannel(), message.getSerialId());
                        ChannelFuture cf = con.getChannelFuture();
                        if (cf.isDone() && cf.isSuccess()) {
                            if (cf.channel().isActive()) {
                                ByteBuf content;
                                while ((content = con.getCache().poll()) != null) {
                                    cf.channel().writeAndFlush(content);
                                }
                                cf.channel().writeAndFlush(message.getContent());
                            }
                        } else if (!cf.isDone()) {
                            con.getCache().offer(message.getContent());
                        } else {
                            // 连接失败
                            activeConnects.remove(request.getClientChannel(), con.getSerialId());
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
            taskQueue.offer(request);
        }

    }

}
