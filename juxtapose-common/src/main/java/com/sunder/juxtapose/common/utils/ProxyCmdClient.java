package com.sunder.juxtapose.common.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;

/**
 * @author : denglinhai
 * @date : 15:39 2026/01/15
 *         与服务端命令服务器交互的客户端
 */
public class ProxyCmdClient {
    // 关闭对端真实服务连接url
    private final static String CLOSE_CONNECTION_URL = "http://%s:%s/api/cmd/connection/%s";

    private final String host;
    private final Integer port;

    public ProxyCmdClient(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 关闭对端与真实服务器建立起的连接
     *
     * @param serialId 序列id
     * @return
     */
    public boolean closeConnection(long serialId) {
        HttpRequest httpRequest = HttpUtil.createRequest(
                Method.DELETE, String.format(CLOSE_CONNECTION_URL, host, port, serialId));
        try (HttpResponse response = httpRequest.execute()) {
            if (!response.isOk()) {
                throw new RuntimeException(String.format("Close remote connection error:%s.", response.body()));
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return true;
    }

}
