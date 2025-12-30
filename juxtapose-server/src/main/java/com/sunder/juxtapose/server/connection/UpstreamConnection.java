package com.sunder.juxtapose.server.connection;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.ProxyConnection;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.channel.ChannelFuture;

/**
 * @author : sunder
 * @date : 17:04 2023/7/14
 *         代指代理端到真实服务器的连接
 */
public class UpstreamConnection extends ProxyConnection {
    private ChannelFuture channelFuture;

    public UpstreamConnection(ProxyProtocol protocol, ProxyRequest proxyRequest) {
        super(protocol, proxyRequest);
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }

    public void setChannelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
    }
}
