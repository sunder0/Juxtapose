package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 11:49 2023/7/7
 */
public class PingMessage extends Message {
    public final static byte SERVICE_ID = 1 << 1;

    private final static byte[] CONTENT = "PING".getBytes(StandardCharsets.UTF_8);
    private ByteBuf body;

    public PingMessage() {
        super(SERVICE_ID);
        ByteBuf byteBuf = Unpooled.directBuffer(CONTENT.length);
        byteBuf.writeBytes(CONTENT);
        this.body = byteBuf.asReadOnly();
    }

    public PingMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        return body.retainedSlice();
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        if (message.readableBytes() != CONTENT.length) {
            throw new RuntimeException("ping消息长度不匹配!");
        }

        for (byte data : CONTENT) {
            if (data != message.readByte()) {
                throw new RuntimeException("ping消息内容不匹配!");
            }
        }
    }
}
