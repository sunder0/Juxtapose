package com.sunder.juxtapose.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 19:02 2025/07/11
 *         默认配置管理器
 */
public class DefaultConfigManager<T extends Component<?>> extends BaseModule<T> implements ConfigManager<T> {
    public final static String NAME = "DEFAULT_CONFIG_MANAGER";
    private final Map<String, Config> configMap = new ConcurrentHashMap<>(16);

    public DefaultConfigManager(T belongComponent) {
        super(NAME, belongComponent);
    }

    @Override
    public void registerConfig(Config config) {
        String name;
        if (configMap.containsKey((name = config.getName()))) {
            throw new ConfigException(String.format("Configuration[%s] already exists!", name));
        }

        configMap.put(name, config);
        try {
            config.initialize();
        } catch (Exception ex) {
            configMap.remove(name);
            throw ex;
        }
    }

    @Override
    public void deregisterConfig(Config config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateConfig(Config config) {

    }

    @Override
    public void saveConfig(Config config) {
        if (config.canSave()) {
            config.save();
        }
    }

    @Override
    public Config getConfigByName(String name) {
        return configMap.get(name);
    }

}
