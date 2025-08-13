package com.sunder.juxtapose.server.conf;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.setting.Setting;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;

import java.nio.charset.StandardCharsets;

/**
 * @author : denglinhai
 * @date : 23:05 2025/07/28
 */
public class ServerConfig extends BaseConfig {
    public final static String NAME = "SERVER_CONFIG";
    private final static String RELAY_SERVER_GROUP = "RelayServer";
    private final static String ENCRYPT_GROUP = "Encrypt";

    private final String SERVER_CONFIG_FILE = "conf/client.properties";
    private Setting config; // 存储整个server.properties的配置

    public ServerConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        ClassPathResource resource = new ClassPathResource(SERVER_CONFIG_FILE);
        config = new Setting();
        config.init(resource, StandardCharsets.UTF_8, true);
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

    public String getRelayServerHost() {
        return config.getStr("relay.server.host", RELAY_SERVER_GROUP, "0.0.0.0");
    }

    public int getRelayServerPort() {
        return config.getInt("relay.server.port", RELAY_SERVER_GROUP, 1201);
    }

    public String getEncryptMethod() {
        return config.getStr("encrypt.method", ENCRYPT_GROUP, "pem");
    }

    public int getEncryptServerPort() {
        return config.getInt("encrypt.server.port", ENCRYPT_GROUP, 1202);
    }

}
