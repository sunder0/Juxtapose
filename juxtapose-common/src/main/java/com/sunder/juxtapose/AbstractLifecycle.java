package com.sunder.juxtapose;

import cn.hutool.core.lang.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author : denglinhai
 * @date : 22:23 2025/07/10
 */
public abstract class AbstractLifecycle implements Lifecycle {
    private LifecycleState state = LifecycleState.NEW;
    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();
    protected ReentrantLock lock = new ReentrantLock(false);

    public AbstractLifecycle() {
    }

    public AbstractLifecycle(LifecycleListener... listeners) {
        Assert.notNull(listeners, "Listeners not null!");
        List<LifecycleListener> filter = Arrays.stream(listeners).filter(Objects::nonNull).collect(Collectors.toList());
        this.listeners.addAll(filter);
    }

    @Override
    public void init() throws LifecycleException {
        try {
            lock.lock();
            if (state.after(LifecycleState.INITIALIZING)) {
                throw new LifecycleException("Repeated initialization!");
            }
            state = LifecycleState.INITIALIZING;
            fireLifecycleEvent(BEFORE_INIT_EVENT, this);
            initInternal();
            state = LifecycleState.INITIALIZED;
            fireLifecycleEvent(AFTER_INIT_EVENT, this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将具体初始化实现逻辑延展至子类
     */
    protected void initInternal() {

    }

    @Override
    public void start() throws LifecycleException {
        try {
            lock.lock();
            if (state.after(LifecycleState.STARTING)) {
                throw new LifecycleException("Repeated start!");
            }
            state = LifecycleState.STARTING;
            fireLifecycleEvent(BEFORE_START_EVENT, this);
            startInternal();
            state = LifecycleState.STARTED;
            fireLifecycleEvent(AFTER_START_EVENT, this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将具体启动实现逻辑延展至子类
     */
    protected void startInternal() {

    }

    @Override
    public void stop() throws LifecycleException {
        try {
            lock.lock();
            if (state.after(LifecycleState.STOPPING)) {
                throw new LifecycleException("Repeated stop!");
            }
            state = LifecycleState.STOPPING;
            fireLifecycleEvent(BEFORE_STOP_EVENT, this);
            stopInternal();
            state = LifecycleState.STOPPED;
            fireLifecycleEvent(AFTER_STOP_EVENT, this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将具体停止实现逻辑延展至子类
     */
    protected void stopInternal() {

    }


    @Override
    public void destroy() throws LifecycleException {
        try {
            lock.lock();
            if (state.after(LifecycleState.DESTROYING)) {
                throw new LifecycleException("Repeated destroy!");
            }
            state = LifecycleState.DESTROYING;
            fireLifecycleEvent(BEFORE_DESTROY_EVENT, this);
            destroyInternal();
            state = LifecycleState.DESTROYED;
            fireLifecycleEvent(AFTER_DESTROY_EVENT, this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将具体销毁实现逻辑延展至子类
     */
    protected void destroyInternal() {

    }

    @Override
    public LifecycleState getState() {
        return state;
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    /**
     * 传播周期事件
     */
    private void fireLifecycleEvent(String event, Object data) {
        LifecycleEvent eventObj = new LifecycleEvent(this, event, data);
        for (LifecycleListener listener : listeners) {
            listener.lifecycleEvent(eventObj);
        }
    }

}
