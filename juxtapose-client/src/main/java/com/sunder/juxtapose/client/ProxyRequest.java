package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.ProxyMessageTransfer.SimpleProxyMessageTransfer;
import com.sunder.juxtapose.common.id.IdGenerator;
import com.sunder.juxtapose.common.id.SimpleIdGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetAddress;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 16:29 2023/6/21
 */
public class ProxyRequest {
    public final static IdGenerator ID_GENERATOR = new SimpleIdGenerator();

    private final Long serialId;
    private String protocol; // 协议
    private String host; // 主机名，可能为域名/ipv4/ipv6地址
    private Integer port;
    private String domain; // 单独存放域名
    private InetAddress ip; // 存放java标准化后的ip格式
    // 与请求方保持的channel
    private final Channel clientChannel;

    // 消息传递者
    private final ProxyMessageTransfer transfer = new SimpleProxyMessageTransfer(this);


    public ProxyRequest(String host, Integer port, Channel clientChannel) {
        this.host = host;
        this.port = port;
        this.clientChannel = clientChannel;
        this.serialId = ID_GENERATOR.nextId();
    }

    public ProxyRequest(String protocol, String host, Integer port, Channel clientChannel) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.clientChannel = clientChannel;
        this.serialId = ID_GENERATOR.nextId();
    }

    public ProxyRequest(String host, Integer port, String domain, InetAddress ip, Channel clientChannel) {
        this.host = host;
        this.port = port;
        this.domain = domain;
        this.ip = ip;
        this.clientChannel = clientChannel;
        this.serialId = ID_GENERATOR.nextId();
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

    public String getDomain() {
        return domain;
    }

    public InetAddress getIp() {
        return ip;
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
