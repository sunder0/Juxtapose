package com.sunder.juxtapose.common.handler;

import com.sunder.juxtapose.common.mesage.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * @author : denglinhai
 * @date : 14:19 2023/7/7
 */
@ChannelHandler.Sharable
public class RelayMessageWriteEncoder extends ChannelOutboundHandlerAdapter {
    public final static RelayMessageWriteEncoder INSTANCE = new RelayMessageWriteEncoder();

    private RelayMessageWriteEncoder() {

    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Message) {
            try {
                Message message = (Message) msg;
                ByteBuf byteBuf = message.serialize(ctx.alloc());
                ctx.writeAndFlush(byteBuf, promise);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                throw new RuntimeException(ex);
            }
        } else {
            ctx.writeAndFlush(msg, promise);
        }
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
