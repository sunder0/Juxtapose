package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BootstrapComponent;
import com.sunder.juxtapose.common.LogModule;

/**
 * @author : denglinhai
 * @date : 18:56 2025/07/29
 */
public final class ClientBootstrap extends BootstrapComponent {
    public final static String NAME = "CLIENT_BOOTSTRAP";

    private static final String JUXTAPOSE_ENV = "JUXTAPOSE_HOME";

    public ClientBootstrap() {
        super(NAME);
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = new ClientConfig(configManager);
        configManager.registerConfig(cfg);

        addChildComponent(new ProxyCoreComponent(this));

        addModule(new LogModule<>(cfg.getLogConfig(), this));

        super.initInternal();
    }

    public static void main(String[] args) {
        //System.setProperty(JUXTAPOSE_ENV, serverHome);

        ClientBootstrap bootstrap = new ClientBootstrap();
        bootstrap.start();
    }

}
