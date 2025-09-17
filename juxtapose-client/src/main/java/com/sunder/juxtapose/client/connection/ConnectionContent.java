package com.sunder.juxtapose.client.connection;

import com.sunder.juxtapose.common.ProxyProtocol;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author : denglinhai
 * @date : 16:26 2025/09/17
 */
public class ConnectionContent {
    private ProxyProtocol protocol; // 代理走的协议类型
    private InetSocketAddress sourceAddress; // 客户端地址
    private String proxyHost; // 代理的请求host
    private Integer proxyPort; // 代理的请求port
    private Map<String, Object> metadata; // 元数据信息

    public ConnectionContent(ProxyProtocol protocol, InetSocketAddress sourceAddress, String proxyHost,
            Integer proxyPort) {
        this.protocol = protocol;
        this.sourceAddress = sourceAddress;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public ProxyProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(ProxyProtocol protocol) {
        this.protocol = protocol;
    }

    public InetSocketAddress getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(InetSocketAddress sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
