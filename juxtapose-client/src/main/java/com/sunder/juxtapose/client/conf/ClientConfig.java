package com.sunder.juxtapose.client.conf;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.setting.Setting;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;

import java.nio.charset.StandardCharsets;


/**
 * @author : denglinhai
 * @date : 18:31 2025/07/15
 */
public class ClientConfig extends BaseConfig {
    public final static String NAME = "CLIENT_CONFIG";
    private final static String SOCKS_GROUP = "Socks5";
    private final static String HTTP_GROUP = "Http";
    private final static String ENCRYPT_GROUP = "Encrypt";

    private final String CLIENT_CONFIG_FILE = "conf/client.properties";
    private Setting config; // 存储整个client.properties的配置

    public ClientConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        ClassPathResource resource = new ClassPathResource(CLIENT_CONFIG_FILE);
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

    public String getSocks5Host() {
        return config.getStr("socks.host", SOCKS_GROUP, "127.0.0.1");
    }

    public int getSocks5Port() {
        return config.getInt("socks.port", SOCKS_GROUP);
    }

    public boolean getSocks5Auth() {
        return config.getBool("socks.auth", SOCKS_GROUP);
    }

    public String getSocks5User() {
        return config.getStr("socks.userName", SOCKS_GROUP, "0");
    }

    public String getSocks5Pwd() {
        return config.getStr("socks.password", SOCKS_GROUP, "1");
    }

    public String getEncryptMethod() {
        return config.getStr("encrypt.method", ENCRYPT_GROUP, "pem");
    }

    public String getHttpHost() {
        return config.getStr("http.host", HTTP_GROUP, "127.0.0.1");
    }

    public int getHttpPort() {
        return config.getInt("http.port", HTTP_GROUP);
    }

}
