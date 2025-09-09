package com.sunder.juxtapose.common;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 00:22 2025/07/11
 *         基础配置类
 */
public abstract class BaseConfig implements Config {
    protected boolean initialized;
    protected String name;
    protected Path path; // 配置路径的封装
    protected final ConfigManager<?> configManager;

    public BaseConfig(ConfigManager<?> configManager, String name) {
        this.configManager = configManager;
        this.name = name;
    }

    @Override
    public void initialize() {
        if (initialized) {
            throw new ConfigException("Repeated initialization!");
        }
        try {
            initInternal();
            initialized = true;
        } catch (Exception ex) {
            throw ex;
        }
    }

    protected void initInternal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConfigManager<?> getConfigManager() {
        return configManager;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        BaseConfig that = (BaseConfig) object;
        return Objects.equals(name, that.name) && Objects.equals(configManager.getName(), that.configManager.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
