package com.sunder.juxtapose.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author : denglinhai
 * @date : 17:04 2023/7/14
 */
public class ActiveProxyConnection {
    private String host;
    private int port;
    private long serialId;
    private ChannelFuture channelFuture;
    //TODO: 有OOM危险
    private Queue<ByteBuf> cache = new ConcurrentLinkedQueue<>();

    public ActiveProxyConnection(String host, int port, long serialId) {
        this.host = host;
        this.port = port;
        this.serialId = serialId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getSerialId() {
        return serialId;
    }

    public void setSerialId(long serialId) {
        this.serialId = serialId;
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }

    public void setChannelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
    }

    public Queue<ByteBuf> getCache() {
        return cache;
    }

    public void setCache(Queue<ByteBuf> cache) {
        this.cache = cache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActiveProxyConnection that = (ActiveProxyConnection) o;
        return port == that.port &&
                serialId == that.serialId &&
                Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, serialId);
    }
}
