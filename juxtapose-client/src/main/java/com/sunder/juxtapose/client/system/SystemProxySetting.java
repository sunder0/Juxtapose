package com.sunder.juxtapose.client.system;

/**
 * @author : denglinhai
 * @date : 17:17 2025/09/23
 */
public interface SystemProxySetting {

    /**
     * 开启系统本地代理
     *
     * @param proxyHost 代理本地的主机
     * @param proxyPort 代理本地的端口
     * @param ignoreOverride 忽视的代理地址， eg: localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*
     */
    void enableSystemProxy(String proxyHost, int proxyPort, String ignoreOverride);

    /**
     * 禁用系统代理
     */
    void disableSystemProxy();

}
