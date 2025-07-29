package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 18:01 2025/07/11
 */
public interface ConfigManager<T extends Component<?>> extends Module<T> {

    /**
     * 注册一个配置
     *
     * @param config
     */
    void registerConfig(Config config);

    /**
     * 注销一个配置
     *
     * @param config
     */
    void deregisterConfig(Config config);

    /**
     * 更新一个配置
     *
     * @param config
     */
    void updateConfig(Config config);

    /**
     * 保存一个配置
     *
     * @param config
     */
    void saveConfig(Config config);

    /**
     * 从管理器里获取配置
     *
     * @param name
     * @return
     */
    Config getConfigByName(String name);

    @SuppressWarnings("unchecked")
    default <T extends Config> T getConfigByName(String name, Class<T> requireType) {
        Config c = getConfigByName(name);
        if (c == null) {
            return null;
        } else if (requireType.isInstance(c)) {
            return (T) c;
        }

        throw new ConfigException(new ClassCastException(
                String.format("Config:[%s] instance not requestType:[%s]!", name, requireType.getCanonicalName())));
    }

}
