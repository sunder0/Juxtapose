package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.publisher.Socks5ProxyRequestPublisher;
import com.sunder.juxtapose.client.subscriber.DirectForwardingSubscriber;
import com.sunder.juxtapose.client.subscriber.ProxyRelayServerComponent;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 23:49 2025/07/14
 */
public class ProxyCoreComponent extends BaseCompositeComponent<ClientBootstrap> implements ProxyRequestPublisher {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    // 代理节点的配置信息
    private ProxyServerConfig proxyServerCfg;
    // 代理请求的订阅者, NAME -> ProxyRequestSubscriber
    private final Map<String, ProxyRequestSubscriber> subscribers = new ConcurrentHashMap<>();

    public ProxyCoreComponent(ClientBootstrap parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig((proxyServerCfg = new ProxyServerConfig(configManager)));

        // 添加socks5代理处理
        addChildComponent(new Socks5ProxyRequestPublisher(this));

        // 添加直连订阅
        addChildComponent(new DirectForwardingSubscriber(this));

        // 添加代理订阅
        for (ProxyServerNodeConfig node : proxyServerCfg.getProxyNodeConfigs()) {
            addChildComponent(new ProxyRelayServerComponent(node, this));
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
        ProxyRequestSubscriber subscriber = subscribers.get(0);

        // transfers.put(request, req);
        subscriber.subscribe(request);
    }

    /**
     * 注册一个代理请求订阅者
     *
     * @param subscriber
     */
    public void registerProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        subscribers.put(subscriber.getName(), subscriber);
    }

    /**
     * 移除一个代理请求订阅者
     *
     * @param subscriber
     */
    public void removeProxyRequestSubscriber(ProxyRequestSubscriber subscriber) {
        subscribers.remove(subscriber.getName());
    }

}
