package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 14:16 2025/07/29
 *         顶层组件
 */
public class ToplevelComponent extends BaseCompositeComponent<VoidComponent> {

    public ToplevelComponent(String name, ApplicationContext context) {
        super(name, new VoidComponent(), ComponentLifecycleListener.INSTANCE);
        this.context = context;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

}
