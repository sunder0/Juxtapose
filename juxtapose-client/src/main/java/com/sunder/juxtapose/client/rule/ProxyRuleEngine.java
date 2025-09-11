package com.sunder.juxtapose.client.rule;

import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.conf.ProxyRuleConfig;
import com.sunder.juxtapose.client.rule.ProxyRule.DomainKeywordProxyRule;
import com.sunder.juxtapose.client.rule.ProxyRule.DomainProxyRule;
import com.sunder.juxtapose.client.rule.ProxyRule.DomainSuffixProxyRule;
import com.sunder.juxtapose.client.rule.ProxyRule.GeoIPProxyRule;
import com.sunder.juxtapose.client.rule.ProxyRule.MatchProxyRule;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.ProxyAction;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : denglinhai
 * @date : 17:35 2025/09/09
 */
public class ProxyRuleEngine extends BaseComponent<ProxyCoreComponent> {
    public final static String NAME = "RULE_ENGINE";

    private GeoIPDatabase GEOIPDB;
    private final List<ProxyRule> proxyRules = new ArrayList<>();

    public ProxyRuleEngine(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        ClientConfig ccfg = configManager.getConfigByName(ClientConfig.NAME, ClientConfig.class);
        try {
            this.GEOIPDB = new GeoIPDatabase(ccfg.getGeoIPPath());
        } catch (Exception ex) {
            throw new ComponentException("Init GEOIP DB failed.", ex);
        }

        ProxyRuleConfig prcfg = new ProxyRuleConfig(configManager);
        configManager.registerConfig(prcfg);
        prcfg.getRules().forEach(rule -> addRule(parseRule(rule, GEOIPDB)));
    }

    /**
     * 判断属于哪个规则
     *
     * @param domain 域名
     * @param ip ip
     * @param port 端口
     * @return 返回匹配结果
     */
    public RuleResult match(String domain, InetAddress ip, int port) {
        for (ProxyRule proxyRule : proxyRules) {
            if (proxyRule.matches(domain, ip, port)) {
                return new RuleResult(proxyRule.proxyAction(), proxyRule.proxyGroup());
            }
        }
        // 不存在不能匹配的场景，最后一个规则MATCH是一定能匹配的
        throw new RuntimeException("Proxy rule settings are incorrect, please check...");
    }

    /**
     * 添加一个rule
     *
     * @return true/false
     */
    public boolean addRule(ProxyRule proxyRule) {
        return this.proxyRules.add(proxyRule);
    }

    /**
     * 移除一个rule
     *
     * @return true/false
     */
    public boolean removeRule(ProxyRule proxyRule) {
        return this.proxyRules.remove(proxyRule);
    }

    /**
     * 匹配结果
     */
    public static class RuleResult {
        public ProxyAction action;
        public String proxyGroup;

        public RuleResult(ProxyAction action, String proxyGroup) {
            this.action = action;
            this.proxyGroup = proxyGroup;
        }
    }


    /**
     * 将rule描述转变为引擎中的rule
     *
     * @param ruleContent 规则内容
     * @param GEOIP GEOIP数据库
     * @return com.sunder.juxtapose.client.rule.define.Rule
     */
    private static ProxyRule parseRule(String ruleContent, GeoIPDatabase GEOIP) {
        String[] parts = ruleContent.split(",");
        if (parts.length < 2) {
            return null;
        }

        /**
         * 1. DOMAIN-SUFFIX,msn.com,Default 三段表示为规则名称、拦截的值、代理组
         * 2. DOMAIN-SUFFIX,chromeapi.net,DIRECT 三段表示规则名称、拦截的值、代理动作
         *  注意：三段表示法，除了DIRECT、REJECT等固定动作外，其余表示代理组名称，默认动作为PROXY
         * 3. MATCH,Default 两端表示规则名称、代理组。是放在最后一个规则，当所有规则不生效就走这个，默认代理所有，
         */
        String name = parts[0];
        String value = parts[1];
        ProxyAction proxyAction = parts.length > 2 ? ProxyAction.isProxy(parts[2]) ? ProxyAction.PROXY
                : ProxyAction.valueOf(parts[2]) : ProxyAction.PROXY;
        String proxyGroup = parts.length > 2 ? ProxyAction.isProxy(parts[2]) ? parts[2] : null : value;

        switch (name) {
            case "DOMAIN":
                return new DomainProxyRule(value, proxyAction, proxyGroup);
            case "DOMAIN-SUFFIX":
                return new DomainSuffixProxyRule(value, proxyAction, proxyGroup);
            case "DOMAIN-KEYWORD":
                return new DomainKeywordProxyRule(value, proxyAction, proxyGroup);
            case "GEOIP":
                return new GeoIPProxyRule(value, proxyAction, proxyGroup, GEOIP);
            case "IP-CIDR":
                return null;
            // 更多规则类型...
            default:
                return new MatchProxyRule(proxyGroup);
        }
    }

}
