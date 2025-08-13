package com.sunder.juxtapose.common.mesage;

import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 16:35 2025/08/08
 */
public class AuthRequestMessage extends Message {
    public final static byte SERVICE_ID = 1 << 5;

    private String method; // 认证方式
    // simple auth
    private String userName;
    private String password;

    public AuthRequestMessage(String userName, String password) {
        super(SERVICE_ID);
        this.method = AuthenticationStrategy.SIMPLE;
        this.userName = userName;
        this.password = password;
    }

    public AuthRequestMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        // short(method length) + method + short(userName length) + userName + short(password length) + password
        byte[] methodBytes = method.getBytes(StandardCharsets.UTF_8);
        byte[] userBytes = userName.getBytes(StandardCharsets.UTF_8);
        byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);

        ByteBuf header = allocator.directBuffer(2 + methodBytes.length);
        header.writeShort(methodBytes.length);
        header.writeBytes(methodBytes);

        ByteBuf content;
        if (AuthenticationStrategy.SIMPLE.equals(method)) {
            content = allocator.directBuffer(+2 + userBytes.length + 2 + pwdBytes.length);
            content.writeShort(userBytes.length);
            content.writeBytes(userBytes);
            content.writeShort(pwdBytes.length);
            content.writeBytes(pwdBytes);
        } else {
            // ignore....
            content = allocator.directBuffer(0);
        }

        CompositeByteBuf composite = allocator.compositeBuffer(2);
        composite.addComponent(true, header);
        composite.addComponent(true, content);

        return composite;
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        int methodLength = message.readShort();
        byte[] method = new byte[methodLength];
        message.readBytes(message);
        this.method = new String(method, StandardCharsets.UTF_8);

        if (AuthenticationStrategy.SIMPLE.equals(this.method)) {
            int userLength = message.readShort();
            byte[] userName = new byte[userLength];
            message.readBytes(userName);
            this.userName = new String(userName, StandardCharsets.UTF_8);

            int pwdLength = message.readShort();
            byte[] pwd = new byte[pwdLength];
            message.readBytes(pwd);
            this.password = new String(pwd, StandardCharsets.UTF_8);
        }
    }

    public String getMethod() {
        return method;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
