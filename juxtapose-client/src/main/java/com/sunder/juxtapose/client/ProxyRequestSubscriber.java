package com.sunder.juxtapose.client;

import com.sunder.juxtapose.common.Named;
import com.sunder.juxtapose.common.ProxyProtocol;

/**
 * @author : denglinhai
 * @date : 15:52 2025/07/15
 *         接受来自请求代理的订阅者
 */
public interface ProxyRequestSubscriber extends Named {

    /**
     * socks5代理
     */
    String SOCKS5_PROXY = "Socks5";
    /**
     * http代理
     */
    String HTTP_PROXY = "Http";

    /**
     * 是否支持代理
     * @return bool
     */
    default boolean isProxy() {
        return false;
    }

    /**
     * 代理模式
     * @return ProxyMode
     */
    default ProxyProtocol proxyMode() {
        throw new UnsupportedOperationException();
    }

    /**
     * 订阅服务端url，，eg：127.0.0.1:443
     * @return ProxyMode
     */
    default String proxyUri() {
        throw new UnsupportedOperationException();
    }

    /**
     * 订阅一个请求代理
     *
     * @param request 代理请求
     */
    void subscribe(ProxyRequest request);
}
