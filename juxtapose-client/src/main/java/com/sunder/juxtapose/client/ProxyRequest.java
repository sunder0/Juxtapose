package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.ProxyMessageTransfer.SimpleProxyMessageTransfer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : denglinhai
 * @date : 16:29 2023/6/21
 */
public class ProxyRequest {
    public final static AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final Long serialId;
    private String protocol; // 协议
    private String host;
    private Integer port;
    // 与请求方保持的channel
    private final Channel clientChannel;

    // 消息传递者
    private final ProxyMessageTransfer transfer = new SimpleProxyMessageTransfer(this);


    public ProxyRequest(String host, Integer port, Channel clientChannel) {
        this.host = host;
        this.port = port;
        this.clientChannel = clientChannel;
        this.serialId = ID_GENERATOR.incrementAndGet();
    }

    public ProxyRequest(String protocol, String host, Integer port, Channel clientChannel) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.clientChannel = clientChannel;
        this.serialId = ID_GENERATOR.incrementAndGet();
    }

    public Long getSerialId() {
        return serialId;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public Channel getClientChannel() {
        return clientChannel;
    }

    /**
     * 传递消息
     *
     * @param byteBuf
     */
    public void transferMessage(ByteBuf byteBuf) {
        this.transfer.transferMessage(byteBuf);
    }

    /**
     * 返回消息
     *
     * @param byteBuf
     * @return ChannelFuture
     */
    public ChannelFuture returnMessage(ByteBuf byteBuf) {
        return this.transfer.returnMessage(byteBuf);
    }

    /**
     * 返回消息
     *
     * @param byteBuf
     * @return ChannelFuture
     */
    public ChannelFuture returnMessage(Object byteBuf) {
        if (!(byteBuf instanceof ByteBuf)) {
            throw new ProxyMessageTransferException(new ClassCastException("message not is ByteBuf instance."));
        }
        return returnMessage((ByteBuf) byteBuf);
    }

    /**
     * 给transfer设置message receiver
     *
     * @param receiver ProxyMessageReceiver
     */
    public void setProxyMessageReceiver(ProxyMessageReceiver receiver) {
        this.transfer.setProxyMessageReceiver(receiver);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ProxyRequest that = (ProxyRequest) object;
        return Objects.equals(protocol, that.protocol) && Objects.equals(host, that.host)
                && Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, host, port);
    }
}
