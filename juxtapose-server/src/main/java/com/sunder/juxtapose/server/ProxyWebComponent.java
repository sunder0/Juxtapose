package com.sunder.juxtapose.server;

import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;
import com.sunder.juxtapose.common.connection.ConnectionManager;
import com.sunder.juxtapose.server.connection.UpstreamConnectionManager;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author : denglinhai
 * @date : 11:39 2026/01/15
 *         封装spring web组件，用于命令交互
 */
public class ProxyWebComponent extends BaseComponent<ProxyCoreComponent> {
    public final static String NAME = "PROXY_MANAGE_COMPONENT";
    private final static String JUXTAPOSE_ENV = "JUXTAPOSE_HOME";
    private final static String SPRING_CONFIG_File = "//conf//spring.properties";

    public ProxyWebComponent(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void startInternal() {
        String confFolder = Platform.getSystemVal(JUXTAPOSE_ENV);
        String applicationFile = confFolder + SPRING_CONFIG_File;
        System.setProperty("spring.config.location", applicationFile);

        ApplicationContext applicationContext = SpringApplication.run(CommandServer.class);
        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        ConnectionManager connectionManager = getModuleByName(UpstreamConnectionManager.NAME, true,
                UpstreamConnectionManager.class);
        beanFactory.registerSingleton(UpstreamConnectionManager.NAME, connectionManager);
    }

    @SpringBootApplication
    @ComponentScan(basePackages = {"com.sunder.juxtapose.server.api"})
    public static class CommandServer {
    }

}
