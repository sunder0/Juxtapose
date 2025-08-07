package com.sunder.juxtapose.common;

import io.netty.util.internal.PlatformDependent;

/**
 * @author : denglinhai
 * @date : 00:22 2025/07/11
 *         平台相关接口
 */
public interface Platform {

    /**
     * 是否是windows系统
     *
     * @return boolean
     */
    default boolean isWindows() {
        return PlatformDependent.isWindows();
    }

    /**
     * 是否是unix系统
     *
     * @return boolean
     */
    default boolean isUnix() {
        return PlatformDependent.isOsx();
    }
}
