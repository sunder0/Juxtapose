package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 16:35 2025/08/08
 */
public class AuthResponseMessage extends Message {
    public final static byte SERVICE_ID = 1 << 6;

    private boolean passed; // 是否通过
    private String message; // 未通过的原因

    public AuthResponseMessage(boolean passed) {
        super(SERVICE_ID);
        this.passed = passed;
    }

    public AuthResponseMessage(boolean passed, String message) {
        super(SERVICE_ID);
        this.passed = passed;
        this.message = message;
    }

    public AuthResponseMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        // bool(passed) + short(message length) + message
        ByteBuf content;
        if (passed) {
            content = allocator.directBuffer(1);
            content.writeBoolean(passed);

        } else {
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            content = allocator.directBuffer(1 + 2 + msgBytes.length);
            content.writeBoolean(passed);
            content.writeShort(msgBytes.length);
            content.writeBytes(msgBytes);
        }
        return content;
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        this.passed = message.readBoolean();
        if (!this.passed) {
            byte[] msgBytes = new byte[message.readShort()];
            message.readBytes(msgBytes);
            this.message = new String(msgBytes, StandardCharsets.UTF_8);
        }
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
