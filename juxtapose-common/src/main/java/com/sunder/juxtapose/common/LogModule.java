package com.sunder.juxtapose.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.io.resource.Resource;
import org.slf4j.LoggerFactory;

/**
 * @author : denglinhai
 * @date : 19:18 2025/08/14
 */
public class LogModule<T extends Component<?>> extends BaseModule<T> {
    public final static String NAME = "LOG_MODULE";

    private final String CURRENT_LOG_PATH = "logs/juxtapose.log";
    private final String LOG_PATH = "logs";
    private final String SERVICE_NAME = "juxtapose";

    public LogModule(String logbackXml, String level, T belongComponent) {
        super(NAME, belongComponent);
        initializeLogSystem(logbackXml, level);
    }

    /**
     * @return 获取当前日志路径
     */
    public String getCurrentLogPath() {
        MultiProtocolResource resource = new MultiProtocolResource(CURRENT_LOG_PATH, true);
        return resource.getResource().getUrl().getPath();
    }

    /**
     * 初始化logback日志系统
     *
     * @param logbackXml
     */
    private void initializeLogSystem(String logbackXml, String level) {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            // 设置日志等级property
            context.putProperty("LOG_LEVEL", level);

            Resource resource = new FileResource(logbackXml);
            configurator.doConfigure(resource.getStream());
            LoggerFactory.getLogger(LogModule.class).info("Logback initialized successfully");
        } catch (Exception ex) {
            throw new ComponentException("Logback initialized failed!", ex);
        }
    }

}
