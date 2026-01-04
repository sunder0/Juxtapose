package com.sunder.juxtapose.common.pool;

import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.VoidChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : denglinhai
 * @date : 10:49 2025/12/31
 * 保持固定连接数量的连接池, 未达到最大数量前一直创建连接，达到后从池中取用，即类似Semaphore机制
 */
public abstract class FixedChannelPool implements ChannelPool {
    protected final Logger logger;
    // 最大连接保持数
    protected final int maximumPoolSize;
    // 连接空闲时长保活时间（单位：毫秒）
    protected final long keepAliveTime;
    protected final Bootstrap bootstrap;
    protected final EventLoopGroup group;

    protected final AtomicInteger channelSize = new AtomicInteger(0);
    protected final List<Channel> pooled;

    // 连接超时时间（可配置）
    private final long connectionTimeoutMs;
    // 是否已关闭
    private final AtomicBoolean shutdown = new AtomicBoolean(false);


    public FixedChannelPool(Bootstrap bootstrap, int maximumPoolSize) {
        this(bootstrap, maximumPoolSize, -1, 5000);
    }

    /**
     * 完整构造函数
     *
     * @param bootstrap           引导类
     * @param maximumPoolSize     最大连接数
     * @param keepAliveTime       保活时间(ms)，-1表示永久
     * @param connectionTimeoutMs 连接超时时间(ms)
     */
    public FixedChannelPool(Bootstrap bootstrap, int maximumPoolSize,
                            long keepAliveTime, long connectionTimeoutMs) {
        if (maximumPoolSize <= 0) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }
        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectionTimeoutMs must be positive");
        }

        this.logger = LoggerFactory.getLogger(FixedChannelPool.class);
        this.bootstrap = bootstrap.clone();
        this.group = bootstrap.config().group();
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.pooled = new CopyOnWriteArrayList<>();
    }

    @Override
    public CompletableFuture<Channel> acquire(ProxyRequest request, Connection connection) {
        CompletableFuture<Channel> future = new CompletableFuture<>();

        // 检查连接池状态
        if (shutdown.get()) {
            future.completeExceptionally(new IllegalStateException("Channel pool is shutdown"));
            return future;
        }

        try {
            doAcquire(future, request, connection);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 执行获取连接逻辑
     */
    private void doAcquire(CompletableFuture<Channel> future, ProxyRequest request, Connection connection) {
        if (channelSize.get() >= maximumPoolSize) {
            // 尝试从池中获取可用连接
            Channel channel = tryGetFromPool(request);
            if (channel != null) {
                future.complete(channel);
                return;
            } else {
                if (channelSize.get() < maximumPoolSize) {
                    // 创建新连接
                    createNewChannel(future, request, connection);
                    return;
                }
            }
        }

        // 创建新连接
        createNewChannel(future, request, connection);
    }

    /**
     * 尝试从连接池获取可用连接
     */
    private Channel tryGetFromPool(ProxyRequest request) {
        if (pooled.isEmpty()) {
            return null;
        }

        int index = selectChannelIndex(request);
        Channel channel = pooled.get(index);

        if (isChannelValid(channel)) {
            return channel;
        }

        // 移除无效连接
        removeInvalidChannel(channel);
        return null;
    }

    /**
     * 选择策略
     */
    private int selectChannelIndex(ProxyRequest request) {
        //todo: 可扩展不同的策略
        return Math.abs(request.hashCode() % pooled.size());
    }

    /**
     * 检查连接是否有效
     */
    private boolean isChannelValid(Channel channel) {
        return channel != null && channel.isActive() && channel.isOpen();
    }

    @Override
    public ChannelFuture release(Channel channel) {
        if (!isChannelValid(channel)) {
            return removeInvalidChannel(channel);
        }

        return new VoidChannelPromise(channel, true);
    }

    /**
     * 移除无效连接
     */
    private ChannelFuture removeInvalidChannel(Channel channel) {
        if (pooled.remove(channel)) {
            channelSize.decrementAndGet();
            try {
                return channel.close().syncUninterruptibly();
            } catch (Exception ex) {
                logger.error("Failed to close invalid channel", ex);
            }
        }
        return null;
    }

    @Override
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return; // 已经关闭
        }

        if (channelSize.get() <= 0) {
            return;
        }

        List<ChannelFuture> closeFutures = new ArrayList<>();
        for (Channel channel : pooled) {
            closeFutures.add(channel.close());
        }

        // 等待所有连接关闭完成
        try {
            closeFutures.forEach(ChannelFuture::getNow);
        } catch (Exception ex) {
            logger.error("Error during pool shutdown", ex);
        } finally {
            pooled.clear();
            channelSize.set(0);
        }
    }

    /**
     * 创建新连接
     */
    private void createNewChannel(CompletableFuture<Channel> future,
                                  ProxyRequest request, Connection connection) {
        if (shutdown.get()) {
            future.completeExceptionally(new IllegalStateException("Pool is shutting down"));
            return;
        }

        ChannelFuture channelFuture = createNewChannel0(request, connection);

        // 超时控制
        // group.next().schedule(() -> {
        //     if (!future.isDone()) {
        //         future.completeExceptionally(new TimeoutException(
        //                 "Connection creation timeout after " + connectionTimeoutMs + "ms"));
        //         channelFuture.cancel(true);
        //     }
        // }, connectionTimeoutMs, TimeUnit.MILLISECONDS);

        channelFuture.addListener((ChannelFutureListener) cf -> {
            if (cf.isSuccess()) {
                channelSize.incrementAndGet();
                pooled.add(cf.channel());
                future.complete(cf.channel());
            } else if (!future.isDone()) {
                future.completeExceptionally(cf.cause());
            }
        });
    }

    /**
     * 获取当前池大小
     */
    public int getPoolSize() {
        return channelSize.get();
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveCount() {
        return (int) pooled.stream()
                .filter(this::isChannelValid)
                .count();
    }


    /**
     * 创建一个新的连接
     *
     * @param request    代理请求
     * @param connection 逻辑连接
     * @return channelFuture
     */
    protected abstract ChannelFuture createNewChannel0(ProxyRequest request, Connection connection);
}
