package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

/**
 * @author : denglinhai
 * @date : 16:51 2023/7/7
 */
public class ProxyResponseMessage extends Message {
    public final static byte SERVICE_ID = 1 << 4;

    private Long serialId; // 序列id
    private boolean success; // 请求是否成功
    private ByteBuf content; // 返回的内容

    public ProxyResponseMessage(Long serialId, ByteBuf message) {
        super(SERVICE_ID);
        this.serialId = serialId;
        this.content = message;
        this.success = true;
    }

    public ProxyResponseMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        if (success) {
            // serialId(8) + boolean(1) + content length(4)
            ByteBuf header = allocator.directBuffer(8 + 1 + 4);
            header.writeLong(serialId);
            header.writeBoolean(success);
            header.writeInt(content.readableBytes());

            CompositeByteBuf composite = allocator.compositeBuffer(2);
            composite.addComponent(true, header);
            composite.addComponent(true, content);
            return composite;
        } else {
            ByteBuf header = allocator.directBuffer(8 + 1 + 4);
            header.writeLong(serialId);
            header.writeBoolean(success);

            if (content == null) {
                header.writeInt(0);
                return header;
            } else {
                header.writeInt(content.readableBytes());
            }
            CompositeByteBuf composite = allocator.compositeBuffer(2);
            composite.addComponent(true, header);
            composite.addComponent(true, content);
            return composite;
        }
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        this.serialId = message.readLong();
        this.success = message.readBoolean();

        int length = message.readInt();
        if (this.success) {
            this.content = message.readRetainedSlice(length);
        } else {
            if (length != 0) {
                this.content = message.readRetainedSlice(length);
            }
        }
    }

    public Long getSerialId() {
        return serialId;
    }

    public boolean isSuccess() {
        return success;
    }

    public ByteBuf getContent() {
        return content;
    }
}
