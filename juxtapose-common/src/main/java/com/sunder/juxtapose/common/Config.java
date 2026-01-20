package com.sunder.juxtapose.common;

/**
 * @author : sunder
 * @date : 00:21 2025/07/11
 */
public interface Config extends Named {

    /**
     * 初始化config，将配置文件加载
     */
    void initialize();

    /**
     * 绑定的配置管理器
     *
     * @return ConfigManager
     */
    ConfigManager<?> getConfigManager();

    /**
     * 添加一个配置监听类
     *
     * @param listener com.sunder.juxtapose.common.ConfigListener
     */
    void addConfigListener(ConfigListener listener);

    /**
     * 移除一个配置监听类
     *
     * @param listener com.sunder.juxtapose.common.ConfigListener
     */
    void removeConfigListener(ConfigListener listener);

    /**
     * 能否保存
     *
     * @return boolean
     */
    default boolean canSave() {
        return false;
    }

    /**
     * 是否自动加载
     *
     * @return boolean
     */
    default boolean autoReload() {
        return false;
    }

    /**
     * 保存
     */
    default void save() {
        throw new UnsupportedOperationException();
    }

}
