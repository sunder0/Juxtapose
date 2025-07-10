package com.sunder.juxtapose;

/**
 * @author : denglinhai
 * @date : 16:22 2025/07/10
 */
public interface Lifecycle {

    String BEFORE_INIT_EVENT = "before_init";
    String AFTER_INIT_EVENT = "after_init";

    String BEFORE_START_EVENT = "before_start";
    String AFTER_START_EVENT = "after_start";

    String BEFORE_STOP_EVENT = "before_stop";
    String AFTER_STOP_EVENT = "after_stop";

    String BEFORE_DESTROY_EVENT = "before_destroy";
    String AFTER_DESTROY_EVENT = "after_destroy";

    /**
     * 初始化
     *
     * @throws LifecycleException
     */
    void init() throws LifecycleException;

    /**
     * 启动
     *
     * @throws LifecycleException
     */
    void start() throws LifecycleException;

    /**
     * 停止
     *
     * @throws LifecycleException
     */
    void stop() throws LifecycleException;

    /**
     * 销毁
     *
     * @throws LifecycleException
     */
    void destroy() throws LifecycleException;

    /**
     * 获取当前状态
     *
     * @return LifecycleState
     */
    LifecycleState getState();

    /**
     * 添加一个生命周期监听
     *
     * @param listener LifecycleListener
     */
    void addLifecycleListener(LifecycleListener listener);

    /**
     * 移除一个生命周期监听
     *
     * @param listener LifecycleListener
     */
    void removeLifecycleListener(LifecycleListener listener);

}
