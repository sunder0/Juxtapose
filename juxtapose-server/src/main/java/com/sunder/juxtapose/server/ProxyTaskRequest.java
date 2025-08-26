package com.sunder.juxtapose.server;


import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.server.session.ClientSession;

import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 11:34 2023/7/10
 *         代理任务请求，代表一个从客户端传过来的代理请求
 */
public class ProxyTaskRequest {
    // 从客户端传过来的代理请求信息
    private ProxyRequestMessage message;
    // 和客户端连接的channel
    private ClientSession clientSession;

    public ProxyTaskRequest(ProxyRequestMessage message, ClientSession clientSession) {
        this.message = Objects.requireNonNull(message);
        this.clientSession = clientSession;
    }

    public ProxyRequestMessage getMessage() {
        return message;
    }

    public void setMessage(ProxyRequestMessage message) {
        this.message = message;
    }

    public ClientSession getClientSession() {
        return clientSession;
    }

    public void setClientSession(ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProxyTaskRequest request = (ProxyTaskRequest) o;
        return Objects.equals(message.getSerialId(), request.getMessage().getSerialId()) &&
                Objects.equals(message.getHost(), request.getMessage().getHost()) &&
                Objects.equals(message.getPort(), request.getMessage().getPort());
    }

    @Override
    public int hashCode() {
        return Objects.hash(message.getSerialId(), message.getHost(), message.getPort());
    }
}
