package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 00:21 2025/07/11
 *         小一点的组件统一称为module
 */
public interface Module<T extends Component<?>> extends Named {

    /**
     * 返回隶属于那个组件
     *
     * @return component
     */
    T getComponent();
}
