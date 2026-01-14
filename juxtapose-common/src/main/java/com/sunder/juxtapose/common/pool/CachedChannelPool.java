package com.sunder.juxtapose.common.pool;

import cn.hutool.core.thread.ThreadFactoryBuilder;
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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : denglh
 * @date : 12:51 2026/1/1
 *         <p>
 *         整体类似CachedThreadPool，优先从空闲队列获取连接（LIFO策略，最近使用的连接更可能保持热度）,只有当没有空闲连接时才创建新连接
 */
public abstract class CachedChannelPool implements ChannelPool {
    // 最大连接保持数, 默认Integer.MAX_VALUE， 理论上达不到，因为需要保证一个connection对应一个channel
    protected final int maximumPoolSize;
    // 最大空闲时间（毫秒），超过此时间的空闲连接会被回收
    protected final long maxIdleTime;

    protected final Logger logger;
    protected final Bootstrap bootstrap;
    protected final EventLoopGroup group;

    // 活跃连接数（正在使用的连接）
    private final AtomicInteger activeCount = new AtomicInteger(0);
    // 空闲连接数
    private final AtomicInteger idleCount = new AtomicInteger(0);
    // 总连接数（活跃 + 空闲）
    private final AtomicInteger totalCount = new AtomicInteger(0);

    // 空闲连接队列
    private final ConcurrentLinkedDeque<IdleChannel> idleQueue = new ConcurrentLinkedDeque<>();
    // 活跃连接集合 channel --> 刚进queue时的时间
    private final ConcurrentHashMap<Channel, Long> activeChannels = new ConcurrentHashMap<>();

    // 清理空闲连接的调度器
    private ScheduledExecutorService idleCleaner;
    // 是否已关闭
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public CachedChannelPool(Bootstrap bootstrap) {
        this(bootstrap, 60_000L);
    }

    /**
     * 完整构造函数
     *
     * @param bootstrap 引导类
     * @param maxIdleTime 最大空闲时间(ms)，-1表示永久保持
     */
    public CachedChannelPool(Bootstrap bootstrap, long maxIdleTime) {
        this.bootstrap = bootstrap.clone();
        this.group = bootstrap.config().group();
        this.maximumPoolSize = Integer.MAX_VALUE;
        this.maxIdleTime = maxIdleTime;
        this.logger = LoggerFactory.getLogger(CachedChannelPool.class);

        // 启动空闲连接清理任务
        startIdleCleaner();
    }

