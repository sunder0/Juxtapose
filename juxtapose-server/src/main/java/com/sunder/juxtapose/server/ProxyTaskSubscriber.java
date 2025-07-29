package com.sunder.juxtapose.server;

/**
 * @author : denglinhai
 * @date : 12:12 2023/7/14
 */
public interface ProxyTaskSubscriber {

    void subscribe(ProxyTaskRequest request);
}
