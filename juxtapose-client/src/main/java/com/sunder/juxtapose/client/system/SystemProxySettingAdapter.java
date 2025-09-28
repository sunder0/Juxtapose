package com.sunder.juxtapose.client.system;

import com.sunder.juxtapose.client.SystemAppContext;
import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.Platform;

/**
 * @author : denglinhai
 * @date : 10:08 2025/09/23
 */
public class SystemProxySettingAdapter extends BaseComponent<ProxyCoreComponent> {
    public final static String NAME = "SYSTEM_PROXY_SETTING";

    private ClientConfig cfg;
    private SystemProxySetting systemProxySetting;

    public SystemProxySettingAdapter(ProxyCoreComponent parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
        SystemAppContext.CONTEXT.registerSystemProxySetting(this);
    }

    @Override
    protected void startInternal() {
        cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        if (cfg.getProxyEnable()) {
            enableSystemProxy();
        }

        // 设置关闭清理钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("close juxtapose system proxy ...");
            disableSystemProxy();
        }));
    }

    /**
     * 开启系统本地代理
     */
    public void enableSystemProxy() {
        if (Platform.isWindows()) {
            if (systemProxySetting == null) {
                systemProxySetting = new WindowsSystemProxySetting();
            }
            systemProxySetting.enableSystemProxy(cfg.getProxyHost(), cfg.getHttpPort(), cfg.getProxyOverride());
        } else if (Platform.isMac()) {
            if (systemProxySetting == null) {
                systemProxySetting = new MacOSSystemProxySetting();
            }
            systemProxySetting.enableSystemProxy(cfg.getProxyHost(), cfg.getSocks5Port(), cfg.getProxyOverride());
        }
    }

    /**
     * 禁用系统代理
     */
    public void disableSystemProxy() {
        if (systemProxySetting != null) {
            systemProxySetting.disableSystemProxy();
        }
    }

}
