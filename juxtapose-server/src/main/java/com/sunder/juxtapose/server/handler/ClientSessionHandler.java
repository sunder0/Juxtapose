package com.sunder.juxtapose.server.handler;

import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.mesage.AuthRequestMessage;
import com.sunder.juxtapose.common.mesage.AuthResponseMessage;
import com.sunder.juxtapose.server.session.ClientSession;
import com.sunder.juxtapose.server.session.SessionManager;
import com.sunder.juxtapose.server.session.SessionState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 14:41 2025/08/26
 */
public class ClientSessionHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger;
    private final SessionManager sessionManager;
    private final AuthenticationStrategy authStrategy;

    public ClientSessionHandler(SessionManager sessionManager, AuthenticationStrategy authStrategy) {
        this.logger = LoggerFactory.getLogger(ClientSessionHandler.class);
        this.sessionManager = sessionManager;
        this.authStrategy = authStrategy;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String sessionId = ctx.channel().id().asShortText();
        ClientSession session = new ClientSession(sessionId, (SocketChannel) ctx.channel());
        sessionManager.addSession(session);
        session.changeState(SessionState.CONNECTED);
        ctx.channel().attr(ClientSession.SESSION_KEY).set(session);

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            byte serviceId = byteBuf.getByte(byteBuf.readerIndex());
            if (serviceId == AuthRequestMessage.SERVICE_ID) {
                ClientSession session = sessionManager.getSession(ctx.channel().id().asShortText());
                if (session.getState() != SessionState.CONNECTED
                        && session.getState() != SessionState.AUTHENTICATING) {
                    ctx.writeAndFlush(new AuthResponseMessage(false, "repeat authentication"));
                }

                session.changeState(SessionState.AUTHENTICATING);

                AuthRequestMessage message = new AuthRequestMessage(byteBuf);
                if (authStrategy.checkPermission(message.getUserName(), message.getPassword())) {
                    session.changeState(SessionState.AUTHENTICATED);
                    ctx.writeAndFlush(new AuthResponseMessage(true));
                } else {
                    session.changeState(SessionState.DISCONNECTED);
                    AuthResponseMessage authMsg = new AuthResponseMessage(false, "401");
                    ctx.writeAndFlush(authMsg).addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String sessionId = ctx.channel().id().asShortText();
        ClientSession session = sessionManager.removeSession(sessionId);

        if (session != null && session.getState() != SessionState.CLOSED) {
            session.changeState(SessionState.DISCONNECTED);
            ctx.channel().attr(ClientSession.SESSION_KEY).set(null);
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ClientSession session = sessionManager.getSession(ctx.channel().id().asShortText());
        if (session != null) {
            logger.error("Exception caught for session:[{}].", session.getSessionId(), cause);
            session.changeState(SessionState.DISCONNECTED);
        }
        ctx.close();
    }

}
