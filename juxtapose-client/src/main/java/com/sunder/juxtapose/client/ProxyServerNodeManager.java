package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeGroupConfig;
import com.sunder.juxtapose.client.subscriber.DirectForwardingSubscriber;
import com.sunder.juxtapose.client.subscriber.HttpProxyRequestSubscriber;
import com.sunder.juxtapose.client.subscriber.JuxtaProxyRequestSubscriber;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.ProxyProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 10:31 2025/09/12
 */
public class ProxyServerNodeManager extends BaseCompositeComponent<ProxyCoreComponent> {
    public final static String NAME = "PROXY_SERVER_NODE_MANAGER";

    private ProxyServerConfig proxyServerCfg;
    // 证书信息
    private CertComponent certComponent;

    // 直连服务节点, NAME -> ProxyRequestSubscriber
    private final Map<String, ProxyRequestSubscriber> directNodes = new ConcurrentHashMap<>(16);
    // 代理服务节点, NAME -> ProxyRequestSubscriber
    private final Map<String, ProxyRequestSubscriber> proxyNodes = new ConcurrentHashMap<>(16);

    // 代理组, GROUP_NAME -> (NAME -> ProxyRequestSubscriber)
    private final Map<String, Map<String, ProxyRequestSubscriber>> proxyGroups = new ConcurrentHashMap<>(16);

    public ProxyServerNodeManager(ProxyCoreComponent parent) {
        super(NAME, parent);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig((proxyServerCfg = new ProxyServerConfig(configManager)));

        // 添加证书订阅
        addChildComponent(certComponent = new CertComponent(this));

        // 添加代理订阅
        addChildComponent(new DirectForwardingSubscriber(this));
        for (ProxyServerNodeConfig node : proxyServerCfg.getProxyNodeConfigs()) {
            if (node.protocol == ProxyProtocol.JUXTA) {
                addChildComponent(new JuxtaProxyRequestSubscriber(node, certComponent, this));
            } else if (node.protocol == ProxyProtocol.HTTP) {
                addChildComponent(new HttpProxyRequestSubscriber(node, certComponent, this));
            }
        }

        // 代理节点分组
        for (ProxyServerNodeGroupConfig group : proxyServerCfg.getProxyNodeGroupConfigs()) {
            proxyGroups.computeIfAbsent(group.name, k -> new ConcurrentHashMap<>(64));
            for (String proxyNode : group.proxies) {
                proxyGroups.get(group.name).put(proxyNode, proxyNodes.get(proxyNode));
            }
        }

        super.initInternal();
    }

    /**
     * 返回可用的直连接点
     *
     * @return
     */
    public ProxyRequestSubscriber directNode(ProxyRequest request) {
        List<ProxyRequestSubscriber> proxyNodes = new ArrayList<>(directNodes.values());

        int index = Math.abs(request.hashCode() % proxyNodes.size());
        return proxyNodes.get(index);
    }

    /**
     * 返回可用的代理节点
     *
     * @param request 代理请求
     * @param proxyGroup 代理组
     */
    public ProxyRequestSubscriber proxyNode(String proxyGroup, ProxyRequest request) {
        if (!proxyGroups.containsKey(proxyGroup)) {
            throw new RuntimeException("Proxy group is not exist.");
        }

        List<ProxyRequestSubscriber> proxyNodes = new ArrayList<>(proxyGroups.get(proxyGroup).values());
        if (proxyNodes.isEmpty()) {
            logger.error("No proxy nodes available, proxy will been closed.");
            request.close();
            throw new RuntimeException("No proxy nodes available.");
        }

        // todo: 代理组的策略替代默认的hash发布策略
        int index = Math.abs(request.hashCode() % proxyNodes.size());
        return proxyNodes.get(index);
    }

    // todo: 检测代理节点是否存活。。


    /**
     * 添加一个代理节点
     *
     * @param proxyNode
     * @return
     */
    public ProxyRequestSubscriber registerProxyRequestSubscriber(ProxyRequestSubscriber proxyNode) {
        if (!proxyNode.isProxy()) {
            return directNodes.putIfAbsent(proxyNode.getName(), proxyNode);
        }

        return proxyNodes.putIfAbsent(proxyNode.getName(), proxyNode);
    }

    /**
     * 移除一个代理节点
     *
     * @param proxyNode
     * @return
     */
    public ProxyRequestSubscriber removeProxyRequestSubscriber(ProxyRequestSubscriber proxyNode) {
        if (!proxyNode.isProxy()) {
            return directNodes.remove(proxyNode.getName());
        }

        return proxyNodes.remove(proxyNode.getName());
    }

}
