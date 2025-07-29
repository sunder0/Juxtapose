package com.sunder.juxtapose.common.mesage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 15:52 2023/7/7
 */
public class ProxyRequestMessage extends Message {
    public final static byte SERVICE_ID = 1 << 3;

    private Long serialId; // 序列id
    private String host; // www.baidu.com
    private Integer port;
    private ByteBuf content; // 转发的内容

    public ProxyRequestMessage(Long serialId, String host, Integer port, ByteBuf content) {
        super(SERVICE_ID);
        this.serialId = serialId;
        this.host = host;
        this.port = port;
        this.content = content;
    }

    public ProxyRequestMessage(ByteBuf byteBuf) {
        super(byteBuf);
    }

    @Override
    protected ByteBuf serialize0(ByteBufAllocator allocator) {
        //long(serialId) + int(host length) + host + short(port) + int(content length)
        byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);

        ByteBuf header = allocator.directBuffer(8 + 4 + hostBytes.length + 2 + 4);
        header.writeLong(serialId);
        header.writeInt(hostBytes.length);
        header.writeBytes(hostBytes);
        header.writeShort(port);
        header.writeInt(content.readableBytes());

        CompositeByteBuf composite = allocator.compositeBuffer(2);
        composite.addComponent(true, header);
        composite.addComponent(true, content);

        return composite;
    }

    @Override
    protected void deserialize0(ByteBuf message) {
        this.serialId = message.readLong();
        int hostLength = message.readInt();

        byte[] hostBytes = new byte[hostLength];
        message.readBytes(hostBytes);
        this.host = new String(hostBytes, StandardCharsets.UTF_8);
        this.port = Short.toUnsignedInt(message.readShort());

        int contentLength = message.readInt();
        this.content = message.readRetainedSlice(contentLength);
    }

    public Long getSerialId() {
        return serialId;
    }

    public void setSerialId(Long serialId) {
        this.serialId = serialId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public ByteBuf getContent() {
        return content;
    }

    public void setContent(ByteBuf content) {
        this.content = content;
    }
}
