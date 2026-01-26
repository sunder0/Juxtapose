package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.system.SystemProxySettingAdapter;
import com.sunder.juxtapose.common.ApplicationContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : sunder
 * @date : 16:55 2025/09/23
 */
public class ClientApplicationContext implements ApplicationContext {
    private String profileUrl; // 目前只支持一个profile链接下载，
    // 只存放每个组里选择的当前节点，比如select组是用户选择的节点，urltest组则是存放的延迟最低的节点等
    // group name -> node name
    private Map<String, String> selectNodes = new ConcurrentHashMap<>(16);
    private ClientOperate clientOperate; // 客户端相关操作
    private SystemProxySettingAdapter systemProxySetting; // 系统代理设置
    private ProxyServerNodeManager proxyNodeManager;

    public ClientApplicationContext() {
    }

    public void addSelectNode(String group, String node) {
        selectNodes.putIfAbsent(group, node);
    }

    public void removeSelectNode(String group) {
        selectNodes.remove(group);
    }

    public ClientOperate getClientOperate() {
        return clientOperate;
    }

    public void registerClientOperate(ClientOperate clientOperate) {
        this.clientOperate = clientOperate;
    }

    public SystemProxySettingAdapter getSystemProxySetting() {
        return systemProxySetting;
    }

    public void registerSystemProxySetting(SystemProxySettingAdapter systemProxySetting) {
        this.systemProxySetting = systemProxySetting;
    }

    public Map<String, String> getSelectNodes() {
        return selectNodes;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public void refreshProxySubscribers() {
        proxyNodeManager.refreshProxySubscribers();
    }

    public CompletableFuture<Long> testLatency(String name) {
        return proxyNodeManager.testLatency(name);
    }

    public void registerProxyNodeManager(ProxyServerNodeManager proxyNodeManager) {
        this.proxyNodeManager = proxyNodeManager;
    }
}
