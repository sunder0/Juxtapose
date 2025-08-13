package com.sunder.juxtapose.client.conf;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.CharUtil;
import cn.hutool.core.util.StrUtil;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.ProxyMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 15:18 2025/08/12
 */
public class ProxyServerConfig extends BaseConfig {
    public final static String NAME = "PROXY_SERVER_CONFIG";

    private final String PROXY_SERVER_CONFIG_FILE = "conf/proxy_servers.properties";
    private final List<ProxyServerNodeConfig> proxyNodeConfigs = new ArrayList<>(); // 存储整个client.properties的配置

    public ProxyServerConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        ClassPathResource resource = new ClassPathResource(PROXY_SERVER_CONFIG_FILE);
        List<String> readLines = FileUtil.readLines(resource.getUrl(), StandardCharsets.UTF_8);

        Map<String, Object> nodeConfig = new HashMap<>();
        for (String line : readLines) {
            if (line == null || StrUtil.startWith(line, "#")) {
                continue;
            }
            line = line.trim();
            if (StrUtil.isBlank(line)) {
                proxyNodeConfigs.add(BeanUtil.mapToBean(nodeConfig, ProxyServerNodeConfig.class, true));
                nodeConfig.clear();
                continue;
            }

            // 记录代理模式
            if (StrUtil.isSurround(line, CharUtil.BRACKET_START, CharUtil.BRACKET_END)) {
                nodeConfig.put("proxyMode", line.substring(1, line.length() - 1).trim());
                continue;
            }

            final String[] keyValue = StrUtil.splitToArray(line, '=', 2);
            // 跳过不符合键值规范的行
            if (keyValue.length < 2) {
                continue;
            }

            nodeConfig.put(keyValue[0].trim(), keyValue[1].trim());
        }

        if (!nodeConfig.isEmpty()) {
            proxyNodeConfigs.add(BeanUtil.mapToBean(nodeConfig, ProxyServerNodeConfig.class, true));
        }
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
        //todo
    }

    public List<ProxyServerNodeConfig> getProxyNodeConfigs() {
        return proxyNodeConfigs;
    }

    /**
     * 代理服务节点配置
     */
    public static class ProxyServerNodeConfig {
        public String auth;
        public String userName;
        public String password;

        public ProxyMode proxyMode;
        public String host;
        public Integer port;

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            ProxyServerNodeConfig that = (ProxyServerNodeConfig) object;
            return Objects.equals(proxyMode, that.proxyMode) && Objects.equals(host, that.host)
                    && Objects.equals(port, that.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(proxyMode, host, port);
        }

        @Override
        public String toString() {
            return "ProxyServerNodeConfig{" +
                    "auth='" + auth + '\'' +
                    ", userName='" + userName + '\'' +
                    ", password='" + password + '\'' +
                    ", proxyMode='" + proxyMode + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }

}
