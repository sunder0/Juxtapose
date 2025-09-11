package com.sunder.juxtapose.client.rule;

import com.sunder.juxtapose.common.ProxyAction;

import java.net.InetAddress;

/**
 * @author : denglinhai
 * @date : 17:15 2025/09/09
 */
public interface ProxyRule {

    /**
     * @param domain 域名
     * @param ip ip
     * @param port 端口
     * @return 是否匹配规则
     */
    boolean matches(String domain, InetAddress ip, int port);

    /**
     * 获取规则动作
     *
     * @return ProxyAction
     */
    ProxyAction proxyAction();

    /**
     * 代理组， 代理需要使用哪个proxy group
     *
     * @return proxy group
     */
    String proxyGroup();


    /**
     * 域名关键字规则
     */
    class DomainKeywordProxyRule implements ProxyRule {
        private final String keyword;
        private final ProxyAction action;
        private final String proxyGroup;

        public DomainKeywordProxyRule(String keyword, ProxyAction action, String proxyGroup) {
            this.keyword = keyword;
            this.action = action;
            this.proxyGroup = proxyGroup;
        }

        @Override
        public boolean matches(String domain, InetAddress ip, int port) {
            return domain.contains(keyword) || domain.toLowerCase().contains(keyword);
        }

        @Override
        public ProxyAction proxyAction() {
            return action;
        }

        @Override
        public String proxyGroup() {
            return proxyGroup;
        }

    }

    /**
     * 域名完全匹配规则
     */
    class DomainProxyRule implements ProxyRule {
        private final String domain;
        private final ProxyAction action;
        private final String proxyGroup;

        public DomainProxyRule(String domain, ProxyAction action, String proxyGroup) {
            this.domain = domain;
            this.action = action;
            this.proxyGroup = proxyGroup;
        }

        @Override
        public boolean matches(String domain, InetAddress ip, int port) {
            return this.domain.equalsIgnoreCase(domain);
        }

        public String getDomain() {
            return domain;
        }

        public ProxyAction proxyAction() {
            return action;
        }

        @Override
        public String proxyGroup() {
            return proxyGroup;
        }
    }

    /**
     * 域名后缀匹配规则
     */
    class DomainSuffixProxyRule implements ProxyRule {
        private final String suffix;
        private final ProxyAction action;
        private final String proxyGroup;

        public DomainSuffixProxyRule(String suffix, ProxyAction action, String proxyGroup) {
            this.suffix = suffix;
            this.action = action;
            this.proxyGroup = proxyGroup;
        }

        @Override
        public boolean matches(String domain, InetAddress ip, int port) {
            return domain != null && domain.toLowerCase().endsWith(suffix.toLowerCase());
        }

        @Override
        public ProxyAction proxyAction() {
            return action;
        }

        @Override
        public String proxyGroup() {
            return proxyGroup;
        }
    }

    /**
     * GEOIP库查询规则
     */
    class GeoIPProxyRule implements ProxyRule {
        private final String countryCode;
        private final ProxyAction action;
        private final GeoIPDatabase GEOIPDB;
        private final String proxyGroup;

        public GeoIPProxyRule(String countryCode, ProxyAction action, String proxyGroup, GeoIPDatabase geoIPDB) {
            this.countryCode = countryCode;
            this.action = action;
            this.proxyGroup = proxyGroup;
            this.GEOIPDB = geoIPDB;
        }

        @Override
        public boolean matches(String domain, InetAddress ip, int port) {
            return GEOIPDB.country(ip).equalsIgnoreCase(countryCode);
        }

        @Override
        public ProxyAction proxyAction() {
            return action;
        }

        @Override
        public String proxyGroup() {
            return proxyGroup;
        }

    }

    /**
     * 最后一个匹配规则，前置所有规则不符合后默认走的规则，默认走代理
     */
    class MatchProxyRule implements ProxyRule {
        private final String proxyGroup;

        public MatchProxyRule(String proxyGroup) {
            this.proxyGroup = proxyGroup;
        }

        @Override
        public boolean matches(String domain, InetAddress ip, int port) {
            return true;
        }

        @Override
        public ProxyAction proxyAction() {
            return ProxyAction.PROXY;
        }

        @Override
        public String proxyGroup() {
            return proxyGroup;
        }
    }

}
