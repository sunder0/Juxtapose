package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.publisher.HttpProxyRequestPublisher;
import com.sunder.juxtapose.client.publisher.Socks5ProxyRequestPublisher;
import com.sunder.juxtapose.client.rule.ProxyRuleEngine;
import com.sunder.juxtapose.client.rule.ProxyRuleEngine.RuleResult;
import com.sunder.juxtapose.client.subscriber.DirectForwardingSubscriber;
import com.sunder.juxtapose.client.subscriber.HttpProxyRequestSubscriber;
import com.sunder.juxtapose.client.subscriber.JuxtaProxyRequestSubscriber;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.ProxyAction;
import com.sunder.juxtapose.common.ProxyMode;
import com.sunder.juxtapose.common.ProxyProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author : denglinhai
 * @date : 23:49 2025/07/14
 */
public class ProxyCoreComponent extends BaseCompositeComponent<StandardClient> implements ProxyRequestPublisher {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    // 证书信息
    private CertComponent certComponent;
    // 简单规则引擎
    private ProxyRuleEngine proxyRuleEngine;
    // 代理节点的配置信息
    private ProxyServerConfig proxyServerCfg;
    // 代理请求的订阅者, NAME -> ProxyRequestSubscriber
    private final Map<String, ProxyRequestSubscriber> proxySubscribers = new ConcurrentHashMap<>();

    public ProxyCoreComponent(StandardClient parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig((proxyServerCfg = new ProxyServerConfig(configManager)));

        addChildComponent(certComponent = new CertComponent(this));
        addChildComponent(proxyRuleEngine = new ProxyRuleEngine(this));
        // 添加socks5本地代理
        addChildComponent(new Socks5ProxyRequestPublisher(this));
        // 添加http本地代理
        addChildComponent(new HttpProxyRequestPublisher(this));
        // 添加直连订阅
        addChildComponent(new DirectForwardingSubscriber(this));

        // 添加代理订阅
        for (ProxyServerNodeConfig node : proxyServerCfg.getProxyNodeConfigs()) {
            if (node.protocol == ProxyProtocol.JUXTA) {
                addChildComponent(new JuxtaProxyRequestSubscriber(node, certComponent, this));
            } else if (node.protocol == ProxyProtocol.HTTP) {
                addChildComponent(new HttpProxyRequestSubscriber(node, certComponent, this));
            }
        }

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

        List<ProxyRequestSubscriber> subscribers;
        if (proxyMode == ProxyMode.GLOBAL) {
            subscribers = proxySubscribers.values().stream().filter(ProxyRequestSubscriber::isProxy)
                    .collect(Collectors.toList());
        } else if (proxyMode == ProxyMode.DIRECT) {
            subscribers = proxySubscribers.values().stream().filter(e -> !e.isProxy()).collect(Collectors.toList());
        } else if (proxyMode == ProxyMode.RULE) {
            RuleResult result = proxyRuleEngine.match(request.getDomain(), request.getIp(), request.getPort());
            if (result.action == ProxyAction.REJECT) {
                logger.warn("Reject proxy request:[{}:{}].", request.getHost(), request.getPort());
                request.getClientChannel().close();
                return;
            } else if (result.action == ProxyAction.DIRECT) {
                subscribers = proxySubscribers.values().stream().filter(e -> !e.isProxy()).collect(Collectors.toList());
            } else {
                // todo: 暂时只有一个组Default，后续实现多组
                String proxyGroup = result.proxyGroup;
                subscribers = proxySubscribers.values().stream().filter(ProxyRequestSubscriber::isProxy)
                        .collect(Collectors.toList());
            }
        } else {
            subscribers = new ArrayList<>();
        }

        int index = request.hashCode() % subscribers.size();
        subscribers.get(index).subscribe(request);
    }

    /**
     * 注册一个代理请求订阅者
     *
     * @param subscriber
     */
    public void registerProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        proxySubscribers.put(subscriber.getName(), subscriber);
    }

    /**
     * 移除一个代理请求订阅者
     *
     * @param subscriber
     */
    public void removeProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        proxySubscribers.remove(subscriber.getName());
    }

}
