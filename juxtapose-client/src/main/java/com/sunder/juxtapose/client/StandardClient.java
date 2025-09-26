package com.sunder.juxtapose.client;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.dns.StandardDnsResolverPool;
import com.sunder.juxtapose.common.DefaultConfigManager;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.ToplevelComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 18:56 2025/07/29
 */
public final class StandardClient extends ToplevelComponent implements ClientOperate {
    private final static String JUXTAPOSE_ENV = "JUXTAPOSE_HOME";
    private final static Logger logger = LoggerFactory.getLogger(StandardClient.class);

    public StandardClient() {
        super("STANDARD_CLIENT");
    }

    @Override
    protected void initInternal() {
        ProxyContext.CONTEXT.registerClientOperate(this);

        DefaultConfigManager<StandardClient> configManager = new DefaultConfigManager<>(this);
        addModule(configManager);

        ClientConfig cfg = new ClientConfig(configManager);
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
            StandardClient client = new StandardClient();
            client.init();
            client.start();
            logger.info("Juxtapose client start successful.");
        } catch (Exception ex) {
            logger.error("Juxtapose client start error.", ex);
            System.exit(0);
        }
    }

    /**
     * 注册清理资源钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("close juxtapose client resource...");
            StandardDnsResolverPool.dnsResolver.close();
        }));
    }

    // todo: log mange


    @Override
    public void enableSystemProxy() {

    }

    @Override
    public void disableSystemProxy() {

    }

    @Override
    public void restart() {

    }

    @Override
    public void close() {
        destroy();
        System.exit(0);
    }
}
