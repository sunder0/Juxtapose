package com.sunder.juxtapose.server;

import com.sunder.juxtapose.common.BootstrapComponent;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.server.conf.ServerConfig;

/**
 * @author : denglinhai
 * @date : 19:04 2025/07/29
 */
public class ServerBootstrap extends BootstrapComponent {
    public final static String NAME = "SERVER_BOOTSTRAP";

    public ServerBootstrap() {
        super(NAME);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig(new ServerConfig(configManager));

        addChildComponent(new ProxyCoreComponent(this));

        super.initInternal();
    }

}
