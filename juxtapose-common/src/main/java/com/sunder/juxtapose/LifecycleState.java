package com.sunder.juxtapose;

/**
 * @author : denglinhai
 * @date : 16:23 2025/07/10
 */
public enum LifecycleState {
    NEW(1),
    INITIALIZING(2),
    INITIALIZED(3),
    STARTING(4),
    STARTED(5),
    STOPPING(6),
    STOPPED(7),
    DESTROYING(8),
    DESTROYED(9);

    private final int step;

    LifecycleState(int step) {
        this.step = step;
    }

    /**
     * 判断状态是否在传入状态后面
     *
     * @param state
     * @return boolean
     */
    public boolean after(LifecycleState state) {
        return this.step >= state.step;
    }

    public int getStep() {
        return step;
    }

}
