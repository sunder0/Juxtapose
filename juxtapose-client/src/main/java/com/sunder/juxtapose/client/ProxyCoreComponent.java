package com.sunder.juxtapose.client;

import cn.hutool.core.lang.Assert;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.connection.DefaultConnectionManager;
import com.sunder.juxtapose.client.publisher.HttpProxyRequestPublisher;
import com.sunder.juxtapose.client.publisher.Socks5ProxyRequestPublisher;
import com.sunder.juxtapose.client.rule.ProxyRuleEngine;
import com.sunder.juxtapose.client.rule.ProxyRuleEngine.RuleResult;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ProxyMode;

/**
 * @author : denglinhai
 * @date : 23:49 2025/07/14
 */
public class ProxyCoreComponent extends BaseCompositeComponent<StandardClient> implements ProxyRequestPublisher {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    // 简单规则引擎
    private ProxyRuleEngine proxyRuleEngine;
    // 代理节点管理器
    private ProxyServerNodeManager proxyNodeManager;

    public ProxyCoreComponent(StandardClient parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        // 连接管理器
        addModule(new DefaultConnectionManager(this));
        // 代理规则引擎
        addChildComponent(proxyRuleEngine = new ProxyRuleEngine(this));
        // 代理节点管理器
        addChildComponent(proxyNodeManager = new ProxyServerNodeManager(this));

        // 添加socks5本地代理
        addChildComponent(new Socks5ProxyRequestPublisher(this));
        // 添加http本地代理
        addChildComponent(new HttpProxyRequestPublisher(this));

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        super.startInternal();
    }

    @Override
    protected void stopInternal() {
        super.stopInternal();
    }

    @Override
    protected void destroyInternal() {

        super.destroyInternal();
    }

    /**
     * 发布代理请求给订阅者
     *
     * @param request
     */
    @Override
    public void publishProxyRequest(ProxyRequest request) {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        ProxyMode proxyMode = cfg.getProxyMode();

        ProxyRequestSubscriber subscriber;
        if (proxyMode == ProxyMode.GLOBAL) {
            // todo: 默认全局代理走url-test组，会选择一个最低耗时的节点
            subscriber = proxyNodeManager.proxyNode("Default", request);
        } else if (proxyMode == ProxyMode.DIRECT) {
            subscriber = proxyNodeManager.directNode(request);
        } else if (proxyMode == ProxyMode.RULE) {
            RuleResult result = proxyRuleEngine.match(request.getDomain(), request.getIp(), request.getPort());
            switch (result.action) {
                case REJECT:
                    logger.warn("current proxy request[{}] was rejected.", request.requestUri());
                    request.close();
                    return;
                case DIRECT:
                    subscriber = proxyNodeManager.directNode(request);
                    break;
                default:
                    String proxyGroup = result.proxyGroup;
                    subscriber = proxyNodeManager.proxyNode(proxyGroup, request);
                    break;
            }
        } else {
            subscriber = null;
        }

        Assert.notNull(subscriber, "ProxyRequestSubscriber is not null!");
        subscriber.subscribe(request);
    }

    /**
     * 注册一个代理请求订阅者
     *
     * @param subscriber
     */
    public void registerProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        proxyNodeManager.registerProxyRequestSubscriber(subscriber);
    }

    /**
     * 移除一个代理请求订阅者
     *
     * @param subscriber
     */
    public void removeProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        proxyNodeManager.removeProxyRequestSubscriber(subscriber);
    }

}
