package com.sunder.juxtapose.client;

import io.netty.buffer.ByteBuf;

/**
 * @author : denglinhai
 * @date : 18:29 2023/6/21
 */
public interface ProxyMessageReceiver {

    void receive(Long serialId, ByteBuf message);
}
