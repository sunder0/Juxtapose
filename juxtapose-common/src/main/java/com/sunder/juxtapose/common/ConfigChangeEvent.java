package com.sunder.juxtapose.common;

import java.util.EventObject;

/**
 * @author : denglinhai
 * @date : 12:12 2026/01/20
 */
public class ConfigChangeEvent extends EventObject {
    private String property;
    private Object oldVal;
    private Object newVal;

    public ConfigChangeEvent(Config source, String property, Object oldVal, Object newVal) {
        super(source);
    }

    public String getProperty() {
        return property;
    }

    public Object getOldVal() {
        return oldVal;
    }

    public Object getNewVal() {
        return newVal;
    }
}
