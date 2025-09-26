package com.sunder.juxtapose.client.conf;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.yaml.YamlUtil;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigException;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.MultiProtocolResource;
import com.sunder.juxtapose.common.ProxyProtocol;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 15:18 2025/08/12
 */
public class ProxyServerConfig extends BaseConfig {
    public final static String NAME = "PROXY_SERVER_CONFIG";

    private final String PROXY_SERVER_CONFIG_FILE = "conf/proxy_servers1.yaml";

    private Dict config; // 存储整个proxy_servers.yaml配置
    // 存储整个所有代理节点的配置
    private final List<ProxyServerNodeConfig> proxyNodeConfigs = new ArrayList<>();
    // 存储整个所有代理组的配置
    private final List<ProxyServerNodeGroupConfig> proxyNodeGroupConfigs = new ArrayList<>();

    public ProxyServerConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_SERVER_CONFIG_FILE, true);
        this.config = YamlUtil.loadByPath(resource.getResource().getUrl().getPath());
        initProxyData();
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public boolean autoReload() {
        return true;
    }

    @Override
    public void save() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_SERVER_CONFIG_FILE, true);
        try {
            YamlUtil.dump(config, new FileWriter(resource.getResource().getUrl().getPath()));
        } catch (Exception ex) {
            throw new ConfigException("save proxy node config error.", ex);
        }
    }

    /**
     * 从yaml文件中加载
     *
     * @param yaml yaml文件输入流
     */
    public void loadYamlStream(InputStream yaml) {
        this.config.clear();
        this.proxyNodeConfigs.clear();
        this.proxyNodeGroupConfigs.clear();
        this.config = YamlUtil.load(yaml, Dict.class);
        initProxyData();
        save();
        try {
            yaml.close();
        } catch (Exception ignore) {
        }
    }

    public List<ProxyServerNodeConfig> getProxyNodeConfigs() {
        return proxyNodeConfigs;
    }

    public List<ProxyServerNodeGroupConfig> getProxyNodeGroupConfigs() {
        return proxyNodeGroupConfigs;
    }

    public File getConfigDirectory() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_SERVER_CONFIG_FILE, true);
        return ((FileResource) resource.getResource()).getFile();
    }

    /**
     * 初始化代理数据
     */
    private void initProxyData() {
        // 转化忽视null和大小写
        CopyOptions options = new CopyOptions().ignoreNullValue().ignoreCase();
        // 获取proxy node config
        List<Map<String, Object>> proxyNodeInfos = config.getBean("proxies");
        for (Map<String, Object> proxyNodeInfo : proxyNodeInfos) {
            // 兼容clashwindows的yaml格式，type是小写
            proxyNodeInfo.put("type", proxyNodeInfo.get("type").toString().toUpperCase());

            ProxyServerNodeConfig proxyConfig =
                    BeanUtil.mapToBean(proxyNodeInfo, ProxyServerNodeConfig.class, true, options);
            proxyConfig.auth = StrUtil.isNotBlank(proxyConfig.username) && StrUtil.isNotBlank(proxyConfig.password);

            proxyNodeConfigs.add(proxyConfig);
        }

        // 获取proxy group config
        List<Map<String, Object>> proxyGroupInfos = config.getBean("proxy-groups");
        for (Map<String, Object> proxyGroupInfo : proxyGroupInfos) {
            ProxyServerNodeGroupConfig groupConfig =
                    BeanUtil.mapToBean(proxyGroupInfo, ProxyServerNodeGroupConfig.class, true, options);

            proxyNodeGroupConfigs.add(groupConfig);
        }
    }

    /**
     * 代理服务节点配置
     */
    public static class ProxyServerNodeConfig {
        public String name;

        public Boolean tls;
        public Boolean auth;
        public String username;
        public String password;

        public ProxyProtocol type;
        public String server;
        public Integer port;

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            ProxyServerNodeConfig that = (ProxyServerNodeConfig) object;
            return Objects.equals(type, that.type) && Objects.equals(server, that.server)
                    && Objects.equals(port, that.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, server, port);
        }

        @Override
        public String toString() {
            return "ProxyServerNodeConfig{" +
                    "name='" + name + '\'' +
                    ", tls=" + tls +
                    ", auth=" + auth +
                    ", username='" + username + '\'' +
                    ", password='" + password + '\'' +
                    ", protocol=" + type +
                    ", host='" + server + '\'' +
                    ", port=" + port +
                    '}';
        }
    }

    /**
     * 代理组的配置
     */
    public static class ProxyServerNodeGroupConfig {
        public String name; // 代理组名称
        public List<String> proxies; // 代理组里的节点
        public String type; // 代理组的类型

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            ProxyServerNodeGroupConfig that = (ProxyServerNodeGroupConfig) object;
            return Objects.equals(name, that.name) && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }

        @Override
        public String toString() {
            return "ProxyServerNodeGroupConfig{" +
                    "name='" + name + '\'' +
                    ", proxies=" + proxies +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

}
