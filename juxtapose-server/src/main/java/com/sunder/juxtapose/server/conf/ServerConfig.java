package com.sunder.juxtapose.server.conf;

import cn.hutool.setting.Setting;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.MultiProtocolResource;
import com.sunder.juxtapose.common.ProxyProtocol;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 23:05 2025/07/28
 */
public class ServerConfig extends BaseConfig {
    public final static String NAME = "SERVER_CONFIG";
    private final static String PROXY_SERVER_GROUP = "ProxyServer";
    private final static String ENCRYPT_GROUP = "Encrypt";

    private final String SERVER_CONFIG_FILE = "conf/server.properties";
    private Setting config; // 存储整个server.properties的配置

    public ServerConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        MultiProtocolResource resource = new MultiProtocolResource(SERVER_CONFIG_FILE, true);
        config = new Setting();
        config.init(resource.getResource(), StandardCharsets.UTF_8, true);
        config.autoLoad(autoReload());
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
        config.store(config.getSettingPath());
    }

    public String getLogConfig() {
        return config.getStr("logging.config", "${JUXTAPOSE_HOME}/conf/logback_server.xml");
    }

    public String getLogLevel() {
        return config.getStr("logging.level", "info");
    }

    public ProxyProtocol getProxyProto() {
        return ProxyProtocol.valueOf(config.getStr("proxy.proto", PROXY_SERVER_GROUP, "JUXTA"));
    }

    public String getProxyHost() {
        return config.getStr("proxy.host", PROXY_SERVER_GROUP, "0.0.0.0");
    }

    public int getProxyPort() {
        return config.getInt("proxy.port", PROXY_SERVER_GROUP, 2201);
    }

    public boolean getProxyAuth() {
        return config.getBool("proxy.auth", PROXY_SERVER_GROUP, false);
    }

    public boolean getProxyTls() {
        return config.getBool("proxy.tls", PROXY_SERVER_GROUP, false);
    }

    public String getProxyUserName() {
        return config.getStr("proxy.username", PROXY_SERVER_GROUP, null);
    }

    public String getProxyPassword() {
        return config.getStr("proxy.password", PROXY_SERVER_GROUP, null);
    }

    public String getEncryptMethod() {
        return config.getStr("encrypt.method", ENCRYPT_GROUP, "pem");
    }

    public int getEncryptServerPort() {
        return config.getInt("encrypt.server.port", ENCRYPT_GROUP, 2202);
    }

}
