package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 23:32 2025/08/17
 */
public enum ProxyAction {
    PROXY, DIRECT, REJECT,
    ;

    /**
     * 是不是需要代理
     * @param val
     * @return
     */
    public static boolean isProxy(String val) {
        return !ProxyAction.DIRECT.name().equals(val) && !ProxyAction.REJECT.name().equals(val);
    }

}
