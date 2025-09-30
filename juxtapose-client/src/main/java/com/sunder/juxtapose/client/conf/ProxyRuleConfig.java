package com.sunder.juxtapose.client.conf;

import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.setting.yaml.YamlUtil;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.MultiProtocolResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author : denglinhai
 * @date : 11:48 2025/09/10
 */
public class ProxyRuleConfig extends BaseConfig {
    public final static String NAME = "PROXY_RULE_CONFIG";

    private final String PROXY_RULE_CONFIG_FILE = "conf/proxy_rules.yaml";
    private final Logger logger;
    private final Executor executor;
    private Map<String, Object> config;

    public ProxyRuleConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
        this.logger = LoggerFactory.getLogger(ProxyRuleConfig.class);
        this.executor = Executors.newSingleThreadExecutor(
                ThreadFactoryBuilder.create().setNamePrefix("proxy-rule-").build());
    }

    @Override
    protected void initInternal() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_RULE_CONFIG_FILE, true);
        this.config = YamlUtil.loadByPath(resource.getResource().getUrl().getPath());
        if (autoReload()) {
            registerWatcher(resource.getResource());
        }
    }

    @Override
    public boolean canSave() {
        return false;
    }

    @Override
    public boolean autoReload() {
        return true;
    }

    @Override
    public void save() {
        // todo:...
    }

    @SuppressWarnings("unchecked")
    public List<String> getRules() {
        return Collections.unmodifiableList((List<String>) config.get("rules"));
    }

    public File getConfigDirectory() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_RULE_CONFIG_FILE, true);
        return ((FileResource) resource.getResource()).getFile();
    }

    /**
     * 注册监听
     *
     * @param resource
     */
    private void registerWatcher(Resource resource) {
        try {
            Path config_file = Paths.get(resource.getUrl().toURI());
            WatchService watchService = FileSystems.getDefault().newWatchService();

            Path directory = config_file.getParent();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            executor.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = null;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changedFile = (Path) event.context();
                            if (changedFile.getFileName().equals(config_file.getFileName())) {
                                this.config = YamlUtil.loadByPath(resource.getUrl().getPath());
                            }
                        }
                    }
                    key.reset();
                }
            });
        } catch (Exception ex) {
            logger.error("The rule content was monitored to change, but loading failed.", ex);
        }
    }

}
