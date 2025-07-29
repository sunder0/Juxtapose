package com.sunder.juxtapose.common;

import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 17:32 2025/07/11
 *         基础模块
 */
public class BaseModule<T extends Component<?>> implements Module<T> {
    protected String name;
    // 隶属于那个组件
    protected T belongComponent;

    public BaseModule(T belongComponent) {
        this.name = getName();
        this.belongComponent = belongComponent;
    }

    public BaseModule(String name, T belongComponent) {
        this.name = name;
        this.belongComponent = Objects.requireNonNull(belongComponent);
    }

    @Override
    public T getComponent() {
        return belongComponent;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

}
