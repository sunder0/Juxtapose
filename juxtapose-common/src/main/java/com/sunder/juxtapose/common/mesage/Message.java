package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

/**
 * @author : denglinhai
 * @date : 11:38 2023/7/6
 */
public abstract class Message {
    public final static int LENGTH_MAX_FRAME = 1024 * 1024 * 20;
    public final static int LENGTH_FILED_OFFSET = 1; // 内容偏移量
    public final static int LENGTH_FILED_LENGTH = 4; // 内容长度

    protected byte serviceId; // 服务的类型

    public Message(byte serviceId) {
        this.serviceId = serviceId;
    }

    public Message(ByteBuf byteBuf) {
        deserialize(byteBuf);
    }

    public ByteBuf serialize(ByteBufAllocator allocator) {
        ByteBuf header = allocator.directBuffer(5);
        header.writeByte(serviceId);

        ByteBuf body = serialize0(allocator);
        header.writeInt(body.readableBytes());

        CompositeByteBuf composite = allocator.compositeBuffer(2);
        composite.addComponent(true, header);
        composite.addComponent(true, body);
        return composite;
    }

    protected abstract ByteBuf serialize0(ByteBufAllocator allocator);

    /**
     * 解析消息
     * @param message
     */
    public void deserialize(ByteBuf message) {
        this.serviceId = message.readByte();

        int length = message.readInt();
        if (length != message.readableBytes()) {
            throw new RuntimeException("长度和可读字节数不匹配!");
        }

        deserialize0(message);
    }

    protected abstract void deserialize0(ByteBuf message);

    public byte getServiceId() {
        return serviceId;
    }
}
