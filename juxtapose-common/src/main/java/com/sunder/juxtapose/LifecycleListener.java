package com.sunder.juxtapose;

/**
 * @author : denglinhai
 * @date : 16:26 2025/07/10
 */
@FunctionalInterface
public interface LifecycleListener {

    /**
     * lifecycle事件监听
     *
     * @param event 事件封装
     */
    void lifecycleEvent(LifecycleEvent event);
}
