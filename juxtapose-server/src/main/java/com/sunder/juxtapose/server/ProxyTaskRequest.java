package com.sunder.juxtapose.server;


import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import io.netty.channel.socket.SocketChannel;

import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 11:34 2023/7/10
 *         代理任务请求，代表一个从客户端传过来的代理请求
 */
public class ProxyTaskRequest {
    // 从客户端传过来的代理请求信息
    private ProxyRequestMessage message;
    // 和客户端连接的channel
    private SocketChannel clientChannel;

    public ProxyTaskRequest(ProxyRequestMessage message, SocketChannel clientChannel) {
        this.message = Objects.requireNonNull(message);
        this.clientChannel = clientChannel;
    }

    public ProxyRequestMessage getMessage() {
        return message;
    }

    public void setMessage(ProxyRequestMessage message) {
        this.message = message;
    }

    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    public void setClientChannel(SocketChannel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProxyTaskRequest request = (ProxyTaskRequest) o;
        return Objects.equals(message.getSerialId(), request.getMessage().getSerialId()) &&
                Objects.equals(message.getHost(), request.getMessage().getHost()) &&
                Objects.equals(message.getPort(), request.getMessage().getPort());
    }

    @Override
    public int hashCode() {
        return Objects.hash(message.getSerialId(), message.getHost(), message.getPort());
    }
}
