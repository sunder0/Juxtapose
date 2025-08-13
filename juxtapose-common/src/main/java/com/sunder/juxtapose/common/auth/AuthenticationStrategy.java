package com.sunder.juxtapose.common.auth;

/**
 * @author : denglinhai
 * @date : 18:15 2025/08/08
 *         认证策略
 */
public interface AuthenticationStrategy {
    /**
     * 简单验证
     */
    String SIMPLE = "simple";

    /**
     * 验证权限
     *
     * @return 验证是否通过
     */
    boolean checkPermission(String userName, String password);

}
