package com.sunder.juxtapose.client.conf;

import cn.hutool.setting.yaml.YamlUtil;
import com.sunder.juxtapose.common.BaseConfig;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.MultiProtocolResource;

import java.util.List;
import java.util.Map;

/**
 * @author : denglinhai
 * @date : 11:48 2025/09/10
 */
public class ProxyRuleConfig extends BaseConfig {
    public final static String NAME = "PROXY_RULE_CONFIG";

    private final String PROXY_RULE_CONFIG_FILE = "conf/proxy_rules.yaml";
    private Map<String, Object> config;

    public ProxyRuleConfig(ConfigManager<?> configManager) {
        super(configManager, NAME);
    }

    @Override
    protected void initInternal() {
        MultiProtocolResource resource = new MultiProtocolResource(PROXY_RULE_CONFIG_FILE, true);
        this.config = YamlUtil.loadByPath(resource.getResource().getUrl().getPath());
    }

    @Override
    public boolean canSave() {
        return false;
    }

    @Override
    public boolean autoReload() {
        return false;
    }

    @Override
    public void save() {
        // todo:...
    }

    @SuppressWarnings("Uncheked")
    public List<String> getRules() {
        return (List<String>) config.get("rules");
    }

}
