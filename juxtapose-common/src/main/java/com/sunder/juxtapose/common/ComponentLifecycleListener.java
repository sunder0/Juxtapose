package com.sunder.juxtapose.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 19:55 2025/07/28
 */
public class ComponentLifecycleListener implements LifecycleListener {
    public final static ComponentLifecycleListener INSTANCE = new ComponentLifecycleListener();
    private final static Logger logger = LoggerFactory.getLogger(ComponentLifecycleListener.class);

    private ComponentLifecycleListener() {
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        Component<?> component = (Component<?>) event.getSource();
        logger.info("Component[{}] lifecycle event: [{}], lifecycle status:[{}].", component.getName(), event.getType(),
                component.getState());
    }

}
