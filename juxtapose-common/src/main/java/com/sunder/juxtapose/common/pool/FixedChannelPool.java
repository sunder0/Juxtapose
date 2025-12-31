package com.sunder.juxtapose.common.pool;

import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.VoidChannelPromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : denglinhai
 * @date : 10:49 2025/12/31
 *         保持固定连接数量的连接池
 */
public abstract class FixedChannelPool implements ChannelPool {
    // 最大连接保持数
    protected int maximumPoolSize;
    // 连接空闲时长保活时间
    protected long keepAliveTime;
    protected final EventLoopGroup group;
    protected final AtomicInteger channelSize = new AtomicInteger(0);
    protected final List<Channel> pooled = Collections.synchronizedList(new ArrayList<>(64));

    public FixedChannelPool(EventLoopGroup group, int maximumPoolSize, long keepAliveTime) {
        this.group = group;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
    }

    @Override
    public CompletableFuture<Channel> acquire(ProxyRequest request, Connection connection) {
        CompletableFuture<Channel> future = new CompletableFuture<>();

        group.next().execute(() -> {
            if (channelSize.get() >= maximumPoolSize) {
                int index = Math.abs(request.hashCode() % pooled.size());
                Channel channel = pooled.get(index);
                if (channel.isActive()) {
                    future.complete(channel);
                } else {
                    release(channel);
                    createNewChannel(future, request, connection);
                }
            } else {
                createNewChannel(future, request, connection);
            }
        });

        return future;
    }

    @Override
    public ChannelFuture release(Channel channel) {
        if (channel.isActive() || channelSize.get() <= 0) {
            return new VoidChannelPromise(channel, true);
        }
        pooled.remove(channel);
        channelSize.decrementAndGet();

        return channel.close();
    }

    @Override
    public void shutdown() {
        // todo:....
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
     * 创建一个新的连接
     *
     * @param request 代理请求
     * @param connection 逻辑连接
     * @return channelFuture
     */
    private ChannelFuture createNewChannel(CompletableFuture<Channel> future, ProxyRequest request,
            Connection connection) {
        ChannelFuture channelFuture = createNewChannel0(request, connection);
        channelFuture.addListener((ChannelFutureListener) cf -> {
            if (cf.isSuccess()) {
                channelSize.incrementAndGet();
                pooled.add(cf.channel());
                future.complete(cf.channel());
            } else {
                future.completeExceptionally(cf.cause());
            }
        });
        return channelFuture;
    }
}
