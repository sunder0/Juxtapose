package com.sunder.juxtapose.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;

import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;

/**
 * @author : denglinhai
 * @date : 18:21 2025/07/19
 *         消息传递，client -> ProxyRequest subscriber
 */
public interface ProxyMessageTransfer {

    /**
     * 传递消息
     *
     * @param message 从被代理方来的消息（客户端）
     */
    void transferMessage(ByteBuf message);

    /**
     * 返回消息
     *
     * @param message 从代理服务器来的消息（服务端）
     */
    ChannelFuture returnMessage(ByteBuf message);

    /**
     * 设置代理消息的接受者，目前为ProxyRequest的subscriber
     *
     * @param receiver 接受者
     */
    void setProxyMessageReceiver(ProxyMessageReceiver receiver);

    /**
     * 简单代理消息传递者
     */
    class SimpleProxyMessageTransfer implements ProxyMessageTransfer {
        private final ProxyRequest request;
        private volatile ProxyMessageReceiver receiver;
        // 临时存储receiver未设置前的消息
        private Queue<ByteBuf> cacheQueue = new LinkedTransferQueue<>();

        public SimpleProxyMessageTransfer(ProxyRequest request) {
            this.request = request;
        }

        @Override
        public void transferMessage(ByteBuf message) {
            if (receiver == null) {
                cacheQueue.offer(message);
                return;
            }
            synchronized (this) {
                if (receiver == null) {
                    cacheQueue.offer(message);
                    return;
                }
                receiver.receive(request.getSerialId(), message);
            }
        }

        @Override
        public ChannelFuture returnMessage(ByteBuf message) {
            return request.getClientChannel().writeAndFlush(message);
        }

        @Override
        public void setProxyMessageReceiver(ProxyMessageReceiver receiver) {
            synchronized (this) {
                Queue<ByteBuf> cache = this.cacheQueue;
                while (!cache.isEmpty()) {
                    ByteBuf buf = cache.poll();
                    receiver.receive(request.getSerialId(), buf);
                }

                this.receiver = receiver;
                this.cacheQueue = null;
            }
        }

    }

}
