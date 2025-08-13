package com.sunder.juxtapose.common.auth;

import cn.hutool.core.util.StrUtil;

/**
 * @author : denglinhai
 * @date : 18:20 2025/08/08
 *         简单用户认证
 */
public class SimpleAuthenticationStrategy implements AuthenticationStrategy {
    private final String userName;
    private final String password;

    public SimpleAuthenticationStrategy(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public boolean checkPermission(String userName, String password) {
        if (StrUtil.isBlank(userName) || StrUtil.isBlank(password)) {
            return false;
        }

        return userName.equals(this.userName) && password.equals(this.password);
    }

}
