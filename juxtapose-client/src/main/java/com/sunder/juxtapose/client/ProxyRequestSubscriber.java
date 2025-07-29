package com.sunder.juxtapose.client;

import com.sunder.juxtapose.common.Named;

/**
 * @author : denglinhai
 * @date : 15:52 2025/07/15
 *         接受来自请求代理的订阅者
 */
public interface ProxyRequestSubscriber extends Named {

    /**
     * 订阅一个请求代理
     *
     * @param request 代理请求
     */
    void subscribe(ProxyRequest request);
}
