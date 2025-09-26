package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.system.SystemProxySettingAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : denglinhai
 * @date : 16:55 2025/09/23
 */
public class ProxyContext {
    public final static ProxyContext CONTEXT = new ProxyContext();

    private String profileUrl; // 目前只支持一个profile链接下载，
    // 只存放每个组里选择的当前节点，比如select组是用户选择的节点，urltest组则是存放的延迟最低的节点等
    // group name -> node name
    private Map<String, String> selectNodes = new HashMap<>(16);
    private ClientOperate clientOperate; // 客户端相关操作
    private SystemProxySettingAdapter systemProxySetting; // 系统代理设置

    private ProxyContext() {
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
}
