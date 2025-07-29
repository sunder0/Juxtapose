package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 14:16 2025/07/29
 *         启动类组件
 */
public class BootstrapComponent extends BaseCompositeComponent<VoidComponent> implements Platform {
    private final DefaultConfigManager<BootstrapComponent> configManager = new DefaultConfigManager<>(this);

    public BootstrapComponent(String name) {
        super(name, new VoidComponent(), ComponentLifecycleListener.INSTANCE);
        addModule(configManager);
    }

}
