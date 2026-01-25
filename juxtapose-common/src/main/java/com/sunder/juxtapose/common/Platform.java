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
import io.netty.util.internal.SystemPropertyUtil;

import java.util.concurrent.ThreadFactory;

/**
 * @author : sunder
 * @date : 00:22 2025/07/11
 * 平台相关接口
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

    /**
     * 创建事件按循环组
     *
     * @param nThreads 线程数
     * @return EventLoopGroup
     */
    static EventLoopGroup createEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads, threadFactory);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads, threadFactory);
        }

        return new NioEventLoopGroup(nThreads, threadFactory);
    }


    /**
     * 检查是否是 JDK 8u333 之前的版本
     */
    static boolean isBeforeJDK8u333() {
        String version = SystemPropertyUtil.get("java.version");
        if (version == null) {
            return true; // 保守估计，认为是旧版本
        }

        // 处理不同格式的版本字符串
        version = version.toLowerCase();
        try {
            int majorVersion;
            int updateVersion = 0;

            if (version.startsWith("1.8.")) {
                // Oracle JDK 格式: 1.8.0_333
                majorVersion = 8;
                int underscoreIndex = version.indexOf('_');
                if (underscoreIndex != -1) {
                    String updateStr = version.substring(underscoreIndex + 1);
                    // 移除可能的后缀如 -b10
                    if (updateStr.contains("-")) {
                        updateStr = updateStr.split("-")[0];
                    }
                    updateVersion = Integer.parseInt(updateStr);
                }
            } else if (version.startsWith("8u")) {
                // OpenJDK 格式: 8u333
                majorVersion = 8;
                String updateStr = version.substring(2);
                // 移除可能的后缀
                if (updateStr.contains("-")) {
                    updateStr = updateStr.split("-")[0];
                }
                updateVersion = Integer.parseInt(updateStr);
            } else if (version.startsWith("8.")) {
                // 其他格式: 8.0.333
                majorVersion = 8;
                String[] parts = version.split("\\.");
                if (parts.length >= 3) {
                    updateVersion = Integer.parseInt(parts[2]);
                }
            } else {
                // 不是 JDK 8
                return false;
            }

            return updateVersion < 333;
        } catch (Exception ex) {
            // 解析失败，保守估计为旧版本
            return true;
        }
    }

}
