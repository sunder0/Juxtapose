package com.sunder.juxtapose.client;

import io.netty.buffer.ByteBuf;

/**
 * @author : denglinhai
 * @date : 18:29 2023/6/21
 */
public interface ProxyMessageReceiver {

    /**
     * 接受从客户端传过来的代理的消息内容
     *
     * @param serialId
     * @param message
     */
    void receive(Long serialId, ByteBuf message);
}
