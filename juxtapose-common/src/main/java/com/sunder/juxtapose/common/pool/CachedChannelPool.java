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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author : denglh
 * @date : 12:51 2026/1/1
 *
 * 整体类似CachedThreadPool，优先从空闲队列获取连接（LIFO策略，最近使用的连接更可能保持热度）,只有当没有空闲连接时才创建新连接
 */
public abstract class CachedChannelPool implements ChannelPool {
    // 核心连接数（空闲时保留的最小连接数）
    private final int corePoolSize;
    // 最大连接保持数
    protected final int maximumPoolSize;
    // 最大空闲时间（毫秒），超过此时间的空闲连接会被回收
    private final long maxIdleTime;
    // 连接创建超时时间
    private final long connectionTimeoutMs;

    protected final Logger logger;
    protected final Bootstrap bootstrap;
    protected final EventLoopGroup group;

    // 活跃连接数（正在使用的连接）
    private final AtomicInteger activeCount = new AtomicInteger(0);
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
    // 锁，用于创建连接时的同步
    private final ReentrantLock createLock = new ReentrantLock();

    // 如果连接数达到理论最大值，将请求加入等待队列
    private final LinkedBlockingQueue<ConnectionRequest> pendingRequests = new LinkedBlockingQueue<>();

    /**
     * 连接请求封装类
     */
    private static class ConnectionRequest {
        final CompletableFuture<Channel> future;
        final ProxyRequest request;
        final Connection connection;

        ConnectionRequest(CompletableFuture<Channel> future, ProxyRequest request, Connection connection) {
            this.future = future;
            this.request = request;
            this.connection = connection;
        }
    }

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
    }


    public CachedChannelPool(Bootstrap bootstrap) {
        this(bootstrap, 10, 30000, 5000);
    }

    /**
     * 完整构造函数
     *
     * @param bootstrap           引导类
     * @param corePoolSize        核心连接数（空闲时保留的最小连接数）
     * @param maxIdleTime         最大空闲时间(ms)，-1表示永久保持
     * @param connectionTimeoutMs 连接创建超时时间(ms)
     */
    public CachedChannelPool(Bootstrap bootstrap, int corePoolSize,
                             long maxIdleTime, long connectionTimeoutMs) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be positive");
        }
        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("connectionTimeoutMs must be positive");
        }

        this.bootstrap = bootstrap.clone();
        this.group = bootstrap.config().group();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = Integer.MAX_VALUE;
        this.maxIdleTime = maxIdleTime;
        this.connectionTimeoutMs = connectionTimeoutMs;
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
                    System.out.println("total: " + totalCount.get() + ", idle: " + idleQueue.size() + ", activity:" + activeChannels.size());
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

        int idleSize = idleQueue.size();
        int targetSize = Math.max(corePoolSize, activeCount.get() * 2); // 保留一些缓冲

        // 如果空闲连接过多，清理掉一些, 优先清理前面的，前面的连接时间更长
        while (idleSize > targetSize && !idleQueue.isEmpty()) {
            IdleChannel idleChannel = idleQueue.pollFirst();
            if (idleChannel != null) {
                // 清理过期连接
                if (idleChannel.isExpired(maxIdleTime)) {
                    closeChannelQuietly(idleChannel.channel);
                    totalCount.decrementAndGet();
                    idleSize--;
                } else {
                    // 放回队列末尾， 后面的连接时间更短
                    idleQueue.offerLast(idleChannel);
                    break;
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
            // 如果连接数达到理论最大值，将请求加入等待队列
            pendingRequests.offer(new CachedChannelPool.ConnectionRequest(future, request, connection));
            processPendingRequests();
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
            if (isChannelValid(channel)) {
                return channel;
            } else {
                // 移除无效连接
                closeChannelQuietly(channel);
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
        try {
            createLock.lock();

            // 再次检查，避免重复创建
            Channel idleChannel = tryAcquireIdleChannel();
            if (idleChannel != null) {
                activeCount.incrementAndGet();
                activeChannels.put(idleChannel, System.currentTimeMillis());
                future.complete(idleChannel);
                return;
            }

            // 创建新连接
            ChannelFuture channelFuture = createNewChannel0(request, connection);

            // // 设置超时
            // ScheduledFuture<?> timeoutFuture = group.next().schedule(() -> {
            //     if (!future.isDone()) {
            //         future.completeExceptionally(new TimeoutException(
            //                 "Connection creation timeout after " + connectionTimeoutMs + "ms"));
            //     }
            // }, connectionTimeoutMs, TimeUnit.MILLISECONDS);

            channelFuture.addListener((ChannelFutureListener) cf -> {
               // timeoutFuture.cancel(false); // 取消超时任务

                if (cf.isSuccess()) {
                    Channel channel = cf.channel();
                    totalCount.incrementAndGet();
                    activeCount.incrementAndGet();
                    activeChannels.put(channel, System.currentTimeMillis());
                    future.complete(channel);
                } else if (!future.isDone()) {
                    future.completeExceptionally(cf.cause());
                    // 创建失败，尝试处理等待队列中的请求
                    processPendingRequests();
                }
            });
        } finally {
            createLock.unlock();
        }
    }

    /**
     * 处理等待队列中的连接请求
     */
    private void processPendingRequests() {
        if (pendingRequests.isEmpty()) {
            return;
        }

        group.next().execute(() -> {
            while (!pendingRequests.isEmpty()) {
                ConnectionRequest req = pendingRequests.poll();
                if (req != null) {
                    doAcquire(req.future, req.request, req.connection);
                }
            }
        });
    }

    @Override
    public ChannelFuture release(Channel channel) {
        if (channel == null || shutdown.get()) {
            return channel != null ? channel.close() : new VoidChannelPromise(null, true);
        }

        // 从活跃集合中移除
        activeChannels.remove(channel);
        activeCount.decrementAndGet();

        // 检查连接是否仍然有效
        if (!isChannelValid(channel)) {
            closeChannelQuietly(channel);
            totalCount.decrementAndGet();
            processPendingRequests(); // 尝试处理等待的请求
            return channel.close();
        }

        // 将有效连接放入空闲队列
        idleQueue.offerLast(new IdleChannel(channel));

        // 处理等待队列中的请求
        processPendingRequests();

        return new VoidChannelPromise(channel, true);
    }

    /**
     * 检查连接是否有效
     */
    private boolean isChannelValid(Channel channel) {
        return channel != null && channel.isActive() && channel.isOpen();
    }

    /**
     * 安静地关闭连接（不抛出异常）
     */
    private void closeChannelQuietly(Channel channel) {
        if (channel != null && channel.isOpen()) {
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

        // 清空等待队列
        pendingRequests.clear();

        // 重置计数器
        activeCount.set(0);
        totalCount.set(0);
    }

    /**
     * 强制回收所有空闲连接（用于内存紧张等情况）
     */
    public void evictIdleChannels() {
        if (shutdown.get()) {
            return;
        }

        int initialIdleSize = idleQueue.size();
        int targetSize = Math.max(corePoolSize / 2, activeCount.get()); // 保留少量缓冲

        while (idleQueue.size() > targetSize) {
            IdleChannel idleChannel = idleQueue.pollFirst();
            if (idleChannel != null) {
                closeChannelQuietly(idleChannel.channel);
                totalCount.decrementAndGet();
            } else {
                break;
            }
        }

        logger.info("Evicted " + (initialIdleSize - idleQueue.size()) + " idle channels");
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
