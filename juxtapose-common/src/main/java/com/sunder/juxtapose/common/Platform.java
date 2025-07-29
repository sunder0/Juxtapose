package com.sunder.juxtapose.common;

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
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    /**
     * 是否是unix系统
     *
     * @return boolean
     */
    default boolean isUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("mac");
    }
}
