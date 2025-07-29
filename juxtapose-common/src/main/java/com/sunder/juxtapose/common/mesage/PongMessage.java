package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * @author : denglinhai
 * @date : 18:40 2023/7/7
 */
public class PongMessage extends Message {
    public final static byte SERVICE_ID = 1 << 2;

    public PongMessage() {
        super(SERVICE_ID);
    }

    public PongMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        return null;
    }

    @Override
    protected void deserialize0(ByteBuf message) {

    }
}
