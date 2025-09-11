package com.sunder.juxtapose.common;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
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
    static boolean isWindows() {
        return PlatformDependent.isWindows();
    }

    /**
     * 是否是mac系统
     *
     * @return boolean
     */
    static boolean isMac() {
        return PlatformDependent.isOsx();
    }

    /**
     * 获取系统值，先从properties找，再从env里找
     *
     * @param name key
     * @return val
     */
    static String getSystemVal(String name) {
        String varValue = System.getProperty(name);
        // 环境变量中查找
        if (null == varValue) {
            varValue = System.getenv(name);
        }
        return varValue;
    }

    /**
     * 确定哪个ServerSocketChannel
     *
     * @return ServerSocketChannel
     */
    static Class<? extends ServerSocketChannel> serverSocketChannelClass() {
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
    static Class<? extends SocketChannel> socketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }

        return NioSocketChannel.class;
    }

    /**
     * 确定哪个DatagramChannel
     *
     * @return SocketChannel
     */
    static Class<? extends DatagramChannel> datagramChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueDatagramChannel.class;
        }

        return NioDatagramChannel.class;
    }

    /**
     * 创建事件按循环组
     *
     * @param nThreads 线程数
     * @return EventLoopGroup
     */
    static EventLoopGroup createEventLoopGroup(int nThreads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads);
        }

        return new NioEventLoopGroup(nThreads);
    }

}
