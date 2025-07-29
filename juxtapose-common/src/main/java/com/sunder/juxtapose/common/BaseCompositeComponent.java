package com.sunder.juxtapose.common;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 14:26 2025/07/11
 *         基础组合组件
 */
public abstract class BaseCompositeComponent<T extends Component<?>> extends BaseComponent<T>
        implements CompositeComponent<T> {
    // componentName -> component
    protected Map<String, Component<?>> componentMap = new ConcurrentHashMap<>(16);

    protected BaseCompositeComponent(String name) {
        super(name);
    }

    protected BaseCompositeComponent(String name, T parent, LifecycleListener... listeners) {
        super(name, parent, listeners);
    }

    @Override
    protected void initInternal() {
        for (Entry<String, Component<?>> entry : componentMap.entrySet()) {
            try {
                entry.getValue().init();
            } catch (Exception ex) {
                logger.error("Component[{}] init error, {}.", entry.getKey(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    protected void startInternal() {
        for (Entry<String, Component<?>> entry : componentMap.entrySet()) {
            try {
                entry.getValue().start();
            } catch (Exception ex) {
                logger.error("Component[{}] start error, {}.", entry.getKey(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    protected void stopInternal() {
        for (Entry<String, Component<?>> entry : componentMap.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception ex) {
                logger.error("Component[{}] stop error, {}.", entry.getKey(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    protected void destroyInternal() {
        for (Entry<String, Component<?>> entry : componentMap.entrySet()) {
            try {
                entry.getValue().destroy();
            } catch (Exception ex) {
                logger.error("Component[{}] destroy error, {}.", entry.getKey(), ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void addChildComponent(Component<?> child) {
        componentMap.put(child.getName(), child);
    }

    @Override
    public void removeChildComponent(String name) {
        componentMap.remove(name);
    }

    @Override
    public Component<?> getChildComponentByName(String name) {
        return componentMap.get(name);
    }

    @Override
    public Map<String, Component<?>> getChildComponents() {
        return Collections.unmodifiableMap(componentMap);
    }

}
