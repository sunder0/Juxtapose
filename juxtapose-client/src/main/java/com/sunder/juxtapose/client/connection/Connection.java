package com.sunder.juxtapose.client.connection;

import com.sunder.juxtapose.client.ProxyMessageReceiver;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.SocketChannel;

/**
 * @author : denglinhai
 * @date : 16:06 2025/09/16
 */
public interface Connection {

    /**
     * 状态修改
     *
     * @param newState 新状态
     * @return bool
     */
    boolean changeState(ConnectionState newState);

    /**
     * 更新活跃时间
     */
    void updateActivityTime();

    /**
     * @return 获取连接id
     */
    String getConnectId();

    /**
     * @return 获取连接状态
     */
    ConnectionState getState();

    /**
     * @return 获取连接统计数据
     */
    ConnectionStats getStats();

    /**
     * @return 获取连接内容信息
     */
    ConnectionContent getContent();

    /**
     * 绑定proxy channel, 即写出channel（转发给proxy server端数据的channel）
     *
     * @param channel io.netty.channel.socket.SocketChannel
     */
    void bindProxyChannel(SocketChannel channel);

    /**
     * 激活数据传输，使得从publisher的数据可以到达subscriber，从而将数据转发给代理服务端
     *
     * @param receiver ProxyMessageReceiver
     */
    void activeMessageTransfer(ProxyMessageReceiver receiver);

    /**
     * 读取代理服务器消息，写入客户端消息
     *
     * @return io.netty.channel.ChannelFuture
     */
    ChannelFuture readMessage(Object message);

    /**
     * 读取客户端消息，写入代理服务器
     *
     * @return io.netty.channel.ChannelFuture
     */
    ChannelFuture writeMessage(Object message);

    /**
     * 关闭整个连接
     *
     * @return io.netty.channel.ChannelFuture
     */
    ChannelFuture close();

    /**
     * 添加一个状态监听
     *
     * @param listener com.sunder.juxtapose.client.connection.ConnectionStateListener
     * @return bool
     */
    boolean addConnectionStateListener(ConnectionStateListener listener);

    /**
     * 移除一个状态监听
     *
     * @param listener com.sunder.juxtapose.client.connection.ConnectionStateListener
     * @return bool
     */
    boolean removeConnectionStateListener(ConnectionStateListener listener);
}
