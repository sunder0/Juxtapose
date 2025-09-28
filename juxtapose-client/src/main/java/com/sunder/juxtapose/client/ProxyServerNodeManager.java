package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeGroupConfig;
import com.sunder.juxtapose.client.subscriber.DirectForwardingSubscriber;
import com.sunder.juxtapose.client.subscriber.HttpProxyRequestSubscriber;
import com.sunder.juxtapose.client.subscriber.JuxtaProxyRequestSubscriber;
import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.ProxyProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : denglinhai
 * @date : 10:31 2025/09/12
 */
public class ProxyServerNodeManager extends BaseCompositeComponent<ProxyCoreComponent> {
    public final static String NAME = "PROXY_SERVER_NODE_MANAGER";

    private final AtomicBoolean updProxy = new AtomicBoolean(false);
    private final Queue<ProxyRequest> updCacheQueue = new ConcurrentLinkedQueue<>();

    private ProxyServerConfig proxyServerCfg;
    // 证书信息
    private CertComponent certComponent;
    // select 类型的组，每个profile只允许有一个
    private ProxyServerNodeGroupConfig selectGroup;
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
        loadProxySubscribers();

        SystemAppContext.CONTEXT.registerProxyNodeManager(this);

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
        // 在更新订阅期间，暂时不可用，临时，todo：可以添加一个缓存队列用于处理更新代理
        if (updProxy.get()) {
            return directNode(request);
        }

        // 有select组且用户选了select组的节点
        Map<String, String> selectNodes = SystemAppContext.CONTEXT.getSelectNodes();
        String select;
        if (selectGroup != null && (select = selectNodes.get(selectGroup.name)) != null) {
            System.out.println(11);
            return proxyNodes.get(select);
        }

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

    /**
     * 清空代理订阅节点
     */
    public void truncateAndLoadProxySubscribers() {
        if (updProxy.compareAndSet(false, true)) {
            proxyNodes.clear();
            proxyGroups.clear();

            loadProxySubscribers();
            updProxy.set(false);
        }
    }

    /**
     * 加载订阅节点
     */
    private void loadProxySubscribers() {
        for (ProxyServerNodeConfig node : proxyServerCfg.getProxyNodeConfigs()) {
            if (node.type == ProxyProtocol.JUXTA) {
                addChildComponent(new JuxtaProxyRequestSubscriber(node, certComponent, this));
            } else if (node.type == ProxyProtocol.HTTP) {
                addChildComponent(new HttpProxyRequestSubscriber(node, certComponent, this));
            }
        }

        // 代理节点分组
        checkProfileGroup();
        for (ProxyServerNodeGroupConfig group : proxyServerCfg.getProxyNodeGroupConfigs()) {
            if (group.type.equals("select")) {
                selectGroup = group;
            }

            proxyGroups.computeIfAbsent(group.name, k -> new ConcurrentHashMap<>(64));
            for (String proxyNode : group.proxies) {
                proxyGroups.get(group.name).put(proxyNode, proxyNodes.get(proxyNode));
            }
        }
    }

    private void checkProfileGroup() {
        long count = proxyServerCfg.getProxyNodeGroupConfigs().stream().filter(e -> e.type.equals("select")).count();
        if (count > 1) {
            throw new ComponentException("There can only be one select group type.");
        }
    }
}
