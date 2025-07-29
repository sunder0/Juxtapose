package com.sunder.juxtapose.common;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : denglinhai
 * @date : 13:38 2025/07/11
 *         基础单个组件
 */
public abstract class BaseComponent<T extends Component<?>> extends BaseLifecycle implements Component<T> {
    protected final Logger logger;
    protected String name;
    // 父组件
    protected T parent;
    // moduleName -> module
    protected final Map<String, Module<?>> moduleMap = new ConcurrentHashMap<>(16);

    protected BaseComponent(String name) {
        this.name = name;
        this.parent = null;
        this.logger = LoggerFactory.getLogger(name);
    }

    protected BaseComponent(String name, T parent, LifecycleListener... listeners) {
        super(listeners);
        this.name = name;
        this.parent = Objects.requireNonNull(parent);
        this.logger = LoggerFactory.getLogger(name);
    }

    @Override
    public T getParentComponent() {
        return parent;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Module<?> getModuleByName(String moduleName, boolean searchAtParent) {
        Module<?> module = moduleMap.get(moduleName);
        if (module == null && searchAtParent) {
            return getParentComponent().getModuleByName(moduleName, true);
        }
        return module;
    }

    @Override
    public void addModule(Module<?> module) {
        moduleMap.put(module.getName(), module);
    }

    /**
     * @return 配置管理器
     */
    protected ConfigManager<?> getConfigManager() {
        Module<?> module = getModuleByName(DefaultConfigManager.NAME, true);
        if (module instanceof ConfigManager) {
            return (ConfigManager<?>) module;
        } else {
            return null;
        }
    }

}
