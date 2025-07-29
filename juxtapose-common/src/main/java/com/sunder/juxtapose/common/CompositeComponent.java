package com.sunder.juxtapose.common;

import java.util.Map;

/**
 * @author : denglinhai
 * @date : 14:10 2025/07/11
 *         组合组件接口
 */
public interface CompositeComponent<T extends Component<?>> extends Component<T> {

    /**
     * 添加一个子组件
     *
     * @param child 子组件
     */
    void addChildComponent(Component<?> child);

    /**
     * 移除一个子组件
     *
     * @param name 子组件
     */
    void removeChildComponent(String name);

    /**
     * 根据名称获取一个子组件
     *
     * @param name 名称
     * @return component
     */
    Component<?> getChildComponentByName(String name);

    /**
     * 根据名称和类型获取子组件
     *
     * @param name 名称
     * @param requireType 类型
     * @param <V> 子组件
     * @return 子组件
     */
    @SuppressWarnings("unchecked")
    default <V extends Component<?>> V getChildComponentByName(String name, Class<V> requireType) {
        Component<?> c = getChildComponentByName(name);
        if (c == null) {
            return null;
        }

        if (!requireType.isInstance(c)) {
            throw new ComponentException(new ClassCastException(
                    String.format("Component [%s] is not type of %s.", getName(), requireType.getName())));
        }

        return (V) c;
    }

    /**
     * 子组件列表
     *
     * @return 子组件列表
     */
    Map<String, Component<?>> getChildComponents();

}
