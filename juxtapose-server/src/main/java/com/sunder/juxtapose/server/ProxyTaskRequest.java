package com.sunder.juxtapose.server;


import cn.hutool.core.lang.Assert;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.Objects;

/**
 * @author : sunder
 * @date : 11:34 2023/7/10
 *         代理任务请求，代表一个从客户端传过来的代理请求
 */
public class ProxyTaskRequest {
    // 从客户端传过来的代理请求信息
    private Long serialId;
    private ProxyProtocol protocol;
    private String host;
    private Integer port;
    private ByteBuf content; // 转发的内容

    // 和客户端连接的channel
    private final Channel clientChannel;

    public ProxyTaskRequest(Long serialId, ProxyProtocol protocol, String host, Integer port, ByteBuf content,
            Channel clientChannel) {
        this.serialId = serialId;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.content = content;
        this.clientChannel = clientChannel;
    }

    public ProxyTaskRequest(ProxyProtocol protocol, ProxyRequestMessage message, Channel clientChannel) {
        this.serialId = message.getSerialId();
        this.protocol = protocol;
        Assert.notNull(message);
        this.host = message.getHost();
        this.port = message.getPort();
        this.content = message.getContent();
        this.clientChannel = clientChannel;
    }

    public Long getSerialId() {
        return serialId;
    }

    public ProxyProtocol getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public ByteBuf getContent() {
        return content;
    }

    public Channel getClientChannel() {
        return clientChannel;
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
        return Objects.equals(serialId, request.getSerialId()) &&
                Objects.equals(host, request.getHost()) &&
                Objects.equals(port, request.getPort());
    }

    @Override
    public int hashCode() {
        return Objects.hash(serialId, host, port);
    }
}
