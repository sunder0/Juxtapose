package com.sunder.juxtapose.server;

import com.sunder.juxtapose.common.BootstrapComponent;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.server.conf.ServerConfig;

/**
 * @author : denglinhai
 * @date : 19:04 2025/07/29
 */
public final class ServerBootstrap extends BootstrapComponent {
    public final static String NAME = "SERVER_BOOTSTRAP";

    private static final String JUXTAPOSE_ENV = "JUXTAPOSE_HOME";

    public ServerBootstrap() {
        super(NAME);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        ServerConfig cfg;
        configManager.registerConfig(cfg = new ServerConfig(configManager));

        addChildComponent(new ProxyCoreComponent(this));

        addModule(new LogModule<>(cfg.getLogConfig(), this));

        super.initInternal();
    }

    public static void main(String[] args) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.start();
    }

}
