package com.sunder.juxtapose.server;

/**
 * @author : denglinhai
 * @date : 19:50 2025/08/26
 *         一般指处理一个proxy request， 并且发布到dispatcher
 */
public interface ProxyTaskPublisher {

    /**
     * 发布一个代理任务
     *
     * @param request 代理任务
     */
    void publishProxyTask(ProxyTaskRequest request);
}
