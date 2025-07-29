package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 00:21 2025/07/11
 */
public interface Component<T extends Component<?>> extends Lifecycle, Named {

    /**
     * 获取上级组件
     *
     * @return parentComponent
     */
    default T getParentComponent() {
        throw new UnsupportedOperationException("");
    }

    /**
     * 根据module名称获取module
     *
     * @param moduleName 名称
     * @param searchAtParent 是否找不到时从父组件获取
     * @return
     */
    Module<?> getModuleByName(String moduleName, boolean searchAtParent);

    /**
     * 根据模块名获取模块，从当前组件获取
     *
     * @param moduleName 模块名称
     * @param requireType 模块实现类Class对象
     * @param <V> 模块实现类型
     * @return 模块对象
     */
    default <V extends Module<?>> V getModuleByName(String moduleName, Class<V> requireType) {
        return getModuleByName(moduleName, false, requireType);
    }

    /**
     * 根据模块名获取模块，从当前组件获取
     *
     * @param moduleName 模块名称
     * @param searchAtParent 如果当前组件无法找到，是否从父组件中搜寻
     * @param requireType 模块实现类Class对象
     * @param <V> 模块实现类型
     * @return 模块对象
     */
    @SuppressWarnings("unchecked")
    default <V extends Module<?>> V getModuleByName(String moduleName, boolean searchAtParent, Class<V> requireType) {
        Module<?> m = getModuleByName(moduleName, searchAtParent);
        if (m == null) {
            return null;
        }

        if (requireType.isInstance(m)) {
            return (V) m;
        }

        throw new ComponentException(new ClassCastException(
                String.format("Module [%s] is not type of %s.", getName(), requireType.getName())));
    }

    /**
     * 添加模块
     *
     * @param module 模块
     */
    void addModule(Module<?> module);

}
