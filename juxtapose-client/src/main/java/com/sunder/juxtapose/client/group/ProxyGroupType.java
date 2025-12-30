package com.sunder.juxtapose.client.group;

import java.util.Arrays;

/**
 * @author : sunder
 * @date : 11:05 2025/12/23
 */
public enum ProxyGroupType {
    SELECT("select"), // 用户手动选择节点
    URL_TEST("url-test"), // 组内自动选择延迟最低的节点
    FALLBACK("fallback"), // 按顺序尝试节点，使用第一个可用的
    LOAD_BALANCE("load-balance"), // 在可用节点间进行负载均衡， 支持不同的均衡策略
    /**
     * 流量通过多个代理节点转发
     * - name: "Relay"
     * type: relay
     * proxies:
     * - "入口节点"
     * - "中间节点"
     * - "出口节点"
     */
    RELAY("relay");

    private String val;

    ProxyGroupType(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }

    public static ProxyGroupType fromVal(String val) {
        return Arrays.stream(values()).filter(e -> e.val.equalsIgnoreCase(val)).findFirst().orElse(null);
    }

}
