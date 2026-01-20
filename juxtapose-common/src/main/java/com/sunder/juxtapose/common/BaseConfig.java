package com.sunder.juxtapose.common;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author : sunder
 * @date : 00:22 2025/07/11
 *         基础配置类
 */
public abstract class BaseConfig implements Config {
    protected boolean initialized;
    protected String name;
    protected Path path; // 配置路径的封装
    protected final ConfigManager<?> configManager;
    protected final List<ConfigListener> listeners = new CopyOnWriteArrayList<>();

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
    public void addConfigListener(ConfigListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConfigListener(ConfigListener listener) {
        listeners.remove(listener);
    }

    /**
     * 传播配置更改事件
     */
    protected void fireConfigChange(String property, Object oldVal, Object newVal) {
        ConfigChangeEvent eventObj = new ConfigChangeEvent(this, property, oldVal, newVal);
        for (ConfigListener listener : listeners) {
            listener.configChange(eventObj);
        }
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
