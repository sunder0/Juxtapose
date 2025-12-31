package com.sunder.juxtapose.common.pool;

import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.concurrent.CompletableFuture;

/**
 * @author : denglinhai
 * @date : 10:42 2025/12/31
 *         channel连接池
 */
public interface ChannelPool {

    /**
     * 从连接池中获取一个channel
     *
     * @param request 一个代理描述
     * @param connection 此次构建的连接
     * @return java.util.concurrent.Future
     */
    CompletableFuture<Channel> acquire(ProxyRequest request, Connection connection);

    /**
     * 释放一个连接
     *
     * @param channel 被释放的channel
     */
    ChannelFuture release(Channel channel);

    /**
     * 关闭连接池
     */
    void shutdown();

}
