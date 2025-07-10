package com.sunder.juxtapose;

import java.util.EventObject;

/**
 * @author : denglinhai
 * @date : 16:26 2025/07/10
 */
public class LifecycleEvent extends EventObject {
    private String type;
    private Object data;

    public LifecycleEvent(Lifecycle source, String type, Object data) {
        super(source);
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public Object getData() {
        return data;
    }
}
