package com.sunder.juxtapose.client;

import com.sunder.juxtapose.common.Named;
import com.sunder.juxtapose.common.ProxyProtocol;

/**
 * @author : denglinhai
 * @date : 14:50 2025/12/25
 *         代理服务节点
 */
public interface ProxyServerNode extends Named {

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

}
