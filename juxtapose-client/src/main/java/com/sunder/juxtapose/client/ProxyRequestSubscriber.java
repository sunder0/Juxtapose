package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.connection.Connection;
import com.sunder.juxtapose.common.Named;
import com.sunder.juxtapose.common.ProxyProtocol;

/**
 * @author : denglinhai
 * @date : 15:52 2025/07/15
 *         接受来自请求代理的订阅者
 */
public interface ProxyRequestSubscriber extends Named {

    /**
     * 是否支持代理
     *
     * @return bool
     */
    default boolean isProxy() {
        return false;
    }

    /**
     * 代理模式
     *
     * @return ProxyMode
     */
    default ProxyProtocol proxyProtocol() {
        throw new UnsupportedOperationException();
    }

    /**
     * 订阅服务端url，，eg：127.0.0.1:443
     *
     * @return ProxyMode
     */
    default String proxyUri() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return 代理组名
     */
    default String proxyGroup() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return 代理延迟
     */
    default long proxyLatency() {
        throw new UnsupportedOperationException();
    }

    /**
     * 订阅一个请求代理, 返回一个与代理服务器的连接
     *
     * @param request 代理请求
     */
    Connection subscribe(ProxyRequest request);
}