    /**
     * 启动空闲连接清理任务
     */
    private void startIdleCleaner() {
        if (maxIdleTime > 0) {
            idleCleaner = Executors.newSingleThreadScheduledExecutor(
                    ThreadFactoryBuilder.create().setNamePrefix("Cached-channel-cleaner-").setDaemon(true).build());

            // 每5秒检查一次空闲连接
            idleCleaner.scheduleAtFixedRate(() -> {
                try {
                    System.out.println("total: " + totalCount.get() + ", idle: " + idleCount.get() + ", activity:"
                            + activeCount.get());
                    cleanIdleChannels();
                } catch (Exception ex) {
                    logger.error("Error cleaning idle channels", ex);
                }
            }, maxIdleTime / 2, maxIdleTime / 2, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 清理过期空闲连接
     */
    private void cleanIdleChannels() {
        if (shutdown.get() || idleQueue.isEmpty()) {
            return;
        }

        long inActive = idleQueue.stream().filter(c -> !c.channel.isActive()).count();
        if (inActive > idleQueue.size() / 3) {
            // 优先清理前面的，前面的连接时间更长
            while (!idleQueue.isEmpty()) {
                IdleChannel idleChannel = idleQueue.pollFirst();
                if (idleChannel != null) {
                    if (idleChannel.isExpired(maxIdleTime)) {
                        closeChannelQuietly(idleChannel.channel);
                        totalCount.decrementAndGet();
                        idleCount.decrementAndGet();
                    } else {
                        idleQueue.offerLast(idleChannel);
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<Channel> acquire(ProxyRequest request, Connection connection) {
        CompletableFuture<Channel> future = new CompletableFuture<>();

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
    private void doAcquire(CompletableFuture<Channel> future,
            ProxyRequest request, Connection connection) {
        // 1. 首先尝试从空闲队列获取
        Channel idleChannel = tryAcquireIdleChannel();
        if (idleChannel != null) {
            logger.info("connection[{}] get idle channel successful.", connection.getConnectId());
            activeCount.incrementAndGet();
            activeChannels.put(idleChannel, System.currentTimeMillis());
            future.complete(idleChannel);
            return;
        }

        // 2. 如果没有空闲连接，检查是否可以立即创建新连接
        if (totalCount.get() < maximumPoolSize) {
            createNewChannelAsync(future, request, connection);
        } else {
            throw new RuntimeException("The maximum number of channels that can be created has been exceeded!");
        }
    }

    /**
     * 尝试从空闲队列获取连接, 优先从队列尾部获取，尾部链接时间短，比较“热”
     */
    private Channel tryAcquireIdleChannel() {
        CachedChannelPool.IdleChannel idleChannel;
        while ((idleChannel = idleQueue.pollLast()) != null) {
            Channel channel = idleChannel.channel;

            // 检查连接是否有效
            idleCount.decrementAndGet();
            if (isChannelValid(channel)) {
                return channel;
            } else {
                // 移除无效连接
                totalCount.decrementAndGet();
            }
        }
        return null;
    }

    /**
     * 异步创建新连接
     */
    private void createNewChannelAsync(CompletableFuture<Channel> future,
            ProxyRequest request, Connection connection) {
        // 再次检查，避免重复创建
        Channel idleChannel = tryAcquireIdleChannel();
        if (idleChannel != null) {
            activeCount.incrementAndGet();
            activeChannels.put(idleChannel, System.currentTimeMillis());
            future.complete(idleChannel);
            return;
        }

        // 创建新连接
        createNewChannel0(request, connection).addListener((ChannelFutureListener) cf -> {
            if (cf.isSuccess()) {
                Channel channel = cf.channel();
                totalCount.incrementAndGet();
                activeCount.incrementAndGet();
                activeChannels.put(channel, System.currentTimeMillis());
                future.complete(channel);
            } else if (!future.isDone()) {
                future.completeExceptionally(cf.cause());
            }
        });

    }

    @Override
    public ChannelFuture release(Channel channel) {
        if (channel == null || shutdown.get()) {
            return channel != null ? channel.close() : new VoidChannelPromise(null, true);
        }

        // 从活跃集合中移除
        if (activeChannels.remove(channel) != null) {
            activeCount.decrementAndGet();
        }

        IdleChannel idleChannel = new IdleChannel(channel);
        // 检查连接是否仍然有效
        if (!isChannelValid(channel)) {
            totalCount.decrementAndGet();
            if (idleQueue.remove(idleChannel)) {
                idleCount.decrementAndGet();
            }
            return channel.close();
        }

        // 将有效连接放入空闲队列
        if (!idleQueue.contains(idleChannel) && idleQueue.offerLast(idleChannel)) {
            idleCount.incrementAndGet();
        }

        return new VoidChannelPromise(channel, true);
    }

    /**
     * 检查连接是否有效
     */
    private boolean isChannelValid(Channel channel) {
        return channel != null && channel.isActive();
    }

    /**
     * 安静地关闭连接（不抛出异常）
     */
    private void closeChannelQuietly(Channel channel) {
        if (channel != null && channel.isActive()) {
            try {
                channel.close().awaitUninterruptibly(1000);
            } catch (Exception ex) {
                logger.error("Error closing channel", ex);
            }
        }
    }

    @Override
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        // 停止空闲连接清理器
        if (idleCleaner != null) {
            idleCleaner.shutdown();
            try {
                idleCleaner.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 关闭所有空闲连接
        for (IdleChannel idleChannel : idleQueue) {
            closeChannelQuietly(idleChannel.channel);
        }
        idleQueue.clear();

        // 关闭所有活跃连接
        for (Channel channel : activeChannels.keySet()) {
            closeChannelQuietly(channel);
        }
        activeChannels.clear();

        // 重置计数器
        activeCount.set(0);
        totalCount.set(0);
    }


    /**
     * 创建一个新的连接
     *
     * @param request 代理请求
     * @param connection 逻辑连接
     * @return channelFuture
     */
    protected abstract ChannelFuture createNewChannel0(ProxyRequest request, Connection connection);

    /**
     * 空闲连接封装类
     */
    private static class IdleChannel {
        final Channel channel;
        final long idleSince; // 空闲开始时间

        IdleChannel(Channel channel) {
            this.channel = channel;
            this.idleSince = System.currentTimeMillis();
        }

        boolean isExpired(long maxIdleTime) {
            return maxIdleTime > 0 &&
                    (System.currentTimeMillis() - idleSince) > maxIdleTime;
        }

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            IdleChannel that = (IdleChannel) object;
            return Objects.equals(channel, that.channel);
        }

        @Override
        public int hashCode() {
            return channel.id().hashCode();
        }
    }

}
