package com.sunder.juxtapose.common.connection;


import io.netty.channel.Channel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

/**
 * @author : denglinhai
 * @date : 14:43 2026/01/21
 */
public class TrafficHandlerInfo {
    private Channel channel;
    private ChannelTrafficShapingHandler trafficShapingHandler;

    private long lastCumulativeWrittenBytes = 0; // 上一次记录的总写入
    private long lastCumulativeReadBytes = 0; // 上一次记录的总读取

    public TrafficHandlerInfo(Channel channel, ChannelTrafficShapingHandler trafficShapingHandler) {
        this.channel = channel;
        this.trafficShapingHandler = trafficShapingHandler;
    }

    public ChannelTrafficShapingHandler getTrafficShapingHandler() {
        return trafficShapingHandler;
    }

    public long getLastCumulativeWrittenBytes() {
        return lastCumulativeWrittenBytes;
    }

    public void setLastCumulativeWrittenBytes(long lastCumulativeWrittenBytes) {
        this.lastCumulativeWrittenBytes = lastCumulativeWrittenBytes;
    }

    public long getLastCumulativeReadBytes() {
        return lastCumulativeReadBytes;
    }

    public void setLastCumulativeReadBytes(long lastCumulativeReadBytes) {
        this.lastCumulativeReadBytes = lastCumulativeReadBytes;
    }

    public Channel getChannel() {
        return channel;
    }
}
