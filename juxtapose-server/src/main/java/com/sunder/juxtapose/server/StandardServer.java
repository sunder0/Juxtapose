package com.sunder.juxtapose.server;

import com.sunder.juxtapose.common.DefaultConfigManager;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ToplevelComponent;
import com.sunder.juxtapose.server.conf.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 19:04 2025/07/29
 */
public final class StandardServer extends ToplevelComponent {
    private static final String JUXTAPOSE_ENV = "JUXTAPOSE_HOME";
    private final static Logger logger = LoggerFactory.getLogger(StandardServer.class);

    public StandardServer() {
        super("STANDARD_SERVER");
    }

    @Override
    protected void initInternal() {
        DefaultConfigManager<StandardServer> configManager = new DefaultConfigManager<>(this);
        addModule(configManager);

        ServerConfig cfg = new ServerConfig(configManager);
        configManager.registerConfig(cfg);
        addModule(new LogModule<>(cfg.getLogConfig(), cfg.getLogLevel(), this));

        addChildComponent(new ProxyCoreComponent(this));

        super.initInternal();
        registerShutdownHook();
    }

    public static void main(String[] args) {
        String projectDir = Platform.getSystemVal(JUXTAPOSE_ENV);
        if (projectDir == null) {
            logger.error("If debug start, please set [JUXTAPOSE_HOME] in property or environment. "
                    + "eg: JUXTAPOSE_HOME=/Users/Juxtapose");
            return;
        }

        try {
            StandardServer server = new StandardServer();
            server.init();
            server.start();
            logger.info("Juxtapose server start successful.");
        } catch (Exception ex) {
            logger.error("Juxtapose server start error.", ex);
            System.exit(0);
        }
    }

    /**
     * 注册清理资源钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("close juxtapose server resource...");

        }));
    }

    // todo: log mange

}
