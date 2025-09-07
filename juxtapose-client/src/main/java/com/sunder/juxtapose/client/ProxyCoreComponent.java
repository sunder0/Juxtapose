package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.publisher.HttpProxyRequestPublisher;
import com.sunder.juxtapose.client.publisher.Socks5ProxyRequestPublisher;
import com.sunder.juxtapose.client.subscriber.DirectForwardingSubscriber;
import com.sunder.juxtapose.client.subscriber.HttpProxyRequestSubscriber;
import com.sunder.juxtapose.client.subscriber.JuxtaProxyRequestSubscriber;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ConfigManager;
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
public class ProxyCoreComponent extends BaseCompositeComponent<ClientBootstrap> implements ProxyRequestPublisher {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    // 证书信息
    private CertComponent certComponent;
    // 代理节点的配置信息
    private ProxyServerConfig proxyServerCfg;
    // 代理请求的订阅者, NAME -> ProxyRequestSubscriber
    private final Map<String, ProxyRequestSubscriber> proxySubscribers = new ConcurrentHashMap<>();

    public ProxyCoreComponent(ClientBootstrap parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig((proxyServerCfg = new ProxyServerConfig(configManager)));

        addChildComponent(certComponent = new CertComponent(this));
        //certComponent.initInternal();
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
            subscribers = proxySubscribers.values().stream().filter(e -> !e.isProxy()).collect(Collectors.toList());
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
