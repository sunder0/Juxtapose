package com.sunder.juxtapose.server.connection;

import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.connection.ConnectionState;
import com.sunder.juxtapose.common.connection.ProxyConnection;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.util.ReferenceCountUtil;

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

    @Override
    public ChannelFuture writeMessage(Object message) {
        if (proxyChannel.isActive() && isWritableState()) {
            return proxyChannel.writeAndFlush(message);
        } else {
            logger.warn("Attempted client -> proxy node send message in non-writable state: {}, channel active: {}",
                    state, proxyChannel.isActive());
            if (!proxyChannel.isActive()) {
                logger.info("Proxy channel closed, terminating the connection[{}].", connectId);
                close();
            }
            if (message instanceof ByteBuf) {
                ReferenceCountUtil.release(message);
            }
            return null;
        }
    }

    @Override
    public ChannelFuture readMessage(Object message) {
        if (proxyRequest.isActive() && isReadableState()) {
            updateActivityTime();
            return proxyRequest.returnMessage(message);
        } else {
            logger.warn("Attempted proxy node -> client send message in non-writable state: {}, channel active: {}",
                    state, proxyRequest.isActive());
            if (!proxyRequest.isActive()) {
                logger.info("Client channel closed, terminating the connection[{}].", connectId);
                closeForce();
            }
            if (message instanceof ByteBuf) {
                ReferenceCountUtil.release(message);
            }
            return null;
        }
    }


    @Override
    public ChannelFuture close() {
        try {
            lock.lock();
            if (state != ConnectionState.CLOSED) {
                changeState(ConnectionState.CLOSED);
                if (proxyChannel != null && proxyChannel.isActive()) {
                    proxyChannel.attr(CONNECT_KEY).set(null);
                    return proxyChannel.close().addListener(f ->
                            logger.info("Close connection[{}] success.", connectId));
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }

    public void setChannelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
    }
}
