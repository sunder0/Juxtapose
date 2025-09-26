package com.sunder.juxtapose.client;

/**
 * @author : denglinhai
 * @date : 17:25 2025/09/22
 */
public interface ClientOperate {

    /**
     * 开启系统代理
     */
    void enableSystemProxy();

    /**
     * 关闭系统代理
     */
    void disableSystemProxy();

    /**
     * 重启客户端
     */
    void restart();

    /**
     * 关闭客户端
     */
    void close();

}
