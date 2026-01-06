package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * @author : denglinhai
 * @date : 17:04 2026/01/06
 *         客户端这段连接关闭，但是服务端感受不到，故发此消息通知服务端关闭连接
 */
public class ProxyCloseMessage extends Message {
    public final static byte SERVICE_ID = -1;

    private Long serialId; // 序列id

    public ProxyCloseMessage(Long serialId) {
        super(SERVICE_ID);
        this.serialId = serialId;
    }

    public ProxyCloseMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        // long(serialId)
        ByteBuf byteBuf = allocator.directBuffer(8 );
        byteBuf.writeLong(serialId);

        return byteBuf;
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        this.serialId = message.readLong();
    }

    public Long getSerialId() {
        return serialId;
    }
}
