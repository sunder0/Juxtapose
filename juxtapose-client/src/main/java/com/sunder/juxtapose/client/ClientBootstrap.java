package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BootstrapComponent;
import com.sunder.juxtapose.common.ConfigManager;

/**
 * @author : denglinhai
 * @date : 18:56 2025/07/29
 */
public final class ClientBootstrap extends BootstrapComponent {
    public final static String NAME = "CLIENT_BOOTSTRAP";

    public ClientBootstrap() {
        super(NAME);
    }

    @Override
    protected void initInternal() {
        ConfigManager<?> configManager = getConfigManager();
        configManager.registerConfig(new ClientConfig(configManager));

        addChildComponent(new ProxyCoreComponent(this));

        super.initInternal();
    }

}
