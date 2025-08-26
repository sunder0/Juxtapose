package com.sunder.juxtapose.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.io.resource.Resource;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 19:18 2025/08/14
 */
public class LogModule<T extends Component<?>> extends BaseModule<T> {
    public final static String NAME = "LOG_MODULE";

    public LogModule(String logbackXml, T belongComponent) {
        super(NAME, belongComponent);
        initializeLogSystem(logbackXml);
    }

    /**
     * 初始化logback日志系统
     *
     * @param logbackXml
     */
    private void initializeLogSystem(String logbackXml) {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();

            Resource resource = new FileResource(logbackXml);
            configurator.doConfigure(resource.getStream());

            // 输出状态信息（用于调试）
            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
            LoggerFactory.getLogger(LogModule.class).info("Logback initialized successfully");
        } catch (Exception ex) {
            throw new ComponentException("Logback initialized failed!", ex);
        }
    }

}
