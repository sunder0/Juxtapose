package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 12:02 2026/01/20
 */
@FunctionalInterface
public interface ConfigListener {

    /**
     * 监听配置更改
     *
     * @param event 配置更改事件
     */
    void configChange(ConfigChangeEvent event);
}
