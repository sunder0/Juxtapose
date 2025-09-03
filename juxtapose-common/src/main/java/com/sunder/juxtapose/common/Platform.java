package com.sunder.juxtapose.common;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.PlatformDependent;

/**
 * @author : denglinhai
 * @date : 00:22 2025/07/11
 *         平台相关接口
 */
public interface Platform {

    /**
     * 是否是windows系统
     *
     * @return boolean
     */
    default boolean isWindows() {
        return PlatformDependent.isWindows();
    }

    /**
     * 是否是unix系统
     *
     * @return boolean
     */
    default boolean isUnix() {
        return PlatformDependent.isOsx();
    }

    /**
     * 确定哪个ServerSocketChannel
     *
     * @return ServerSocketChannel
     */
    default Class<? extends ServerSocketChannel> getServerSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }

        return NioServerSocketChannel.class;
    }

    /**
     * 确定哪个SocketChannel
     *
     * @return SocketChannel
     */
    default Class<? extends SocketChannel> getSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }

        return NioSocketChannel.class;
    }

    /**
     * 创建事件按循环组
     *
     * @param nThreads 线程数
     * @return EventLoopGroup
     */
    default EventLoopGroup createEventLoopGroup(int nThreads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads);
        }

        return new NioEventLoopGroup(nThreads);
    }

}
