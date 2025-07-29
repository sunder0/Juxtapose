package com.sunder.juxtapose.client;

/**
 * @author : denglinhai
 * @date : 16:25 2025/07/19
 */
public interface ProxyRequestPublisher {

    /**
     * 发布一个代理请求
     *
     * @param request 代理请求
     */
    void publishProxyRequest(ProxyRequest request);
}
